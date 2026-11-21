"""End-to-end AI triage measurement on the local stack (#234, spec Docs/specs/234-ai-triage-end-to-end.md).

Submits the holdout texts through the API gateway as participants, one every 30 s (spec D1), while an operator polls
the control room's list every 2 s, then reads Reports' database once every deadline has passed (spec D3, D4).

    python -I e2e_triage.py --run sc3 --out runs/2026-11-21-e2e --env ../../../../infra/.env.example --project farol-234
    python -I e2e_triage.py --verify runs/2026-11-21-e2e
    python -I e2e_triage.py --self-test

Standard library only. `--run` needs the stack up (`docker compose up --wait`, with `--profile ai` for SC3) and
writes `<phase>-requests.jsonl` (one row per case, no text), `<phase>-summary.json` and `<phase>-status.jsonl`.
"""

from __future__ import annotations

import argparse
import contextlib
import hashlib
import io
import json
import math
import re
import statistics
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
CASES = "holdout.jsonl"
# Spec 234 D1, D2, D4: pre-registered, copied into the manifest before the runs
PROFILE = {"interval_s": 30, "poll_s": 2, "deadline_s": 30, "sc5_grace_s": 2, "cases": CASES, "order": "file"}
PARTICIPANTS = ["rita@farol.test", "sofia@farol.test", "ines@farol.test", "nuno@farol.test"]
OPERATOR = "miguel@farol.test"
TOKEN_RENEW_S = 180  # access tokens live 300 s
LOG_LINE = re.compile(r"Triage of request ([0-9a-f-]{36}): \w+ .* in (\d+) ms")
failures: list[str] = []


def check(condition: bool, name: str) -> None:
    print(("ok " if condition else "FAILED ") + name)
    if not condition:
        failures.append(name)


def nearest_rank(values: list[int], q: float) -> int | None:
    ordered = sorted(values)
    return ordered[math.ceil(q * len(ordered)) - 1] if ordered else None


def spread(values: list[int]) -> dict:
    return {"n": len(values), "p50": statistics.median(values) if values else None,
            "p95_nearest_rank": nearest_rank(values, .95), "max": max(values) if values else None}


def outcome(row: dict) -> str:
    """Spec 234 D3: what the request reads after its deadline."""
    if row["http_status"] != 201:
        return "NotCreated"
    if row["state"] == "Pending" and row["not_triaged_reason"] is None:
        return "Timeout"  # derived on read 30 s after submission (decision 6 of 15 November 2026)
    return row["not_triaged_reason"] or row["state"]


def valid_on_time(row: dict, deadline_ms: int) -> bool:
    return (row["http_status"] == 201 and row["state"] == "Suggested" and row["suggested_category"] is not None
            and row["suggested_priority"] is not None and row["e2e_ms"] is not None and row["e2e_ms"] <= deadline_ms)


def summarise(rows: list[dict], profile: dict = PROFILE) -> dict:
    deadline_ms = profile["deadline_s"] * 1000
    sc5_limit_ms = (profile["deadline_s"] + profile["sc5_grace_s"]) * 1000
    non_abstention = [r for r in rows if not r["abstain_expected"]]
    valid = [r for r in non_abstention if valid_on_time(r, deadline_ms)]
    valid_all = [r for r in rows if valid_on_time(r, deadline_ms)]
    outcomes: dict[str, int] = {}
    for row in rows:
        outcomes[outcome(row)] = outcomes.get(outcome(row), 0) + 1
    shown = [r for r in rows if r["first_seen_ms"] is not None]
    not_triaged_in_time = [r for r in rows if r["not_triaged_seen_ms"] is not None
                           and r["not_triaged_seen_ms"] <= sc5_limit_ms]
    out_of_order = sum(r["out_of_order_polls"] for r in rows)
    abstentions = [r for r in rows if r["abstain_expected"]]
    return {
        "requests": len(rows),
        "created": sum(r["http_status"] == 201 for r in rows),
        "sc3": {
            "valid_on_time_non_abstention": len(valid), "non_abstention_cases": len(non_abstention),
            "valid_on_time_rate": round(len(valid) / len(non_abstention), 4) if non_abstention else None,
            "valid_on_time_all": len(valid_all),
            "valid_on_time_rate_all": round(len(valid_all) / len(rows), 4) if rows else None,
            "pass": bool(non_abstention) and len(valid) / len(non_abstention) >= .95,
            "suggestion_e2e_ms": spread([r["e2e_ms"] for r in rows if r["state"] == "Suggested"]),
            "worker_ms": spread([r["worker_ms"] for r in rows if r["worker_ms"] is not None]),
            "expected_abstentions_abstained": sum(r["not_triaged_reason"] == "Abstained" for r in abstentions),
            "expected_abstentions": len(abstentions),
        },
        "outcomes": dict(sorted(outcomes.items())),
        "sc5": {
            "shown": len(shown), "not_triaged_in_time": len(not_triaged_in_time),
            "out_of_order_polls": out_of_order,
            "pass": bool(rows) and len(shown) == len(rows) and len(not_triaged_in_time) == len(rows)
            and out_of_order == 0,
            "first_seen_ms": spread([r["first_seen_ms"] for r in shown]),
            "not_triaged_seen_ms": spread([r["not_triaged_seen_ms"] for r in rows
                                           if r["not_triaged_seen_ms"] is not None]),
        },
    }


def load_cases() -> list[dict]:
    return [json.loads(line) for line in (HERE / CASES).read_text(encoding="utf-8").splitlines() if line.strip()]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


# ---------------------------------------------------------------- run


class Stack:
    def __init__(self, env_path: str, project: str) -> None:
        sys.path.insert(0, str(ROOT / "infra" / "tests"))
        import identity_smoke  # noqa: PLC0415 - the PKCE login of the smoke tests, only when a stack is used
        self.idp = identity_smoke
        self.env_path, self.project = env_path, project
        self.env = identity_smoke.read_env(env_path)
        self.gateway = f"http://127.0.0.1:{self.env.get('GATEWAY_PORT', '8080')}"
        host = self.env.get("PUBLIC_HOST", "localhost")
        self.keycloak = f"http://{host}:{self.env.get('KEYCLOAK_PORT', '8180')}"
        self.redirect_uri = f"http://{host}:{self.env.get('WEB_PORT', '3000')}/callback"
        self.tokens: dict[str, dict] = {}  # e-mail -> {"at": monotonic, "access_token", "refresh_token"}

    def _token_call(self, form: dict) -> dict:
        url = f"{self.keycloak}/realms/{self.idp.REALM}/protocol/openid-connect/token"
        status, _, body = self.idp.request(urllib.request.build_opener(self.idp.NoRedirect), url,
                                           {"client_id": self.idp.WEB_CLIENT, **form})
        if status != 200:
            raise RuntimeError(f"token endpoint answered {status}")
        return {"at": time.monotonic(), **json.loads(body)}

    def log_in(self, emails: list[str]) -> None:
        """PKCE login, as auth_users_smoke.token_for; about 6 s each on the laptop, so done before the run."""
        for email in emails:
            verifier, challenge = self.idp.pkce_pair()
            _, location = self.idp.log_in(self.keycloak, self.redirect_uri, email, self.env["FAROL_DEMO_PASSWORD"],
                                          challenge)
            code = urllib.parse.parse_qs(urllib.parse.urlparse(location or "").query).get("code", [""])[0]
            self.tokens[email] = self._token_call({"grant_type": "authorization_code", "code": code,
                                                   "redirect_uri": self.redirect_uri, "code_verifier": verifier})

    def keep_fresh(self) -> None:
        """Renews each access token with its refresh token (no password hashing) well before its 300 s end."""
        def renew() -> None:
            while True:
                for email, held in list(self.tokens.items()):
                    if time.monotonic() - held["at"] > TOKEN_RENEW_S:
                        self.tokens[email] = self._token_call({"grant_type": "refresh_token",
                                                               "refresh_token": held["refresh_token"]})
                time.sleep(5)
        threading.Thread(target=renew, daemon=True).start()

    def token(self, email: str) -> str:
        return self.tokens[email]["access_token"]

    def compose(self, *args: str, timeout: int = 120) -> str:
        argv = ["docker", "compose", "-p", self.project, "-f", str(ROOT / "infra" / "compose.yaml"),
                "--env-file", self.env_path, *args]
        return subprocess.run(argv, capture_output=True, text=True, timeout=timeout, check=True,
                              encoding="utf-8").stdout

    def sql(self, statement: str) -> list[list[str]]:
        out = self.compose("exec", "-T", "-e", f"PGPASSWORD={self.env['REPORTS_DB_PASSWORD']}", "postgres", "psql",
                           "-h", "postgres", "-U", "reports", "-d", "reports", "-v", "ON_ERROR_STOP=1", "-tA",
                           "-F", "\t", "-c", statement)
        return [line.split("\t") for line in out.splitlines() if line.strip()]

    def call(self, path: str, email: str, body: dict | None = None) -> tuple[int, dict | None]:
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.gateway + path, data=data, method="POST" if body is not None else "GET",
                                     headers={"Authorization": f"Bearer {self.token(email)}",
                                              "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=15) as response:
                return response.status, json.loads(response.read() or b"null")
        except urllib.error.HTTPError as error:
            return error.code, None


def seed(stack: Stack, event: uuid.UUID, team: uuid.UUID) -> None:
    """Spec 234 D2: the rows MembershipChanged and TeamChanged would write (US-12, US-18 not built yet)."""
    subs = {email: stack.idp.SEEDED_USERS[email][1] for email in [*PARTICIPANTS, OPERATOR]}
    values = ", ".join(f"('{event}', '{subs[e]}', 'RegularUser', 'Active', '{{}}', 1)" for e in PARTICIPANTS)
    stack.sql("INSERT INTO team_copy VALUES ('%s', '%s', 'Control room', "
              "'[{\"permission\": \"HelpRequests.Triage\", \"reportTypes\": []}]', '{Health}', false, 1); "
              "INSERT INTO membership_copy VALUES %s, ('%s', '%s', 'RegularUser', 'Active', '{%s}', 1)"
              % (team, event, values, event, subs[OPERATOR], team))


def run(phase: str, out: Path, env_path: str, project: str, trial: bool = False) -> int:
    stack = Stack(env_path, project)
    # --trial: three synthetic texts, not holdout cases, to check the stack before a measured run
    cases = [{"id": f"X{i}", "input": {"text": f"Teste de funcionamento {i}: a casa de banho junto ao palco está fechada."},
              "expected": {"abstain": False}} for i in range(1, 4)] if trial else load_cases()
    event, team = uuid.uuid4(), uuid.uuid4()
    seed(stack, event, team)
    stack.log_in([*PARTICIPANTS, OPERATOR])
    stack.keep_fresh()
    base = f"/api/v1/events/{event}"
    sent: dict[str, dict] = {}   # request id -> row
    order: list[str] = []        # request ids in submission order
    rows: list[dict] = []
    status_log: list[dict] = []
    t0 = time.monotonic()

    def poll() -> None:
        now = time.monotonic()
        code, triage = stack.call(f"{base}/triage-status", OPERATOR)
        state = (triage or {}).get("available"), (triage or {}).get("reason")
        if not status_log or (status_log[-1]["available"], status_log[-1]["reason"]) != state or code != 200:
            status_log.append({"t_s": round(now - t0, 1), "http_status": code, "available": state[0],
                               "reason": state[1]})
        code, listed = stack.call(f"{base}/help-requests", OPERATOR)
        if code != 200 or not listed:
            return
        seen = [item for item in listed["items"] if item["id"] in sent]
        highest = -1
        for item in seen:
            row, index = sent[item["id"]], order.index(item["id"])
            row["out_of_order_polls"] += index < highest
            highest = max(highest, index)
            elapsed = round((now - row["_sent"]) * 1000)
            if row["first_seen_ms"] is None:
                row["first_seen_ms"] = elapsed
            if (item["triage"] or {}).get("state") == "NotTriaged" and row["not_triaged_seen_ms"] is None:
                row["not_triaged_seen_ms"] = elapsed

    def wait_until(moment: float) -> None:
        while time.monotonic() < moment:
            next_poll = time.monotonic() + PROFILE["poll_s"]
            poll()
            time.sleep(max(0.0, min(next_poll, moment) - time.monotonic()))

    for i, case in enumerate(cases):
        account = PARTICIPANTS[i % len(PARTICIPANTS)]
        wait_until(t0 + i * PROFILE["interval_s"])
        start = time.monotonic()
        code, body = stack.call(f"{base}/help-requests", account, {
            "clientRequestId": str(uuid.uuid4()), "description": case["input"]["text"],
            "location": {"mapVersion": 1, "x": 0.5, "y": 0.5}})
        row = {"case": case["id"], "abstain_expected": case["expected"]["abstain"], "account": account.split("@")[0],
               "slot_s": i * PROFILE["interval_s"], "http_status": code, "submit_ms": round((time.monotonic() - start) * 1000),
               "request_id": (body or {}).get("id"), "submitted_at": None, "stored_at": None, "state": None,
               "not_triaged_reason": None, "suggested_category": None, "suggested_priority": None, "e2e_ms": None,
               "worker_ms": None, "first_seen_ms": None, "not_triaged_seen_ms": None, "out_of_order_polls": 0,
               "_sent": start}
        rows.append(row)
        if row["request_id"]:
            sent[row["request_id"]] = row
            order.append(row["request_id"])
        print(f"{phase} {case['id']} {code} t={time.monotonic() - t0:.0f}s", flush=True)
    wait_until(time.monotonic() + PROFILE["deadline_s"] + PROFILE["sc5_grace_s"] + 4)

    # One snapshot after every deadline (spec D3)
    for request_id, submitted, stored, state, category, priority, reason, e2e in stack.sql(
            "SELECT r.id, r.submitted_at, t.updated_at, t.state, coalesce(t.suggested_category, ''), "
            "coalesce(t.suggested_priority, ''), coalesce(t.not_triaged_reason, ''), "
            "round(extract(epoch FROM t.updated_at - r.submitted_at) * 1000) "
            f"FROM help_request r JOIN triage_suggestion t ON t.request_id = r.id WHERE r.event_id = '{event}'"):
        if request_id in sent:
            sent[request_id].update(submitted_at=submitted, stored_at=stored, state=state,
                                    suggested_category=category or None, suggested_priority=priority or None,
                                    not_triaged_reason=reason or None, e2e_ms=int(float(e2e)))
    for line in stack.compose("logs", "--no-log-prefix", "reports", timeout=300).splitlines():
        match = LOG_LINE.search(line)
        if match and match.group(1) in sent:
            sent[match.group(1)]["worker_ms"] = int(match.group(2))
    for row in rows:
        del row["_sent"]
        if row["state"] == "Suggested" or row["not_triaged_reason"] is not None:
            continue
        row["e2e_ms"] = None  # nothing stored: the pending row's timestamp is the submission's, not an outcome

    out.mkdir(parents=True, exist_ok=True)
    (out / f"{phase}-requests.jsonl").write_text("".join(json.dumps(r) + "\n" for r in rows), encoding="utf-8")
    (out / f"{phase}-status.jsonl").write_text("".join(json.dumps(s) + "\n" for s in status_log), encoding="utf-8")
    summary = summarise(rows)
    (out / f"{phase}-summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))
    return 0


# ---------------------------------------------------------------- verify


def verify(run_dir: Path) -> None:
    manifest = json.loads((run_dir / "manifest.json").read_text(encoding="utf-8"))
    check(manifest.get("profile") == PROFILE and {"sc3", "sc5"} <= set(manifest.get("pass_rules", {})),
          "234 AC-1 the manifest pre-registers the profile and the pass rules")
    check(manifest.get("package_sha256", {}).get(CASES) == sha256(HERE / CASES)
          and re.fullmatch(r"[0-9a-f]{40}", manifest.get("driver_commit", "")) is not None,
          "234 AC-1 the manifest hashes the case file and names the driver commit")
    ids = [c["id"] for c in load_cases()]
    for phase, recorded in manifest.get("results", {}).items():
        rows = read_jsonl(run_dir / f"{phase}-requests.jsonl")
        check([r["case"] for r in rows] == ids, f"234 AC-2 the rows hold one line per holdout case ({phase})")
        summary = json.loads((run_dir / f"{phase}-summary.json").read_text(encoding="utf-8"))
        check(summary == summarise(rows) and recorded.get("summary") == summary,
              f"234 AC-2 a summary is recomputed from its rows ({phase})")


# ---------------------------------------------------------------- self-test


def row(case: str, **values) -> dict:
    base = {"case": case, "abstain_expected": False, "http_status": 201, "state": "Suggested",
            "suggested_category": "Health", "suggested_priority": "High", "not_triaged_reason": None,
            "e2e_ms": 12000, "worker_ms": 11000, "first_seen_ms": 500, "not_triaged_seen_ms": None,
            "out_of_order_polls": 0}
    base.update(values)
    return base


def self_test() -> None:
    late = summarise([row("A"), row("B", e2e_ms=30001), row("C", http_status=400, state=None, e2e_ms=None)])
    check(late["sc3"]["valid_on_time_non_abstention"] == 1 and late["outcomes"].get("NotCreated") == 1,
          "234 AC-2 valid within 30 s counts only stored suggestions")
    misses = summarise([row("A"), row("B", state="Pending", not_triaged_reason="Abstained"),
                        row("C", state="Pending", e2e_ms=None), row("D", abstain_expected=True, state="Pending",
                                                                     not_triaged_reason="Abstained")])
    check(misses["sc3"]["valid_on_time_non_abstention"] == 1 and misses["sc3"]["non_abstention_cases"] == 3
          and misses["outcomes"] == {"Abstained": 2, "Suggested": 1, "Timeout": 1}
          and misses["sc3"]["valid_on_time_all"] == 1 and misses["sc3"]["expected_abstentions_abstained"] == 1
          and not misses["sc3"]["pass"],
          "234 AC-2 an abstention and a request still pending count as misses")
    times = summarise([row(str(i), e2e_ms=ms) for i, ms in enumerate(range(1000, 21000, 1000))])
    check(times["sc3"]["suggestion_e2e_ms"] == {"n": 20, "p50": 10500.0, "p95_nearest_rank": 19000, "max": 20000}
          and times["sc3"]["pass"], "234 AC-2 percentiles are nearest rank")
    fallback = {"state": "Pending", "not_triaged_reason": "ProviderError", "suggested_category": None,
                "suggested_priority": None, "not_triaged_seen_ms": 1500}
    ok = [row(str(i), **fallback) for i in range(3)]
    check(summarise(ok)["sc5"]["pass"]
          and not summarise([*ok, row("x", **{**fallback, "not_triaged_seen_ms": 32001})])["sc5"]["pass"]
          and not summarise([*ok, row("y", **{**fallback, "first_seen_ms": None})])["sc5"]["pass"]
          and not summarise([*ok, row("z", **{**fallback, "out_of_order_polls": 1})])["sc5"]["pass"]
          and not summarise([*ok, row("w", **{**fallback, "not_triaged_seen_ms": None})])["sc5"]["pass"],
          "234 AC-2 SC5 passes only when every request is shown and NotTriaged in time")
    # --verify on a temporary run directory, complete and then with one thing changed at a time
    rows = [row(c["id"]) for c in load_cases()]
    summary = summarise(rows)
    manifest = {"profile": PROFILE, "pass_rules": {"sc3": "", "sc5": ""}, "driver_commit": "0" * 40,
                "package_sha256": {CASES: sha256(HERE / CASES)}, "results": {"sc3": {"summary": summary}}}

    def failed(manifest_: dict, rows_: list[dict], summary_: dict) -> set[str]:
        with tempfile.TemporaryDirectory() as tmp:
            run_dir = Path(tmp)
            (run_dir / "manifest.json").write_text(json.dumps(manifest_), encoding="utf-8")
            (run_dir / "sc3-requests.jsonl").write_text("".join(json.dumps(r) + "\n" for r in rows_), encoding="utf-8")
            (run_dir / "sc3-summary.json").write_text(json.dumps(summary_), encoding="utf-8")
            before = len(failures)
            with contextlib.redirect_stdout(io.StringIO()):
                verify(run_dir)
            found = set(failures[before:])
            del failures[before:]
            return found

    summary_check, rows_check = "234 AC-2 a summary is recomputed from its rows (sc3)", \
        "234 AC-2 the rows hold one line per holdout case (sc3)"
    check(failed(manifest, rows, summary) == set()
          and failed(manifest, rows, {**summary, "requests": 1}) == {summary_check},
          "234 AC-2 a summary is recomputed from its rows")
    check(failed(manifest, rows[1:], summary) == {rows_check, summary_check},
          "234 AC-2 the rows hold one line per holdout case")
    check(failed({**manifest, "profile": {**PROFILE, "interval_s": 1}}, rows, summary)
          == {"234 AC-1 the manifest pre-registers the profile and the pass rules"},
          "234 AC-1 the manifest pre-registers the profile and the pass rules")
    hashes = "234 AC-1 the manifest hashes the case file and names the driver commit"
    check(failed({**manifest, "package_sha256": {CASES: "0" * 64}}, rows, summary) == {hashes}
          and failed({**manifest, "driver_commit": ""}, rows, summary) == {hashes}, hashes)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run", choices=["sc3", "sc5"])
    parser.add_argument("--out", type=Path)
    parser.add_argument("--env", default=str(ROOT / "infra" / ".env.example"))
    parser.add_argument("--project", default="farol-234")
    parser.add_argument("--verify", type=Path, metavar="RUN_DIR")
    parser.add_argument("--trial", action="store_true", help="with --run: three synthetic texts instead of the holdout")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.run:
        return run(args.run, args.out, args.env, args.project, args.trial)
    if args.verify:
        verify(args.verify)
    if args.self_test:
        self_test()
    if not (args.verify or args.self_test):
        parser.error("give --run, --verify or --self-test")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

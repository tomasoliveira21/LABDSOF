"""#148 evidence drill against a running stack with --profile observability. Writes JSON snapshots to OUT.

usage: python -I drill.py <env-file> <compose-project> <out-dir>
"""
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ENV_PATH, PROJECT, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
REPO = os.getcwd()
sys.path.insert(0, os.path.join(REPO, "infra", "tests"))
import identity_smoke as idp  # noqa: E402

env = idp.read_env(ENV_PATH)
GRAFANA = "http://127.0.0.1:" + env.get("GRAFANA_PORT", "3001")
PROM = "http://127.0.0.1:" + env.get("PROMETHEUS_PORT", "9090")
GATEWAY = "http://127.0.0.1:" + env.get("GATEWAY_PORT", "8080")
RABBIT = "http://127.0.0.1:" + env.get("RABBITMQ_UI_PORT", "15672")
GAUTH = "Basic " + base64.b64encode(f"{env['GRAFANA_ADMIN_USER']}:{env['GRAFANA_ADMIN_PASSWORD']}".encode()).decode()
RAUTH = "Basic " + base64.b64encode(f"{env['RABBITMQ_ADMIN_USER']}:{env['RABBITMQ_ADMIN_PASSWORD']}".encode()).decode()
COMPOSE = ["docker", "compose", "-p", PROJECT, "-f", "infra/compose.yaml", "--env-file", ENV_PATH,
           "--profile", "observability"]
os.makedirs(OUT, exist_ok=True)
timeline = []


def now():
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def note(text):
    line = f"{now()} {text}"
    print(line, flush=True)
    timeline.append(line)


def http(url, data=None, headers=None, method=None):
    body = json.dumps(data).encode() if data is not None else None
    h = {"Content-Type": "application/json", **(headers or {})}
    try:
        with urllib.request.urlopen(urllib.request.Request(url, body, h, method=method), timeout=20) as r:
            return r.status, dict(r.headers), r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode()
    except OSError as e:
        return 0, {}, str(e)


def save(name, obj):
    with open(os.path.join(OUT, name), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"capturedAt": now(), **obj}, f, indent=2)
        f.write("\n")


def rules():
    """Grafana-managed rule states: {title: (state, health, [(service, instance state)])}."""
    s, _, b = http(GRAFANA + "/api/prometheus/grafana/api/v1/rules", headers={"Authorization": GAUTH})
    out = {}
    for g in json.loads(b)["data"]["groups"] if s == 200 else []:
        for r in g["rules"]:
            out[r["name"]] = {"state": r["state"], "health": r.get("health"),
                              "instances": sorted((a["labels"].get("service", a["labels"].get("alertname", "")),
                                                   a["state"]) for a in r.get("alerts", []))}
    return out


def query(expr):
    q = {"queries": [{"refId": "A", "datasource": {"uid": "prometheus"}, "expr": expr, "instant": True}],
         "from": "now-5m", "to": "now"}
    s, _, b = http(GRAFANA + "/api/ds/query", q, {"Authorization": GAUTH})
    if s != 200:
        return {"status": s, "body": b[:500]}
    frames = json.loads(b)["results"]["A"].get("frames", [])
    return [{"labels": (f["schema"]["fields"][1].get("labels") or {}), "value": f["data"]["values"][1][-1]}
            for f in frames if len(f["schema"]["fields"]) > 1]


def snapshot(name, why):
    note(f"snapshot {name}: {why}")
    save(name, {"why": why, "alertRules": rules(),
                "dashboard": {e: query(e) for e in ("up", "farol_outbox_oldest_pending_seconds",
                                                    "farol_triage_window_eligible")}})


def wait_rule(title, predicate, what, limit=420):
    start = time.time()
    while time.time() - start < limit:
        r = rules().get(title)
        if r and predicate(r):
            note(f"{title}: {what} after {int(time.time() - start)} s -> {r}")
            return True
        time.sleep(10)
    note(f"{title}: NOT {what} within {limit} s -> {rules().get(title)}")
    return False


def compose(*args):
    subprocess.run(COMPOSE + list(args), check=True, capture_output=True)


def token(email):
    base = f"http://{env.get('PUBLIC_HOST', 'localhost')}:{env.get('KEYCLOAK_PORT', '8180')}"
    redirect_uri = f"http://{env.get('PUBLIC_HOST', 'localhost')}:{env.get('WEB_PORT', '3000')}/callback"
    verifier, challenge = idp.pkce_pair()
    _, location = idp.log_in(base, redirect_uri, email, env["FAROL_DEMO_PASSWORD"], challenge)
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location or "").query).get("code", [""])[0]
    _, _, body = idp.request(urllib.request.build_opener(idp.NoRedirect),
                             f"{base}/realms/{idp.REALM}/protocol/openid-connect/token",
                             {"grant_type": "authorization_code", "client_id": idp.WEB_CLIENT, "code": code,
                              "redirect_uri": redirect_uri, "code_verifier": verifier})
    return json.loads(body)["access_token"]


def correlated_help_request():
    """A3: one help request followed by its correlation id: gateway -> Reports -> outbox -> broker."""
    tok = token("rita@farol.test")
    sub = json.loads(base64.urlsafe_b64decode(tok.split(".")[1] + "==="))["sub"]
    event = str(uuid.uuid4())
    # Synthetic fixture: the membership copy US-06 will deliver from Auth & Users does not exist yet
    psql = (f"INSERT INTO membership_copy VALUES ('{event}', '{sub}', 'RegularUser', 'Active', '{{}}', 1)")
    subprocess.run(COMPOSE + ["exec", "-T", "-e", f"PGPASSWORD={env['REPORTS_DB_PASSWORD']}", "postgres", "psql",
                              "-h", "postgres", "-U", "reports", "-d", "reports", "-c", psql],
                   check=True, capture_output=True)
    cid = str(uuid.uuid4())
    body = {"clientRequestId": str(uuid.uuid4()), "location": {"mapVersion": 1, "x": 0.5, "y": 0.5},
            "description": "Synthetic drill request"}
    s, h, b = http(f"{GATEWAY}/api/v1/events/{event}/help-requests", body,
                   {"Authorization": f"Bearer {tok}", "X-Correlation-Id": cid})
    note(f"A3 POST help request with X-Correlation-Id {cid}: {s}, response header {h.get('X-Correlation-Id')}")
    time.sleep(3)
    logs = {}
    for service in ("gateway", "reports"):
        out = subprocess.run(COMPOSE + ["logs", "--no-color", "--no-log-prefix", service],
                             capture_output=True, text=True).stdout
        logs[service] = [json.loads(line) for line in out.splitlines() if cid in line and line.startswith("{")]
    s2, _, b2 = http(f"{RABBIT}/api/queues/%2F/reports.reports-events/get",
                     {"count": 50, "ackmode": "ack_requeue_true", "encoding": "auto"}, {"Authorization": RAUTH})
    broker = [{"properties": m["properties"], "routing_key": m["routing_key"]}
              for m in (json.loads(b2) if s2 == 200 else []) if m["properties"].get("correlation_id") == cid]
    save("a3-correlated-help-request.json", {
        "correlationId": cid, "request": {"status": s, "responseHeader": h.get("X-Correlation-Id"),
                                          "requestId": json.loads(b).get("id") if s == 201 else None},
        "logLines": logs, "brokerMessages": broker,
        "note": "Synthetic membership row inserted into Reports' membership_copy for the drill (US-06 not built)."})


def main():
    note("drill start")
    snapshot("01-all-up.json", "stack healthy with --profile observability")
    correlated_help_request()

    note("A5 stop reports")
    compose("stop", "reports")
    ok = wait_rule("Service or telemetry down", lambda r: r["state"] == "firing", "firing")
    snapshot("02-reports-stopped-firing.json", f"reports stopped; firing={ok}")
    note("A5 start reports")
    compose("start", "reports")
    ok = wait_rule("Service or telemetry down", lambda r: r["state"] == "inactive", "normal again")
    snapshot("03-reports-started-normal.json", f"reports started; normal={ok}")

    note("missing telemetry: stop prometheus")
    compose("stop", "prometheus")
    ok = wait_rule("Service or telemetry down", lambda r: r["state"] == "firing", "firing (query error)")
    snapshot("04-prometheus-stopped.json", f"prometheus stopped; firing={ok}")
    compose("start", "prometheus")
    ok = wait_rule("Service or telemetry down", lambda r: r["state"] == "inactive", "normal again")
    snapshot("05-prometheus-started.json", f"prometheus started; normal={ok}")

    stats = subprocess.run(["docker", "stats", "--no-stream", "--format",
                            "{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"] +
                           subprocess.run(COMPOSE + ["ps", "-q"], capture_output=True, text=True).stdout.split(),
                           capture_output=True, text=True).stdout
    note("docker stats captured")
    with open(os.path.join(OUT, "docker-stats.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write(f"# {now()} docker stats --no-stream, stack with --profile observability\n{stats}")
    with open(os.path.join(OUT, "timeline.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(timeline) + "\n")


main()

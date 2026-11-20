"""Smoke test of local observability (#148): run after `docker compose --profile observability up --wait`.

Checks that Prometheus scrapes the services and RabbitMQ, that Grafana is healthy with the Farol dashboard and the
three core alert rules provisioned, and that one request carries the same X-Correlation-Id through the gateway and
Reports into their JSON logs.

Standard library only. Values come from the Compose env file; the compose project is optional:
    python3 -I infra/tests/observability_smoke.py infra/.env.example [compose-project]
Exit code 0 when every check passes; each failure is printed.
"""

from __future__ import annotations

import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

COMPOSE_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "compose.yaml")
# Watched by the service-down alert; Auth & Users, Transports and Badges are listed as hand-offs and may be down
EXPECTED_UP = {"gateway", "reports", "events", "rabbitmq"}
ALERT_RULES = {"farol-service-down", "farol-outbox-stalled", "farol-triage-below-target"}
failures = 0


def check(ok: bool, what: str) -> None:
    global failures
    print(("PASS " if ok else "FAIL ") + what)
    failures += 0 if ok else 1


def read_env(path: str) -> dict[str, str]:
    env = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                env[key] = value
    return env


def get(url: str, headers: dict[str, str] | None = None) -> tuple[int, dict[str, str], str]:
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers or {}), timeout=10) as r:
            return r.status, dict(r.headers), r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode()
    except OSError as e:
        return 0, {}, str(e)


def scraped_up(prometheus: str) -> set[str]:
    status, _, body = get(prometheus + "/api/v1/query?query=up")
    if status != 200:
        return set()
    return {r["metric"].get("service", "") for r in json.loads(body)["data"]["result"] if r["value"][1] == "1"}


def logged_with(env_path: str, project: str | None, service: str, correlation_id: str) -> bool:
    """True when the service logged a JSON line whose correlationId field is this id."""
    argv = ["docker", "compose", *(["-p", project] if project else []), "-f", COMPOSE_FILE, "--env-file", env_path,
            "logs", "--no-color", "--no-log-prefix", service]
    out = subprocess.run(argv, capture_output=True, text=True, check=False).stdout
    for line in out.splitlines():
        if correlation_id in line:
            try:
                if json.loads(line).get("correlationId") == correlation_id:
                    return True
            except json.JSONDecodeError:
                pass
    return False


def main(env_path: str, project: str | None = None) -> int:
    env = read_env(env_path)
    prometheus = f"http://127.0.0.1:{env.get('PROMETHEUS_PORT', '9090')}"
    grafana = f"http://127.0.0.1:{env.get('GRAFANA_PORT', '3001')}"
    gateway = f"http://127.0.0.1:{env.get('GATEWAY_PORT', '8080')}"

    # The first scrape comes up to 15 s after start-up
    up: set[str] = set()
    for _ in range(12):
        up = scraped_up(prometheus)
        if EXPECTED_UP <= up:
            break
        time.sleep(5)
    check(EXPECTED_UP <= up, f"Prometheus scrapes {sorted(EXPECTED_UP)} (up: {sorted(up)})")

    status, _, body = get(grafana + "/api/health")
    check(status == 200 and json.loads(body or "{}").get("database") == "ok", f"Grafana is healthy ({status})")

    credentials = f"{env.get('GRAFANA_ADMIN_USER', '')}:{env.get('GRAFANA_ADMIN_PASSWORD', '')}"
    auth = {"Authorization": "Basic " + base64.b64encode(credentials.encode()).decode()}
    status, _, _ = get(grafana + "/api/dashboards/uid/farol-local", auth)
    check(status == 200, f"the Farol dashboard is provisioned ({status})")
    status, _, body = get(grafana + "/api/v1/provisioning/alert-rules", auth)
    rules = {r["uid"] for r in json.loads(body)} if status == 200 else set()
    check(ALERT_RULES <= rules, f"the three core alert rules are provisioned ({sorted(rules)})")

    # Without a token: Reports refuses it (401) and still logs it, with the id (the status check is not logged)
    path = f"/api/v1/events/{uuid.uuid4()}/help-requests"
    correlation_id = str(uuid.uuid4())
    status, headers, _ = get(gateway + path, {"X-Correlation-Id": correlation_id})
    echoed = {k.lower(): v for k, v in headers.items()}.get("x-correlation-id")
    check(status == 401 and echoed == correlation_id, f"the gateway returns the caller's X-Correlation-Id ({echoed})")
    status, headers, _ = get(gateway + path,{"X-Correlation-Id": "bad id\twith tab" + "x" * 80})
    replaced = {k.lower(): v for k, v in headers.items()}.get("x-correlation-id", "")
    check(len(replaced) == 36 and "bad" not in replaced, f"the gateway replaces an invalid X-Correlation-Id ({replaced})")
    time.sleep(1)
    for service in ("gateway", "reports"):
        check(logged_with(env_path, project, service, correlation_id),
              f"{service} logs a JSON line with correlationId {correlation_id}")

    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "infra/.env.example",
                  sys.argv[2] if len(sys.argv) > 2 else None))

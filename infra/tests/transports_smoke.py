"""Smoke test of Transports in the running stack (#193): run after `docker compose up --wait`.

Checks, through the API gateway, that Transports validates real Keycloak tokens (ADR-006): no token and a tampered
token get 401 Problem Details; Rita's real token is accepted, and both operations of US-41 reach Transports' access
check: reading the buses and adding a route answer 404 for an event Rita is not a member of (US-41 D14; no event can be
created through the gateway before US-06, so the full path is in the integration tests); a malformed event id gets
400; the X-Correlation-Id comes back. Then it checks inside the stack that the
`transports` schema comes from Flyway, that the scheduled job has read the recorded buses through WireMock and
recorded a successful import (spec D5, D8), that the buses of that import are stored (US-40), and that
Transports consumes both of its queues as the `transports` broker user (spec D3).

The smoke writes nothing itself: the only rows are the job's own import records, so a rerun gives the same result.

Standard library only; reuses the PKCE login of identity_smoke.py and `docker compose exec` for psql and
rabbitmqctl. Values come from the Compose env file; the compose project is optional:
    python3 -I infra/tests/transports_smoke.py infra/.env.example [compose-project]
Exit code 0 when every check passes; each failure is printed.
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import events_smoke as smoke  # noqa: E402 - path set above (python -I leaves the script's folder out)
import identity_smoke as idp  # noqa: E402

DEADLINE = 60  # seconds; the job reads at start-up and every 30 s, the listeners retry every 5 s
RITA = "rita@farol.test"
QUEUES = ("transports.auth-users-events", "transports.events-events")
NOT_FOUND = "Event not found."


def psql(env_path: str, env: dict[str, str], project: str | None, sql: str) -> tuple[bool, str]:
    """Runs one statement in the transports database as its own user, over TCP like the isolation check of CI."""
    return smoke.exec_in(env_path, project, "-e", f"PGPASSWORD={env['TRANSPORTS_DB_PASSWORD']}", "postgres", "psql",
                         "-h", "postgres", "-U", "transports", "-d", "transports", "-v", "ON_ERROR_STOP=1", "-tAc",
                         sql)


def post_json(gateway: str, path: str, token: str, payload: dict) -> tuple[int, dict[str, str], str]:
    """A JSON POST through the gateway (the shared helper sends form data); nothing is stored past the 404."""
    request = urllib.request.Request(gateway + path, data=json.dumps(payload).encode(), method="POST",
                                     headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
    try:
        response = urllib.request.build_opener(idp.NoRedirect).open(request, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, {k.lower(): v for k, v in response.headers.items()}, response.read().decode()


def latest_import(env_path: str, env: dict[str, str], project: str | None) -> tuple[bool, str]:
    """True when the latest read of the source succeeded with at least one bus."""
    ok, out = psql(env_path, env, project,
                   "SELECT outcome || '|' || coalesce(vehicle_count::text, '') || '|' || coalesce(error, '') "
                   "FROM source_import ORDER BY import_id DESC LIMIT 1")
    if not ok:
        return False, out
    outcome, _, rest = out.partition("|")
    count, _, error = rest.partition("|")
    return outcome == "Succeeded" and count.isdigit() and int(count) > 0, out or "no import yet"


def buses_in_use(env_path: str, env: dict[str, str], project: str | None) -> tuple[bool, str]:
    """True when the last valid import stored its buses (US-40): positions in use, from that import."""
    ok, out = psql(env_path, env, project,
                   "SELECT count(*) FROM bus_observation WHERE retrieved_at = "
                   "(SELECT max(finished_at) FROM source_import WHERE outcome = 'Succeeded')")
    return ok and out.isdigit() and int(out) > 0, out


def consuming(env_path: str, project: str | None) -> tuple[bool, str]:
    """True when both queues have a consumer and the `transports` broker user is connected."""
    ok, queues = smoke.exec_in(env_path, project, "rabbitmq", "rabbitmqctl", "-q", "list_queues", "name", "consumers")
    if not ok:
        return False, queues
    counts: dict[str, int] = {}
    for line in queues.splitlines():
        name, _, count = line.partition("\t")
        if count.strip().isdigit():
            counts[name.strip()] = int(count.strip())
    ok, connections = smoke.exec_in(env_path, project, "rabbitmq", "rabbitmqctl", "-q", "list_connections", "user")
    if not ok:
        return False, connections
    users = {line.strip() for line in connections.splitlines() if line.strip()}
    return (all(counts.get(queue, 0) >= 1 for queue in QUEUES) and "transports" in users,
            f"consumers {[counts.get(queue, 0) for queue in QUEUES]}, connection users {sorted(users)}")


def wait_for(check) -> tuple[bool, str]:
    deadline = time.monotonic() + DEADLINE
    result = check()
    while not result[0] and time.monotonic() < deadline:
        time.sleep(1)
        result = check()
    return result


def main(env_path: str, project: str | None = None) -> int:
    env = idp.read_env(env_path)
    gateway = f"http://127.0.0.1:{env.get('GATEWAY_PORT', '8080')}"

    rita = smoke.token_for(env, RITA)
    idp.check(bool(rita), "Keycloak gives Rita an access token")
    if not rita:
        for failure in idp.failures:
            print(f"::error::{failure}")
        return 1

    buses = f"/api/v1/events/{uuid.uuid4()}/bus-observations"
    status, headers, body = smoke.call(gateway, buses)
    idp.check(smoke.is_problem(status, headers, body, 401) and headers.get("www-authenticate", "").startswith("Bearer"),
              "GET /api/v1/events/{id}/bus-observations without a token gets 401 Problem Details with "
              "WWW-Authenticate: Bearer" + ("" if status == 401 else f" (got {status})"))
    status, headers, body = smoke.call(gateway, buses, smoke.tampered(rita))
    idp.check(smoke.is_problem(status, headers, body, 401),
              "GET /api/v1/events/{id}/bus-observations with a tampered token gets 401 Problem Details"
              + ("" if status == 401 else f" (got {status})"))

    correlation = f"smoke-{uuid.uuid4()}"
    status, headers, body = smoke.call(gateway, buses, rita, correlation)
    idp.check(smoke.is_problem(status, headers, body, 404) and smoke.problem(body).get("detail") == NOT_FOUND,
              "GET /api/v1/events/{id}/bus-observations with Rita's Keycloak token reaches the access check: 404 for "
              "an event she is not a member of (token accepted, US-41)" + ("" if status == 404 else f" (got {status})"))
    idp.check(headers.get("x-correlation-id") == correlation,
              "the X-Correlation-Id sent through the gateway comes back")

    route = f"/api/v1/events/{uuid.uuid4()}/transport-options"
    status, headers, body = post_json(gateway, route, rita, {"mode": "ExternalBus", "externalRoute": "500"})
    idp.check(smoke.is_problem(status, headers, body, 404) and smoke.problem(body).get("detail") == NOT_FOUND,
              "POST /api/v1/events/{id}/transport-options with Rita's Keycloak token reaches the access check: 404 "
              "for an event she is not a member of (US-41)" + ("" if status == 404 else f" (got {status})"))

    status, headers, body = smoke.call(gateway, "/api/v1/events/not-a-uuid/bus-observations", rita)
    idp.check(smoke.is_problem(status, headers, body, 400),
              "GET /api/v1/events/not-a-uuid/bus-observations with a valid token gets 400 Problem Details"
              + ("" if status == 400 else f" (got {status})"))

    ok, out = psql(env_path, env, project,
                   "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success")
    idp.check(ok and out == "1", "the transports schema comes from Flyway (V1 applied)"
              + ("" if ok and out == "1" else f" (got {out})"))

    ok, detail = wait_for(lambda: latest_import(env_path, env, project))
    idp.check(ok, "the scheduled job read the recorded buses through WireMock and recorded Succeeded"
              + ("" if ok else f" (latest: {detail})"))

    ok, detail = wait_for(lambda: buses_in_use(env_path, env, project))
    idp.check(ok, "the buses of the last import are stored and in use (US-40)"
              + ("" if ok else f" (got {detail})"))

    ok, detail = wait_for(lambda: consuming(env_path, project))
    idp.check(ok, "Transports consumes both of its queues as the transports broker user"
              + ("" if ok else f" (got {detail})"))

    for failure in idp.failures:
        print(f"::error::{failure}")
    return 1 if idp.failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "infra/.env.example",
                  sys.argv[2] if len(sys.argv) > 2 else None))

"""Smoke test of Events in the running stack (#192): run after `docker compose up --wait`.

Checks, through the API gateway, that Events validates real Keycloak tokens (ADR-006) and hides events from users
who are not members: no token and a tampered token get 401 Problem Details; Rita's real token is accepted and gets
404 for an event she is not a member of, and so does the bootstrap FarolAdmin, who is not a member either (same
problem), including on the teams of US-16, the publish of US-11 and the map upload of US-19; a malformed id gets
400; an operation of a later story gets 501; a map upload without its file part is refused; the X-Correlation-Id
comes back. Then it checks inside the stack that the `events` schema comes from Flyway, that the map directory of
US-19 is writable by the service's user, and that Events consumes its queue as the `events` broker user.

The smoke writes nothing of its own (the map probe's file is removed again): no use case creates an event before
US-06, so every event id is unknown, the team
operations of US-16 and US-17, the publish of US-11 and the map upload of US-19 cannot be reached past that 404,
and a rerun gives the same result.

Standard library only; reuses the PKCE login of identity_smoke.py and `docker compose exec` for psql and
rabbitmqctl. Values come from the Compose env file; the compose project is optional:
    python3 -I infra/tests/events_smoke.py infra/.env.example [compose-project]
Exit code 0 when every check passes; each failure is printed.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import identity_smoke as idp  # noqa: E402 - path set above (python -I leaves the script's folder out)

COMPOSE_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "compose.yaml")
CONSUMER_DEADLINE = 60  # seconds; the listener retries every 5 s until rabbitmq-setup has created its user and queue
RITA = "rita@farol.test"
QUEUE = "events.auth-users-events"
NOT_FOUND = "Event not found."


def token_for(env: dict[str, str], email: str) -> str | None:
    host = env.get("PUBLIC_HOST", "localhost")
    base = f"http://{host}:{env.get('KEYCLOAK_PORT', '8180')}"
    redirect_uri = f"http://{host}:{env.get('WEB_PORT', '3000')}/callback"
    token_url = f"{base}/realms/{idp.REALM}/protocol/openid-connect/token"
    verifier, challenge = idp.pkce_pair()
    _, location = idp.log_in(base, redirect_uri, email, env["FAROL_DEMO_PASSWORD"], challenge)
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location or "").query).get("code", [""])[0]
    status, _, body = idp.request(urllib.request.build_opener(idp.NoRedirect), token_url,
                                  {"grant_type": "authorization_code", "client_id": idp.WEB_CLIENT, "code": code,
                                   "redirect_uri": redirect_uri, "code_verifier": verifier})
    return json.loads(body).get("access_token") if status == 200 else None


def call(gateway: str, path: str, token: str | None = None, correlation_id: str | None = None,
         post: bool = False) -> tuple[int, dict[str, str], str]:
    # No Accept header on purpose: with `Accept: application/json` Spring writes a Problem Details as application/json.
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if correlation_id:
        headers["X-Correlation-Id"] = correlation_id
    status, response_headers, body = idp.request(urllib.request.build_opener(idp.NoRedirect), gateway + path,
                                                 {} if post else None, headers)
    return status, {name.lower(): value for name, value in response_headers.items()}, body


def upload(gateway: str, path: str, token: str | None = None) -> tuple[int, dict[str, str], str]:
    """A multipart/form-data POST with one small PNG part, as the map upload of US-19 takes it."""
    boundary = "farol-smoke-boundary"
    png = bytes.fromhex("89504E470D0A1A0A") + b"only the signature: the refusals here come first"
    lines = [f"--{boundary}",
             'Content-Disposition: form-data; name="file"; filename="map.png"',
             "Content-Type: image/png",
             ""]
    body = ("\r\n".join(lines) + "\r\n").encode() + png + f"\r\n--{boundary}--\r\n".encode()
    headers = {"Content-Type": f"multipart/form-data; boundary={boundary}", "Content-Length": str(len(body))}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(gateway + path, data=body, headers=headers, method="POST")
    try:
        response = urllib.request.build_opener(idp.NoRedirect).open(request, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        status = response.status if hasattr(response, "status") else response.code
        return (status, {name.lower(): value for name, value in dict(response.headers).items()},
                response.read().decode("utf-8", "replace"))


def is_problem(status: int, headers: dict[str, str], body: str, expected: int) -> bool:
    if status != expected or not headers.get("content-type", "").startswith("application/problem+json"):
        return False
    try:
        return json.loads(body).get("status") == expected
    except (ValueError, AttributeError):
        return False


def tampered(token: str) -> str:
    return token[:-4] + ("AAAA" if not token.endswith("AAAA") else "BBBB")


def problem(body: str) -> dict:
    try:
        data = json.loads(body)
    except ValueError:
        return {}
    return data if isinstance(data, dict) else {}


def same(p: dict) -> dict:
    """What two 404s must share; `instance` carries the path, so it differs."""
    return {key: p.get(key) for key in ("type", "title", "status", "detail")}


def exec_in(env_path: str, project: str | None, *args: str) -> tuple[bool, str]:
    """Runs `docker compose exec -T` with an argument list (no shell)."""
    argv = ["docker", "compose", *(["-p", project] if project else []), "-f", COMPOSE_FILE, "--env-file", env_path,
            "exec", "-T", *args]
    try:
        run = subprocess.run(argv, capture_output=True, text=True, timeout=60, check=False)
    except (OSError, subprocess.SubprocessError) as error:
        return False, str(error)[-300:]
    ok = run.returncode == 0
    return ok, run.stdout.strip() if ok else run.stderr.strip()[-300:]


def psql(env_path: str, env: dict[str, str], project: str | None, sql: str) -> tuple[bool, str]:
    """Runs one statement in the events database as its own user, over TCP like the isolation check of CI."""
    return exec_in(env_path, project, "-e", f"PGPASSWORD={env['EVENTS_DB_PASSWORD']}", "postgres", "psql", "-h",
                   "postgres", "-U", "events", "-d", "events", "-v", "ON_ERROR_STOP=1", "-tAc", sql)


def consuming(env_path: str, project: str | None) -> tuple[bool, str]:
    """True when QUEUE has a consumer and the `events` broker user is connected."""
    ok, queues = exec_in(env_path, project, "rabbitmq", "rabbitmqctl", "-q", "list_queues", "name", "consumers")
    if not ok:
        return False, queues
    counts: dict[str, int] = {}
    for line in queues.splitlines():
        name, _, count = line.partition("\t")
        if count.strip().isdigit():
            counts[name.strip()] = int(count.strip())
    ok, connections = exec_in(env_path, project, "rabbitmq", "rabbitmqctl", "-q", "list_connections", "user")
    if not ok:
        return False, connections
    users = {line.strip() for line in connections.splitlines() if line.strip()}
    return (counts.get(QUEUE, 0) >= 1 and "events" in users,
            f"consumers {counts.get(QUEUE, 0)}, connection users {sorted(users)}")


def wait_consuming(env_path: str, project: str | None) -> tuple[bool, str]:
    deadline = time.monotonic() + CONSUMER_DEADLINE
    result = consuming(env_path, project)
    while not result[0] and time.monotonic() < deadline:
        time.sleep(1)
        result = consuming(env_path, project)
    return result


def main(env_path: str, project: str | None = None) -> int:
    env = idp.read_env(env_path)
    gateway = f"http://127.0.0.1:{env.get('GATEWAY_PORT', '8080')}"
    bootstrap = env["FAROL_BOOTSTRAP_FAROL_ADMIN_EMAIL"].strip().lower()

    rita = token_for(env, RITA)
    idp.check(bool(rita), "Keycloak gives Rita an access token")
    daniel = token_for(env, bootstrap)
    idp.check(bool(daniel), "Keycloak gives the bootstrap FarolAdmin an access token")
    if not rita or not daniel:
        for failure in idp.failures:
            print(f"::error::{failure}")
        return 1

    unknown = f"/api/v1/events/{uuid.uuid4()}"
    status, headers, body = call(gateway, unknown)
    idp.check(is_problem(status, headers, body, 401) and headers.get("www-authenticate", "").startswith("Bearer"),
              "GET /api/v1/events/{id} without a token gets 401 Problem Details with WWW-Authenticate: Bearer"
              + ("" if status == 401 else f" (got {status})"))
    status, headers, body = call(gateway, unknown, tampered(rita))
    idp.check(is_problem(status, headers, body, 401),
              "GET /api/v1/events/{id} with a tampered token gets 401 Problem Details"
              + ("" if status == 401 else f" (got {status})"))

    correlation = f"smoke-{uuid.uuid4()}"
    status, headers, body = call(gateway, unknown, rita, correlation)
    seen = problem(body)
    idp.check(is_problem(status, headers, body, 404) and seen.get("detail") == NOT_FOUND,
              "GET /api/v1/events/{random id} with Rita's Keycloak token gets 404 Problem Details "
              "(token accepted, she is not a member)" + ("" if status == 404 else f" (got {status})"))
    idp.check(headers.get("x-correlation-id") == correlation,
              "the X-Correlation-Id sent through the gateway comes back")

    status, headers, body = call(gateway, f"/api/v1/events/{uuid.uuid4()}", daniel)
    idp.check(is_problem(status, headers, body, 404) and bool(seen) and same(problem(body)) == same(seen),
              "the bootstrap FarolAdmin, who is not a member, gets the same 404"
              + ("" if status == 404 else f" (got {status})"))

    status, headers, body = call(gateway, "/api/v1/events/not-a-uuid", rita)
    idp.check(is_problem(status, headers, body, 400),
              "GET /api/v1/events/not-a-uuid with a valid token gets 400 Problem Details"
              + ("" if status == 400 else f" (got {status})"))

    # US-16 and US-17 serve the teams of an event, so this path now hides an event Rita is not a member of
    status, headers, body = call(gateway, f"/api/v1/events/{uuid.uuid4()}/teams", rita)
    idp.check(is_problem(status, headers, body, 404) and problem(body).get("detail") == NOT_FOUND,
              "GET /api/v1/events/{id}/teams of an event Rita is not a member of gets the same 404 (US-16)"
              + ("" if status == 404 else f" (got {status})"))

    # US-11 publishes a draft, so this path now hides an event Rita is not a member of too; no event can be
    # created yet (US-06), so a publish that goes through is only reachable in the integration tests
    publish = f"/api/v1/events/{uuid.uuid4()}/publish"
    status, headers, body = call(gateway, publish, post=True)
    idp.check(is_problem(status, headers, body, 401) and headers.get("www-authenticate", "").startswith("Bearer"),
              "POST /api/v1/events/{id}/publish without a token gets 401 Problem Details with "
              "WWW-Authenticate: Bearer" + ("" if status == 401 else f" (got {status})"))

    status, headers, body = call(gateway, publish, rita, post=True)
    idp.check(is_problem(status, headers, body, 404) and problem(body).get("detail") == NOT_FOUND,
              "POST /api/v1/events/{id}/publish of an event Rita is not a member of gets the same 404 (US-11)"
              + ("" if status == 404 else f" (got {status})"))

    # US-19 uploads a map version; as the publish, it is only reachable on an event the caller is a member of
    maps = f"/api/v1/events/{uuid.uuid4()}/map-versions"
    status, headers, body = upload(gateway, maps)
    idp.check(is_problem(status, headers, body, 401) and headers.get("www-authenticate", "").startswith("Bearer"),
              "POST /api/v1/events/{id}/map-versions without a token gets 401 Problem Details with "
              "WWW-Authenticate: Bearer" + ("" if status == 401 else f" (got {status})"))

    status, headers, body = upload(gateway, maps, rita)
    idp.check(is_problem(status, headers, body, 404) and problem(body).get("detail") == NOT_FOUND,
              "POST /api/v1/events/{id}/map-versions of an event Rita is not a member of gets the same 404 "
              "(US-19)" + ("" if status == 404 else f" (got {status})"))

    status, headers, body = call(gateway, maps, rita, post=True)
    idp.check(status in (400, 415),
              "POST /api/v1/events/{id}/map-versions without a file part is refused before anything is stored "
              "(US-19)" + ("" if status in (400, 415) else f" (got {status})"))

    status, headers, body = call(gateway, f"/api/v1/events/{uuid.uuid4()}/map-versions/current", rita)
    idp.check(is_problem(status, headers, body, 501) and "US-21" in str(problem(body).get("detail", "")),
              "an operation of a later story (GET /api/v1/events/{id}/map-versions/current) gets 501 Problem "
              "Details naming its story" + ("" if status == 501 else f" (got {status})"))

    # The upload writes on the events-maps volume as user 10001, and no story reaches that write before US-06, so
    # the only check the smoke can make is that the directory is there and writable (review of #253)
    ok, out = exec_in(env_path, project, "events", "sh", "-c",
                      "touch /var/lib/farol/maps/.probe && rm /var/lib/farol/maps/.probe")
    idp.check(ok, "the events service can write on FAROL_MAP_DIRECTORY (US-19)" + ("" if ok else f" (got {out})"))

    ok, out = psql(env_path, env, project,
                   "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success")
    idp.check(ok and out == "1", "the events schema comes from Flyway (V1 applied)"
              + ("" if ok and out == "1" else f" (got {out})"))

    ok, detail = wait_consuming(env_path, project)
    idp.check(ok, "Events consumes events.auth-users-events as the events broker user"
              + ("" if ok else f" (got {detail})"))

    for failure in idp.failures:
        print(f"::error::{failure}")
    return 1 if idp.failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "infra/.env.example",
                  sys.argv[2] if len(sys.argv) > 2 else None))

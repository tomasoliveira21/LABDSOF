"""Smoke test of Auth & Users in the running stack (#191): run after `docker compose up --wait`.

Checks, through the API gateway, that Auth & Users validates real Keycloak tokens (ADR-006) and keeps accounts in
its own database: no token and a tampered token get 401 Problem Details; Rita's real token creates her account at
the first GET /api/v1/me and the second call returns the same one; the bootstrap account (#179) is FarolAdmin; an
operation of a later story gets 501; the X-Correlation-Id comes back. Then it reads the outbox in the `auth_users`
database: the rows of those first calls must have `published_at`, which is set after the broker's confirm. The
queue is not counted, because Events (#192) will consume it.

Standard library only; reuses the PKCE login of identity_smoke.py and `docker compose exec` for psql. Values come
from the Compose env file; the compose project is optional:
    python3 -I infra/tests/auth_users_smoke.py infra/.env.example [compose-project]
Exit code 0 when every check passes; each failure is printed.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import identity_smoke as idp  # noqa: E402 - path set above (python -I leaves the script's folder out)

COMPOSE_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "compose.yaml")
PUBLISH_DEADLINE = 30  # seconds; the outbox backs off 2 s, 4 s, 8 s... so a slow first publish still fits
RITA = "rita@farol.test"
RITA_ID = idp.SEEDED_USERS[RITA][1]
RITA_NAME = "Rita Participante"
DANIEL_NAME = "Daniel Admin"
EVENT_TYPES = {"UserProfileChanged", "GlobalRoleChanged"}


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


def call(gateway: str, path: str, token: str | None = None,
         correlation_id: str | None = None) -> tuple[int, dict[str, str], str]:
    # No Accept header on purpose: with `Accept: application/json` Spring writes a Problem Details as application/json.
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if correlation_id:
        headers["X-Correlation-Id"] = correlation_id
    status, response_headers, body = idp.request(urllib.request.build_opener(idp.NoRedirect), gateway + path,
                                                 None, headers)
    return status, {name.lower(): value for name, value in response_headers.items()}, body


def is_problem(status: int, headers: dict[str, str], body: str, expected: int) -> bool:
    if status != expected or not headers.get("content-type", "").startswith("application/problem+json"):
        return False
    try:
        return json.loads(body).get("status") == expected
    except (ValueError, AttributeError):
        return False


def tampered(token: str) -> str:
    return token[:-4] + ("AAAA" if not token.endswith("AAAA") else "BBBB")


def psql(env_path: str, env: dict[str, str], project: str | None, sql: str) -> tuple[bool, str]:
    """Runs one statement in the auth_users database as its own user, over TCP like the isolation check of CI."""
    argv = ["docker", "compose", *(["-p", project] if project else []), "-f", COMPOSE_FILE, "--env-file", env_path,
            "exec", "-T", "-e", f"PGPASSWORD={env['AUTH_USERS_DB_PASSWORD']}", "postgres", "psql", "-h", "postgres",
            "-U", "auth_users", "-d", "auth_users", "-v", "ON_ERROR_STOP=1", "-tAc", sql]
    try:
        run = subprocess.run(argv, capture_output=True, text=True, timeout=60, check=False)
    except (OSError, subprocess.SubprocessError) as error:
        return False, str(error)[-300:]
    ok = run.returncode == 0
    return ok, run.stdout.strip() if ok else run.stderr.strip()[-300:]


def outbox_row(env_path: str, env: dict[str, str], project: str | None, user_id: str,
               event_type: str) -> tuple[int, int, str]:
    """(rows, published rows, last error) of one user's event type; (-1, -1, error) when psql fails."""
    uuid.UUID(user_id)  # only constants reach the SQL below
    assert event_type in EVENT_TYPES, event_type
    ok, out = psql(env_path, env, project,
                   "SELECT count(*), count(published_at), regexp_replace(coalesce(max(last_error), ''), '\\s+', ' ', 'g') "
                   f"FROM outbox WHERE type = '{event_type}' AND body->'payload'->>'userId' = '{user_id}'")
    if not ok:
        return -1, -1, out
    parts = out.split("|", 2)
    try:
        return int(parts[0]), int(parts[1]), parts[2] if len(parts) > 2 else ""
    except (ValueError, IndexError):
        return -1, -1, out


def wait_published(env_path: str, env: dict[str, str], project: str | None, user_id: str, event_type: str,
                   expected: tuple[int, int] = (1, 1)) -> tuple[int, int, str]:
    deadline = time.monotonic() + PUBLISH_DEADLINE
    row = outbox_row(env_path, env, project, user_id, event_type)
    while row[:2] != expected and time.monotonic() < deadline:
        time.sleep(0.5)
        row = outbox_row(env_path, env, project, user_id, event_type)
    return row


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

    status, headers, body = call(gateway, "/api/v1/me")
    idp.check(is_problem(status, headers, body, 401) and headers.get("www-authenticate", "").startswith("Bearer"),
              "GET /api/v1/me without a token gets 401 Problem Details with WWW-Authenticate: Bearer"
              + ("" if status == 401 else f" (got {status})"))
    status, headers, body = call(gateway, "/api/v1/me", tampered(rita))
    idp.check(is_problem(status, headers, body, 401),
              "GET /api/v1/me with a tampered token gets 401 Problem Details"
              + ("" if status == 401 else f" (got {status})"))

    correlation = f"smoke-{uuid.uuid4()}"
    status, headers, body = call(gateway, "/api/v1/me", rita, correlation)
    first = json.loads(body) if status == 200 else {}
    idp.check(status == 200 and headers.get("content-type", "").startswith("application/json")
              and first == {"userId": RITA_ID, "email": RITA, "displayName": RITA_NAME, "globalRoles": []},
              "the first GET /api/v1/me with Rita's Keycloak token gets her account "
              "(fixed id, e-mail, name, no global role)" + ("" if status == 200 else f" (got {status})"))
    idp.check(headers.get("x-correlation-id") == correlation,
              "the X-Correlation-Id sent through the gateway comes back")

    status, _, body = call(gateway, "/api/v1/me", rita)
    idp.check(status == 200 and bool(first) and json.loads(body) == first,
              "a second GET /api/v1/me returns the same account")

    status, _, body = call(gateway, "/api/v1/me", daniel)
    admin = json.loads(body) if status == 200 else {}
    idp.check(status == 200 and admin.get("userId") == idp.BOOTSTRAP_ID
              and str(admin.get("email", "")).lower() == bootstrap and admin.get("displayName") == DANIEL_NAME
              and admin.get("globalRoles") == ["FarolAdmin"],
              "the bootstrap FarolAdmin (e-mail verified) gets FarolAdmin in globalRoles"
              + ("" if status == 200 else f" (got {status})"))

    status, headers, body = call(gateway, "/api/v1/me/memberships", rita)
    try:
        detail = str(json.loads(body).get("detail", ""))
    except (ValueError, AttributeError):
        detail = ""
    idp.check(is_problem(status, headers, body, 501) and "US-01" in detail,
              "an operation of a later story (GET /api/v1/me/memberships) gets 501 Problem Details naming US-01"
              + ("" if status == 501 else f" (got {status})"))

    ok, out = psql(env_path, env, project,
                   "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success")
    idp.check(ok and out == "1", "the auth_users schema comes from Flyway (V1 applied)"
              + ("" if ok and out == "1" else f" (got {out})"))

    row = wait_published(env_path, env, project, RITA_ID, "UserProfileChanged")
    idp.check(row[:2] == (1, 1), "Rita's UserProfileChanged is published once (published_at set within 30 s)"
              + ("" if row[:2] == (1, 1) else f" (rows, published = {row[:2]}; last_error: {row[2]})"))
    row = outbox_row(env_path, env, project, RITA_ID, "GlobalRoleChanged")
    idp.check(row[:2] == (0, 0), "Rita has no GlobalRoleChanged"
              + ("" if row[:2] == (0, 0) else f" (got {row[:2]})"))
    for event_type in ("UserProfileChanged", "GlobalRoleChanged"):
        row = wait_published(env_path, env, project, idp.BOOTSTRAP_ID, event_type)
        idp.check(row[:2] == (1, 1), f"the bootstrap FarolAdmin's {event_type} is published once"
                  + ("" if row[:2] == (1, 1) else f" (rows, published = {row[:2]}; last_error: {row[2]})"))

    for failure in idp.failures:
        print(f"::error::{failure}")
    return 1 if idp.failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "infra/.env.example",
                  sys.argv[2] if len(sys.argv) > 2 else None))

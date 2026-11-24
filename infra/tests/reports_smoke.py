"""Smoke test of Reports in the running stack (#194): run after `docker compose up --wait`.

Checks, through the API gateway only, that Reports validates real Keycloak tokens (ADR-006) and reaches its
database: no token and a tampered token get 401; a valid token of a seeded user who is not a member of the
event gets 404 (the token passed, the membership check ran against Reports' own database).

Standard library only; reuses the PKCE login of identity_smoke.py. Values come from the Compose env file:
    python3 -I infra/tests/reports_smoke.py infra/.env.example
Exit code 0 when every check passes; each failure is printed.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.parse
import urllib.request
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import identity_smoke as idp  # noqa: E402 - path set above (python -I leaves the script's folder out)


def call(gateway: str, path: str, token: str | None, body: dict | None = None, method: str | None = None) -> int:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(gateway + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


def main(env_path: str) -> int:
    env = idp.read_env(env_path)
    host = env.get("PUBLIC_HOST", "localhost")
    base = f"http://{host}:{env.get('KEYCLOAK_PORT', '8180')}"
    redirect_uri = f"http://{host}:{env.get('WEB_PORT', '3000')}/callback"
    gateway = f"http://127.0.0.1:{env.get('GATEWAY_PORT', '8080')}"
    token_url = f"{base}/realms/{idp.REALM}/protocol/openid-connect/token"

    verifier, challenge = idp.pkce_pair()
    _, location = idp.log_in(base, redirect_uri, "rita@farol.test", env["FAROL_DEMO_PASSWORD"], challenge)
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location or "").query).get("code", [""])[0]
    status, _, body = idp.request(urllib.request.build_opener(idp.NoRedirect), token_url,
                                  {"grant_type": "authorization_code", "client_id": idp.WEB_CLIENT, "code": code,
                                   "redirect_uri": redirect_uri, "code_verifier": verifier})
    token = json.loads(body).get("access_token") if status == 200 else None
    idp.check(bool(token), "a seeded user gets an access token from Keycloak")
    if not token:
        return 1

    path = f"/api/v1/events/{uuid.uuid4()}/help-requests"
    submit = {"clientRequestId": str(uuid.uuid4()), "location": {"mapVersion": 1, "x": 0.5, "y": 0.5},
              "description": "Synthetic smoke test"}
    idp.check(call(gateway, path, None, submit) == 401, "Reports refuses a request without a token (401)")
    tampered = token[:-4] + ("AAAA" if not token.endswith("AAAA") else "BBBB")
    idp.check(call(gateway, path, tampered, submit) == 401, "Reports refuses a token with a wrong signature (401)")
    idp.check(call(gateway, path, token, submit) == 404,
              "Reports accepts the Keycloak token and answers 404 to a user who is not a member of the event")
    idp.check(call(gateway, f"{path}/{uuid.uuid4()}", token) == 404,
              "Reports reads its database and does not reveal another event's request (404)")
    # US-24 (#72): the participant's own requests go through the gateway with the query string
    idp.check(call(gateway, f"{path}?mine=true", None) == 401, "following my requests needs a token (401)")
    idp.check(call(gateway, f"{path}?mine=true", token) == 404,
              "following my requests answers 404 to a user who is not a member of the event")
    # US-36 (#83): the triage status of scenario D1, on the default stack, which has no `ai` service
    status = path.replace("/help-requests", "/triage-status")
    idp.check(call(gateway, status, None) == 401, "the triage status needs a token (401)")
    idp.check(call(gateway, status, token) == 404,
              "the triage status answers 404 to a user who is not a member of the event")
    # US-35 (#82): the control room's decisions go through the gateway, PATCH included
    request = f"{path}/{uuid.uuid4()}"
    idp.check(call(gateway, request, None, {"priority": "High"}, "PATCH") == 401,
              "setting the category or priority needs a token (401)")
    idp.check(call(gateway, request, token, {"priority": "High"}, "PATCH") == 404,
              "setting the category or priority answers 404 to a user who is not a member of the event")
    idp.check(call(gateway, f"{request}/accept-suggestion", token, {}) == 404,
              "accepting a suggestion answers 404 to a user who is not a member of the event")
    # US-28 (#75): the acknowledge and the change history go through the gateway
    idp.check(call(gateway, f"{request}/acknowledge", None, {}) == 401, "acknowledging needs a token (401)")
    idp.check(call(gateway, f"{request}/acknowledge", token, {}) == 404,
              "acknowledging answers 404 to a user who is not a member of the event")
    idp.check(call(gateway, f"{request}/history", token) == 404,
              "the change history answers 404 to a user who is not a member of the event")

    for failure in idp.failures:
        print(f"::error::{failure}")
    return 1 if idp.failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "infra/.env.example"))

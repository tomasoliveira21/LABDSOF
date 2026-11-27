"""Check RP-initiated Keycloak logout through the real gateway (US-02)."""
from __future__ import annotations

import json
import os
import sys
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import identity_smoke as idp  # noqa: E402


def main(env_path: str) -> int:
    env = idp.read_env(env_path)
    host = env.get("PUBLIC_HOST", "localhost")
    keycloak = f"http://{host}:{env.get('KEYCLOAK_PORT', '8180')}"
    web = f"http://{host}:{env.get('WEB_PORT', '3000')}"
    callback = web + "/callback"
    protocol = f"{keycloak}/realms/{idp.REALM}/protocol/openid-connect"
    api = f"http://127.0.0.1:{env.get('GATEWAY_PORT', '8080')}/api/v1/me"
    plain = urllib.request.build_opener(idp.NoRedirect)
    verifier, challenge = idp.pkce_pair()
    session, _, action, _ = idp.start_login(keycloak, callback, challenge)
    if not action:
        print("::error::Keycloak did not offer the login form")
        return 1
    _, headers, _ = idp.request(session, action, {
        "username": "rita@farol.test", "password": env["FAROL_DEMO_PASSWORD"]})
    code = urllib.parse.parse_qs(urllib.parse.urlsplit(headers.get("Location", "")).query).get("code", [""])[0]
    status, _, body = idp.request(plain, protocol + "/token", {
        "grant_type": "authorization_code", "client_id": idp.WEB_CLIENT, "code": code,
        "redirect_uri": callback, "code_verifier": verifier})
    if status != 200:
        print("::error::Keycloak did not issue the login tokens")
        return 1

    tokens = json.loads(body)
    bearer = {"Authorization": f"Bearer {tokens['access_token']}"}
    before, _, _ = idp.request(plain, api, headers=bearer)
    idp.check(before == 200, f"an active session reaches the gateway (got {before})")
    logout_query = urllib.parse.urlencode({
        "id_token_hint": tokens["id_token"], "post_logout_redirect_uri": web + "/", "state": "logout-smoke"})
    status, headers, _ = idp.request(session, protocol + "/logout?" + logout_query)
    location = urllib.parse.urlsplit(headers.get("Location", ""))
    idp.check(status in (302, 303) and location.netloc == urllib.parse.urlsplit(web).netloc
              and location.path == "/" and urllib.parse.parse_qs(location.query).get("state") == ["logout-smoke"],
              "Keycloak completes the logout and redirects to the app")

    refused, headers, _ = idp.request(plain, api, headers=bearer)
    idp.check(refused == 401 and "invalid_token" in headers.get("WWW-Authenticate", ""),
              f"the same access token is refused immediately (got {refused})")
    query = urllib.parse.urlencode({"client_id": idp.WEB_CLIENT, "response_type": "code", "scope": "openid",
                                   "redirect_uri": callback, "prompt": "none", "code_challenge": challenge,
                                   "code_challenge_method": "S256"})
    _, headers, _ = idp.request(session, protocol + "/auth?" + query)
    error = urllib.parse.parse_qs(urllib.parse.urlsplit(headers.get("Location", "")).query).get("error")
    idp.check(error == ["login_required"], "the browser session requires login again")
    for failure in idp.failures:
        print(f"::error::{failure}")
    return 1 if idp.failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "infra/.env.example"))

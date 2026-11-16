"""Smoke test of the identity set-up (#189): run against a stack started with `docker compose up --wait`.

Checks, through Keycloak's published port only, that the farol realm was imported as ADR-006 says:
the web client logs a seeded user in with Authorization Code + PKCE and the token carries the claims the
services check; the unsafe paths are refused; the realm settings and the seeded users are the expected ones;
the Auth & Users service account can manage users through the Admin API and nothing else.

Standard library only. Values come from the same env file as Compose:
    python3 infra/tests/identity_smoke.py infra/.env.example
Exit code 0 when every check passes; each failure is printed.
"""

from __future__ import annotations

import base64
import hashlib
import html
import json
import re
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request

REALM = "farol"
WEB_CLIENT = "farol-web"
AUDIENCE = "farol-api"
# e-mail -> (e-mail verified, fixed id = the token's `sub`, the same after every `down -v`)
BOOTSTRAP_ID = "00000000-0000-4000-8000-000000000001"
SEEDED_USERS = {
    "rita@farol.test": (True, "00000000-0000-4000-8000-000000000002"),
    "miguel@farol.test": (True, "00000000-0000-4000-8000-000000000003"),
    "sofia@farol.test": (True, "00000000-0000-4000-8000-000000000004"),
    "ines@farol.test": (True, "00000000-0000-4000-8000-000000000005"),
    "nuno@farol.test": (True, "00000000-0000-4000-8000-000000000006"),
    "vera@farol.test": (False, "00000000-0000-4000-8000-000000000007"),  # not verified: never FarolAdmin (#179)
}

failures: list[str] = []


def check(condition: bool, label: str) -> None:
    print(("PASS " if condition else "FAIL ") + label)
    if not condition:
        failures.append(label)


def read_env(path: str) -> dict[str, str]:
    values: dict[str, str] = {}
    with open(path, encoding="utf-8") as env_file:
        for line in env_file:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip()
    return values


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: D102 - keep the 302 visible
        return None


class Session:
    """A browser-like login session. Keycloak marks its cookies Secure even over HTTP on localhost, which
    browsers accept (localhost is a secure context) but http.cookiejar does not, so cookies are kept here."""

    def __init__(self) -> None:
        self.cookies: dict[str, str] = {}
        self.opener = urllib.request.build_opener(NoRedirect)


def request(client, url: str, data: dict | None = None, headers: dict | None = None):
    session = client if isinstance(client, Session) else None
    opener = session.opener if session else client
    body = urllib.parse.urlencode(data).encode() if data is not None else None
    headers = dict(headers or {})
    if session and session.cookies:
        headers["Cookie"] = "; ".join(f"{name}={value}" for name, value in session.cookies.items())
    req = urllib.request.Request(url, data=body, headers=headers)
    try:
        response = opener.open(req, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        if session:
            for set_cookie in response.headers.get_all("Set-Cookie") or []:
                name, _, rest = set_cookie.partition("=")
                session.cookies[name.strip()] = rest.split(";", 1)[0]
        status = response.status if hasattr(response, "status") else response.code
        return status, dict(response.headers), response.read().decode("utf-8", "replace")


def claims(token: str) -> dict:
    payload = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))


def pkce_pair() -> tuple[str, str]:
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    return verifier, challenge


def start_login(base: str, redirect_uri: str, challenge: str | None, method: str = "S256"):
    """Opens the login page in a fresh session; returns (session, status, form action or None, body)."""
    opener = Session()
    params = {"client_id": WEB_CLIENT, "response_type": "code", "scope": "openid",
              "redirect_uri": redirect_uri, "state": secrets.token_urlsafe(8)}
    if challenge is not None:
        params.update(code_challenge=challenge, code_challenge_method=method)
    status, _, body = request(opener, f"{base}/realms/{REALM}/protocol/openid-connect/auth?"
                              + urllib.parse.urlencode(params))
    match = re.search(r'<form[^>]*id="kc-form-login"[^>]*action="([^"]+)"', body)
    return opener, status, html.unescape(match.group(1)) if match else None, body


def log_in(base: str, redirect_uri: str, username: str, password: str, challenge: str | None):
    """Returns (status, location) of the credentials POST."""
    opener, _, action, _ = start_login(base, redirect_uri, challenge)
    if action is None:
        return None, None
    status, headers, _ = request(opener, action, {"username": username, "password": password})
    return status, headers.get("Location", "")


def main(env_path: str) -> int:
    env = read_env(env_path)
    host = env.get("PUBLIC_HOST", "localhost")
    base = f"http://{host}:{env.get('KEYCLOAK_PORT', '8180')}"
    issuer = f"{base}/realms/{REALM}"
    web_origin = f"http://{host}:{env.get('WEB_PORT', '3000')}"
    redirect_uri = f"{web_origin}/callback"
    password = env["FAROL_DEMO_PASSWORD"]
    token_url = f"{issuer}/protocol/openid-connect/token"
    plain = urllib.request.build_opener(NoRedirect)

    # 1. Discovery: the issuer is the public URL the services are configured with (ADR-006)
    status, _, body = request(plain, f"{issuer}/.well-known/openid-configuration")
    check(status == 200 and json.loads(body).get("issuer") == issuer, f"discovery publishes issuer {issuer}")

    # 2. Happy path: Authorization Code + PKCE (S256) for a seeded participant
    verifier, challenge = pkce_pair()
    status, location = log_in(base, redirect_uri, "rita@farol.test", password, challenge)
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location or "").query).get("code", [None])[0]
    check(status == 302 and bool(code) and location.startswith(redirect_uri),
          "seeded user logs in and is sent back to the web app with a code")
    status, _, body = request(plain, token_url, {"grant_type": "authorization_code", "client_id": WEB_CLIENT,
                                                 "code": code or "", "redirect_uri": redirect_uri,
                                                 "code_verifier": verifier})
    tokens = json.loads(body) if status == 200 else {}
    check("access_token" in tokens, "code + verifier are exchanged for tokens")
    if "access_token" in tokens:
        access = claims(tokens["access_token"])
        audience = access.get("aud") if isinstance(access.get("aud"), list) else [access.get("aud")]
        check(access.get("iss") == issuer, "access token iss is the public issuer")
        check(AUDIENCE in audience, f"access token aud contains {AUDIENCE}")
        check(access.get("email") == "rita@farol.test" and access.get("email_verified") is True,
              "access token carries email and email_verified")
        check(access.get("sub") == SEEDED_USERS["rita@farol.test"][1] and access.get("name") == "Rita Participante",
              "access token carries the fixed sub and the name")
        check(access.get("exp", 0) - access.get("iat", 0) == 300, "access token lives 5 minutes")
        check("refresh_token" in tokens, "a refresh token is issued")
        # Refresh tokens are rotated: the first one cannot be used twice
        refresh = {"grant_type": "refresh_token", "client_id": WEB_CLIENT, "refresh_token": tokens["refresh_token"]}
        first, _, _ = request(plain, token_url, refresh)
        second, _, _ = request(plain, token_url, refresh)
        check(first == 200 and second == 400, "a refresh token is refused when reused (rotation)")

    # 3. Unsafe paths are refused
    verifier, challenge = pkce_pair()
    status, location = log_in(base, redirect_uri, "rita@farol.test", password, challenge)
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location or "").query).get("code", [""])[0]
    status, _, _ = request(plain, token_url, {"grant_type": "authorization_code", "client_id": WEB_CLIENT,
                                              "code": code, "redirect_uri": redirect_uri})
    check(status == 400, "a code without its PKCE verifier is refused")

    _, status, action, _ = start_login(base, redirect_uri, None)
    check(action is None, "a login without a PKCE challenge is refused")
    _, status, action, _ = start_login(base, redirect_uri, "a" * 43, method="plain")
    check(action is None, "the plain PKCE method is refused (S256 only)")
    _, status, action, _ = start_login(base, "http://attacker.test/callback", pkce_pair()[1])
    check(action is None, "a redirect URI outside the web app's origin is refused")

    # Another account than the happy path's: repeated runs must not lock it (brute-force detection, 5 failures)
    status, location = log_in(base, redirect_uri, "vera@farol.test", "not-the-password", pkce_pair()[1])
    check(status == 200 and not location, "a wrong password gets no code")

    status, _, _ = request(plain, token_url, {"grant_type": "password", "client_id": WEB_CLIENT,
                                              "username": "rita@farol.test", "password": password})
    check(status in (400, 401), "the password grant is disabled for the web client")

    # 4. Realm settings and seeded users, read through the Admin API with the bootstrap admin
    status, _, body = request(plain, f"{base}/realms/master/protocol/openid-connect/token",
                              {"grant_type": "password", "client_id": "admin-cli",
                               "username": env["KEYCLOAK_ADMIN_USER"], "password": env["KEYCLOAK_ADMIN_PASSWORD"]})
    admin = {"Authorization": "Bearer " + json.loads(body).get("access_token", "")} if status == 200 else {}
    check(bool(admin), "the Keycloak admin from infra/.env can log in")
    status, _, body = request(plain, f"{base}/admin/realms/{REALM}", headers=admin)
    realm = json.loads(body) if status == 200 else {}
    check(realm.get("registrationAllowed") is False, "sign-up is off (Sprint B)")
    check(realm.get("bruteForceProtected") is True, "brute-force detection is on")
    check("length(10)" in realm.get("passwordPolicy", "") and "notEmail" in realm.get("passwordPolicy", ""),
          "password policy: at least 10 characters, not the e-mail")
    check(realm.get("revokeRefreshToken") is True and realm.get("refreshTokenMaxReuse") == 0,
          "refresh tokens are rotated")
    check(realm.get("ssoSessionIdleTimeout") == 43200 and realm.get("ssoSessionIdleTimeoutRememberMe") == 2592000,
          "session idle 12 h, 30 days with Remember me")

    status, _, body = request(plain, f"{base}/admin/realms/{REALM}/clients?clientId={WEB_CLIENT}", headers=admin)
    web = (json.loads(body) or [{}])[0] if status == 200 else {}
    check(web.get("publicClient") is True and web.get("directAccessGrantsEnabled") is False
          and web.get("implicitFlowEnabled") is False, "farol-web is public, code flow only")
    check(web.get("redirectUris") == [f"{web_origin}/*"], f"farol-web redirects only to {web_origin}")

    bootstrap = env["FAROL_BOOTSTRAP_FAROL_ADMIN_EMAIL"]
    for email, (verified, user_id) in {**SEEDED_USERS, bootstrap: (True, BOOTSTRAP_ID)}.items():
        status, _, body = request(plain, f"{base}/admin/realms/{REALM}/users?exact=true&email="
                                  + urllib.parse.quote(email), headers=admin)
        found = json.loads(body) if status == 200 else []
        check(len(found) == 1 and found[0].get("emailVerified") is verified
              and found[0].get("id") == user_id and not found[0].get("requiredActions"),
              f"seeded user {email} exists with id {user_id}, e-mail verified={verified}, no pending action")

    # 5. The Auth & Users service account manages users and nothing else (ADR-006, D7)
    status, _, body = request(plain, token_url, {"grant_type": "client_credentials", "client_id": "farol-auth-users",
                                                 "client_secret": env["FAROL_AUTH_USERS_CLIENT_SECRET"]})
    service = {"Authorization": "Bearer " + json.loads(body).get("access_token", "")} if status == 200 else {}
    check(bool(service), "farol-auth-users gets a token with its client secret")
    status, _, _ = request(plain, f"{base}/admin/realms/{REALM}/users?max=1", headers=service)
    check(status == 200, "farol-auth-users can read users through the Admin API")
    status, _, _ = request(plain, f"{base}/admin/realms/{REALM}/clients", headers=service)
    check(status == 403, "farol-auth-users cannot read the realm's clients")
    status, _, _ = request(plain, token_url, {"grant_type": "client_credentials", "client_id": "farol-auth-users",
                                              "client_secret": "wrong-secret"})
    check(status == 401, "a wrong client secret is refused")

    print(f"\n{len(failures)} failure(s)" if failures else "\nAll identity checks passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "infra/.env"))

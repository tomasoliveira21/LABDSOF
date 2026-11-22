"""Verify the Badges read path in the running Compose stack with a real Keycloak token."""

from __future__ import annotations

import os
import sys
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import auth_users_smoke as auth  # noqa: E402 - python -I leaves the script's folder out
import identity_smoke as idp  # noqa: E402


def main(env_path: str) -> int:
    env = idp.read_env(env_path)
    gateway = f"http://127.0.0.1:{env.get('GATEWAY_PORT', '8080')}"
    path = "/api/v1/me/badges"

    status, headers, body = auth.call(gateway, path)
    idp.check(auth.is_problem(status, headers, body, 401), "Badges rejects an unauthenticated request")

    token = auth.token_for(env, "rita@farol.test")
    idp.check(bool(token), "Keycloak issues an access token for the seeded account")
    if token:
        correlation = f"badges-{uuid.uuid4()}"
        status, headers, body = auth.call(gateway, path, token, correlation)
        idp.check(status == 200 and body.strip() == "[]", "An account with no badge progress gets 200 with []")
        idp.check(headers.get("x-correlation-id") == correlation, "The Badges response returns the correlation ID")

    for failure in idp.failures:
        print(f"::error::{failure}")
    return 1 if idp.failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "infra/.env.example"))

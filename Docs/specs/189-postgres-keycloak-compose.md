# #189 · PostgreSQL and Keycloak in Compose

> **Issue:** [#189](https://github.com/tomasoliveira21/LABDSOF/issues/189) · **Type:** chore · **Owner:** Tiago Teixeira · **Spec rules:** [README](README.md)
> **Sources:** [ADR-003](../Technical/adr/0003-database-and-data-ownership.md), [ADR-006](../Technical/adr/0006-identity-and-access.md), [ADR-010](../Technical/adr/0010-cicd-strategy.md), [ADR-011](../Technical/adr/0011-deployment-and-infrastructure.md), [`local-delivery.md`](../Technical/local-delivery.md) §§1–2, [security assessment](../Technical/security-and-privacy-assessment.md) §9 and finding 8, [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md) decisions 7 and 10.

## 1. Acceptance criteria

| # | Given / when / then | Covered by |
|---|---|---|
| AC-1 | Given a clean machine, when `docker compose up --wait` runs, then PostgreSQL and Keycloak are healthy | CI step *Build and start the stack* (`--wait` on the health checks) |
| AC-2 | Given the PostgreSQL instance, when a service connects with its own user, then it can reach only its own database | CI step *Check that each database user reaches only its own database* |
| AC-3 | Given the realm export in the repository, when Keycloak starts, then the web client (Authorization Code + PKCE) and synthetic test users exist | CI step *Check the identity set-up through Keycloak* (`infra/tests/identity_smoke.py`) |
| AC-4 | Given the admin pages, when the stack runs, then they are published on `127.0.0.1` only | CI step *Check that only 127.0.0.1 is published and the new containers are hardened* |

## 2. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | Images `postgres:18.7-alpine3.24` and `quay.io/keycloak/keycloak:26.8.2`, pinned by digest | Latest patch of each on 16 November 2026 (26.8.2 has security fixes); Keycloak 26.8 supports PostgreSQL 14 to 18; pinned like RabbitMQ (guide H7) |
| D2 | **Keycloak runs `start --optimized` everywhere**, from a two-line `infra/keycloak/Dockerfile`; ADR-006 amendment proposed | `start-dev` cannot run read-only (measured: it writes to `/opt/keycloak/lib/quarkus`; a tmpfs there hides the image's files). Weighed by a five-view council on 16 November 2026: unanimous for this option over *start-dev without read-only* and *both modes behind a switch*. Conditions: the amendment is the team's to accept in the PR review; a scripted PKCE login proves the token claims; scope kept to two lines |
| D3 | Keycloak published on `127.0.0.1:8180`, not `0.0.0.0` as `local-delivery.md` §1 said | Its admin console shares the port with the login pages; the demo runs only on the laptop (decision 10), so nothing needs the network (assessment §9) |
| D4 | `KC_HOSTNAME=http://localhost:8180` with `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` | Tokens always carry the public issuer (checked from inside the network), while services fetch the keys at `http://keycloak:8080` (ADR-006, issuer and JWKS set separately) |
| D5 | Each database owned by its own login role; `CONNECT` and `TEMPORARY` revoked from `PUBLIC` on the six databases, `postgres` and `template1` | PostgreSQL grants both to `PUBLIC` on every new database; without the revoke any role could connect anywhere (ADR-003) |
| D6 | Init script uses psql variables (`:"name"`, `:'password'`) | No password is pasted into SQL text; a quote in a password cannot break or inject the script |
| D7 | `KC_DB_POOL_MAX_SIZE=20`; PostgreSQL `pids_limit: 256` | Keycloak's default pool (100) equals PostgreSQL's `max_connections` (100, 3 reserved); five Hikari pools of 10 plus 20 stay at 70 |
| D8 | PostgreSQL runs as the image's `postgres` user, read-only, with tmpfs on `/tmp` and `/var/run/postgresql` | Same hardening anchor as every other container, with no capability needed (measured) |
| D9 | Realm: sign-up off, brute-force per account, password policy `length(10) and notEmail`, access token 5 min, refresh tokens rotated, session idle 12 h (30 days with *Remember me*), session max 24 h | ADR-006 Keycloak setup table. The 24 h session maximum is new: Keycloak's default (10 h) would end a 12 h night shift |
| D10 | Every seeded user has first name, last name and e-mail; all verified except `vera@farol.test` | Keycloak 24+ asks for missing profile fields at login (verify-profile); an unverified account lets #191 test the FarolAdmin bootstrap refusal (#179) |
| D11 | `farol-auth-users` gets the `kc-admin-api` client scope and only `manage-users` and `view-users` | Keycloak 26.8.2 Admin API accepts only tokens with that scope; account deletion (D7 of ADR-006) needs no more |
| D12 | CI and local runs read `infra/.env.example` with `--env-file`; a missing value stops `up` with its name (`${VAR:?}`) | ADR-010 point 6; no secret in CI; fail fast instead of an empty password |
| D13 | Identity checks in a stdlib-only Python file run by CI (`infra/tests/identity_smoke.py`) | A browser-like PKCE login is too long for inline YAML; ADR-011's "no script" is about delivering the demo, which still uses only `docker compose` commands |

## 3. Features (build order)

1. PostgreSQL with the six databases and owners (`infra/postgres/10-create-databases.sh`, `infra/compose.yaml`)
2. Keycloak image, realm export and Compose service (`infra/keycloak/`)
3. `infra/.env.example` with every variable
4. CI: isolation, identity, ports and hardening checks
5. Docs: `local-delivery.md` §0 and §1, ADR-006 addendum (proposed), CHANGELOG

## 4. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| A user connects to another service's database | `permission denied for database` | CI isolation step (every pair, 30 combinations) |
| A service user connects to `postgres` or `template1` | Refused | CI isolation step |
| Wrong database password | Refused (`scram-sha-256` over TCP) | CI isolation step connects over TCP with the password; checked by hand on 16 Nov |
| The isolation check itself is vacuous | Fails when a `GRANT CONNECT` is added | Run by hand on 16 Nov: the step failed with *events can reach the auth_users database*, passed again after the revoke |
| Empty or missing password in `infra/.env` | `up` stops naming the variable; the init script refuses an empty password | `${VAR:?}` in Compose; check in `10-create-databases.sh` |
| Init script with Windows line endings | Still runs | `.gitattributes`: `*.sh text eol=lf`; executable bit stored in git |
| Health check passes during first-start initialisation | Not possible: checks TCP, the init server listens only on the socket | `pg_isready -h 127.0.0.1` |
| Login without PKCE, with `plain` PKCE, or code without its verifier | Refused | `identity_smoke.py`: *a login without a PKCE challenge is refused*, *the plain PKCE method is refused*, *a code without its PKCE verifier is refused* |
| Redirect to another origin | Refused | *a redirect URI outside the web app's origin is refused* |
| Password grant on the web client | Refused | *the password grant is disabled for the web client* |
| Wrong password | No code | *a wrong password gets no code* (on `vera`, so repeated runs do not lock the happy-path account) |
| Refresh token reused | Refused | *a refresh token is refused when reused (rotation)* |
| Token claims | `iss` public URL, `aud` has `farol-api`, `email`, `email_verified`, `name`, `sub`, 5 min | *access token …* checks |
| Seeded user with a pending required action | None | *seeded user … no pending action* |
| Service account reaches more than users | `403` | *farol-auth-users cannot read the realm's clients* |
| Wrong client secret | `401` | *a wrong client secret is refused* |
| The identity check itself is vacuous | Fails with wrong secrets | Run by hand on 16 Nov with a mutated env file: 5 failures, exit 1 |
| Keycloak restarted with an existing realm | Import skipped, realm kept | Checked by hand: *Realm 'farol' already exists. Import skipped* |
| Realm or database values changed after the first start | Not applied until `down -v` | Documented in `local-delivery.md` §0 and in the files |
| Keycloak or PostgreSQL published on the network | Only `127.0.0.1:8180`; PostgreSQL and port 9000 not published | CI ports step |
| Hardening silently dropped | `read_only`, `cap_drop: ALL`, `no-new-privileges` on both | CI hardening step |
| Keycloak down | Services still start (they fetch keys on the first token) | Out of scope here: #190 to #192 |

## 5. Handoff and changelog

- **16 Nov 2026:** research (10 min), council on the start mode (D2), implementation, local runs of the whole stack (10 containers healthy in about 2 min on a cold build) and of every CI step. Measured: Keycloak ready about 12 s after PostgreSQL, about 610 MB and 70 processes; PostgreSQL about 80 MB.
- **For #190 (gateway) and #191/#192 (services):** issuer `http://localhost:8180/realms/farol` (`FAROL_ISSUER_URI`), keys `http://keycloak:8080/realms/farol/protocol/openid-connect/certs` (`FAROL_JWKS_URI`), audience `farol-api`. JDBC `jdbc:postgresql://postgres:5432/<database>` with user `<database>` and its password from `infra/.env`; keep the Hikari pool at 10 or below.
- **Open for the team:** accept or reject the ADR-006 addendum in the review of this PR.

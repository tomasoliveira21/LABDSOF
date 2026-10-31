# Local delivery and demo

> **Decided 23 October 2026** (#129; guide G4–G7, H5 and H6). Implements [ADR-011](adr/0011-deployment-and-infrastructure.md) points 4–6. The walking skeleton is to apply this procedure. The full stack below is the **Sprint B target**. Sprint A now has a separate minimum described in §0: one Reports synthetic in-memory demo service; it does not claim the full stack is implemented.
> **Aligned on 26 October 2026** with [ADR-006](adr/0006-identity-and-access.md) (Keycloak), [ADR-013](adr/0013-api-gateway.md) (API gateway) and the [Security and Privacy Assessment](security-and-privacy-assessment.md) §9 (ports). HTTPS for the phones is open (§8).

Delivery is local only, because cloud hosting costs were not justified ([clarification record](../Meetings/2026-10-22-local-deployment-clarification.md)). Everything runs on one laptop with Docker. The phones open the same React web app in their browser.

## 0. Sprint A: run the executable minimum first

The 31 October review (#166) limits deliverable 10 to a small one-service slice, HTTP tests, Dockerfile, Compose and CI. Follow the [R0 run instructions](plans/local-walking-skeleton.md#3-run-and-demonstrate-from-the-repository-root) and [actual branch evidence](evidence/walking-skeleton-2026-10-31.md). The current `infra/compose.yaml` starts Reports only with profile `demo`, bound to `127.0.0.1:8080`: Swagger creates and reads synthetic help requests with fixed identity and in-memory storage. `GET /api/v1/status` remains diagnostic. No secret file is required. #58 was closed on 31 October 2026 after #167; #148 remains open until actual observability exists.

The procedures in §§1–8 are retained for **Sprint B**, when the named services/configuration exist. They must not be followed as if Keycloak, a web UI, databases and the broker are already part of R0. Authentication and domain guarantees apply when business endpoints are introduced; the demo uses only fixed synthetic identity/event values and is absent without the demo profile.

## 1. What runs

One Compose file, `infra/compose.yaml`. Images are built from the repository: there is no registry (guide H7).

| Compose service | What it is | Profile | Port published |
|---|---|---|---|
| `auth-users`, `events`, `reports`, `transports`, `badges` | The five Spring Boot services, one container each, image `farol-<service>:${VERSION}` | default | none |
| `gateway` | The API gateway, image `farol-gateway:${VERSION}` ([ADR-013](adr/0013-api-gateway.md)): the only entry point of the API, with CORS, JWT validation and the rate limits | default | `0.0.0.0:8080` |
| `keycloak` | The identity provider ([ADR-006](adr/0006-identity-and-access.md)), pinned version, realm imported from `infra/keycloak/farol-realm.json` | default | `0.0.0.0:8180` |
| `web` | Serves the React app, image `farol-web:${VERSION}`. The app calls the gateway and Keycloak directly | default | `0.0.0.0:${WEB_PORT}` (3000, guide F2) |
| `postgres` | **One** PostgreSQL instance with **six databases and six users**: one per service and one for Keycloak. No user can read another's database (ADR-003) | default | none |
| `rabbitmq` | The broker, with a fixed node name, a named volume and one user per service (ADR-006) | default | `127.0.0.1:15672` (management UI) |
| `wiremock` | WireMock in front of Porto Digital and of the AI provider (the local `ai` container, guide K1): proxy, recorded responses and failure stubs (guide J2, J4). Its root is [`infra/wiremock/`](../../infra/wiremock/README.md), mounted at `/home/wiremock`, with the recorded Porto Digital stubs. Transports and Reports point at it by default | default | `127.0.0.1:8090` (admin API) |
| `ai` | Local model: Ollama with `qwen3:4b` (guide K1, [AI plan](plans/ai-provider-evaluation.md)). Reports reaches it through `wiremock` | `ai` | none |
| `prometheus`, `grafana` | Observability stack ([ADR-012](adr/0012-observability.md), accepted architecture) | `observability` | `127.0.0.1` only |

- **Only the gateway, Keycloak and the web app are published to the network** (`0.0.0.0`, ADR-013). Three admin pages are also published, but **only on `127.0.0.1`**, so only the demo laptop opens them: RabbitMQ's management UI, WireMock's admin API (the presenter uses it to trigger D1 and D2, §8) and Grafana. Nothing else is published: the services, PostgreSQL and RabbitMQ's AMQP port are reachable only inside Compose (assessment §9).
- **One PostgreSQL instance, not six.** Separate databases with separate users keep the ownership rule. More containers would only add memory. Supersedes the "five PostgreSQL containers" suggestion in the [deployment research](../Research/architecture-investigation/deployment.md) (G6).
- An init script in `infra/postgres/` creates the six databases and users from the `.env` values on the first start.
- Third-party images (Keycloak, PostgreSQL, RabbitMQ, WireMock, observability) are pinned to an exact version in the Compose file and change only in a PR (guide H7).
- Health checks: `postgres` (`pg_isready`), `rabbitmq` (`rabbitmq-diagnostics ping`), `keycloak` (`/health/ready` on its management port), the gateway and each service (`/actuator/health`). Services and Keycloak wait for their database to be healthy (`depends_on: condition: service_healthy`). They do **not** wait for RabbitMQ or Keycloak: they reconnect on their own, fetch Keycloak's keys when the first token arrives (ADR-006), and a help request is still saved to the outbox while the broker is down (§9 of [`architecture.md`](architecture.md)).
- Named volumes for PostgreSQL and RabbitMQ. A volume is not a backup (§6).

## 2. Configuration and secrets

- `infra/.env.example` is versioned and lists every variable with a harmless example value. `infra/.env` is in `.gitignore`. Each person copies the example once: `cp infra/.env.example infra/.env`.
- Variables:
  - `VERSION`, `WEB_PORT` and `PUBLIC_HOST`: the address the phones use, the laptop's LAN IP for the demo, `localhost` while developing. The Keycloak hostname (`KC_HOSTNAME`), the token issuer the services check (`FAROL_ISSUER_URI`), the gateway's allowed origin and the web app's API and login addresses are built from it.
  - The PostgreSQL superuser and the six database users and passwords; the RabbitMQ admin and the five service users.
  - Keycloak's admin user, the `farol-auth-users` client secret and `FAROL_BOOTSTRAP_FAROL_ADMIN_EMAIL` (ADR-006).
  - `TRANSPORT_SOURCE_URL` (WireMock by default), the AI provider settings (guide K1) and `SPRING_PROFILES_ACTIVE`.
- No real secret goes into the repository, the images or the CHANGELOG. Passwords are generated per machine, never the image defaults. The realm export holds `${…}` placeholders, not secrets (assessment finding 8). An API key for a hosted AI provider, if K1 picks one, lives only in `infra/.env`.

## 3. Demo data

- Demo data comes from **Flyway migrations kept only for the demo**, in `src/main/resources/db/demo/` of each service. They run only with the Spring profile `demo` (`spring.flyway.locations` adds `classpath:db/demo`).
- Demo accounts come from the Keycloak realm import (ADR-006); their Farol accounts are created at their first login.
- The data is synthetic: invented events, users, teams and requests, with no real personal data.
- To start from a clean state, delete the volumes and start again (§5). The realm import and the migrations recreate everything.

## 4. Starting

Requirements: Docker Desktop (or Docker Engine with the Compose plugin) and git. The minimum RAM is measured with the walking skeleton and written here.

```sh
git checkout v0.1.0                  # the release to show (guide H7); use main while developing
cp infra/.env.example infra/.env     # first time only; set VERSION=0.1.0 and PUBLIC_HOST
docker compose -f infra/compose.yaml build
docker compose -f infra/compose.yaml up -d
docker compose -f infra/compose.yaml --profile ai --profile observability up -d   # optional components
```

Then run the smoke test (§7).

## 5. Updating, rolling back and resetting

| Action | Commands |
|---|---|
| Update everything to a new release | `git checkout vX.Y.Z`, set `VERSION=X.Y.Z` in `.env`, `docker compose -f infra/compose.yaml build`, then `up -d` |
| Replace a single service | `docker compose -f infra/compose.yaml build <service>` then `docker compose -f infra/compose.yaml up -d --no-deps <service>`. The other containers keep running. This is how independent deployment is shown |
| Roll back | Check out the previous tag and build it again, as for an update (guide H7). Flyway migrations are not undone: they must be compatible with the previous image (*expand and contract*). After rolling back, check that the consumers and the outbox are empty or draining (RabbitMQ management UI) |
| Reset the demo data | `docker compose -f infra/compose.yaml down -v` then `up -d`. Deletes the volumes; the realm import and the demo migrations recreate the data |
| Stop | `docker compose -f infra/compose.yaml down` (keeps the volumes) |

Run the smoke test after every update or rollback.

## 6. Backup and recovery (G7)

- **Before every demo and before every update**, take a dump of the six databases, Keycloak's included, so the accounts stay consistent with Auth & Users:
  `docker compose -f infra/compose.yaml exec -T postgres pg_dump -U <superuser> -Fc <database> > backups/<database>.dump` (one per database; `backups/` is in `.gitignore`).
- Restore one database: `docker compose -f infra/compose.yaml exec -T postgres pg_restore -U <superuser> --clean -d <database> < backups/<database>.dump`, then restart that service.
- What this covers: going back to a known demo state, and moving that state to the backup laptop. What it does **not** cover: messages in RabbitMQ queues (no backup of queued messages), and the loss of the laptop's disk between dumps. The RPO for disk loss is **not guaranteed**. Data retention and backup in production stay out of scope (guide D5, 🔮).
- The time to roll back and to restore (RTO) is measured with the walking skeleton and written here. Until then, no recovery figure is claimed.

## 7. Smoke test

A checklist of commands, run by hand after starting, updating or rolling back. It passes only if every line passes.

| # | Check | Command | Pass |
|---|---|---|---|
| 1 | Every container is healthy. The health checks call `/actuator/health` and Keycloak's `/health/ready` | `docker compose -f infra/compose.yaml ps` | Every container `running (healthy)` |
| 2 | The web app opens | `curl -fsI http://localhost:${WEB_PORT}/`, and open `http://<LAN IP>:${WEB_PORT}` on a phone | HTTP 200; the page loads on the phone |
| 3 | The gateway routes and validates tokens | `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/v1/me` | `401`: the call reached the gateway and was refused without a token |
| 4 | Login works | Log in with a demo account on the laptop (`localhost`); on a phone once HTTPS is decided (§8) | Keycloak's page opens; back in the app, the account's name is shown (`GET /me`) |
| 5 | One round trip through RabbitMQ | One service publishes an event and another consumes it. The exact command is written here in Sprint B: the Sprint A skeleton has no broker (#166) | The consumer shows the change; the queue is empty afterwards |

Once `ci.yml` exists ([ADR-010](adr/0010-cicd-strategy.md) point 9), the same checks, without the phone and the login, will run in CI with Compose on every push to `main`, every tag and manual runs (Sprint B). Today `ci.yml` (#167) runs the Sprint A skeleton's smoke test instead (status, synthetic create and read, Swagger) on every pull request, push to `main`, tag and manual run.

## 8. The demo

**Machine.** One team member's laptop runs the demo. A second laptop is ready as a backup: same tag, same `.env`, the latest dumps restored, smoke test passed the day before. The laptop has full-disk encryption and a screen lock: it holds every database (assessment §9).

**Phones.** The phones and the laptop join the same network, preferably a phone hotspot or a router of our own: venue Wi-Fi often blocks traffic between clients. The phones open `http://<laptop LAN IP>:${WEB_PORT}`. Allow ports 8080, 8180 and `${WEB_PORT}` in the laptop's firewall (on Windows, Docker Desktop asks the first time). Show the URL as a QR code on the screen.

- **Open: HTTPS for the phones** ([assessment](security-and-privacy-assessment.md) §9, finding 2). Over plain HTTP anyone on the network can read the tokens, and the browser blocks the Web Crypto call that the PKCE login needs on a page that is not HTTPS or `localhost`, so **login fails on a phone**. The assessment proposes a certificate from a local authority (e.g. mkcert) on the gateway, Keycloak and the web app, trusted once on each demo phone, or the phone pages in a laptop browser's device mode. This procedure is updated when it is decided. Until then, the phone pages are shown in device mode on `localhost`.
- Turning off PKCE, token validation, CORS or Keycloak's HTTPS requirement to make the demo work is **not allowed** (assignment §11). Every security control stays on; D1 and D2 break a dependency, not a control.

**Degraded modes** (scenarios D1 and D2, [`features.md` §3.1](../Product/features.md); the demo script and the values are in guide J2, [`architecture.md`](architecture.md) §9.3):

| Show | How |
|---|---|
| D1 · AI unavailable | Add an AI failure stub (dropped connection, slow answer) in WireMock through its admin API, and remove it to recover ([`architecture.md`](architecture.md) §9.3). Also: `docker compose -f infra/compose.yaml stop ai`, or start without the `ai` profile |
| D2 · Transport source unavailable, late or invalid | The demo always goes through `wiremock`. Add a failure stub (no answer, late, invalid data) through its admin API, or `docker compose -f infra/compose.yaml stop wiremock` |
| Broker down | `docker compose -f infra/compose.yaml stop rabbitmq`: requests are still saved (outbox) and catch up after `start rabbitmq` |
| Another service down | `docker compose -f infra/compose.yaml stop badges`: the other services keep working |

## 9. What is still open

- The minimum RAM of the demo laptop, and the measured RTO for rollback and restore: measured with the walking skeleton (guide G7); the in-memory Sprint A skeleton cannot measure them (#166), so when they are measured is open.
- The exact round-trip command in the smoke test: Sprint B slice.
- HTTPS for the phones, which the PKCE login needs (§8): assessment §9, finding 2.
- Closed: the AI container is Ollama with `qwen3:4b` (guide K1, [ADR-005](adr/0005-ai-triage-provider-and-evaluation.md)); the degraded-mode script and x = 30 s, y = 5 min (guide J1, J2; [`architecture.md` §9](architecture.md#9-degraded-modes)). Still open: the model measurements on the demo laptop (guide K1).
# Local delivery and demo

> **Decided 23 October 2026** (#129, Bruno; guide G4–G7 and H5). Implements [ADR-011](adr/0011-deployment-and-infrastructure.md) points 4–6. **Bruno** defines this procedure and **Tomás** applies it in the walking skeleton. Nothing below is running yet: it is the specification that the `infra/` folder and the walking skeleton must follow.

Delivery is local only, because cloud hosting costs were not justified ([clarification record](../Meetings/2026-10-22-local-deployment-clarification.md)). Everything runs on one laptop with Docker. The phones open the same React web app in their browser.

## 1. What runs

One Compose file, `infra/compose.yaml`. Images are built from the repository: there is no registry (guide H7).

| Compose service | What it is | Profile | Port published |
|---|---|---|---|
| `auth-users`, `events`, `reports`, `transports`, `badges` | The five Spring Boot services, one container each, image `farol-<service>:${VERSION}` | default | none |
| `postgres` | **One** PostgreSQL instance with **five databases and five users**, one per service. No user can read another service's database (ADR-003) | default | `127.0.0.1:5432` |
| `rabbitmq` | The broker, with a fixed node name and a named volume | default | `127.0.0.1:15672` (management UI) |
| `web` | Serves the React app and proxies `/api/` to the backend (gateway or services, depending on guide C6) | default | `0.0.0.0:${WEB_PORT}` (the only port on the LAN) |
| `transport-sim` | Porto Digital simulator or recorded responses (guide J4, Guerra). Transports points at it by default | default | none |
| `ai` | Local model (e.g. Ollama) if guide K1 picks one | `ai` | none |
| `prometheus`, `grafana` | Observability stack, if ADR-012 is accepted | `observability` | `127.0.0.1` only |

- **One PostgreSQL instance, not five.** Five databases with separate users keep the ownership rule. Five containers would only add memory. Supersedes the "five PostgreSQL containers" suggestion in the [deployment research](../Research/architecture-investigation/deployment.md) (G6).
- An init script in `infra/postgres/` creates the five databases and users from the `.env` values on the first start.
- Health checks: `postgres` (`pg_isready`), `rabbitmq` (`rabbitmq-diagnostics ping`), each service (`/actuator/health`). Services wait for their database to be healthy (`depends_on: condition: service_healthy`). They do **not** wait for RabbitMQ: they reconnect on their own, and a help request is still saved to the outbox while the broker is down (§9 of [`architecture.md`](architecture.md)).
- Named volumes for PostgreSQL and RabbitMQ. A volume is not a backup (§6).

## 2. Configuration and secrets

- `infra/.env.example` is versioned and lists every variable with a harmless example value. `infra/.env` is in `.gitignore`. Each person copies the example once: `cp infra/.env.example infra/.env`.
- Variables: `VERSION`, `WEB_PORT`, the PostgreSQL superuser, the five database users and passwords, the RabbitMQ user, `TRANSPORT_SOURCE_URL` (simulator by default, real Porto Digital URL when wanted), the AI provider settings (guide K1) and `SPRING_PROFILES_ACTIVE`.
- No real secret goes into the repository, the images or the CHANGELOG. An API key for a hosted AI provider, if K1 picks one, lives only in `infra/.env`.

## 3. Demo data

- Demo data comes from **Flyway migrations kept only for the demo**, in `src/main/resources/db/demo/` of each service. They run only with the Spring profile `demo` (`spring.flyway.locations` adds `classpath:db/demo`).
- The data is synthetic: invented events, users, teams and requests, with no real personal data.
- To start from a clean state, delete the volumes and start again (§5). The migrations recreate everything.

## 4. Starting

Requirements: Docker Desktop (or Docker Engine with the Compose plugin) and git. The minimum RAM is measured with the walking skeleton and written here.

```sh
git checkout v0.1.0                  # the release to show (guide H7); use main while developing
cp infra/.env.example infra/.env     # first time only; set VERSION=0.1.0
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
| Reset the demo data | `docker compose -f infra/compose.yaml down -v` then `up -d`. Deletes the volumes; the demo migrations recreate the data |
| Stop | `docker compose -f infra/compose.yaml down` (keeps the volumes) |

Run the smoke test after every update or rollback.

## 6. Backup and recovery (G7)

- **Before every demo and before every update**, take a dump of the five databases:
  `docker compose -f infra/compose.yaml exec -T postgres pg_dump -U <superuser> -Fc <database> > backups/<database>.dump` (one per database; `backups/` is in `.gitignore`).
- Restore one database: `docker compose -f infra/compose.yaml exec -T postgres pg_restore -U <superuser> --clean -d <database> < backups/<database>.dump`, then restart that service.
- What this covers: going back to a known demo state, and moving that state to the backup laptop. What it does **not** cover: messages in RabbitMQ queues (no backup of queued messages), and the loss of the laptop's disk between dumps. The RPO for disk loss is **not guaranteed**. Data retention and backup in production stay out of scope (guide D5, 🔮).
- The time to roll back and to restore (RTO) is measured with the walking skeleton and written here. Until then, no recovery figure is claimed.

## 7. Smoke test

A checklist of commands, run by hand after starting, updating or rolling back. It passes only if every line passes.

| # | Check | Command | Pass |
|---|---|---|---|
| 1 | Every container is healthy. The health check of each service calls its `/actuator/health`, so this also checks the five services | `docker compose -f infra/compose.yaml ps` | Every container `running (healthy)` |
| 2 | The web app opens | `curl -fsI http://localhost:${WEB_PORT}/`, and open `http://<LAN IP>:${WEB_PORT}` on a phone | HTTP 200; the home page loads on the phone |
| 3 | One round trip through RabbitMQ | The walking skeleton's end-to-end action: one service publishes an event and another consumes it. Tomás writes the exact command here when the skeleton exists | The consumer shows the change; the queue is empty afterwards |

The same checks may later run in CI with Compose. That belongs to ADR-010 (Bruno), not to this procedure.

## 8. The demo

**Machine.** One team member's laptop runs the demo. A second laptop is ready as a backup: same tag, same `.env`, the latest dumps restored, smoke test passed the day before.

**Phones.** The phones and the laptop join the same network, preferably a phone hotspot or a router of our own: venue Wi-Fi often blocks traffic between clients. The phones open `http://<laptop LAN IP>:${WEB_PORT}`. Only the `web` port is published on the LAN; allow it in the laptop's firewall (on Windows, Docker Desktop asks the first time). Show the URL as a QR code on the screen.

- Limitation: this is plain HTTP, so browser features that need HTTPS (precise geolocation, installing as a PWA, service workers) do not work on the phones. The MVP does not need them: participants mark their location on the venue map (F6). If a story ever needs them, add local HTTPS then.

**Degraded modes** (scenarios D1 and D2, [`features.md` §3.1](../Product/features.md); the demo script and the values are Tiago's, guide J2):

| Show | How |
|---|---|
| D1 · AI unavailable | `docker compose -f infra/compose.yaml stop ai`, or start without the `ai` profile. With a hosted provider (K1), point the AI URL in `.env` to an unreachable address and restart `reports` |
| D2 · Transport source unavailable, late or invalid | The demo always uses `transport-sim`, never the real API. Stop it (`docker compose -f infra/compose.yaml stop transport-sim`) or use its fault injection (J4, Guerra) |
| Broker down | `docker compose -f infra/compose.yaml stop rabbitmq`: requests are still saved (outbox) and catch up after `start rabbitmq` |
| Another service down | `docker compose -f infra/compose.yaml stop badges`: the other services keep working |

## 9. What is still open

- The minimum RAM of the demo laptop, and the measured RTO for rollback and restore: measured with the walking skeleton (Tomás).
- The exact round-trip command in the smoke test: walking skeleton (Tomás).
- What sits behind `/api/` in the `web` proxy: guide C6 (Guerra).
- The AI container or provider: guide K1 (Tomás). The simulator: J4 (Guerra). The degraded-mode script and the values of x and y: J1, J2 (Tiago).
- Running the smoke test in CI: ADR-010 (Bruno).

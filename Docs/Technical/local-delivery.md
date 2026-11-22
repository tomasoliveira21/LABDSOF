# Local delivery and demo

> **Decided 23 October 2026** (#129; guide G4–G7, H5 and H6). Implements [ADR-011](adr/0011-deployment-and-infrastructure.md) points 4–6. The Sprint B slice applies this procedure; the full stack below is the **Sprint B target**. Sprint A now has a separate minimum described in §0: one Reports synthetic in-memory demo service; it does not claim the full stack is implemented.
> **Aligned on 26 October 2026** with [ADR-006](adr/0006-identity-and-access.md) (Keycloak), [ADR-013](adr/0013-api-gateway.md) (API gateway) and the [Security and Privacy Assessment](security-and-privacy-assessment.md) §9 (ports).
> **15 November 2026** ([meeting record](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md), decision 10): the demo runs only on the laptop, so HTTPS for phones is not needed for the demo (§8).

Delivery is local only, because cloud hosting costs were not justified ([clarification record](../Meetings/2026-10-22-local-deployment-clarification.md)). Everything runs on one laptop with Docker. The participant pages are shown in the laptop's browser at phone width.

## 0. What runs today: the Sprint B base (#202, #189, #212, #194, #191)

`infra/compose.yaml` starts the base that every service is built on (#202; Sprint B planning of 15 November 2026, decision 13): the web app, RabbitMQ, the API gateway, the five services, **PostgreSQL** with one database per service and one for Keycloak, and **Keycloak** with the `farol` realm (#189). **Reports** keeps help requests in its own database (Flyway runs at start-up, so it waits for PostgreSQL), validates Keycloak tokens, publishes `HelpRequestSubmitted` through its outbox and keeps copies of memberships and teams from RabbitMQ, as the `reports` user (#194, [spec](../specs/194-reports-postgres-outbox.md)). **Auth & Users** keeps accounts in its own database (Flyway at start-up, so it waits for PostgreSQL too), validates Keycloak tokens, creates the caller's account at the first `GET /api/v1/me` and publishes `UserProfileChanged` (and `GlobalRoleChanged` for the bootstrap FarolAdmin) through its outbox to `farol.auth-users`, as the `auth-users` user (#191, [spec](../specs/191-auth-users-service.md)). The other services are still empty skeletons that answer only `GET /api/v1/status` (#190, #192, #193, #195, #201).

Requirements: Docker Desktop, or Docker Engine with Compose v2. Java 21 with Maven, and Node 22, only to build outside Docker. From the repository root:

```sh
cp infra/.env.example infra/.env                         # first time only; then replace every password with your own
docker compose -f infra/compose.yaml up --build --wait   # builds 8 images; returns when all 10 long-running containers are healthy and rabbitmq-setup has finished
docker compose -f infra/compose.yaml ps                  # every container "running (healthy)"
curl http://localhost:8080/status/reports                # {"service":"reports","status":"ok"}, through the gateway
docker compose -f infra/compose.yaml down                # stop; add -v to also delete the PostgreSQL and RabbitMQ volumes
```

| What | Where |
|---|---|
| Web app (placeholder page), Compose service `frontend` | `http://localhost:3000` (`WEB_PORT`) |
| API gateway | `http://localhost:8080` (`GATEWAY_PORT`). Routes `/api/v1/**` by the route table of [ADR-013](adr/0013-api-gateway.md); any other path is a 404 at the gateway |
| Each service's status, through the gateway | `/status/auth-users`, `/status/events`, `/status/reports`, `/status/transports` and `/status/badges` forward to that service's `GET /api/v1/status`. Diagnostic only, outside the product API |
| RabbitMQ management UI | `http://localhost:15672` (`RABBITMQ_UI_PORT`). Log in with `RABBITMQ_ADMIN_USER` of `infra/.env` (the operator's account; no service uses it). The image's `guest` works only inside the container: do not lift that restriction |
| RabbitMQ topology and service users (#212) | The one-shot `rabbitmq-setup` container imports [`infra/rabbitmq/topology.json`](../../infra/rabbitmq/topology.json) (the exchanges and queues of the [API outline §7.5](api-outline.md#75-queues), each with its dead-letter queue) and declares one user per service (`auth-users`, `events`, `reports`, `transports`, `badges`, passwords `RABBITMQ_<SERVICE>_PASSWORD`): it can write only to its own exchange `farol.<service>` and to `farol.dead-letters`, read only its own queues, declare nothing, and has no management access. It runs right after the broker is healthy, on every `up`, and keeps waiting messages; it shows `Exited (0)` in `ps`. The web app starts only after it has finished with exit 0, so that `up --wait` waits for it (see the health-check note in §1, #220) |
| Keycloak: login pages and admin console | `http://localhost:8180` (`KEYCLOAK_PORT`). Realm `farol`, issuer `http://localhost:8180/realms/farol`. Admin console: the `KEYCLOAK_ADMIN_USER` of `infra/.env` |
| Test accounts (synthetic, `.test` domain) | `rita@farol.test` (participant), `miguel@farol.test` (control room), `sofia@farol.test` (event admin), `ines@farol.test` (responder), `nuno@farol.test` (a participant of another event, for isolation tests), `vera@farol.test` (e-mail not verified) and the bootstrap FarolAdmin `FAROL_BOOTSTRAP_FAROL_ADMIN_EMAIL` (`daniel@farol.test` in the example). Password: `FAROL_DEMO_PASSWORD`. Each has a fixed id, so the token's `sub` stays the same after `down -v`: `00000000-0000-4000-8000-00000000000N`, with N = 1 for the FarolAdmin, then 2 to 7 for Rita, Miguel, Sofia, Inês, Nuno and Vera |
| Reports API (#194) | Through the gateway: `POST /api/v1/events/{eventId}/help-requests` and `GET …/help-requests/{requestId}` with a Keycloak access token. A user gets `404` until Reports has their membership of the event, which arrives with `MembershipChanged` from Auth & Users (#191). Contract: [`backend/reports/contracts/openapi.yaml`](../../backend/reports/contracts/openapi.yaml) |
| Auth & Users API (#191) | Through the gateway, with a Keycloak access token: `GET /api/v1/me` returns `userId` (the token's `sub`), `email`, `displayName` and `globalRoles`; the first call creates the account, and the bootstrap account gets `FarolAdmin` once its e-mail is verified (#179). The other operations of the contract answer `501` with the story that brings them. Contract: [`backend/auth-users/contracts/openapi.yaml`](../../backend/auth-users/contracts/openapi.yaml); event schemas: [`backend/auth-users/contracts/events/`](../../backend/auth-users/contracts/events/) |
| PostgreSQL | Not published. Databases and owners `auth_users`, `events`, `reports`, `transports`, `badges` and `keycloak`; each owner can connect only to its own database |
| Events API (#192) | Through the gateway, with a Keycloak access token: `GET /api/v1/events/{eventId}` returns the event to its members (an EventAdmin in any status, a RegularUser only while it is `Published` or `Closed`, with the join link only while it is published); anyone else, an unknown event and a FarolAdmin who is not a member get the same `404`, and a malformed id gets `400`. Memberships arrive with `MembershipChanged` from Auth & Users, so every user gets `404` until Auth & Users publishes one for them. The other operations of the contract answer `501` with the story that brings them. Contract: [`backend/events/contracts/openapi.yaml`](../../backend/events/contracts/openapi.yaml); event schemas: [`backend/events/contracts/events/`](../../backend/events/contracts/events/) |

- Every port is published on `127.0.0.1` only: the demo runs only on the laptop (decision 10 of 15 November 2026). The services, PostgreSQL, RabbitMQ's AMQP port and Keycloak's management port (9000, health) are not published.
- **Keycloak** is built from `infra/keycloak/Dockerfile`: the official image, pinned by digest, with its build step done in the image, started with `start --optimized --import-realm` in production mode over HTTP on `localhost`. This lets it run on a read-only file system like every other container; `start-dev` cannot (#189, amendment of ADR-006 proposed on 16 November 2026).
- **Events is no longer a skeleton (#192, [spec](../specs/192-events-service.md)).** It keeps events, teams, venue maps and points of interest in its own database (Flyway at start-up, so it waits for PostgreSQL), validates Keycloak tokens, keeps a copy of the memberships it reads from `events.auth-users-events` and has an outbox to `farol.events` (nothing writes to it before US-06), as the `events` broker user. Its settings come from `infra/.env` through `infra/compose.yaml`, with no new variable: `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` (database and owner `events`, `EVENTS_DB_PASSWORD`), `SPRING_RABBITMQ_HOST`, `_USERNAME`, `_PASSWORD` (`events`, `RABBITMQ_EVENTS_PASSWORD`), `FAROL_ISSUER_URI` (the public issuer, equal to the token's `iss`) and `FAROL_JWKS_URI` (Keycloak's keys on the internal address, fetched at the first token). It waits only for PostgreSQL. Its `/actuator/health` needs a token, so its health check stays on `/api/v1/status`.
- **Events' checks (#192):** its integration tests (`*IT`) start PostgreSQL and RabbitMQ with Testcontainers, so `mvn -f backend/events/pom.xml verify` needs Docker; its image build runs only `mvn package`. After the issuer check, `ci.yml` runs [`infra/tests/events_smoke.py`](../../infra/tests/events_smoke.py) through the gateway: no token or a tampered token gets `401`; Rita's real token gets `404` for an event she is not a member of, and the bootstrap FarolAdmin gets the same `404`; a malformed id gets `400`; a later story's operation gets `501`; the correlation id comes back. It also checks that the schema comes from Flyway and that Events consumes `events.auth-users-events`. It runs locally after `up --wait` too: `python3 infra/tests/events_smoke.py infra/.env`.
- **A new RabbitMQ password** in `infra/.env` is applied at the next `up`. A changed queue argument in `topology.json` makes `rabbitmq-setup` fail: reset with `down -v`. **The broker's admin** is created only on an empty volume: after updating from a version without it, reset once with `down -v`.
- **Changing the realm or a database value** (`infra/keycloak/farol-realm.json`, `infra/postgres/10-create-databases.sh`, a database or Keycloak password in `infra/.env`): the realm is imported and the databases are created only on the first start, so reset with `docker compose -f infra/compose.yaml down -v`, then `up` again. This deletes all local data.
- A missing value in `infra/.env` stops `up` with the variable's name.
- **Auth & Users' settings** come from `infra/.env` through `infra/compose.yaml`, with no new variable: `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` (database and owner `auth_users`, `AUTH_USERS_DB_PASSWORD`), `SPRING_RABBITMQ_HOST`, `_USERNAME`, `_PASSWORD` (`auth-users`, `RABBITMQ_AUTH_USERS_PASSWORD`), `FAROL_ISSUER_URI` (the public issuer, equal to the token's `iss`), `FAROL_JWKS_URI` (Keycloak's keys on the internal address, fetched at the first token) and `FAROL_BOOTSTRAP_FAROL_ADMIN_EMAIL` (optional: an empty value grants nobody, spec D6). It waits only for PostgreSQL: the outbox keeps events until RabbitMQ accepts them.
- Build and test one module: `mvn -f backend/<module>/pom.xml verify`. Reports', Auth & Users' and Events' integration tests (`*IT`) start PostgreSQL and RabbitMQ with Testcontainers, so they need Docker; those image builds run only `mvn package` (#194, #191, #192). Web app with live reload: `cd frontend`, `npm ci`, `npm run dev` (`http://localhost:5173`).
- **Each Java image keeps its Maven dependencies between builds** (#219): a BuildKit cache per service, `farol-maven-<service>`, kept by Docker and never copied into an image. The first build downloads what each service needs; later builds download only what is new. One cache per service, so the six images still build at the same time and no two Maven processes write the same files (a shared one failed in CI). Maven retries Maven Central up to 10 times when it refuses (`429 Too Many Requests`), and each build drops the `*.lastUpdated` markers it finds in its cache, so a dependency that failed to resolve once is tried again instead of being refused for up to 24 h. It needs BuildKit, the default builder of Docker Desktop and of Docker Engine since 23.0. `build --no-cache` also starts these caches empty, so it downloads everything again. To empty them by hand: `docker builder prune --filter type=exec.cachemount`.
- `ci.yml` runs `mvn verify` in every module, fills those Maven caches with what those builds downloaded (so the image builds download nothing; the cache ids come from the backend `Dockerfile`s, and a backend Dockerfile without a Maven cache fails that step), starts this stack with `infra/.env.example`, checks that each database user reaches only its own database, runs [`infra/tests/identity_smoke.py`](../../infra/tests/identity_smoke.py) (PKCE login with a test account, token claims, refused paths, realm settings, the Auth & Users service account), runs [`infra/tests/reports_smoke.py`](../../infra/tests/reports_smoke.py) (Reports refuses a missing or tampered token and accepts a real Keycloak token, #194), runs [`infra/tests/auth_users_smoke.py`](../../infra/tests/auth_users_smoke.py) (through the gateway: no token or a tampered token gets `401`, Rita's real token creates her account, the bootstrap account is FarolAdmin, a later story's operation gets `501`, the correlation id comes back, and the outbox rows of those first calls are published, #191), checks from a service container that the issuer is the public URL and the keys are on `keycloak:8080`, checks that only `127.0.0.1` is published and that PostgreSQL and Keycloak are hardened, and checks each status through the gateway and the web page, on every pull request, push to `main`, tag and manual run. The identity check also runs locally: `python3 infra/tests/identity_smoke.py infra/.env`. The Auth & Users check also runs locally after `up --wait`: `python3 infra/tests/auth_users_smoke.py infra/.env`. [`dependency-review.yml`](../../.github/workflows/dependency-review.yml) runs `actions/dependency-review-action` on every pull request and fails on a dependency with a high or critical vulnerability (#191); it needs the repository's dependency graph.
- Each service keeps the layout of Reports: package `pt.isep.labdsof.<service>` (`authusers` for Auth & Users) with `adapters/in/web`, `application` and `domain`, its own `pom.xml` and multi-stage `Dockerfile`.
- Differences from the target below: the web app's Compose service is `frontend` (§1 calls it `web`), and the health checks call `/api/v1/status` instead of `/actuator/health` (Auth & Users' `/actuator/health` needs a token).

The Sprint A minimum (Reports alone, with the synthetic in-memory demo and Swagger on `127.0.0.1:8080`, #166) is the tag `v0.1.0`. Check it out to follow its [run instructions](plans/local-walking-skeleton.md#3-run-and-demonstrate-from-the-repository-root); [evidence](evidence/walking-skeleton-2026-10-31.md). The demo was removed from Reports in #194.

The procedures in §§1–8 are the **Sprint B target**. Follow each one only once what it needs exists: authentication (#190), each service's database and events (#191 to #194, #201). Keycloak and PostgreSQL exist since #189.

## 1. What runs

One Compose file, `infra/compose.yaml`. Images are built from the repository: there is no registry (guide H7).

| Compose service | What it is | Profile | Port published |
|---|---|---|---|
| `auth-users`, `events`, `reports`, `transports`, `badges` | The five Spring Boot services, one container each, image `farol-<service>:${VERSION}` | default | none |
| `gateway` | The API gateway, image `farol-gateway:${VERSION}` ([ADR-013](adr/0013-api-gateway.md)): the only entry point of the API, with CORS, JWT validation and the rate limits | default | `0.0.0.0:8080` |
| `keycloak` | The identity provider ([ADR-006](adr/0006-identity-and-access.md)), pinned version built with `infra/keycloak/Dockerfile`, realm imported from `infra/keycloak/farol-realm.json` | default | `127.0.0.1:8180` (the demo runs only on the laptop, 15 November 2026) |
| `web` | Serves the React app, image `farol-web:${VERSION}`. The app calls the gateway and Keycloak directly | default | `0.0.0.0:${WEB_PORT}` (3000, guide F2) |
| `postgres` | **One** PostgreSQL instance with **six databases and six users**: one per service and one for Keycloak. No user can read another's database (ADR-003) | default | none |
| `rabbitmq` | The broker, with a fixed node name, a named volume and one user per service (ADR-006) | default | `127.0.0.1:15672` (management UI) |
| `wiremock` | WireMock in front of Porto Digital and of the AI provider (the local `ai` container, guide K1): proxy, recorded responses and failure stubs (guide J2, J4). Its root is [`infra/wiremock/`](../../infra/wiremock/README.md), mounted at `/home/wiremock`, with the recorded Porto Digital stubs. Transports and Reports point at it by default | default | `127.0.0.1:8090` (admin API) |
| `ai` | Local model: Ollama with `qwen3:4b` (guide K1, [AI plan](plans/ai-provider-evaluation.md)). Reports reaches it through `wiremock`. *20 November 2026 (#229, [spec US-34](../specs/US-34-ai-triage-suggestion.md) D7, D10): the service is in `infra/compose.yaml` (same image and settings as the evaluation runs, a model volume, no published port). Reports calls it directly at `http://ai:11434` when `FAROL_AI_URL` is set in `infra/.env`; empty by default, so CI and the default stack never call or download a model and every request reads `NotTriaged`/`Unavailable`. WireMock in front of it is left to the degraded-mode work (US-36). Pull the model once: `docker compose -f infra/compose.yaml exec ai ollama pull qwen3:4b`. The model's offline gate fails (#224, #227), so its suggestions are not release evidence* | `ai` | none |
| `prometheus`, `grafana` | Observability stack ([ADR-012](adr/0012-observability.md), accepted architecture) | `observability` | `127.0.0.1` only |

- **Only the gateway and the web app are published to the network** (`0.0.0.0`, ADR-013). Keycloak and three admin pages are published **only on `127.0.0.1`**, so only the demo laptop opens them: Keycloak (its login pages share the port with its admin console, and the demo runs only on the laptop, 15 November 2026), RabbitMQ's management UI, WireMock's admin API (the presenter uses it to trigger D1 and D2, §8) and Grafana. Nothing else is published: the services, PostgreSQL and RabbitMQ's AMQP port are reachable only inside Compose (assessment §9).
- **One PostgreSQL instance, not six.** Separate databases with separate users keep the ownership rule. More containers would only add memory. Supersedes the "five PostgreSQL containers" suggestion in the [deployment research](../Research/architecture-investigation/deployment.md) (G6).
- An init script in `infra/postgres/` creates the six databases and users from the `.env` values on the first start.
- Third-party images (Keycloak, PostgreSQL, RabbitMQ, WireMock, observability) are pinned to an exact version in the Compose file and change only in a PR (guide H7).
- Health checks: `postgres` (`pg_isready`), `rabbitmq` (`rabbitmq-diagnostics ping`), `keycloak` (`/health/ready` on its management port), the gateway and each service (`/actuator/health`). Services and Keycloak wait for their database to be healthy (`depends_on: condition: service_healthy`). They do **not** wait for RabbitMQ or Keycloak: they reconnect on their own, fetch Keycloak's keys when the first token arrives (ADR-006), and a help request is still saved to the outbox while the broker is down (§9 of [`architecture.md`](architecture.md)).
- **One-shot containers and `up --wait`** (#220). `--wait` expects every container to be running or healthy and fails on one that has exited, even with exit 0 (`container …-rabbitmq-setup-1 exited (0)`). The one exception is a service that another service depends on with `condition: service_completed_successfully`: `--wait` then waits for it to finish with exit 0, and fails with its exit code otherwise. This is the same in Compose 2.38 (GitHub runners) and 5.x. So the web app, which never talks to the broker, depends on `rabbitmq-setup` that way; no backend service does (ADR-011). Without it, `up --wait` failed whenever `rabbitmq-setup` finished before Keycloak started (three runs out of three on a Windows laptop on 17 November 2026). A new one-shot container needs the same kind of dependant; CI checks it for `rabbitmq-setup`.
- Named volumes for PostgreSQL and RabbitMQ. A volume is not a backup (§6).

## 2. Configuration and secrets

- `infra/.env.example` is versioned and lists every variable with a harmless example value. `infra/.env` is in `.gitignore`. Each person copies the example once: `cp infra/.env.example infra/.env`.
- Variables:
  - `VERSION`, `WEB_PORT` and `PUBLIC_HOST`: the address the browser uses, `localhost` for the demo and while developing (a LAN IP only if phones are used again, §8). The Keycloak hostname (`KC_HOSTNAME`), the token issuer the services check (`FAROL_ISSUER_URI`), the gateway's allowed origin and the web app's API and login addresses are built from it.
  - The PostgreSQL superuser and the six database users and passwords; the RabbitMQ admin and the five service users.
  - Keycloak's admin user, the `farol-auth-users` client secret and `FAROL_BOOTSTRAP_FAROL_ADMIN_EMAIL` (ADR-006).
  - `TRANSPORT_SOURCE_URL` (WireMock by default), the AI provider settings (guide K1) and `SPRING_PROFILES_ACTIVE`.
- No real secret goes into the repository, the images or the CHANGELOG. Passwords are generated per machine, never the image defaults. The realm export holds `${…}` placeholders, not secrets (assessment finding 8). K1 chose local Ollama, which needs no API key (guide K1, [ADR-005](adr/0005-ai-triage-provider-and-evaluation.md)); a hosted provider, if ever approved, would keep its key only in `infra/.env`.

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
- What this covers: going back to a known demo state, and moving that state to the backup laptop. What it does **not** cover: messages in RabbitMQ queues (no backup of queued messages), and the loss of the laptop's disk between dumps. The RPO for disk loss is **not guaranteed**. Retention is decided (NFR-19, [assessment §7](security-and-privacy-assessment.md)); only backup in production stays out of scope (guide D5, 🔮).
- The time to roll back and to restore (RTO) is measured once the Sprint B slice has databases, and written here; the in-memory Sprint A skeleton cannot measure it (§9, #166). Until then, no recovery figure is claimed.

## 7. Smoke test

A checklist of commands, run by hand after starting, updating or rolling back. It passes only if every line passes.

| # | Check | Command | Pass |
|---|---|---|---|
| 1 | Every container is healthy. The health checks call `/actuator/health` and Keycloak's `/health/ready` | `docker compose -f infra/compose.yaml ps` | Every container `running (healthy)` |
| 2 | The web app opens | `curl -fsI http://localhost:${WEB_PORT}/`, and open it in the laptop's browser at phone width | HTTP 200; the page loads |
| 3 | The gateway routes and validates tokens | `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/v1/me` | `401`: the call reached the gateway and was refused without a token |
| 4 | Login works | Log in with a demo account on the laptop (`localhost`) | Keycloak's page opens; back in the app, the account's name is shown (`GET /me`) |
| 5 | One round trip through RabbitMQ | One service publishes an event and another consumes it. The exact command is written here in Sprint B: the Sprint A skeleton has no broker (#166) | The consumer shows the change; the queue is empty afterwards |

`ci.yml` exists since #167 ([ADR-010](adr/0010-cicd-strategy.md) point 9). In Sprint B the same checks, without the browser and the login, are added to it, to run in CI with Compose on every push to `main`, every tag and manual runs (Sprint B). Today `ci.yml` (#167) runs the Sprint A skeleton's smoke test instead (status, synthetic create and read, Swagger) on every pull request, push to `main`, tag and manual run.

## 8. The demo

**Machine.** One team member's laptop runs the demo. A second laptop is ready as a backup: same tag, same `.env`, the latest dumps restored, smoke test passed the day before. The laptop has full-disk encryption and a screen lock: it holds every database (assessment §9).

**Phones: not used in the demo** (decided on 15 November 2026, [record](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md) decision 10). The participant and field-team pages are shown in the laptop's browser at phone width (device mode), on `localhost`. Over plain HTTP on a LAN anyone on the network could read the tokens, and the browser blocks the Web Crypto call that the PKCE login needs on a page that is not HTTPS or `localhost`, so login would fail on a phone ([assessment](security-and-privacy-assessment.md) §9, finding 2); on `localhost` neither problem exists. HTTPS (e.g. a certificate from a local authority such as mkcert) is revisited for the phone's location in Sprint C, which also needs a secure page.
- Turning off PKCE, token validation, CORS or Keycloak's HTTPS requirement to make the demo work is **not allowed** (assignment §11). Every security control stays on; D1 and D2 break a dependency, not a control.

**Degraded modes** (scenarios D1 and D2, [`features.md` §3.1](../Product/features.md); the demo script and the values are in guide J2, [`architecture.md`](architecture.md) §9.3):

| Show | How |
|---|---|
| D1 · AI unavailable | Add an AI failure stub (dropped connection, slow answer) in WireMock through its admin API, and remove it to recover ([`architecture.md`](architecture.md) §9.3). Also: `docker compose -f infra/compose.yaml stop ai`, or start without the `ai` profile |
| D2 · Transport source unavailable, late or invalid | The demo always goes through `wiremock`. Add a failure stub (no answer, late, invalid data) through its admin API, or `docker compose -f infra/compose.yaml stop wiremock` |
| Broker down | `docker compose -f infra/compose.yaml stop rabbitmq`: requests are still saved (outbox) and catch up after `start rabbitmq` |
| Another service down | `docker compose -f infra/compose.yaml stop badges`: the other services keep working |

## 9. What is still open

- The minimum RAM of the demo laptop, and the measured RTO for rollback and restore (guide G7): the in-memory Sprint A skeleton cannot measure them (#166), so they are measured with the Sprint B slice.
- The exact round-trip command in the smoke test: Sprint B slice.
- Closed: HTTPS for phones is not needed for the demo, which runs only on the laptop (§8, 15 November 2026); revisited for the phone's location in Sprint C.
- Closed: the AI container is Ollama with `qwen3:4b` (guide K1, [ADR-005](adr/0005-ai-triage-provider-and-evaluation.md)); the degraded-mode script and x = 30 s, y = 5 min (guide J1, J2; [`architecture.md` §9](architecture.md#9-degraded-modes)). Still open: the model measurements on the demo laptop (guide K1).
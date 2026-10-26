# Walking skeleton and local deployment decisions

**Issue:** [#58](https://github.com/tomasoliveira21/LABDSOF/issues/58) · **Tracker:** [#147](https://github.com/tomasoliveira21/LABDSOF/issues/147) · **Owner:** Tomás Oliveira · **Updated:** 26 October 2026

**Documentary decision complete for PR review.** This document selects the scope, topology, delivery and rollback procedure. It does not claim that the application, pipeline or recovery drill has already run. Issue #58's executable deliverable remains open until its evidence exists; reviewing this decision is not that execution evidence.

## 1. Basis in the supplied course slides

Sources supplied for this task: *Product_Management_Slides.pdf*, PDF pages **23–25**, especially **Example Walking Skeleton, Scenario A** (page 25); *Mapping_Prioritization_Metrics_Slides.pdf*, PDF pages **4–5**, **Anatomy of a story map** and **Running a story-mapping workshop**. The files are course references, not new repository instructions.

The first example is a button sending a very small DTO to a backend that persists it; later increments add attributes and business rules. The mapping slides place the smallest working path across the user's activities in the first horizontal release. Therefore R0 must prove a real write/read across architectural components, not just health endpoints, and must not expand into the whole Sprint B journey.

**Choice for Farol: Scenario A**, retaining Farol's already accepted security and architecture constraints. A user presses **Send demo help request** for one seeded synthetic event; Reports saves the minimal request and the page displays the stored request/status. One seed category and map point avoid building the full input/map UI. The control-room demo page reads that saved request. AI, dispatch, badge rules and transport integration are later slices.

## 2. Walking-skeleton slice and release cut

| Backbone activity | R0: thin working path | Later increment |
|---|---|---|
| Access | Keycloak synthetic demo login, existing membership and permission checks | Registration, account management and invitation workflows |
| Choose event | One seeded published event; no event-creation UI | Real event catalogue, administration and membership flows |
| Ask for help | One button sends fixed category, map point and unique client request id | Editable category/text/map form, full validations and user stories |
| Persist and display | Reports transaction saves the request + outbox; UI reads the saved id/status | AI suggestion, routing, responder lifecycle and full audit views |
| Operate | Start the local stack, test, replace one image and demonstrate persistence | Load evidence, complete dashboards and production-style recovery drills |

Concrete path: **React → Spring Cloud Gateway → Reports REST → Reports PostgreSQL database → REST read → React**, with **Keycloak** authentication and **Auth & Users** supplying the minimum seeded membership/permission path. Reports' outbox publishes the minimal notification to its triage queue and an idempotent technical consumer records delivery; it makes **no model call in R0**. This is real broker wiring, not completed F8 triage. No synchronous service-to-service domain REST is introduced.

At least Reports and Auth & Users have executable independently built containers. The other three services retain independent project/image/health scaffolds only; no complete business modules are required for R0. This preserves the five-service architecture without turning the walking skeleton into five complete products.

### Minimal HTTP and data contract

Use the API outline's paths, under `/api/v1`, coordinated with [PR #124](https://github.com/tomasoliveira21/LABDSOF/pull/124):

- `POST /api/v1/events/{eventId}/help-requests`: fixed synthetic `category`, map position and `clientRequestId`; server identifies the caller from its token. Return `201` with the stored id/status; a duplicate id returns the existing request rather than another row.
- `GET /api/v1/events/{eventId}/help-requests/{requestId}`: owner sees their status; the seeded authorised control-room identity can read the operational view. Invalid/expired tokens, another event and another requester's private data remain denied.
- The demo DTO is a strict subset of the product contract, not a parallel permanent API. Hand-write the contract before implementation; full domain rules are added with their stories.
- Persist request, event/account references, status, submission time and outbox in Reports only. AMQP notification carries request id/correlation metadata, not raw incident text or coordinates; the worker reads the row through the Reports application layer. Consumption deduplicates by message id.

The slide's absence of business validation is about slicing, **not permission to disable login, isolation, DTO syntax/size checks or rate limits**. Synthetic demo accounts/data are explicitly labelled.

## 3. Answers to G1–G7, F2 and C1

| Item | Selected answer |
|---|---|
| **G1 — platform** | **One local laptop with Docker Engine/Desktop and Compose v2. No cloud deployment.** Professor's authorisation and cost reason remain in [ADR-011](../adr/0011-deployment-and-infrastructure.md) |
| **G2 — infrastructure cost** | No new hosted VM/database/monitoring or API subscription for this scope. Existing hardware/electricity are not asserted free. Keep the [dated cloud assessment](../../Research/architecture-investigation/deployment.md) and [operating cost estimate](../../Deliverables/operating-cost-estimation.md); explain costs as the reason cloud was omitted |
| **G3 — region** | No cloud region: processing/storage on the local delivery machine in Portugal. No provider receives real help-request data by default; future external processing needs its own review. Local placement alone is not a GDPR-compliance claim |
| **G4 — environments** | `dev` locally with synthetic seeds; isolated ephemeral `test` stack in CI; `demo` locally from a reviewed release tag with synthetic data. Separate Compose project names/volumes/env files; no paid production environment |
| **G5 — IaC** | Versioned `infra/compose.yaml`, Dockerfiles, service settings, PostgreSQL bootstrap and Keycloak realm. **No Terraform/Bicep provisioning in current scope.** Bicep is only the retained Azure alternative |
| **G6 — execution** | Compose default profile runs the application/infrastructure; optional `observability` and `ai` profiles add telemetry and local inference. One command path and one base network/volume definition, not competing independent stacks |
| **G7 — rollback** | Build the previous reviewed tag, replace affected service images and rerun smoke tests. Flyway migrations stay expand/contract compatible; never undo DB migrations automatically. Take consistent demo dumps before change; restore from those if a demo-state reset is required |
| **F2 — web hosting** | React static build served by a local web-server container, `WEB_PORT=3000`; browser calls the gateway on 8080 and Keycloak on 8180 using configured origins. No Vercel/Azure static site is required |
| **C1 — RabbitMQ hosting** | One local RabbitMQ container, stable node name, named data volume, per-service users/vhosts/permissions. AMQP private to Compose; management UI only `127.0.0.1:15672`; metrics private to the telemetry network |

### Local topology and resources

Five service containers + gateway + web + Keycloak + one PostgreSQL + RabbitMQ + WireMock form the planned default topology. PostgreSQL has **six databases/users: five domain-service databases and one Keycloak database**, not six database machines. Three domain services may be health-only scaffolds in R0. Runtime/user secrets remain in ignored local configuration. No database, AMQP, model or scrape endpoint is published to the LAN.

The local baseline is the [delivery procedure proposed in PR #130](https://github.com/tomasoliveira21/LABDSOF/pull/130); CI/tag semantics follow [PR #132](https://github.com/tomasoliveira21/LABDSOF/pull/132) and [PR #128](https://github.com/tomasoliveira21/LABDSOF/pull/128): **build locally from tags, no registry, CI never connects to anyone's laptop**. The chosen R0 implements this contract; there is no unresolved registry-versus-pull decision here. Integrating those owners' documentation is separate from choosing this approach.

**Capacity planning, not a measured minimum:** budget a 16 GiB host and 8 GiB for Docker for base delivery/telemetry; full simultaneous local inference may require a larger allocation/host, so budget 24 GiB host/12 GiB Docker for that demo profile and measure before release. Inspection on 26 October found a 16 GiB host and Docker configured with about 4 GiB RAM/8 CPUs; settings were not changed. That is an environment observation, not proof the target stack fits. R0 does not start the AI profile. Reduce concurrent optional profiles or use another local team machine if the measured host limit is exceeded; do not silently replace local hosting with a paid cloud machine.

## 4. Startup, smoke check and recovery procedure

**Implementation command contract** (files below are to be supplied by #58; no claim they exist/run yet):

```sh
cp infra/.env.example infra/.env
# Set local secrets, PUBLIC_HOST=localhost, WEB_PORT=3000 and VERSION=dev.
docker compose --env-file infra/.env -f infra/compose.yaml -p farol-dev build
docker compose --env-file infra/.env -f infra/compose.yaml -p farol-dev up -d --wait
python3 scripts/smoke_local.py --base-url http://localhost:3000 --gateway-url http://localhost:8080
```

The smoke script's exact checks are fixed: all required containers healthy; web HTTP 200; gateway `/api/v1/me` answers 401 without a token; browser logs in through Keycloak; clicking the synthetic request button yields a stored id; an authenticated read returns the same row; duplicate submission does not duplicate it; the outbox notification is consumed once logically. CI automates infrastructure/negative-auth/queue checks and integration fixtures; manual browser login and the actual demo laptop are checked separately. CI success is not a phone/LAN test.

**Update one service:** build `reports`, then `up -d --no-deps reports`; other service processes keep their existing images. **Stop:** `down` without `-v`, preserving local volumes. **Reset synthetic demo only:** explicit `down -v`, then reseed; never use this as routine rollback.

**Before every demo/update:** stop writes, take `pg_dump -Fc` for all six databases, export broker definitions and record the current git tag/configuration checksum; resume writes afterwards. Dumps live in ignored encrypted local storage and are copied to the backup laptop. Definitions are not a backup of queued messages.

**Rollback:** stop the changed worker/service, check out the previous reviewed tag, set its image version, build and replace that service, then run the same smoke checks; verify outbox/queue drain and state. **Restore:** quiesce the stack, restore the six matching demo dumps, import the matching realm/configuration and replay only retained outbox records idempotently. Disk loss between dumps and queued messages not recoverable from retained history have no guaranteed RPO.

RTO targets remain **≤10 min code rollback / ≤60 min demo-state restore**, evaluated by a timed drill; they are not measured results. RPO is the last consistent demo dump, not zero host-loss data loss. Production retention/backup policy stays outside this local-demo decision.

**Demo access decision:** R0 uses the laptop browser and its responsive/device mode on `localhost`; there is no unresolved requirement to make plain-HTTP PKCE work on a phone. Real-phone LAN login is a later HTTPS integration gate owned with Guerra. Never disable PKCE/token validation/CORS to demonstrate it.

## 5. Analysis → design → tests → implementation → evidence

The course slide on the software-engineering process (Product Management, PDF p.23) applies to configuration as well as Java. This document supplies analysis/design and the test plan; code/tests and their execution records follow it.

| Acceptance | Required check |
|---|---|
| Real thin slice | Button → authenticated API → durable DB row → displayed status, with exactly one synthetic event and without AI/routing/transport business features |
| Isolation | Unauthenticated, other-event and other-requester access are denied; service database users cannot open another service's database |
| Independent delivery | Two executable services and five independent scaffolds/images; replace one without rebuilding all |
| Persistence/queue | Request/outbox atomicity, logical deduplication and broker round trip; ordinary container replacement preserves data |
| CI/delivery | Reviewed CI jobs build/test/images and execute the smoke contract; local machine follows the same startup procedure |
| Recovery | Record tag/configuration/host, time the rollback/restore, verify resulting rows/queue state; report failures |
| Scope explanation | Report/presentation states local-only delivery was professor-authorised because of cost; cloud evaluation is retained |

**Documentation is answered; runtime evidence is not invented.** The executable R0 issue remains open until these tests run. The later observability and AI PRs complete their own concrete decisions; they are not prerequisites for calling a DB-backed non-AI R0 a walking skeleton.

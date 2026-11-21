# #193 · Transports service

> **Issue:** [#193](https://github.com/tomasoliveira21/LABDSOF/issues/193) · **Type:** chore · **Owner:** António Guerra · **Spec rules:** [README](README.md)
> **Sources:** [ADR-001](../Technical/adr/0001-architectural-style.md), [ADR-003](../Technical/adr/0003-database-and-data-ownership.md), [ADR-004](../Technical/adr/0004-service-communication.md), [ADR-006](../Technical/adr/0006-identity-and-access.md), [ADR-008](../Technical/adr/0008-transport-data-source.md), [ADR-011](../Technical/adr/0011-deployment-and-infrastructure.md), [ADR-012](../Technical/adr/0012-observability.md), [ADR-013](../Technical/adr/0013-api-gateway.md), [API outline](../Technical/api-outline.md) §1, §5 and §7, [`architecture.md`](../Technical/architecture.md) §5, §7 and §9.1 (J1), [domain model](../Technical/domain-model.md) (Transports), [`features.md`](../Product/features.md) US-40 and US-41, [`infra/wiremock/README.md`](../../infra/wiremock/README.md), [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md) decisions 9 and 13, and the specs of Auth & Users [#191](191-auth-users-service.md), Events [#192](192-events-service.md) and Reports [#194](194-reports-postgres-outbox.md), whose patterns this service follows.

## 1. Acceptance criteria

The first four are the issue's. AC-5 to AC-7 make its scope boxes "RabbitMQ wiring", "first use case" and "structured logs with correlation ID" testable.

| # | Given / when / then | Covered by |
|---|---|---|
| AC-1 | Given the Compose stack, when it starts, then the Transports container is healthy, uses only its own database, and its schema (TransportOption, TransportDeparture, BusVehicle, BusObservation, the source imports, the copies of memberships, events and teams, processed messages) comes from Flyway | `193 AC-1 …` in `MigrationIT`, `StatusEndpointIT`; CI *Build and start the stack* and the database isolation step; `infra/tests/transports_smoke.py` (Flyway `V1`) |
| AC-2 | Given a request with a valid token, then the token is validated against Keycloak; a missing, expired, tampered, wrong-issuer, wrong-audience or unsigned token, or a `sub` that is not a UUID, gets `401` Problem Details | `193 AC-2 …` in `SecurityIT`; CI step *Check that Transports validates Keycloak tokens and reads the bus source through WireMock* (a real token through the gateway) |
| AC-3 | Given the CI pipeline, when it runs, then the service is built, tested (gates of the [README](README.md)) and started in the Compose smoke test | CI *Build and test every module*, smoke steps |
| AC-4 | Given `contracts/openapi.yaml`, then it holds the Sprint B endpoints of API outline §5 (D6), the service serves exactly those paths, and each operation of a later story answers `501` naming it | `193 AC-4 …` in `ContractIT`; smoke (`501` naming US-41) |
| AC-5 | Given `MembershipChanged`, `EventChanged` or `TeamChanged` in `transports.auth-users-events` or `transports.events-events`, then the copy is upserted by version; duplicates (`messageId`) and older versions change nothing; an invalid message goes to its dead-letter queue with the reason; `PointOfInterestChanged`, `UserStatusChanged` and `UserDeleted` are acknowledged and logged | `193 AC-5 …` in `MembershipTest`, `EventCopyTest`, `TeamTest`, `LocalCopyServiceTest`, `LocalCopiesIT`, `ConsumedContractIT`; smoke (both queues consumed as `transports`) |
| AC-6 | Given the source URL (WireMock in Compose), when the scheduled job runs, then it sends the geo-query of ADR-008 with the J1 timeouts and records one attempt: `Succeeded` with the vehicle count, `Unavailable` (timeout, connection error, HTTP error) or `Invalid` (the answer is not a list of NGSI entities) | `193 AC-6 …` in `SourceImportTest`, `SourceImportServiceTest`, `PortoDigitalSourceTest`, `SourceImportJobTest`, `SourceImportIT`; smoke (a `Succeeded` import) |
| AC-7 | Every request, consumed message and import run carries a correlation id in the structured logs; no token, e-mail or name is logged (M-10) | `193 AC-7 …` in `CorrelationIdFilterTest`, `LoggingIT`, `LocalCopiesIT`, `SourceImportJobTest` |

## 2. Decisions

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **Same structure and patterns as Events (#192) and Auth & Users (#191)**: Spring JDBC with hand-written SQL, resource server, Problem Details, correlation filter, `openapi-generator` with `skipDefaultInterface` and no coverage exclusion (`ContractIT` covers the generated code), `*IT` tests in failsafe, image build with `package`, and the quality gates of the [README](README.md) | The issue says "same layout as `backend/reports`". Events and Auth & Users are that layout plus the generator (#191 D2), built to the gates the team agreed, and Reports adopted the gates in #194. One way of building the services the team reviews |
| D2 | **No outbox.** "RabbitMQ wiring for its events" means the two queues Transports consumes | The issue lists an outbox table, but API outline §5 and §7.4 say Transports **publishes nothing**, `topology.json` has no `farol.transports` exchange, and the `transports` broker user may write only to `farol.dead-letters` (#212). *An unused outbox* would be code nobody calls, held to the coverage gate, and would need a new exchange and permission. Decided by the owner on 21 November 2026 |
| D3 | **Consumers of `transports.auth-users-events` and `transports.events-events`** (pattern of #192 D2 and #214 D7–D9): envelope and payload validated; `processed_message` plus a versioned upsert in one transaction; an invalid message goes to its dead-letter queue with `x-farol-error`; other failures are tried 4 times in all (waits of 1 s, 2 s and 4 s), then dead-lettered. **Stored now:** `MembershipChanged` (the `Removed` rows are kept, #192 D3; a missing `status` is read as `Active`), `EventChanged` (status and version) and `TeamChanged` (permissions, deleted, version). A `TeamChanged` for a team stored under another event changes nothing and is logged at `WARN` (#214 D7). **Acknowledged and logged** until their stories: `PointOfInterestChanged` (US-20, US-37), `UserStatusChanged` and `UserDeleted` (account deletion) | US-41, the owner's next story, needs all three copies: *Member* to read bus observations, refusing a suspended event (API outline §7.4), and `Transport.Edit` to add an external route. *Memberships only* (as #192) would split one consumer across two pull requests. Decided by the owner on 21 November 2026 |
| D4 | **Schema `V1__transports.sql`**: `transport_option` (`option_id`, `event_id`, `mode` check `Shuttle/ExternalBus`, `name` required for a shuttle, `external_route` required for an external bus, `stop_poi_ids uuid[]`, `version`, who and when), `transport_departure` (`departure_id`, `option_id` with cascade, `event_id`, `destination`, `scheduled_at`, `expected_at`), `bus_vehicle` (`vehicle_id`, `data_provider`, `external_entity_id` unique per provider, `fleet_vehicle_id`), `bus_observation` (one row per vehicle, the latest valid one: longitude, latitude, `route_id`, `observed_at`, `retrieved_at`), `source_import` (one row per attempt: start, end, outcome, vehicle count or error), `membership_copy`, `event_copy`, `team_copy`, `processed_message`. `event_id` on every event table (ADR-003) | The four entities the issue names, with the attributes of the domain model. No `origin` column: the mode decides it (a shuttle is manual, an external bus is external). `bus_vehicle` and `bus_observation` have no `event_id`, because external bus data spans events (domain model, *How to read the model*). US-38's delay and cancel columns come with US-38 (expand and contract) |
| D5 | **First use case: the scheduled read.** Every 30 s (fixed delay, so two runs never overlap) a job calls `GET {source}/v2/entities?q=vehicleType==bus&georel=near;maxDistance:2000&geometry=point&coords=41.1724,-8.6841`, with a 3 s connect and a 5 s response timeout (J1), reads the answer as a list of NGSI entities and saves one `source_import` row: `Succeeded` with the number of vehicles, `Unavailable` (timeout, connection error, HTTP error) or `Invalid` (not a JSON array of objects with an `id`). An error is stored without NUL and cut to 500 characters. Off with `farol.transport.source.enabled=false`; on without a URL, start-up fails | The issue's "a scheduled job reads the external bus source through WireMock". Everything US-40 (#84) asks for stays there: storing `BusVehicle` and `BusObservation`, the validation rules, rejecting an invalid import as a whole, the freshness limit, the retry and the circuit breaker of J1. *Storing observations here* would take US-40's first acceptance criterion into a set-up issue. Decided by the owner on 21 November 2026 |
| D6 | **Contract**: the operations of API outline §5 whose stories include a Sprint B story (US-40 or US-41): `GET /events/{eventId}/bus-observations` (`501` "US-41"), `GET /events/{eventId}/transport-source-status` (`501` "US-40") and `POST /events/{eventId}/transport-options` (`501` "US-41"), plus `GET /status`. Their request and response shapes are proposals until US-40 and US-41 | The issue asks for "the Sprint B endpoints of §5". `GET /transport-options` (US-39), `PATCH`/`DELETE` (US-37) and `/delay`/`/cancel` (US-38) are Sprint C. The gateway already routes all three path groups to Transports (ADR-013) |
| D7 | **Venue coordinates from configuration**: `farol.transport.source.coords` = `41.1724,-8.6841` (the Queimódromo), `max-distance` = `2000` | Events has no venue location, and the WireMock stubs match the query text exactly. One import per deployment until an event carries its location |
| D8 | **WireMock in Compose**: the `wiremock` service (`wiremock/wiremock:3.13.2`, pinned by digest) with `infra/wiremock` mounted read-only at `/home/wiremock` and its admin API on `127.0.0.1:8090`; Transports reads `TRANSPORT_SOURCE_URL`, `http://wiremock:8080` by default, and does not wait for it | ADR-008 decision 4 and [`local-delivery.md`](../Technical/local-delivery.md) §1. A source that is down is scenario D2, not a start-up failure. Proxy mode to the real API (guide J4) is not configured here |
| D9 | **Correlation id**: header `X-Correlation-Id`, kept when it matches `[A-Za-z0-9-]{8,64}`, otherwise a new UUID (#191 D8); a consumed message uses its AMQP `correlationId`; each import run gets a new UUID | ADR-012 point 1; the import is a system action with no request behind it |
| D10 | **Security**: resource server as Events (issuer, JWKS and audience `farol-api` from configuration; a `sub` that is not a UUID gets `401`); public: `GET /api/v1/status` and `GET /openapi.yaml` | ADR-006. Membership checks come with the first implemented event endpoint (US-41) |
| D11 | **CI smoke** `infra/tests/transports_smoke.py`: `401` without a token and with a tampered one, `501` naming US-41 with Rita's real token, `400` for a malformed id, the correlation id echoed, Flyway `V1`, a `Succeeded` import with vehicles, both queues consumed by the `transports` user | Same checks as the smokes of #191, #192 and #194, plus the import |
| D12 | **Health**: Compose keeps `/api/v1/status` | `/actuator/health` needs a token on the API port (#191, #192). The private management port of #148 (PR #231) is taken by this service when that pull request is merged |

## 3. Features (build order)

1. Module, Flyway `V1` (D4) · `MigrationIT`, `StatusEndpointIT`
2. Copies in the domain and their use case (D3) · `MembershipTest`, `EventCopyTest`, `TeamTest`, `LocalCopyServiceTest`
3. Consumers and their JDBC adapter (D3) · `LocalCopiesIT`, `ConsumedContractIT`, `DeadLettersTest`
4. Security, Problem Details, correlation id (D9, D10) · `SecurityIT`, `LoggingIT`, the copied unit tests
5. Contract and its operations (D6) · `ContractIT`
6. The scheduled read and WireMock in Compose (D5, D7, D8) · `SourceImportTest`, `SourceImportServiceTest`, `PortoDigitalSourceTest`, `SourceImportJobTest`, `SourceImportIT`
7. CI smoke and documents (D11) · `infra/tests/transports_smoke.py`

## 4. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| A value outside the agreed lists, a version below 1 | Refused by the database | `MigrationIT` |
| An external route without its route reference, a shuttle without a name | Refused by the database | `MigrationIT` |
| A bus position outside the globe | Refused by the database | `MigrationIT` |
| `MembershipChanged`, `EventChanged`, `TeamChanged` duplicate, older or equal version | Nothing changes | `LocalCopyServiceTest`, `LocalCopiesIT` |
| `TeamChanged` for a team stored under another event | Nothing changes; `WARN` log | `LocalCopyServiceTest`, `LocalCopiesIT` |
| `MembershipChanged` without `status` | Stored as `Active` | `LocalCopiesIT` |
| Bad JSON, `null`, no `eventId`, `schemaVersion` ≠ 1, unknown role or permission, `null` in `teamIds`, no `version` | Dead-lettered with the reason, not retried, nothing stored | `LocalCopiesIT` |
| Database failure while consuming | 4 attempts in all, then dead-lettered | `LocalCopiesIT` |
| `PointOfInterestChanged`, `UserStatusChanged`, `UserDeleted` | Acknowledged and logged, nothing stored | `LocalCopiesIT` |
| Transports starts before its broker user exists | Starts; consumes once the user exists | `LocalCopiesIT` |
| Token cases (as #191 §4) | `401` Problem Details | `SecurityIT` |
| Operations of later stories | `501` naming the story with a token, `401` without | `ContractIT`, `SecurityIT` |
| Malformed event id | `400` before the `501` | `ContractIT` |
| The coordinates written differently, a `404` from the source | `Unavailable` with `HTTP 404`, never `Succeeded` | `PortoDigitalSourceTest`, `SourceImportIT` |
| No bus near the venue (`[]`) | `Succeeded` with 0 vehicles | `SourceImportIT` |
| `503`, a dropped connection, an answer slower than the timeout | `Unavailable` | `SourceImportIT` |
| `200` with `{}`, HTML, `[1]`, an entity without `id`, truncated JSON | `Invalid` with the reason | `SourceImportIT` |
| An error with NUL or longer than 500 characters | Stored without NUL, cut to 500 | `SourceImportTest` |
| A run that throws (database down) | Logged; the next run still happens | `SourceImportJobTest` |
| Job switched on without a source URL | Start-up fails, naming the property | `SourceImportIT` |
| Token, e-mail or name in the logs | Never | `LoggingIT` |

## 5. Handoff and changelog

- **21 Nov 2026 · research.** Issue #193 (no comments), API outline §1, §5 and §7, ADR-008 and J1, domain model, US-40 and US-41, `infra/wiremock/`, `topology.json` and `setup.sh` (the `transports` user writes only to `farol.dead-letters`), the gateway routes (already in place), the specs of #191, #192 and #194, and the open pull requests that touch the same files: #231 (observability: Transports' `pom.xml`, Compose, CI) and #230 (Compose). The owner decided D2, D3 and D5 and the scope of the first use case.

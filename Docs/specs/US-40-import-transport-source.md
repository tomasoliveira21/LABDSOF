# US-40 · Import the external transport source

> **Issue:** [#84](https://github.com/tomasoliveira21/LABDSOF/issues/84) · **Type:** user story (F9, transport information and external integration) · **Service:** Transports · **Owner:** António Guerra · **Spec rules:** [README](README.md)
> **Sources:** issue #84; [`features.md`](../Product/features.md) US-40 and §3.1 (scenario D2); [ADR-008](../Technical/adr/0008-transport-data-source.md); [`architecture.md`](../Technical/architecture.md) §7, §9.1 (J1 values) and §9.2 (how D2 is observed); [domain model](../Technical/domain-model.md) (BusVehicle, BusObservation); [`infra/wiremock/README.md`](../../infra/wiremock/README.md); the Transports spec [#193](193-transports-service.md), whose job, adapter and `source_import` this story builds on; the proof of concept #48 (`PoC/src/api/fiware.ts` on its branch), which read the same entities.
> **Base:** stacked on `chore/193-transports-service` (PR #244) until it is merged, then rebased onto `main` (spec README, *Workflow*).
> **Decided by the owner on 21 November 2026:** a broken bus rejects the whole import (D2); a valid import replaces the buses in use (D4); Resilience4j for the retry and the circuit breaker (D5); the source-status endpoint stays with US-43 (D9).

## 1. Source

**Story:** as the Farol team, we want to import the external transport source regularly and validate it, so that participants get regular public transport data (assignment §6.5). Technical goal, no persona.

**This PR:** the scheduled import of #193 turns into the real one. It stores the buses near the venue (`bus_vehicle`, and each one's latest position in `bus_observation`), validates them, drops late positions, replaces the buses in use, and retries and pauses as J1 decided. **Not in it:** any endpoint that shows the buses (US-41), the source-status endpoint and the backoffice warning (US-43), the participant warning and `stale` (US-42).

| # | Acceptance criterion | Tests (name starts with `US-40 AC-<n>`) |
|---|---|---|
| AC-1 | Given the source answers with valid data, when the import runs, then the data is stored with the time of the import *(issue)* | `BusImportTest`, `SourceImportServiceTest`, `BusDataIT`, `ImportIT` |
| AC-2 | Given the source answers with invalid or incomplete data, when the import runs, then the import is rejected as a whole and the previous data stays in use *(issue)* | `BusImportTest`, `SourceImportServiceTest`, `ImportIT` |
| AC-3 | Given a bus whose position is older than the freshness limit (5 min), when the import runs, then that bus is left out and the rest of the import is stored | `BusImportTest`, `ImportIT` |
| AC-4 | Given a valid import, then it replaces the buses in use: a bus that is no longer in the answer is no longer in use | `BusDataIT`, `ImportIT` |
| AC-5 | Given the source fails (timeout, connection error, HTTP `5xx` or `429`), then the call is tried 3 times, 1 s and 2 s apart; if it still fails, the previous data stays in use; after repeated failures the source is not called for 60 s, then one trial call decides whether it is used again (J1) | `ResilienceIT` |
| AC-6 | Every run is recorded in `source_import` and logged: `Succeeded` with the number of buses stored, `Invalid` with the first broken bus and why, `Unavailable` with the last error or `circuit open` | `SourceImportServiceTest`, `ImportIT`, `ResilienceIT`, `SourceImportJobTest` |
| AC-7 | The import's state is measured: the circuit breaker `transport-source`, `farol.transport.import{result}` and `farol.transport.data.age` (architecture §9.2) | `MetricsIT` |

## 2. Quality gates

The gates of [#193](193-transports-service.md) for `backend/transports`, unchanged: `mvn verify` green; JaCoCo ≥ 90 % lines and branches, generated code included; PIT `DEFAULTS` ≥ 80 % on `domain` and `application`; Testcontainers with the Compose images (PostgreSQL, RabbitMQ, WireMock with the recorded stubs); every check step of `ci.yml` green locally before the pull request. No threshold is lowered, no test is skipped, no exclusion is added.

## 3. Decisions

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **Built on #193**: the same job (every 30 s, fixed delay), the same adapter and `source_import`, the empty `bus_vehicle` and `bus_observation` of `V1`. No new table | #193 D5 left exactly this story's part out of the first use case |
| D2 | **A broken bus rejects the whole import**, and the previous data stays in use (AC-2). A bus is broken when it has: no `id`, or the same `id` as another bus of the same answer; no `dataProvider`; a `location` that is not a GeoJSON `Point` with longitude in [-180, 180] and latitude in [-90, 90], or that is exactly 0,0; no route (`stcp:route:<route>` in `annotations`); an `observationDateTime` that is missing, unreadable, or more than 1 minute after the time of the import. `fleetVehicleId` is optional. The answer itself not being a list of entities stays `Invalid` as in #193 | **Decided by the owner on 21 November 2026**, as the story says. *Drop only the broken bus* (what the proof of concept did) keeps the good buses, but changes the story; none of the 300 recorded answers had a broken bus, and switching later is a small change. 0,0 and the 1-minute tolerance for clock drift are the proof of concept's and this spec's |
| D3 | **Late positions are dropped one by one**: a bus observed more than `farol.transport.source.freshness` (5 min) before the time of the import is left out, and the import is otherwise valid (AC-3). An import whose buses are all late stores none and is `Succeeded` with 0 | `features.md` §3.1: "late observations are dropped on import". Late is not broken: the source is working, the bus is old. Five minutes: ADR-008, the *y* of the meeting with the professor |
| D4 | **A valid import replaces the buses in use** (AC-4): each bus is upserted in `bus_vehicle` by (`data_provider`, `external_entity_id`) and its position in `bus_observation`; the positions of buses that are not in the import are deleted. Vehicles themselves are kept (they are the identity of a bus across imports). Positions, deletions and the `source_import` row are written in **one transaction**: a failed or rejected import leaves everything as it was | **Decided by the owner on 21 November 2026.** *Keep each bus until it is 5 min old* would show a bus that drove away for up to 5 minutes. "The buses near the venue now" is exactly the last valid import, which is also what US-42's "Data from HH:MM" names |
| D5 | **Resilience4j 2.4.0**, core modules `resilience4j-circuitbreaker`, `resilience4j-retry` and `resilience4j-micrometer`, configured in code from `application.yaml` with the values of J1: retry 3 attempts, waits of 1 s then 2 s; circuit breaker counting the last 6 calls, at least 3, opening at ≥ 50 % failed, open 60 s, then 1 trial call. Every value is a property under `farol.transport.source.retry` and `farol.transport.source.circuit-breaker`, so the tests can shorten the waits. **The retry wraps the circuit breaker** (§9.1), so every attempt counts for the circuit and nothing is retried while it is open. Retried and counted as failures: I/O errors, timeouts, HTTP `5xx` and `429`. Counted but not retried: other HTTP `4xx` (asking again will not change them). Neither retried nor counted: an answer that arrives but is invalid (the source is working). While the circuit is open the run records `Unavailable` with `circuit open` and calls nothing | **Decided by the owner on 21 November 2026.** The metric names of §9.2 are Resilience4j's. *By hand:* no dependency, but a second circuit breaker for the team to trust and metrics to make by hand. The Spring Boot starter is not used: its support of Boot 4 is uncertain, and the core modules need none. One failed import is 3 failed calls, which opens the circuit (§9.1: "one failed import opens it"); the worst run is 3 × 5 s + 1 s + 2 s = 18 s, under the 30 s interval |
| D6 | **The query asks for up to 1000 buses**: `&limit=1000` after the geo-query | The real API returns 20 entities when no limit is sent (review of #193); the recorded stubs ignore other parameters (`infra/wiremock/README.md`) |
| D7 | **What a bus is made of** (the adapter's job): `externalEntityId` = `id`; `dataProvider` and `fleetVehicleId` = those attributes' `value`; `routeId` = the `<route>` of the first `stcp:route:<route>` in `annotations`; position = `location.value.coordinates` (longitude first); `observedAt` = `observationDateTime.value` (ISO-8601, with or without a fraction of a second, `Z` or an offset). The time of the import (`retrieved_at`) is the clock's time when the answer arrived. The adapter only reads; the domain (`BusImport`) decides what is broken, late or kept | The rules live in the domain, where the mutation gate covers them. The format is the one of the 300 recorded answers and of the proof of concept |
| D8 | **What is recorded** (AC-6): one `source_import` row per run, as in #193. `Succeeded`: the number of buses stored; the number of late buses dropped goes to the log line. `Invalid`: the first broken bus in the answer's order, by its position from 0 as in #193, its `id` when it has one, and the reason (`entity 3 (urn:ngsi-ld:Vehicle:porto:stcp:bus:1126): no route`). `Unavailable`: the last error, or `circuit open`. Log line per run: outcome, buses, late, attempts, duration | US-43 reads the last `Succeeded` row for "the time of the last successful import"; US-42 its `finished_at` for "Data from HH:MM" |
| D9 | **The source-status endpoint stays with US-43**: `GET …/transport-source-status` keeps answering `501`, now naming US-43 instead of US-40 | **Decided by the owner on 21 November 2026.** US-40's criteria are about the import, nothing in Sprint B reads the endpoint, and its user is US-43's backoffice warning. #193 had named US-40 (its D6) |
| D10 | **Metrics** (AC-7): Resilience4j's Micrometer binding for the circuit breaker and the retry named `transport-source` (`resilience4j.circuitbreaker.state{name=transport-source}`); a counter `farol.transport.import{result}` (`succeeded`, `invalid`, `unavailable`); a gauge `farol.transport.data.age` in seconds since the last `Succeeded` import, read from `source_import` (`NaN` before the first one). They are in the application's `MeterRegistry`; Prometheus reads them once #231 is merged | §9.2 names them for the D2 panel of the dashboard (ADR-012) |

## 4. Features (build order)

| # | Feature | Tests |
|---|---|---|
| F1 | Domain: `BusReading` (what the adapter read, any field may be missing), `ObservedBus` (a valid bus), `BusImport` (the rules of D2 and D3: accepted buses and late count, or the first broken bus and its reason) | `BusImportTest` |
| F2 | Adapter: the readings from the NGSI entities (D7) and `&limit=1000` (D6) | `PortoDigitalSourceTest`, `PortoDigitalSourceIT` |
| F3 | Persistence: replace the buses in use in one transaction (D4) | `BusDataIT` |
| F4 | Use case: read, validate, store or reject, record (D2–D4, D8) | `SourceImportServiceTest`, `ImportIT` |
| F5 | Retry and circuit breaker (D5) | `ResilienceIT` |
| F6 | Metrics (D10); the `501` of the source-status endpoint names US-43 (D9); documents | `MetricsIT`, `ContractIT` |

## 5. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| A recorded answer (2 to 13 buses) | `Succeeded`, every bus stored with its route, position, observation time and the time of the import | `ImportIT` |
| `[]` (no bus near the venue) | `Succeeded` with 0; the buses in use are removed (none is near the venue now) | `BusImportTest`, `ImportIT` |
| One bus without `id`, `dataProvider`, `location`, route or `observationDateTime` | `Invalid` naming that bus and what is missing; the previous buses stay | `BusImportTest`, `ImportIT` |
| A position at 0,0, a longitude of 181, a latitude of -91, a `location` that is not a `Point` | `Invalid` | `BusImportTest`, `PortoDigitalSourceTest` |
| The same `id` twice in one answer | `Invalid` | `BusImportTest` |
| `observationDateTime` 61 s in the future / 59 s in the future | `Invalid` / accepted | `BusImportTest` |
| `observationDateTime` with `.00Z`, without a fraction, with `+01:00`; unreadable | Read as the same instant / `Invalid` | `PortoDigitalSourceTest` |
| A bus observed 5 min 1 s before the import / exactly 5 min before | Dropped as late / kept | `BusImportTest` |
| Every bus late | `Succeeded` with 0, late count in the log | `BusImportTest`, `ImportIT` |
| A late bus that is also broken | `Invalid`: broken wins over late | `BusImportTest` |
| No `fleetVehicleId` | Accepted, stored without it | `BusImportTest`, `BusDataIT` |
| Two routes in `annotations`, or none / `stcp:route:` with no route | The first one / `Invalid` | `PortoDigitalSourceTest` |
| A bus in the previous import that is not in this one | Its position is no longer in use; the vehicle row stays | `BusDataIT` |
| The same bus in two imports | One vehicle row, its position updated | `BusDataIT` |
| The database fails while storing | Nothing changes: no positions, no deletions, and the run logs the error (no row, as in #193) | `ImportIT` |
| `503` twice then `200` | `Succeeded` after 3 calls; waits of about 1 s then 2 s | `ResilienceIT` |
| `503` three times | `Unavailable` (`HTTP 503`); the circuit opens; the next run calls nothing and records `circuit open` | `ResilienceIT` |
| After the open period | One trial call: success closes the circuit, failure opens it again | `ResilienceIT` |
| `404` (the query written differently) | Not retried; `Unavailable` (`HTTP 404`); counted for the circuit | `ResilienceIT` |
| A timeout on each attempt | 3 attempts, `Unavailable`; the run ends under the 30 s interval | `ResilienceIT` |
| An invalid answer (`{}`, HTML) | Not retried, not counted for the circuit; `Invalid` | `ResilienceIT` |
| Metrics before the first valid import | The data age is `NaN`; the import counter counts each result | `MetricsIT` |

## 6. Handoff and changelog

- **21 Nov 2026 · research and decisions.** Read: issue #84, `features.md` US-40 and §3.1, ADR-008, architecture §7 and §9.1–9.2, the recorded answers (all 300 entities of the first snapshots have the 17 attributes; none broken), the proof of concept's reader, and the code of #193. The owner decided D2, D4, D5 and D9 on 21 November 2026.
- **21 Nov 2026 · read as its builder** before approval: the numbering of the broken bus, the J1 values as properties and the data age before the first import were settled here (D5, D8, D10).

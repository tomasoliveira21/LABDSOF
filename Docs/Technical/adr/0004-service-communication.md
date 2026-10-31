# ADR-004 · Service communication

## Status

**Accepted**: decided by the team on 15 October 2026. Updated on 21 October 2026: services deployed independently from the start (guide A4) and the transport source (guide A2, J4). Updated on 22 October 2026: retry values before the dead-letter queue (guide C3) and resilience values for the Porto Digital poll (guide J1). Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) C1, C2, C3, C4, C5 and C7.

## Context

- The assignment asks for at least one asynchronous workflow that handles retries, duplicate events, failures and eventual consistency (§6.3). In Farol this is the help-request triage ([`architecture.md`](../architecture.md) §6).
- Five services (guide A2) are deployed on their own from Sprint A (guide A4); Sprint B already requires at least two (assignment §6.2). A service that is down must not stop the others: a help request must still be saved when the AI or another service fails (architecture driver D6).
- Each service owns its database ([ADR-003](0003-database-and-data-ownership.md)), so it cannot read another service's data directly.
- Transports reads an external source, the Porto Digital API, which can be unavailable, slow or return invalid data (guide A2, J4).
- The apps need fresh data (backoffice list, request status, announcements), but the team is small (driver D9).

## Decision

1. **Broker: RabbitMQ** (C1), used through **Spring AMQP** (C2). *Topic* exchanges for publish-subscribe; a **dead-letter exchange** for messages that keep failing.
2. **Only events between services** (C4). No service calls another service's REST API. Each service keeps a local copy of what it needs from another (e.g. Reports keeps the teams, their permissions and help categories from Events; every service keeps the memberships and team assignments of its events from Auth & Users).
3. **Delivery guarantees** (C3):
   - **Outbox table** in each service: the business change and the outgoing event are saved in the same transaction; a polling job publishes pending rows to RabbitMQ. An event is never lost and never sent for a change that was rolled back.
   - **At-least-once delivery, idempotent consumers:** each consumer records the `message_id`s it has processed and ignores duplicates.
   - After **4 attempts** with exponential backoff (1 s, 2 s, 4 s), the message goes to the **dead-letter queue** of its queue, with the failure reason in its headers. DLQ messages are never reprocessed automatically: they show on the DLQ panel of the dashboard ([ADR-012](0012-observability.md), NFR-34) and are moved back by hand once the cause is fixed ([`architecture.md`](../architecture.md) §6).
4. **Apps → services: synchronous REST, contract-first** (C7). The OpenAPI file is written by hand in `backend/<service>/contracts/openapi.yaml`, paths start with `/api/v1`, and `openapi-generator` produces the Spring interfaces and DTOs and the app clients ([`spec-driven-development.md`](../../Process/spec-driven-development.md)).
5. **External sources are read by a scheduled poll** in the owning service, never inside a user's request: Transports polls the Porto Digital API with a timeout, validates the data and keeps the last valid copy ([`architecture.md`](../architecture.md) §7). Its HTTP client points to a configurable base URL, so the same code runs against the real API or the simulator / recorded responses (guide J4).
6. **Polling for live data** (C5): the apps poll for the backoffice list, request status and announcements. One instance per service for now.

## Consequences

- Once more than one service runs, the broker, the outbox and eventual consistency are real between deployed services, not simulated inside one process. Note of 31 October 2026: the Sprint A skeleton has one service and no broker (R0, #166, [ADR-010](0010-cicd-strategy.md) point 9); this starts in Sprint B.
- A service can be down without blocking the others; its events wait in the outbox or in its queue and are processed when it comes back.
- **Eventual consistency:** local copies lag behind the source by the time it takes an event to arrive. The UI must handle it (e.g. "awaiting triage").
- Every service needs an outbox table, a publishing job and a processed-messages table. This is repeated code; a shared library is not allowed (guide A3: services share only contracts), so each service keeps its own small copy.
- Contracts become the only coupling between services and apps; a breaking change in an OpenAPI file or an event must be versioned.
- Polling adds load during read peaks and delays updates by up to the polling interval. Acceptable for the MVP; push (WebSocket or Server-Sent Events) can replace it later.
- RabbitMQ is one more component to run in every environment. Testcontainers covers it in the tests.
- Tests and the degraded-mode demo (D2) run Transports against the simulator, never the real API, so they are repeatable and failures can be injected on demand.

## Rationale

- Events only, with local copies, keep a service that is down from breaking the others (driver D6), which synchronous REST between services would not.
- The outbox with idempotent consumers is the simplest way to lose no event and send none for a rolled-back change (§6.3), without running Debezium.
- RabbitMQ covers publish-subscribe and dead-lettering with less to run than Kafka, and runs the same way locally and in the tests.
- Polling is enough for the MVP's freshness and keeps the small team away from connection handling (driver D9).

## Alternatives considered

| Option | Why not chosen |
|---|---|
| Apache Kafka | Built for high-volume event streams and replay, which Farol does not need; heavier to run locally and in the cloud |
| Redis Streams | Would add Redis only for messaging; no built-in dead-letter handling |
| Cloud queue (e.g. Azure Service Bus, Amazon SQS) | Ties the code to one cloud platform (none is used: delivery is local, guide G1) and cannot run the same way locally and in tests |
| Synchronous REST between services | Simpler to follow, but couples availability: a service that is down breaks its callers (driver D6) |
| Publishing to the broker inside the business transaction, without an outbox | Simpler, but an event can be lost or sent for a change that was rolled back (assignment §6.3, lost messages) |
| Debezium (change data capture) to publish the outbox | No polling job, but one more component to run; a polling job is enough for the MVP volume |
| WebSocket or Server-Sent Events for live data | Better freshness, but needs connection handling and scaling work; polling is enough for the MVP |
| Code-first OpenAPI (generated from the controllers) | Faster to start, but the contract would follow the code; contract-first is the base of Spec-Driven Development |

## Open points

- Resolved on 22 October 2026: retry values (guide C3) and Porto Digital poll values (guide J1: every 30 s, freshness limit 5 min); see [`architecture.md`](../architecture.md) §6 and §9. The DLQ is watched on the dashboard of [ADR-012](0012-observability.md) (accepted on 31 October 2026), not by an alert.
- Resolved on 26 October 2026: the simulator tool is **WireMock**, confirmed in guide J4 (#155); see [ADR-008](0008-transport-data-source.md), decision 4.
- Resolved: RabbitMQ runs locally in a container (guide C1 and G1; the cloud is an alternative, [ADR-011](0011-deployment-and-infrastructure.md)); API gateway, rate limiting and CORS (guide C6, [ADR-013](0013-api-gateway.md), 22 October 2026); API outline and event catalogue (guide C8, [`api-outline.md`](../api-outline.md), #123).

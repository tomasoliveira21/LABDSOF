# Local walking skeleton and infrastructure specification

**Issue:** [#58](https://github.com/tomasoliveira21/LABDSOF/issues/58) · **Tracker:** [#147](https://github.com/tomasoliveira21/LABDSOF/issues/147) · **Owner:** Tomás Oliveira · **Date:** 26 October 2026

**Status: draft specification for review.** No application, Compose stack, workflow or runtime evidence is supplied by this draft. Keep the implementation issue/PR open until the acceptance checks below pass. This plan does not accept the currently open delivery/CI proposals on behalf of their owners.

## Outcome and boundaries

Deliver R0: a reproducible local vertical slice with an authenticated request, one service-owned database operation, an automated test, a multi-stage image, CI build/smoke checks and local startup/update/rollback. Keep the five services independently deployable from the start, without implementing their complete product backlog.

The professor authorised local-only delivery because of cost: [clarification](../../Meetings/2026-10-22-local-deployment-clarification.md), [ADR-011](../adr/0011-deployment-and-infrastructure.md). Retain the [cloud/cost assessment](../../Research/architecture-investigation/deployment.md); no paid cloud platform or public deployment is required. A single local host is a shared failure boundary, not cloud availability evidence.

Coverage: G1–G7, React web hosting F2, RabbitMQ hosting C1, and the first working implementation of the repository's service/container conventions. Detailed observability is [#148](https://github.com/tomasoliveira21/LABDSOF/issues/148), AI research/evaluation is [#149](https://github.com/tomasoliveira21/LABDSOF/issues/149); application stories #81–#83 remain separate.

## Existing work to reuse

| Dependency | Reuse / coordination |
|---|---|
| [#129 / PR #130](https://github.com/tomasoliveira21/LABDSOF/pull/130) | Bruno's local startup/update/rollback/demo procedure; do not write a competing runbook |
| [#131 / PR #132](https://github.com/tomasoliveira21/LABDSOF/pull/132) | Bruno's CI/CD strategy, required checks and local smoke-test policy |
| [#127 / PR #128](https://github.com/tomasoliveira21/LABDSOF/pull/128) | Version/tag source and rebuilding a previous release; no registry assumed before review |
| [#123 / PR #124](https://github.com/tomasoliveira21/LABDSOF/pull/124) | REST/event outline; agree the actual slice contract before implementation |
| [ADR-006](../adr/0006-identity-and-access.md), [ADR-013](../adr/0013-api-gateway.md) | Guerra's Keycloak/gateway configuration, identity validation, CORS and rate limits |
| [Architecture guide](../architecture-decisions-guide.md) J1–J4 | Tiago's resilience values and Guerra's simulator; do not choose alternative retry policies here |

These PRs are dependencies/proposals, not files asserted to exist in main. Reconcile their reviewed versions before freezing this implementation.

## Proposed implementation sequence

1. **Freeze the slice and contracts.** Propose an Events read of one seeded synthetic event through the web app → gateway → Events → Events database, using a synthetic Keycloak account and event membership. Confirm the exact REST operation against the API outline and write the hand-authored OpenAPI contract before generating code. The slice must not bypass per-event authorisation or implement the complete event-management stories.
2. **Create service scaffolds.** Java 21/Spring Boot 4.x/Maven, one independent project and multi-stage Dockerfile per service in `backend/<service>/`. No cross-service code imports. Implement only the selected Events slice; other services expose health/readiness and the minimum wiring needed for the agreed smoke check. Keep builds/version inputs aligned with the CI/versioning reviews.
3. **Describe local infrastructure.** Add `infra/compose.yaml`, ignored local environment values and a versioned `.env.example` with placeholders. Include five backend containers, the React web assets, PostgreSQL, RabbitMQ and the agreed Keycloak/gateway components. Keycloak requires its own owned database/credentials in addition to the five service databases. Do not describe this complete stack as only eight containers: supporting components add to the baseline.
4. **Enforce data boundaries.** One PostgreSQL server can contain five separately owned service databases plus identity infrastructure's database, with explicit users/permissions. Revoke inappropriate default access and test that one service's credentials cannot open another's database. Use named volumes, migrations and deterministic synthetic seed data; do not put secrets in images or versioned files.
5. **Prove asynchronous wiring.** Agree a tiny synthetic publish/consume probe with the event-outline owner; store a business/probe row and an outbox row transactionally, publish with confirms and consume idempotently. An automated smoke check observes the consumed outcome and fails on timeout. Keep test-only probe contracts clearly separated from accepted production business events; do not invent a second event catalogue.
6. **Build and smoke-test.** Implement the reviewed CI steps: compile, meaningful unit/integration tests, image build and a local runner smoke test. Start services only when required dependencies are ready; a missing broker/AI must not prevent an already independent durable request/outbox path from operating when that feature exists. No remote runner may assume access to a personal laptop.
7. **Demonstrate delivery/recovery.** Follow the reviewed local runbook: start, update one service, smoke-test, rebuild the prior tag/image for rollback, verify persisted state and queue recovery. Measure host resources and recovery rather than inventing minimum RAM or RTO figures.

The base Compose owns shared services and networks. Optional `observability` and `ai` profiles are implemented by their workstreams against this contract; no second independent Compose stack should silently duplicate the broker/database.

## Acceptance and evidence checklist

| ID | Check | Required evidence |
|---|---|---|
| AC-1 | A second developer follows the approved instructions on a clean local checkout and starts the slice with placeholder configuration replaced locally | Exact commit/tag, commands, host OS/CPU/RAM and observed start/health results |
| AC-2 | A test user reads the seeded event through the actual web/gateway/service/database path; unauthenticated and another-event access fail | Contract-linked integration tests and authorised/denied responses |
| AC-3 | All five backend projects/images are independently buildable; the implemented service can be replaced without rebuilding the others | Build logs, image versions and individual service restart/update record |
| AC-4 | A synthetic AMQP round trip is verified; duplicate processing is harmless and state survives ordinary container replacement | Integration test/smoke command, observed DB/queue outcomes and bounded wait |
| AC-5 | CI builds/tests/images and performs the agreed smoke test; no cloud deployment/registry credential is silently required | Actual CI run and local startup evidence, not a green documentation-only conventions check |
| AC-6 | Database users are isolated, credentials are not committed and local endpoints/secrets follow Guerra's security design | Denied cross-database test, configuration review and sanitised logs |
| AC-7 | Local update/rollback and state persistence work; host/disk-loss limitations are explicit | Prior/current version identifiers, recovery time measured during the drill and backup/restore assumptions |
| AC-8 | The report/demo explains the cost reason for local delivery and names what was really demonstrated | Reviewed local runbook/report wording and evidence links |

## Decisions still to freeze before code

- Exact vertical-slice endpoint/event contracts and synthetic seed/membership layout, coordinated with the outline and identity owners.
- Reviewed versions of local delivery, CI/CD and release-tag proposals; whether image publication exists at all.
- Final local network/TLS setup, host access from phones and Keycloak/gateway ownership handoff.
- Actual minimum RAM/CPU, startup time, smoke-test command and recovery results, which require running the implementation.

Do not mark #58 completed, ADRs newly accepted or a runtime result measured just because this specification has been reviewed. The draft PR is the place to review the plan and later add its implementation/evidence.

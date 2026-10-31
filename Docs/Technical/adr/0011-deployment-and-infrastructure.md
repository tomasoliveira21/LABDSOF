# ADR-011 · Local deployment and cloud alternatives

## Status

**Local-only delivery authorised by the professor, as reported by Tomás on 22 October 2026.** Source: [recorded clarification](../../Meetings/2026-10-22-local-deployment-clarification.md), [#53](https://github.com/tomasoliveira21/LABDSOF/issues/53). This records the authorised delivery scope. **The local delivery procedure was decided on 23 October 2026** (#129, Bruno; guide G4–G7 and H5) and is specified in [`local-delivery.md`](../local-delivery.md); Tomás applies it in the walking skeleton. No completed deployment, model benchmark or cloud resource provisioning is claimed. The pipeline is in [ADR-010](0010-cicd-strategy.md).

This supersedes this ADR's initial proposal to validate Azure as the current deployment target. The complete [cloud investigation and cost model](../../Research/architecture-investigation/deployment.md) remain available as evidence and a future alternative.

## Context

Five services are independently deployable from Sprint A (ADR-001), each with its own database (ADR-003), communicating via RabbitMQ (ADR-004). Hosting the application, broker, databases and telemetry incurs costs not covered by an assumption of free student hosting. Tomás reported that the professor agreed the project may proceed locally, provided the team explains that cloud deployment was omitted because of cost.

## Decision and proposed implementation

1. **Current scope: deploy and demonstrate locally.** No cloud platform, subscription, remote VM or managed-service purchase is required. Preserve the cost evaluation; include the cost reason and the professor's authorisation in the final report/presentation.
2. Keep the five backend services independently built and replaceable as containers on one local machine. The topology is five backend containers, the API gateway ([ADR-013](0013-api-gateway.md)), Keycloak ([ADR-006](0006-identity-and-access.md)), RabbitMQ, **one PostgreSQL instance with six separate databases/users** (five services and Keycloak), a `web` container serving the React app and WireMock in front of the external systems (J2, J4): eleven containers. Local inference and observability are added behind Compose profiles. Phones open the web app in their browser over the LAN. Only the gateway, Keycloak and the web app reach the network; admin pages are published on `127.0.0.1` only. How phones log in, since the PKCE login needs HTTPS off `localhost`, is open ([security assessment](../security-and-privacy-assessment.md) §9, Guerra). Exact resource sizing remains to be measured.
3. Preserve service/database ownership and denied cross-service access. Sharing a PostgreSQL server or physical host does not permit shared tables or code dependencies. Separate PostgreSQL containers were considered and not taken: they add memory without adding ownership guarantees.
4. One versioned `infra/compose.yaml` with optional profiles, pinned base images, `infra/.env` created from a versioned `infra/.env.example` (no secrets in the repository), health checks and named volumes for PostgreSQL/RabbitMQ. Services wait for their database but not for the broker. Synthetic demo data comes from demo-only Flyway migrations. Missing AI or broker must not block a durable request save/outbox entry. These remain implementation acceptance checks, not delivered application behaviour.
5. CI still builds and tests the images. Delivery is building locally from the release tag (guide H7) and running a manual smoke-test checklist (all containers healthy, web app opens, the gateway refuses a call without a token, login works, one RabbitMQ round trip), using only `docker compose` commands: no script or Makefile. This replaces the old cloud-production-deploy-on-every-merge expectation. CI never reaches a personal machine; it runs the same Compose file and smoke test (without the phone and the login) on its own runner on every push to `main`, every tag and manual runs ([ADR-010](0010-cicd-strategy.md)).
6. Roll back by building the previous tag, for one service or all of them, without undoing Flyway migrations. Check background consumers, outbox and queue drain. Before every demo and update, `pg_dump` the six databases to return to a known state or move it to the backup laptop. Local volumes are not backups; queued messages and host/disk loss are not covered (RPO not guaranteed; D5 stays deferred). RTO is measured with the walking skeleton.

## Consequences

- Avoids recurring cloud-hosting expenditure for the academic delivery. Existing local equipment, electricity and any optional API usage are not asserted to have zero cost.
- A shared local machine is a common failure/resource boundary. No cloud HA, managed scaling, public reachability or cloud recovery is demonstrated.
- Local independent service replacement, integration, degraded modes and observability still need evidence; the cloud exemption does not waive those concerns.
- The report/presentation must state the cost rationale and what was actually run, using the retained dated evaluation as support.

## Alternatives considered

- Azure Container Apps + PostgreSQL + RabbitMQ VM: researched with costs, but not required/provisioned for this delivery after the professor's authorisation.
- ECS/Fargate + RDS + Amazon MQ: viable future alternative, still has hosting and operational costs.
- Vercel-only deployment: does not cover continuously running workers and the persistent broker by itself; unnecessary for local delivery.
- All containers on one cloud VM: a lower-complexity cloud alternative, but still incurs VM/storage cost; not required now.
- One physical machine per database/service: stronger host isolation at higher resource/operational cost; independent deployment does not require it.

## Evidence still pending

Compose/skeleton implementation following [`local-delivery.md`](../local-delivery.md), minimum RAM of the demo laptop, measured RTO, repeatable local startup, independent update/restart, authorised smoke tests, event isolation, AI/broker failure tests and observed recovery/resource use. AI choice/privacy and detailed observability proposals remain separately reviewable. No cloud benchmark is necessary to claim local delivery; do not claim one was performed.

## Walking-skeleton documentary completion (26 October 2026, #58)

The [course-slide-based R0 and local decision detail](../plans/local-walking-skeleton.md) apply Bruno's accepted local procedure. R0 sends one synthetic minimal request through the authenticated web/gateway/Reports path, persists it and displays stored state; a real broker round trip completes the technical slice without AI. *Superseded for Sprint A on 31 October 2026 ([plan](../plans/local-walking-skeleton.md), #166, [ADR-010](0010-cicd-strategy.md) point 9): R0 is one Reports service in memory; this slice is the Sprint B target.* The document adds environment separation, local resource experiment budgets, explicit smoke acceptance and timed recovery targets. It does not replace the Compose-only/manual local runbook with a custom startup script or claim measured RAM/RTO.

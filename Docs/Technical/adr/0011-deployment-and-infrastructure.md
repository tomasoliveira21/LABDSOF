# ADR-011 · Local deployment and cloud alternatives

## Status

**Local-only delivery authorised by the professor, as reported by Tomás on 22 October 2026.** Source: [recorded clarification](../../Meetings/2026-10-22-local-deployment-clarification.md), [#53](https://github.com/tomasoliveira21/LABDSOF/issues/53). This records the authorised delivery scope; the detailed container/operational implementation below remains a recommendation for team review. No completed deployment, model benchmark or cloud resource provisioning is claimed. ADR-010 remains Bruno's CI/CD strategy.

This supersedes this ADR's initial proposal to validate Azure as the current deployment target. The complete [cloud investigation and cost model](../../Research/architecture-investigation/deployment.md) remain available as evidence and a future alternative.

## Context

Five services are independently deployable from Sprint A (ADR-001), each with its own database (ADR-003), communicating via RabbitMQ (ADR-004). Hosting the application, broker, databases and telemetry incurs costs not covered by an assumption of free student hosting. Tomás reported that the professor agreed the project may proceed locally, provided the team explains that cloud deployment was omitted because of cost.

## Decision and proposed implementation

1. **Current scope: deploy and demonstrate locally.** No cloud platform, subscription, remote VM or managed-service purchase is required. Preserve the cost evaluation; include the cost reason and the professor's authorisation in the final report/presentation.
2. Keep the five backend services independently built and replaceable as containers on one local machine. A proposed minimum topology has five backend containers, RabbitMQ, a PostgreSQL instance with five separate databases/users, and a container serving the React web assets (eight containers). Gateway, simulator, observability and optional local inference add components as needed; the mobile app runs on a device/emulator. Exact resource sizing remains to be measured.
3. Preserve service/database ownership and denied cross-service access. Sharing a PostgreSQL server or physical host does not permit shared tables or code dependencies. Separate local PostgreSQL containers are an alternative if stronger process isolation helps testing; neither option requires separate machines.
4. Use a versioned Compose specification, pinned images, local env/secrets, database health checks and named volumes for PostgreSQL/RabbitMQ. Missing AI or broker must not block a durable request save/outbox entry. These remain implementation acceptance checks, not delivered application behaviour.
5. CI still builds/tests and produces versioned images. Replace the old cloud-production-deploy-on-every-merge expectation with a reproducible local startup/update and smoke-test procedure. Bruno defines pipeline/local handoff in ADR-010; no automatic access from public CI to a personal machine is implied.
6. Roll back individual service images to the last healthy version without undoing Flyway migrations. Check background consumers, outbox and queue drain. Local volumes survive container replacement but are not backups; do not claim recovery from host/disk loss. RTO/RPO and backup scope still need explicit validation.

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

Compose/skeleton implementation, repeatable local startup, independent update/restart, authorised smoke tests, event isolation, AI/broker failure tests and observed recovery/resource use. AI choice/privacy and detailed observability proposals remain separately reviewable. No cloud benchmark is necessary to claim local delivery; do not claim one was performed.

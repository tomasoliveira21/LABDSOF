# ADR-011 · Deployment and infrastructure

## Status

**Proposed**, 22 October 2026, [#53](https://github.com/tomasoliveira21/LABDSOF/issues/53). Covers G1–G7, F2, C1 hosting and D2 server topology. No cloud purchase/provisioning or team acceptance. ADR-010 remains reserved for Bruno's CI/CD strategy.

## Context

Five services are independently deployable from Sprint A (ADR-001). Each owns a PostgreSQL database (ADR-003) and exchanges events through RabbitMQ (ADR-004). The deployment platform was reopened on 15 October; student credits and the approved budget are unknown. The sourced [deployment investigation](../../Research/architecture-investigation/deployment.md) includes workload comparison, cost assumptions, environments, recovery and implementation checks.

## Proposed decision

1. Validate Azure Container Apps Consumption for the five stateless services, private ACR images and West Europe resources, conditional on full quote/credit/quota checks. Keep one replica per service while operational; do not strand asynchronous workers with HTTP-only scale-to-zero.
2. Host the React management app on Azure Static Web Apps Free within plan limits; API authorisation is separate from static hosting.
3. Host RabbitMQ on one private Linux VM with persistent managed disk and stable node identity; acknowledge single-node failure risk. Compare an EU managed RabbitMQ quote before implementation. TLS/private access and secret policy coordinate with Guerra.
4. One PostgreSQL server per persistent environment, five separate databases/users with cross-service access denied. Shared compute/failure domain is an explicit MVP trade-off.
5. Bicep for the Azure resources; Compose for local dependencies/services; one budgeted academic production/demo target and isolated temporary validation. Bruno implements continuous deployment and scoped pipeline identity.
6. Image-digest/revision rollback with background-worker shutdown and compatible Flyway migrations. Define stateful recovery/replay before claiming durability; backup scope D5 must be revisited if this recovery proposal is accepted.

## Consequences

- Independent deployments without Kubernetes administration; platform coupling and ongoing stateful/telemetry charges remain.
- The researched cost scenarios exceed an assumed free/student-only budget; exact quote and a service-hours calendar are acceptance gates, not a future optimisation.
- A single broker and database server have no HA guarantee. Rollback does not undo DB migrations or establish zero data loss after disk failure.
- The diagrams retain pending physical choices until this ADR is accepted; no accepted architecture is silently replaced.

## Alternatives considered

- ECS/Fargate + RDS + Amazon MQ: viable, but requires an AWS access/credit and complete infrastructure-cost comparison; retain as fallback.
- Vercel alone: request-driven HTTP containers/static hosting do not cover the persistent broker and continuously active workers.
- All containers on one VM: possible synthetic-demo budget contingency, but couples host failure and service operations.
- Kubernetes: unnecessary initial operational load for a four-person MVP.
- One PostgreSQL server per service: stronger resource isolation at a higher baseline cost; not required for database ownership.

## Acceptance evidence pending

Actual credit/budget and regional quote; network/gateway/security design; JVM/database/broker sizing; smallest independent deploy and restart test; measured rollback/recovery drill. No runnable deployment stack exists in this research PR.

# Deployment, infrastructure and cost investigation

**22 October 2026 · #53 · PROPOSED** · Covers G1–G7, F2, RabbitMQ hosting (C1) and PostgreSQL topology (D2).

## Current delivery scope: local-only, authorised by the professor

**Update recorded 22 October 2026:** Tomás reported that the professor authorised local-only execution because of cloud-hosting costs. The team must explain this reason in the final report/presentation. See the [clarification](../../Meetings/2026-10-22-local-deployment-clarification.md) and [ADR-011](../../Technical/adr/0011-deployment-and-infrastructure.md).

The platform comparisons, cloud topology, regional prices, budget scenarios and cloud rollback notes below are retained as the **assessment of an alternative, not the current deployment plan or an implementation prerequisite**. No paid cloud environment is required. The current recommendation is Compose on one local machine: five separate backend containers, one RabbitMQ, one PostgreSQL instance with five service-owned databases and one web-server container, plus gateway/simulator/observability/AI components as needed. Local volume persistence is not a host-loss backup guarantee.

Local startup, service replacement, smoke tests, migrations, observability and degraded modes still need implementation/evidence. The cloud IaC/region/subscription steps below apply only if cloud hosting is reconsidered. A local prototype may use one PostgreSQL container or separate ones; this does not change the five-database ownership rule.

## Platform comparison

The non-negotiable constraints are five independently deployable Java services from Sprint A, background outbox publishers and consumers, RabbitMQ, PostgreSQL, private images and a small team. HTTP traffic is not the only source of work.

| Option | Fit | Cost/operations implications | Recommendation |
|---|---|---|---|
| Azure Container Apps Consumption + managed PostgreSQL + broker VM | Independent service revisions and private networking; fits background services with a live replica | Per-second compute plus database, VM/disk, registry, networking and telemetry; student eligibility must be verified | First platform to validate; existing Azure student route and less orchestration work than Kubernetes |
| Amazon ECS/Fargate + RDS PostgreSQL + Amazon MQ | Technically suitable for long-running tasks and managed RabbitMQ | Task CPU/memory, broker/storage, RDS, load balancer, NAT/endpoints, public IP and logs all need quotes | Viable fallback if AWS access/credits or a better complete quote are available |
| Vercel | Good static React hosting; now also supports OCI HTTP functions | Request-driven stateless functions do not replace persistent RabbitMQ/PostgreSQL or continuously running outbox/AMQP workers | Do not select as the sole platform; frontend-only use adds another account without a clear MVP benefit |
| One Azure VM running all containers | Independently replaceable containers remain possible | Cheapest topology may differ by quote, but one host couples failures and patching; no per-service cloud revisions | Contingency for a strictly limited synthetic demo budget, not a proven scalable alternative |

These are workload-fit conclusions, not provider benchmarks. Fargate and Amazon MQ expose separate [compute](https://aws.amazon.com/fargate/pricing/) and [broker/storage](https://aws.amazon.com/amazon-mq/pricing/) charges. Vercel's current [Docker documentation](https://vercel.com/kb/guide/docker-on-vercel-vs-render) describes HTTP functions with external state: rejecting it here is about the workers and broker, not an outdated claim that Vercel cannot run Docker.

## Original cloud topology proposal (retained alternative)

- **Region (G3):** Azure West Europe for compute, PostgreSQL, disks, registry and telemetry where supported. Check SKU/quota availability before committing. EU resource placement does not prove where every CDN request, support access or AI subprocess executes; Guerra reviews those flows.
- **Five Container Apps:** one per service, initially 0.5 vCPU/1 GiB **as a sizing hypothesis**, `minReplicas=maxReplicas=1` during operation. Check actual JVM RSS/startup and raise resources if necessary. Every service consumes events and/or publishes an outbox: an HTTP-only scale rule must not strand background work. Scaling rules are described in [Azure's documentation](https://learn.microsoft.com/en-us/azure/container-apps/scale-app). A second revision may temporarily overlap during rollout and must be included in cost and idempotency tests.
- **Ingress:** only the public API entry point agreed with Guerra; service ingress restricted as the gateway decision requires. Services still communicate only via events. A gateway container is an extra resource, not included in the five-service compute subtotal.
- **Web (F2):** React static assets on Azure Static Web Apps Free. Private repository deployment is supported in Microsoft's [quickstart](https://learn.microsoft.com/en-us/azure/static-web-apps/get-started-portal); verify [plan limits](https://learn.microsoft.com/en-us/azure/static-web-apps/plans), SPA fallback and authenticated API calls. Static assets being public is not authorisation; the API checks role/event membership. No secrets in the JS bundle. Backend CORS and identity remain Guerra's work.
- **Database (D2):** one Azure PostgreSQL Flexible Server per persistent environment, five **databases**, five non-superuser owners/runtime roles, no cross-service credentials. Revoke default `PUBLIC` connect privileges as appropriate and test denied access from each other service. A server can host multiple databases ([compute documentation](https://learn.microsoft.com/en-us/azure/postgresql/compute-storage/concepts-compute)); it is not five servers and not one shared database. Start by quoting a Burstable tier and 32 GiB; shared CPU/memory and a common failure domain are accepted MVP compromises, with capacity and connection pools measured later.
- **Registry:** private Azure Container Registry; immutable commit SHA/digest. Container Apps [managed-identity image pulls](https://learn.microsoft.com/en-us/azure/container-apps/managed-identity-image-pull) avoid embedding registry admin passwords. Bruno selects the pipeline identity and tags; Guerra selects secret access policy.

## RabbitMQ hosting and state

Recommend a single dedicated Linux VM, initially quote 2 vCPU/4 GiB, managed persistent block disk, stable hostname/node name, private DNS and AMQPS accessible only from the backend network. The management UI and metrics endpoint stay private; administration uses a controlled tunnel. Pin a supported image/version at implementation time. Persist `/var/lib/rabbitmq`; export definitions separately. TLS/credential rotation belongs to Guerra's security design.

This avoids treating a broker as a disposable stateless app. Container Apps supports [ephemeral storage and Azure Files](https://learn.microsoft.com/en-us/azure/container-apps/storage-mounts); that capability alone is not evidence of correct broker disk performance or recovery. A managed RabbitMQ offer can replace the VM if an EU-region, TLS, persistence and total-cost quote is better. Do not replace RabbitMQ with Service Bus or SQS without a new architecture decision.

Use durable queues/messages, publisher confirms, unroutable-message handling and manual consumer acknowledgements. Mark an outbox row published only after confirmed routing/acceptance; consumers acknowledge after their transaction commits. [Confirms and acknowledgements](https://www.rabbitmq.com/docs/confirms) cover different sides of delivery. Neither a single VM nor an outbox guarantees recovery from every disk loss. Follow the [production checklist](https://www.rabbitmq.com/docs/production-checklist); monitor disk/memory alarms before they block publishers. No HA claim: host or disk loss interrupts asynchronous processing.

## Cost and budget gates

**No subscription balance, eligibility or approved spend is known.** Azure for Students advertises [$100 credit](https://azure.microsoft.com/en-us/free/students/); it is not $100 per month and must not be multiplied across team members without checking their offers.

The [snapshot](azure-price-snapshot.json) was fetched on 22 October using the public [Retail Prices API](https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices), filter `serviceName = Azure Container Apps`, `armRegionName = westeurope`, consumption prices in USD. Observed unit rates: active CPU $0.000034/vCPU-second; active memory $0.000004/GiB-second; idle CPU and memory each $0.000004 per corresponding second; requests $0.56/million. Free monthly subscription grants are [180,000 vCPU-seconds, 360,000 GiB-seconds and 2 million HTTP requests](https://learn.microsoft.com/en-us/azure/container-apps/billing), shared rather than granted to each service.

For five services, each 0.5 vCPU + 1 GiB, assuming **all allocated hours are active** and the whole free grant remains unused elsewhere:

`C_apps(h) = max(0, 5 × 0.5 × h × 3600 − 180000) × 0.000034 + max(0, 5 × 1 × h × 3600 − 360000) × 0.000004`

| Active hours per service/month | Five-service compute subtotal (USD) |
|---|---:|
| 30 | 3.78 |
| 100 | 30.24 |
| 730 | 268.38 |

These are scenario calculations, **not the full bill**. Requests, gateway, concurrent revisions, environment/network charges and all stateful resources are excluded from that subtotal. Background work may prevent idle billing; do not budget every quiet HTTP hour at the idle rate. If grants are already consumed, add $7.56 to each subtotal.

The same snapshot contains supporting regional public meters. Assume stateful components remain allocated for 730 h/month while the application operating hours vary:

| Supporting component | Observed meter and sizing hypothesis | Monthly USD |
|---|---|---:|
| RabbitMQ Linux VM, B2s | $0.048/hour × 730 (2 vCPU/4 GiB sizing hypothesis); disk excluded | 35.04 |
| PostgreSQL Flexible Server B2S | $0.0796/hour × 730; capacity still to test | 58.11 |
| PostgreSQL storage | 32 billed GB × $0.1369/GB-month | 4.38 |
| ACR Basic base fee | $0.1666/day × (730/24); overage/tasks excluded | 5.07 |
| VM disks | **Unquoted planning allowance** | 10.00 |
| Networking and other storage | **Unquoted planning allowance** | 10.00 |
| Telemetry | **Unquoted planning allowance** | 10.00 |
| Supporting subtotal with allowances | Rounded after summing raw meters | **132.60** |

Thus the 100-hour application scenario is **$162.84/month** including these allowances; continuous application operation is **$400.98/month**. Both exclude AI, tax/currency conversion, a gateway, temporary environments/revisions and any environment/private-network charges not yet determined. The $30 allowances are neither regional quotes nor guaranteed caps. A smaller PostgreSQL tier may lower cost but needs capacity evidence; no five-server cost is assumed. These figures show why $100 credit cannot be assumed sufficient. Verify actual regional SKU/plan/network charges, eligibility and disk requirements, replace every allowance, and recompute before acceptance. No financial commitment is made here.

Propose a $75 monthly **review threshold**, not an approved budget: if the complete quote exceeds it or remaining credit cannot cover the planned dates, stay local while choosing a cheaper/reduced-hours demo topology. No promise of an always-on cloud environment follows from this proposal. Use 50/80/100% budget alerts, cap replicas, cap telemetry volume and AI spend, and review spend daily during demos. [Budget alerts do not stop consumption](https://learn.microsoft.com/en-us/azure/cost-management-billing/costs/tutorial-acm-create-budgets). A documented shutdown/deallocation schedule must address VM disks, databases and other resources that can keep accruing charges; do not simply set HTTP replicas to zero and assume the bill stops.

## Environments, IaC and local execution

**G4:** local development and automated integration tests use synthetic data. One persistent cloud environment represents the academic production/demo target; promotion validation runs locally or in a short-lived isolated cloud environment with separate DBs, broker vhost and credentials. No permanent second full cloud stack until budgeted. Keep real production data out of previews. Coordinate with Bruno's continuous-deployment rule rather than quietly introducing a manual gate on each merge.

**G5:** propose [Bicep](https://learn.microsoft.com/en-us/azure/azure-resource-manager/bicep/overview), modules for network, registry, PostgreSQL, broker VM/disk, apps, web and telemetry under `infra/azure/`, with separate environment parameters. Run lint/build and `what-if`, review deletions, then deploy under Bruno's scoped pipeline identity. Terraform is portable but adds state/backend management; Bicep fits a single Azure target. No modules or resources have been deployed by #53.

**G6:** the implementation should add `infra/compose.yaml`: five separately built services, five PostgreSQL containers locally for clear ownership tests (cloud still uses one server; **not taken**: on 23 October 2026 the team chose one PostgreSQL instance with five databases/users, see [`local-delivery.md`](../../Technical/local-delivery.md)), RabbitMQ with named volume and fixed node name, local web, and the Porto Digital simulator. Optional `observability` and `ai` profiles add Prometheus/Grafana and Ollama. Only loopback development ports are published; credentials stay in ignored local env files. Database health checks gate migrations using [Compose service readiness](https://docs.docker.com/compose/how-tos/startup-order/); a missing broker must not prevent saving a help request to its DB/outbox. Services reconnect rather than requiring a globally healthy dependency graph. The simulator choice remains J4's implementation question. This is a topology specification, not a runnable application that does not yet exist.

## Rollback and recovery (G7)

1. Record the last healthy image digests, revision names, configuration and database migration version before promotion. Smoke-test authorised CRUD, event isolation, saved request while AI is disabled, and one publish/consume round trip.
2. On release regression, route HTTP to the last healthy [Container Apps revision](https://learn.microsoft.com/en-us/azure/container-apps/revisions-manage), stop/deactivate failed background consumers as well, and check outbox/queue drain. HTTP traffic routing alone does not stop a faulty worker. Never reverse a Flyway migration automatically; expand/contract is already accepted.
3. A broker process restart should recover the same disk/node. For node loss, restore definitions and recover compatible broker data per [RabbitMQ restore requirements](https://www.rabbitmq.com/docs/backup); export of definitions is **not a backup of queued messages**. Retain enough outbox history for controlled replay; replay is idempotent, but already-purged rows cannot rescue lost queues.
4. PostgreSQL managed [point-in-time recovery](https://learn.microsoft.com/en-us/azure/postgresql/backup-restore/concepts-backup-restore) creates a recovery path; restore into an isolated server and validate all five DBs before repointing services. Coordinated recovery across DBs, broker and projections needs an explicit replay plan. Do not reset every service independently and assume consistency.
5. Proposed drill targets: code rollback ≤10 minutes; stateful restore ≤60 minutes; ordinary process restart loses no committed business rows; catastrophic disk-loss RPO is **unmeasured and not guaranteed**. Record observed RTO/RPO and evidence before advertising recovery guarantees. D5 currently defers backup/retention: accepting this G7 recovery design requires revisiting that scope; until then use synthetic demos and state the durability limitation.

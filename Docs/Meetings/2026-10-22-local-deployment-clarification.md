# Local deployment clarification

**Recorded:** 22 October 2026 · **Source:** Tomás Oliveira's report of his conversation with the professor, supplied while updating #53. This is a record of the clarification relayed by Tomás, not a verbatim quotation or minutes of a new team meeting.

## Professor's authorisation

The professor said the team may proceed with **local-only deployment/execution instead of cloud deployment because of the costs**. The final report and presentation must explain why cloud deployment was not carried out: **the infrastructure/hosting cost**.

Keep the [deployment and cost investigation](../Research/architecture-investigation/deployment.md), including the price snapshot, assumptions and alternatives, as evidence supporting the explanation. The cost estimates remain dated scenario calculations, not expenses incurred or current quotes.

## Effect on delivery

- The current delivery/demo target is the local machine, using independently deployable service containers; no cloud account, VM, managed database, cloud monitoring or remote hosting is required for this scope.
- Keep the five-service architecture, one database owned by each service, RabbitMQ and the React clients. Local hosting does not turn the services into a monolith or require five physical machines.
- Continue builds, tests, images, migrations, local startup/smoke tests, degraded-mode demonstrations and observability evidence. The clarification concerns cloud deployment; it does not establish performance, availability or data-safety results.
- The previous promise that every merge automatically deploys to cloud production is superseded for this scope. Bruno adapts the delivery workflow to reproducible local execution; unattended access to a developer's machine is not assumed.
- AI provider/model selection remains pending evaluation. Local execution of the application neither requires an external API nor automatically approves one; privacy, quality and possible API cost still need review.

## Explanation to include in the report and presentation

> Cloud hosting options and operating costs were evaluated. After discussing the costs with the professor, the team was authorised to deliver and demonstrate the system locally. Cloud deployment was therefore not performed because of its cost. The cloud assessment is retained, while independent service deployment and integration are demonstrated locally.

Explain the limitation alongside the reason: cloud operation, managed-platform scaling and cloud recovery have not been demonstrated. Show the local topology, startup commands, integration/failure tests and observed results instead of claiming a cloud deployment occurred.

## Follow-up

**23 October 2026 (#129):** Bruno defined the local delivery procedure: startup, update, rollback, smoke test, backup and the demo set-up. See [`local-delivery.md`](../Technical/local-delivery.md).

**26 October 2026 — simplification requested by Tomás (not a new professor/team decision):** use the most basic tools that demonstrate the assignment points. Proposed minimum observability is two local containers (Prometheus/Grafana), Actuator health/metrics, Docker JSON logs and correlation IDs; Grafana handles its own alerts. No separate trace/collector/alert container is required because assignment §6.7 allows correlation **or** trace identifiers. Keep one local Ollama/model and one evaluation on the existing test split; repeated/cross-provider studies are optional. See the [minimum demo specification](../Technical/plans/local-observability.md) and [AI evaluation decision](../Technical/plans/ai-provider-evaluation.md). Product SLOs and human/privacy safeguards are retained; no runtime compliance is claimed.

## Linked records

[ADR-011](../Technical/adr/0011-deployment-and-infrastructure.md) · [Architecture §10](../Technical/architecture.md#10-deployment-view) · [Decisions guide](../Technical/architecture-decisions-guide.md) · [Research overview](../Research/architecture-investigation/README.md).

## Sprint A review correction (31 October 2026)

Tomás relayed a review requiring deliverable 10's executable minimum: one endpoint/service/test, Dockerfile, Compose, CI on every PR and ignore rules. This is the source of #166's narrower R0, not a new professor quotation or team meeting. The [full authenticated local application](../Technical/local-delivery.md) is the Sprint B target. #58/#148 are reopened because Markdown-only merges did not deliver running software. ADR-005/012 statuses are reconciled to the existing 26–27 October guide/NFR architectural decisions; runtime/model/privacy evidence remains separate.

**31 October follow-up requested by Tomás (#166):** evolve the diagnostic-only R0 into a small Reports workflow: create and read synthetic help requests using a fixed demo identity and temporary in-memory storage. An explicit demo profile and namespace distinguish this prototype from the authenticated production API. No new professor/team acceptance or production feature completion is claimed.

**31 October CI follow-up (#168):** Tomás requested SonarQube Cloud pull-request analysis after the skeleton merge, made GitHub public and confirmed creating the Sonar project. Add a pinned Maven scanner, JaCoCo coverage and a quality-gate job; the two GitHub identifier variables are configured; the token, CI-based analysis setting and first successful authenticated PR analysis remain pending. This records his request, not a new professor/team meeting or vote. Only source analysis uses the cloud; application delivery remains local. See the [activation guide](../Technical/sonarqube-cloud.md).

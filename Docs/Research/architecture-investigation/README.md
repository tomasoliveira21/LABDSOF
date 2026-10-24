# Architecture investigation

**Issue:** [#53](https://github.com/tomasoliveira21/LABDSOF/issues/53) · **Date:** 22 October 2026 · **Owner:** Tomás Oliveira

**Status: research completed for review; recommendations are PROPOSED.** No team approval, cloud deployment, model benchmark or privacy approval is claimed. Repository baseline: `5ee70a3`. Primary sources were checked on the date above; prices and provider terms must be rechecked before purchase or real-data use.

## Current delivery scope

The professor authorised **local-only execution because of cloud costs**, as [reported by Tomás](../../Meetings/2026-10-22-local-deployment-clarification.md) on 22 October. The final report/presentation must explain this reason. The cloud recommendations in the original investigation below are retained as an evaluated alternative, not a requirement to provision services. [ADR-011](../../Technical/adr/0011-deployment-and-infrastructure.md) records the local scope. Its exact topology, AI choice and monitoring implementation still need review/evidence.

## Scope and findings

| Guide item | Research and recommendation | Still needed before acceptance |
|---|---|---|
| G1 | Local Compose execution authorised; [cloud evaluation](deployment.md) retained with cost rationale | Local startup and independent service smoke tests |
| G2 | [Cost evaluation](deployment.md#cost-and-budget-gates) retained to explain why cloud hosting was omitted | Include the cost reason and authorisation in report/presentation |
| G3 | No cloud region required for local delivery; West Europe remains a studied alternative | Guerra's local/provider data-flow review |
| G4 | Local development/test/demo environments; no required cloud production target | Reproducible local handoff with Bruno |
| G5 | Compose for the current local target; Bicep design retained for possible future Azure hosting | Versioned local infrastructure specification |
| G6 | Docker Compose service/health/volume specification | Backend, frontend and simulator implementation |
| G7 | Image-digest rollback and stateful recovery drill | Measured RTO/RPO; team decision on backup scope |
| F2 | Serve React management assets locally; cloud static hosting remains an alternative | Local SPA/auth/CORS tests with Guerra |
| C1 hosting | Local RabbitMQ container with persistent volume; cloud VM assessment retained | Local identity/permissions, replay/recovery drill |
| D2 topology | One PostgreSQL server per environment, five distinct databases and restricted service users | Integration proof of privilege separation and capacity |
| I1–I3 | [Observability](observability.md): local Micrometer/OpenTelemetry + Prometheus/Grafana; Azure Monitor retained as a cloud alternative | Local instrumentation/dashboard and load evidence |
| K1 | [AI assessment](responsible-ai.md): local Ollama and hosted API comparison retained; local app delivery does not decide the model/provider | Quality/latency benchmark, privacy and any optional API cost |
| K2 | Responsible AI assessment, prompt/schema and [100 synthetic cases + offline evaluator](evaluation/README.md) | Independent labels, fresh holdout, actual model evaluation |

## Review package

- [Deployment research](deployment.md), [observability research](observability.md), [Responsible AI Opportunity Assessment](responsible-ai.md).
- [ADR-005](../../Technical/adr/0005-ai-triage-provider-and-evaluation.md), [ADR-011](../../Technical/adr/0011-deployment-and-infrastructure.md), [ADR-012](../../Technical/adr/0012-observability.md). ADR-010 remains Bruno's pipeline decision.
- [Evaluation instructions and limitations](evaluation/README.md), [system prompt](evaluation/system-prompt.txt), [output schema](evaluation/output.schema.json), dataset and offline baseline report.
- [Azure price snapshot](azure-price-snapshot.json): dated evidence, not a subscription quote.

## Dependencies and handoff

Guerra owns identity, gateway, secrets and Security & Privacy (#46). Tiago owns retry/circuit-breaker values, message redelivery and load tests (#43). Bruno owns pipeline authentication, tags and API/event contracts. This research gives constraints and acceptance checks without replacing their decisions. The joint domain-model work with Guerra is outside #53.

The local-only scope is recorded separately from the remaining proposals. The team can review AI, observability and detailed local implementation independently; do not treat the professor's cloud exemption as acceptance of all technical recommendations. A future implementation issue should cover the deployment skeleton, telemetry and model comparison; closing this research issue does not mean those systems exist.

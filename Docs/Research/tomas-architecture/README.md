# Tomás's architecture investigation

**Issue:** [#53](https://github.com/tomasoliveira21/LABDSOF/issues/53) · **Date:** 22 October 2026 · **Owner:** Tomás Oliveira

**Status: research completed for review; recommendations are PROPOSED.** No team approval, cloud deployment, model benchmark or privacy approval is claimed. Repository baseline: `5ee70a3`. Primary sources were checked on the date above; prices and provider terms must be rechecked before purchase or real-data use.

## Scope and findings

| Guide item | Research and recommendation | Still needed before acceptance |
|---|---|---|
| G1 | [Deployment](deployment.md): Azure Container Apps for the five independent services, subject to budget and subscription capability; ECS is viable; Vercel does not cover the whole workload | Subscription access, regional quote, minimum deploy smoke test |
| G2 | [Cost model](deployment.md#cost-and-budget-gates): measured retail unit-price snapshot and explicit allowances; student credits are finite | Actual credits, calendar and approved budget |
| G3 | West Europe as a proposed resource region; no claim that region alone establishes GDPR compliance | Guerra's data-flow and provider review |
| G4 | Local development + one synthetic-data cloud demo environment; isolated temporary validation before production promotion | Agreement on environment policy with Bruno |
| G5 | Bicep modules for Azure; no paid provisioning in this research | IaC implementation and what-if review |
| G6 | Docker Compose service/health/volume specification | Backend, frontend and simulator implementation |
| G7 | Image-digest rollback and stateful recovery drill | Measured RTO/RPO; team decision on backup scope |
| F2 | Azure Static Web Apps Free for the React management app | SPA/auth/CORS tests with Guerra; plan eligibility |
| C1 hosting | One RabbitMQ VM with durable disk, private AMQPS, stable node identity; accepts single-node failure risk | Quote, patch ownership, replay/recovery drill |
| D2 topology | One PostgreSQL server per environment, five distinct databases and restricted service users | Integration proof of privilege separation and capacity |
| I1–I3 | [Observability](observability.md): Micrometer + OpenTelemetry; local Prometheus/Grafana, cloud Azure Monitor; SLOs and alert/dashboard specification | Instrumentation, dashboard provisioning, load evidence |
| K1 | [AI assessment](responsible-ai.md): compare hosted `mistral-small-2603` with local Ollama `qwen3:4b`; hosted candidate preferred for a low-volume synthetic cloud demo | Quality/latency benchmark, token costs, provider/privacy gates |
| K2 | Responsible AI assessment, prompt/schema and [100 synthetic cases + offline evaluator](evaluation/README.md) | Independent labels, fresh holdout, actual model evaluation |

## Review package

- [Deployment research](deployment.md), [observability research](observability.md), [Responsible AI Opportunity Assessment](responsible-ai.md).
- Proposed [ADR-005](../../Technical/adr/0005-ai-triage-provider-and-evaluation.md), [ADR-011](../../Technical/adr/0011-deployment-and-infrastructure.md), [ADR-012](../../Technical/adr/0012-observability.md). ADR-010 remains Bruno's pipeline decision.
- [Evaluation instructions and limitations](evaluation/README.md), [system prompt](evaluation/system-prompt.txt), [output schema](evaluation/output.schema.json), dataset and offline baseline report.
- [Azure price snapshot](azure-price-snapshot.json): dated evidence, not a subscription quote.

## Dependencies and handoff

Guerra owns identity, gateway, secrets and Security & Privacy (#46). Tiago owns retry/circuit-breaker values, message redelivery and load tests (#43). Bruno owns pipeline authentication, tags, mobile setup and API/event contracts. This research gives constraints and acceptance checks without replacing their decisions. The joint domain-model work with Guerra is outside #53.

The team can review each proposed ADR independently. Keep guide markers under investigation until the team accepts the recommendations. A future implementation issue should cover the deployment skeleton, telemetry and model comparison; closing this research issue does not mean those systems exist.

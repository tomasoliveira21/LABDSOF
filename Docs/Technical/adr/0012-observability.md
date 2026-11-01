# ADR-012 · Observability

## Status

**Accepted — architectural decision; implementation/evidence pending.** Status reconciled on 31 October 2026 (#166) with [guide I1–I3](../architecture-decisions-guide.md) and [NFRs §5](../../Product/non-functional-requirements.md#5-validated-with-the-team-on-26-october-2026): team-confirmed local stack/SLOs on 26 October and the 27 October simplification to Prometheus/Grafana, correlated Docker logs and three Grafana alerts. This records the existing agreement, not a new vote. The [local specification](../plans/local-observability.md) is accepted as the selected architecture; #148 is reopened for actual instrumentation/dashboard/alert work. No live dashboard, implemented metric or measured SLO is claimed.

**Delivery-scope update:** use the local observability option for the [authorised local-only delivery](../../Meetings/2026-10-22-local-deployment-clarification.md). Azure Monitor/log ingestion proposals remain documented cloud alternatives, not current provisioning requirements. Targets and evidence still need validation.

## Context

The asynchronous triage spans queueing, inference and persistence. Fallback must preserve operator visibility, while RabbitMQ, the AI and transport source can fail. Assignment §6.7 requires observable evidence. The [research specification](../../Research/architecture-investigation/observability.md) defines SLIs/denominators, dashboard panels, metric contracts, alerts, sampling and privacy controls.

## Decision

1. Micrometer/Actuator health/metrics and structured logs with a correlation ID carried through HTTP and AMQP. Assignment §6.7 permits correlation or trace identifiers; no OpenTelemetry agent or distributed trace backend is required for this demo. Record submission-to-stored-suggestion timestamps, not only provider duration.
2. **Minimum local stack: two containers, Prometheus and Grafana.** Grafana-managed alerts use its built-in alert handling; no separate Alertmanager, Collector, Tempo, Jaeger or Loki. Inspect structured logs through Docker Compose. Scrape private service/RabbitMQ endpoints. Cloud tooling remains a researched future alternative.
3. Bounded labels and structured redacted logs; no personal request/prompt content, tokens, exact coordinates or high-cardinality identifiers in metric labels.
4. The versioned Farol local operations dashboard covers API success/latency, durable-request visibility, triage/fallback, outbox/queues/DLQ, broker/resources, transport freshness. Use three core Grafana alerts: service/telemetry down, outbox stalled and useful triage below target; inspect other signals on the dashboard/in acceptance checks.
5. Use product SC3/SC5 as mandatory acceptance evidence. The useful-triage target is **≥95% valid suggestions stored within 30 s over a one-hour submission cohort**, following the canonical [ADR-005 coverage definition](0005-ai-triage-provider-and-evaluation.md#coverage-definition-aligned-26-october-2026-145) (aligned 26 October 2026, #145). Retain failures/abstentions in the operational denominator and finalise after the cohort deadlines; SC5 visibility remains 100%. Confirmed targets still require runtime/load/degraded-mode evidence. No-data windows must not appear healthy.

## Consequences

- Correlation across asynchronous boundaries using existing logs, plus two local metrics/dashboard containers.
- No dedicated trace store or log search UI; operators follow one correlation ID in Compose logs.
- Demo data is synthetic; redaction and bounded labels still apply.

## Alternatives considered

- Local stack with an OpenTelemetry Collector, Tempo and a separate Alertmanager (the earlier proposal of #151), dropped in the 27 October simplification (#153): assignment §6.7 accepts correlation *or* trace identifiers, so correlation IDs in the logs are enough and a distributed tracing platform is unnecessary for the demo; Grafana-managed alert rules replace the separate Alertmanager ([plan](../plans/local-observability.md)). It remains an optional future extension.
- Fully self-hosted Prometheus/Loki/Tempo/Grafana in cloud: portable but adds stores/patching/recovery for this team; optional future extension.
- Console logs only: insufficient latency/queue visibility and no measurable error budget.
- Provider-call latency only: hides outbox/queue/storage delays and cannot establish SC3.

## Acceptance evidence pending

Correlated request log entries, scraped metrics/dashboard, no-sensitive-data inspection, one trigger/resolve alert drill and actual SC3/SC5 evidence. No cloud-ingestion quote or trace-store evidence is required for local delivery. The telemetry and the failure/load evidence are integrated with the CI/CD and resilience work.

## I1–I3 completion record (26 October 2026, #148)

The [decision specification](../plans/local-observability.md) fixes ports, 15 s scrape/evaluation, 1-day/512 MiB metric storage, rotated correlated logs, cohort accounting, four dashboard groups and three core Grafana alerts. Outbox alert is **>60 s for 2 min**, superseding the earlier 30 s research proposal and matching the NFR/resilience value. Configuration, compatible pinned versions and observed correlated logs/alerts/SLOs are implementation evidence, not open software-selection decisions.

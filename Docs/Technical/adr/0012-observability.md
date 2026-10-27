# ADR-012 · Observability

## Status

**Proposed**, 22 October 2026, [#53](https://github.com/tomasoliveira21/LABDSOF/issues/53), guide I1–I3. Documentary choices completed by the owner on 26 October 2026 in [local observability decisions](../plans/local-observability.md): minimum Prometheus/Grafana locally, Docker logs and correlation IDs. The user-requested simplification supersedes the earlier Collector/Tempo/separate Alertmanager proposal. Targets are not measured SLOs and no live dashboard is claimed.

**Delivery-scope update:** use the local observability option for the [authorised local-only delivery](../../Meetings/2026-10-22-local-deployment-clarification.md). Azure Monitor/log ingestion proposals remain documented cloud alternatives, not current provisioning requirements. Targets and evidence still need validation.

## Context

The asynchronous triage spans queueing, inference and persistence. Fallback must preserve operator visibility, while RabbitMQ, the AI and transport source can fail. Assignment §6.7 requires observable evidence. The [research specification](../../Research/architecture-investigation/observability.md) defines SLIs/denominators, dashboard panels, metric contracts, alerts, sampling and privacy controls.

## Proposed decision

1. Micrometer/Actuator health/metrics and structured logs with a correlation ID carried through HTTP and AMQP. Assignment §6.7 permits correlation or trace identifiers; no OpenTelemetry agent or distributed trace backend is required for this demo. Record submission-to-stored-suggestion timestamps, not only provider duration.
2. **Minimum local stack: two containers, Prometheus and Grafana.** Grafana-managed alerts use its built-in alert handling; no separate Alertmanager, Collector, Tempo, Jaeger or Loki. Inspect structured logs through Docker Compose. Scrape private service/RabbitMQ endpoints. Cloud tooling remains a researched future alternative.
3. Bounded labels and structured redacted logs; no personal request/prompt content, tokens, exact coordinates or high-cardinality identifiers in metric labels.
4. The versioned Farol local operations dashboard covers API success/latency, durable-request visibility, triage/fallback, outbox/queues/DLQ, broker/resources, transport freshness. Use three core Grafana alerts: service/telemetry down, outbox stalled and useful triage below target; inspect other signals on the dashboard/in acceptance checks.
5. Use product SC3/SC5 as mandatory acceptance evidence. The useful-triage target is **≥95% valid suggestions stored within 30 s over a one-hour submission cohort**, following the canonical [ADR-005 coverage definition](0005-ai-triage-provider-and-evaluation.md#coverage-definition-aligned-26-october-2026-145) (aligned 26 October 2026, #145). Retain failures/abstentions in the operational denominator and finalise after the cohort deadlines; SC5 visibility remains 100%. Proposed targets still need review and runtime/load/degraded-mode evidence. No-data windows must not appear healthy.

## Consequences

- Correlation across asynchronous boundaries using existing logs, plus two local metrics/dashboard containers.
- No dedicated trace store or log search UI; operators follow one correlation ID in Compose logs.
- Demo data is synthetic; redaction and bounded labels still apply.

## Alternatives considered

- Fully self-hosted Prometheus/Loki/Tempo/Grafana in cloud: portable but adds stores/patching/recovery for this team; optional future extension.
- Console logs only: insufficient latency/queue visibility and no measurable error budget.
- Provider-call latency only: hides outbox/queue/storage delays and cannot establish SC3.

## Acceptance evidence pending

Correlated request log entries, scraped metrics/dashboard, no-sensitive-data inspection, one trigger/resolve alert drill and actual SC3/SC5 evidence. No cloud-ingestion quote or trace-store evidence is required for local delivery. Bruno and Tiago integrate the telemetry and failure/load evidence with their assigned work.

## I1–I3 completion record (26 October 2026, #148)

The [decision specification](../plans/local-observability.md) fixes ports, 15 s scrape/evaluation, 1-day/512 MiB metric storage, rotated correlated logs, cohort accounting, four dashboard groups and three core Grafana alerts. Outbox alert is **>60 s for 2 min**, superseding the earlier 30 s research proposal and matching Tiago's NFR/resilience value. Configuration, compatible pinned versions and observed correlated logs/alerts/SLOs are implementation evidence, not open software-selection decisions.

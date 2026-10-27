# ADR-012 · Observability

## Status

**Proposed**, 22 October 2026, [#53](https://github.com/tomasoliveira21/LABDSOF/issues/53), guide I1–I3. Documentary choices completed by the owner on 26 October 2026 in [local observability decisions](../plans/local-observability.md): Prometheus/Grafana, OpenTelemetry Collector, Tempo and Alertmanager locally. Targets are not measured SLOs and no live dashboard is claimed.

**Delivery-scope update:** use the local observability option for the [authorised local-only delivery](../../Meetings/2026-10-22-local-deployment-clarification.md). Azure Monitor/log ingestion proposals remain documented cloud alternatives, not current provisioning requirements. Targets and evidence still need validation.

## Context

The asynchronous triage spans queueing, inference and persistence. Fallback must preserve operator visibility, while RabbitMQ, the AI and transport source can fail. Assignment §6.7 requires observable evidence. The [research specification](../../Research/architecture-investigation/observability.md) defines SLIs/denominators, dashboard panels, metric contracts, alerts, sampling and privacy controls.

## Proposed decision

1. Micrometer/Actuator metrics and a single compatible OpenTelemetry Java instrumentation path. Propagate trace context through HTTP and AMQP; record submission-to-stored-suggestion latency, not only provider duration.
2. **Local stack:** Prometheus for metrics/rules, Grafana for dashboards, one OTel Java trace agent → OpenTelemetry Collector → **Tempo single binary**, and **Alertmanager with local UI only**. Logs stay structured in Docker; no Loki or external notification service. Prometheus scrapes private service/RabbitMQ endpoints. Azure tooling is a retained future alternative, not part of the selected local target.
3. Bounded labels and structured redacted logs; no personal request/prompt content, tokens, exact coordinates or high-cardinality identifiers in metric labels.
4. The versioned Farol local operations dashboard covers API success/latency, durable-request visibility, triage/fallback, outbox/queues/DLQ, broker/resources, transport freshness and cost. Alert on missing visibility, stalled queues, broker alarms, repeated probes and budget consumption, each with a runbook.
5. Use product SC3/SC5 as mandatory acceptance evidence. The useful-triage target is **≥95% valid suggestions stored within 30 s over a one-hour submission cohort**, following the canonical [ADR-005 coverage definition](0005-ai-triage-provider-and-evaluation.md#coverage-definition-aligned-26-october-2026-145) (aligned 26 October 2026, #145). Retain failures/abstentions in the operational denominator and finalise after the cohort deadlines; SC5 visibility remains 100%. Proposed targets still need review and runtime/load/degraded-mode evidence. No-data windows must not appear healthy.

## Consequences

- Correlated diagnosis across asynchronous boundaries, with explicit instrumentation/export work and telemetry spend.
- Azure-specific cloud charts/queries, while application instrumentation remains portable.
- Sampling and privacy reduce diagnostic detail; synthetic probes and aggregate metrics must compensate.

## Alternatives considered

- Fully self-hosted Prometheus/Loki/Tempo/Grafana in cloud: portable but adds stores/patching/recovery for this team; retain locally.
- Console logs only: insufficient latency/queue visibility and no measurable error budget.
- Provider-call latency only: hides outbox/queue/storage delays and cannot establish SC3.

## Acceptance evidence pending

End-to-end correlated trace, exported broker metrics, no-sensitive-data inspection, trigger/resolve alert drill, ingestion quote and measured SC3/SC5. Bruno and Tiago integrate the telemetry and failure/load evidence with their assigned work.

## I1–I3 completion record (26 October 2026, #148)

The [decision specification](../plans/local-observability.md) fixes ports, 15 s scrape/evaluation, 7-day/2 GiB metric storage, 24 h traces, redaction, cohort accounting, dashboard panels and local alert grouping. Outbox alert is **>60 s for 2 min**, superseding the earlier 30 s research proposal and matching Tiago's NFR/resilience value. Configuration, compatible pinned versions and measured traces/alerts/SLOs are implementation evidence, not open software-selection decisions.

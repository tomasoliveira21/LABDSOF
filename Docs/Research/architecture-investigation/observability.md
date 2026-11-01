# Observability investigation

> **Research snapshot of 22 October 2026 (#53)**, kept as evidence and as the record of the alternatives; it is not rewritten. **Decisions in force:** [ADR-012](../../Technical/adr/0012-observability.md), the [local observability decisions](../../Technical/plans/local-observability.md), [NFR-03, NFR-33 and NFR-34](../../Product/non-functional-requirements.md#2-requirements). Where this document differs, they win: for example, Prometheus and Grafana with three core alerts, without a Collector, Tempo or Alertmanager.

**22 October 2026 · #53 · I1–I3 · PROPOSED.** The targets below are design proposals, not measured service levels. SC3 (30 s) and SC5 (no requests lost with AI off) come from the product vision.

**Current scope update:** [Local-only delivery is authorised because of cloud costs](../../Meetings/2026-10-22-local-deployment-clarification.md). Implement the minimum local Prometheus/Grafana plus correlated Docker logs option for the demo; Azure Monitor, cloud alert routing and cloud ingestion costs below are retained alternatives. The proposed SLIs/SLOs and evidence checks still apply locally; no cloud dashboard is required.

**Decision update (26 October, #148):** the owner has completed the [concrete local observability decisions](../../Technical/plans/local-observability.md): Prometheus/Grafana only, structured Docker logs with correlation IDs and Grafana built-in alert handling. This user-requested simplification replaces the former Collector/Tempo/separate Alertmanager proposal. This supersedes any unspecified trace-backend or cloud-first wording below; runtime evidence still follows implementation.

## Stack (I1)

Instrument Java services with **Micrometer/Actuator health/metrics and correlation IDs in structured logs**. Distributed tracing is optional; assignment §6.7 allows correlation or trace identifiers. Spring Boot documents [HTTP/JVM metrics and registries](https://docs.spring.io/spring-boot/reference/actuator/metrics.html); OpenTelemetry supports [Java automatic instrumentation](https://opentelemetry.io/docs/languages/java/automatic/). Lock compatible versions when the actual Spring Boot 4.x skeleton exists; avoid double-instrumenting spans with two Java agents.

Locally, use Prometheus + Grafana for metrics/dashboard/three core alert rules and structured console logs. No additional trace/log/alerting container is required. In Azure, use Azure Monitor/Application Insights for application telemetry, Log Analytics for console/system logs and native resource metrics; use the documented [Java Azure Monitor OpenTelemetry distribution](https://learn.microsoft.com/en-us/azure/azure-monitor/app/opentelemetry-enable) if selected, rather than assuming a generic OTLP endpoint accepts everything. [Container Apps observability](https://learn.microsoft.com/en-us/azure/container-apps/observability) covers platform monitoring; [Log Analytics integration](https://learn.microsoft.com/en-us/azure/container-apps/log-monitoring) covers logs. End-to-end export must be proven with the skeleton.

For RabbitMQ enable the private [Prometheus plugin](https://www.rabbitmq.com/docs/prometheus). Locally Prometheus scrapes it; in the cloud a private collector/scraper must explicitly export a bounded set of broker metrics to the selected backend. Merely enabling the plugin does not create Azure charts. Avoid per-message metrics; follow [RabbitMQ monitoring guidance](https://www.rabbitmq.com/docs/monitoring).

Alternative: self-host Prometheus, Loki, Tempo and Grafana entirely. It offers portable queries and avoids telemetry-vendor coupling, but adds persistent stores, patching and recovery work. This is an optional extension; it is not required locally and is not zero-cost cloud infrastructure. Azure-native observability minimises initial operations, with ingestion/sampling caps and vendor-specific dashboard/query maintenance as accepted costs.

## Measurement contract

Carry a validated/generated correlation ID across HTTP and RabbitMQ headers, with `message_id` for idempotency and `correlation_id` for diagnosis. W3C trace propagation is optional if tracing is later introduced. Store UTC timestamps for submitted, committed, visible-to-operator and suggestion-stored milestones. End-to-end triage latency includes queue wait, model time and storage, not just provider duration. Use milestone timestamps to expose event-driven waiting; linked spans are an optional later enhancement.

JSON logs: time, level, service, environment, release SHA, operation, trace ID, correlation ID, outcome and dependency. Never log help-request text, prompt/completion, exact location, names, tokens or credentials. Metrics labels are bounded service/operation/status/category/provider; no user ID, request ID, event ID or free text as labels. Trace IDs belong in logs/traces, not metric labels. No trace collection/sampling pipeline is required for the minimum demo; retain the same privacy controls for correlated logs.

## SLIs and proposed SLOs (I2)

| Flow | Numerator/denominator or measure | Target and window |
|---|---|---|
| Help request availability | Successful durable creates / valid authorised create attempts; timeouts, 5xx and ambiguous commits count as bad until reconciled; exclude test traffic and invalid/unauthorised input | ≥99.5% in rolling 7 days during advertised service hours; include release outages |
| Durable create speed | Histogram from API ingress to DB/outbox commit response | p95 ≤2 s over 15 min, ≥20 attempts; include failed/time-out requests as bad in a companion success ratio |
| Operator visibility (SC5) | Persisted request IDs visible through authorised operator polling within 5 s / all persisted requests | 100% in the AI-off acceptance test; proposed ≥99.9% in operation over 1 h |
| Useful triage (SC3) | Valid, non-abstained suggestions stored within 30 s end to end / all AI-eligible requests submitted in the one-hour cohort, including failures | **≥95% over 1 h [PROPOSED]**; finalise after the last 30 s deadline; report the complete ratio and degraded periods |
| AI fallback | Fallback outcomes / all AI-eligible requests | Observed ratio, not an availability substitute; any loss of a request is a failing test |
| Event propagation | Oldest unpublished outbox age and publish-to-consumer delay | p95 ≤10 s over 15 min in normal mode; queues may lag during dependency failure |
| Transport freshness | Time since the last successfully validated import | Show data timestamp; use the agreed freshness threshold, not a newly invented one |
| AI quality | Under-prioritised urgent cases, category/team accuracy, schema failures on the versioned evaluation set | 0 urgent under-prioritisations; ≥80% category and team accuracy proposed; dataset-only gate, not clinical safety evidence |

**Coverage alignment (26 October 2026, #145):** the canonical [ADR-005 coverage definition](../../Technical/adr/0005-ai-triage-provider-and-evaluation.md#coverage-definition-aligned-26-october-2026-145) now uses **≥95%**, replacing the earlier 90% offline proposal. A valid result passes schema/semantic checks, event-team eligibility and the raw priority floor; queue wait, all provider attempts and storage are inside 30 s. Provider errors, circuit-open calls, timeouts, refusals, invalid/late output and abstentions are misses even when fallback works. Determine eligibility before seeing the result. A one-hour submission cohort is finalised 30 s after it closes; rolling cohorts use the same deadline lag. Do not discard failures or degraded periods from the complete ratio; no eligible requests means no data. Offline evaluation applies the same percentage to non-abstention reference cases, with expected abstentions scored separately. It is not the same population as operational traffic and is not evidence of a measured SLO. SC5's 100% visibility remains separate.

A week of perfect uptime during a short demo is not evidence for a year-long SLA. Define service hours in the deployment calendar before computing availability; report both total and in-window traffic and counts. Empty windows return **no data**, not 100%. A p95 based only on successes must be accompanied by timeout/error counts. Alert on error-budget burn once traffic is sufficient; initial low-volume demo alerts use explicit counts/probes below.

## Dashboard and alert specification (I3)

One **Farol operations** dashboard, filtered by environment/release, with these panels:

1. Availability and p50/p95/p99 request duration per service; request count and last successful synthetic authenticated probe.
2. Requests committed, visible, awaiting triage, suggested and fallback; oldest pending age; a reconciliation count for committed-but-not-visible IDs.
3. AI end-to-end and provider latency, schema/timeout/error ratios, tokens/cost estimate and accepted/changed suggestions. No invented model confidence score.
4. Outbox pending count/oldest age, queue ready/unacknowledged counts, DLQ additions, consumers, broker up/disk/memory alarms.
5. Transport last valid import age, invalid-response count and upstream error/latency.
6. JVM RSS/heap/GC, restart/OOM counts, database connections/storage and daily/forecast spend.

Metric names below are **proposed custom contracts**, not already emitted metrics. Histogram implementations may expose backend-specific suffixes.

| Signal | Initial trigger | Owner action / recovery |
|---|---|---|
| `farol_probe_success` + scrape age | Two consecutive 30 s create/read probe failures or >2 min no sample | Check ingress/revision/DB; show missing telemetry separately from application failure |
| `farol_requests_not_visible` | Any reconciled committed request still absent after 5 s on two checks | Immediate investigation; disable AI if implicated, preserve saved rows and reconcile visibility |
| `farol_triage_pending_oldest_seconds` | >30 s for 2 min while eligible requests exist | Mark AI degraded, verify fallback, check worker/provider |
| `farol_outbox_oldest_seconds` | >60 s for 2 min (decided 24 October 2026; first proposed here as 30 s) | Check broker alarms, connectivity and confirms; never delete rows to silence alert |
| `farol_dlq_added_total` | Any increase over 5 min | Preserve poison message evidence without sensitive bodies; diagnose schema/retry cause before controlled replay |
| `farol_broker_up` / disk alarm | Down >60 s or disk/memory alarm on two checks | Check VM/disk; DB/outbox should continue saving requests |
| Create failure ratio | >5% over 5 min with ≥20 valid attempts, or repeated synthetic failure | Compare release/dependency traces, roll back bad release |
| Spend | 50/80/100% of approved budget; sudden daily increase | Check usage/credits; alerts are notifications, not a hard spending cap |

For the local minimum, three core rules are Grafana-managed and displayed in its Alerting UI; other rows above are dashboard/manual acceptance signals. No external receiver or cloud action group is required. No notification integration was created by this investigation. Every alert needs a resolution condition and link to the rollback/recovery runbook in [deployment research](deployment.md#rollback-and-recovery-g7).

## Acceptance evidence still to collect

Create a request and find its HTTP → outbox → AMQP → model → stored-suggestion milestones using the same correlation ID in structured logs; no distributed trace UI is required. Stop AI and broker separately; prove visibility and recovery, trigger/resolve alerts, inspect logs for sensitive data, and record telemetry volume/cost. Export/dashboard provisioning comes with the pipeline; failure/load runs come from the resilience work (#43). Dashboard and alerts here are reviewable specifications, not a claim of running monitoring.

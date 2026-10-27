# Local observability decisions

**Issue:** [#148](https://github.com/tomasoliveira21/LABDSOF/issues/148) · **Tracker:** [#147](https://github.com/tomasoliveira21/LABDSOF/issues/147) · **Owner:** Tomás Oliveira · **Updated:** 26 October 2026

**I1–I3 documentary choices complete for review.** This document selects the local software, data paths, endpoints, storage bounds, SLIs, dashboard and alert rules. Instrumentation, configuration files, live panels and measured compliance remain implementation/evidence, not an unanswered architectural choice or a result fabricated by this PR.

## 1. I1: minimum local demonstration stack

Assignment §6.7 (PDF pp.10–11) requires structured logs, health checks, relevant metrics, **correlation or trace identifiers**, error reporting and a small dashboard. Therefore correlation IDs satisfy the identification requirement; a distributed tracing platform is unnecessary for this academic demo. This simplification supersedes the earlier Collector/Tempo/separate Alertmanager proposal from #151.

Use the shared `infra/compose.yaml` and optional `observability` profile. **Only two extra containers: Prometheus and Grafana.**

| Tool | Minimum use | Access/storage |
|---|---|---|
| Spring Boot Actuator + Micrometer | Existing Java processes expose health, HTTP/JVM and a few business metrics | Private management port 8081; `/actuator/health` and `/actuator/prometheus`; no LAN management mapping |
| Prometheus | Scrape service and RabbitMQ metrics every 15 s | UI `127.0.0.1:9090`, private scrape network; named volume, 1-day retention and 512 MiB cap |
| Grafana | One small dashboard and Grafana-managed alert rules using Prometheus | `127.0.0.1:3001`; local credentials; built-in alert handling, no external receiver or separate Alertmanager container |
| Docker console logs | Structured errors and correlation across the request path | `docker compose logs`; rotation 10 MiB × 2 files/container; sanitised evidence exports deleted within 14 days |

Enable RabbitMQ's private `rabbitmq_prometheus` plugin on 15692. No separate log store, trace backend, OpenTelemetry agent/Collector, Tempo, Jaeger, cloud account or notification integration is required. The previous larger stack remains an optional future alternative, not a demo dependency. [Grafana's built-in Alertmanager](https://grafana.com/docs/grafana/latest/alerting/set-up/configure-alertmanager/) handles Grafana-managed rules; operators inspect firing/resolved states in the UI. This demonstrates local error/alert reporting, not delivery of email/Slack notifications.

Start with the existing laptop; record `docker stats --no-stream` during the demo. No 24 GiB machine or extra fixed RAM reservation is an acceptance requirement. Actual capacity is measured, not guaranteed. Pin compatible image versions when creating the implementation; do not use mutable `latest` in its committed Compose.

## 2. Small configuration and correlation contract

Version one `infra/observability/prometheus.yml`, Grafana's Prometheus datasource and one dashboard JSON. Create the three core Grafana rules through the UI, then export their provisioning configuration into Git so the demo can be reproduced; no plugin or external alert service. Secrets stay in ignored `infra/.env`. These are files to implement, not configuration claimed to exist already.

- Generate a random `correlationId` at ingress. Preserve it in the HTTP response, structured service logs and AMQP `correlation_id`; reject/replace invalid or excessive incoming values. A log entry records time, level, service, operation, outcome and this ID. Show the same ID on submit, outbox publication, consumption and final outcome. This is **log correlation**, not a claimed distributed trace.
- Never log incident text, prompt/completion, coordinates, names, tokens or credentials. IDs are log fields, never metric labels. Bound labels to service, environment, operation and outcome.
- Persist submission/commit/suggestion timestamps in Reports; compute the full submission-to-storage latency from those milestones, not provider-only timing. Counters cover failures/fallbacks; gauges expose outbox age and cohort counts.
- Read errors with `docker compose --env-file infra/.env -f infra/compose.yaml logs --tail=100 reports`; filter the displayed logs by the correlation ID when demonstrating one request. No separate log viewer is needed.
- Down/no-data is displayed explicitly; an empty graph is not evidence of a healthy service.

## 3. I2: exact indicators and targets

The [ADR-005 coverage definition](../adr/0005-ai-triage-provider-and-evaluation.md#coverage-definition-aligned-26-october-2026-145) remains authoritative. All targets below are **specified targets, not measured achievements**.

| Indicator | Formula/window | Target |
|---|---|---|
| Durable help creation | Successful committed creates / valid authorised attempts, rolling 7 advertised-service days | ≥99.5%; 5xx, timeout and unresolved ambiguous commits are misses |
| Commit latency | API ingress → DB/outbox commit; count failures separately, 15 min with ≥20 attempts | p95 ≤2 s |
| Operator visibility | Committed request visible through authorised polling within 5 s / committed requests, 1 h | ≥99.9% operational target; **100% with AI off in SC5 test** |
| Useful AI triage | Schema/semantic/eligible-team-valid non-abstained suggestion stored within 30 s / **all pre-defined AI-eligible submissions in a 1 h cohort** | **≥95%**; errors, invalid/late output and abstentions remain misses |
| Event propagation | Oldest unpublished outbox age and publish-to-consumer delay | p95 ≤10 s in normal 15 min windows; expose broker failures explicitly |
| Transport freshness | Age of the last validated import | Warning after **5 min**, polling **30 s**, matching Tiago's J1/J2 |
| Quality | Reviewed offline dataset and raw model output, separate from live traffic | ≥80% category/team accuracy; zero urgent-case failures and correct expected abstentions |

**One-hour accounting implementation:** at time `T`, the cohort contains submissions in `[T−3630 s, T−30 s)`, so every included request's 30 s deadline has expired. Query the persisted submission/outcome timestamps in a single consistent snapshot and expose two bounded-label gauges: `farol_triage_window_eligible` and `farol_triage_window_valid_on_time`. Export these every 15 s; the ratio is numerator/denominator **only when the denominator > 0**. Do not reconstruct submission cohorts from successful completion timestamps or omit unprocessed requests. Provider outcome counters are complementary, not a substitute for this denominator.

Show the full cohort count/ratio and degraded periods together. Empty cohorts display **No data**. Offline expected-abstention cases are scored separately and cannot inflate the live numerator. A deliberate AI-off demo can have 0% useful AI triage while SC5 still passes; it is evidence of fallback, not a reason to report a false 95% success.

## 4. I3: one dashboard and the alert rules

**One dashboard, four groups of panels:**

1. Service health, request/error counts and HTTP latency.
2. Saved requests, AI useful/on-time numerator/denominator, fallback counts and end-to-end latency.
3. Outbox age, RabbitMQ queue/DLQ depth and broker health.
4. Transport data age and JVM memory. Inspect container CPU/RAM with `docker stats`; no host exporter needed.

**Three core alerts** are enough for the small demonstration: service/telemetry down, outbox stalled and useful triage below target. Keep the additional product signals below as dashboard/manual acceptance checks; they do not require a separate alert rule for every row.

| Rule | Trigger/window | Severity and local response |
|---|---|---|
| API probe failed | Two failed 30 s authenticated probes in succession | Critical: inspect gateway/DB/release; preserve data and roll back bad code |
| Committed request not visible | Any reconciled committed request still missing after 5 s on two checks | Critical: reconcile request visibility; AI may be disabled but the saved request cannot disappear |
| Useful triage below target | Mature 1 h ratio <95%, at least 20 eligible requests, for 5 min | Warning: inspect queue/model/validation; keep manual handling. With fewer than 20 requests show the ratio/count but avoid noisy percentage paging |
| Urgent suggestion failure | Any reviewed/known urgent case under-prioritised or missing/late | Critical test/review failure; do not suppress it behind an aggregate percentage |
| Triage stuck | Oldest eligible pending request >30 s for 2 min | Warning: mark degraded, verify fallback and worker/provider |
| **Outbox stalled** | **Oldest pending row >60 s for 2 min** | Warning: inspect broker alarm/connection/confirm. This adopts Tiago's 60 s decision in NFR #139/resilience; the old 30 s research trigger is superseded |
| DLQ not empty | DLQ depth >0 for 1 min | Warning: inspect sanitised reason/schema, fix then replay manually; never auto-delete to silence it |
| Broker down / alarm | Broker down >60 s, or memory/disk alarm persists 30 s | Critical: check container/disk; durable saves should continue through outbox |
| Telemetry missing | Expected target/collector produces no sample for 2 min | Warning: monitoring failure, separate from application success/failure |
| Transport stale | Last valid data age >5 min | Warning: show stale-data notice; preserve last valid rows and event shuttles |

Grafana evaluates the three core rules every 15 s, with the thresholds/windows above: expected service down for 2 min, outbox >60 s for 2 min, and the mature one-hour triage ratio <95% for 5 min with >=20 eligible submissions. Configure no-data/error as a visible degraded state. The remaining table rows guide inspection and acceptance tests. View firing and recovery in Grafana; no email, Slack or paid receiver is configured.

## 5. Verification and documentary closure

| Assignment §6.7 point | Smallest evidence to show |
|---|---|
| Structured application logs | One JSON log entry from Reports |
| Health checks | Actuator health plus Compose service status |
| Relevant metrics | A request changes a Prometheus counter |
| Correlation or trace identifiers | The same correlation ID in HTTP/outbox/consumer log entries |
| Error reporting | Stop a dependency: error/fallback log and Grafana alert state |
| Small operational dashboard | The four-group Farol dashboard |
| Meaningful SLIs | Display counts/latency and the defined 95%/30 s cohort, with honest no-data/window state |


Minimum evidence: (1) healthy services and one live metric/dashboard, (2) the same correlation ID in submit/outbox/consumer/outcome logs, (3) stop/restart a service to show an alert firing and resolving, (4) with AI off the saved request stays visible and fallback increases, and (5) sanitised logs plus observed container usage. Record the tag/machine and screenshots or a short run record. Demonstrate an outbox delay with the broker stopped as part of the existing resilience demo.

The >=95%/30 s target and one-hour cohort remain the product specification. A short demonstration, a small sample or a provider-only benchmark cannot establish an hour-long SLO; show actual counts/window and no-data until the cohort is mature. SC5 still requires 100% visibility in its AI-off test.

**I1–I3 have explicit answers with two added containers.** More panels, distributed tracing, centralised log storage and external alert routing are optional extensions. Runtime files and results remain implementation evidence, separate from this documentary decision; no team acceptance or runtime success is invented.

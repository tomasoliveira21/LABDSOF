# Local observability decisions

**Issue:** [#148](https://github.com/tomasoliveira21/LABDSOF/issues/148) · **Tracker:** [#147](https://github.com/tomasoliveira21/LABDSOF/issues/147) · **Owner:** Tomás Oliveira · **Updated:** 26 October 2026

**I1–I3 documentary choices complete for review.** This document selects the local software, data paths, endpoints, storage bounds, SLIs, dashboard and alert rules. Instrumentation, configuration files, live panels and measured compliance remain implementation/evidence, not an unanswered architectural choice or a result fabricated by this PR.

## 1. I1: selected stack and deployment

Use the shared `infra/compose.yaml` from [walking skeleton PR #150](https://github.com/tomasoliveira21/LABDSOF/pull/150), with an **observability profile**. No separate cloud account or duplicated application stack.

| Component | Selected role | Network/access | Storage decision |
|---|---|---|---|
| Micrometer + Spring Boot Actuator | HTTP/JVM/business metrics from each Java service/gateway | `/actuator/prometheus` on private management port 8081; no LAN mapping | Application emits, Prometheus stores |
| Prometheus | Scrape metrics and evaluate metric/alert rules | `127.0.0.1:9090`; private scrape network | Named volume; 7 days **and** 2 GiB cap; first limit reached applies |
| Grafana | One Farol operations dashboard; metric and trace exploration | `127.0.0.1:3001`, leaving web port 3000 free; configured local admin secret | Datasources/dashboard provisioned from versioned files, settings in a volume |
| OpenTelemetry Collector | Receive application OTLP spans, batch/filter/export | OTLP gRPC 4317 / HTTP 4318 only inside Compose; no host mapping | Bounded memory/queue; no raw incident payload capture |
| **Grafana Tempo, single binary** | **Selected local trace backend**; Grafana queries it | Private HTTP 3200, OTLP receiver private to the collector | Local named volume; 24 h retention, 2 GiB planning disk budget |
| Alertmanager | Group and show Prometheus alerts | `127.0.0.1:9093`; no external integration | Small named state volume; local receiver with no email/Slack/webhook configuration |
| Docker structured console logs | Initial log inspection using Compose tooling; **no Loki in current scope** | Local operator only | Rotation 10 MiB × 2 files/container; export only sanitised evidence and delete exports within 14 days |

RabbitMQ's `rabbitmq_prometheus` plugin is scraped privately on 15692; its management API remains a separate localhost-only administration surface. Services expose only the required health/prometheus endpoints on the private management listener; domain API authorisation is not replaced by scrape access.

The Collector exports to Tempo; Grafana has **Prometheus and Tempo** datasources. Use **one OpenTelemetry Java agent** for traces, with default payload/body capture disabled. Micrometer owns the metrics path; disable duplicate agent metric/log exporters. Explicitly propagate W3C context in RabbitMQ headers and link producer/consumer spans; do not assume every framework automatically instruments the outbox.

Use 100% trace sampling for the small **synthetic local demonstration**, with bounded export queues and the 24 h store; do not advertise it as a production sampling policy. If capacity is exceeded, record dropped spans and lower a documented rate rather than hiding the loss. Instrumentation/configuration versions are pinned as implementation inputs in the Compose/agent lock changes, not mutable `latest` tags.

**Resource planning:** reserve up to about 2 GiB extra Docker RAM and 5 GiB disk for this profile; these are planning budgets, not benchmarked minima. The demo should be checked on the same machine/tag as the application; do not claim performance on a different/cloud machine. Retention limits concern operational telemetry, not deleting business audit rows or replacing Guerra's personal-data policy.

Why these choices: Prometheus/Grafana already match ADR-012; Tempo supplies the previously unspecified trace store without adding another dashboard; the Collector decouples application exporters; Alertmanager provides local alert state without paid notifications. Loki/ELK and cloud Application Insights add stores/operational cost not needed to demonstrate I1–I3 locally. See [Tempo configuration](https://grafana.com/docs/tempo/latest/configuration/) and [Prometheus alert rules](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/).

## 2. Data and configuration contract

Version `infra/observability/{prometheus.yml,rules.yml,alertmanager.yml,otel-collector.yml,tempo.yml}` and `infra/observability/grafana/{datasources.yml,dashboards.yml,farol-operations.json}`. Put local passwords in ignored `infra/.env`; no SaaS keys are needed. Bind receiver listeners to the private container interface, not merely container localhost, while exposing only the explicit loopback admin ports above.

- Scrape and rule evaluation every **15 s**; private targets for all implemented service management endpoints and RabbitMQ. A missing target produces **no data/down**, never a green empty graph.
- JSON logs carry UTC time, severity, service, environment, release, trace/correlation id and outcome; never text/prompt/completion, coordinates, names, tokens or credentials. No user/request/event/correlation IDs in metric labels. Bound labels to service, environment, operation, outcome and fixed category/provider identifiers.
- Persist request submission, commit, operator visibility and suggestion-stored timestamps in the owning service. Trace spans connect HTTP → database/outbox → RabbitMQ → worker/model → validation/store. A direct provider span is not end-to-end SC3.
- Emit counters for attempts, fallbacks/errors, DLQ additions and semantic violations; histograms for API/provider/end-to-end latency; gauges for pending age, queue depths, freshness and resource usage.
- Alerts have severity, service, environment and a runbook URL, not personal data. Local UI is the selected notification destination; external recipients are not configured by this decision.

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

**Dashboard: Farol local operations**, variables environment/release/service, six rows:

1. API traffic, successful durable writes, 4xx/5xx and p50/p95/p99 latency; last successful synthetic probe.
2. Committed/visible/pending requests, oldest pending age, ≥95% triage ratio with numerator/denominator, fallback reasons and deadline violations.
3. Model attempt/end-to-end latency, semantic/schema failures, operator acceptance/correction counts and local model up/resource state.
4. Outbox oldest age/count, ready/unacknowledged messages, consumers, DLQ and broker alarms.
5. Transport import result, last-valid-data age and circuit state; invalid data rejected rather than replacing valid rows.
6. JVM/host/container CPU/RAM, restarts, database pools/disk, scrape health and Collector/Tempo dropped spans.

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

Each rule resolves automatically when its condition clears; stale/no-data is an explicit state. Alertmanager groups by environment/service/rule (30 s group wait, 5 min grouping interval, 1 h repeat). No email, Slack or paid receiver is required. Runbooks use the local startup/rollback procedure; identify the dependency rather than asking the model to fix it.

## 5. Verification and documentary closure

The implementation must demonstrate: healthy scrape targets and live provisioned panels; a correlated HTTP-to-stored-outcome trace; exact synthetic cohort ratios at 95% and below; no-data behaviour; trigger and resolution of broker/DLQ/provider alerts; SC5 with the model stopped; redacted logs; actual host usage and retention bounds. Record exact versions, tag, machine and window/counts.

**No architectural question is left undecided in I1–I3 by this document:** stack, trace backend, collection, dashboard, notification destination, thresholds and windows are selected. Software compatibility, fitting on the machine and SLO achievement are test results to obtain during implementation. They cannot honestly be marked measured just because documentation is complete. ADR-012 remains subject to PR/team review; no acceptance is invented.

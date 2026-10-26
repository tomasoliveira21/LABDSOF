# Local observability implementation specification

**Issue:** [#148](https://github.com/tomasoliveira21/LABDSOF/issues/148) · **Tracker:** [#147](https://github.com/tomasoliveira21/LABDSOF/issues/147) · **Owner:** Tomás Oliveira · **Date:** 26 October 2026

**Status: draft specification for review.** No telemetry configuration, instrumented service, dashboard or measured SLO is supplied by this draft. Review the specification before implementation; keep the issue/PR open until the acceptance evidence exists.

## Outcome and boundaries

Implement guide I1–I3 on the local delivery machine: useful metrics, correlated asynchronous traces, safe structured logs, a provisioned dashboard and tested alerts. Use [ADR-012](../adr/0012-observability.md), the [observability investigation](../../Research/architecture-investigation/observability.md) and the canonical [ADR-005 coverage definition](../adr/0005-ai-triage-provider-and-evaluation.md#coverage-definition-aligned-26-october-2026-145). Cloud monitoring is a retained alternative, not a current requirement.

Depends on a runnable slice from [#58 / draft PR #150](https://github.com/tomasoliveira21/LABDSOF/pull/150). The base Compose/network/database/broker are owned there; this issue adds the observability profile/configuration rather than a separate duplicated stack. Coordinate model/fallback fixtures with #149 and product integration #81–#83. Preserve Tiago's resilience policies and Guerra's privacy/security boundaries.

## Proposed local configuration contract

| Concern | Implementation responsibility |
|---|---|
| Application metrics | Micrometer/Actuator in each implemented Java service; a private scrape endpoint and bounded labels |
| Broker metrics | RabbitMQ Prometheus plugin on the private Compose network; verify actual scrape targets rather than assuming the plugin creates charts |
| Metrics storage/query | Prometheus in the optional `observability` Compose profile; versioned scrape/rule configuration and a defined local retention/storage bound |
| Dashboards | Grafana in that profile; provision datasource/dashboard files, authenticated local access and persistent settings as needed |
| Tracing | One compatible OpenTelemetry Java instrumentation path, an OTLP collector and a reviewed local trace backend; choose/record exact versions, export protocols and retention before code |
| Logs | Structured JSON console logs searchable via the local container tooling initially; no extra log-storage service is required without a reviewed need |
| Alert evaluation | Versioned local rules in the chosen Prometheus/Grafana path; one authoritative rule per signal, not duplicate alert engines |
| Access/cost | Only intended local/LAN interfaces; no telemetry SaaS or cloud resources; report CPU/RAM/disk use on the demo host |

Do not label a dashboard deployed merely because JSON panels exist: it must load against live configured datasources. Do not enable two Java agents that emit duplicate spans.

## Telemetry to implement

1. Propagate W3C trace context across HTTP and RabbitMQ metadata; record service/version/environment, correlation IDs and message IDs for diagnosis. Correlation IDs are not unbounded metric labels.
2. Record help-request milestones: submitted, committed/outbox persisted, visible to the operator, provider attempt and valid suggestion stored. Queue wait, retries, validation and storage belong inside end-to-end SC3 latency.
3. Count every pre-defined AI-eligible request and its valid/on-time, fallback, timeout, refusal, invalid-output or error outcome. Do not exclude difficult requests after seeing a provider response.
4. Expose outbox pending/oldest age, queue ready/unacknowledged counts, consumers, DLQ additions and broker up/disk/memory alarms. Also expose transport last-valid-data age and circuit/fallback status when those flows exist.
5. Review redaction: no raw incident text, prompt/completion, exact coordinates, credentials, tokens or personal identities in telemetry. IDs may be used only where justified for correlation/access review, never as metric dimensions.

## Metric and alert acceptance contract

- **Useful triage:** at least 95% valid suggestions stored within 30 s, on all AI-eligible submissions in a one-hour cohort; finalise after the last 30 s deadline. Keep failure/abstention/late outputs in the denominator and show zero-traffic periods as no data. Offline reference-case coverage is separate, as ADR-005 defines.
- **SC5:** 100% of requests remain visible with AI disabled; working fallback is not successful AI coverage. A short smoke test cannot establish a one-hour SLO or a seven-day availability claim.
- **Dashboard panels:** API success/latency/traffic; request visibility and triage/fallback age; provider latency/errors; outbox/queue/DLQ; broker/JVM/database resource indicators; transport freshness when implemented. Show sample counts and release/environment context.
- **Alerts:** repeated authenticated probe failure, a committed request missing from the operator view, stale triage/outbox, broker alarms/down, DLQ additions and absence of scrape data. Each rule needs a threshold/window, owner action and resolution condition.
- **Threshold reconciliation:** the old research outbox trigger is 30 s for 2 min while the NFR/resilience work records 60 s for 2 min. Use the reviewed current decision and update the stale source before adding rules; do not silently hardcode another value. Do not override provider deadlines/backoff owned by J1.
- **Notification scope:** demonstrate alerts in the local UI first. No e-mail, chat integration or external recipient configuration is authorised by this specification.

## Acceptance and evidence checklist

| ID | Check | Evidence required |
|---|---|---|
| AC-1 | The observability profile starts from the base local stack; scrape targets are healthy and the provisioned Grafana dashboard loads | Exact versions/commit, startup command, target status and datasource-backed panels |
| AC-2 | A synthetic workflow can be followed from HTTP through the asynchronous path to its stored outcome | Correlated trace/log references with sanitised payloads and no duplicate instrumentation |
| AC-3 | Known synthetic counts produce the expected useful-triage and fallback ratios; no traffic shows no data | Counter/cohort test, 30 s boundary check and original denominators shown |
| AC-4 | Broker/provider failure and missing telemetry are distinct, visible states; requests are not hidden by AI failure | Controlled local failure test and SC5 integration evidence when the request flow exists |
| AC-5 | An alert triggers, identifies the corrective action and resolves after recovery | Recorded trigger/resolve sequence and runbook link; no fictitious external notification |
| AC-6 | Logs/traces/rules meet privacy, bounded-label and access constraints | Sanitised sample inspection and review with Guerra |
| AC-7 | Local memory/CPU/storage use and the time window actually observed are recorded | Machine description, measured duration/counts and explicit limitations, not an asserted production SLA |

## Decisions still to freeze before code

Exact trace backend/version, Java instrumentation/export compatibility, local retention/sample bounds, datasource/rule ownership and all alert thresholds need review. These choices must fit the local host and the actual skeleton; no benchmark is implied by choosing the software.

Update ADR-012, guide I1–I3 and the research status only after the relevant review and evidence. Existing draft plans do not become accepted team decisions automatically. The issue remains open until the implementation checklist is satisfied.

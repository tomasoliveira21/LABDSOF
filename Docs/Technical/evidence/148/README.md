# Local observability run evidence (#148), 20 November 2026

Stack: `infra/compose.yaml` with `--profile observability` and `infra/.env.example`, on the development laptop (Windows 11, Docker Desktop 27.4.0 on WSL 2), images built from the branch of #148. Every file here was written by [`drill.py`](drill.py) through the HTTP APIs of Grafana, Prometheus (through Grafana's data source) and RabbitMQ's management API, with UTC timestamps; [`timeline.txt`](timeline.txt) is its log. **No screenshots:** the browser automation tool was not available in the session that ran the drill, so the states are the JSON Grafana itself reports. Data is synthetic.

Reproduce: `docker compose -p farol-148 -f infra/compose.yaml --env-file infra/.env.example --profile observability up --build -d --wait`, then `python -I Docs/Technical/evidence/148/drill.py infra/.env.example farol-148 <out-dir>` from the repository root (it stops and starts Reports and Prometheus).

## What was shown

| Plan §5 point | Evidence | Result |
|---|---|---|
| Structured logs | [`a3-correlated-help-request.json`](a3-correlated-help-request.json) `logLines` | JSON lines (`logstash` format) from the gateway and Reports |
| Health checks and metrics | [`01-all-up.json`](01-all-up.json) `dashboard.up` | `up` = 1 for gateway, Reports, Events and RabbitMQ; 0 for Auth & Users, Transports and Badges (hand-offs below) |
| Correlation identifiers | [`a3-correlated-help-request.json`](a3-correlated-help-request.json) | One help request, `X-Correlation-Id` `0e37a838-…`: returned by the gateway; in the gateway's request line, Reports' request line and Reports' `Outbox message … (HelpRequestSubmitted) published` line; and the AMQP `correlation_id` of that message in the `reports.reports-events` queue |
| Error reporting: alert fires and resolves | [`02-…firing`](02-reports-stopped-firing.json), [`03-…normal`](03-reports-started-normal.json) | Reports stopped at 10:28:59: *Service or telemetry down* firing for `reports` at 10:31:31 (150 s: 2 min `for` plus scrape and evaluation steps). Started at 10:31:31: back to normal at 10:32:23 (50 s, mostly Reports' start-up) |
| Missing telemetry | [`04-…stopped`](04-prometheus-stopped.json), [`05-…started`](05-prometheus-started.json) | Prometheus stopped at 10:32:23: the rule's query fails, every instance *Alerting (Error)*, firing at 10:35:04. Started: normal at 10:35:27 |
| No data | [`01-all-up.json`](01-all-up.json) `alertRules` | *Useful triage below 95 %* has health `nodata` (state *NoData*): no service emits `farol_triage_window_eligible` / `farol_triage_window_valid_on_time` yet (#81–#83) |
| Observed resources | [`docker-stats.txt`](docker-stats.txt) | Prometheus 30.5 MiB, Grafana 109.4 MiB (256 MiB limits each); the whole stack 1942 MiB (1726 MiB without the profile on `main`), of which Keycloak 622 MiB |

A synthetic membership row was inserted into Reports' `membership_copy` for the help request: the membership event US-06 will deliver from Auth & Users does not exist yet.

## Not measured or not shown

- **SC3/SC5** (moved to #149): no triage worker exists, so no cohort, ratio or AI-off visibility was measured.
- **Outbox stalled alert** was provisioned and evaluated (state *Normal*, value 0) but not driven above 60 s in this drill.
- **Auth & Users is not scraped:** its security filter chain also covers the management port, so Prometheus gets `401`. It needs one more `SecurityFilterChain` for `EndpointRequest.to("health", "prometheus")` (as Reports has), a Java security change outside the configuration-only agreement. Its correlation id already works (#191).
- **Transports and Badges** have no actuator; **Events** has metrics but no correlation filter (Java code); all are hand-offs to their owners.
- **PostgreSQL** has no exporter (it would be a third container); outbox age comes from Reports.
- Evaluation every **10 s**, not the plan's 15 s: Grafana's scheduler ticks every 10 s and refuses a 15 s rule interval. The `for` windows are the plan's.
- Load, latency percentiles and long-run behaviour: not measured.

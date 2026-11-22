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
| No data | [`01-all-up.json`](01-all-up.json) `alertRules` | *Useful triage below 95 %* has health `nodata` (state *NoData*): no service emits `farol_triage_window_eligible` / `farol_triage_window_valid_on_time` (still none after #81–#83 merge: SC3, #149). The *Transport data age* panel is empty too: no code emits `farol_transport_data_age_seconds` |
| Observed resources | [`docker-stats.txt`](docker-stats.txt) | Prometheus 30.5 MiB, Grafana 109.4 MiB (256 MiB limits each); the whole stack 1942 MiB (1726 MiB without the profile on `main`), of which Keycloak 622 MiB |

A synthetic membership row was inserted into Reports' `membership_copy` for the help request: the membership event US-06 will deliver from Auth & Users does not exist yet.

## Not measured or not shown

- **SC3/SC5** (moved to #149): no triage worker exists, so no cohort, ratio or AI-off visibility was measured.
- **Outbox stalled alert** was provisioned and evaluated (state *Normal*, value 0) but not driven above 60 s in this drill.
- **PostgreSQL** has no exporter (it would be a third container); outbox age comes from Reports.
- Evaluation every **10 s**, not the plan's 15 s: Grafana's scheduler ticks every 10 s and refuses a 15 s rule interval. The `for` windows are the plan's.
- Load, latency percentiles and long-run behaviour: not measured.

## Update, 20 November 2026: Auth & Users scraped

The drill above ran when Auth & Users still answered `401` on its management port (its security chain covered it), so its `up` is 0 in those snapshots. With its owner's agreement it now has the same management `SecurityFilterChain` as Reports (only `health` and `prometheus`, only on a separate management port), and it is in the `farol-services` job. `observability_smoke.py` on the rebuilt stack:

```
PASS Prometheus scrapes ['auth-users', 'events', 'gateway', 'rabbitmq', 'reports'] (up: ['auth-users', 'events', 'gateway', 'rabbitmq', 'reports'])
PASS Grafana is healthy (200)
PASS the Farol dashboard is provisioned (200)
PASS the three core alert rules are provisioned (['farol-outbox-stalled', 'farol-service-down', 'farol-triage-below-target'])
PASS the gateway returns the caller's X-Correlation-Id (d0211a41-166a-4122-a75b-3213d5f71609)
PASS the gateway replaces an invalid X-Correlation-Id (14781796-b928-4194-8f14-2c3af363932e)
PASS gateway logs a JSON line with correlationId d0211a41-166a-4122-a75b-3213d5f71609
PASS reports logs a JSON line with correlationId d0211a41-166a-4122-a75b-3213d5f71609
```

On the Compose network `auth-users:8081/actuator/env` still answers `401`; the gateway does not route `/actuator` (`404`).

## Update, 20 November 2026: every service scraped, Events correlated

With their owners' agreement, Transports and Badges got actuator and the Prometheus registry, and Events a correlation filter like Auth & Users'. All six Spring services are now in the `farol-services` job. The CI-equivalent steps of `ci.yml` ran locally on the `farol-ci` project with 0 failed steps; `observability_smoke.py`:

```
PASS Prometheus scrapes ['auth-users', 'badges', 'events', 'gateway', 'rabbitmq', 'reports', 'transports'] (up: ['auth-users', 'badges', 'events', 'gateway', 'rabbitmq', 'reports', 'transports'])
PASS Grafana is healthy (200)
PASS the Farol dashboard is provisioned (200)
PASS the three core alert rules are provisioned (['farol-outbox-stalled', 'farol-service-down', 'farol-triage-below-target'])
PASS the gateway returns the caller's X-Correlation-Id (ad5aa4cb-b974-410e-8050-d35c9271a475)
PASS the gateway replaces an invalid X-Correlation-Id (61870887-03b4-4b1a-aaec-88e5e36a7cf2)
PASS gateway logs a JSON line with correlationId ad5aa4cb-b974-410e-8050-d35c9271a475
PASS reports logs a JSON line with correlationId ad5aa4cb-b974-410e-8050-d35c9271a475
PASS events logs a JSON line with correlationId b9555d2a-75e7-4614-bb64-40b1093d1167
```

Grafana alert rules right after (`/api/prometheus/grafana/api/v1/rules`):

```
captured 2026-11-20T12:08:11Z
Service or telemetry down: state=inactive health=ok [('auth-users', 'Normal'), ('badges', 'Normal'), ('events', 'Normal'), ('gateway', 'Normal'), ('rabbitmq', 'Normal'), ('reports', 'Normal'), ('transports', 'Normal')]
Outbox stalled: state=inactive health=ok [('reports', 'Normal')]
Useful triage below 95 %: state=inactive health=nodata [('-', 'NoData')]
```

`docker stats --no-stream` of that stack (memory; 2006 MiB in total, Prometheus and Grafana included):

```
farol-ci-auth-users-1	216.5MiB / 512MiB
farol-ci-badges-1	136.9MiB / 512MiB
farol-ci-events-1	134.3MiB / 512MiB
farol-ci-frontend-1	10.41MiB / 64MiB
farol-ci-gateway-1	170.5MiB / 512MiB
farol-ci-grafana-1	91.09MiB / 256MiB
farol-ci-keycloak-1	635.2MiB / 1GiB
farol-ci-postgres-1	104.6MiB / 512MiB
farol-ci-prometheus-1	30.32MiB / 256MiB
farol-ci-rabbitmq-1	119.5MiB / 512MiB
farol-ci-reports-1	218MiB / 512MiB
farol-ci-transports-1	138.2MiB / 512MiB
```

## Update, 22 November 2026: after merging main

`main` brought Spring Security to Events (#192), Badges (#201) and the gateway (#190). Without changes the scrape of Events and Badges would have been refused, and the merge also left the gateway, Events and Badges without the management port, because their own `environment` replaced the anchor's. Fixed here: the management-port chain and a `ManagementPortIT` in Events and Badges, the shared settings repeated in those three services, and Badges' health check on `8081`. The gateway now refuses an `/api/v1` request without a token itself, so `observability_smoke.py` logs in as a demo account to reach Reports and Events. One stack on the `farol-ci` project, `--profile observability`, images built from this branch:

```
PASS Prometheus scrapes ['auth-users', 'badges', 'events', 'gateway', 'rabbitmq', 'reports', 'transports'] (up: ['auth-users', 'badges', 'events', 'gateway', 'rabbitmq', 'reports', 'transports'])
PASS Grafana is healthy (200)
PASS the Farol dashboard is provisioned (200)
PASS the three core alert rules are provisioned (['farol-outbox-stalled', 'farol-service-down', 'farol-triage-below-target'])
PASS the gateway returns the caller's X-Correlation-Id (22942cbd-f32c-4d92-b42d-a93f2e17e983)
PASS the gateway replaces an invalid X-Correlation-Id (0e4b4f44-9de8-4244-9fc0-d60c4ee0868d)
PASS a real Keycloak token for the demo account
PASS gateway logs a JSON line with correlationId 22942cbd-f32c-4d92-b42d-a93f2e17e983
PASS reports logs a JSON line with correlationId 5ee65690-465c-4236-bda5-20a674b658a0
PASS events logs a JSON line with correlationId 7e5cd87f-6be9-4941-ae0e-1c03d8ce8a1d
```

The Reports, Events, Badges and Auth & Users smokes of `ci.yml` passed on the same stack. `mvn -B -ntp verify` passed in Reports, Events, Badges, Auth & Users and the gateway.

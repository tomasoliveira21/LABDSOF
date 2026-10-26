# High-Level System Architecture

> LABDSOF 2026/27 · Sprint A · Assignment §6 (Common Technical Requirements) and §7.1.2 (deliverable 7, *Technical Design*)
> **Status (21 October 2026):** reflects the team decisions recorded in [`architecture-decisions-guide.md`](architecture-decisions-guide.md) (Portuguese). Items marked **[PENDING]** wait for an investigation; the owner is in that document. The reference diagrams are drawn in draw.io and listed in §14 ([ADR-009](adr/0009-architecture-documentation.md)); the Mermaid diagrams below are working sketches.
> Legend: see [`product-vision.md`](../Product/product-vision.md#legend).

> **Research update (22 October 2026, #53):** The [investigation](../Research/architecture-investigation/README.md) supplies recommendations for deployment, observability and AI, including C1 hosting and D2 server topology. ADR-005 and ADR-012 remain **Proposed**. [ADR-011](adr/0011-deployment-and-infrastructure.md) now records the professor-authorised local-only delivery due to costs, as reported by Tomás; the cloud evaluation is retained. Detailed implementation/evidence and diagram alignment remain pending.

## 1. Architecture drivers

What shapes the design, and where it comes from:

| # | Driver | Source |
|---|---|---|
| D1 | **Isolation between events**: one team must never see or change another event's data | Report, *Per-event permissions*; §5 technical risks |
| D2 | **Read peaks**: tens of thousands of participant requests within minutes (e.g. end of a concert) | Report §5 technical risks |
| D3 | **Weak or congested network** at the venue: short messages, data with its update time | Report §5; assumption A8; annex C10 |
| D4 | **Freshness**: every piece of information shows when it was last updated | Report §5; annex C4 |
| D5 | **Sensitive data**: location and help requests (possibly health) need consent, minimisation, short retention | Report §5 regulatory risks |
| D6 | **AI with fallback**: the main workflow must keep working when the AI is unavailable | Assignment §5.6.2; annex C9 |
| D7 | **At least two independently deployable backend components**, or a justified modular monolith | Assignment §6.2 |
| D8 | **At least one asynchronous workflow** and **one external integration** (or realistic simulator) | Assignment §6.3, §6.5 |
| D9 | **Small team, three sprints** | Report §5 execution risks |

## 2. System context

```mermaid
flowchart LR
    P(["Participant<br/>(P1)"])
    O(["Control-room operator<br/>(P2: team with HelpRequests.Triage)"])
    R(["Field-team member<br/>(P5: team with HelpRequests.Respond)"])
    A(["EventAdmin<br/>(P3)"])
    PA(["FarolAdmin<br/>(P4, global role)"])
    SYS["Farol"]
    TR["Porto Digital urban platform<br/>(FIWARE NGSI v2, live bus data)"]
    LLM["AI model provider<br/>[PENDING]"]

    P -- "uses the web app on the phone" --> SYS
    O -- "handles help requests" --> SYS
    R -- "gets routed requests, updates status" --> SYS
    A -- "manages the event and its teams (web app)" --> SYS
    PA -- "creates events, names EventAdmins" --> SYS
    SYS -- "reads transport data" --> TR
    SYS -- "asks for triage suggestions" --> LLM
```

The identity provider, Keycloak, is self-hosted inside Farol ([ADR-006](adr/0006-identity-and-access.md)), so it is not an external system at this level (guide B2); it appears from logical level 2.

## 3. Container view

The five services are deployed independently from Sprint A (§11).

```mermaid
flowchart LR
    subgraph Clients
        direction TB
        WEB["Web app<br/>(React)<br/>phone pages: participants, field teams<br/>laptop pages: event management, control room"]
    end

    GW["API gateway<br/>(Spring Cloud Gateway)<br/>routes · JWT · CORS · rate limits"]
    KC["Keycloak<br/>(identity provider)"]

    subgraph Backend["Backend services (Spring Boot)"]
        direction TB
        AU["Auth & Users"]
        EV["Events"]
        RE["Reports"]
        TS["Transports"]
        BA["Badges"]
    end

    MQ[("RabbitMQ")]
    DBS[("PostgreSQL<br/>one database per service")]
    TR["Transport data"]
    LLM["AI model provider"]

    WEB --> GW
    WEB -. "login (OIDC, PKCE)" .-> KC
    GW --> AU & EV & RE & TS & BA
    AU -- "account deletion [Admin API]" --> KC
    AU & EV & RE & TS & BA <-- "domain events [AMQP]" --> MQ
    AU & EV & RE & TS & BA --> DBS
    TS -- "scheduled poll" --> TR
    RE -- "triage" --> LLM
```

Not drawn: every container sends logs, metrics and health checks to the observability stack (assignment §6.7; stack **[PENDING team acceptance]**; [observability proposal](../Research/architecture-investigation/observability.md)). Clients get live updates by **polling** (no push in the MVP). Keycloak keeps its own database on the PostgreSQL server; every service validates the token against Keycloak's keys ([ADR-006](adr/0006-identity-and-access.md)).

## 4. Responsibilities and data ownership

| Service | Responsibility (features) | Owns (see [`domain-model.md`](domain-model.md)) |
|---|---|---|
| **Auth & Users** | Authentication, authorisation, accounts, the global `FarolAdmin` role, memberships of events with their role (`EventAdmin`, `RegularUser`) and team assignments, friends (F1, F2, roles and team members of F4, F18) | UserAccount, GlobalRoleGrant, EventMembership, TeamAssignment; friendships |
| **Events** | Events, venue map and points of interest, teams with their permissions and help categories, announcements, check-in, entries (F3, teams of F4, F5, F10, F16, F17) | Event, VenueMap, PointOfInterest, Team, TeamPermission, Announcement; check-ins |
| **Reports** | Community reports and their confirmation, help requests, incident backoffice, AI triage with fallback (F6, F7, F8, F12, F13) | HelpRequest, TriageSuggestion, StatusChange; community reports |
| **Transports** | External transport integration and event shuttles (F9) | TransportOption |
| **Badges** | Badges (F14, F15) | Badges |

Entities in lower case are not in the domain model yet; Tomás and Guerra add them.

**Where roles and permissions live** ([`features.md` §4](../Product/features.md#4-roles-teams-and-permissions-22-october-2026), to confirm with the team):
- **Auth & Users** holds who is who: the `FarolAdmin` grant, each user's membership of each event with its role, and which members are in which team. It publishes a domain event when any of these changes.
- **Events** holds the teams: their names, permissions (fixed list, with the report types for `Reports.Review`) and help categories. It publishes a domain event when a team or its permissions change.
- A member's permissions in an event are the union of their teams' permissions; an `EventAdmin` has all of them. Each service that checks a permission keeps a local copy of the memberships, team assignments and team permissions it needs (rules below). Where the check runs and how the token carries it is guide E2 (Guerra).

**Rules:**
- Each service owns its data and its database; no service reads another service's database.
- Services talk **only through events**. Each service keeps a local copy of what it needs from another (e.g. Reports keeps the teams, their permissions and help categories from Events; every service keeps the memberships and team assignments of its events from Auth & Users). The cost is eventual consistency.
- Each service is its own Maven project in `backend/<service>/` and depends only on the others' contracts (OpenAPI and events). Inside, it follows a **hexagonal layout**: `domain`, `application`, `adapters/in`, `adapters/out`.
- APIs are **contract-first**: hand-written OpenAPI in `backend/<service>/contracts/openapi.yaml`, paths under `/api/v1`, server and client code generated ([`spec-driven-development.md`](../Process/spec-driven-development.md)).

## 5. Communication

| From → To | Style | Why |
|---|---|---|
| Apps → services | Synchronous (HTTP/REST) | Request–response by nature |
| Apps (live lists, request status, announcements) | **Polling** | Enough for the MVP; no push infrastructure |
| Service → service | **Asynchronous only** (RabbitMQ, Spring AMQP, *topic* exchanges) | A service that is down does not stop the others |
| Transports → transport data | Scheduled poll | The external source may be slow or down; never called inside a user's request |

## 6. The asynchronous workflow: help-request triage

Required by assignment §6.3 and §7.1.2 (*Asynchronous workflow*). End-to-end flow: [`user-workflows.md`, W2](../Product/user-workflows.md#w2--participant-asks-for-help-core-journey).

| Concern (assignment §6.3) | How it is handled |
|---|---|
| **Lost messages** | Reports saves the request and the outgoing event in the same transaction (**outbox table**); a polling job publishes pending events to RabbitMQ |
| **Duplicate events** | Consumers are idempotent: each keeps a table of processed `message_id`s |
| **Retries** | **4 attempts** in the consumer (Spring Boot 4: `spring.rabbitmq.listener.simple.retry.max-retries=3`), exponential backoff **1 s → 2 s → 4 s** (at most 10 s), so about 7 s before the **dead-letter exchange**. Messages that cannot be converted or fail validation go straight to the DLQ (`AmqpRejectAndDontRequeueException`). A failing AI call is **not** a message failure: the triage worker records the fallback (§9) and acknowledges the message, so the AI retries (J1) and the message retries never multiply (otherwise 2 × 4 = 8 calls and more than a minute, far beyond SC3). **Why 4 attempts over about 7 s:** the failures a retry can fix (a database connection lost, the broker restarting) clear within seconds; errors that last longer are bugs or poison messages that no retry fixes, so more attempts would only delay the DLQ. Exponential backoff gives a recovering dependency room, and the 10 s cap bounds how long a consumer thread is blocked (stateless retry runs on the listener thread) |
| **Dead letters** | One DLQ per queue (`<queue>.dlq`). `RepublishMessageRecoverer` adds the exception and the original routing key as headers. DLQ messages are **never lost and never reprocessed automatically**: an alert fires when a DLQ is not empty (assignment §6.7; DLQ panel and alert in the proposed [ADR-012](adr/0012-observability.md)); once the cause is fixed, they are moved back to the original queue by hand (safe, because consumers are idempotent); messages that will never be valid are logged and deleted. No DLQ message can hide a help request from the operator: the backoffice reads Reports' own database, and an untriaged request is simply "Not triaged". **Why by hand:** a message that failed four times usually failed because of a bug or bad data, and replaying it automatically would loop; a person replays it once the cause is fixed. The recoverer keeps the reason, which the default `RejectAndDontRequeueRecoverer` loses. No time-to-live on the DLQ: nothing disappears unnoticed, and the volume is small |
| **Outbox publisher** | Polls every 1 s, batches of 100, with publisher confirms. If the broker is down, rows stay pending and are sent on a later run; the age of the oldest pending row is a metric, alerted when it stays above 60 s for 2 min (decided 24 October 2026). **Why:** 1 s keeps the delay to consumers small compared with the 30 s of SC3, at the cost of one indexed query per second per service; 60 s is 60 missed runs, which means the broker is down, not slow, and the 2 min window keeps a short peak from raising the alert |
| **Failures** | If no suggestion arrives in time, the request stays in the operator's list with the participant's category, in order of arrival (non-AI fallback), marked "not triaged" |
| **Eventual consistency** | The backoffice shows "awaiting triage" until the suggestion arrives; the operator can act before it does |

## 7. External integration

| Integration | Purpose | Handling of unavailable, late, incomplete or invalid data (assignment §6.5) |
|---|---|---|
| **Transport data**: Porto Digital urban platform (FIWARE NGSI v2, live bus positions; [ADR-008](adr/0008-transport-data-source.md)) | Transport for F9 | Calls with a timeout; data validated on import; data older than its freshness limit counts as late. On any of these, the last valid data is kept and shown with its time and a warning (US-42, US-43); event shuttles entered manually are unaffected. A **simulator or recorded real responses** under the team's control (guide J4) is used for automated tests, to cause failures and invalid data in the demo, and as a fallback on presentation day |
| **AI model provider** **[PENDING]** (Tomás; local Ollama is an option) | Triage suggestions (F8) | Timeout, retry and circuit breaker, then fallback (§9.1); the suggestion is never shown as a fact (assignment §5.6.2) |

## 8. Security, authentication and authorisation

Owner: **Guerra** (identity provider, token validation, service-to-service authentication, secrets, Security & Privacy assessment). Decided by the team on 22 October 2026 ([record](../Meetings/2026-10-22-identity-and-gateway-decisions.md)): [ADR-006](adr/0006-identity-and-access.md) (identity) and [ADR-013](adr/0013-api-gateway.md) (gateway).

| Concern | Decided |
|---|---|
| Accounts | **Login mandatory for every user**, participants included (9 October 2026, DM-1) |
| Identity provider | **Keycloak**, self-hosted, Authorization Code + PKCE for the web app. The token carries **identity only**; the account is created in Auth & Users at the first `GET /me` (guide E1, ADR-006) |
| Authorisation | Checked **per event** (invariant I1): the user's role in that event (`EventAdmin` or `RegularUser`) and the permissions of their teams there (§4). `FarolAdmin` is the only global role and gives no access to any event's data. Accounts, roles and memberships live in Auth & Users, teams and their permissions in Events. **Every service validates the JWT** (the gateway too) and checks the permission **in the use case**, against its local copies of memberships and teams; changes reach it as events within ≤ 5 s (guide E2, ADR-006) |
| Isolation between events | `event_id` on every event-scoped table, filtered by the application; each service has an integration test proving that a member of event X cannot read or change event Y. A non-member gets 404 |
| Audit | Every change to a help request recorded (StatusChange, domain model I5) |
| Rate limiting | **Per account, never per IP**, at the gateway: 120 requests/min; **5 help requests per 10 min**. Reports per account (US-48) are a Reports business rule (guide C6, ADR-013) |
| Service-to-service and secrets | No HTTP between services; one RabbitMQ user per service. Secrets from environment variables (`.env` locally, GitHub Actions secrets in CI); gitleaks on every PR (guide E3, E4) |

**Security and Privacy Assessment** (Sprint A deliverable 9, guide E5): [`security-and-privacy-assessment.md`](security-and-privacy-assessment.md), written on 25 October 2026, to review with the team. It holds the initial threat model, the sensitive data, the access-control requirements, the data-minimisation measures, retention and deletion, consent and the local demo environment, plus the findings that change the design before Sprint B (§10 there).

## 9. Degraded modes

**Decided (9 October 2026):** the §6.8 scenarios are **AI unavailable (D1)** and **transport source unavailable (D2)** ([`features.md` §3.1](../Product/features.md#31-degraded-mode-scenarios-decided-9-october-2026)). **Decided (22 October 2026):** the resilience patterns and values (§9.1), how D1 and D2 are triggered, observed and tested (§9.2), and the demo script (§9.3), all run on the local machine of the authorised local-only delivery ([ADR-011](adr/0011-deployment-and-infrastructure.md)). Guide items J1 and J2. **WireMock** as the simulator of guide J4 is Tiago's proposal from J2; Guerra, owner of J4, confirms or changes it.

| Unavailable | What the user sees |
|---|---|
| **AI provider** | Requests go to the operator untriaged, with the participant's category, in order of arrival |
| **Transport source** | Last known data with its time and a warning |
| **Another service** | The other services keep working (events only between services, local copies) |
| **RabbitMQ** | Requests are still saved (outbox); triage and copies between services catch up when the broker returns |

### 9.1 Resilience patterns and values (J1)

**Library: Resilience4j 2.4** (`resilience4j-spring-boot4`, which supports Spring Boot 4 since March 2026), used only in the outgoing adapters (`adapters/out`). It is the only mature option with a circuit breaker for Spring Boot 4, it is configured in `application.yml`, and it publishes Micrometer metrics and its state in the health check, which the dashboard proposed in [ADR-012](adr/0012-observability.md) needs (assignment §6.7). Alternatives considered: Spring Framework 7 core retry (no circuit breaker; Spring AMQP uses it for message retries, §6), Spring Cloud Circuit Breaker (an extra layer over Resilience4j, tied to the Spring Cloud release train) and Failsafe (no Spring Boot auto-configuration or metrics).

Timeouts are set on the HTTP client (`RestClient`), not with a Resilience4j `TimeLimiter`, because the calls are blocking and a client timeout also frees the connection. Retry wraps the circuit breaker, so every attempt counts for the circuit, and nothing is retried while the circuit is open. External calls never run inside a user's request: the AI is called by the triage worker and the transport source by a scheduled import.

| External call | Timeout | Retry | Circuit breaker | When it fails |
|---|---|---|---|---|
| **AI provider** (Reports, triage worker) | connect 2 s, response 10 s | 2 attempts, 2 s apart; only on timeout, connection error, 5xx and 429; never on other 4xx or invalid output | last 10 calls, at least 4; opens at ≥ 50 % failed or slow (> 8 s) calls; open 30 s; 1 trial call | D1 fallback. Worst case ≈ 22 s, inside the 30 s of SC3. **To confirm with K1** (Tomás): a local model (Ollama on CPU) may need a 20 s response timeout and a single attempt |
| **Porto Digital** (Transports, scheduled import every 30 s: the *x* of the meeting with the professor on 22 October, P4) | connect 3 s, response 5 s | 3 attempts, 1 s and 2 s apart | last 6 calls, at least 3; opens at ≥ 50 % failed calls; open 60 s; 1 trial call | D2. Only buses within 2 km of the venue, with an NGSI geo-query (`georel=near;maxDistance:2000`): 10 KB instead of 190–300 KB for the whole city (measured on 22 October) |
| **Identity provider**, only if external (JWKS keys, every service) | connect 2 s, response 3 s | none | none | Keys cached for 5 min and refreshed when an unknown `kid` arrives; if the refresh fails, the cached keys stay in use. **To confirm with E1** (Guerra); mocked authentication has no external call |

**Why these values:**

- **AI, 10 s response and 2 attempts:** the most that fits in the 30 s of SC3: 10 s + 2 s + 10 s = 22 s, leaving about 8 s for the outbox (≤ 1 s), the queue and saving the suggestion. A third attempt would not fit (34 s). A hosted model answers a short classification prompt with a small JSON output in a few seconds, so 10 s is a generous margin. Connect 2 s: a reachable provider accepts a connection in well under a second; waiting longer only delays the fallback.
- **AI, retry only transient failures:** a timeout, a dropped connection, a 5xx or a 429 can succeed a moment later. Other 4xx (wrong key, malformed request) fail the same way every time. Invalid output is not retried either: the same prompt tends to fail the same way, the fallback is safe, and it keeps the worst case short.
- **AI, a small circuit (10 calls, at least 4, 50 %, open 30 s):** at night only a few help requests arrive per minute. With a large window, many requests would each wait 22 s before the circuit opened; with this one, it opens after 2 failed requests (2 attempts each). 50 % tolerates an isolated invalid answer. Slow calls (> 8 s, close to the timeout) count as failures, so a provider that is degrading opens the circuit before it starts failing. 30 s open is short enough that triage returns quickly after the provider recovers (at most one or two requests miss it), and long enough to stop paying 22 s per request during an outage. One trial call is enough evidence at this traffic level.
- **Porto Digital, every 30 s:** bus positions arrive about 1 min after they are observed (measured on 16 and 22 October). Importing more often brings no newer data; importing less often makes it older.
- **Porto Digital, 5 s response, 3 attempts:** the API answered in 0.4–0.8 s (six times the margin). The worst case (3 × 5 s + 3 s = 18 s) is shorter than the 30 s interval, so two imports never overlap.
- **Porto Digital, circuit open 60 s:** one failed import (3 failed attempts) opens it. During an outage, calls drop from 6 to 1 per minute, so we do not hammer a public service we do not control, and recovery shows within about 90 s.
- **Porto Digital, geo-query:** the participant only needs buses near the venue; it cuts the payload 20–30 times and the validation work with it.
- **Identity provider:** tokens are validated locally with the cached keys; the call happens only when the keys change, so it needs no retry or circuit, but it must be short because it runs inside a user's request.

### 9.2 How D1 and D2 are triggered, observed and tested (J2)

| | D1 · AI unavailable | D2 · Transport source unavailable |
|---|---|---|
| **Detected** | No valid suggestion: timeout, connection error, provider error or invalid output (fails the JSON schema) | Import fails after its retries, the data fails validation, or the data is older than the freshness limit |
| **Degraded state** | Circuit breaker open or half-open → banner *"AI triage unavailable"*. A single fallback only labels that request, so one bad answer does not alarm the operator | Last import failed, or data older than the limit → participant warning. Backoffice warning from the first failed import, so the team knows before participants do |
| **Freshness limit** | — | **5 min** for bus positions (the *y* of the meeting with the professor on 22 October, P4); older observations are dropped on import, which also covers *late* data (assignment §6.5). Why: positions normally arrive about 1 min old, and a bus in the city covers about 2 km in 5 min, so an older position misleads. It replaces the 24 h proposed for a static GTFS timetable, dropped with the professor's answer ([ADR-008](adr/0008-transport-data-source.md)) |
| **Recovery** | Circuit closes after a successful trial call; the banner disappears. Untouched "Not triaged" requests from the last 15 min are sent to triage once more; a late suggestion never overwrites an operator's decision (I4). Why 15 min and once: an older request has almost certainly been handled by an operator, and a single pass cannot loop | First valid import clears the warnings; invalid data never replaces valid data |
| **Observed in the UI** | Request in the backoffice **at once**, with the participant's category and the label *"Not triaged"*; banner | *"Data from HH:MM"*, warning *"Live transport information unavailable"*, event shuttles still shown; backoffice warning with the time of the last good import |
| **Observed in operations** | Metrics: `resilience4j.circuitbreaker.state{name=ai-provider}`, `farol.triage.fallback{reason}`. Log line per fallback with the reason and the correlation id. Shown on the local dashboard proposed in [ADR-012](adr/0012-observability.md) (triage/fallback panel) | Metrics: `resilience4j.circuitbreaker.state{name=transport-source}`, `farol.transport.import{result}`, `farol.transport.data.age` (seconds). Log line per failed import. Shown on the local dashboard proposed in [ADR-012](adr/0012-observability.md) (transport freshness panel) |
| **Automated test** | **US-36** (SC5), Reports integration test, on every PR. WireMock replaces the provider and answers with a delay over the timeout, a 503, a dropped connection and an invalid body; 20 requests per case. Pass: 100 % shown to the operator as "Not triaged" within 30 s, fallback reason recorded, circuit opens and WireMock receives no more calls; after recovery, a new request gets a suggestion and an operator's priority is kept | **US-42, US-43**, Transports integration test, on every PR. WireMock replays recorded Porto Digital responses: one valid import, then a 503, a timeout and an invalid payload; a fixed `Clock` moves past the freshness limit. Pass: the API returns the last valid data with `dataAsOf` and `stale=true`, shuttles still returned, invalid data never stored; after a valid import, `stale=false` |

**Simulator and demo trigger (guide J4, proposed; Guerra confirms): WireMock in front of each external system.** In the tests and in the local demo run, the AI provider URL and the Porto Digital URL (both configuration, [ADR-004](adr/0004-service-communication.md)) point at a **WireMock** container, one more service in the local Compose file:

- **Normal operation:** WireMock *proxies* every request to the real system, so the demo starts with real AI suggestions and live buses.
- **Recorded responses:** WireMock records real Porto Digital responses (*record and playback*); the automated tests replay them, and on presentation day they replace the real API if it is down.
- **Failure:** the presenter adds, through WireMock's admin API, a higher-priority stub that drops the connection, answers later than the timeout or returns invalid data, and removes it to recover.
- **Without WireMock:** in a local run that does not need failures, the URLs point straight at the real systems. WireMock is never part of a service image.

Why: the services carry no fault-injection code, the professor's request for a simulator is met with the same tool the tests already use (Testcontainers supports it), and it works with HTTPS, because WireMock is itself the HTTPS client of the real system. Alternatives considered: a fault switch inside each service (failure code shipped in every image), our own simulator in Spring Boot (more code to keep close to the NGSI v2 format), Toxiproxy (network faults only, no recording or invalid data, and it breaks the certificate check of HTTPS providers), and stopping a container (needs a restart and shows only one kind of failure). Backup if WireMock fails during the demo: point the URL at an unreachable address and restart the service container. The demo runs on the local machine (guide G4, [ADR-011](adr/0011-deployment-and-infrastructure.md)); the Compose set-up is Tomás's (G5, G6).

### 9.3 Demo script (D1 and D2, about 5 minutes)

Before the demo: Farol running on the local machine with `docker compose` ([ADR-011](adr/0011-deployment-and-infrastructure.md)); WireMock in proxy mode in front of the AI provider and Porto Digital; one published event with a venue map, points of interest and an event shuttle; three windows side by side: **participant** (the web app's phone pages, on a phone or a phone-sized browser window, logged in, event open), **operator** (backoffice, laptop pages), **dashboard** (circuit breaker state, fallbacks, transport data age: the local Grafana dashboard proposed in [ADR-012](adr/0012-observability.md); until it exists, the Actuator `health` and `metrics` endpoints). Each failure is one prepared `curl` command to WireMock's admin API.

**D1 · AI unavailable**

1. Participant sends a help request (*Health*). Operator: it appears at once, and a suggestion arrives in a few seconds, marked as a suggestion. *Normal behaviour.*
2. Presenter adds the AI *connection dropped* stub.
3. Participant sends a second request. Operator: it appears **at once** with the participant's category and the label *"Not triaged"*. Participant: the usual confirmation; nothing looks different.
4. Participant sends a third request. The circuit opens: the banner *"AI triage unavailable: requests are shown by order of arrival"* appears. Dashboard: circuit `OPEN`, fallback counter +2 with the reason `connection`.
5. Operator sets the priority and team of the second request by hand and dispatches it. *F7 works without the AI.*
6. Presenter removes the stub, waits 30 s, and the participant sends a fourth request. It gets a suggestion, the circuit closes and the banner disappears. The third request (untouched) receives a late suggestion; the second keeps the operator's values.
7. Optional: repeat with the *slow answer* stub to show that even when the AI hangs, the request still appears at once.

**D2 · Transport source unavailable**

1. Participant opens *Transport*: buses near the venue and the event shuttle, with a recent *"Data from HH:MM"*.
2. Presenter adds the Porto Digital *invalid data* stub. Within one import (≤ 30 s): the import is rejected and the buses on screen do not change; the time stops moving and the warning *"Live transport information unavailable"* appears, with a link to the operator's site; the **event shuttle is still shown**. Backoffice: warning with the time of the last good import. *Invalid data never replaces valid data.*
3. Presenter swaps it for the *connection dropped* stub. Dashboard: failed imports, circuit `OPEN` after one import, data age growing. The participant's screen stays as in step 2.
4. Presenter removes the stub. Within about 90 s (circuit open time plus one import), the warnings disappear and the time updates.

Invalid data comes first because an invalid answer is still a successful HTTP call: shown after the circuit has opened, it would only reach Transports once the circuit lets a trial call through.

## 10. Deployment view

**Current scope: local-only deployment/execution.** Tomás reported that the professor authorised proceeding locally **because of cloud-hosting costs**, provided the team explains that reason in the final report/presentation. See the [clarification record](../Meetings/2026-10-22-local-deployment-clarification.md) and [ADR-011](adr/0011-deployment-and-infrastructure.md). The [cloud assessment and dated costs](../Research/architecture-investigation/deployment.md) remain documented evidence and future alternatives, not a requirement to provision cloud resources.

The local topology recommendation is one machine with five independently replaceable backend containers, one RabbitMQ with a persistent volume, one PostgreSQL instance with five separate databases/users and a web-server container for the React app. Gateway, simulator, observability and optional inference add components as needed; phones open the same web app in the browser. The API gateway and Keycloak are two of them, the only containers published to the host with the web app ([ADR-013](adr/0013-api-gateway.md), [ADR-006](adr/0006-identity-and-access.md)). Separate database containers are also possible without separate machines. Exact Compose configuration and resource sizing still need implementation/measurement.

Preserved requirements and limits:
- **One instance per service** initially; independent images and service replacement still demonstrate the microservice boundaries.
- **Multi-stage Dockerfiles**, Maven, Java 21 and Spring Boot 4.x; **GitHub Actions** checks every service on every PR/merge/tag.
- The earlier automatic-deploy-to-cloud-production promise is superseded for this delivery scope. Bruno defines reproducible local startup/update and smoke-test handoff; cloud credentials/ACR/provisioning are not prerequisites.
- **Flyway** migrations remain compatible with the previous application image (*expand and contract*); rolling back code does not undo database migrations.
- Local metrics/traces/logs and degraded-mode/integration tests still provide evidence. No cloud HA, managed scaling or cloud recovery result is claimed.
- Persistent local volumes are not a backup or a guarantee against host/disk loss. Backup scope and observed recovery limits remain explicit.

### 10.1 Peak load and scaling (J3)

**Expected peak** (design target, not a measurement):

| Step | Value | Source |
|---|---|---|
| Entries per night, average | ≈ 31,000 (250,000 over 8 nights) | Evidence E1 ([problem report](../Deliverables/problem-opportunity-report-queima.md)) |
| Busiest night | 40,000 (average + 30 %) | Assumption: the nights with the biggest headliners and weekend nights fill above the average; no official capacity per night was found (to check with the organisers, FAP). The margin keeps the design on the safe side |
| People with the app that night | 60 % → 24,000 | Event-app adoption of 55–65 % with promotion (problem report; vendor figures), and the survey target V1 (≥ 60 % would use it). The top of the range on purpose: for a load test, more users is the safe error |
| Open it in the 10 min after the last concert | 50 % → **12,000 open apps** (the web app open in a phone browser) | Assumption: everyone leaves at once and *how do I get home* (F9) is the main question of the night; report §5: *"tens of thousands of requests within minutes"* |
| Requests per open app | 1 poll every 10 s + about 5 screen loads | The 10 s polling interval follows SC6 (announcement on screen in ≤ 15 s: one 10 s interval plus the server time; SC6 raised from 10 s on 24 October 2026) and C5 (polling, no push) |
| **Read peak** | ≈ 1,200 req/s of polling + ≈ 100 req/s of screen loads ≈ **1,300 req/s for 10 min** | Mostly announcements (Events) and transport (Transports). Check: 780,000 requests in 10 min, consistent with the report's "tens of thousands within minutes" |
| **Help requests** | **1 per second** for 10 min | 600 requests in 10 min is far more than the PCR (90 people) could handle; it is set high on purpose so the asynchronous path is never the bottleneck |

**Scaling with one instance per service (C5)**, in this order:

1. **Less work per request:** conditional requests (ETag from the item's last-update time, so a 304 needs no data read); the venue-map image under a versioned URL with a long cache lifetime, so it is downloaded once; polling every 10 s with random jitter, only while the page is visible (Page Visibility API), and slower on `429` or `503` with `Retry-After`. Rate limiting stays with the gateway decision (C6, Guerra).
2. **Isolation:** help requests live in Reports, away from the read peak (Events and Transports). A read peak can slow the map, but not a help request (SC2).
3. **Vertical scaling:** more CPU and memory for the most loaded container (its limits in the Compose file), connection pool sized to match. On one local machine this is bounded by the machine itself.
4. **More instances, when C5 changes:** **Events first** (it receives about 90 % of the peak: announcement polling, map and points of interest), then Transports. Both are stateless. Locally this is a second container of the same image behind the gateway (C6, Guerra), e.g. `docker compose up --scale events=2`, on the same machine. Before that, the outbox publisher and the scheduled imports must run on a single instance (`FOR UPDATE SKIP LOCKED` or ShedLock), or every instance would publish and import the same data.

Why this order: steps 1 and 2 cost no infrastructure and protect what matters most, the help request; step 1 alone removes most of the work, because most polls find nothing new. Scaling is the last resort, and C5 still limits it; any change to C5 must be motivated by the load-test results (guide A4: evolution driven by evidence). Managed cloud scaling (more instances on several hosts) is not part of the local-only delivery; it stays an evaluated alternative in [ADR-011](adr/0011-deployment-and-infrastructure.md) and the [deployment research](../Research/architecture-investigation/deployment.md), and nothing here claims it was demonstrated.

**Load test (Sprint C performance evidence, assignment §7.3.2 item 7): k6.** Scripts in JavaScript; open-model scenarios (requests arrive at a set rate, as they do at the end of a concert, instead of a fixed number of users who wait for each answer, which hides slowdowns); pass criteria written as *thresholds* that fail the run; one binary (or its official Docker image) that runs next to the local Compose stack. It runs by hand in Sprint C, against the full system started with `docker compose` on the local machine (guide G1 and G4, [ADR-011](adr/0011-deployment-and-infrastructure.md)), not on every PR, because it needs the whole system and takes about 20 min; a public CI runner cannot reach a team member's machine. The k6 scripts, the Compose file used and the k6 summary (JSON) of each run are committed as the evidence. The AI provider is replaced by WireMock with 2–5 s latency, so the test measures Farol, costs nothing and does not hit the provider's rate limits; the transport import is not affected by user load. Alternatives considered: Gatling (Java DSL, but a heavier JVM and Maven setup) and JMeter (XML plans that are hard to review in a PR).

**Local run.** k6 generates the load from a second computer on the same network when one is available; otherwise from the same machine, with CPU and memory limits on the Farol containers so k6 does not take their resources, and its own CPU use recorded. The results describe that machine (its CPU, memory and the Compose limits are written down with them), not a cloud environment. If one machine cannot generate or serve the L2 rate, L4 records the limit it reached and the report states it; that limit, not an assumed cloud capacity, is the evidence for the scaling plan.

Why these pass criteria: **p95 < 500 ms** on the server leaves most of SC1's 5 s for a weak venue network (driver D3); **errors < 1 %** because a failed poll is retried 10 s later, but more than 1 in 100 would be visible to users; **0 lost help requests** is the product's core promise (SC5); the L4 goal of **1.5 ×** the peak covers errors in the assumptions above, such as higher adoption.

| Scenario | Load | Pass criteria |
|---|---|---|
| **L1 Smoke** | 1 virtual user, 1 min, every endpoint | 0 errors |
| **L2 End of concert** | 0 → 1,300 req/s in 3 min, held 10 min. Mix: 85 % announcement polling, 8 % transport, 5 % map and points of interest, 2 % other | errors < 1 %; p95 < 500 ms, p99 < 1.5 s; no container restart; an announcement published during the run reaches the pollers in ≤ 15 s (SC6) |
| **L3 Help during the peak** | L2 plus 1 help request/s; an operator polls the backoffice list every 2 s | 600 of 600 requests shown (0 lost); time to the backoffice p95 ≤ 5 s (SC2); suggestion p95 ≤ 30 s (SC3) |
| **L4 Stress** | From 200 req/s, +200 req/s every 2 min, until p95 > 1 s or errors > 5 % | No pass or fail: records the limit of each service on one instance on the local machine, the evidence for the scaling plan. Goal ≥ 1.5 × the peak (≈ 2,000 req/s) |
| **L5 D1 under load** (Should) | L3 with the WireMock *connection dropped* stub on the AI provider | 100 % of requests shown as "Not triaged" (SC5) |

## 11. Architectural style and its evolution

**Decided:** **microservices from the start** ([ADR-001](adr/0001-architectural-style.md)). They meet assignment §6.2 directly, isolate failures (a read peak does not stop help requests) and give small, closed contracts per service.

**Evolution (decided 21 October 2026, guide A4; professor's answer):** the architecture is defined from the start: the five services are independently deployable from Sprint A, each with its own `main`, Dockerfile and database, which already meets the Sprint B requirement of two independently deployable backend components (§6.2); there is **no modular-monolith phase**. There is no imposed path: any change is **motivated by evidence** (measurements, tests, failures), not by the calendar, and is recorded as a new or superseding ADR. For example, scaling a service for the peak (J3) would be recorded with the evidence that motivated it. Those ADRs are the *architecture evolution narrative* of §7.3.2 (item 11). This replaces the proposal of 15 October (modular monolith in Sprints A–B, split in Sprint C).

## 12. Technology choices

| Decision | Choice |
|---|---|
| Backend | **Java 21 + Spring Boot 4.x**, Maven |
| Frontend | **One React web app** for every user; each page is built for the phone (participants, field teams) or for a laptop (event management, control room). The React Native app was removed on 22 October 2026 ([ADR-002](adr/0002-backend-and-frontend-technology.md), #125) |
| Database | **PostgreSQL**, one database per service; Flyway |
| Message broker | **RabbitMQ** with Spring AMQP |
| Live updates | **Polling** |
| Resilience | **Resilience4j 2.4** (timeouts, retry, circuit breaker); message retries in Spring AMQP |
| Simulator of external systems (tests and demo) | **[PROPOSED]** **WireMock** (guide J2; J4 owner Guerra confirms) |
| Load testing | **k6** |
| API | Contract-first **OpenAPI**, `/api/v1` |
| CI/CD | **GitHub Actions**, monorepo, private repository |
| Architecture views | **C4** (all four levels, level 3 for every service) and **4+1** views without scenarios, drawn in **draw.io** |
| Deployment | **Local-only scope authorised by the professor, reported by Tomás; cloud omitted due to costs.** [ADR-011](adr/0011-deployment-and-infrastructure.md); local implementation pending |
| AI provider and model | **[PROPOSED]** Hosted Mistral Small vs local Ollama comparison; selection gates in [ADR-005](adr/0005-ai-triage-provider-and-evaluation.md) |
| Observability | **[PROPOSED]** Local Micrometer/OpenTelemetry + Prometheus/Grafana; Azure Monitor retained as cloud alternative. [ADR-012](adr/0012-observability.md) |
| Identity | **Keycloak** (OpenID Connect), self-hosted; Spring Security resource server in every service; `oidc-client-ts` in the web app. [ADR-006](adr/0006-identity-and-access.md) |
| API gateway | **Spring Cloud Gateway (Server WebMvc)**, CORS and Bucket4j rate limits per account. [ADR-013](adr/0013-api-gateway.md) |
| Venue map | **[PROPOSED]** The image with an **SVG** overlay for the markers, inside a **`react-zoom-pan-pinch`** zoom/pan container; normalised `MapPosition` coordinates; the same component on phone and laptop pages (to confirm with a spike on a real iPhone and Android phone before the map is built). PDF maps are rendered to a PNG on upload by the Events service (**Apache PDFBox**). See [ADR-007](adr/0007-venue-map-representation.md), waits for Tiago's review |

## 13. Architecture Decision Records

Location and format: `Docs/Technical/adr/NNNN-title.md`, Michael Nygard format plus *Alternatives considered*.

| ADR | Decision | Status |
|---|---|---|
| [ADR-001](adr/0001-architectural-style.md) | Architectural style and evolution (§11) | Accepted |
| [ADR-002](adr/0002-backend-and-frontend-technology.md) | Backend and frontend technology | Accepted |
| [ADR-003](adr/0003-database-and-data-ownership.md) | Database and data ownership | Accepted |
| [ADR-004](adr/0004-service-communication.md) | Service communication: message broker, delivery guarantees, communication only through events | Accepted |
| [ADR-005](adr/0005-ai-triage-provider-and-evaluation.md) | AI workflow, provider and fallback | Proposed; evaluation/privacy gates pending |
| [ADR-006](adr/0006-identity-and-access.md) | Identity and access control | Accepted |
| [ADR-007](adr/0007-venue-map-representation.md) | Venue map representation | Proposed; rewritten for the web app on 23 October 2026, waits for Tiago |
| [ADR-008](adr/0008-transport-data-source.md) | Transport data source | Accepted |
| [ADR-009](adr/0009-architecture-documentation.md) | Architecture documentation (C4, 4+1, draw.io) | Accepted |
| ADR-010 | CI/CD strategy | Waits for Bruno's pipeline review |
| [ADR-011](adr/0011-deployment-and-infrastructure.md) | Local deployment and cloud alternatives | Local-only delivery authorised; detailed implementation/recovery pending |
| [ADR-012](adr/0012-observability.md) | Observability | Proposed; instrumentation and SLO validation pending |
| [ADR-013](adr/0013-api-gateway.md) | API gateway, CORS and rate limiting | Accepted |

> ⚠️ **GAP: API outline and walking skeleton.** Sprint A also asks for an API outline (item 7; owner: Bruno, with the event catalogue) and a walking skeleton: a minimal end-to-end implementation with an automated build, an initial test and a containerised deployment (item 10; later phase, no owner yet).

## 14. Architecture diagrams

4+1 views without scenarios, drawn in draw.io ([ADR-009](adr/0009-architecture-documentation.md)). Each `.drawio.svg` file renders here and opens in draw.io for editing. The logical view also includes the [domain model](domain-model.md).

| View | Levels |
|---|---|
| Logical | [1](diagrams/logical-view-level-1.drawio.svg) · [2](diagrams/logical-view-level-2.drawio.svg) · [3](diagrams/logical-view-level-3.drawio.svg) · [4 web](diagrams/logical-view-level-4-web.drawio.svg) · [4 reports](diagrams/logical-view-level-4-reports.drawio.svg) · [4 transports](diagrams/logical-view-level-4-transports.drawio.svg) |
| Process: help request and triage | [1](diagrams/process-view-level-1-help-request.drawio.svg) · [2](diagrams/process-view-level-2-help-request.drawio.svg) · [3](diagrams/process-view-level-3-help-request.drawio.svg) · [4](diagrams/process-view-level-4-help-request.drawio.svg) |
| Process: transport import | [1](diagrams/process-view-level-1-transports.drawio.svg) · [2](diagrams/process-view-level-2-transports.drawio.svg) · [3](diagrams/process-view-level-3-transports.drawio.svg) · [4](diagrams/process-view-level-4-transports.drawio.svg) |
| Implementation (development) | [1](diagrams/implementation-view-level-1.drawio.svg) · [2](diagrams/implementation-view-level-2.drawio.svg) · [3](diagrams/implementation-view-level-3.drawio.svg) · [4 frontend](diagrams/implementation-view-level-4-web.drawio.svg) · [4 reports](diagrams/implementation-view-level-4-reports.drawio.svg) · [4 transports](diagrams/implementation-view-level-4-transports.drawio.svg) |
| Physical (local delivery) | [1](diagrams/physical-view-level-1.drawio.svg) · [2](diagrams/physical-view-level-2.drawio.svg) · [3](diagrams/physical-view-level-3.drawio.svg) |
| Logical → implementation | [1](diagrams/mapping-logical-implementation-level-1.drawio.svg) · [2](diagrams/mapping-logical-implementation-level-2.drawio.svg) · [3](diagrams/mapping-logical-implementation-level-3.drawio.svg) · [4 web](diagrams/mapping-logical-implementation-level-4-web.drawio.svg) · [4 reports](diagrams/mapping-logical-implementation-level-4-reports.drawio.svg) · [4 transports](diagrams/mapping-logical-implementation-level-4-transports.drawio.svg) |
| Logical → physical | [1](diagrams/mapping-logical-physical-level-1.drawio.svg) · [2](diagrams/mapping-logical-physical-level-2.drawio.svg) · [3](diagrams/mapping-logical-physical-level-3.drawio.svg) |

Updated on 26 October 2026 (#<issue>): one web app and no mobile app, the API gateway and Keycloak in the logical, implementation and physical views, and the physical view of the local delivery: one machine, one container per service and one PostgreSQL server with six databases (five services and Keycloak). WireMock appears only in the physical view; logically, Reports and Transports talk to the AI provider and Porto Digital.

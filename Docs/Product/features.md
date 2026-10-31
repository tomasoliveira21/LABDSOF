# Main Features and Initial Backlog

> LABDSOF 2026/27 · Sprint A (Sprint 1) · Assignment §7.1.2 (deliverables 4 *MVP definition* and 5 *Product Backlog*)
> **Product name:** **Farol** (decided 9 October 2026).
> **Status:** feature list and MoSCoW revised on **9 October 2026** (owner of the topic: Bruno Lopes) on top of the team meeting of 8 October ([minutes](../Meetings/2026-10-08-sprint-1-gap-review.md)). Decided: login is mandatory, response teams, degraded-mode scenarios, external integration, badge tiers, attendance check-in. Still open: report types and expiry (§6.1) and the AI priority scale (AI-3, §5.2); the AI provider was decided on 27 October 2026 (guide K1; ADR-005 accepted on 31 October). **[PROPOSED] 10 October 2026** (personas owner): a platform admin (persona P4) creates events and names their first admin (F3, F4), to confirm with the team. **16 October 2026:** user stories (§7) checked against the DoR (owner: Tiago Teixeira); issues created on 22 October 2026 (#58 to #121, §8.3); sprint goals and initial release plan (§8) **[PROPOSED]**, to confirm with the team. **22 October 2026** (Tiago Teixeira, backlog owner): new role model, `FarolAdmin` (global), `EventAdmin` and `RegularUser` (per event), with teams and permissions defined by each event's admins (§4), replacing the fixed roles and teams of 9 October; stories split into smaller ones and renumbered US-01 to US-62 (§7.1). To confirm with the team.
> **Sources:** [`problem-opportunity-report-queima.md`](../Deliverables/problem-opportunity-report-queima.md) (*MVP definition*, §5 risks) · [`market-and-competitor-analysis-product-specific.md`](../Deliverables/market-and-competitor-analysis-product-specific.md) (§1, §2) · assignment PDF (§5.5, §5.6, §6).
> Legend: see [`product-vision.md`](product-vision.md#legend).

## 1. How the product solves the problem

| Root cause (report §2) | What the product does | Features |
|---|---|---|
| 1. No single source of operational information | One platform per event where the team publishes everything participants need | F5, F9, F10 |
| 2. Communication is static | The team updates information during the night; every item shows its last-update time. Participants report what they see, and the team confirms it | F10, F11, F12, F13 |
| 3. No shared spatial reference of the venue | The venue map, uploaded by the team, is the common reference for participants and operators | F5, F6, F12 |
| 4. Incident coordination depends on radio and phone *(hypothesis)* | Help requests arrive structured, with location, category and status, and are triaged and routed to a team of the event in the backoffice | F6, F7, F8 |
| 5. Slow processes at the gate | Out of scope as a process; at most, entrance status information | F17 |

## 2. Feature list and priority

MoSCoW revised on 9 October 2026. The MVP still follows the report's scope mitigation: *"prioritise help requests, the map and transport information in the MVP"* (report §5, execution risks). *Rank* is the build order within the Must-haves.

| ID | Feature | Component (annex §1) | MoSCoW | Rank | Persona | Notes |
|---|---|---|---|---|---|---|
| F1 | Login and logout | — | Must | 1 | P1, P2, P3 | **Login is mandatory for every user**, participants included. Mocked authentication is allowed for the MVP (assignment §6.6, footnote 1) |
| F2 | Personal account management | — | Must | 2 | P1, P2, P3 | Profile, password, consent, badge visibility, delete account (deletes badges and personal data) |
| F3 | Event management | C1 | Must | 3 | P4, P3 | Create, edit, publish and close events. Every other feature is scoped by event; isolation between events is a top technical risk (report §5). The `FarolAdmin` (P4) creates an event, for a verified organisation, and names its first `EventAdmin`; the EventAdmins edit, publish and close it (§4) |
| F4 | Event roles, teams and permissions | C1 | Must | 4 | P3, P4 | **22 Oct:** roles per event, `EventAdmin` and `RegularUser`, plus the global `FarolAdmin`, who never sees an event's help requests, reports or participants. EventAdmins create the event's teams (e.g. *Security*, *Nursing*), set each team's permissions and add RegularUsers to them (§4) |
| F5 | Venue map with points of interest | C3 | Must | 5 | P1, P3 | Map uploaded as an image or PDF; points placed on it (entrances, stages, WC, first aid, transport stops) |
| F6 | Help request | C6 | Must | 6 | P1 | Category and location marked on the map; location shared only with the request |
| F7 | Incident backoffice | C7 | Must | 7 | P2, P5 | Receive, prioritise, route to a team and close help requests; live list and map; full status history. Members of a team with the *respond* permission get what is routed to their team on the phone and update its status (§4.3) |
| F8 | AI help-request triage, with non-AI fallback | C9 | Must (assignment §5.6) | 8 | P2 | Suggests category, priority and team (one of the event's teams that handles the category, §4.2); the operator decides. Kept separate from F7 on purpose (see note below). See §5 |
| F9 | Transport information with external integration | C5 | Must (assignment §6.5) | 9 | P1, P3 | **Porto Digital urban platform** (FIWARE NGSI v2): live positions of the city buses (§3), plus event shuttles and temporary stops entered by the team |
| F10 | Live announcements, with last-update time | C4 | Must | 10 | P1, P3 | |
| F11 | Weak-network mode: cached map and information, "data from HH:MM" | C10 | Could | — | P1 | Not one of the §6.8 degraded-mode scenarios (§3.1). Moved from Must to Could on 9 October 2026 |
| F12 | Community reports on the map | — | Must (assignment §5.5) | 11 | P1 | Waze-style reports (queues, WC out of service, …). See §6 |
| F13 | Report confirmation and unconfirmation by the responsible team | — | Must | 12 | P2, P5 | Only the responsible team confirms a report; a confirmation can be withdrawn. See §6.2 |
| F14 | Badges for confirmed reports (all-time and per event) | — | Must | 13 | P1 | Two of the three badge tracks. See §6.3 |
| F15 | Badges for event attendance | — | Should | — | P1 | Third badge track. See §6.3 |
| F16 | Event check-in at the venue | — | Should | — | P1, P3 | The logged-in participant scans the event's QR code **at the venue, during the event dates**; this is what counts as "attended" for F15. See §6.3 |
| F17 | Entrance status per gate, updated manually by the team | C8 | Could | — | P1, P3 | |
| F18 | Friends inside the app | — | Could | — | P1 | Add and remove friends |
| F19 | Chat between friends | — | Won't (this release) | — | P1 | |
| F20 | Link to the ticket on the ticketing partner's site | C11 | Won't (this release) | — | P1 | Ticketing is a partner, not part of the MVP (annex C11) |

**Out of scope** (report, *Out of scope*; annex §1): physical capacity of entrances, heavy integrations, advanced crowd prediction, full automation, own ticketing.

**Removed on 9 October 2026:** the former *"participant web app opened by link or QR, without an account"*. Login is now mandatory, so the participant app is simply the client application (assignment §6.1), reached after F1. The QR code still opens the event, after login.

**Why F7 and F8 stay separate (proposed answer to "should they be merged?"):** on the slide they are presented together as *incident handling*. In the backlog they stay apart because (1) F7 must work on its own: it **is** the fallback when the AI is down (assignment §5.6.2, scenario D1); (2) F8 has its own evaluation, acceptance criteria and Responsible AI assessment (assignment §5.6.1, Sprint 1 deliverable 6); (3) they can be built and demoed in different sprints. If the team prefers one ID, rename F8 to *F7.b*. Nothing else changes.

## 3. Assignment requirements covered by the features

| Assignment requirement | How the product meets it | Status |
|---|---|---|
| §5.5 Ethical engagement design | F12–F15: community reports, team confirmation and badges | Decided (§6) |
| §5.6 At least one AI-assisted workflow, with evaluation and baseline or fallback | F8: help-request triage | Decided; details open (§5.2) |
| §6.1 Client application | Participant web app (PWA) and backoffice, both behind login | Decided |
| §6.3 At least one asynchronous workflow | Help request → AI triage → notification of the event team (see [`architecture.md`](../Technical/architecture.md)) | Decided ([ADR-004](../Technical/adr/0004-service-communication.md)) |
| §6.5 Integration with at least one external system or realistic simulator | **Porto Digital urban platform**, FIWARE NGSI v2 bus API (F9; accepted by the professor, [ADR-008](../Technical/adr/0008-transport-data-source.md)) | Decided |
| §6.6 Authentication and authorisation | F1 login for every user; F2 account and consent; F4 per-event roles and team permissions (§4); audit records for help requests (`AuditEntry` in the domain model; NFR-16) | Decided; mocked authentication allowed for the MVP |
| §6.8 At least one degraded-mode scenario | **D1** AI unavailable and **D2** transport source unavailable (§3.1) | Decided |

### 3.1 Degraded-mode scenarios (decided, 9 October 2026)

Two scenarios, no more. Both are demoed by breaking one dependency while the rest of the product keeps working. Assignment §6.8: *"fail safely and communicate limitations clearly to users"*.

#### D1 · AI triage unavailable

| | |
|---|---|
| **Dependency** | AI model provider used by the triage worker (F8) |
| **Detected when** | No valid suggestion within **30 s** of submission (SC3), because of a timeout (response 20 s, one attempt: the local profile, NFR-11), a provider error, an invalid or incomplete answer (unknown category, missing field), or the provider being marked down after repeated failures (circuit breaker: at least half of the last 10 calls failed or were slow, minimum 4 calls). Values: [`architecture.md` §9.1](../Technical/architecture.md#91-resilience-patterns-and-values-j1) |
| **What the operator sees** | The request appears in the backoffice **at once** (it never waits for the AI), with the participant's category, the label **"Not triaged"**, in order of arrival. While the provider is down (circuit breaker open), a banner says *"AI triage unavailable: requests are shown by order of arrival"* |
| **What the operator does** | Sets the priority and team by hand. Nothing else changes in F7 |
| **What the participant sees** | Nothing different. Their request is sent and tracked as usual |
| **When the AI comes back** | The banner disappears after the first successful call. Requests from the last 15 min that the operator has not touched are sent to triage once more and may still receive a suggestion; a suggestion **never overwrites** an operator's decision and **never lowers** a priority (NFR-22) |
| **Recorded** | Every fallback is logged with the reason (timeout, error, invalid output) and the correlation id, and counted as a metric (`farol.triage.fallback`); the circuit breaker state is on the dashboard ([ADR-012](../Technical/adr/0012-observability.md)) |
| **How it is demoed** | On the local machine (local-only delivery, [ADR-011](../Technical/adr/0011-deployment-and-infrastructure.md)), the triage worker points at **WireMock** (the simulator of guide J4), which normally passes requests to the real AI provider. The presenter makes it drop the connection (or answer late, or return invalid output) and a participant submits help requests from the web app's phone pages; the services carry no fault-injection code. Step-by-step script: [`architecture.md` §9.3](../Technical/architecture.md#93-demo-script-d1-and-d2-about-5-minutes) |
| **Test** | US-36 (SC5): 100 % of requests are shown to the operator with the AI off. Reports integration test on every PR, with WireMock in place of the provider (delay over the timeout, 503, dropped connection, invalid body); also under load in Sprint C (scenario L5) |

#### D2 · Transport source unavailable

| | |
|---|---|
| **Dependency** | Porto Digital urban platform (FIWARE NGSI v2 bus API, [ADR-008](../Technical/adr/0008-transport-data-source.md)) |
| **Detected when** | The import fails (no answer, HTTP error), the feed is invalid or incomplete (fails validation on import), or the data is older than its freshness limit: **5 min** (decided 22 October 2026; the *y* of the meeting with the professor, P4), so late observations are dropped on import. Bus positions normally arrive about 1 min old, and a bus covers about 2 km in 5 min, so an older position misleads. The 24 h proposed earlier was for a static GTFS timetable, dropped with the professor's answer |
| **What the participant sees** | The **last valid data**, with *"Data from HH:MM"* and a warning *"Live transport information unavailable"*, plus a link to the operator's website. **Event shuttles and temporary stops entered by the team are not affected**: they are internal data |
| **What the team sees** | A warning in the backoffice that the transport source is down, with the time of the last successful import |
| **Invalid data** | Never replaces valid data: the import is rejected as a whole and the previous version stays in use |
| **Recovery** | Import every 30 s (the *x* of the meeting with the professor, P4), retried with backoff (3 attempts, 1 s and 2 s apart; after repeated failures the circuit breaker waits 60 s before trying again, so a public service we do not control is not hammered); the warning disappears after the first valid import |
| **How it is demoed** | On the local machine, Transports points at **WireMock**, the simulator of the Porto Digital API (guide J4), which passes requests to the real API or replays the responses recorded on 25 October 2026 ([`infra/wiremock/`](../../infra/wiremock/README.md)). The presenter makes it return invalid data, then drop the connection, and the participant opens the transport screen. Script: [`architecture.md` §9.3](../Technical/architecture.md#93-demo-script-d1-and-d2-about-5-minutes) |
| **Test** | US-42, US-43: Transports integration test on every PR, with WireMock replaying recorded Porto Digital responses, then a 503, a timeout and an invalid payload, and a fixed clock past the freshness limit: the last valid data, its time and the stale flag are returned, shuttles still shown, invalid data never stored |

## 4. Roles, teams and permissions (22 October 2026)

Defined on 22 October 2026 by Tiago Teixeira (backlog owner), to confirm with the team. It **replaces** the fixed event roles (`Owner`, `Admin`, `Operator`, `Responder`) and the four fixed response teams decided on 9 October 2026: each event now creates its own teams and decides what each one may do. The personas do not change: P2 (operator) and P5 (responder) are now **jobs**, given by the permissions of the team a user belongs to, not roles.

### 4.1 Roles

| Role | Scope | What it can do |
|---|---|---|
| **FarolAdmin** | The whole platform (global) | Creates events for verified organisations and names their first EventAdmin; names a new EventAdmin when an event has none; suspends and reactivates events. Sees only each event's name, dates, organisation, EventAdmins and status, **never** its help requests, reports or participants (persona P4) |
| **EventAdmin** | One event | Everything in that event: edits, publishes and closes it; creates its teams and sets their permissions; adds users to teams; makes other members EventAdmin (persona P3) |
| **RegularUser** | One event | Every user who joins the event (scanning its QR code or opening its link, US-12). Uses the participant features: asks for help, reports problems, checks in (persona P1). Gets more permissions only from the teams they are in |

- Roles belong to a user's **membership of an event**, not to the account. The same person can be **EventAdmin in one event and RegularUser in another**.
- A user who is not a member of an event has no access to its data (US-14). `FarolAdmin` is the only global role, and it gives no access to any event's data.
- An event always keeps **at least one EventAdmin**: the last one cannot be removed or demoted, nor delete their account (US-05; guide D7).

### 4.2 Teams and permissions

An EventAdmin creates the teams of their event (e.g. *Security*, *Nursing*, *PCR*, *Logistics*, *Control room*), gives each team permissions from the fixed list below, and adds RegularUsers of the event to it.

| Permission | Members of the team can… | Persona |
|---|---|---|
| `HelpRequests.Triage` | See every help request of the event, set its priority and status, route it to a team and close it | P2 |
| `HelpRequests.Respond` | Receive the help requests routed to their team, accept them and mark them resolved | P5 |
| `Reports.Review` *(for chosen report types)* | Confirm, reject and unconfirm reports of those types; send them to a team to check or fix | P2 |
| `Reports.Respond` | Receive the reports sent to their team, answer "true / not true" and mark them resolved | P5 |
| `Announcements.Publish` | Publish, edit and remove announcements | P3, P2 |
| `Map.Edit` | Upload the venue map and edit its points of interest | P3 |
| `Transport.Edit` | Add and change event shuttles and temporary stops | P3 |
| `Entrances.Update` | Update the status of each entrance | P2, P5 |

- Each team also has the **help categories it handles** (`Health`, `Safety`, `LostPerson`, `Theft`, `Other`). They give the default team when a request is routed, and the teams the AI may suggest (F8).
- A user can be in **several teams** of the same event and gets the permissions of all of them.
- A change to a team's permissions or members applies at the member's **next action** and is recorded with who made it and when.
- An EventAdmin has every permission in their event without being in a team.
- The permissions are a **fixed list** in the product; EventAdmins choose from it and cannot invent new ones. This keeps authorisation testable (SC8).

### 4.3 Who receives what, and who acts

Professor feedback on the presentation (12 October 2026): it was not clear who receives the reports and how they are handled. With teams and permissions:

| What | Goes to | Who acts |
|---|---|---|
| **Help request** | Every member with `HelpRequests.Triage` (one shared queue, the control room) | The control room sets priority and team, then routes it to a team, whose members with `HelpRequests.Respond` accept it, go to the point on the map and resolve it |
| **Report** | Members with `Reports.Review` for its type | They confirm or reject it. If it cannot be checked from the control room, or needs fixing, they send it to a team with `Reports.Respond` |

- **Help requests** always pass through the control room first, because they need a decision: priority, team, whether someone must go.
- **Reports** go straight to whoever reviews their type, because the type already says who is responsible.

Both journeys as BPMN: [`user-workflows.md`, *Complete journeys*](user-workflows.md#complete-journeys-bpmn). The lanes there are personas (P2, P5), which are now team permissions.

### 4.4 Pilot set-up for Queima das Fitas

A suggested starting point for the pilot event. The EventAdmin creates it by hand (US-16 to US-18) and can change it.

| Team | Permissions | Help categories | Report types it reviews |
|---|---|---|---|
| **Control room** | `HelpRequests.Triage`, `Reports.Review`, `Announcements.Publish`, `Entrances.Update` | — | All |
| **Nursing** (medical) | `HelpRequests.Respond` | `Health` | — |
| **Security** | `HelpRequests.Respond`, `Reports.Respond`, `Entrances.Update` | `Safety`, `Theft` | — |
| **PCR** (risk-prevention committee) | `HelpRequests.Respond`, `Reports.Respond` | `LostPerson`, `Other` | — |
| **Logistics** | `Reports.Respond`, `Transport.Edit` | — | — |

The help categories still need to be checked against the interview with the security volunteer (DM-4).

## 5. AI-assisted workflow: help-request triage

### 5.1 What is decided

**Decision (8 October 2026): help-request triage.** It is part of the core journey (help requests are the top MVP priority), its fallback already exists in the product (F7), and it is easy to evaluate with a synthetic dataset. The participant Q&A assistant was rejected: a wrong transport answer at night is a higher safety risk, and a chat interface would need justification (assignment §5.6).

| | Help-request triage |
|---|---|
| What the AI does | Reads the participant's category and optional text, then suggests a **category, a priority and the team** (one of the event's teams that handles the category, §4.2). The operator accepts or changes the suggestion |
| User need | Requests arrive without category or priority (root cause 4); assumption A4 |
| Non-AI fallback | Scenario D1 (§3.1): participant's category, order of arrival, "Not triaged" |
| Human control | The AI only suggests. It never lowers a priority, and it never overwrites an operator's decision (NFR-22) |
| Speed target | Suggestion within **30 s** of submission (SC3) |
| Market | Raven Controls does this for professional control rooms (annex C9); nobody does it for academic events |

### 5.2 Still to define

**Research available (22 October 2026, #53):** [Responsible AI assessment](../Research/architecture-investigation/responsible-ai.md) proposes answers to AI-1–AI-11, with an [evaluation package](../Research/architecture-investigation/evaluation/README.md) and [ADR-005](../Technical/adr/0005-ai-triage-provider-and-evaluation.md), accepted as architecture on 31 October 2026 (#166). Still pending: independent labels, provider/privacy review and measured model/SC3/SC5 evidence (guide K1, K2). The original questions below remain the review checklist.

This is the list the team works through before the AI work starts. Owner: AI lead (see [`team-and-process.md`](../Process/team-and-process.md) §2).

| # | Item | Open question | Needed for |
|---|---|---|---|
| AI-1 | **Input** | Exactly which fields go to the model: category, free text, map zone, time of night? Is the location sent, or only the zone? | Privacy, prompt design |
| AI-2 | **Output format** | A fixed JSON schema: category (fixed list), priority (`Low`/`Medium`/`High`/`Critical`), team (one of the event's teams, §4.2), a short reason for the operator. What happens with an answer outside the schema (→ D1) | Validation, UI |
| AI-3 | **Priority scale** | What each level means, with examples, so people and the AI apply the same rule. What is the "default priority" of each category, so that "never lowers a priority" is measurable | Evaluation, NFR-22 |
| AI-4 | **Model and provider** | Which provider and model; hosted API or local model; cost per request; what happens to the text sent (data retention by the provider, GDPR) | Cost, privacy, ADR |
| AI-5 | **Prompt design** | System prompt, few-shot examples, European Portuguese informal language, how the participant's text is isolated from the instructions | Quality, prompt injection |
| AI-6 | **Evaluation dataset** | Size (proposal: ≥ 100 cases), who writes and labels it, share of difficult cases (typos, slang, mixed languages, empty text, prompt injection, two problems in one request), how origin, scope and limits are documented (§5.6.1) | Evaluation |
| AI-7 | **Acceptance criteria** | Confirm SC4: 0 urgent cases under-prioritised, category accuracy ≥ 80 %; add response-team accuracy? Compared with the non-AI baseline (participant's category only) | Go / no-go |
| AI-8 | **What the operator sees** | Is the suggestion shown with a reason or a confidence? How is it marked as a suggestion, not a fact (§5.6.2)? One click to accept? | UI, trust |
| AI-9 | **Monitoring** | Metrics: latency p95, fallback rate, % of suggestions accepted or changed by operators, cost per night | Observability (§6.7) |
| AI-10 | **Safety and trust** | How each item of §5.6.3 is handled: inaccurate output, prompt injection, disclosure, harmful content, bias (e.g. by language), user correction, human approval | Responsible AI assessment |
| AI-11 | **Responsible AI Opportunity Assessment** | The Sprint 1 document itself (deliverable 6): proposed workflow, expected value, risks, evaluation approach, fallback | Sprint 1 deliverable |

## 6. Ethical engagement mechanism: community reports and badges

**Decision (8 October, detailed 9 October 2026):** participants report what they see on the venue map, in the style of Waze. The responsible team confirms useful reports, and participants earn **badges** for confirmed reports and for events attended. Reports keep information fresh for everyone (root cause 2) and show the team problems sooner; badges give participants a reason to report.

### 6.1 What can be reported

Each report is a type, a point on the venue map and a time. It is not a help request: anything urgent or about a person goes through *Ask for help*.

| Report type | Example | Field team in the pilot set-up (§4.4) | Expires after |
|---|---|---|---|
| Queue at an entrance | "Gate 2: long queue (> 15 min)" · short / medium / long | Security | 30 min |
| Crowded area | "Very crowded in front of the main stage" | Security | 20 min |
| WC problem | Out of service, no water, … | Logistics | 60 min |
| Water point or bar out of water | "No water at bar 3" | PCR | 60 min |
| Hazard | Broken glass, spill, dark area, broken fence, … | Logistics | Until the team closes it |
| Transport problem | "Shuttle stop moved", "shuttle full", "shuttle not passing" | Logistics | 30 min |
| Wrong information | A map point or announcement that is no longer true | Logistics | Until the team closes it |

### 6.2 How a report behaves

1. A participant places a report on the map. It goes straight to the **members with `Reports.Review` for its type** (§4.2, §4.3), who see it **in the backoffice at once**. Its status is *Pending*.
2. **Only a member with `Reports.Review` for its type (or an EventAdmin) confirms or rejects it.** If it cannot be checked from the control room, they send it to a team with `Reports.Respond` to check it on site. Other participants see it only once it is confirmed. They can tap *"still there"* or *"not there anymore"*: this helps the team decide, but it does not confirm the report and does not count for badges. **[PROPOSED]**
3. A confirmed report counts for the reporter's badges and appears on everyone's map with its time.
4. The team can **unconfirm** a report (e.g. confirmed by mistake, or found to be false). It disappears from the map, **no longer counts** for badges, and the reporter's badge levels are recalculated, so a badge can go down a tier.
5. A report **expires** after the time set for its type (§6.1). An expired confirmed report still counts for badges; it is only removed from the map.
6. A report never changes official information (map, announcements, transport) automatically. The team does that.
7. If the problem needs action (a broken WC, broken glass), the reviewer sends it to a team with `Reports.Respond`, and a member of that team fixes it and marks the report *Resolved*: it leaves the map and still counts for badges. A report nobody confirms is removed when it expires and earns nothing.

Report statuses **[PROPOSED]**: `Pending → Confirmed | Rejected | Expired`, then `Confirmed → Resolved | Expired | Unconfirmed`.

### 6.3 Badges (decided)

Badges are always linked to the participant's **account** (F1, F2). There are no anonymous badges. There are **three badge tracks**; each one moves up through the same six tiers as the count grows.

| Track | What is counted | Scope | MoSCoW |
|---|---|---|---|
| **Event attendance** | Events attended | All events, across the years | Should (F15) |
| **Report history** | Confirmed reports | All events, across the years | Must (F14) |
| **Event reporter** | Confirmed reports | One specific event (e.g. "Queima 2027") | Must (F14) |

| Tier | Count (events attended or confirmed reports) |
|---|---|
| **Wood** | 1–2 |
| **Bronze** | 3–9 |
| **Silver** | 10–49 |
| **Gold** | 50–99 |
| **Platinum** | 100–499 |
| **Diamond** | 500+ |

**There are no badges for help requests**: emergencies must never be gamified.

**How attendance is proved (decided, 9 October 2026):** an event counts as attended when the participant, **logged in**, scans the event's QR code **at the venue during the event dates** (F16, check-in). It counts once per event. Opening the event at home, or from a link, does not count. The QR codes are placed at the venue entrances by the event team.

### 6.4 The five questions of assignment §5.5

1. **Behaviour encouraged:** participants report what they see at the venue, which keeps information accurate for everyone, and they come back to the events.
2. **Why it helps the community:** other participants avoid queues and broken services; the team learns about problems before they become incidents.
3. **Misuse prevention:**
   - only reports **confirmed by the responsible team** count for badges, so false reports earn nothing;
   - the team can unconfirm a report, and the badge count goes back down;
   - reports are rate-limited per account;
   - reports expire;
   - the team can hide reports and suspend an account from reporting;
   - reports never change official information automatically.
4. **Exclusion or exposure:**
   - no public leaderboard and no public profile; badges are private by default;
   - nothing shows other participants who made a report;
   - attendance badges show neither which nights you were there nor where you were;
   - participants who don't report lose no information.
5. **Control and opt-out:** reporting and badges are optional. In account management (F2) a participant can hide their badges, stop earning them, or delete them; deleting the account deletes the badges.

## 7. Initial backlog: user stories

Rewritten on 9 October 2026 to match the feature list above; checked against the [Definition of Ready](../Process/definition-of-ready-and-done.md#definition-of-ready-backlog-item) on 16 October 2026 (professor feedback T1, [notes](../Meetings/2026-10-12-sprint-1-presentation-feedback.md)); split into smaller stories and renumbered on 22 October 2026, with the role model of §4. One story = one issue = one PR. Acceptance criteria use *Given / When / Then*. Every story names its persona and its dependencies; a story moves to *Ready* on the board only when none of its dependencies blocks it (§8.4). Won't-have features (F19, F20) have no stories.

### 7.1 DoR check and split

#### DoR check (16 October 2026)

| DoR item | What failed | Fix |
|---|---|---|
| User story format | Old US-04 had no *so that* | Added (US-14) |
| Persona | No story named a persona ID; old US-01 and US-02 said only "user" | *Persona* line on every story (P1–P5, or the technical goal) |
| Testable *Given / When / Then* | Help request offline needed the cached map (F11, Could); routing assumed push notifications (guide C5 decided polling) and had no criterion to close; adding a shuttle had no criterion; transport assumed a timetable feed, but the source is not decided; "too many reports" had no number; reports had no *reject*; entrance status had nothing for the team side | Criteria rewritten (see each story). Numbers that are new are marked **[PROPOSED]** |
| Dependencies identified | No story listed its dependencies | *Depends on* line on every story; blockers in §8.4 |
| One story = one PR | Several stories held two or more PRs of work | Split (below) |
| AI fallback | The AI suggestion did not say what happens when the AI fails | *AI fallback* line pointing to US-36 (scenario D1) |
| On the board | No story had an issue | Issues #58 to #121 created on 22 October 2026 from the user-story form (§8.3). All of them are on the [GitHub Projects board](https://github.com/users/tomasoliveira21/projects/1) (checked 24 October 2026); the DoR box "On the GitHub Projects board" is ticked in each story issue (24 October 2026) |

#### How the stories were split (22 October 2026)

The 29 stories of 16 October became **62**. Each story is now **one user action, or one rule, that can be built, tested and demoed on its own in one PR of a few days**. Three rules were used:

1. **One action per story.** *Log in* and *log out* are different actions with different tests, so they are two stories (US-01, US-02). The same for publishing, joining and closing an event, or for accepting and resolving a request.
2. **Separate what is blocked from what can start.** A part that waits for an open decision becomes its own story, so the rest can be *Ready*. Example: account deletion (US-05) waits for guide D7; sign-up (US-03) does not.
3. **Separate apps and personas.** What the event team does in the backoffice and what the participant sees are different PRs, on different pages of the web app (guide F1). Example: the team adds shuttles (US-37), the participant sees them (US-39).

| Old story (16 October) | New stories |
|---|---|
| US-01 Log in and log out | US-01, US-02; the QR-code criterion moved to US-12 |
| US-02 Create and edit account | US-03, US-04 |
| US-29 Delete account | US-05 |
| US-25 Create an event | US-06, US-07 |
| US-28 Transfer Owner, suspend event | US-08, US-09 |
| US-03 Edit, publish and close an event | US-10, US-11, US-12, US-13 |
| US-04 Isolation between events | US-14 |
| US-05 Invite members, roles and teams | US-15, US-16, US-17, US-18 (new role model, §4) |
| US-06 Venue map and points | US-19, US-20, US-21 |
| US-07 Find a type of place | US-22 |
| US-08 Ask for help | US-23, US-24, US-25 |
| US-09 See new requests | US-26, US-27 |
| US-10 Prioritise, route, close | US-28, US-29, US-30 |
| US-26 Responder | US-31, US-32, US-33 |
| US-11 AI suggestion | US-34, US-35 |
| US-12 Requests without AI (D1) | US-36 |
| US-13 Shuttles | US-37, US-38, US-39 |
| US-14 Next transport options | US-40, US-41 |
| US-15 Out-of-date transport (D2) | US-42, US-43 |
| US-16 Announcements | US-44, US-45 |
| US-17 Weak-network mode | US-46 |
| US-18 Report a problem | US-47, US-48, US-49 |
| US-19 Confirm, reject, unconfirm | US-50, US-51 |
| US-27 Send a report to a responder | US-52, US-53 |
| US-20 Badges for confirmed reports | US-54, US-55, US-56 |
| US-21 Attendance badge | US-57 |
| US-22 Check-in | US-58 |
| US-23 Entrance status | US-59, US-60 |
| US-24 Friends | US-61, US-62 |

#### Why these numbers **[PROPOSED]**

| Number | Story | Why |
|---|---|---|
| **5 reports in 10 minutes** per account | US-48 | A participant can see several problems at once (a queue, a broken WC, broken glass), so 5 allows a real burst. More than that in 10 minutes looks like spam or badge farming, which §6.4 must prevent. Reports expire after 20–60 minutes (§6.1), so 10 minutes is shorter than any expiry. It is a setting, to tune with real data. Confirmed by Tiago on 24 October 2026 (#137) |
| **≤ 5 s** until a routed request appears on a responder's phone | US-31 | Same target as SC2 for the control room: the responder is on the critical path of a help request. With polling (guide C5) it needs a refresh every 2–3 s on the open screen, which one instance per service can serve at the expected load |

#### Why these criteria were rewritten

| Story | Before | After | Why |
|---|---|---|---|
| US-25 (no network) | "shows the nearest first-aid point" | Says the request was not sent, lets the participant send it again, and shows the first-aid points if the map is already loaded | Finding the nearest point offline needs the cached map, which is F11 (Could). A Must story cannot depend on a Could. The new criterion can be tested with the map in memory |
| US-29 (routing) | "its responders get an alert on their phone" | The request appears in the routed team's list (US-31) | Push notifications were never decided; the team decided polling (guide C5). The phone side is US-31, so US-29 stops where the control room's work ends |
| US-41 (transport) | "next departures" from the STCP feed | "next buses at the stops near the venue", from whatever source is chosen | The source is waiting for the professor (Porto Digital FIWARE or another). FIWARE gives buses in circulation, not timetables, so the old criterion could fail whichever source is chosen |

#### Issue type of each story

README §1 allows four issue types. Rule used in §8.3, from the split between participant pages and event-team pages of the web app (guide F1):

- **[Front Office]**: the participant's pages of the web app, built for the phone (P1).
- **[Back Office]**: the event team's work: event management on the web (P3, P4) and the backoffice of the control room and of the field teams (P2, P5). Field-team members work on the phone, but in the backoffice field view (personas, P5), not in the participant pages.
- **[Technicians]**: technical work with no single user screen: login and logout (US-01, US-02, used by every page), isolation between events (US-14, a test in every service), the import of the transport source (US-40) and the walking skeleton.

### F1 · Login and logout

**US-01** · As a **user**, I want to log in, so that I can use my account and the events I belong to.
*Persona:* P1, P2, P3, P4, P5 · *Depends on:* walking skeleton (§8.2); identity provider (guide E1, Guerra; mocked authentication is allowed for the MVP, assignment §6.6)
- Given I have an account, when I log in with valid credentials, then I see the events I am a member of.
- Given I enter wrong credentials, when I log in, then I see an error that does not say which field was wrong.

**US-02** · As a **user**, I want to log out, so that nobody else can use my session on this device.
*Persona:* P1, P2, P3, P4, P5 · *Depends on:* US-01
- Given I am logged in, when I log out, then my session ends and protected pages ask me to log in again.
- Given I logged out, when the old session token is used, then it is refused.

### F2 · Personal account management

**US-03** · As a **new user**, I want to create an account, so that I can join events on Farol.
*Persona:* P1, P2, P3, P5 · *Depends on:* US-01; identity provider (guide E1)
- Given I am a new user, when I sign up and accept the terms, then my account is created and I am logged in.
- Given I do not accept the terms, when I sign up, then no account is created.
- Given the e-mail is already used by an account, when I sign up, then no new account is created.

**US-04** · As a **user**, I want to edit my name and password, so that my account stays correct and safe.
*Persona:* P1, P2, P3, P4, P5 · *Depends on:* US-03
- Given I am logged in, when I edit my name, then the change is saved.
- Given I change my password, when I log in again, then only the new password works.

**US-05** · As a **user**, I want to delete my account, so that my personal data leaves Farol.
*Persona:* P1, P2, P3, P5 · *Depends on:* US-03, US-54; account deletion (guide D7, Bruno)
- Given I delete my account, then my personal data, memberships and badges are deleted, and my past reports stay only as anonymous statistics.
- Given I deleted my account, when I try to log in with it, then I am refused.
- Given I am the last EventAdmin of an event, when I try to delete my account, then I am asked to name another EventAdmin first.

### F3 · Event management

**US-06** · As the **FarolAdmin**, I want to create an event for a verified organisation and name its first EventAdmin, so that only real organisers run events on Farol.
*Persona:* P4 · *Depends on:* US-01; role model (§4)
- Given an organisation asks for a new event, when I confirm the requester represents it and create the event with a name and dates, then it is saved as a draft that participants cannot see, and the named user becomes its EventAdmin.
- Given a user who is not the FarolAdmin, when they try to create an event, then the action is refused.

**US-07** · As the **FarolAdmin**, I want to see the list of events with only their basic details, so that I can manage the platform without seeing private event data.
*Persona:* P4 · *Depends on:* US-06
- Given I open the list of events, then I see each event's name, dates, organisation, EventAdmins and status.
- Given I am the FarolAdmin and not a member of event X, when I try to open its help requests, reports or participants, then the request is refused.

**US-08** · As the **FarolAdmin**, I want to name a new EventAdmin for an event, so that an event is never left without someone in charge.
*Persona:* P4 · *Depends on:* US-06
- Given the organisation asks (e.g. its only EventAdmin left), when I name a new EventAdmin, then they get the role in that event and the change is recorded with my name and the time.

**US-09** · As the **FarolAdmin**, I want to suspend and reactivate an event, so that an abandoned or misused event cannot be used.
*Persona:* P4 · *Depends on:* US-06
- Given I suspend an event, then participants can no longer open it, and its EventAdmins see that it is suspended.
- Given I reactivate a suspended event, then it returns to the status it had before.

**US-10** · As an **EventAdmin**, I want to edit my event's details, so that participants see the right name, dates and description.
*Persona:* P3 · *Depends on:* US-06
- Given I am an EventAdmin of a draft or published event, when I change its name, dates or description, then the change is saved and shown to participants.
- Given I am a RegularUser of the event, when I try to edit it, then the action is refused.

**US-11** · As an **EventAdmin**, I want to publish my event, so that participants can find it.
*Persona:* P3 · *Depends on:* US-06
- Given my event is a draft, when I publish it, then participants can open it, and its QR code and link are generated.

**US-12** · As a **participant**, I want to join an event by scanning its QR code or opening its link, so that I can use it.
*Persona:* P1 · *Depends on:* US-01, US-11
- Given I am logged in, when I scan the QR code of a published event, then I become a RegularUser of that event and it opens.
- Given I am not logged in, when I scan the QR code, then I am asked to log in and then taken to that event.
- Given the event is a draft, closed or suspended, when I scan its QR code, then I cannot join it.

**US-13** · As an **EventAdmin**, I want to close my event, so that it stops receiving help requests and reports when it is over.
*Persona:* P3 · *Depends on:* US-11
- Given I close the event, then participants can no longer send help requests or reports to it, and they see it is closed.

**US-14** · As a **member of event X**, I want no one outside my event to see its data, so that our help requests, reports and participants stay private (SC8).
*Persona:* P1, P2, P3, P5; technical goal SC8 (report §5, technical risks) · *Depends on:* US-01, US-06; `event_id` filtering (guide D3, decided); role model (§4)
- Given a user is a member of event X only, when they request any data from event Y, then the request is refused and the attempt is logged with the user, the event and the time.
- Given a user is EventAdmin in event X and RegularUser in event Y, when they try an EventAdmin action in event Y, then it is refused.
- Given a service stores event data, then an integration test proves that a member of event X cannot read or change event Y's data (guide D3).

### F4 · Event roles, teams and permissions

**US-15** · As an **EventAdmin**, I want to make a member of my event an EventAdmin, or remove that role, so that I can share the management of the event.
*Persona:* P3 · *Depends on:* US-12; role model (§4)
- Given a RegularUser of my event, when I make them EventAdmin, then they can manage this event only, and the change is recorded with my name and the time.
- Given an EventAdmin of my event, when I make them a RegularUser again, then they lose the EventAdmin permissions at their next action.
- Given I am the last EventAdmin, when I try to remove my own role, then the action is refused.

**US-16** · As an **EventAdmin**, I want to create, rename and delete the teams of my event (e.g. *Security*, *Nursing*), so that the event's organisation is reflected in Farol.
*Persona:* P3 · *Depends on:* US-06; role model (§4)
- Given I create a team with a name, then it appears in my event's team list, with no members and no permissions.
- Given a team name already exists in my event, when I create another with the same name, then it is refused.
- Given I delete a team, then its members lose its permissions, and the requests routed to it go back to the control room queue.

**US-17** · As an **EventAdmin**, I want to set the permissions and help categories of each team, so that each team can do exactly what its job needs.
*Persona:* P3 · *Depends on:* US-16
- Given a team, when I give it permissions from the list in §4.2, then its members can do those actions in this event only.
- Given I give `Reports.Review` to a team, then I choose the report types it reviews.
- Given I set the help categories a team handles, then it becomes the default team for those categories when a request is routed.
- Given I remove a permission, then the team's members lose it at their next action, and the change is recorded with my name and the time.

**US-18** · As an **EventAdmin**, I want to add RegularUsers of my event to a team, and remove them, so that the right people get the team's permissions.
*Persona:* P3 · *Depends on:* US-12, US-16
- Given a RegularUser of my event, when I add them to the *Nursing* team, then they get the team's permissions in this event.
- Given a user who is not a member of my event, when I try to add them to a team, then it is refused.
- Given a user is in two teams, then they have the permissions of both.
- Given I remove a user from a team, then they lose its permissions at their next action.

### F5 · Venue map

**US-19** · As an **EventAdmin** (or a member with `Map.Edit`), I want to upload the venue map as an image or PDF, so that participants and the team share the same reference.
*Persona:* P3 · *Depends on:* US-06; venue map library (guide F3, ADR-007, #44)
- Given I upload a valid image or PDF, when I save, then it becomes the event's current map version.
- Given I upload a file that is not an image or PDF, when I save, then it is refused with a message and the current map stays.
- Given I upload a file larger than 20 MB, when I save, then it is refused with a message giving the limit, and the current map stays (ADR-013, L-4).

**US-20** · As an **EventAdmin** (or a member with `Map.Edit`), I want to place, edit and remove points of interest on the map, so that people find entrances, stages, WC, first aid and transport stops.
*Persona:* P3 · *Depends on:* US-19
- Given I place a point of type *first aid* with a name, when I save, then it is stored at that position on the map.
- Given I move or remove a point, when I save, then the change is stored.

**US-21** · As a **participant**, I want to open the venue map with its points of interest, so that I know where things are.
*Persona:* P1 · *Depends on:* US-12, US-20
- Given the event has a map, when I open it, then I see the current version with every point, its icon and its name.
- Given the team uploaded a new map version, when I open the map again, then I see the new version.

**US-22** · As a **participant**, I want to filter the map by a type of place (WC, first aid, entrance, transport stop), so that I find it quickly.
*Persona:* P1 · *Depends on:* US-21
- Given the map has first-aid points, when I filter by *first aid*, then only those points are highlighted.
- Given the map has no point of the type I filter by, when I filter, then I see a message saying so.

### F6 · Help request

**US-23** · As a **participant**, I want to ask for help by choosing a category and marking where I am on the map, so that the team can reach me.
*Persona:* P1 · *Depends on:* US-14, US-21
- Given I choose a category and mark a location, when I submit, then I see a confirmation.
- Given I submitted a request, then my location is visible only to members with `HelpRequests.Triage`, to EventAdmins and to the team it is routed to.
- Given I have sent 5 help requests in the last 10 minutes, when I submit another, then it is refused with a message saying when I can ask again, and the first-aid points are shown on the map (ADR-013, L-2: answer 429 with `Retry-After`).

**US-24** · As a **participant**, I want to follow the status of my help request, so that I know whether help is coming.
*Persona:* P1 · *Depends on:* US-23
- Given I submitted a request, when I open it, then I see its status (*Sent*, *Seen*, *On the way*, *Resolved* or *Closed*) and the time of the last change.
- Given its status changes, then I see the new status within 5 s while the screen is open (polling, guide C5).

**US-25** · As a **participant**, I want to know when my help request was not sent, so that I never think help is coming when it is not.
*Persona:* P1 · *Depends on:* US-23
- Given I have no network, when I submit, then the app tells me the request was not sent, lets me send it again, and never shows it as sent.
- Given the map is already loaded, when the request fails, then the app shows where the first-aid points are.

### F7 · Incident backoffice

**US-26** · As a **control-room member** (`HelpRequests.Triage`), I want to see new help requests in a list as they arrive, so that I can react quickly.
*Persona:* P2 · *Depends on:* US-17, US-23
- Given a request is submitted for my event, then it appears in my list within 5 s without reloading the page (SC2; polling, guide C5).
- Given I do not have `HelpRequests.Triage` in this event, when I open the list, then it is refused.

**US-27** · As a **control-room member**, I want to see the help requests on the venue map, so that I know where each one is.
*Persona:* P2 · *Depends on:* US-26
- Given a request has a location, then it is shown at that point on the venue map, with its category and priority.

**US-28** · As a **control-room member**, I want to set a request's priority and status, with every change recorded, so that everyone knows how urgent it is and what happened.
*Persona:* P2 · *Depends on:* US-26
- Given I change the priority or status of a request, then the change is recorded with my name, the time, and the old and new values (SC9).
- Given I open a request, then I see its full change history.

**US-29** · As a **control-room member**, I want to route a request to a team, so that the right people deal with it.
*Persona:* P2 · *Depends on:* US-17, US-28
- Given I open a request, then the team that handles its category is proposed by default (US-17).
- Given I route a request to the *Nursing* team, then it appears in that team's list with its point on the map (US-31), and the routing is recorded.
- Given a team without `HelpRequests.Respond`, then I cannot route requests to it.

**US-30** · As a **control-room member**, I want to close a request, so that finished cases leave the active queue.
*Persona:* P2 · *Depends on:* US-28
- Given I close a request, then it leaves the active list, the participant sees it *Closed*, and the closing is recorded.

**US-31** · As a **field-team member** (`HelpRequests.Respond`), I want to see on my phone the requests routed to my team, so that I know where to go.
*Persona:* P5 · *Depends on:* US-29
- Given a request is routed to my team, then it appears on my phone within 5 s **[PROPOSED]**, with its category and its point on the venue map.
- Given a request is routed to another team, then I do not see it.

**US-32** · As a **field-team member**, I want to accept a request, so that the control room and the participant know someone is on the way.
*Persona:* P5 · *Depends on:* US-31
- Given I accept a request, then its status becomes *On the way* and the participant sees "Help is on the way".
- Given another member of my team already accepted it, then I see that and cannot accept it too.

**US-33** · As a **field-team member**, I want to mark a request resolved, so that the control room and the participant know it is over.
*Persona:* P5 · *Depends on:* US-32
- Given I accepted a request, when I mark it resolved, then the participant and the control room see it *Resolved*, and the change is recorded.

### F8 · AI help-request triage

**US-34** · As a **control-room member**, I want each new help request to arrive with a suggested category, priority and team, so that I can route it faster.
*Persona:* P2 · *Depends on:* US-17, US-26; AI provider and model (guide K1, Tomás); input, output schema and priority scale (§5.2 AI-1 to AI-3) · *AI fallback:* US-36 (scenario D1)
- Given a help request is submitted and the AI is available, then a suggestion is shown within 30 s, marked as a suggestion (SC3).
- Given the event's teams and their help categories (US-17), then the AI only suggests a team of this event.
- Given the AI suggests a priority lower than the category's default, then the default priority is kept (NFR-22; the per-category floor of the [local AI plan](../Technical/plans/ai-provider-evaluation.md)).

**US-35** · As a **control-room member**, I want to accept or change the AI suggestion, so that I stay in control of the decision.
*Persona:* P2 · *Depends on:* US-34 · *AI fallback:* US-36
- Given I accept the suggestion, then its values become the request's values and the change is recorded as mine.
- Given I already set a priority, when a suggestion arrives, then my priority is not changed.

**US-36** · As a **control-room member**, I want requests to keep arriving when the AI is down, so that no one is left without help (scenario D1).
*Persona:* P2 · *Depends on:* US-34 · *AI fallback:* this story is the fallback
- Given the AI does not answer within 30 s, or answers with invalid data, then the request is shown with the participant's category, marked "Not triaged", in order of arrival (SC5).
- Given the AI provider is down, then the backoffice shows a banner saying triage is unavailable.
- Given a fallback happens, then it is logged with its reason (timeout, error or invalid output) and counted in the fallback-rate metric.

### F9 · Transport information and external integration

**US-37** · As an **EventAdmin** (or a member with `Transport.Edit`), I want to add the event's shuttles and temporary stops with their schedule, so that participants know how to get home.
*Persona:* P3 · *Depends on:* US-20
- Given I add a shuttle with its stops and departure times, when I save, then it is stored for the event.

**US-38** · As an **EventAdmin** (or a member with `Transport.Edit`), I want to mark a shuttle as delayed or cancelled, so that participants do not wait for nothing.
*Persona:* P3 · *Depends on:* US-37
- Given I mark a shuttle as delayed or cancelled, then the change is stored with the time it was made.

**US-39** · As a **participant**, I want to see the event's shuttles and temporary stops, so that I know how to get home.
*Persona:* P1 · *Depends on:* US-37
- Given the event has shuttles, when I open the transport screen, then I see each shuttle's stops and next departures.
- Given a shuttle was marked delayed or cancelled (US-38), then I see that with the time of the change.

**US-40** · As the **Farol team**, we want to import the external transport source regularly and validate it, so that participants get regular public transport data (assignment §6.5).
*Persona:* technical goal (assignment §6.5) · *Depends on:* walking skeleton; transport data source: Porto Digital urban platform, accepted by the professor ([ADR-008](../Technical/adr/0008-transport-data-source.md)); proof of concept #48 as reference
- Given the source answers with valid data, when the import runs, then the data is stored with the time of the import.
- Given the source answers with invalid or incomplete data, when the import runs, then the import is rejected as a whole and the previous data stays in use.

**US-41** · As a **participant**, I want to see the next regular buses (STCP) home from the venue, so that I don't walk home or drive.
*Persona:* P1 · *Depends on:* US-12, US-40
- Given the external transport source was imported, then I see the next buses at the stops near the venue, with the time of the data.

**US-42** · As a **participant**, I want to know when the transport information may be out of date, so that I don't rely on it blindly (scenario D2).
*Persona:* P1 · *Depends on:* US-39, US-41; resilience values (guide J1, #43)
- Given the transport source is unavailable or its data is older than the freshness limit, then I see the last valid information with "Data from HH:MM" and a warning, and event shuttles are still shown.
- Given the source comes back, then the warning disappears after the next valid import.

**US-43** · As an **EventAdmin**, I want to be warned when the transport source is down, so that the team can tell participants another way.
*Persona:* P3 · *Depends on:* US-40; resilience values (guide J1, #43)
- Given the source is unavailable, then the backoffice shows a warning with the time of the last successful import.
- Given the source comes back, then the warning disappears after the next valid import.

### F10 · Announcements

**US-44** · As an **EventAdmin** (or a member with `Announcements.Publish`), I want to publish an announcement (e.g. "Gate 3 closed, use Gate 1"), so that participants find out immediately.
*Persona:* P3 · *Depends on:* US-12
- Given I publish an announcement, then participants with the app open see it within 15 s without reloading, with its publication time (SC6).
- Given I do not have `Announcements.Publish`, when I try to publish, then it is refused.

**US-45** · As an **EventAdmin** (or a member with `Announcements.Publish`), I want to edit or remove an announcement, so that wrong or old information disappears.
*Persona:* P3 · *Depends on:* US-44
- Given I edit or remove an announcement, then participants see the change within 15 s, and an edited one shows the time of the edit.

### F11 · Weak-network mode (Could)

**US-46** · As a **participant**, I want the map and the last information to work when the network drops, so that I'm not left without information.
*Persona:* P1 · *Depends on:* US-21, US-44
- Given I opened the event once with network, when I lose connection, then I still see the map and information with "Data from HH:MM".

### F12 · Community reports

**US-47** · As a **participant**, I want to report a problem on the map (e.g. a long queue at a gate), so that the team knows about it.
*Persona:* P1 · *Depends on:* US-17, US-21; report types and expiry times (§6.1)
- Given I choose a report type and a point on the map, when I submit, then the members with `Reports.Review` for that type see it in the backoffice straight away, with status *Pending*.
- Given my report is pending, then other participants do not see it, and I see it as *Pending*.

**US-48** · As a **participant**, I want a clear message when I report too often, so that I know when I can report again.
*Persona:* P1 · *Depends on:* US-47
- Given I have sent 5 reports in the last 10 minutes, when I send another, then it is refused with a message saying when I can report again.

**US-49** · As a **participant**, I want to see the confirmed reports on the map, so that I avoid queues and broken services.
*Persona:* P1 · *Depends on:* US-50
- Given a report was confirmed, then it appears on every participant's map with its type and time, and without the reporter's name.
- Given a report reaches the expiry time of its type (§6.1), then it leaves the map.

### F13 · Report confirmation

**US-50** · As a **reviewer** (`Reports.Review`), I want to confirm or reject a report, so that only true information is shown and rewarded.
*Persona:* P2 · *Depends on:* US-47
- Given a pending report of a type I review, when I confirm it, then it is shown on participants' maps (US-49) and counts as a confirmed report of the reporter.
- Given a pending report, when I reject it, then it never appears on participants' maps and does not count.
- Given a report of a type I do not review, then I cannot confirm or reject it (an EventAdmin can).

**US-51** · As a **reviewer**, I want to unconfirm a report, so that a mistake or a false report stops being shown and rewarded.
*Persona:* P2 · *Depends on:* US-50
- Given a confirmed report, when I unconfirm it, then it disappears from the map, no longer counts, and the change is recorded with my name and the time.

**US-52** · As a **reviewer**, I want to send a report to a field team to check it on site, so that reports I cannot check from the control room still get an answer.
*Persona:* P2, P5 · *Depends on:* US-50, US-31
- Given a pending report, when I send it to a team with `Reports.Respond`, then its members get it on their phone with its point on the map.
- Given a member answers "true" or "not true", then the answer comes back to me, and I confirm or reject the report.

**US-53** · As a **field-team member** (`Reports.Respond`), I want to fix the problem of a confirmed report and mark it resolved, so that it leaves the map.
*Persona:* P5 · *Depends on:* US-52
- Given a confirmed report was sent to my team to fix, when I mark it resolved, then it leaves the map and still counts for the reporter's badges.

### F14 · Badges for confirmed reports

**US-54** · As a **participant**, I want a *Report history* badge for all my confirmed reports, so that my contribution over the years is recognised.
*Persona:* P1 · *Depends on:* US-50, US-51
- Given my confirmed-report count reaches 1, 3, 10, 50, 100 or 500, then my *Report history* badge moves to Wood, Bronze, Silver, Gold, Platinum or Diamond.
- Given one of my confirmed reports is unconfirmed, then my count goes down and the tier is recalculated, so the badge can go down a tier.

**US-55** · As a **participant**, I want an *Event reporter* badge for each event, so that my contribution to that event is recognised.
*Persona:* P1 · *Depends on:* US-54
- Given my confirmed reports in event X reach a tier threshold, then my *Event reporter* badge for event X moves to that tier.
- Given one of my confirmed reports in event X is unconfirmed, then that badge is recalculated.

**US-56** · As a **participant**, I want to hide my badges or stop earning them, so that taking part stays my choice (§6.4).
*Persona:* P1 · *Depends on:* US-54
- Given I hide my badges in my account, then no one else can see them.
- Given I turn badges off, then no new badges are earned until I turn them on again.

### F15 · Badges for event attendance (Should)

**US-57** · As a **participant**, I want an *Event attendance* badge for the events I attend, so that coming back is recognised.
*Persona:* P1 · *Depends on:* US-54, US-58
- Given I checked in to an event (US-58), then the event counts as attended, once per event.
- Given my attended-event count reaches a tier threshold, then my *Event attendance* badge moves to that tier.

### F16 · Event check-in (Should)

**US-58** · As a **participant**, I want to check in at the venue by scanning the event's QR code, so that my attendance is recorded for my badges.
*Persona:* P1 · *Depends on:* US-12
- Given I am logged in and the event is running, when I scan the venue QR code, then I see "Checked in" and the event counts as attended.
- Given I already checked in to this event, when I scan again, then nothing changes (one check-in per event).
- Given the event has not started or has ended, when I scan the QR code, then the event opens but no check-in is recorded.

### F17 · Entrance status (Could)

**US-59** · As a **team member** with `Entrances.Update`, I want to set the status of each entrance, so that participants choose a better gate.
*Persona:* P2, P5 · *Depends on:* US-20
- Given I set Gate 2 to "long queue", then the status is saved with the time of the update.

**US-60** · As a **participant**, I want to see the status of each entrance, so that I choose a gate with a shorter queue.
*Persona:* P1 · *Depends on:* US-21, US-59
- Given the team marked Gate 2 as "long queue", then I see it on the map with the time of the update.

### F18 · Friends (Could)

**US-61** · As a **participant**, I want to send and accept friend requests, so that I can find people I know who use Farol.
*Persona:* P1 · *Depends on:* US-03
- Given I send a friend request and it is accepted, then each of us appears in the other's friend list.
- Given a friend request is declined, then neither of us appears in the other's list.

**US-62** · As a **participant**, I want to remove a friend, so that I control who is in my list.
*Persona:* P1 · *Depends on:* US-61
- Given I remove a friend, then we no longer appear in each other's list.

## 8. Sprint goals and initial release plan **[PROPOSED]**

Assignment §7.1.2, item 5, and §7.2–§7.3. Proposed on 16 October 2026 and updated on 22 October 2026 with the smaller stories (Tiago, backlog owner), to confirm with the team at the next planning meeting. Order follows the MoSCoW rank in §2 and the dependencies in §7. The reasons are in §8.5.

### 8.1 Sprint goals

| Sprint | Dates | Goal |
|---|---|---|
| **B** · Build and operate the vertical slice | 2 Nov – 6 Dec 2026 | **A logged-in participant joins a published event and asks for help on its venue map; the control room sees the request at once with an AI suggestion, or without one when the AI is down, and routes it to a team whose member accepts and resolves it.** The event's teams and permissions are set by its EventAdmin. Deployed through the pipeline, with logs and metrics, and bus data read from the external source. Answers the Sprint B investor question: *does the product deliver observable user value, and can the team deploy, monitor and operate it?* |
| **C** · Evaluate, harden and present | 7 Dec 2026 – 3 Jan 2027 | **The MVP is complete: accounts, transport with event shuttles and the D2 warning, live announcements, community reports reviewed by the right team, and badges.** The AI triage is evaluated against the non-AI baseline, and both degraded modes (D1, D2) are demoed. Answers: *is this a credible product and a maintainable system?* |

### 8.2 Initial release plan

Every merge to `main` is built and tested (guide H4). Delivery is local: a *release* is a git tag (guide H7), built and run on a laptop for the review ([`local-delivery.md`](../Technical/local-delivery.md), guide H5).

| Release | When | What it contains | Assignment |
|---|---|---|---|
| **R0 · Walking skeleton** | Sprint A, before Sprint B starts on 2 Nov 2026 (#58, owner: Tomás) | One Reports service with synthetic help-request create/read in memory, diagnostic health, HTTP tests, Dockerfile, Compose and CI smoke on every PR (#166; [run instructions](../Technical/plans/local-walking-skeleton.md)). Keycloak, persistence/outbox and the full application are Sprint B. Not a user story: a task issue | §7.1.2 item 10 (Sprint A) |
| **R1 · Vertical slice** | Sprint B review (6 Dec 2026) | 27 stories: US-01, 02, 06, 11, 12, 14, 16, 17, 18, 19, 20, 21, 23, 24, 26 to 36, 40, 41. The help-request journey end to end (W2 + W3), with teams and permissions, AI triage, scenario D1 and the first external integration | §7.2.2 items 1–13 |
| **R2 · MVP** | Sprint C review (3 Jan 2027) | Every remaining Must (28 stories): US-03, 04, 05, 07, 08, 09, 10, 13, 15, 22, 25, 37, 38, 39, 42, 43, 44, 45, 47 to 56. Should US-58 and US-57 if there is time | §7.3.2 items 1–2, 8 |
| **Later** (product roadmap) | After the course | Could: US-46, US-59 to US-62. Won't: F19, F20 | §7.3.2 item 13 |

**Cut line for Sprint C.** If Sprint C runs short, drop in this order: US-57 and US-58; then US-52 and US-53; then US-22; then US-08, US-09, US-10 and US-45 (reasons in §8.5).

### 8.3 Stories on the board

Each story is a GitHub issue created from the [user-story form](../../.github/ISSUE_TEMPLATE/user-story.yml), titled `[<Type>] US-XX: <Title>` (README §1). Type rule in §7.1. Created on 22 October 2026 with the label `user story` (the walking skeleton: `task`) and the milestone of their sprint (`Sprint A`, `Sprint B`, `Sprint C`; Could stories have none). Each issue's *Dependencies* links the issues it needs. The old feature issues #2 to #16 (Sprint A feature discovery) stay open, but were archived from the board on 24 October 2026 (#138): each one is covered by the stories below or is out of scope (#6, #8, #10, #14). #11 (Problem and Opportunity Report) was closed as delivered.

**Board:** [GitHub Projects, *LABDSOF*](https://github.com/users/tomasoliveira21/projects/1) (project 1 of `tomasoliveira21`; private, open to the team). Every issue in the table below is on it, with the other open issues of the repository (auto-add workflow). How its fields are used, decided and applied on 24 October 2026 (Tiago, backlog owner, #138); the board is the source of truth for the values (they are not repeated in this table):

| Field | Values | Rule |
|---|---|---|
| *Status* | Backlog → Ready → In progress → In review → Done | *Ready* only when the story meets the [DoR](../Process/definition-of-ready-and-done.md#definition-of-ready-backlog-item) and none of its dependencies blocks it (§8.4); a dependency on an unblocked story that is not built yet only orders the work. *Done* follows the DoD; the board workflows move closed issues and merged PRs to *Done* |
| *Priority* | P0, P1, P2 | Must in the release of the next sprint → **P0** (now R1, Sprint B; the walking skeleton #58, R0, is also P0); every other Must → **P1**; Should and Could → **P2**. Re-applied at the start of each sprint |
| *Size* | XS, S, M, L, XL | From the number of acceptance criteria: 1 → XS, 2 → S, 3 → M, 4 → L, 5 or more → XL. One step up when the story depends on an open or external decision or system (E1, role model, D3, F3, K1, ADR-008, J1, §6.1, D7). One step up for US-21, US-23 and US-26 (first use of the map and of polling, §8.5) and US-46 (offline cache). An XL story is split before it is *Ready* |
| Sprint | Milestones *Sprint A*, *Sprint B*, *Sprint C* | The issue's milestone (this table's *Sprint* column); board views are grouped or filtered by *Milestone*. No *Iteration* field, so the sprint is kept in one place (decided on 24 October 2026 instead of the Iteration field of [`definition-of-ready-and-done.md`](../Process/definition-of-ready-and-done.md#in-the-tool)) |
| *Estimate*, *Start date*, *Target date* | — | Not used for now |

Result of the rules on 24 October 2026: **P0** 28, **P1** 28, **P2** 7; **XS** 16, **S** 22, **M** 16, **L** 9, **XL** 0. Only #58 is *Ready*: every story waits for at least one open blocker of §8.4 (the identity provider E1 alone blocks 60 of the 62 stories), so it stays in *Backlog* until that blocker is closed.

| Story | Issue | Type | Sprint |
|---|---|---|---|
| Walking skeleton (task) | #58 | Technicians | A |
| US-01 · Log in | #59 | Technicians | B |
| US-02 · Log out | #60 | Technicians | B |
| US-03 · Sign up | #86 | Front Office | C |
| US-04 · Edit name and password | #87 | Front Office | C |
| US-05 · Delete my account | #88 | Front Office | C |
| US-06 · Create an event and name its first EventAdmin | #61 | Back Office | B |
| US-07 · List events with basic details only | #89 | Back Office | C |
| US-08 · Name a new EventAdmin | #90 | Back Office | C |
| US-09 · Suspend and reactivate an event | #91 | Back Office | C |
| US-10 · Edit event details | #92 | Back Office | C |
| US-11 · Publish an event | #62 | Back Office | B |
| US-12 · Join an event by QR code or link | #63 | Front Office | B |
| US-13 · Close an event | #93 | Back Office | C |
| US-14 · Keep each event's data private | #64 | Technicians | B |
| US-15 · Make or remove an EventAdmin | #94 | Back Office | C |
| US-16 · Create, rename and delete teams | #65 | Back Office | B |
| US-17 · Set a team's permissions and help categories | #66 | Back Office | B |
| US-18 · Add and remove team members | #67 | Back Office | B |
| US-19 · Upload the venue map | #68 | Back Office | B |
| US-20 · Place points of interest | #69 | Back Office | B |
| US-21 · Open the venue map | #70 | Front Office | B |
| US-22 · Filter the map by type of place | #95 | Front Office | C |
| US-23 · Ask for help on the map | #71 | Front Office | B |
| US-24 · Follow my help request | #72 | Front Office | B |
| US-25 · Help request not sent | #97 | Front Office | C |
| US-26 · Live list of help requests | #73 | Back Office | B |
| US-27 · Help requests on the map | #74 | Back Office | B |
| US-28 · Set priority and status, with history | #75 | Back Office | B |
| US-29 · Route a request to a team | #76 | Back Office | B |
| US-30 · Close a request | #77 | Back Office | B |
| US-31 · Field team sees routed requests | #78 | Back Office | B |
| US-32 · Accept a request | #79 | Back Office | B |
| US-33 · Mark a request resolved | #80 | Back Office | B |
| US-34 · AI triage suggestion | #81 | Back Office | B |
| US-35 · Accept or change the AI suggestion | #82 | Back Office | B |
| US-36 · Requests without AI (scenario D1) | #83 | Back Office | B |
| US-37 · Add shuttles and temporary stops | #98 | Back Office | C |
| US-38 · Mark a shuttle delayed or cancelled | #99 | Back Office | C |
| US-39 · See the event's shuttles | #100 | Front Office | C |
| US-40 · Import the external transport source | #84 | Technicians | B |
| US-41 · See the next buses home | #85 | Front Office | B |
| US-42 · Out-of-date transport warning (D2) | #101 | Front Office | C |
| US-43 · Transport source down warning (D2) | #102 | Back Office | C |
| US-44 · Publish an announcement | #103 | Back Office | C |
| US-45 · Edit or remove an announcement | #104 | Back Office | C |
| US-46 · Weak-network mode (Could) | #117 | Front Office | — |
| US-47 · Report a problem on the map | #105 | Front Office | C |
| US-48 · Report rate limit | #106 | Front Office | C |
| US-49 · See confirmed reports on the map | #107 | Front Office | C |
| US-50 · Confirm or reject a report | #108 | Back Office | C |
| US-51 · Unconfirm a report | #109 | Back Office | C |
| US-52 · Send a report to be checked on site | #110 | Back Office | C |
| US-53 · Fix a report and mark it resolved | #111 | Back Office | C |
| US-54 · *Report history* badge | #112 | Front Office | C |
| US-55 · *Event reporter* badge | #113 | Front Office | C |
| US-56 · Hide or stop earning badges | #114 | Front Office | C |
| US-57 · *Event attendance* badge (Should) | #115 | Front Office | C |
| US-58 · Check in at the venue (Should) | #116 | Front Office | C |
| US-59 · Set entrance status (Could) | #118 | Back Office | — |
| US-60 · See entrance status (Could) | #119 | Front Office | — |
| US-61 · Send and accept friend requests (Could) | #120 | Front Office | — |
| US-62 · Remove a friend (Could) | #121 | Front Office | — |

### 8.4 What still blocks *Ready*

These dependencies were open on 22 October 2026; the struck-through ones were closed by 31 October 2026, with the source. A story that needs one stays in *Backlog*, not *Ready*, until it is closed. All are expected before Sprint B starts on 2 November.

| Open dependency | Owner | Blocks |
|---|---|---|
| ~~Walking skeleton (R0, #58), in Sprint A; needs the deployment platform (guide G1, #53)~~ Closed on 31 October 2026: local delivery (guide G1, [ADR-011](../Technical/adr/0011-deployment-and-infrastructure.md)); R0 merged in #167 and #58 closed | Tomás | ~~Every story (first to be built)~~ |
| ~~Identity provider (guide E1)~~ Closed on 22 October 2026: Keycloak ([ADR-006](../Technical/adr/0006-identity-and-access.md)) | Guerra | ~~US-01, US-03~~ |
| Role, team and permission model (§4): team confirmation, then the domain model (Tomás + Guerra). ~~Where permissions are checked (guide E2)~~ closed on 22 October 2026: in each service's use cases, against local copies ([ADR-006](../Technical/adr/0006-identity-and-access.md)). Replaces personas PE-5 and PE-6 | Team; Tomás + Guerra | US-06, US-14, US-15, US-16 |
| Venue map library (guide F3, ADR-007, #44) | Tiago | US-19 |
| ~~AI provider and model (guide K1); AI-1, AI-2~~ closed: Ollama with `qwen3:4b` ([ADR-005](../Technical/adr/0005-ai-triage-provider-and-evaluation.md), accepted on 31 October 2026). Still open: AI-3, the priority scale ([API outline](../Technical/api-outline.md) O7) | Tomás, AI lead | US-34 |
| ~~Resilience values (guide J1, #43)~~ Closed on 24 October 2026 (guide J1, #43, PR #52; [`architecture.md` §9.1](../Technical/architecture.md#91-resilience-patterns-and-values-j1)) | Tiago | ~~US-42, US-43~~ |
| Report types and expiry times (§6.1) | Team | US-47 |
| ~~Account deletion (guide D7)~~ Closed on 27 October 2026 (guide D7, #135; [ADR-006](../Technical/adr/0006-identity-and-access.md#changes-for-the-api-outline-and-d7)) | Bruno | ~~US-05~~ |

Stories that depend on a blocked story wait for it too (see each story's *Depends on*).

### 8.5 Why this plan

#### Why this Sprint B goal

Assignment §7.2.1 asks Sprint B for *"an operational end-to-end version of the product's most important user journey"*. That journey is the help request:

- the report's scope mitigation puts help requests first (*"prioritise help requests, the map and transport information in the MVP"*, report §5), and F6–F8 are the first features after the foundations (rank 6–8, §2);
- W2 + W3 is journey 1 in [`user-workflows.md`](user-workflows.md), and it is already drawn end to end in BPMN;
- it holds the riskiest parts of the product: the AI workflow (§5.6), the asynchronous workflow (§6.3), isolation between events (report §5) and, since 22 October, permissions set per team. Building them in Sprint B leaves Sprint C for evaluating them.

The goal also covers every Sprint B deliverable of §7.2.2:

| §7.2.2 deliverable | Covered by |
|---|---|
| 1 · Functional client application | US-12, US-21, US-23 (phone pages); US-06, US-11, US-16 to US-18 (laptop pages) |
| 2 · Authentication and authorisation | US-01, US-02, US-14, US-16 to US-18 |
| 3 · Persistent data storage | Every story (PostgreSQL, one database per service, guide D1–D2) |
| 4 · Documented API contracts | OpenAPI written first for each story (contract-first, guide C7) |
| 5 · At least two backend components | Auth & Users, Events, Reports and Transports (guide A2) |
| 6 · One asynchronous workflow | Help request → AI triage → backoffice (US-23, US-34; §3, row §6.3) |
| 7 · Initial external-system integration | US-40, US-41 |
| 8 · First version of the AI capability | US-34, US-35 |
| 9 · Non-AI baseline or fallback | US-36 |
| 10 · Automated tests | DoD, Level 2, on every story |
| 11 · CI/CD pipeline | R0, walking skeleton |
| 12 · Structured logs, health checks, initial metrics | R0 (health check); DoD Level 2 (logs with correlation ID); US-36 (fallback-rate metric) |
| 13 · Demonstration of a failure condition | Scenario D1 (US-36) |
| 14 · Updated backlog and architecture documentation | This section, updated at each planning |

#### Why these stories in Sprint B, and whether it is realistic

Every Sprint B story is a step of the journey or something it needs first:

- **Account and event:** US-01 and US-02 → US-06 → US-11 → US-12.
- **Teams and permissions:** US-16 → US-17 → US-18. Without them nobody has `HelpRequests.Triage` or `HelpRequests.Respond`, so no one could see or act on a request.
- **Map:** US-19 → US-20 → US-21, where the participant marks their location.
- **Help request:** US-23, US-24 → control room US-26 to US-30 → field team US-31 to US-33.
- **AI and its fallback:** US-34 to US-36.
- **External integration:** US-40, US-41 (deliverable 7).
- **Isolation:** US-14, a top technical risk and SC8.

Nothing in Sprint B is there for convenience, so Sprint B has no cut line.

**Capacity:** 27 stories in 5 weeks (the walking skeleton is done in Sprint A), for 4 people: about 7 stories per person, under 1.5 per person per week. After the split each story is one action and one PR of one to three days. The largest are US-17 (permission checks), US-21 and US-23 (first use of the map), US-26 (first use of polling) and US-34 (AI).

**Risks and what happens if they occur:**

| Risk | Plan |
|---|---|
| A blocker in §8.4 is not closed by 2 November | The stories that need it stay in *Backlog*; the team starts with the stories it does not block (US-02, US-11, US-12, US-28 to US-30) |
| The team does not confirm the role model (§4) | US-16 to US-18 are replaced by teams created by a seed script with the pilot set-up (§4.4); the other stories do not change, because they check permissions, not team names |

#### Why US-40 and US-41 are in Sprint B, and US-42 and US-43 in Sprint C

Sprint B must already have an *initial external-system integration* (§7.2.2, item 7), so the import (US-40) and its screen (US-41) are in Sprint B. Sprint B needs only **one** failure condition (§7.2.2, item 13), and D1 (US-36) covers it. The *resilience and degraded-mode demonstration* is a Sprint C deliverable (§7.3.2, item 8), where D2 (US-42, US-43) is the second scenario. US-42 also needs the event shuttles (US-39), so that they stay on screen when the source is down.

#### Why sign-up (US-03 to US-05) is in Sprint C

- Mocked authentication is allowed for the MVP (assignment §6.6, footnote 1), so seeded test accounts are enough for the help-request journey; log in and log out (US-01, US-02) are in Sprint B.
- Sign-up is not a step of that journey.
- The terms and consent text follow the [Security & Privacy assessment](../Technical/security-and-privacy-assessment.md) §8 (guide E5, Guerra; written on 25 October 2026, to review with the team).
- US-03 to US-05 all handle personal data, so they are built and tested together.

#### Why the FarolAdmin's other stories (US-07 to US-09) are in Sprint C

Only creating the event (US-06) is on the journey. Listing events, naming a new EventAdmin and suspending an event are the FarolAdmin's day-to-day and exception work. The FarolAdmin is an internal user (P4), who can do them by hand while they are not built.

#### Why this cut order in Sprint C

Sprint C has 28 Must stories in four weeks, including Christmas, and it also holds the evaluation, accessibility, security, performance and presentation deliverables (§7.3.2). If the stories do not fit, they are dropped in this order:

1. **US-57 and US-58**: Should (MoSCoW). The engagement mechanism (§5.5) is already complete with the two Must badge tracks (US-54, US-55).
2. **US-52 and US-53**: reports can still be confirmed and rejected from the control room (US-50).
3. **US-22**: a convenience. The map already shows every point with its icon (US-21).
4. **US-08, US-09, US-10 and US-45**: the FarolAdmin can do US-08 and US-09 by hand; the name and dates of an event are set when it is created (US-06); a wrong announcement can be corrected by publishing a new one (US-44).

**Never cut:**
- **US-03, US-04 and US-05**: personal data (consent and the right to erasure), checked in the security review (§7.3.2, item 6).
- **US-25**: a participant must never think a request was sent when it was not.
- **US-37 to US-39, US-42 and US-43**: scenario D2 (§7.3.2, item 8).
- **US-44**: the answer to root cause 2, static communication (§1).
- **US-47 to US-51 and US-54 to US-56**: the engagement mechanism, which the assignment requires (§5.5), with its opt-out (§6.4).

#### Why the walking skeleton comes first

It is a Sprint A deliverable (§7.1.2, item 10), so it stays in Sprint A (#58) and is done before Sprint B starts. DoD Level 2 asks for every story to be *deployed through the pipeline*, so no story can be *Done* before the skeleton exists. One week is enough for one endpoint, one test, a Dockerfile and the pipeline, because the CI/CD choices are decided (guide H1–H5). It needs the deployment platform (guide G1, #53) first. **Owner:** Tomás ([professor meeting of 22 October 2026](../Meetings/2026-10-22-professor-meeting.md)).

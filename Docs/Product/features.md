# Main Features and Initial Backlog

> LABDSOF 2026/27 · Sprint A (Sprint 1) · Assignment §7.1.2 (deliverables 4 *MVP definition* and 5 *Product Backlog*)
> **Product name:** **Farol** (decided 9 October 2026).
> **Status:** feature list and MoSCoW revised on **9 October 2026** (owner of the topic: Bruno Lopes) on top of the team meeting of 8 October ([minutes](../Meetings/2026-10-08-sprint-1-gap-review.md)). Decided: login is mandatory, response teams, degraded-mode scenarios, external integration, badge tiers, attendance check-in. Still open: the AI workflow details (§5.2), report types and expiry (§6.1). **[PROPOSED] 10 October 2026** (personas owner): a platform admin (persona P4) creates events and names their Owner (F3, F4, US-25), to confirm with the team. User stories (§7) are **[PROPOSED]**, to validate with the team.
> **Sources:** [`problem-opportunity-report-queima.md`](../Deliverables/problem-opportunity-report-queima.md) (*MVP definition*, §5 risks) · [`market-and-competitor-analysis-product-specific.md`](../Deliverables/market-and-competitor-analysis-product-specific.md) (§1, §2) · assignment PDF (§5.5, §5.6, §6).
> Legend: see [`product-vision.md`](product-vision.md#legend).

## 1. How the product solves the problem

| Root cause (report §2) | What the product does | Features |
|---|---|---|
| 1. No single source of operational information | One platform per event where the team publishes everything participants need | F5, F9, F10 |
| 2. Communication is static | The team updates information during the night; every item shows its last-update time. Participants report what they see, and the team confirms it | F10, F11, F12, F13 |
| 3. No shared spatial reference of the venue | The venue map, uploaded by the team, is the common reference for participants and operators | F5, F6, F12 |
| 4. Incident coordination depends on radio and phone *(hypothesis)* | Help requests arrive structured, with location, category and status, and are triaged and routed to a response team in the backoffice | F6, F7, F8 |
| 5. Slow processes at the gate | Out of scope as a process; at most, entrance status information | F17 |

## 2. Feature list and priority

MoSCoW revised on 9 October 2026. The MVP still follows the report's scope mitigation: *"prioritise help requests, the map and transport information in the MVP"* (report §5, execution risks). *Rank* is the build order within the Must-haves.

| ID | Feature | Component (annex §1) | MoSCoW | Rank | Persona | Notes |
|---|---|---|---|---|---|---|
| F1 | Login and logout | — | Must | 1 | P1, P2, P3 | **Login is mandatory for every user**, participants included. Mocked authentication is allowed for the MVP (assignment §6.6, footnote 1) |
| F2 | Personal account management | — | Must | 2 | P1, P2, P3 | Profile, password, consent, badge visibility, delete account (deletes badges and personal data) |
| F3 | Event management | C1 | Must | 3 | P4, P3 | Create, edit, publish and close events. Every other feature is scoped by event; isolation between events is a top technical risk (report §5). **[PROPOSED] 10 Oct:** only the platform admin (P4) creates an event, for a verified organisation, and names its first Owner; the Owner edits, publishes and closes it |
| F4 | Event role and response-team management | C1 | Must | 4 | P3, P4 | Invite members, assign, change and revoke roles (`Owner`, `Admin`, `Operator`, `Responder` **[PROPOSED]**); put operators and responders in response teams (§4). **[PROPOSED] 10 Oct:** one platform-wide role, `PlatformAdmin`, outside every event: transfers the Owner role and suspends events, but never sees an event's help requests, reports or participants |
| F5 | Venue map with points of interest | C3 | Must | 5 | P1, P3 | Map uploaded as an image or PDF; points placed on it (entrances, stages, WC, first aid, transport stops) |
| F6 | Help request | C6 | Must | 6 | P1 | Category and location marked on the map; location shared only with the request |
| F7 | Incident backoffice | C7 | Must | 7 | P2, P5 | Receive, prioritise, route to a response team and close help requests; live list and map; full status history. **[PROPOSED] 12 Oct:** responders get what is dispatched to their team on the phone and update its status (§4.1) |
| F8 | AI help-request triage, with non-AI fallback | C9 | Must (assignment §5.6) | 8 | P2 | Suggests category, priority and response team; the operator decides. Kept separate from F7 on purpose (see note below). See §5 |
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

**Out of scope** (report *Fora de âmbito*; annex §1): physical capacity of entrances, heavy integrations, advanced crowd prediction, full automation, own ticketing.

**Removed on 9 October 2026:** the former *"participant web app opened by link or QR, without an account"*. Login is now mandatory, so the participant app is simply the client application (assignment §6.1), reached after F1. The QR code still opens the event, after login.

**Why F7 and F8 stay separate (proposed answer to "should they be merged?"):** on the slide they are presented together as *incident handling*. In the backlog they stay apart because (1) F7 must work on its own: it **is** the fallback when the AI is down (assignment §5.6.2, scenario D1); (2) F8 has its own evaluation, acceptance criteria and Responsible AI assessment (assignment §5.6.1, Sprint 1 deliverable 6); (3) they can be built and demoed in different sprints. If the team prefers one ID, rename F8 to *F7.b*. Nothing else changes.

## 3. Assignment requirements covered by the features

| Assignment requirement | How the product meets it | Status |
|---|---|---|
| §5.5 Ethical engagement design | F12–F15: community reports, team confirmation and badges | Decided (§6) |
| §5.6 At least one AI-assisted workflow, with evaluation and baseline or fallback | F8: help-request triage | Decided; details open (§5.2) |
| §6.1 Client application | Participant web app (PWA) and backoffice, both behind login | Decided |
| §6.3 At least one asynchronous workflow | Help request → AI triage → notification of the event team (see [`architecture.md`](../Technical/architecture.md)) | **[PROPOSED]** |
| §6.5 Integration with at least one external system or realistic simulator | **Porto Digital urban platform**, FIWARE NGSI v2 bus API (F9; accepted by the professor, [ADR-008](../Technical/adr/0008-transport-data-source.md)) | Decided |
| §6.6 Authentication and authorisation | F1 login for every user; F2 account and consent; F4 per-event roles; audit records for help requests (domain model I5) | Decided; mocked authentication allowed for the MVP |
| §6.8 At least one degraded-mode scenario | **D1** AI unavailable and **D2** transport source unavailable (§3.1) | Decided |

### 3.1 Degraded-mode scenarios (decided, 9 October 2026)

Two scenarios, no more. Both are demoed by breaking one dependency while the rest of the product keeps working. Assignment §6.8: *"fail safely and communicate limitations clearly to users"*.

#### D1 · AI triage unavailable

| | |
|---|---|
| **Dependency** | AI model provider used by the triage worker (F8) |
| **Detected when** | No valid suggestion within **30 s** of submission (SC3), because of a timeout (10 s per attempt, 2 attempts), a provider error, an invalid or incomplete answer (unknown category, missing field), or the provider being marked down after repeated failures (circuit breaker: at least half of the last 10 calls failed or were slow, minimum 4 calls). Values: [`architecture.md` §9.1](../Technical/architecture.md#91-resilience-patterns-and-values-j1) |
| **What the operator sees** | The request appears in the backoffice **at once** (it never waits for the AI), with the participant's category, the label **"Not triaged"**, in order of arrival. While the provider is down (circuit breaker open), a banner says *"AI triage unavailable: requests are shown by order of arrival"* |
| **What the operator does** | Sets the priority and response team by hand. Nothing else changes in F7 |
| **What the participant sees** | Nothing different. Their request is sent and tracked as usual |
| **When the AI comes back** | The banner disappears after the first successful call. Requests from the last 15 min that the operator has not touched are sent to triage once more and may still receive a suggestion; a suggestion **never overwrites** an operator's decision and **never lowers** a priority (domain model I4) |
| **Recorded** | Every fallback is logged with the reason (timeout, error, invalid output) and the correlation id, and counted as a metric (`farol.triage.fallback`); the circuit breaker state is on the dashboard |
| **How it is demoed** | The triage worker points at **WireMock** (guide J4), which normally passes requests to the real AI provider. The presenter makes it drop the connection (or answer late, or return invalid output) and a participant submits help requests; the services carry no fault-injection code. Step-by-step script: [`architecture.md` §9.3](../Technical/architecture.md#93-demo-script-d1-and-d2-about-5-minutes) |
| **Test** | SC5: 100 % of requests are shown to the operator with the AI off. Reports integration test on every PR, with WireMock in place of the provider (delay over the timeout, 503, dropped connection, invalid body); also under load in Sprint C (scenario L5) |

#### D2 · Transport source unavailable

| | |
|---|---|
| **Dependency** | Porto Digital urban platform (FIWARE NGSI v2 bus API, [ADR-008](../Technical/adr/0008-transport-data-source.md)) |
| **Detected when** | The import fails (no answer, HTTP error), the feed is invalid or incomplete (fails validation on import), or the data is older than its freshness limit: **5 min** (decided 22 October 2026), so late observations are dropped on import. Bus positions normally arrive about 1 min old, and a bus covers about 2 km in 5 min, so an older position misleads. The 24 h proposed earlier was for a static GTFS timetable, dropped with the professor's answer |
| **What the participant sees** | The **last valid data**, with *"Data from HH:MM"* and a warning *"Live transport information unavailable"*, plus a link to the operator's website. **Event shuttles and temporary stops entered by the team are not affected**: they are internal data |
| **What the team sees** | A warning in the backoffice that the transport source is down, with the time of the last successful import |
| **Invalid data** | Never replaces valid data: the import is rejected as a whole and the previous version stays in use |
| **Recovery** | Import every 30 s, retried with backoff (3 attempts, 1 s and 2 s apart; after repeated failures the circuit breaker waits 60 s before trying again, so a public service we do not control is not hammered); the warning disappears after the first valid import |
| **How it is demoed** | Transports points at **WireMock**, the team's simulator of the Porto Digital API (guide J4), which normally passes requests to the real API. The presenter makes it return invalid data, then drop the connection, and the participant opens the transport screen. Script: [`architecture.md` §9.3](../Technical/architecture.md#93-demo-script-d1-and-d2-about-5-minutes) |
| **Test** | US-15: Transports integration test on every PR, with WireMock replaying recorded Porto Digital responses, then a 503, a timeout and an invalid payload, and a fixed clock past the freshness limit: the last valid data, its time and the stale flag are returned, shuttles still shown, invalid data never stored |

## 4. Response teams (decided, 9 October 2026)

Every event has these four response teams. The admin puts operators and responders in them (F4, §4.1). Help requests are routed to a team (F7, F8); community reports are confirmed by the team responsible for their type (F13).

| Response team | Handles help requests about | Confirms reports about |
|---|---|---|
| **Medical** | Health: injury, feeling unwell, intoxication, panic attack | — |
| **Security** | Safety: fights, aggression, harassment, theft, crushing | Queues at entrances, crowded areas |
| **PCR** (risk-prevention committee) | Welfare: lost person or friend, someone who needs support, alcohol and drug risk | Water points or bars out of water |
| **Logistics** | — (does not receive help requests) | WC problems, hazards (glass, spills, dark areas, broken fences), transport problems, wrong information |

The event **Admin** can confirm any report and handle any request. Each help category maps to a default team; the operator or the AI suggestion can change it. The help categories follow the domain model (`Health`, `Safety`, `LostPerson`, `Theft`, `Other`) and still need to be checked against the interview with the security volunteer (DM-4).

### 4.1 Who receives what, and who acts **[PROPOSED] 12 October 2026**

Teacher feedback on the presentation: it was not clear who receives the reports and how they are handled. Each response team has two kinds of members, one that **decides** and one that **acts** (personas P2 and P5):

| Role | Where | What they receive | What they do |
|---|---|---|---|
| **Operator** (P2) | Control room, laptop | **Every new help request** of the event (one shared queue) and **the reports of the types their team is responsible for** (§6.1) | Decides. Help request: sets priority and team, then dispatches it or handles it from the control room. Report: confirms or rejects it, asks a responder to check it on site, or sends one to fix it |
| **Responder** (P5) | In the field, phone | **Only what an operator dispatches** to their team | Acts: accepts ("on my way"), goes to the point on the map, marks it resolved. For a report: answers "true / not true", or fixes the problem |
| **Admin** | Anywhere | Everything in the event | Can do what an operator does, for any team |

- **Help requests** always pass through the control room first, because they need a decision: priority, team, whether someone must go.
- **Reports** go straight to the operators of the responsible team, because the report type already says who is responsible.

Both journeys as BPMN: [`user-workflows.md`, *Complete journeys*](user-workflows.md#complete-journeys-bpmn).

## 5. AI-assisted workflow: help-request triage

### 5.1 What is decided

**Decision (8 October 2026): help-request triage.** It is part of the core journey (help requests are the top MVP priority), its fallback already exists in the product (F7), and it is easy to evaluate with a synthetic dataset. The participant Q&A assistant was rejected: a wrong transport answer at night is a higher safety risk, and a chat interface would need justification (assignment §5.6).

| | Help-request triage |
|---|---|
| What the AI does | Reads the participant's category and optional text, then suggests a **category, a priority and the response team** (§4). The operator accepts or changes the suggestion |
| User need | Requests arrive without category or priority (root cause 4); assumption A4 |
| Non-AI fallback | Scenario D1 (§3.1): participant's category, order of arrival, "Not triaged" |
| Human control | The AI only suggests. It never lowers a priority, and it never overwrites an operator's decision (domain model I4) |
| Speed target | Suggestion within **30 s** of submission (SC3) |
| Market | Raven Controls does this for professional control rooms (annex C9); nobody does it for academic events |

### 5.2 Still to define

This is the list the team works through before the AI work starts. Owner: AI lead (see [`team-and-process.md`](../Process/team-and-process.md) §2).

| # | Item | Open question | Needed for |
|---|---|---|---|
| AI-1 | **Input** | Exactly which fields go to the model: category, free text, map zone, time of night? Is the location sent, or only the zone? | Privacy, prompt design |
| AI-2 | **Output format** | A fixed JSON schema: category (fixed list), priority (`Low`/`Medium`/`High`/`Critical`), response team (§4), a short reason for the operator. What happens with an answer outside the schema (→ D1) | Validation, UI |
| AI-3 | **Priority scale** | What each level means, with examples, so people and the AI apply the same rule. What is the "default priority" of each category, so that "never lowers a priority" is measurable | Evaluation, I4 |
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

| Report type | Example | Confirmed by (§4) | Expires after |
|---|---|---|---|
| Queue at an entrance | "Gate 2: long queue (> 15 min)" · short / medium / long | Security | 30 min |
| Crowded area | "Very crowded in front of the main stage" | Security | 20 min |
| WC problem | Out of service, no water, ...| Logistics | 60 min |
| Water point or bar out of water | "No water at bar 3" | PCR | 60 min |
| Hazard | Broken glass, spill, dark area, broken fence , ...| Logistics | Until the team closes it |
| Transport problem | "Shuttle stop moved", "shuttle full", "shuttle not passing" | Logistics | 30 min |
| Wrong information | A map point or announcement that is no longer true | Logistics | Until the team closes it |

### 6.2 How a report behaves

1. A participant places a report on the map. It goes straight to the **operators of the team responsible for its type** (§6.1, §4.1), who see it **in the backoffice at once**. Its status is *Pending*.
2. **Only an operator of the responsible team (or an Admin) confirms or rejects it.** If it cannot be checked from the control room, the operator asks a responder of that team to check it on site **[PROPOSED]**. Other participants see it only once it is confirmed. They can tap *"still there"* or *"not there anymore"*: this helps the team decide, but it does not confirm the report and does not count for badges. **[PROPOSED]**
3. A confirmed report counts for the reporter's badges and appears on everyone's map with its time.
4. The team can **unconfirm** a report (e.g. confirmed by mistake, or found to be false). It disappears from the map, **no longer counts** for badges, and the reporter's badge levels are recalculated, so a badge can go down a tier.
5. A report **expires** after the time set for its type (§6.1). An expired confirmed report still counts for badges; it is only removed from the map.
6. A report never changes official information (map, announcements, transport) automatically. The team does that.
7. **[PROPOSED] 12 Oct:** if the problem needs action (a broken WC, broken glass), the operator sends a responder of the team, who fixes it and marks the report *Resolved*: it leaves the map and still counts for badges. A report nobody confirms is removed when it expires and earns nothing.

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

## 7. Initial backlog: user stories **[PROPOSED]**

Rewritten on 9 October 2026 to match the feature list above. **To validate with the team** before they go to the GitHub Projects board (one story = one issue = one PR). Acceptance criteria use *Given / When / Then*. Estimates and sprint allocation are not included. Won't-have features (F19, F20) have no stories.

### F1 · Login and logout

**US-01** · As a **user** (participant or team member), I want to log in and log out, so that only I can use my account.
- Given I have an account, when I log in with valid credentials, then I see the events I can access.
- Given I enter wrong credentials, then I see an error that does not say which field was wrong.
- Given I am logged in, when I log out, then my session ends and protected pages ask me to log in again.
- Given I am not logged in, when I scan an event's QR code, then I am asked to log in and then taken to that event.

### F2 · Personal account management

**US-02** · As a **user**, I want to create and manage my account, so that I control my personal data.
- Given I am a new participant, when I sign up and accept the terms, then my account is created.
- Given I am logged in, when I edit my name or password, then the change is saved.
- Given I delete my account, then my personal data and badges are deleted, and my past reports stay only as anonymous statistics.

### F3 · Event management

**US-25** **[PROPOSED]** · As a **platform admin**, I want to create an event for a verified organisation and name its first Owner, so that only real organisers run events on Farol.
- Given an organisation asks for a new event, when I confirm the requester represents it and create the event with a name and dates, then it is saved as a draft that participants cannot see, and the named user becomes its Owner.
- Given a user who is not a platform admin, when they try to create an event, then the action is refused.
- Given the Owner of an event leaves the organisation, when the organisation asks, then I transfer the Owner role, and the change is recorded with my name and the time.
- Given an event is abandoned or misused, when I suspend it, then participants can no longer open it.
- Given I am a platform admin, when I open an event, then I see only its name, dates, organisation, owners and status, never its help requests, reports or participants.

**US-03** · As an **event owner**, I want to edit, publish and close my event, so that participants can find it only while it is relevant.
- Given the platform admin created my event (US-25), then I see it as a draft that participants cannot see, and I can edit it.
- Given I publish the event, then participants can open it and its QR code and link are generated.
- Given I close the event, then participants can no longer send help requests or reports to it.

**US-04** · As a **team member of event X**, I want to be sure no other event's team can see my event's data.
- Given a user is a member of event X only, when they request any data from event Y, then the request is refused and the attempt is logged.

### F4 · Event role and response-team management

**US-05** · As an **event owner or admin**, I want to invite people, give them a role and put them in a response team, so that each person can only do what their job requires.
- Given I invite a user as *Operator* in the *Medical* team, then they see event X's help requests but cannot change its configuration.
- Given I invite a user as *Responder* in the *Medical* team, then they see only the requests and reports dispatched to that team. **[PROPOSED]**
- Given I change or revoke a member's role, then the change applies at their next action and is recorded with my name and the time.
- Given I am an *Operator*, when I try to change the event's roles, then the action is refused.

### F5 · Venue map

**US-06** · As an **event admin**, I want to upload the venue map as an image or PDF and place points of interest on it, so that participants and operators share the same reference.
- Given I upload a valid image or PDF, when I save, then participants see the new map version.
- Given I place a point of type *first aid*, then participants see it with its icon and name.

**US-07** · As a **participant**, I want to find a type of place on the map (WC, first aid, entrance, transport stop), so that I know where to go.
- Given the map has first-aid points, when I filter by *first aid*, then only those points are highlighted.

### F6 · Help request

**US-08** · As a **participant**, I want to ask for help by choosing a category and marking where I am on the map, so that the team can reach me.
- Given I choose a category and mark a location, when I submit, then I see a confirmation and the status of my request.
- Given I have no network, when I submit, then the app tells me the request was not sent and shows the nearest first-aid point.

### F7 · Incident backoffice

**US-09** · As an **incident operator**, I want to see new help requests as they arrive, on a list and on the map, so that I can react quickly.
- Given a request is submitted for my event, then it appears in my list within 5 s without reloading the page (SC2).
- Given a request has a location, then it is shown at that point on the venue map.

**US-10** · As an **incident operator**, I want to set the priority, route a request to a response team and close it, so that the right people deal with it.
- Given I change the status, priority or team of a request, then the change is recorded with my name and the time.
- Given I route a request to the *Medical* team, then its responders get an alert on their phone with the point on the map.

**US-26** **[PROPOSED]** · As a **responder**, I want to get the requests dispatched to my team on my phone and update their status, so that the control room and the participant know help is on the way.
- Given a request is dispatched to my team, then I get an alert with its category and its point on the venue map.
- Given I accept it, then its status becomes *On the way* and the participant sees "Help is on the way".
- Given another responder of my team already accepted it, then I see that and cannot accept it too.
- Given I mark it resolved, then the participant and the control room see it closed.

### F8 · AI help-request triage

**US-11** · As an **incident operator**, I want each new help request to arrive with a suggested category, priority and response team, so that I can route it faster.
- Given a help request is submitted and the AI is available, then a suggestion is shown within 30 s, marked as a suggestion.
- Given I accept the suggestion, then its values become the request's values and the change is recorded as mine.
- Given I already set a priority, when a suggestion arrives, then my priority is not changed.

**US-12** · As an **incident operator**, I want requests to keep arriving when the AI is down, so that no one is left without help (scenario D1).
- Given the AI does not answer within 30 s, or answers with invalid data, then the request is shown with the participant's category, marked "Not triaged", in order of arrival.
- Given the AI provider is down, then the backoffice shows a banner saying triage is unavailable.

### F9 · Transport information and external integration

**US-13** · As an **event admin**, I want to add the event's shuttles and temporary stops with their schedule, so that participants know how to get home.
- Given I mark a shuttle as delayed, then participants see the change with the time it was made.

**US-14** · As a **participant**, I want to see the next transport options home from the venue, including regular STCP lines, so that I don't walk home or drive.
- Given the STCP feed was imported, then I see the next departures from the stops near the venue, with the time of the data.

**US-15** · As a **participant**, I want to know when the transport information may be out of date, so that I don't rely on it blindly (scenario D2).
- Given the transport source is unavailable or sends invalid data, then I see the last valid information with "Data from HH:MM" and a warning, and event shuttles are still shown.
- Given the source comes back, then the warning disappears after the next valid import.

### F10 · Announcements

**US-16** · As an **event admin**, I want to publish an announcement (e.g. "Gate 3 closed, use Gate 1"), so that participants find out immediately.
- Given I publish an announcement, then participants with the app open see it within 10 s without reloading, with its publication time (SC6).

### F11 · Weak-network mode (Could)

**US-17** · As a **participant**, I want the map and the last information to work when the network drops, so that I'm not left without information.
- Given I opened the event once with network, when I lose connection, then I still see the map and information with "Data from HH:MM".

### F12 · Community reports

**US-18** · As a **participant**, I want to report a problem on the map (e.g. a long queue at a gate), so that other people and the team know about it.
- Given I choose a report type and a point on the map, when I submit, then the responsible team sees it in the backoffice straight away.
- Given my report is confirmed, then it appears on other participants' maps with its time, and disappears when it expires.
- Given I have sent too many reports in a short time, then new reports are refused with a message.

### F13 · Report confirmation

**US-19** · As an **operator of the responsible team**, I want to confirm or unconfirm a report, so that only true information is shown and rewarded.
- Given a report of a type my team is responsible for, when I confirm it, then it appears on participants' maps and counts for the reporter's badges.
- Given a confirmed report, when I unconfirm it, then it disappears from the map, stops counting, and the reporter's badge levels are recalculated.
- Given a report of a type my team is not responsible for, then I cannot confirm it (an Admin can).
- Given I cannot check a report from the control room, when I ask a responder of my team to check it, then they get it on their phone and their answer comes back to me. **[PROPOSED]**
- Given a confirmed report needs action, when I send it to a responder and they mark it resolved, then it leaves the map and still counts for badges. **[PROPOSED]**

### F14 · Badges for confirmed reports

**US-20** · As a **participant**, I want to earn badges for my confirmed reports, overall and in each event, so that my contribution is recognised.
- Given my confirmed-report count reaches 1, 3, 10, 50, 100 or 500, then my *Report history* badge moves to Wood, Bronze, Silver, Gold, Platinum or Diamond.
- Given my confirmed reports **in event X** reach a tier threshold, then my *Event reporter* badge for event X moves to that tier.
- Given I hide or turn off my badges in my account, then they are not shown and no new ones are earned.

### F15 · Badges for event attendance

**US-21** · As a **participant**, I want to earn a badge for the events I attend, so that coming back is recognised.
- Given I checked in to an event (F16, US-22), then the event counts as attended (once per event).
- Given my attended-event count reaches a tier threshold, then my *Event attendance* badge moves to that tier.

### F16 · Event check-in

**US-22** · As a **participant**, I want to check in at the venue by scanning the event's QR code, so that my attendance is recorded for my badges.
- Given I am logged in and the event is running, when I scan the venue QR code, then I see "Checked in" and the event counts as attended.
- Given I already checked in to this event, when I scan again, then nothing changes (one check-in per event).
- Given the event has not started or has ended, when I scan the QR code, then the event opens but no check-in is recorded.

### F17 · Entrance status (Could)

**US-23** · As a **participant**, I want to see the status of each entrance, so that I choose a gate with a shorter queue.
- Given the team marks Gate 2 as "long queue", then I see it on the map with the time of the update.

### F18 · Friends (Could)

**US-24** · As a **participant**, I want to add friends in the app, so that I can find people I know who use it.
- Given I send a friend request and it is accepted, then each of us appears in the other's friend list.
- Given I remove a friend, then we no longer appear in each other's list.

> ⚠️ **GAP: backlog not on the board.** Decided: the stories go to **GitHub Projects** as issues (one story = one issue = one PR), with sprint goals for Sprints B and C and the initial release plan (§7.1.2, item 5). To do once the stories above are validated.

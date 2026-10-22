# Main Features and Initial Backlog

> LABDSOF 2026/27 · Sprint A (Sprint 1) · Assignment §7.1.2 (deliverables 4 *MVP definition* and 5 *Product Backlog*)
> **Product name:** **Farol** (decided 9 October 2026).
> **Status:** feature list and MoSCoW revised on **9 October 2026** (owner of the topic: Bruno Lopes) on top of the team meeting of 8 October ([minutes](../Meetings/2026-10-08-sprint-1-gap-review.md)). Decided: login is mandatory, response teams, degraded-mode scenarios, external integration, badge tiers, attendance check-in. Still open: the AI workflow details (§5.2), report types and expiry (§6.1). **[PROPOSED] 10 October 2026** (personas owner): a platform admin (persona P4) creates events and names their Owner (F3, F4, US-25), to confirm with the team. **16 October 2026:** user stories (§7) checked against the DoR (owner: Tiago Teixeira); issues not created yet; sprint goals and initial release plan (§8) **[PROPOSED]**, to confirm with the team.
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
| F9 | Transport information with external integration | C5 | Must (assignment §6.5) | 9 | P1, P3 | **STCP GTFS feed** for regular lines (being validated; weather API as plan B, §3) plus event shuttles and temporary stops entered by the team |
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
| §6.5 Integration with at least one external system or realistic simulator | **STCP GTFS feed** (F9). **Plan B:** a weather API (e.g. IPMA open data) for the event nights, only if the GTFS feed is not usable | Decided; GTFS being validated |
| §6.6 Authentication and authorisation | F1 login for every user; F2 account and consent; F4 per-event roles; audit records for help requests (domain model I5) | Decided; mocked authentication allowed for the MVP |
| §6.8 At least one degraded-mode scenario | **D1** AI unavailable and **D2** transport source unavailable (§3.1) | Decided |

### 3.1 Degraded-mode scenarios (decided, 9 October 2026)

Two scenarios, no more. Both are demoed by switching off one dependency while the rest of the product keeps working. Assignment §6.8: *"fail safely and communicate limitations clearly to users"*.

#### D1 · AI triage unavailable

| | |
|---|---|
| **Dependency** | AI model provider used by the triage worker (F8) |
| **Detected when** | No valid suggestion within **30 s** of submission (SC3), because of a timeout, a provider error, an invalid or incomplete answer (unknown category, missing field), or the provider being marked down after repeated failures (circuit breaker) |
| **What the operator sees** | The request appears in the backoffice **at once** (it never waits for the AI), with the participant's category, the label **"Not triaged"**, in order of arrival. While the provider is down, a banner says *"AI triage unavailable: requests are shown by order of arrival"* |
| **What the operator does** | Sets the priority and response team by hand. Nothing else changes in F7 |
| **What the participant sees** | Nothing different. Their request is sent and tracked as usual |
| **When the AI comes back** | Requests the operator has not touched may still receive a suggestion; a suggestion **never overwrites** an operator's decision and **never lowers** a priority (domain model I4) |
| **Recorded** | Every fallback is logged with the reason (timeout, error, invalid output) and counted as a metric (fallback rate) |
| **How it is demoed** | Stop the triage worker, or point it at an invalid provider key, and submit a help request |
| **Test** | SC5: 100 % of requests are shown to the operator with the AI off |

#### D2 · Transport source unavailable

| | |
|---|---|
| **Dependency** | STCP GTFS feed (or the weather API, if plan B is used) |
| **Detected when** | The import fails (no answer, HTTP error), the feed is invalid or incomplete (fails validation on import), or the data is older than its freshness limit **[PROPOSED: 24 h for the static timetable]** |
| **What the participant sees** | The **last valid data**, with *"Data from HH:MM"* and a warning *"Live transport information unavailable"*, plus a link to the operator's website. **Event shuttles and temporary stops entered by the team are not affected**: they are internal data |
| **What the team sees** | A warning in the backoffice that the transport source is down, with the time of the last successful import |
| **Invalid data** | Never replaces valid data: the import is rejected as a whole and the previous version stays in use |
| **Recovery** | Import retried with backoff; the warning disappears after the first valid import |
| **How it is demoed** | Point the feed URL at an unreachable address (or upload a broken file) and open the transport screen |
| **Test** | Automated test with the source stopped: the last data and the warning are shown (US-15) |

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

## 7. Initial backlog: user stories

Rewritten on 9 October 2026 to match the feature list above, and checked against the [Definition of Ready](../Process/definition-of-ready-and-done.md#definition-of-ready-backlog-item) on 16 October 2026 (teacher feedback T1, [notes](../Meetings/2026-10-12-sprint-1-presentation-feedback.md)). One story = one issue = one PR. Acceptance criteria use *Given / When / Then*. Every story names its persona and its dependencies; a story moves to *Ready* on the board only when none of its dependencies blocks it (§8.4). Won't-have features (F19, F20) have no stories. Items still marked **[PROPOSED]** wait for the team decision named in their dependencies.

### 7.1 DoR check (16 October 2026)

| DoR item | What failed | Fix |
|---|---|---|
| User story format | US-04 had no *so that* | Added |
| Persona | No story named a persona ID; US-01 and US-02 said only "user" | *Persona* line on every story (P1–P5, or the technical goal) |
| Testable *Given / When / Then* | US-08: "shows the nearest first-aid point" offline needed the cached map (F11, Could). US-10: "close" had no criterion, and "alert on their phone" duplicated US-26 and assumed push notifications (guide C5 decided polling). US-13: no criterion for adding a shuttle. US-14: assumed a timetable feed, but the transport source is not decided. US-18: "too many reports" had no number. US-19: no *reject*. US-23: nothing for the team side | Criteria rewritten (see each story). Numbers that are new are marked **[PROPOSED]** |
| Dependencies identified | No story listed its dependencies | *Depends on* line on every story; blockers in §8.4 |
| One story = one PR | US-02, US-19 and US-25 each held two or three PRs of work | Split: account deletion → **US-29**; responder check and fix of a report → **US-27**; Owner transfer and event suspension → **US-28**. US-01's QR-code criterion moved to US-03, where the QR code is created |
| AI fallback | US-11 did not say what happens when the AI fails | *AI fallback* line pointing to US-12 (scenario D1) |
| On the board | No story had an issue | Issues to create from the user-story form (§8.3), once the team confirms this plan |

#### Why the stories were split

The DoR page says a story that needs more than one PR is too big and must be split. Each split below also separates an open dependency from work that can start now.

| New story | Split from | Why it is a separate story |
|---|---|---|
| **US-29** · Delete my account | US-02 | (1) It depends on account deletion (guide D7, still open with Bruno); inside US-02 it would block sign-up behind that decision. (2) It deletes badges, which only exist from US-20 in Sprint C. (3) Deleting an account touches every service that holds personal data (Auth & Users, Reports, Badges), so it needs its own events and its own tests for the right to erasure (GDPR). Sign-up and profile editing touch only Auth & Users |
| **US-27** · Send a report to a responder | US-19 | (1) It needs the responder's phone view (US-26) and the `Responder` role, which the team has not confirmed yet (PE-6). (2) Confirm, reject and unconfirm work from the control room alone, so US-19 delivers value without it. (3) It is a different flow, with two personas (P2 sends, P5 checks or fixes), like the help-request dispatch |
| **US-28** · Transfer the Owner role, suspend an event | US-25 | (1) Creating an event is the first step of every journey and must be in Sprint B; transferring the Owner and suspending an event are exception flows, only needed when an owner leaves or an event is misused. (2) Each has its own audit and authorisation tests. (3) Until it is built, the platform admin (an internal user, P4) can do both by hand |

**US-01's QR-code criterion moved to US-03.** The QR code is created when the Owner publishes the event (US-03). Left in US-01, it made login depend on event management.

#### Why these numbers **[PROPOSED]**

| Number | Story | Why |
|---|---|---|
| **5 reports in 10 minutes** per account | US-18 | A participant can see several problems at once (a queue, a broken WC, broken glass), so 5 allows a real burst. More than that in 10 minutes looks like spam or badge farming, which §6.4 must prevent. Reports expire after 20–60 minutes (§6.1), so 10 minutes is shorter than any expiry. It is a setting, to tune with real data |
| **≤ 5 s** until a dispatched request appears on the responder's phone | US-26 | Same target as SC2 for the control room: the responder is on the critical path of a help request. With polling (guide C5) it needs a refresh every 2–3 s on the open screen, which one instance per service can serve at the expected load |

#### Why these criteria were rewritten

| Story | Before | After | Why |
|---|---|---|---|
| US-08 (no network) | "shows the nearest first-aid point" | Says the request was not sent, lets the participant send it again, and shows the first-aid points if the map is already loaded | Finding the nearest point offline needs the cached map, which is F11 (Could). A Must story cannot depend on a Could. The new criterion can be tested with the map in memory |
| US-10 (routing) | "its responders get an alert on their phone" | The request appears in the team's responder list (US-26) | Push notifications were never decided; the team decided polling (guide C5). The phone side belongs to US-26, so US-10 stops where the operator's work ends. A criterion for *close* was also missing |
| US-14 (transport) | "next departures" from the STCP feed | "next buses at the stops near the venue", from whatever source is chosen | The source is waiting for the professor (Porto Digital FIWARE or another). FIWARE gives buses in circulation, not timetables, so the old criterion could fail whichever source is chosen |

#### Issue type of each story

README §1 allows four issue types. Rule used in §8.3, from the two-app decision (guide F1):

- **[Front Office]**: the participant's mobile app (P1).
- **[Back Office]**: the event team's work: event management on the web (P3, P4) and the backoffice of operators and responders (P2, P5). The responder works on the phone, but in the backoffice field view (personas, P5), not in the participant app.
- **[Technicians]**: technical work with no single user screen: login (US-01, used by both apps), isolation between events (US-04, a test in every service) and the walking skeleton.

### F1 · Login and logout

**US-01** · As a **user** (participant or team member), I want to log in and log out, so that only I can use my account.
*Persona:* P1, P2, P3, P4, P5 · *Depends on:* walking skeleton (§8.2); identity provider (guide E1, Guerra; mocked authentication is allowed for the MVP, assignment §6.6)
- Given I have an account, when I log in with valid credentials, then I see the events I can access.
- Given I enter wrong credentials, when I log in, then I see an error that does not say which field was wrong.
- Given I am logged in, when I log out, then my session ends and protected pages ask me to log in again.

### F2 · Personal account management

**US-02** · As a **user**, I want to create my account and edit my details, so that I control my personal data.
*Persona:* P1, P2, P3, P5 · *Depends on:* US-01
- Given I am a new participant, when I sign up and accept the terms, then my account is created.
- Given I do not accept the terms, when I sign up, then no account is created.
- Given I am logged in, when I edit my name or password, then the change is saved.

**US-29** · As a **user**, I want to delete my account, so that my personal data leaves Farol.
*Persona:* P1, P2, P3, P5 · *Depends on:* US-02, US-20; account deletion (guide D7, Bruno)
- Given I delete my account, then my personal data and badges are deleted, and my past reports stay only as anonymous statistics.
- Given I deleted my account, when I try to log in with it, then I am refused.

### F3 · Event management

**US-25** **[PROPOSED]** · As a **platform admin**, I want to create an event for a verified organisation and name its first Owner, so that only real organisers run events on Farol.
*Persona:* P4 · *Depends on:* US-01; team confirms P4 and who creates events (personas PE-5); global `PlatformAdmin` role (guide E2, Guerra)
- Given an organisation asks for a new event, when I confirm the requester represents it and create the event with a name and dates, then it is saved as a draft that participants cannot see, and the named user becomes its Owner.
- Given a user who is not a platform admin, when they try to create an event, then the action is refused.
- Given I am a platform admin, when I open an event, then I see only its name, dates, organisation, owners and status, never its help requests, reports or participants.

**US-28** **[PROPOSED]** · As a **platform admin**, I want to transfer an event's Owner role and suspend an event, so that an event never stays without an owner or open to misuse.
*Persona:* P4 · *Depends on:* US-25
- Given the Owner of an event leaves the organisation, when the organisation asks and I transfer the Owner role, then the new Owner has it and the change is recorded with my name and the time.
- Given an event is abandoned or misused, when I suspend it, then participants can no longer open it.

**US-03** · As an **event owner**, I want to edit, publish and close my event, so that participants can find it only while it is relevant.
*Persona:* P3 · *Depends on:* US-25
- Given the platform admin created my event (US-25), when I open it, then I see it as a draft that participants cannot see, and I can edit it.
- Given I publish the event, then participants can open it and its QR code and link are generated.
- Given I am not logged in, when I scan the event's QR code, then I am asked to log in and then taken to that event.
- Given I close the event, then participants can no longer send help requests or reports to it.

**US-04** · As a **team member of event X**, I want no other event's team to see my event's data, so that our help requests, reports and participants stay private (SC8).
*Persona:* P2, P3, P5; technical goal SC8 (report §5, technical risks) · *Depends on:* US-01, US-03; `event_id` filtering (guide D3, decided)
- Given a user is a member of event X only, when they request any data from event Y, then the request is refused and the attempt is logged with the user, the event and the time.
- Given a service stores event data, then an integration test proves that a member of event X cannot read or change event Y's data (guide D3).

### F4 · Event role and response-team management

**US-05** · As an **event owner or admin**, I want to invite people, give them a role and put them in a response team, so that each person can only do what their job requires.
*Persona:* P3 · *Depends on:* US-03; team confirms the `Responder` role (personas PE-6)
- Given I invite a user as *Operator* in the *Medical* team, then they see event X's help requests but cannot change its configuration.
- Given I invite a user as *Responder* in the *Medical* team, then they see only the requests and reports dispatched to that team. **[PROPOSED]**
- Given I change or revoke a member's role, then the change applies at their next action and is recorded with my name and the time.
- Given I am an *Operator*, when I try to change the event's roles, then the action is refused.

### F5 · Venue map

**US-06** · As an **event admin**, I want to upload the venue map as an image or PDF and place points of interest on it, so that participants and operators share the same reference.
*Persona:* P3 · *Depends on:* US-03; venue map library (guide F3, ADR-007, #44)
- Given I upload a valid image or PDF, when I save, then participants see the new map version.
- Given I upload a file that is not an image or PDF, when I save, then it is refused with a message and the current map stays.
- Given I place a point of type *first aid*, when I save, then participants see it with its icon and name.

**US-07** · As a **participant**, I want to find a type of place on the map (WC, first aid, entrance, transport stop), so that I know where to go.
*Persona:* P1 · *Depends on:* US-06
- Given the map has first-aid points, when I filter by *first aid*, then only those points are highlighted.
- Given the map has no point of the type I filter by, when I filter, then I see a message saying so.

### F6 · Help request

**US-08** · As a **participant**, I want to ask for help by choosing a category and marking where I am on the map, so that the team can reach me.
*Persona:* P1 · *Depends on:* US-04, US-06
- Given I choose a category and mark a location, when I submit, then I see a confirmation and the status of my request.
- Given I submitted a request, then my location is visible only to the event's operators and admins and to the responders it is dispatched to.
- Given I have no network, when I submit, then the app tells me the request was not sent, lets me send it again, and, if the map is already loaded, shows where the first-aid points are.

### F7 · Incident backoffice

**US-09** · As an **incident operator**, I want to see new help requests as they arrive, on a list and on the map, so that I can react quickly.
*Persona:* P2 · *Depends on:* US-05, US-08
- Given a request is submitted for my event, then it appears in my list within 5 s without reloading the page (SC2; polling, guide C5).
- Given a request has a location, then it is shown at that point on the venue map.

**US-10** · As an **incident operator**, I want to set the priority, route a request to a response team and close it, so that the right people deal with it.
*Persona:* P2 · *Depends on:* US-09
- Given I change the status, priority or team of a request, then the change is recorded with my name, the time, and the old and new values (SC9).
- Given I route a request to the *Medical* team, then it appears in that team's responder list with its point on the map (US-26).
- Given I close a request, then the participant sees it closed.

**US-26** **[PROPOSED]** · As a **responder**, I want to get the requests dispatched to my team on my phone and update their status, so that the control room and the participant know help is on the way.
*Persona:* P5 · *Depends on:* US-10; team confirms the `Responder` role (personas PE-6)
- Given a request is dispatched to my team, then it appears on my phone within 5 s **[PROPOSED]**, with its category and its point on the venue map.
- Given I accept it, then its status becomes *On the way* and the participant sees "Help is on the way".
- Given another responder of my team already accepted it, then I see that and cannot accept it too.
- Given I mark it resolved, then the participant and the control room see it closed.

### F8 · AI help-request triage

**US-11** · As an **incident operator**, I want each new help request to arrive with a suggested category, priority and response team, so that I can route it faster.
*Persona:* P2 · *Depends on:* US-09; AI provider and model (guide K1, Tomás); input, output schema and priority scale (§5.2 AI-1 to AI-3) · *AI fallback:* US-12 (scenario D1)
- Given a help request is submitted and the AI is available, then a suggestion is shown within 30 s, marked as a suggestion (SC3).
- Given I accept the suggestion, then its values become the request's values and the change is recorded as mine.
- Given I already set a priority, when a suggestion arrives, then my priority is not changed.
- Given the AI suggests a priority lower than the category's default, then the default priority is kept (domain model I4).

**US-12** · As an **incident operator**, I want requests to keep arriving when the AI is down, so that no one is left without help (scenario D1).
*Persona:* P2 · *Depends on:* US-11 · *AI fallback:* this story is the fallback
- Given the AI does not answer within 30 s, or answers with invalid data, then the request is shown with the participant's category, marked "Not triaged", in order of arrival (SC5).
- Given the AI provider is down, then the backoffice shows a banner saying triage is unavailable.
- Given a fallback happens, then it is logged with its reason (timeout, error or invalid output) and counted in the fallback-rate metric.

### F9 · Transport information and external integration

**US-13** · As an **event admin**, I want to add the event's shuttles and temporary stops with their schedule, so that participants know how to get home.
*Persona:* P3 · *Depends on:* US-06
- Given I add a shuttle with its stops and departure times, when I save, then participants see it on the transport screen.
- Given I mark a shuttle as delayed, then participants see the change with the time it was made.

**US-14** · As a **participant**, I want to see the next transport options home from the venue, including regular STCP buses, so that I don't walk home or drive.
*Persona:* P1 · *Depends on:* US-03; transport data source (professor's answer, guide A2, ADR-008)
- Given the external transport source was read, then I see the next buses at the stops near the venue, with the time of the data.

**US-15** · As a **participant**, I want to know when the transport information may be out of date, so that I don't rely on it blindly (scenario D2).
*Persona:* P1, P3 · *Depends on:* US-13, US-14; resilience values (guide J1, #43)
- Given the transport source is unavailable or sends invalid data, then I see the last valid information with "Data from HH:MM" and a warning, and event shuttles are still shown.
- Given the source sends invalid data, then the import is rejected as a whole and the previous data stays in use.
- Given the source is unavailable, then the backoffice shows a warning with the time of the last successful import.
- Given the source comes back, then the warnings disappear after the next valid import.

### F10 · Announcements

**US-16** · As an **event admin**, I want to publish an announcement (e.g. "Gate 3 closed, use Gate 1"), so that participants find out immediately.
*Persona:* P3 · *Depends on:* US-03
- Given I publish an announcement, then participants with the app open see it within 10 s without reloading, with its publication time (SC6).
- Given I edit or remove an announcement, then participants see the change within 10 s.

### F11 · Weak-network mode (Could)

**US-17** · As a **participant**, I want the map and the last information to work when the network drops, so that I'm not left without information.
*Persona:* P1 · *Depends on:* US-06, US-16
- Given I opened the event once with network, when I lose connection, then I still see the map and information with "Data from HH:MM".

### F12 · Community reports

**US-18** · As a **participant**, I want to report a problem on the map (e.g. a long queue at a gate), so that other people and the team know about it.
*Persona:* P1 · *Depends on:* US-05, US-06; report types and expiry times (§6.1)
- Given I choose a report type and a point on the map, when I submit, then the operators of the responsible team see it in the backoffice straight away, with status *Pending*.
- Given my report is pending, then other participants do not see it.
- Given I have sent 5 reports in the last 10 minutes **[PROPOSED]**, when I send another, then it is refused with a message saying when I can report again.
- Given a report reaches the expiry time of its type (§6.1), then it leaves the map.

### F13 · Report confirmation

**US-19** · As an **operator of the responsible team**, I want to confirm, reject or unconfirm a report, so that only true information is shown and rewarded.
*Persona:* P2 · *Depends on:* US-18
- Given a report of a type my team is responsible for, when I confirm it, then it appears on participants' maps with its time and counts as a confirmed report of the reporter.
- Given a pending report, when I reject it, then it never appears on participants' maps and does not count.
- Given a confirmed report, when I unconfirm it, then it disappears from the map and no longer counts.
- Given a report of a type my team is not responsible for, then I cannot confirm it (an Admin can).

**US-27** **[PROPOSED]** · As an **operator of the responsible team**, I want to send a report to a responder of my team to check it on site or to fix the problem, so that reports I cannot check from the control room still get an answer, and problems get fixed.
*Persona:* P2, P5 · *Depends on:* US-19, US-26; team confirms the `Responder` role (personas PE-6)
- Given I cannot check a report from the control room, when I ask a responder of my team to check it, then they get it on their phone and their answer ("true" or "not true") comes back to me.
- Given a confirmed report needs action, when I send it to a responder and they mark it resolved, then it leaves the map and still counts for badges.

### F14 · Badges for confirmed reports

**US-20** · As a **participant**, I want to earn badges for my confirmed reports, overall and in each event, so that my contribution is recognised.
*Persona:* P1 · *Depends on:* US-19
- Given my confirmed-report count reaches 1, 3, 10, 50, 100 or 500, then my *Report history* badge moves to Wood, Bronze, Silver, Gold, Platinum or Diamond.
- Given my confirmed reports **in event X** reach a tier threshold, then my *Event reporter* badge for event X moves to that tier.
- Given one of my confirmed reports is unconfirmed, then my counts go down and my badge tiers are recalculated, so a badge can go down a tier.
- Given I hide or turn off my badges in my account, then they are not shown and no new ones are earned.

### F15 · Badges for event attendance (Should)

**US-21** · As a **participant**, I want to earn a badge for the events I attend, so that coming back is recognised.
*Persona:* P1 · *Depends on:* US-20, US-22
- Given I checked in to an event (US-22), then the event counts as attended (once per event).
- Given my attended-event count reaches a tier threshold, then my *Event attendance* badge moves to that tier.

### F16 · Event check-in (Should)

**US-22** · As a **participant**, I want to check in at the venue by scanning the event's QR code, so that my attendance is recorded for my badges.
*Persona:* P1 · *Depends on:* US-03
- Given I am logged in and the event is running, when I scan the venue QR code, then I see "Checked in" and the event counts as attended.
- Given I already checked in to this event, when I scan again, then nothing changes (one check-in per event).
- Given the event has not started or has ended, when I scan the QR code, then the event opens but no check-in is recorded.

### F17 · Entrance status (Could)

**US-23** · As a **participant**, I want to see the status of each entrance, so that I choose a gate with a shorter queue.
*Persona:* P1, P3 · *Depends on:* US-06
- Given I am an event admin, when I set Gate 2 to "long queue", then the status is saved with the time of the update.
- Given the team marked Gate 2 as "long queue", then I see it on the map with the time of the update.

### F18 · Friends (Could)

**US-24** · As a **participant**, I want to add friends in the app, so that I can find people I know who use it.
*Persona:* P1 · *Depends on:* US-02
- Given I send a friend request and it is accepted, then each of us appears in the other's friend list.
- Given I remove a friend, then we no longer appear in each other's list.

## 8. Sprint goals and initial release plan **[PROPOSED]**

Assignment §7.1.2, item 5, and §7.2–§7.3. Proposed on 16 October 2026 (Tiago, backlog owner), to confirm with the team at the next planning meeting. Order follows the MoSCoW rank in §2 and the dependencies in §7.

### 8.1 Sprint goals

| Sprint | Dates | Goal |
|---|---|---|
| **B** · Build and operate the vertical slice | 2 Nov – 6 Dec 2026 | **A logged-in participant asks for help on the venue map of a published event; an operator sees the request at once with an AI suggestion, or without one when the AI is down, and dispatches it to a response team whose responder accepts and resolves it.** Deployed through the pipeline, with logs and metrics, and transport data read from the external source. Answers the Sprint B investor question: *does the product deliver observable user value, and can the team deploy, monitor and operate it?* |
| **C** · Evaluate, harden and present | 7 Dec 2026 – 3 Jan 2027 | **The MVP is complete: transport with event shuttles and the D2 warning, live announcements, community reports confirmed by the responsible team, and badges.** The AI triage is evaluated against the non-AI baseline, and both degraded modes (D1, D2) are demoed. Answers: *is this a credible product and a maintainable system?* |

### 8.2 Initial release plan

Every merge to `main` is deployed (continuous deployment, guide H5). A *release* is the version shown at a review.

| Release | When | What it contains | Assignment |
|---|---|---|---|
| **R0 · Walking skeleton** | First week of Sprint B (by 8 Nov 2026) | One endpoint, one test, a Dockerfile and the CI/CD pipeline, deployed. Not a user story: a task issue | §7.1.2 item 10 (late from Sprint A) |
| **R1 · Vertical slice** | Sprint B review (6 Dec 2026) | US-01, US-25, US-03, US-04, US-05, US-06, US-08, US-09, US-10, US-26, US-11, US-12, US-14: the help-request journey end to end (W2 + W3), with AI triage, scenario D1 and the first external integration | §7.2.2 items 1–13 |
| **R2 · MVP** | Sprint C review (3 Jan 2027) | Every remaining Must: US-02, US-29, US-28, US-07, US-13, US-15, US-16, US-18, US-19, US-27, US-20 (scenario D2, engagement mechanism). Should US-22 and US-21 if there is time | §7.3.2 items 1–2, 8 |
| **Later** (product roadmap) | After the course | Could: US-17, US-23, US-24. Won't: F19, F20 | §7.3.2 item 13 |

**Cut line for Sprint C.** If Sprint C runs short, drop in this order: US-21 and US-22, then US-27, US-07 and US-28 (reasons in §8.5).

### 8.3 Stories on the board

Each story is a GitHub issue created from the [user-story form](../../.github/ISSUE_TEMPLATE/user-story.yml), titled `[<Type>] US-XX: <Title>` (README §1): **[Front Office]** for the participant's mobile app, **[Back Office]** for the event team (web event management and the operators' and responders' backoffice), **[Technicians]** for cross-cutting technical work.

| Story | Issue | Type | Sprint |
|---|---|---|---|
| Walking skeleton (task) | # | Technicians | B |
| US-01 · Log in and log out | # | Technicians | B |
| US-25 · Create an event and name its Owner | # | Back Office | B |
| US-03 · Edit, publish and close an event | # | Back Office | B |
| US-04 · Keep each event's data private | # | Technicians | B |
| US-05 · Invite members, roles and teams | # | Back Office | B |
| US-06 · Upload the venue map and points | # | Back Office | B |
| US-08 · Ask for help on the map | # | Front Office | B |
| US-09 · See new help requests live | # | Back Office | B |
| US-10 · Prioritise, route and close a request | # | Back Office | B |
| US-26 · Responder receives and resolves a request | # | Back Office | B |
| US-11 · AI triage suggestion | # | Back Office | B |
| US-12 · Requests without AI (scenario D1) | # | Back Office | B |
| US-14 · Next transport options home | # | Front Office | B |
| US-02 · Create and edit my account | # | Front Office | C |
| US-29 · Delete my account | # | Front Office | C |
| US-28 · Transfer the Owner role and suspend an event | # | Back Office | C |
| US-07 · Find a type of place on the map | # | Front Office | C |
| US-13 · Event shuttles and temporary stops | # | Back Office | C |
| US-15 · Out-of-date transport warning (scenario D2) | # | Front Office | C |
| US-16 · Publish an announcement | # | Back Office | C |
| US-18 · Report a problem on the map | # | Front Office | C |
| US-19 · Confirm, reject or unconfirm a report | # | Back Office | C |
| US-27 · Send a report to a responder | # | Back Office | C |
| US-20 · Badges for confirmed reports | # | Front Office | C |
| US-22 · Check in at the venue (Should) | # | Front Office | C |
| US-21 · Badges for event attendance (Should) | # | Front Office | C |
| US-17 · Weak-network mode (Could) | # | Front Office | — |
| US-23 · Entrance status (Could) | # | Front Office | — |
| US-24 · Friends (Could) | # | Front Office | — |

### 8.4 What still blocks *Ready*

These dependencies are open on 16 October 2026. A story that needs one stays in *Backlog*, not *Ready*, until it is closed. All are expected before Sprint B starts on 2 November.

| Open dependency | Owner | Blocks |
|---|---|---|
| Walking skeleton (R0), which needs the deployment platform (guide G1, Tomás) | To assign at the Sprint B planning | Every story (first to be built) |
| Identity provider (guide E1) and global `PlatformAdmin` role (E2) | Guerra | US-01, US-25 |
| P4 and who creates events (personas PE-5) | Team | US-25, US-28 |
| `Responder` role (personas PE-6) | Team | US-05, US-26, US-27 |
| Venue map library (guide F3, ADR-007, #44) | Tiago | US-06 |
| AI provider and model (guide K1); AI-1 to AI-3 (§5.2) | Tomás, AI lead | US-11 |
| Transport data source (professor, guide A2) | Professor | US-14 |
| Resilience values (guide J1, #43) | Tiago | US-15 |
| Report types and expiry times (§6.1) | Team | US-18 |
| Account deletion (guide D7) | Bruno | US-29 |

### 8.5 Why this plan

#### Why this Sprint B goal

Assignment §7.2.1 asks Sprint B for *"an operational end-to-end version of the product's most important user journey"*. That journey is the help request:

- the report's scope mitigation puts help requests first (*"prioritise help requests, the map and transport information in the MVP"*, report §5), and F6–F8 are the first features after the foundations (rank 6–8, §2);
- W2 + W3 is journey 1 in [`user-workflows.md`](user-workflows.md), and it is already drawn end to end in BPMN;
- it holds the riskiest parts of the product: the AI workflow (§5.6), the asynchronous workflow (§6.3) and isolation between events (report §5). Building them in Sprint B leaves Sprint C for evaluating them.

The goal also covers every Sprint B deliverable of §7.2.2:

| §7.2.2 deliverable | Covered by |
|---|---|
| 1 · Functional client application | US-08 (mobile app), US-25 and US-03 (web app) |
| 2 · Authentication and authorisation | US-01, US-04, US-05 |
| 3 · Persistent data storage | Every story (PostgreSQL, one database per service, guide D1–D2) |
| 4 · Documented API contracts | OpenAPI written first for each story (contract-first, guide C7) |
| 5 · At least two backend components | Auth & Users, Events, Reports and Transports (guide A2) |
| 6 · One asynchronous workflow | Help request → AI triage → backoffice (US-08, US-11; §3, row §6.3) |
| 7 · Initial external-system integration | US-14 |
| 8 · First version of the AI capability | US-11 |
| 9 · Non-AI baseline or fallback | US-12 |
| 10 · Automated tests | DoD, Level 2, on every story |
| 11 · CI/CD pipeline | R0, walking skeleton |
| 12 · Structured logs, health checks, initial metrics | R0 (health check); DoD Level 2 (logs with correlation ID); US-12 (fallback-rate metric) |
| 13 · Demonstration of a failure condition | Scenario D1 (US-12) |
| 14 · Updated backlog and architecture documentation | This section, updated at each planning |

#### Why these stories in Sprint B, and whether it is realistic

Every Sprint B story is either a step of the journey or something it needs first: US-01 → US-25 → US-03 → US-05 → US-06 → US-08 → US-09 → US-10 → US-26, plus US-04 (isolation, a top technical risk and SC8), US-11 and US-12 (AI and its fallback) and US-14 (deliverable 7). Nothing in Sprint B is there for convenience, so Sprint B has no cut line.

**Capacity:** 13 stories and the walking skeleton, in 5 weeks, for 4 people: about 3–4 stories per person, less than one per person per week. Several are small: US-03 (edit, publish, close), US-04 (one test pattern repeated in each service) and US-12 (the backoffice without the AI suggestion). The largest are US-08 and US-09 (first use of the map and of polling) and US-11 (AI).

**Risks and what happens if they occur:**

| Risk | Plan |
|---|---|
| A blocker in §8.4 is not closed by 2 November | The stories that need it stay in *Backlog*; the team starts with the stories it does not block (US-04, US-03, US-05) |
| The team rejects the `Responder` role (PE-6) | US-26 leaves the plan; the journey ends at US-10, with the operator handling the request from the control room |
| The professor's answer on the transport source comes late | US-14 starts with plan B, the weather API (§3), so deliverable 7 is still met |

#### Why US-14 is in Sprint B and US-15 in Sprint C

Sprint B must already have an *initial external-system integration* (§7.2.2, item 7), so US-14 is in Sprint B. Sprint B needs only **one** failure condition (§7.2.2, item 13), and D1 covers it. The *resilience and degraded-mode demonstration* is a Sprint C deliverable (§7.3.2, item 8), where D2 is the second scenario. US-15 also needs US-13, so that event shuttles stay on screen when the source is down.

#### Why US-02 is in Sprint C

- Mocked authentication is allowed for the MVP (assignment §6.6, footnote 1), so seeded test accounts are enough for the help-request journey.
- Sign-up is not a step of that journey.
- The terms and consent text should follow the Security & Privacy assessment (guide E5, Guerra), which is not done yet.
- US-02 and US-29 both handle personal data, so they are built and tested together.

#### Why this cut order in Sprint C

Sprint C is four weeks long, includes Christmas, and also holds the evaluation, accessibility, security, performance and presentation deliverables (§7.3.2). If the stories do not fit, they are dropped in this order:

1. **US-21 and US-22**: Should (MoSCoW). The engagement mechanism (§5.5) is already complete with the two Must badge tracks (F14).
2. **US-27** **[PROPOSED]**: reports can still be confirmed and rejected from the control room (US-19).
3. **US-07**: a convenience. The map already shows every point with its icon (US-06).
4. **US-28**: an exception flow. The platform admin is an internal user and can transfer an Owner or suspend an event by hand for the demo.

**Never cut:**
- **US-02 and US-29**: personal data (consent and the right to erasure), checked in the security review (§7.3.2, item 6).
- **US-13 and US-15**: scenario D2 (§7.3.2, item 8).
- **US-16**: the answer to root cause 2, static communication (§1).
- **US-18, US-19 and US-20**: the engagement mechanism, which the assignment requires (§5.5).

#### Why the walking skeleton comes first

It is a Sprint A deliverable (§7.1.2, item 10) that still has no owner. DoD Level 2 asks for every story to be *deployed through the pipeline*, so no story can be *Done* before the skeleton exists. One week is enough for one endpoint, one test, a Dockerfile and the pipeline, because the CI/CD choices are decided (guide H1–H5). It needs the deployment platform (guide G1, Tomás) first. **Owner:** to assign at the Sprint B planning.

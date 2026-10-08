# Main Features and Initial Backlog

> LABDSOF 2026/27 · Sprint A (Sprint 1) · Assignment §7.1.2 (deliverables 4 *MVP definition* and 5 *Product Backlog*)
> **Status:** feature list, MoSCoW, AI workflow (§4), engagement mechanism (§5) and external integration (§3) **agreed in the team meeting of 8 October 2026** ([minutes](../Meetings/2026-10-08-sprint-1-gap-review.md)). The features come from components C1–C11 (annex §1). User stories and acceptance criteria are still **[PROPOSED]**.
> **Sources:** [`problem-opportunity-report-queima.md`](../Deliverables/problem-opportunity-report-queima.md) (*Definição do MVP*, §5 risks) · [`market-and-competitor-analysis-product-specific.md`](../Deliverables/market-and-competitor-analysis-product-specific.md) (§1, §2) · assignment PDF (§5.5, §5.6, §6).
> Legend: see [`product-vision.md`](product-vision.md#legend).

## 1. How the product solves the problem

| Root cause (report §2) | What the product does | Features |
|---|---|---|
| 1. No single source of operational information | One platform per event where the team publishes everything participants need | F2, F6, F7 |
| 2. Communication is static | The team updates information during the night; every item shows its last-update time | F7, F8 |
| 3. No shared spatial reference of the venue | The venue map, uploaded by the team, is the common reference for participants and operators | F3, F4 |
| 4. Incident coordination depends on radio and phone *(hypothesis)* | Help requests arrive structured, with location, category and status, and are triaged and routed in the backoffice | F4, F5, F9 |
| 5. Slow processes at the gate | Out of scope as a process; at most, entrance status information | F11 (candidate) |

## 2. Feature list and priority

MoSCoW **agreed by the team** (8 October 2026), based on the report's mitigation for scope risk: *"priorizar no MVP os pedidos de ajuda, a planta e a informação de transportes"* (report §5, execution risks). *Rank* is the build order within the Must-haves.

| ID | Feature | Component (annex §1) | MoSCoW | Rank | Persona | Notes |
|---|---|---|---|---|---|---|
| F1 | Multi-event platform with per-event teams and permissions | C1 | Must | 1 | P3 | Foundation: every other feature is scoped by event. Isolation between events is a top technical risk (report §5) |
| F2 | Participant web app (PWA) opened by link or QR, with event selection | C2 | Must | 2 | P1 | No mandatory installation |
| F3 | Venue map uploaded by the team, with points of interest (entrances, stages, WC, first aid, transport stops) | C3 | Must | 3 | P1, P3 | Image or PDF plus points placed on it (annex C3) |
| F4 | Help request from the participant, with category and location marked on the map | C6 | Must | 4 | P1 | "Call 112" shown first for emergencies; location shared only with the request |
| F5 | Incident backoffice: receive, prioritise, route and close help requests | C7 | Must | 5 | P2 | Live list and map; full status history |
| F6 | Transport information, including event shuttles and temporary stops | C5 | Must | 6 | P1, P3 | Event-specific transport entered by the team; link to Google Maps for regular lines (annex C5) |
| F7 | Live announcements from the team, with last-update time | C4 | Must | 7 | P1, P3 | |
| F8 | Weak-network and degraded mode: cached map and information, "data from HH:MM" | C10 | Must | 8 | P1 | Also covers assignment §6.8 |
| F9 | AI help-request triage with non-AI fallback | C9 | Must (assignment §5.6) | 9 | P2 | Suggests category and priority; the operator decides. See §4 |
| F10 | Community reports on the map and badges (ethical engagement) | — | Must (assignment §5.5) | 10 | P1, P3 | Waze-style reports (queues, WC out of service, …) and badges for events attended and confirmed reports. See §5 |
| F11 | Entrance status per gate, updated manually by the team | C8 | Could | — | P1, P3 | Only if survey Q8 shows demand (annex C8) |
| F12 | Link to the ticket on the ticketing partner's site | C11 | Won't (this release) | — | P1 | Ticketing is a partner, not part of the MVP (annex C11) |

**Out of scope** (report *Fora de âmbito*; annex §1): physical capacity of entrances, heavy integrations, advanced crowd prediction, full automation, own ticketing.

## 3. Assignment requirements covered by the features

| Assignment requirement | How the product meets it | Status |
|---|---|---|
| §5.5 Ethical engagement design | F10: community reports and badges | Decided (§5) |
| §5.6 At least one AI-assisted workflow, with evaluation and baseline or fallback | F9: help-request triage | Decided (§4) |
| §6.1 Client application | F2 (PWA) and backoffice | Defined |
| §6.3 At least one asynchronous workflow | Help request → triage → notification of the event team (see [`architecture.md`](../Technical/architecture.md)) | **[PROPOSED]** |
| §6.5 Integration with at least one external system or realistic simulator | **STCP GTFS feed** (F6) if it is public and usable; **otherwise a weather API** (e.g. IPMA open data) for weather for the event nights | Decided as a rule; GTFS check pending |
| §6.6 Authentication and authorisation | Team members authenticated with per-event roles (F1); participants anonymous (to confirm) | **[PROPOSED]**; mocked authentication is allowed for the MVP (assignment footnote 1) |
| §6.8 At least one degraded-mode scenario | F8 (network), plus AI unavailable (F9 fallback) and transport source unavailable (F6) | **[PROPOSED]** |

## 4. AI-assisted workflow: help-request triage (decided)

**Decision (8 October 2026): Option A, help-request triage.** It is part of the core journey (help requests are the top MVP priority), its fallback already exists in the product, and it is easy to evaluate with a synthetic dataset. Option B (participant Q&A assistant) was rejected: a wrong transport answer at night is a higher safety risk, and a chat interface would need justification (assignment §5.6).

| | Help-request triage |
|---|---|
| What the AI does | Reads the participant's category and optional text, then suggests a **category, a priority and the response team** (medical, security, PCR). The operator accepts or changes the suggestion |
| User need | Requests arrive without category or priority (root cause 4); assumption A4 |
| Non-AI fallback | Category chosen by the participant; requests in order of arrival, marked "not triaged" (annex C9) |
| Human control | The AI only suggests. It never lowers a priority, and it never overwrites an operator's decision (domain model I4) |
| Speed target | Suggestion within **30 s** of submission; after that, the fallback applies (success criterion SC3 in [`product-vision.md`](product-vision.md#5-measurable-success-criteria)) |
| Market | Raven Controls does this for professional control rooms (annex C9); nobody does it for academic events |

**The evaluation must include (assignment §5.6.1):**
- intended purpose;
- representative inputs: short help-request texts in European Portuguese, in informal language;
- difficult or adverse cases: typos, slang, mixed languages, empty text, and text that tries to change the instructions (prompt injection);
- expected outputs: category, priority and response team;
- acceptance criteria and quality measures: accuracy per category, and how often an urgent case is under-prioritised;
- latency, cost and known limitations.

> ⚠️ **GAP: Responsible AI assessment.** Needed: (1) the *Responsible AI Opportunity Assessment* document (proposed workflow, expected value, risks, evaluation approach, fallback; Sprint 1 item 6); (2) the origin, scope and limits of the evaluation dataset (assignment §5.6.1).

## 5. Ethical engagement mechanism: community reports and badges (decided)

**Decision (8 October 2026):** participants earn **badges** for the events they attend and for useful **reports** they place on the map, in the style of Waze. Reports keep the information fresh for everyone (root cause 2) and show the team problems sooner. Badges give participants a reason to report.

### 5.1 What can be reported **[PROPOSED]**

Each report is a type, a point on the venue map and a time. It is not a help request: anything urgent or about a person goes through *Ask for help* or 112.

| Report type | Example | Who uses it |
|---|---|---|
| Queue at an entrance | "Gate 2: long queue (> 15 min)" · short / medium / long | Participants choose a gate; the team sees where the pressure is (related to F11) |
| WC problem | Out of service, no water, very long queue | Team sends cleaning or maintenance; participants go to another WC |
| Water point / bar out of water | "No water at bar 3" | Team restocks (alcohol and heat risk) |
| Crowded area | "Very crowded in front of the main stage" | Team, as an early crowd-pressure signal |
| Hazard | Broken glass, spill, dark area, broken fence | Team sends staff |
| Transport problem | "Shuttle stop moved", "shuttle full", "shuttle not passing" | Participants waiting at the stop; team updates F6 |
| Wrong information | A map point or announcement that is no longer true | Team corrects the information |

How a report behaves **[PROPOSED]**:
- It appears on other participants' maps once it is **confirmed**, by the team or by other participants ("3 people confirmed in the last 10 min").
- It **expires** after a set time for its type (e.g. 30 min for queues).
- Participants can tap "still there" or "not there anymore".

### 5.2 Badges **[PROPOSED]**

| Badge | Earned for |
|---|---|
| Event badge | Opening an event during its dates, for example by scanning the QR code at the venue (e.g. "Queima 2027") |
| First report | First report confirmed by the team or by others |
| Reporter | 5 / 20 / 50 confirmed reports |
| Good eye | A report the team marked as useful |

**There are no badges for help requests**: emergencies must never be gamified.

### 5.3 The five questions of assignment §5.5 **[PROPOSED]**

1. **Behaviour encouraged:** participants report what they see at the venue, which keeps information accurate for everyone.
2. **Why it helps the community:** other participants avoid queues and broken services; the team learns about problems before they become incidents.
3. **Misuse prevention:**
   - only confirmed reports count for badges, so false reports earn nothing;
   - reports are rate-limited per device;
   - reports expire;
   - the team can hide reports and block a device;
   - reports never change official information automatically.
4. **Exclusion or exposure:**
   - no public leaderboard and no public profile, and badges are private by default;
   - nothing shows who made a report;
   - attendance badges show neither which nights you were there nor where you were;
   - participants who don't report lose no information.
5. **Control and opt-out:** reporting and badges are optional; badges can be turned off and deleted at any time.

> ⚠️ **GAP: identity for badges.** Badges that span several events need a persistent participant identity: an anonymous device profile or an optional account. This depends on domain question DM-1 ([`domain-model.md`](../Technical/domain-model.md) §6). Losing the device loses the badges unless there is an account.
>
> ⚠️ **GAP: report types and confirmation rule.** The list in §5.1, the number of confirmations and the expiry times are proposals. Needed: the team agrees on them; ideally the interview with the security volunteer confirms which reports the team actually wants.

## 6. Initial backlog: user stories **[PROPOSED]**

Draft stories for the Must-haves, written to be copied into GitHub Projects. Acceptance criteria use *Given / When / Then*. Estimates and sprint allocation are not included.

### F1 · Multi-event and permissions

**US-01** · As an **event admin**, I want to create an event and invite my team with a role, so that each person can only do what their job requires.
- Given I am the owner of event X, when I invite a user as *operator*, then that user can see event X's help requests but cannot change its configuration.
- Given a user is a member of event X only, when they request any data from event Y, then the request is refused and the attempt is logged.

### F2 · Participant web app

**US-02** · As a **participant**, I want to open the event from a link or QR code without installing anything, so that I can get information straight away.
- Given I scan the event QR code, when the page loads, then I see that event's map without creating an account.
- Given the event has more than one night, when I open the link, then I see the current night's information.

### F3 · Venue map

**US-03** · As an **event admin**, I want to upload the venue map as an image or PDF and place points of interest on it, so that participants and operators share the same reference.
- Given I upload a valid image or PDF, when I save, then participants see the new map version.
- Given I place a point of type *first aid*, when a participant opens the map, then the point appears with its icon and name.

**US-04** · As a **participant**, I want to find a type of place on the map (WC, first aid, entrance, transport stop), so that I know where to go.
- Given the map has first-aid points, when I filter by *first aid*, then only those points are highlighted.

### F4 · Help request

**US-05** · As a **participant**, I want to ask for help by choosing a category and marking where I am on the map, so that the team can reach me.
- Given I open *Ask for help*, then "Call 112" is shown first for emergencies.
- Given I choose a category and mark a location, when I submit, then I see a confirmation and the status of my request.
- Given I have no network, when I submit, then the app tells me the request was not sent and shows the nearest first-aid point and "Call 112".

### F5 · Incident backoffice

**US-06** · As an **incident operator**, I want to see new help requests as they arrive, on a list and on the map, so that I can react quickly.
- Given a request is submitted for my event, then it appears in my list within a few seconds without reloading the page.
- Given a request has a location, then it is shown at that point on the venue map.

**US-07** · As an **incident operator**, I want to set the priority and route a request to a response team (medical, security, PCR), so that the right people deal with it.
- Given I change the status or priority of a request, then the change is recorded with my name and the time.
- Given an AI suggestion exists, then it is shown as a suggestion and I can accept or change it.

### F6 · Transport information

**US-08** · As an **event admin**, I want to add the event's shuttles and temporary stops with their schedule, so that participants know how to get home.
- Given I mark a shuttle as delayed, then participants see the change with the time it was made.

**US-09** · As a **participant**, I want to see the next transport options home from the venue, so that I don't walk home or drive.
- Given the transport source is unavailable, then I see the last known information with its time and a warning.

### F7 · Announcements

**US-10** · As an **event admin**, I want to publish an announcement (e.g. "Gate 3 closed, use Gate 1"), so that participants find out immediately.
- Given I publish an announcement, then participants with the app open see it without reloading, with its publication time.

### F8 · Degraded mode

**US-11** · As a **participant**, I want the map and the last information to work when the network drops, so that I'm not left without information.
- Given I opened the event once with network, when I lose connection, then I still see the map and information with "data from HH:MM".

### F9 · AI help-request triage

**US-12** · As an **incident operator**, I want each new help request to arrive with a suggested category, priority and response team, so that I can route it faster.
- Given a help request is submitted, when the AI is available, then a suggestion is shown within 30 s, marked as a suggestion.
- Given the AI does not answer within 30 s, then the request is shown with the participant's category, marked "not triaged", in order of arrival.
- Given I already set a priority, when a suggestion arrives, then my priority is not changed.

### F10 · Community reports and badges

**US-13** · As a **participant**, I want to report a problem on the map (e.g. a long queue at a gate), so that other people and the team know about it.
- Given I choose a report type and a point on the map, when I submit, then the team sees it in the backoffice straight away.
- Given my report is confirmed by the team or by other participants, then it appears on other participants' maps with its time, and disappears when it expires.

**US-14** · As a **participant**, I want to earn badges for the events I attend and for my confirmed reports, so that my contribution is recognised.
- Given my report is confirmed, then my report count goes up and I receive the next badge when I reach it.
- Given I turn badges off, then my badges are deleted and no new ones are earned.

> ⚠️ **GAP: backlog not on the board.** Needed: stories on the **GitHub Projects** board as issues (one story = one issue = one PR), sprint goals for Sprints B and C, and the initial release plan (§7.1.2, item 5).

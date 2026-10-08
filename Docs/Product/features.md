# Main Features and Initial Backlog

> LABDSOF 2026/27 · Sprint 1 · Assignment §7.1.2 (deliverables 4 *MVP definition* and 5 *Product Backlog*)
> **Status:** draft. The feature list comes from the components C1–C11 already identified in the repository (annex §1). Priorities, user stories and acceptance criteria are **[PROPOSED]**.
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

MoSCoW **[PROPOSED]**, based on the report's mitigation for scope risk: *"priorizar no MVP os pedidos de ajuda, a planta e a informação de transportes"* (report §5, execution risks). *Rank* is the build order within the Must-haves.

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
| F9 | AI-assisted workflow with non-AI fallback | C9 | Must (assignment §5.6) | 9 | P2 or P1 | **[DECISION NEEDED]**: which workflow, see §4 |
| F10 | Ethical engagement mechanism | — | Must (assignment §5.5) | 10 | P1 | **[DECISION NEEDED]**: see §5. Not mentioned anywhere in the repository before this document |
| F11 | Entrance status per gate, updated manually by the team | C8 | Could | — | P1, P3 | Only if survey Q8 shows demand (annex C8) |
| F12 | Link to the ticket on the ticketing partner's site | C11 | Won't (this release) | — | P1 | Ticketing is a partner, not part of the MVP (annex C11) |

**Out of scope** (report *Fora de âmbito*; annex §1): physical capacity of entrances, heavy integrations, advanced crowd prediction, full automation, own ticketing.

> ⚠️ **GAP: team agreement on priorities.** The MoSCoW and rank are proposals. Needed: the team confirms them in backlog refinement and records them in Jira (assignment §8.2).

## 3. Assignment requirements covered by the features

| Assignment requirement | How the product meets it | Status |
|---|---|---|
| §5.5 Ethical engagement design | F10 | **[DECISION NEEDED]** |
| §5.6 At least one AI-assisted workflow, with evaluation and baseline or fallback | F9 | **[DECISION NEEDED]** |
| §6.1 Client application | F2 (PWA) and backoffice | Defined |
| §6.3 At least one asynchronous workflow | Help request → triage → notification of the event team (see [`architecture.md`](../Technical/architecture.md)) | **[PROPOSED]** |
| §6.5 Integration with at least one external system or realistic simulator | Transport data (F6): STCP GTFS feed or a simulator | **[DECISION NEEDED]** |
| §6.6 Authentication and authorisation | Team members authenticated with per-event roles (F1); participants anonymous (to confirm) | **[PROPOSED]**; mocked authentication is allowed for the MVP (assignment footnote 1) |
| §6.8 At least one degraded-mode scenario | F8 (network), plus AI unavailable (F9 fallback) and transport source unavailable (F6) | **[PROPOSED]** |

## 4. AI-assisted workflow **[DECISION NEEDED]**

The repository lists two candidates (annex C9). One must be chosen: the assignment requires at least one meaningful AI-assisted workflow with an evaluation and a non-AI baseline or fallback (§5.6), and a *Responsible AI Opportunity Assessment* is a Sprint 1 deliverable (§7.1.2, item 6).

| | **Option A · Help-request triage** | **Option B · Participant Q&A assistant** |
|---|---|---|
| What the AI does | Suggests category and priority for each help request from the participant's text and category; the operator accepts or changes it | Answers participants' questions about the event, only from information the team uploaded, with source and time |
| User need (repository) | Requests arrive without category or priority (root cause 4); assumption A4 | Information is hard to find (survey Q6, Q8 option "Assistente") |
| Assignment fit | "Classification and routing" is listed explicitly (§5.6) | "Retrieval over verified community information" is listed (§5.6); a chat interface must be justified (§5.6) |
| Non-AI fallback | Category chosen by the participant, requests in order of arrival (annex C9) | FAQ page and search over event information (annex C9) |
| Evaluation | Synthetic set of help-request texts with expected category/priority; compare with the fallback | Set of questions with expected answers from event data; compare with search |
| Main risk | Wrongly lowering a priority. Mitigation: AI never lowers a priority without a person (annex C9) | A wrong transport answer at night can put someone at risk (annex C9) |
| Market | Raven Controls already does it for professional control rooms (annex C9) | 42Chat FestivalBot already does it (annex C9) |

**Recommendation [PROPOSED]: Option A.** It addresses the core journey (help requests, the top MVP priority), its fallback is trivial and already part of the product, and it is easy to evaluate with a synthetic dataset. Option B carries a higher safety risk and needs justification for a conversational interface.

**If Option A is chosen, the evaluation must include (assignment §5.6.1):** intended purpose; representative inputs (short help-request texts in European Portuguese, informal language); difficult or adverse cases (typos, slang, mixed languages, empty text, text that tries to change the instructions, i.e. prompt injection); expected outputs (category, priority); acceptance criteria; quality measures (accuracy per category, how often an urgent case is under-prioritised); latency; cost; known limitations.

> ⚠️ **GAP: AI workflow not chosen and no Responsible AI assessment.** Needed: (1) team decision between A and B; (2) the *Responsible AI Opportunity Assessment* document (proposed workflow, expected value, risks, evaluation approach, fallback); (3) the origin, scope and limits of the evaluation dataset (assignment §5.6.1).

## 5. Ethical engagement mechanism **[DECISION NEEDED]**

The assignment requires at least one mechanism that encourages constructive participation, and the team must explain five things about it (§5.5). Nothing about this exists in the repository yet. Options that fit the product:

| Option | Behaviour encouraged | Fit with the repository |
|---|---|---|
| **E1 · Confirm or flag information** | Participants confirm that an item is still correct or flag that it is wrong (e.g. "this shuttle stop is closed"). The team sees flags in the backoffice; the item shows "confirmed N min ago" | Directly addresses root cause 2 (static information) and the risk that out-of-date information makes things worse (report §5) |
| E2 · "Got home safe" group check | Members of a group confirm they got home | Addresses the return-home risk (report §2, consequences), but needs group data and contact between users: privacy cost |
| E3 · Team progress indicators | Operators see open requests, response times and requests resolved per night | Useful for P2, but encourages the team, not the community |

**Recommendation [PROPOSED]: E1**, with draft answers to the five questions in §5.5:

1. *Behaviour encouraged:* participants help keep event information accurate.
2. *Why it helps:* other participants get fresher information, and the team learns about problems sooner.
3. *Misuse prevention:* rate limit per device; flags only alert the team, they never change information automatically; the team can ignore or hide flags.
4. *Exclusion or exposure:* anonymous; no rankings or public profiles; nothing shown about who flagged.
5. *Control and opt-out:* entirely optional; participants who never flag lose nothing.

> ⚠️ **GAP: engagement mechanism not chosen.** Needed: team decision, then a short section answering the five questions of §5.5.

## 6. Initial backlog: user stories **[PROPOSED]**

Draft stories for the Must-haves, written to be copied into Jira. Acceptance criteria use *Given / When / Then*. Estimates and sprint allocation are not included.

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

> ⚠️ **GAP: backlog not in a project-management tool.** The assignment requires Jira or an equivalent tool for the backlog, sprints and decisions (§8.2), and an *initial release plan* and *sprint objectives* (§7.1.2, item 5). Needed: stories in Jira with estimates, sprint goals for Sprints 2 and 3, and the release plan. Stories for F9 and F10 depend on the decisions in §4 and §5.

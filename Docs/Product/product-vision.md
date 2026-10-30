# Product Vision

> LABDSOF 2026/27 · Sprint A (Sprint 1) · Assignment §5.4 and §7.1.2 (deliverable 4)
> **Status:** vision statement, pitch and success-criteria rule agreed in the team meeting of 8 October 2026 ([minutes](../Meetings/2026-10-08-sprint-1-gap-review.md)). Product name **Farol** decided on 9 October 2026. Open: some target numbers.
> **Source of truth for the problem:** [`problem-opportunity-report-queima.md`](../Deliverables/problem-opportunity-report-queima.md) (PT). This document summarises it and links back to it; it does not replace it.

## Legend

| Marker | Meaning |
|---|---|
| *(no marker)* | Already stated in the repository; the source is linked |
| **[PROPOSED]** | Drafted from repository content to fill a gap. Not yet agreed by the team |
| **[DECISION NEEDED]** | The team must choose before the item can be considered done |
| > ⚠️ **GAP** | Information that does not exist in the repository, and what is needed to complete it |

---

## 1. Vision statement

> For the **organising teams of large academic festivals in Portugal** and the **students who attend them**, who today depend on scattered, static information and on radio and phone calls to get help to the right place, **Farol** is a **multi-event coordination platform** that connects a shared venue map, live event information and help requests in a single place. Unlike Instagram posts, WhatsApp help lines and single-festival apps, it links participants and the operations team through the same data, opens from a link or QR code without installation, with one account for every event.

**One-line pitch:** *One map, one source of truth, one tap to get help, for every academic festival.*

Vision statement and pitch **agreed by the team on 8 October 2026**. Updated on 9 October 2026: login is mandatory ("one account for every event"), and "keeps working on a weak network" was removed because offline mode (F11) is now a Could.

### Product name: **Farol** (decided 9 October 2026)

Chosen from the candidates below. Still to check: the name is free on the domain.

| Name | Idea |
|---|---|
| **Farol** | "Lighthouse": guides people home and shows where help is. Short, Portuguese, easy to say at night |
| **Recinto** | "Venue": the map of the venue is the centre of the product |
| **Malta** | Portuguese slang for "the group of friends": community reports, students for students |
| **Rumo** | "Heading / direction": find your way in and home |
| **Ponto** | "Point" or "meeting point": every feature is a point on the map |
| **SOS Festa** | Says the safety part out loud, but it may sound like an emergency-only app |

Check before choosing: the name is free on the domain, and it does not tie the product to one festival (it is multi-event).

## 2. Problem in one paragraph

Large academic festivals concentrate tens of thousands of people per night around a venue, its entrances and its transport. In the pilot case, the Queima das Fitas do Porto, that is about **250 000 entries over 8 nights** (evidence E1). Operational information is fragmented across organisers, the risk-prevention committee (PCR), security, police, transport operators and the city, each on its own channel, and it is published before the event, so nothing that changes during the night reaches the participant (root causes 1 and 2). In 2022 and 2023 this showed up as queues of more than 500 m, waits of over 3 hours, reports of crushing and panic attacks, and complaints about "lack of information and signage" (evidence E3).

Source: report §2 *Problem Statement* and §4 *Problem Evidence*.

## 3. Target community and users

| Group | Role in the product | Interface | Source |
|---|---|---|---|
| **Event team** (primary) | Event organiser (e.g. FAP) and its team: organisation (EventAdmins), PCR, security coordination; operators in the control room and responders in the field, whose jobs come from the teams each event creates ([`features.md` §4](features.md#4-roles-teams-and-permissions-22-october-2026)) | Backoffice | Report §1 |
| **Participants** (primary) | Students aged 18–25, mostly in groups, on their phone at night | Participant web app (PWA) | Report §1 |
| **Operational partners** (secondary) | STCP, Metro do Porto, PSP, Civil Protection: source or destination of information | Not in the MVP | Report §1 |
| **Platform team** (internal) **[PROPOSED]** | The Farol team, with the global `FarolAdmin` role: creates events for verified organisations and names their first EventAdmin; never sees an event's operational data | Backoffice, platform area | Multi-event scope; [`personas.md`](personas.md) P4 |

Both primary users carry **the same weight** (unanimous team decision, report §1). Detailed personas: [`personas.md`](personas.md).

## 4. Value proposition

| For | Gain | Pain removed |
|---|---|---|
| **Participant** | A single, reliable place for practical information (map, entrances, services, transport home) and a way to ask for help with their location on the map | Not knowing where things are, how to get home or whom to ask for help |
| **Event team** | A shared operational picture: what is happening, where, and how to respond faster; one configuration reused across nights and events | Help requests arriving by radio and phone with no structured history, priority or precise location |
| **Both** | The information the team updates appears straight away to participants, with the time of the last update | Static information that is out of date as soon as the night starts |

Source: report *Product vision > Value proposition*; market analysis §3 *Differentiation opportunity*.

**Why it is different** (market analysis §3 and annex §5): no alternative found in Portugal covers the map, transport, help requests and incident handling together (annex §3, matrix). The difference lies in the **combination** (two faces linked by the map), the **segment** (Portuguese academic event teams) and the **delivery** (multi-event, no installation, weak-network ready).

## 5. Measurable success criteria

**Rule (team decision, 8 October 2026):** a success criterion must **prove that the product works**. It must be measurable on the MVP by a test, a timer or a query, without depending on how fast people react. For example, "the AI triage routes a request in under 30 s" is a success criterion; "the event team responds faster" is not, because it depends on the operators, not on the product.

| # | Success criterion | Metric | How it is measured | Target |
|---|---|---|---|---|
| SC1 | The app opens fast, even on a weak network | Time from scanning the QR code to seeing the venue map, participant already logged in | Automated test with network throttling (slow 3G profile) | ≤ 5 s cold, ≤ 1 s from cache (confirmed by Tiago, 24 October 2026) |
| SC2 | A help request reaches the event team at once | Time from "submit" on the phone to the request appearing in the backoffice, without reloading | Automated end-to-end test | p95 ≤ 5 s (confirmed by Tiago, 24 October 2026) |
| SC3 | **The AI triage routes a request fast** | Time from submission to a stored suggestion of category, priority and team | Timer in the triage worker; load test with the evaluation set | **≤ 30 s** (team decision) |
| SC4 | The AI triage is correct where it matters | Urgent cases under-prioritised; category accuracy | Evaluation set, compared with the non-AI fallback | 0 urgent cases under-prioritised; category accuracy ≥ 80 % (confirmed by Tiago, 24 October 2026) |
| SC5 | No request is lost when the AI fails | % of requests shown to the operator when the AI is off or slow | Automated test with the AI stopped | 100 %, with the fallback after 30 s |
| SC6 | Live updates reach participants | Time from publishing an announcement to it appearing on an open participant app | Automated end-to-end test | ≤ 15 s (confirmed by Tiago, 24 October 2026: participant pages poll every 10 s, so the worst case is one interval plus the server time) |
| SC7 | Information shows how fresh it is | % of information items showing their last-update time | Automated UI test | 100 % |
| SC8 | Each event's data stays private to its team | Cross-event access attempts that succeed | Automated authorisation tests (report §5, technical risks) | 0 |
| SC9 | Every change to a request is recorded | % of request changes with an audit record (who, when, from, to) | Database query after the test suite | 100 % |
| SC10 | The app works without network | Map and information shown from cache after the network is cut | Automated offline test. Only if F11 (Could) is built; not a §6.8 scenario | Passes, with "data from HH:MM" |
| SC11 | Asking for help is quick | Time from opening "Ask for help" to request submitted | Usability test with students | Median ≤ 30 s (confirmed by Tiago, 24 October 2026) |

### Validation of the problem (not success criteria)

These measure whether the product is worth building, not whether it works. They come from the user research:

| # | Question | Metric | Target |
|---|---|---|---|
| V1 | Would students use it? (assumptions A2, A3) | % answering "Sim, de certeza" (yes, definitely) or "Provavelmente sim" (probably yes) to survey Q7 | ≥ 60 %; below 40 % triggers a rethink |
| V2 | Is finding information a real problem? | Survey Q6, ease of finding information (1–5) | Average ≤ 3 confirms the problem |

Outcomes the MVP cannot measure (they need a real event): adoption during the event, faster response by the team, less confusion at entrances and transport, positive feedback from event teams (report, *Success criteria*). They stay as long-term outcomes.

**Targets:** SC3 (30 s) set by the team on 8 October 2026; SC1, SC2, SC4, SC6 and SC11 confirmed by Tiago on 24 October 2026 (#137), with SC6 raised from 10 s to 15 s to fit the 10 s polling of guide J3.

**Results (student survey, three waves: 3 to 6 and 30 October 2026, n = 31 to 32):** **V1 met**, 84 % (27/32). **V2 met only by the overall average**, 2.97: wave 1 alone is 3.70 (n = 20) and waves 2 and 3 are 1.64 (n = 11), recruited among a team member's friends after the wave 1 result was known. Read as weak support, to confirm with a test in night conditions. The main pain reported is the wait at the entrance (77 %). Details, limitations and the decisions taken from them: report, *User Research*.

## 6. MVP boundary

| In the MVP | Out of the MVP |
|---|---|
| Participant web app, behind login: event selection, venue map, essential information, transport information, simple help-request flow | Physical capacity of entrances (number of gates, queue funnel design): infrastructure, not software |
| Backoffice: each team configures its own event, manages the venue map, updates information, handles incoming help requests | Heavy integrations with operators |
| Multi-event with per-event permissions | Advanced crowd prediction, full automation |
| Weak-network support with "last updated" times | Own ticketing (link to the ticketing partner at most) |

Source: report *MVP definition* and *Out of scope*; annex §1. Feature-level detail and priorities: [`features.md`](features.md).

## 7. Key risks (summary)

The three critical assumptions are **A2** (participants use the app during the night), **A3** (they accept one app for many events, opened by link or QR) and **A4** (help requests through the app reach the right team faster). If any of them fails, the product loses its reason to exist (report §5). Full list of assumptions A1–A9 and risks: report §5.

## 8. Research status

| Method | Status | Validates | Source |
|---|---|---|---|
| Online student survey (Google Forms, 11 questions) | Done: 33 answers, n = 31 to 32 after the filter (wave 1, 3 to 6 October 2026; waves 2 and 3, 30 October 2026) | A2, A3, A4, problem | [`inquerito-estudantes-google-forms.gs`](../Research/inquerito-estudantes-google-forms.gs); results in the report, *User Research* |
| Interview with a security volunteer | **TBD** | A4, root cause 4 | Report §4 |
| Public evidence (press, operators, FAP) | Done (E1–E7) | Problem | Report §4 |
| Market and competitor analysis | Done | Differentiation | [`market-and-competitor-analysis.md`](../Deliverables/market-and-competitor-analysis.md) |

> ⚠️ **GAP: interview.** The report's *Results*, *Limitations* and *Resulting product decisions* are filled with the survey (30 October 2026): five decisions, three of which change the backlog once the team confirms them. The security-volunteer interview is still missing, so A4 and the event team's side have no primary data.

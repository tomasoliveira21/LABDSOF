# Product Vision

> LABDSOF 2026/27 · Sprint 1 · Assignment §5.4 and §7.1.2 (deliverable 4)
> **Status:** draft for the Sprint 1 review (12 October 2026). Consolidates what is already agreed in the repository and adds the parts that were missing (vision statement, measurable success criteria).
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

> **[PROPOSED]** For the **organising teams of large academic festivals in Portugal** and the **students who attend them**, who today depend on scattered, static information and on radio and phone calls to get help to the right place, **[product name]** is a **multi-event coordination platform** that connects a shared venue map, live event information and help requests in a single place. Unlike Instagram posts, WhatsApp help lines and single-festival apps, it links participants and the operations team through the same data, opens from a link or QR code without installation, and keeps working on a weak network.

**One-line pitch [PROPOSED]:** *One map, one source of truth, one tap to get help, for every academic festival.*

> ⚠️ **GAP: product name.** The product has no name anywhere in the repository. Needed: a working name for the slides, the repository description and the PWA title.

## 2. Problem in one paragraph

Large academic festivals concentrate tens of thousands of people per night around a venue, its entrances and its transport. In the pilot case, the Queima das Fitas do Porto, that is about **250 000 entries over 8 nights** (evidence E1). Operational information is fragmented across organisers, the risk-prevention committee (PCR), security, police, transport operators and the city, each on its own channel, and it is published before the event, so nothing that changes during the night reaches the participant (root causes 1 and 2). In 2022 and 2023 this showed up as queues of more than 500 m, waits of over 3 hours, reports of crushing and panic attacks, and complaints about "lack of information and signage" (evidence E3).

Source: report §2 *Problem Statement* and §4 *Problem Evidence*.

## 3. Target community and users

| Group | Role in the product | Interface | Source |
|---|---|---|---|
| **Event team** (primary) | Event owner (e.g. FAP) and its team: organisation, PCR, security coordination | Backoffice | Report §1 |
| **Participants** (primary) | Students aged 18–25, mostly in groups, on their phone at night | Participant web app (PWA) | Report §1 |
| **Operational partners** (secondary) | STCP, Metro do Porto, PSP, Civil Protection: source or destination of information | Not in the MVP | Report §1 |

Both primary users carry **the same weight** (unanimous team decision, report §1). Detailed personas: [`personas.md`](personas.md).

## 4. Value proposition

| For | Gain | Pain removed |
|---|---|---|
| **Participant** | A single, reliable place for practical information (map, entrances, services, transport home) and a way to ask for help with their location on the map | Not knowing where things are, how to get home or whom to ask for help |
| **Event team** | A shared operational picture: what is happening, where, and how to respond faster; one configuration reused across nights and events | Help requests arriving by radio and phone with no structured history, priority or precise location |
| **Both** | The information the team updates appears straight away to participants, with the time of the last update | Static information that is out of date as soon as the night starts |

Source: report *Visão de produto > Proposta de valor*; market analysis §3 *Oportunidade de diferenciação*.

**Why it is different** (market analysis §3 and annex §5): no alternative found in Portugal covers the map, transport, help requests and incident handling together (annex §3, matrix). The difference lies in the **combination** (two faces linked by the map), the **segment** (Portuguese academic event teams) and the **delivery** (multi-event, no installation, weak-network ready).

## 5. Measurable success criteria

The report lists the success criteria qualitatively (*Critérios de sucesso*). The assignment (§5.4) requires them to be **measurable**. The table below turns each one into a metric. The metric ideas come from the market annex §5 (*Como se prova*); the **targets are [PROPOSED]** and must be confirmed by the team.

| # | Success criterion (report) | Metric | How it is measured in the MVP | Target **[PROPOSED]** | Baseline |
|---|---|---|---|---|---|
| S1 | Students find the information they need quickly | Task success: "find the nearest first-aid point" and "find the next shuttle home" | Usability test with students on the prototype/MVP | ≥ 80 % complete each task in ≤ 30 s | Survey Q6 (ease of finding information, 1–5): **pending** |
| S2 | Opening the app is fast, even on a weak network | Time from scanning the QR code to seeing the venue map | Automated test with network throttling (slow 3G profile) | ≤ 5 s (cold), ≤ 1 s from cache | None (new capability) |
| S3 | Help requests are easier to submit | Time from opening "Ask for help" to request submitted | Usability test | Median ≤ 30 s | Survey Q5 (what students do today): **pending** |
| S4 | Help requests are located precisely | % of help requests with a location marked on the map | Product analytics | ≥ 80 % | Today: location described by voice or not at all (report, root cause 4, *to confirm*) |
| S5 | The event team responds faster | Time from request submitted to acknowledged by an operator | Simulation of a night with a synthetic set of requests | Median ≤ 2 min | **Pending**: interview with the security volunteer |
| S6 | The team has a clear view of what is happening | % of requests with category, location, status and history recorded | Database query | 100 % | Today: no structured history (report, root cause 4) |
| S7 | Information is trustworthy | % of live information items showing their last-update time | Automated UI test | 100 % | Today: none (static posts) |
| S8 | Each event's data stays private to its team | Cross-event access attempts that succeed | Automated authorisation tests (report §5, technical risks) | 0 | — |
| S9 | Students would actually use it (assumption A2/A3) | % answering "Sim, de certeza" or "Provavelmente sim" to survey Q7 | Survey | Validation gate: ≥ 60 %; below 40 % triggers a rethink | **Pending** |

Outcomes the MVP cannot measure directly (they need a real event): adoption during the event, less confusion at entrances and transport, positive feedback from event teams (report, *Critérios de sucesso*). They stay as long-term outcomes.

> ⚠️ **GAP: baselines.** S1, S3, S5 and S9 have no baseline because the survey results and the interview with the security volunteer are still "TBD" (report, *Pesquisa com Utilizadores*). Needed: survey results (target of 100+ answers, report §4) and interview notes, then fill the *Baseline* column and confirm or adjust the targets.
>
> ⚠️ **GAP: team agreement on targets.** Every target above is a proposal. Needed: the team confirms or changes them, and records the decision (Jira or a decision log).

## 6. MVP boundary

| In the MVP | Out of the MVP |
|---|---|
| Participant web app: event selection, venue map, essential information, transport information, simple help-request flow | Physical capacity of entrances (number of gates, queue funnel design): infrastructure, not software |
| Backoffice: each team configures its own event, manages the venue map, updates information, handles incoming help requests | Heavy integrations with operators |
| Multi-event with per-event permissions | Advanced crowd prediction, full automation |
| Weak-network support with "last updated" times | Own ticketing (link to the ticketing partner at most) |

Source: report *Definição do MVP* and *Fora de âmbito*; annex §1. Feature-level detail and priorities: [`features.md`](features.md).

## 7. Key risks (summary)

The three critical assumptions are **A2** (participants use the app during the night), **A3** (they accept one app for many events, opened by link or QR) and **A4** (help requests through the app reach the right team faster). If any of them fails, the product loses its reason to exist (report §5). Full list of assumptions A1–A9 and risks: report §5.

## 8. Research status

| Method | Status | Validates | Source |
|---|---|---|---|
| Online student survey (Google Forms, 11 questions) | Script ready; results **TBD** | A2, A3, A4, problem | [`inquerito-estudantes-google-forms.gs`](../Research/inquerito-estudantes-google-forms.gs) |
| Interview with a security volunteer | **TBD** | A4, root cause 4 | Report §4 |
| Public evidence (press, operators, FAP) | Done (E1–E7) | Problem | Report §4 |
| Market and competitor analysis | Done | Differentiation | [`market-and-competitor-analysis.md`](../Deliverables/market-and-competitor-analysis.md) |

> ⚠️ **GAP: research findings.** The report sections *Resultados*, *Limitações* and *Decisões de produto resultantes* are all "TBD". These are a Sprint 1 deliverable (assignment §7.1.2, item 3). Needed: survey results (even preliminary, with *n*), interview notes, and at least one product decision that changed because of them.

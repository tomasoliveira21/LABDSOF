# Personas

> LABDSOF 2026/27 · Sprint 1 · Assignment §7.1.2 (deliverable 4, *Personas*)
> **Status:** **proto-personas [PROPOSED]**. They are built only from what is in the repository: the user groups and context in the report (§1), the stakeholder map (§3), the public evidence (§4) and the hypotheses the survey is designed to test. **They are not based on user research yet.** Names and ages are fictional. The assignment forbids fabricating research evidence (§5.3, §11), so every attribute below is either sourced or marked as a hypothesis.
> Legend: see [`product-vision.md`](product-vision.md#legend).

## Why five personas

The report has two primary user groups with the same weight: the **event team** and the **participants** (report §1). The event team, however, groups people with different jobs and different permissions: the person who **configures** the event before it starts and the people who **handle help requests** during the night. The journeys and the backoffice screens are different for each, so they are split here into two personas. Together with the participant, that makes three.

**[PROPOSED] 10 October 2026:** Farol hosts **many events** (Queima do Porto, Braga, Coimbra, Lisboa, …), and each organisation manages only its own. Someone outside every event has to create events, decide who runs each one, and step in when an event has no EventAdmin left. That is the fourth persona, **P4**, an internal user from the Farol team, with the global role `FarolAdmin`.

**[PROPOSED] 12 October 2026** (professor feedback on the presentation: who receives the reports and how they are handled was not clear): the people who handle requests during the night do two different jobs. The **control-room operator** receives help requests and reports and **decides** what happens to each one; the **responder** is in the field, gets what the control room routes to their team and **acts** on it. They work in different places, on different screens, so the old P2 is split into **P2** and **P5**.

**22 October 2026:** the role model in [`features.md` §4](features.md#4-roles-teams-and-permissions-22-october-2026) (to confirm with the team) gives each persona a role or a job:

- **Roles.** P4 is the `FarolAdmin` (global). P3 is an `EventAdmin` of her event. P1 is a `RegularUser` of each event she joins. Roles belong to the membership of an event, so the same person can be EventAdmin in one event and RegularUser in another.
- **Jobs, not roles.** P2 and P5 are `RegularUser`s of the event. What they can do comes from the **teams** the EventAdmin puts them in: P2 is in a team with `HelpRequests.Triage` and `Reports.Review` (e.g. *Control room*); P5 is in a team with `HelpRequests.Respond` or `Reports.Respond` (e.g. *Nursing*, *Security*).

| Persona | User group (report §1) | Role or job (features §4) | Main interface | Main journeys |
|---|---|---|---|---|
| **P1 · Rita, the participant** | Participants | `RegularUser` | Participant web app | W1, W2, W6, W7, W8 |
| **P2 · Miguel, the control-room operator** | Event team (PCR, security coordination) | `RegularUser` in a team with `HelpRequests.Triage`, `Reports.Review` | Backoffice, live view (laptop) | W2, W3, W7 |
| **P3 · Sofia, the event admin** | Event team (organisation) | `EventAdmin` | Backoffice, configuration | W4, W5 |
| **P4 · Daniel, the FarolAdmin** **[PROPOSED]** | Farol team (internal user, not in report §1) | `FarolAdmin` (global) | Backoffice, platform area | W4 (first steps) |
| **P5 · Inês, the responder** **[PROPOSED]** | Event team (field teams, e.g. Nursing, Security, PCR, Logistics) | `RegularUser` in a team with `HelpRequests.Respond`, `Reports.Respond` | Backoffice on the phone, field view | W2, W3, W7 |

Journeys W1–W8 are described in [`user-workflows.md`](user-workflows.md).

---

## P1 · Rita, the participant

| | |
|---|---|
| **Who** | 21, university student in Porto, goes to the Queima with a group of friends for several nights |
| **Context of use** | At night, phone in hand, limited battery, congested network, loud environment, often after drinking (report §1) |
| **Goals** | Know where things are (WC, stages, first aid, entrances); know how and when to get home; know whom to ask for help, fast |
| **Frustrations (sourced)** | Long, badly distributed entrance queues and lack of information and signage (E3); information published before the event that does not reflect what changes during the night (root cause 2); the venue map is an image on social media (root cause 3) |
| **What she does today (hypothesis)** | Looks for information on Instagram, the event site and WhatsApp groups; looks for a security guard or staff member when she needs help. Survey (n = 30): information comes from the organisation's social media (77 %) and WhatsApp groups (60 %); in need, 40 % would first ask friends and 33 % look for staff (Q4, Q5; report, *User Research*) |
| **Barriers to adoption (hypothesis)** | Installing another app, battery use, sharing her location, weak network, not trusting that the information is up to date. Survey Q9 (n = 31): battery 48 %, installing another app 39 %, weak network 32 %, outdated information 23 %, sharing location 6 % (report, *User Research*) |
| **Location sharing (hypothesis)** | Survey Q10 (n = 31): 71 % would share the phone's location with a help request, 26 % only a place on the venue map. Product decision 4 of 30 October 2026 (report, *User Research*) asks for the phone's location first, with the map as the fallback; still to reconcile with [assessment M-1](../Technical/security-and-privacy-assessment.md#6-data-minimisation-measures) and [ADR-007](../Technical/adr/0007-venue-map-representation.md) |
| **What success looks like** | Opens the link from the QR code on her ticket or at the venue (annex §7), already logged in from the first time, sees the map in seconds, finds the next shuttle, and can ask for help for a friend in under 30 s (success criteria SC1, SC11) |
| **Design implications** | No installation; one account for every event, logged in once and kept logged in on the phone (login is mandatory, decided 9 October 2026); large tap targets; short text; works from cache; never asks for more data than needed (report §5, risks) |

## P2 · Miguel, the control-room operator

| | |
|---|---|
| **Who** | 24, volunteer in the risk-prevention committee (PCR) or security coordination, in the control room. **[PROPOSED] 12 Oct:** he decides what happens to each request and report; he does not go to the scene, the responders (P5) do |
| **Role or job** | A `RegularUser` of the event, in the *Control room* team, whose permissions are `HelpRequests.Triage` and `Reports.Review` (features §4.2, §4.4). Not a role: if the EventAdmin takes him out of the team, he is a participant like any other |
| **Context of use** | During the night, under time pressure, with many requests arriving at the same time; on a laptop in the control room |
| **Goals** | See every help request with what happened and where; decide the priority; route it to the right team of the event (e.g. *Nursing*, *Security*, *PCR*); know what is still open; confirm or reject the reports of the types his team reviews |
| **Frustrations (hypothesis)** | Requests arrive by radio and phone, with no structured history, no priority and no precise location. *Report root cause 4, explicitly marked as a hypothesis to confirm in the interview with the security volunteer* |
| **What he needs from the product** | A live list and a map of open requests; category, location and status on every request; a suggested priority he can accept or change; a record of who did what |
| **Constraints** | Must not depend on the product alone: radio stays as the backup channel (report §5, regulatory risks) |
| **What success looks like** | Sees each new request within seconds, with its exact location on the map and an AI-suggested priority and team within 30 s (SC2, SC3). Knows, for every report of his team, whether it is pending, confirmed, rejected or resolved |

> ⚠️ **GAP:** almost everything about P2 is a hypothesis until the **interview with the security volunteer** happens (report §4). Needed: interview notes on how a request reaches the team today, which channels are used, how long it takes, and who decides the priority. Then update the *Frustrations* and *Constraints* rows.

## P3 · Sofia, the event admin

| | |
|---|---|
| **Who** | 23, member of the organising team of the academic federation, responsible for operations or communication. She is an **`EventAdmin`** of her event and manages **only that event**: she cannot see or change any other festival on Farol. In another event she joins as a participant, she is a `RegularUser` |
| **Context of use** | Weeks before the event: sets it up on a laptop. During the nights: updates information (an entrance closed, a shuttle delayed) from the control room or her phone |
| **Goals** | Configure the event once (venue map, points of interest, transport, team members) and reuse it every night; publish changes that reach participants straight away |
| **Frustrations (sourced)** | Each partner communicates through its own channel; nobody has the complete picture (root cause 1); the venue map is a static image (root cause 3) |
| **Constraints (sourced)** | Board terms are annual, so knowledge is lost every year (stakeholder conflict 5); limited budget and no technical team; may already have an official app from another supplier (stakeholder conflict 1) |
| **What she needs from the product** | Upload the map as an image or PDF and place points on it; create the event's teams, give each one its permissions and help categories, and add people to them (features §4.2); share the management with other EventAdmins; publish updates that show their time; never see other events' data, and never let other events' teams see hers (report §5, technical risks) |
| **What success looks like** | A second night (or a second event) is ready without new development, only configuration (assumption A9) |

> ⚠️ **GAP:** there is no direct access to the real organisation (FAP); this is a documented limitation (report §4). Needed: at least one conversation with someone who has organised an academic event, to validate assumption A1 (organisations accept to configure and maintain their event on the platform) and the configuration journey W4.

## P4 · Daniel, the FarolAdmin **[PROPOSED]**

| | |
|---|---|
| **Who** | 27, member of the Farol team. Not part of any festival's organisation. He has the only global role, **`FarolAdmin`** (features §4.1) |
| **Context of use** | Office hours, on a laptop, mostly in the weeks before each festival and when an organisation changes its board. Not involved during the event nights |
| **Goals** | Bring a new festival onto Farol quickly and without new code; make sure every event is run by its real organisation; keep each event's data out of reach of everyone else, himself included |
| **Frustrations / risks** | Board terms are annual, so the people who run an event change every year and access gets lost (stakeholder conflict 5, sourced). Anyone could pretend to be a festival and publish a fake event that participants would trust (*hypothesis*: a risk of a multi-event platform, not in the report) |
| **What he needs from the product** | Create an event for a verified organisation and name its first EventAdmin (US-06); see every event's name, dates, organisation, EventAdmins and status (US-07); name a new EventAdmin when an event has none, e.g. when the board changes (US-08); suspend and reactivate an event that is abandoned or misused (US-09) |
| **Constraints** | **Never sees operational data**: no help requests, locations, reports or participant data of any event. Being `FarolAdmin` gives no access to any event's data (features §4.1). Isolation between events is a top technical risk (report §5) and privacy is a regulatory risk |
| **What success looks like** | A new festival goes live with configuration only (assumption A9), run by its real organisers, without Farol staff ever touching its incident data |

> ⚠️ **GAP:** P4 is not in the report (§1 lists only the event team, participants and partners). It follows from the multi-event scope. Since 22 October only the FarolAdmin creates events, for an organisation that asks and is verified (features §4.1, US-06); this is part of the role model still to confirm with the team (PE-5).

## P5 · Inês, the responder **[PROPOSED]**

| | |
|---|---|
| **Who** | 22, volunteer in one of the event's field teams (e.g. *Nursing*, *Security*, *PCR* or *Logistics*; each event creates its own), on foot around the venue |
| **Role or job** | A `RegularUser` of the event, in a team whose permissions include `HelpRequests.Respond` and/or `Reports.Respond` (features §4.2, §4.4). Not a role: the job comes from the team she is in |
| **Context of use** | During the night, on her phone, moving through the crowd, with noise, poor light and often her hands busy. The radio stays as her backup channel |
| **Goals** | Know straight away when something is sent to her team, and exactly where it is; tell the control room that she is on the way and when it is done |
| **Frustrations (hypothesis)** | Tasks arrive by radio, with a spoken description of the place that is hard to find in the crowd; nobody knows whether someone is already on the way. *To confirm in the interview with the security volunteer* |
| **What she needs from the product** | Every help request or report routed to her team on her phone, with its point on the venue map (US-31, US-52); one tap to accept ("on my way") and one to close ("resolved") (US-32, US-33, US-53); for a report, a way to answer "true / not true" when the control room asks her team to check it |
| **Constraints** | Sees only what is routed to her team, never the event's full list. Cannot set priorities, route requests or confirm reports: that needs `HelpRequests.Triage` or `Reports.Review`, the control room's permissions (P2) |
| **What success looks like** | Accepts a routed request within seconds and reaches the marked place without asking the control room where it is |

> ⚠️ **GAP:** like P2, everything about P5 is a hypothesis until the **interview with the security volunteer** (how teams in the field get their tasks today, and who decides what).

---

## Secondary users (not personas for the MVP)

**Operational partners** (STCP, Metro do Porto, PSP, Civil Protection) are a source or destination of information (report §1). In the MVP they do not use the product directly: transport information is entered by the event team or read from an external source (assumption A6). They are listed in the stakeholder map (report §3) and should become personas only if a later release gives them an interface.

## Open items

| # | Item | Needed to close it |
|---|---|---|
| PE-1 | Validate P1's hypotheses (current information sources, barriers, location sharing) | Survey results, questions 4, 5, 9, 10 |
| PE-2 | Validate P2's current workflow and pain points | Interview with the security volunteer |
| PE-3 | Validate P3's willingness to configure and maintain the event | Conversation with an event organiser (assumption A1) |
| PE-4 | Team agrees on splitting the event team into P2 and P3 | Team decision |
| PE-5 | Team agrees on P4 (FarolAdmin) and on who creates events. **Answered on 22 October** by the role model (features §4.1): only the FarolAdmin creates events, for an organisation that asks and is verified, and names its first EventAdmin (US-06) | Team confirmation of the role model (features §4) |
| PE-6 | Team agrees on splitting the incident operator into P2 (control room, decides) and P5 (responder, acts). **Updated on 22 October:** the proposed event role `Responder` is dropped; P2 and P5 are jobs given by team permissions (`HelpRequests.Triage` and `Reports.Review` for P2, `HelpRequests.Respond` and `Reports.Respond` for P5, features §4.2), not roles | Team confirmation of the role model (features §4); interview with the security volunteer (which teams exist and who decides what) |

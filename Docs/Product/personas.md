# Personas

> LABDSOF 2026/27 · Sprint 1 · Assignment §7.1.2 (deliverable 4, *Personas*)
> **Status:** **proto-personas [PROPOSED]**. They are built only from what is in the repository: the user groups and context in the report (§1), the stakeholder map (§3), the public evidence (§4) and the hypotheses the survey is designed to test. **They are not based on user research yet.** Names and ages are fictional. The assignment forbids fabricating research evidence (§5.3, §11), so every attribute below is either sourced or marked as a hypothesis.
> Legend: see [`product-vision.md`](product-vision.md#legend).

## Why three personas, not two

The report has two primary user groups with the same weight: the **event team** and the **participants** (report §1). The event team, however, groups people with different jobs and different permissions: the person who **configures** the event before it starts and the people who **handle help requests** during the night. The journeys and the backoffice screens are different for each, so they are split here into two personas. Together with the participant, that makes three.

| Persona | User group (report §1) | Main interface | Main journeys |
|---|---|---|---|
| **P1 · Rita, the participant** | Participants | Participant web app | W1, W2, W6 |
| **P2 · Miguel, the incident operator** | Event team (PCR, security coordination) | Backoffice, live view | W3 |
| **P3 · Sofia, the event admin** | Event team (owner, organisation) | Backoffice, configuration | W4, W5 |

Journeys W1–W6 are described in [`user-workflows.md`](user-workflows.md).

---

## P1 · Rita, the participant

| | |
|---|---|
| **Who** | 21, university student in Porto, goes to the Queima with a group of friends for several nights |
| **Context of use** | At night, phone in hand, limited battery, congested network, loud environment, often after drinking (report §1) |
| **Goals** | Know where things are (WC, stages, first aid, entrances); know how and when to get home; know whom to ask for help, fast |
| **Frustrations (sourced)** | Long, badly distributed entrance queues and lack of information and signage (E3); information published before the event that does not reflect what changes during the night (root cause 2); the venue map is an image on social media (root cause 3) |
| **What she does today (hypothesis)** | Looks for information on Instagram, the event site and WhatsApp groups; looks for a security guard or staff member when she needs help. *To validate with survey Q4 and Q5* |
| **Barriers to adoption (hypothesis)** | Installing another app, battery use, sharing her location, weak network, not trusting that the information is up to date. *To validate with survey Q9* |
| **Location sharing (hypothesis)** | Prefers to mark where she is on the venue map rather than share GPS. *To validate with survey Q10* |
| **What success looks like** | Opens the link from the QR code on her ticket or at the venue (annex §7), already logged in from the first time, sees the map in seconds, finds the next shuttle, and can ask for help for a friend in under 30 s (success criteria SC1, SC11) |
| **Design implications** | No installation; one account for every event, logged in once and kept logged in on the phone (login is mandatory, decided 9 October 2026); large tap targets; short text; works from cache; "Call 112" first in emergencies; never asks for more data than needed (report §5, risks) |

## P2 · Miguel, the incident operator

| | |
|---|---|
| **Who** | 24, volunteer in the risk-prevention committee (PCR) or security coordination, in the control room or moving around the venue |
| **Context of use** | During the night, under time pressure, with many requests arriving at the same time; may be on a laptop in the control room or on a phone in the field |
| **Goals** | See every help request with what happened and where; decide the priority; send it to the right team (medical, security, PCR); know what is still open |
| **Frustrations (hypothesis)** | Requests arrive by radio and phone, with no structured history, no priority and no precise location. *Report root cause 4, explicitly marked as a hypothesis to confirm in the interview with the security volunteer* |
| **What he needs from the product** | A live list and a map of open requests; category, location and status on every request; a suggested priority he can accept or change; a record of who did what |
| **Constraints** | Must not depend on the product alone: radio stays as the backup channel. The product does not replace 112 (report §5, regulatory risks) |
| **What success looks like** | Sees each new request within seconds, with its exact location on the map and an AI-suggested priority and team within 30 s (SC2, SC3) |

> ⚠️ **GAP:** almost everything about P2 is a hypothesis until the **interview with the security volunteer** happens (report §4). Needed: interview notes on how a request reaches the team today, which channels are used, how long it takes, and who decides the priority. Then update the *Frustrations* and *Constraints* rows.

## P3 · Sofia, the event admin

| | |
|---|---|
| **Who** | 23, member of the organising team of the academic federation (event owner), responsible for operations or communication |
| **Context of use** | Weeks before the event: sets it up on a laptop. During the nights: updates information (an entrance closed, a shuttle delayed) from the control room or her phone |
| **Goals** | Configure the event once (venue map, points of interest, transport, team members) and reuse it every night; publish changes that reach participants straight away |
| **Frustrations (sourced)** | Each partner communicates through its own channel; nobody has the complete picture (root cause 1); the venue map is a static image (root cause 3) |
| **Constraints (sourced)** | Board terms are annual, so knowledge is lost every year (stakeholder conflict 5); limited budget and no technical team; may already have an official app from another supplier (stakeholder conflict 1) |
| **What she needs from the product** | Upload the map as an image or PDF and place points on it; manage who in her team can do what; publish updates that show their time; never see other events' data, and never let other teams see hers (report §5, technical risks) |
| **What success looks like** | A second night (or a second event) is ready without new development, only configuration (assumption A9) |

> ⚠️ **GAP:** there is no direct access to the real organisation (FAP); this is a documented limitation (report §4). Needed: at least one conversation with someone who has organised an academic event, to validate assumption A1 (organisations accept to configure and maintain their event on the platform) and the configuration journey W4.

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

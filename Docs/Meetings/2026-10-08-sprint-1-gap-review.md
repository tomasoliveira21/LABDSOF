# Sprint 1 Gap Review · Team Meeting

> **Date:** 2026-10-08 · **Goal:** close the open decisions before the Sprint 1 presentation on **12 October 2026** (5–7 min).
> **How to use:** go through the sections in order. For each item, read *Done* and *Missing*, decide, write the decision and the owner in the **Decision** line, and tick the box. Anything not decided today goes to **§10 Parking lot**.
> **Background:** full analysis in [`PROJECT-CONTEXT.md`](../../PROJECT-CONTEXT.md) §4 · gap list G1–G23 and decisions DN-1–DN-8 in [`CHANGELOG.md`](../../CHANGELOG.md).

**Attendees:** ______________________ · **Note-taker:** __________

## ✅ Decisions taken (8 October 2026)

| Topic | Decision | Applied in |
|---|---|---|
| Roles | Roles are identified and described, **not assigned to members** | `team-and-process.md` §2 |
| Team | **Antônio Guerra** added to the member list | `team-and-process.md` §1 |
| Project tool | **GitHub Projects** | `team-and-process.md` §3 |
| Sprints | Named **A, B, C**. B: 2 Nov – 6 Dec 2026 · C: 7 Dec 2026 – 3 Jan 2027 | `team-and-process.md` §3 |
| Meetings | **Twice a week**: planning during the class, stand-up at the weekend | `team-and-process.md` §3 |
| Process drift | Tomás turns on branch protection and squash-only merging; every PR has an issue; PR titles follow `<Prefix>: <Description>` | `team-and-process.md` §4 |
| AI workflow | **Help-request triage** (option A) | `features.md` §4 |
| Engagement | **Badges** for events attended and for **Waze-style reports** on the map | `features.md` §5, `user-workflows.md` W7 |
| Integration | **STCP GTFS** if it is public; **otherwise a weather API** | `features.md` §3 |
| MoSCoW | Agreed as proposed | `features.md` §2 |
| Vision | Statement and pitch agreed; name candidates proposed | `product-vision.md` §1 |
| Success criteria | Must prove that the product works (e.g. **AI triage routes in ≤ 30 s**), not depend on people; rewritten as SC1–SC11 | `product-vision.md` §5 |
| DoR | Removed: estimate/fits in sprint, UI sketch, privacy-impact note | `definition-of-ready-and-done.md` |
| DoD | **1 user story = 1 issue = 1 PR**; removed "every PR for the story merged" from Level 2; Level 3 removed | `definition-of-ready-and-done.md` |
| Architecture & domain model | **Not part of this presentation**; they will be evaluated later | — |
| Topic owners | Product vision: **Tomás** · Personas: **Tiago** · Main features: **Bruno** · Workflows: **Antônio**. The owner closes the open items of the topic and presents it | `PROJECT-CONTEXT.md` §4 |

## Status at a glance

| # | Topic | Status | Blocking item for the presentation |
|---|---|---|---|
| 1 | Product vision | 🟢 | Product name |
| 2 | Personas | 🟡 | No research data yet (survey, interview) |
| 3 | Main features | 🟡 | AI workflow and engagement mechanism not chosen |
| 4 | Workflows | 🟢 | Degraded-mode demo scenario |
| 5 | DoR / DoD | 🟡 | Not agreed by the team |
| 6 | Team roles & process | 🔴 | **No roles assigned, no Jira board** |
| 7 | Domain model | 🟡 | Participant identity (DM-1) |
| 8 | Architecture | 🟡 | Services vs monolith, technology stack |

**Suggested time box (≈ 60 min):** §6 roles (10) · §3 features (10) · §1 vision (5) · §5 DoR/DoD (5) · §8 architecture (10) · §7 domain (5) · §2 personas (5) · §4 workflows (3) · §9 presentation plan (7).
Roles go first because the other items need owners.

---

## 6 · Team roles and work process 🔴 *(start here)*

**Done:** the work process is defined in the README: GitHub Flow, branch/commit/PR conventions, two reviews, PR template. Six roles are proposed in [`team-and-process.md`](../Process/team-and-process.md) §2. Ceremonies are proposed.

**Missing:** nobody has a role · full member list · Jira (or equivalent) board, which the assignment requires (§8.2) · Sprint 2/3 dates · branch protection is off · process drift (PRs merged with merge commits instead of squash, commits without an issue number, direct pushes to `main`).

### 6.1 Team members
- [ ] Confirm the full list (the git history only shows Tomás, Tiago and Bruno)
- **Decision:** ____________________________________________

### 6.2 Assign roles
One person can hold more than one role. Everyone still codes and reviews.

| Role | Responsibility | Member |
|---|---|---|
| Product Owner | Backlog priority, accepts stories, speaks for users, records investor feedback | |
| Scrum Master | Runs ceremonies, keeps the board current, removes blockers | |
| Tech lead / architect | Architecture, ADRs, domain model, API outline | |
| QA lead | Test strategy, isolation tests, AI evaluation dataset | |
| DevOps lead | CI/CD, Docker, deployment, observability | |
| AI lead | AI workflow, evaluation, Responsible AI assessment | |

- [ ] Every role has a name

### 6.3 Project-management tool
- **Options:** Jira (named by the assignment) · GitHub Projects (already on GitHub, no extra account; "or equivalent" is allowed)
- **How:** create the board → link it at the top of the README → add the DoR as an issue template and the DoD as the "Done" column rule → load US-01 to US-11 from [`features.md`](../Product/features.md) §6
- [ ] Tool chosen · owner: ______ · ready by: ______

### 6.4 Sprint calendar and ceremonies
- [ ] Sprint 2 dates: ______ to ______ · Sprint 3 dates: ______ to ______
- [ ] Meeting rhythm (planning / stand-up / refinement / review / retro): ____________________

### 6.5 Fix process drift
- [ ] Repo owner turns on branch protection (README §6) and **squash-only** merging (Settings → General → Pull Requests) · owner: Tomás
- [ ] Agreement: every PR has an issue, and PR titles follow `<Prefix>: <Description>`

---

## 3 · Main features 🟡

**Done:** features F1–F12 with MoSCoW priority and build order; mapping to root causes and to the assignment requirements; 11 user stories with Given/When/Then ([`features.md`](../Product/features.md)).

**Missing:** two **mandatory** choices (assignment §5.5 and §5.6) · team agreement on priorities · external integration choice · Responsible AI assessment.

### 3.1 AI-assisted workflow (DN-1)
| | **A · Help-request triage** (recommended) | B · Q&A assistant |
|---|---|---|
| What the AI does | Suggests category and priority; the operator decides | Answers questions from the team's published info |
| Fallback | Participant's category, order of arrival | FAQ and search |
| Risk | Under-prioritising a case. Mitigation: the AI never lowers a priority | A wrong transport answer at night |
- **Why A:** it is in the core journey (W2/W3), the fallback already exists, and it is easy to evaluate with synthetic data.
- [ ] **Decision:** ______ · owner of the Responsible AI assessment: ______

### 3.2 Ethical engagement mechanism (DN-2)
- **E1 · Confirm or flag information** (recommended): participants confirm an item or flag it as wrong; the team sees the flags · E2 · "Got home safe" group check · E3 · Team progress indicators
- The five answers for E1 are already drafted in `features.md` §5.
- [ ] **Decision:** ______

### 3.3 External integration (DN-3)
- **Options:** STCP GTFS feed (real, but availability unknown) · transport simulator (fully under our control)
- **How:** someone checks this week whether the STCP GTFS feed is open and usable; if it is not, we build the simulator.
- [ ] **Decision / owner:** ______

### 3.4 Priorities
- [ ] Agree on the MoSCoW and build order in `features.md` §2 (change anything you disagree with)

---

## 1 · Product vision 🟢

**Done:** problem with evidence; 5 root causes; vision statement and one-line pitch; value proposition; MVP boundary; success criteria S1–S9 with metrics ([`product-vision.md`](../Product/product-vision.md)).

**Missing:** product name · team approval of the vision statement and S1–S9 targets · baselines (these depend on §2).

- [ ] **Product name** (needed for the slides and the PWA title). Ideas: ______ · **Decision:** ______
- [ ] Read the vision statement aloud. Approve it or edit it.
- [ ] Approve the S1–S9 targets (e.g. S1 ≥ 80 % find first aid in ≤ 30 s; S5 median ≤ 2 min to acknowledge).

---

## 5 · Definition of Ready / Done 🟡

**Done:** DoR with 9 items; DoD at three levels: PR, story, sprint ([`definition-of-ready-and-done.md`](../Process/definition-of-ready-and-done.md)); PR template in `.github/`.

**Missing:** team agreement · setup in the tool · a PO to accept stories (settled in §6.2).

- [ ] Skim the DoR. Remove or add items.
- [ ] Skim the DoD (story and sprint levels). Remove or add items.
- [ ] **How:** after approval, remove the [PROPOSED] marks and add the DoR as the issue template (§6.3) · owner: ______

---

## 8 · High-level architecture 🟡

**Done:** drivers D1–D9; context, container and deployment diagrams; async triage flow (retries, idempotency, outbox, fallback); security; degraded modes ([`architecture.md`](../Technical/architecture.md)).

**Missing:** services vs modular monolith · technology stack · ADRs · API outline · walking skeleton · Security & Privacy assessment · deployment target.

### 8.1 Services or modular monolith (DN-4 → ADR-001)
- **Services** (Event + Incident + triage worker): meets assignment §6.2 directly; isolates failures; more work to build and run.
- **Modular monolith:** less work, but the assignment wants *convincing evidence* that services add unnecessary complexity.
- [ ] **Decision:** ______

### 8.2 Technology stack (DN-5)
Choose what the team already knows. Speed matters more than novelty with three sprints.

| Decision | Choice |
|---|---|
| Backend language / framework | |
| Frontend (PWA + backoffice) | |
| Database (one, unless justified) | |
| Message broker | |
| Real-time push (WebSocket / SSE) | |
| AI provider / model | |
| Identity (a mock is allowed) | |
| CI/CD | |

### 8.3 Remaining Sprint 1 technical items
- [ ] Deployment target (university VM / cloud free tier / laptop) and how teachers access it · **Decision:** ______
- [ ] ADR-001 to ADR-008 (one short file each, from the decisions above) · owner: ______
- [ ] API outline · owner: ______
- [ ] Walking skeleton (1 endpoint + 1 test + Dockerfile + CI) · owner: ______
- [ ] Security & Privacy assessment (start from architecture §8 and domain model §5) · owner: ______

---

## 7 · Domain model 🟡

**Done:** 2 bounded contexts; class diagram; 11 entities; business rules I1–I7; privacy and retention ([`domain-model.md`](../Technical/domain-model.md)).

**Missing:** answers to DM-1 to DM-5 · the retention period.

| # | Question | Options | Decision |
|---|---|---|---|
| DM-1 | Is the participant anonymous or do they have an account? | (a) anonymous device token · (b) optional phone number · (c) account | |
| DM-2 | Is a help request the same thing as an incident? | (a) one entity (as modelled) · (b) an incident groups several requests | |
| DM-3 | Can operators create requests (reported by radio)? | yes / no | |
| DM-4 | Help categories and response teams | Keep the proposal until the interview | |
| DM-5 | Per-night configuration? | (a) one for the whole event · (b) per night | |
| — | Retention of help-request data | N days after the event | |

Suggestion for the MVP: **(a)** for DM-1, DM-2 and DM-5; **no** for DM-3. These are the simplest options and can change later.

---

## 2 · Personas 🟡

**Done:** 3 proto-personas: P1 Rita (participant), P2 Miguel (operator), P3 Sofia (admin) ([`personas.md`](../Product/personas.md)).

**Missing:** research evidence. The report's *Resultados / Limitações / Decisões* sections are still TBD.

- [ ] Agree to split the event team into P2 and P3 (PE-4)
- [ ] **Survey:** how many answers so far? Who exports the results (Q4–Q10) by ______? · owner: ______
- [ ] **Interview with the security volunteer:** who does it, and when? · owner: ______ · date: ______
- [ ] **Event organiser conversation** (validates P3 / assumption A1): anyone have a contact? · owner: ______
- **How:** even preliminary data (*n* = 30) is enough. Fill the report's TBD sections and the *Baseline* column in the vision doc, and note **one product decision that changed because of the data**. The assignment asks for this.

---

## 4 · Workflows 🟢

**Done:** W1–W6 with diagrams; W2 (ask for help) + W3 (operator handles it) form the core demo story ([`user-workflows.md`](../Product/user-workflows.md)).

**Missing:** confirmed statuses and response teams (interview) · the degraded-mode scenario to demo.

- [ ] Degraded-mode scenario to demo: network loss (W6) / AI unavailable / transport source down · **Decision:** ______

---

## 9 · Presentation plan (12 October)

| # | Slide | Source | Presenter |
|---|---|---|---|
| 1 | Problem and vision (+ name, pitch) | product-vision §1–2 | Tomás Oliveira |
| 2 | Personas P1–P3 | personas | Tiago Teixeira |
| 3 | Main features + AI + engagement | features §2, §4, §5 | Bruno de Abreu Lopes |
| 4 | Core workflow W2 + W3, plus W7 (reports) | user-workflows | Antônio Guerra |
| 5 | DoR / DoD | definition-of-ready-and-done | |
| 6 | Team roles and process | team-and-process | |

- Domain model and architecture are **not** in this presentation (decision of 8 October).
- About 1 min per slide (≈ 6 min total). Everyone presents at least one slide; the assignment evaluates each student individually.
- [ ] Who builds the deck: ______ · draft ready by: ______ · rehearsal: ______

---

## 10 · Parking lot

| Item | Owner | By |
|---|---|---|
| | | |

## 11 · Action items (fill during the meeting)

| # | Action | Owner | Due |
|---|---|---|---|
| 1 | Update the docs with today's decisions and remove the [PROPOSED] marks | | |
| 2 | Create the board and load the stories | | |
| 3 | Export the survey results | | |
| 4 | Schedule the interview | | |
| 5 | Build the slide deck | | |
| | | | |

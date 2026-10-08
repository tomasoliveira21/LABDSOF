# Changelog

All notable changes to the repository documentation. Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased] · 2026-10-08 · Team meeting decisions

**Why:** the team reviewed the gap analysis on 8 October 2026 and took the decisions listed in [`Docs/Meetings/2026-10-08-sprint-1-gap-review.md`](Docs/Meetings/2026-10-08-sprint-1-gap-review.md). This change applies them.

### Added
- `PROJECT-CONTEXT.md` (project context and what is left before the presentation) and `CLAUDE.md` (points to it).
- `Docs/Meetings/2026-10-08-sprint-1-gap-review.md`: the meeting guide, with the decisions taken.
- `features.md` §5: community reports (Waze-style) and badges, with the five answers of assignment §5.5; stories US-12 to US-14.
- `user-workflows.md` W7: report a problem and earn a badge.
- `product-vision.md` §1: product name candidates.

### Changed
- `team-and-process.md`:
  - roles are described, not assigned;
  - Antônio Guerra added to the team;
  - GitHub Projects chosen as the board;
  - sprints renamed A/B/C;
  - two meetings a week;
  - process-drift actions agreed.
- `definition-of-ready-and-done.md`:
  - removed the estimate, UI sketch and privacy items from the DoR;
  - DoD Level 3 removed;
  - new rule: one story = one issue = one PR.
- `features.md`:
  - MoSCoW agreed;
  - AI workflow = help-request triage;
  - external integration = STCP GTFS if public, otherwise a weather API.
- `product-vision.md`:
  - statement and pitch agreed;
  - success criteria rewritten as SC1–SC11, so they prove the product works (e.g. AI triage ≤ 30 s);
  - survey metrics moved to "validation of the problem".
- `personas.md`: success references updated to the new criteria.

### Not changed on purpose
- `domain-model.md` and `architecture.md`: they are evaluated after the Sprint A presentation. They will need Report/Badge entities and the integration decision.

## 2026-10-08 · Sprint 1 presentation artifacts

**Branch:** `docs/sprint-1-presentation-artifacts` (based on `main` at `e853854`)
**Why:** a gap analysis against the eight topics of the Sprint 1 presentation (12 October 2026) found that the product side was largely covered, while the process and technical artifacts were missing or incomplete. This change fills those gaps **using only information already in the repository** (the three deliverables, the survey script and the assignment PDF) and flags in each document what is still missing.
**Prepared with AI assistance** (Claude, Anthropic): the AI read the repository, drafted the documents and marked every inferred item as **[PROPOSED]**. Nothing in these documents is user-research evidence. All content needs review and agreement by the team before it is presented as the team's position.

### Status of the eight presentation topics

| # | Topic | Before | After (pending team agreement) | What still blocks a Green |
|---|---|---|---|---|
| 1 | Product vision | 🟢 | 🟢 | Product name; baselines for the success criteria (survey, interview) |
| 2 | Personas | 🟡 | 🟡 | Proto-personas only: they need the survey results and the interview to be validated |
| 3 | Main features | 🟡 | 🟡 | AI workflow and engagement mechanism decisions; backlog in Jira |
| 4 | Workflows | 🟡 | 🟢 | Help-request statuses to confirm in the interview |
| 5 | DoR / DoD | 🔴 | 🟡 | Team agreement; set up in the project tool |
| 6 | Team roles and work process | 🟡 | 🟡 | Roles must be assigned by the team; Jira link; sprint dates |
| 7 | Domain model | 🔴 | 🟡 | Open questions DM-1 to DM-5 |
| 8 | High-level architecture | 🔴 | 🟡 | Technology choices, ADRs, target environment |

### Added

| File | Content | Topic |
|---|---|---|
| [`Docs/Product/product-vision.md`](Docs/Product/product-vision.md) | Vision statement and one-line pitch **[PROPOSED]**; problem summary; users; value proposition; **measurable success criteria S1–S9** with metric, method, proposed target and baseline; MVP boundary; key risks; research status. Also defines the **legend** used by all new documents (**[PROPOSED]**, **[DECISION NEEDED]**, ⚠️ **GAP**) | 1 |
| [`Docs/Product/personas.md`](Docs/Product/personas.md) | Three **proto-personas**: P1 Rita (participant), P2 Miguel (incident operator), P3 Sofia (event admin). Splits the report's "event team" into operator and admin because their journeys and permissions differ. Every attribute is sourced or marked as a hypothesis linked to a survey question | 2 |
| [`Docs/Product/features.md`](Docs/Product/features.md) | Mapping root cause → feature; features F1–F12 (from components C1–C11) with MoSCoW and build order; traceability to the assignment's mandatory capabilities (§5.5, §5.6, §6.3, §6.5, §6.6, §6.8); **AI workflow options A (triage) and B (assistant)** with a recommendation for A; **ethical engagement options E1–E3** with a recommendation for E1 and draft answers to the five questions of §5.5; **11 draft user stories (US-01 to US-11)** with Given/When/Then acceptance criteria | 3 |
| [`Docs/Product/user-workflows.md`](Docs/Product/user-workflows.md) | Six journeys with Mermaid diagrams: W1 find information, **W2 ask for help (core journey, sequence diagram)**, **W3 handle a request (help-request lifecycle state diagram)**, W4 set up an event, W5 publish a live update, W6 degraded mode (network loss). Indicates which journeys to use in a 5–7 minute presentation | 4 |
| [`Docs/Process/definition-of-ready-and-done.md`](Docs/Process/definition-of-ready-and-done.md) | Definition of Ready (9 items); Definition of Done on three levels: PR (kept from README §7), user story, sprint increment | 5 |
| [`Docs/Process/team-and-process.md`](Docs/Process/team-and-process.md) | Team members found in the commit history; **six proposed roles** (PO, Scrum Master, tech lead, QA lead, DevOps lead, AI lead) with the *Member* column left for the team; summary of the README process; sprints and ceremonies; tools; **process health check** based on the git history | 6 |
| [`.github/pull_request_template.md`](.github/pull_request_template.md) | The PR template from README §7, which the README said to save but did not exist. GitHub will now pre-fill new PRs with it | 5, 6 |
| [`Docs/Technical/domain-model.md`](Docs/Technical/domain-model.md) | Two bounded contexts (Event Configuration, Incident Management); **Mermaid class diagram**; 11 entities and value objects, each traced to its source in the repository; invariants I1–I7; data lifecycle and privacy; open questions DM-1 to DM-5 | 7 |
| [`Docs/Technical/architecture.md`](Docs/Technical/architecture.md) | Architecture drivers D1–D9 traced to the repository and the assignment; **system context and container diagrams**; responsibilities and data ownership; sync/async communication; the **asynchronous triage workflow** with retries, idempotency, fallback and outbox; external integrations; security and authorisation; degraded modes; deployment view; services vs modular monolith trade-off; technology decisions (no technology chosen); list of ADRs to write | 8 |
| [`CHANGELOG.md`](CHANGELOG.md) | This file | — |

### Changed

| File | Change | Why |
|---|---|---|
| [`README.md`](README.md) | Added a short project description and a **Documentation** index (with the language of each document) at the top | The README had no link to any document |
| [`README.md`](README.md) §7 | "Save as `.github/pull_request_template.md`" → "Saved as [link]" | The file now exists |

### Fixed

| File | Fix |
|---|---|
| [`Docs/Deliverables/market-and-competitor-analysis.md`](Docs/Deliverables/market-and-competitor-analysis.md) (line 4) | Reference to `02a-Anexo-Mercado-Especifico-do-Produto.md`, a file that does not exist, replaced with a link to `market-and-competitor-analysis-product-specific.md` |
| [`Docs/Deliverables/market-and-competitor-analysis-product-specific.md`](Docs/Deliverables/market-and-competitor-analysis-product-specific.md) (line 4) | Reference to `02-Analise-Mercado-e-Concorrencia.md`, a file that does not exist, replaced with a link to `market-and-competitor-analysis.md` |

No other content in the existing deliverables was changed.

### Not changed on purpose

- **Language of the existing deliverables.** The README requires English, but the three deliverables are in Portuguese. The new documents are in English, following the README. Translating the deliverables, or changing the rule, is a team decision (see *Decisions needed*).
- **"Sprint A" vs "Sprint 1".** The two market documents say "Sprint A"; the report and the assignment say "Sprint 1". Left as is because it may be intentional; the team should align it.
- **The report's "Visão de produto" section** was not rewritten. The new product documents summarise it and link back to it, so the report stays the source of truth for the problem.

---

### Open gaps flagged in the documents

Each item is marked ⚠️ **GAP** in the document listed, with what is needed to close it.

| # | Gap | Where it is flagged | Needed |
|---|---|---|---|
| G1 | User research findings are all "TBD" (results, limitations, product decisions) | `product-vision.md` §8 | Survey results (even preliminary, with *n*) and interview notes |
| G2 | No baselines for success criteria S1, S3, S5, S9 | `product-vision.md` §5 | Same as G1 |
| G3 | Success-criteria targets are proposals | `product-vision.md` §5 | Team confirms or changes them |
| G4 | The product has no name | `product-vision.md` §1 | A working name |
| G5 | Persona P2 (operator) is almost entirely hypothesis | `personas.md` | Interview with the security volunteer |
| G6 | Persona P3 (admin) and assumption A1 not validated | `personas.md` | One conversation with an event organiser |
| G7 | Feature priorities are proposals | `features.md` §2 | Team confirms in backlog refinement |
| G8 | AI workflow not chosen; Responsible AI assessment missing | `features.md` §4 | Decision A or B; assessment document; evaluation dataset description |
| G9 | Ethical engagement mechanism not chosen | `features.md` §5 | Decision; answers to the five questions of assignment §5.5 |
| G10 | Backlog not in Jira; no release plan or sprint goals | `features.md` §6 | Stories in Jira with estimates; sprint goals; release plan |
| G11 | Help-request statuses and response teams are assumptions | `user-workflows.md` W3 | Interview with the security volunteer |
| G12 | Degraded-mode scenario to demonstrate not chosen | `user-workflows.md` W6; `architecture.md` §9 | Team picks the scenario |
| G13 | DoR and DoD not agreed or set up in the tool | `definition-of-ready-and-done.md` | Team agreement; issue template and board rule |
| G14 | No one holds the Product Owner role (needed to accept stories) | `definition-of-ready-and-done.md` | Role assignment (G16) |
| G15 | Team list may be incomplete | `team-and-process.md` §1 | Full list of members |
| G16 | Roles not assigned | `team-and-process.md` §2 | Team assigns roles |
| G17 | Dates for Sprints 2 and 3 and ceremony schedule | `team-and-process.md` §3 | Team decision |
| G18 | No Jira (or equivalent) board referenced | `team-and-process.md` §3 | Link to the board |
| G19 | Retention period for help-request data | `domain-model.md` §5 | Number of days; part of the security and privacy assessment |
| G20 | Transport data source not confirmed | `architecture.md` §7 | Check whether the STCP GTFS feed is usable, or build a simulator |
| G21 | Security and Privacy Assessment missing (Sprint 1 item 9) | `architecture.md` §8 | The assessment document |
| G22 | Target deployment environment unknown | `architecture.md` §10 | Where the MVP runs and how teachers access it |
| G23 | ADRs, API outline and walking skeleton missing (Sprint 1 items 7, 8, 10) | `architecture.md` §13 | ADR-001 to ADR-008; API outline; minimal end-to-end code with build, test and container |

### Decisions needed from the team

| # | Decision | Where |
|---|---|---|
| DN-1 | AI workflow: A (triage, recommended) or B (assistant) | `features.md` §4 |
| DN-2 | Engagement mechanism: E1 (confirm or flag information, recommended), E2 or E3 | `features.md` §5 |
| DN-3 | External integration: STCP GTFS or simulator | `features.md` §3; `architecture.md` §7 |
| DN-4 | Separate services or modular monolith (ADR-001) | `architecture.md` §11 |
| DN-5 | Technology stack (backend, frontend, database, broker, AI provider, identity, CI/CD) | `architecture.md` §12 |
| DN-6 | Domain questions DM-1 to DM-5 (participant identity, request vs incident, operator-created requests, categories, per-night configuration) | `domain-model.md` §6 |
| DN-7 | Role assignment | `team-and-process.md` §2 |
| DN-8 | Language of repository documentation (translate the deliverables or change the rule) | `team-and-process.md` §4 |

### Process findings from the git history

Recorded in `team-and-process.md` §4, not fixed here because they depend on repository settings:

- Commits made directly on `main` without a PR (`5ef2c4a`, `3a4ba04`, `e853854`): the "no direct pushes" rule is not enforced.
- PRs #18–#20 were merged with merge commits, although README §4 requires squash merge.
- Excluding the initial commit, 4 of 7 non-merge commits do not follow the commit-message convention.

### Notes for whoever opens the PR

- The commits on this branch have **no issue number**, because there is no issue for this work yet. Create one in `tomasoliveira21/LABDSOF` and use `Closes #<issue>` in the PR description; with squash merge, the PR title becomes the commit on `main` (README §4).
- Suggested PR title: `Docs: Add Sprint 1 product, process and technical artifacts`.
- Mermaid diagrams render on GitHub. They were checked with the Mermaid CLI before committing.

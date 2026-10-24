# Sprint A · Meeting with the Professor

> **Date:** 2026-10-22 · **Context:** points discussed with the professor: architecture diagrams, AI hosting, degraded modes, the Sprint A deliverables, cost estimation, the walking skeleton and the mobile app.
> **Background:** architecture decisions in [`architecture-decisions-guide.md`](../Technical/architecture-decisions-guide.md); earlier meetings in [`2026-10-08-sprint-1-gap-review.md`](2026-10-08-sprint-1-gap-review.md) and [`2026-10-12-sprint-1-presentation-feedback.md`](2026-10-12-sprint-1-presentation-feedback.md); what changed in [`CHANGELOG.md`](../../CHANGELOG.md).

## Points discussed

| # | Point | Outcome |
|---|---|---|
| P1 | **Architecture diagrams** | Delete the **physical view level 4** diagrams (`physical-view-level-4-*`) and their **logical–physical mappings** (`mapping-logical-physical-level-4-*`). The logical–implementation mappings stay. In **physical view level 3**, show that there are **five different databases**, one per service |
| P2 | **AI hosting:** our own machine (e.g. Ollama) or a hosted API with an API key? Who decides? | Already planned: guide K1, owner **Tomás**. The proposal is in [#53](https://github.com/tomasoliveira21/LABDSOF/issues/53) (ADR-005, *Proposed*) |
| P3 | **Degraded modes:** what happens when the transport source or the AI is down | **AI down:** manual triage by the operator (scenario D1, unchanged). **Transport source down:** see P4 |
| P4 | **Transport data flow** | A scheduled loop requests the Porto Digital API every **x** seconds and stores the result in the database. Users always read the stored data, never the API directly. If the stored data is older than **y**, the app shows a warning. This matches [ADR-004](../Technical/adr/0004-service-communication.md) decision 5 and scenario D2 ([`features.md`](../Product/features.md) §3.1). Invalid data still never replaces the last valid data |
| P5 | **Sprint A presentation** | One deck (PowerPoint, Keynote, …) covering the project scope (see *Presentation contents*) |
| P6 | **Sprint A documents as one PDF** | Join the Markdown documents of the Sprint A deliverables, which hold the whole scope of Sprint A, into a single PDF |
| P7 | **Operating cost estimation** | Prepare it with **salaries and infrastructure**. Keep it simple; reasonable assumptions are enough |
| P8 | **Walking skeleton** | To do (assignment §7.1.2, deliverable 10) |
| P9 | **Mobile app** | The React Native mobile app is removed. Farol is **one React web app**; each page is built for one use, on the **phone** (participants, field teams) or on a **laptop** (event management, control room). Guide F4 (React Native setup) and H9 (mobile build) no longer apply ([#125](https://github.com/tomasoliveira21/LABDSOF/issues/125)) |

## Presentation contents

What the professor listed, with the document each section comes from today.

| Section | Source in the repository | Status |
|---|---|---|
| Product vision | [`product-vision.md`](../Product/product-vision.md) | Exists |
| User personas | [`personas.md`](../Product/personas.md) | Exists |
| Customer journeys / general user workflow | [`user-workflows.md`](../Product/user-workflows.md) and the BPMN diagrams | Exists |
| Domain model | [`domain-model.md`](../Technical/domain-model.md) | Exists |
| Architecture & technology | [`architecture.md`](../Technical/architecture.md), ADRs, draw.io diagrams | Exists; diagrams change with P1 |
| Assumptions & dependencies | Assumptions in [`problem-opportunity-report-queima.md`](../Deliverables/problem-opportunity-report-queima.md); dependencies spread across the ADRs | **Missing** as one list |
| Operating costs estimation | Infrastructure research in #53 (guide G2) | **Missing**: salaries and the final figure (P7) |
| Prioritised product backlog (epics) | [`features.md`](../Product/features.md) §2 and §7; issues #58 to #121 (§8.3) | Partial: issues created and on the [board](https://github.com/users/tomasoliveira21/projects/1); priority, size and *Ready* applied in #138 (24 October 2026) |
| NFRs | Architecture drivers ([`architecture.md`](../Technical/architecture.md) §1) and success criteria | **Missing** as one list |
| High-level scope of Sprint 1 | Sprint goals in #45 (`features.md` §8.1, not merged) | Partial |
| People and responsibilities | [`team-and-process.md`](../Process/team-and-process.md) §2 and the guide's owners table | Exists |
| Work methodology | [`team-and-process.md`](../Process/team-and-process.md), contributing guidelines in [`Docs/README.md`](../README.md) | Exists |
| Definition of Ready | [`definition-of-ready-and-done.md`](../Process/definition-of-ready-and-done.md) | Exists |
| Definition of Done | [`definition-of-ready-and-done.md`](../Process/definition-of-ready-and-done.md) | Exists |

## Decisions

| Topic | Decision | Applied in |
|---|---|---|
| Diagrams | Physical level 4 and its logical–physical mappings removed; physical level 3 shows five databases | Removal: diagrams, [ADR-009](../Technical/adr/0009-architecture-documentation.md) and [`architecture.md`](../Technical/architecture.md) §14 (#55). Level 3: Bruno, later |
| Transport data | Poll every x seconds, store the result, users read only from the database, warning after y | Already in ADR-004 and D2. Values of x and y: guide J1 and D2, Tiago |
| Cost estimation | Salaries + infrastructure, simple assumptions | New item, owner **Bruno** |
| Frontend | No mobile app: one React web app with phone pages and laptop pages | Guide §0, F1, F3, F4, H9; [ADR-002](../Technical/adr/0002-backend-and-frontend-technology.md); [ADR-007](../Technical/adr/0007-venue-map-representation.md) rewritten for the web; [`architecture.md`](../Technical/architecture.md); [`features.md`](../Product/features.md) (#125). Diagrams: Bruno, later |

## Open actions

- [x] **Bruno:** delete `physical-view-level-4-*` and `mapping-logical-physical-level-4-*`; update ADR-009 and `architecture.md` §14 (P1). Done in #55.
- [ ] **Bruno:** expand `physical-view-level-3` to show the five databases (P1).
- [x] **Bruno:** remove the mobile app from the documentation (P9). Done in #125, except the diagrams.
- [ ] **Bruno:** remove the mobile app from the diagrams (levels 1 to 3 and `*-level-4-mobile`) (P9).
- [ ] **Tiago:** review [ADR-007](../Technical/adr/0007-venue-map-representation.md), rewritten for the web (`react-zoom-pan-pinch` + SVG markers), and accept or change it (P9).
- [ ] **Tomás:** close K1 (own machine or API key) with ADR-005 (P2).
- [ ] **Tiago:** set x (poll interval) and y (freshness limit) for the transport data (P4, guide J1 and D2).
- [ ] **No owner yet, done at the end:** the presentation deck (P5).
- [ ] **No owner yet, done at the end:** the single PDF with the Sprint A documents (P6).
- [ ] **Bruno:** the operating cost estimation, salaries + infrastructure (P7). The infrastructure part can reuse Tomás's research in #53 (G2).
- [ ] **Tomás:** the walking skeleton (P8).
- [ ] **Tiago:** one list of NFRs, for the deck.
- [ ] **No owner yet, done at the end:** one list of assumptions and dependencies, for the deck, put together from the other documents.
- [ ] **Guerra:** simulator of the Porto Digital API or recorded responses (guide J4).

# Sprint A · Meeting with the Professor

> **Date:** 2026-10-22 · **Context:** points discussed with the professor: architecture diagrams, AI hosting, degraded modes, the Sprint A deliverables, cost estimation and the walking skeleton.
> **Background:** architecture decisions in [`architecture-decisions-guide.md`](../Technical/architecture-decisions-guide.md); earlier meetings in [`2026-10-08-sprint-1-gap-review.md`](2026-10-08-sprint-1-gap-review.md) and [`2026-10-12-sprint-1-presentation-feedback.md`](2026-10-12-sprint-1-presentation-feedback.md); what changed in [`CHANGELOG.md`](../../CHANGELOG.md).

## Points discussed

| # | Point | Outcome |
|---|---|---|
| P1 | **Architecture diagrams** | Delete the **physical view level 4** diagrams (`physical-view-level-4-*`) and the **view-mapping** diagrams (`mapping-*`). In **physical view level 3**, show that there are **five different databases**, one per service |
| P2 | **AI hosting:** our own machine (e.g. Ollama) or a hosted API with an API key? Who decides? | Already planned: guide K1, owner **Tomás**. The proposal is in [#53](https://github.com/tomasoliveira21/LABDSOF/issues/53) (ADR-005, *Proposed*) |
| P3 | **Degraded modes:** what happens when the transport source or the AI is down | **AI down:** manual triage by the operator (scenario D1, unchanged). **Transport source down:** see P4 |
| P4 | **Transport data flow** | A scheduled loop requests the Porto Digital API every **x** seconds and stores the result in the database. Users always read the stored data, never the API directly. If the stored data is older than **y**, the app shows a warning. This matches [ADR-004](../Technical/adr/0004-service-communication.md) decision 5 and scenario D2 ([`features.md`](../Product/features.md) §3.1). Invalid data still never replaces the last valid data |
| P5 | **Sprint A presentation** | One deck (PowerPoint, Keynote, …) covering the project scope (see *Presentation contents*) |
| P6 | **Sprint A documents as one PDF** | Join the Markdown documents of the Sprint A deliverables, which hold the whole scope of Sprint A, into a single PDF |
| P7 | **Operating cost estimation** | Prepare it with **salaries and infrastructure**. Keep it simple; reasonable assumptions are enough |
| P8 | **Walking skeleton** | To do (assignment §7.1.2, deliverable 10) |

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
| Prioritised product backlog (epics) | [`features.md`](../Product/features.md) §2 and §7; backlog work in #45 | Partial: epics not yet on the board |
| NFRs | Architecture drivers ([`architecture.md`](../Technical/architecture.md) §1) and success criteria | **Missing** as one list |
| High-level scope of Sprint 1 | Sprint goals in #45 (`features.md` §8.1, not merged) | Partial |
| People and responsibilities | [`team-and-process.md`](../Process/team-and-process.md) §2 and the guide's owners table | Exists |
| Work methodology | [`team-and-process.md`](../Process/team-and-process.md), contributing guidelines in [`Docs/README.md`](../README.md) | Exists |
| Definition of Ready | [`definition-of-ready-and-done.md`](../Process/definition-of-ready-and-done.md) | Exists |
| Definition of Done | [`definition-of-ready-and-done.md`](../Process/definition-of-ready-and-done.md) | Exists |

## Decisions

| Topic | Decision | Applied in |
|---|---|---|
| Diagrams | Physical level 4 and view mappings removed; physical level 3 shows five databases | Diagrams, [ADR-009](../Technical/adr/0009-architecture-documentation.md) and [`architecture.md`](../Technical/architecture.md) §14: separate PR (Bruno) |
| Transport data | Poll every x seconds, store the result, users read only from the database, warning after y | Already in ADR-004 and D2. Values of x and y: guide J1 and D2, Tiago |
| Cost estimation | Salaries + infrastructure, simple assumptions | New item, owner to assign |

## Open actions

- [ ] **Bruno:** delete `physical-view-level-4-*` and `mapping-*`; show the five databases in `physical-view-level-3`; update ADR-009 and `architecture.md` §14 (P1).
- [ ] **Tomás:** close K1 (own machine or API key) with ADR-005 (P2).
- [ ] **Tiago:** set x (poll interval) and y (freshness limit) for the transport data (P4, guide J1 and D2).
- [ ] **Owner to assign:** the presentation deck (P5).
- [ ] **Owner to assign:** the single PDF with the Sprint A documents (P6).
- [ ] **Owner to assign:** the operating cost estimation, salaries + infrastructure (P7). The infrastructure part can reuse Tomás's research in #53 (G2).
- [ ] **Owner to assign:** the walking skeleton (P8).
- [ ] **Owner to assign:** one list of assumptions and dependencies, and one list of NFRs, for the deck.
- [ ] **Owner to assign:** simulator of the Porto Digital API or recorded responses (guide J4, still without an owner).

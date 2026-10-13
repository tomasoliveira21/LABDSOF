# Sprint 1 Presentation · Teacher Feedback

> **Date:** 2026-10-12 · **Context:** feedback from the teacher on the first presentation (Sprint 1) and on the documentation.
> **Background:** decisions before the presentation in [`2026-10-08-sprint-1-gap-review.md`](2026-10-08-sprint-1-gap-review.md); what changed in [`CHANGELOG.md`](../../CHANGELOG.md).

## Feedback received

| # | Feedback | Action | Status |
|---|---|---|---|
| T1 | **DoR:** it is the set of criteria for an issue to start being worked on. When the backlog is built, **every issue must meet the DoR** | When the user stories (US-01 to US-26) go to GitHub Projects, each one is checked against the DoR before it is moved to *Ready*. The issue form already asks for the DoR fields ([`definition-of-ready-and-done.md`](../Process/definition-of-ready-and-done.md)) | Open: to do when the backlog is loaded |
| T2 | The **main workflows and user journeys need a BPMN diagram**, showing a complete journey: what happens from start to end | Two BPMN diagrams, one lane per actor: help request (W2 + W3) and community report (W7) | Done: [`user-workflows.md`, *Complete journeys*](../Product/user-workflows.md#complete-journeys-bpmn), files in `Docs/Product/bpmn/` |
| T3 | **Be careful with BPMN in the presentation**: it is hard to read on a slide | BPMN stays in the documentation. If a journey goes on a slide, it is a simplified version | Agreed |
| T4 | **Change the presentations so they do not look so AI-made** | Applies to the next presentation. The first deck is not changed (see *Decisions*) | Open: next presentation |
| T5 | **Remove mentions of and integrations with 112** | Removed from the product, technical and deliverable documents. Where a document said the app does not replace 112, it now says the app is not an emergency service | Done |
| T6 | **Only feedback after the presentation:** explain better **who receives the reports and how they are handled** | The incident operator is split into the operator in the control room, who decides, and the responder in the field, who acts. New section on who receives what, report statuses, and the report journey in BPMN | Done, **[PROPOSED]**, to confirm with the team (PE-6) |

## Decisions

| Topic | Decision | Applied in |
|---|---|---|
| First deck | `Farol_First_Presentation.pptx` stays as presented; the feedback is applied in the documents | — |
| 112 | No mention of 112 and no integration with it in the product | `features.md` F6, US-08; `user-workflows.md` W2, W6, W7; `personas.md`; `architecture.md` §2, §9; the three deliverables |
| Who receives what **[PROPOSED]** | **Help requests** go to the control room first (one shared queue): the operator decides the priority and team, then dispatches the request or handles it remotely. **Reports** go straight to the operators of the team responsible for their type. The operator confirms or rejects a report, if needed after a responder checks it on site, and sends a responder when the problem needs fixing | `features.md` §4.1, §6.2 |
| New persona and role **[PROPOSED]** | **P5 · Inês, the responder**: a response-team member in the field, on the phone. Sees only what is dispatched to her team; accepts, goes, resolves. New event role `Responder` and story US-26 | `personas.md`, `features.md` F4, F7, US-05, US-10, US-19, US-26, `domain-model.md`, `architecture.md` §2 |
| BPMN | `.bpmn` files (BPMN 2.0, editable in bpmn.io or Camunda Modeler) plus an `.svg` export shown in the document | `Docs/Product/bpmn/` |

## Open actions

- [ ] Team confirms the operator / responder split and the `Responder` role (personas **PE-6**).
- [ ] Check every user story against the DoR when the backlog is loaded into GitHub Projects (T1).
- [ ] Next presentation: rewrite the slides in the team's own words (T4); no full BPMN on the slides (T3).

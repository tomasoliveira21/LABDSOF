# Definition of Ready and Definition of Done

> LABDSOF 2026/27 · Sprint 1 · Assignment §8 (Development and Delivery Practices)
> **Status:** **[PROPOSED]**, for the team to agree. Before this document, the repository had only a PR checklist titled "Definition of Done" inside the README's PR template (README §7), and no Definition of Ready.
> Legend: see [`product-vision.md`](../Product/product-vision.md#legend).

## Why both

- **Definition of Ready (DoR):** a backlog item can enter a sprint only when the team understands it well enough to finish it in that sprint.
- **Definition of Done (DoD):** an item is finished only when it meets the same quality bar as every other item. It applies at three levels: the pull request, the backlog item (user story) and the sprint increment.

---

## Definition of Ready (backlog item)

A user story or task is *Ready* when:

- [ ] It is written as a user story (*As a … I want … so that …*) or as a task with a clear outcome
- [ ] It names the persona it serves (P1, P2 or P3, see [`personas.md`](../Product/personas.md)) or the technical goal
- [ ] It has acceptance criteria in *Given / When / Then* that can be tested
- [ ] It is estimated by the team and fits in one sprint; otherwise it is split
- [ ] Dependencies (other stories, external data, decisions) are identified, and none of them blocks it
- [ ] It has a tracking issue (GitHub Issue or Jira) whose number will go in the branch and commits (README §1, §3)
- [ ] If it changes the UI: a sketch or wireframe is attached
- [ ] If it handles personal or sensitive data (location, help requests): the privacy impact is noted (report §5, regulatory risks)
- [ ] If it uses AI: the fallback behaviour is described (assignment §5.6.2)

## Definition of Done

### Level 1 · Pull request

Already in the README (§7, PR template), kept as it is:

- [ ] I reviewed my own diff before requesting a review
- [ ] CI pipeline is green
- [ ] Commits follow the conventions
- [ ] Tests added/updated and passing
- [ ] Documentation updated (if applicable)
- [ ] No secrets committed

Plus the review rules in README §5: Copilot review addressed, one approval from another team member, all conversations resolved.

### Level 2 · Backlog item (user story) **[PROPOSED]**

A story is *Done* when:

- [ ] All acceptance criteria pass
- [ ] Every PR for the story meets the Level 1 checklist and is merged into `main`
- [ ] Automated tests cover the acceptance criteria at the appropriate level (unit, integration or end-to-end; assignment §8.3)
- [ ] Authorisation is tested where the story touches event data: a user from another event cannot read or change it (report §5, technical risks)
- [ ] Logs for the new behaviour are structured and carry a correlation ID (assignment §6.7)
- [ ] It is deployed to the shared environment through the pipeline, not by hand (assignment §8.4, §8.5)
- [ ] The Product Owner, or whoever holds that role, has accepted it
- [ ] The Jira or GitHub issue is closed and linked to the PR

### Level 3 · Sprint increment **[PROPOSED]**

A sprint is *Done* when:

- [ ] Every story committed to the sprint is Done, or explicitly moved back to the backlog with a reason
- [ ] The increment runs end to end in the deployed environment
- [ ] Architecture documentation and decision records are updated (assignment §7.2.2, item 14)
- [ ] Known limitations and technical debt are recorded
- [ ] Investor (teaching staff) feedback from the review is recorded in Jira (assignment §8.2)

> **Note:** several items (CI, deployment, logs) cannot be met yet because there is no code or pipeline in the repository. They apply from the first story of the walking skeleton onwards (assignment §7.1.2, item 10).

---

## Open items

> ⚠️ **GAP: team agreement.** Needed: the team reviews and agrees this DoR and DoD (e.g. in sprint planning), and records the decision.
>
> ⚠️ **GAP: the DoR and DoD in the tool.** Needed: the DoR as a checklist on the Jira issue template (or GitHub issue template), and the DoD as the column rule for "Done" on the board.
>
> ⚠️ **GAP: who accepts stories.** Level 2 depends on someone in the Product Owner role. See [`team-and-process.md`](team-and-process.md): roles are not assigned yet.

# Definition of Ready and Definition of Done

> LABDSOF 2026/27 · Sprint A (Sprint 1) · Assignment §8 (Development and Delivery Practices)
> **Status:** agreed in the team meeting of 8 October 2026 ([minutes](../Meetings/2026-10-08-sprint-1-gap-review.md)).
> Legend: see [`product-vision.md`](../Product/product-vision.md#legend).

## Why both

- **Definition of Ready (DoR):** a backlog item can enter a sprint only when the team understands it well enough to start it.
- **Definition of Done (DoD):** an item is finished only when it meets the same quality bar as every other item. It applies at two levels: the pull request and the user story.

**Rule: one user story = one backlog item (GitHub issue) = one pull request.** A story that needs more than one PR is too big and must be split into smaller stories.

---

## Definition of Ready (backlog item)

A user story or task is *Ready* when:

- [ ] It is written as a user story (*As a … I want … so that …*) or as a task with a clear outcome
- [ ] It names the persona it serves (P1, P2 or P3, see [`personas.md`](../Product/personas.md)) or the technical goal
- [ ] It has acceptance criteria in *Given / When / Then* that can be tested
- [ ] Dependencies (other stories, external data, decisions) are identified, and none of them blocks it
- [ ] It has a GitHub issue on the GitHub Projects board, whose number goes in the branch and commits (README §1, §3)
- [ ] If it uses AI: the fallback behaviour is described (assignment §5.6.2)

## Definition of Done

### Level 1 · Pull request

From the README (§7, PR template):

- [ ] I reviewed my own diff before requesting a review
- [ ] CI pipeline is green
- [ ] Commits follow the conventions
- [ ] Tests added/updated and passing
- [ ] Documentation updated (if applicable)
- [ ] No secrets committed

Plus the review rules in README §5: Copilot review addressed, one approval from another team member, all conversations resolved.

### Level 2 · User story

A story is *Done* when its PR is merged and:

- [ ] All acceptance criteria pass
- [ ] Automated tests cover the acceptance criteria at the appropriate level (unit, integration or end-to-end; assignment §8.3)
- [ ] Authorisation is tested where the story touches event data: a user from another event cannot read or change it (report §5, technical risks)
- [ ] Logs for the new behaviour are structured and carry a correlation ID (assignment §6.7)
- [ ] It is deployed to the shared environment through the pipeline, not by hand (assignment §8.4, §8.5)
- [ ] The Product Owner has accepted it
- [ ] The GitHub issue is closed by the PR (`Closes #<issue>`)

> **Note:** several items (CI, deployment, logs) cannot be met yet because there is no code or pipeline in the repository. They apply from the first story of the walking skeleton onwards (assignment §7.1.2, item 10).

---

## In the tool

| Rule | Where it lives |
|---|---|
| DoR | Issue form [`.github/ISSUE_TEMPLATE/user-story.yml`](../../.github/ISSUE_TEMPLATE/user-story.yml): the story, persona, acceptance criteria and dependencies are required fields, and the DoR is a checklist at the end. Work that is not a story uses [`task.yml`](../../.github/ISSUE_TEMPLATE/task.yml). Blank issues are disabled |
| DoD, Level 1 (PR) | [`.github/pull_request_template.md`](../../.github/pull_request_template.md), plus the **Conventions** check (`.github/workflows/conventions.yml`) |
| DoD, Level 2 (story) | Checklist pre-filled in every user-story issue, ticked before the item moves to "Done" |
| Board | GitHub Projects *Status* field: `Backlog → Ready → In progress → In review → Done`. The *Ready* option is described as "Meets the DoR" and *Done* as "Meets the DoD", both linking to this page |

> ⚠️ **Board status (checked 24 October 2026, #138).**
> **Done:** the [board](https://github.com/users/tomasoliveira21/projects/1) exists with the *Status* options above, the *Priority* (P0–P2) and *Size* (XS–XL) fields, and the built-in workflows on (auto-add, item added, item closed, pull request linked, pull request merged); issues #58 to #121 are on it, and its link is in the [README](../README.md). *Priority*, *Size* and *Status* applied to #58 to #121 with the rules of [`features.md` §8.3](../Product/features.md#83-stories-on-the-board) (approved by Tiago, backlog owner, 24 October 2026). The sprint is the issue's milestone (*Sprint A*, *B*, *C*) instead of an *Iteration* field; *Sprint A* is due on 1 November 2026. The *Ready* and *Done* options are described as "Meets the DoR" and "Meets the DoD", with a link to this page.
> **Still to do:**
> - Tick the DoR box "On the GitHub Projects board" in each story issue.
> - The board is private: give the professor access, or make it public (repository owner).

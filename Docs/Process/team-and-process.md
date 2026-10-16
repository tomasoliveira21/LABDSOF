# Team Roles and Work Process

> LABDSOF 2026/27 · Sprint A (Sprint 1) · Assignment §8.1, §8.2 and §10
> **Status:** agreed in the team meeting of 8 October 2026 ([minutes](../Meetings/2026-10-08-sprint-1-gap-review.md)). The work process is defined in the [README](../README.md) and summarised here. Roles are identified by what they do. People are not assigned to them on purpose: everyone codes, reviews and can take any role.
> Legend: see [`product-vision.md`](../Product/product-vision.md#legend).

## 1. Team

| Member | GitHub | Student Number | Contributions so far (from commits) |
|---|---|---|---|
| Tomás Oliveira | `tomasoliveira21` | 1230213 | Repository owner; initial commit; Product Vision and User Research sections of the report (#17); merged PR #19 |
| Tiago Teixeira | `tteixeira30` | 1240493 | Problem and Opportunity Report (#11); student survey script (#11); merged PR #18 and #21; added the assignment PDF |
| Bruno de Abreu Lopes | `BrunoLopes1190` | 1240441 | Contributing guidelines (README); market and competitor analysis; merged PR #20 |
| António Guerra | `AntonioGuerra1190409` | 1190409 | Product Vision and User Research sections of the report (#17); |

## 2. Roles

The assignment asks the team to work as a startup (§1) and evaluates each student individually, including their contribution and understanding of the whole product (§10). These are the roles the project needs and what each one does. Roles are **not assigned to a member**: any member can take a role for a given task, and everyone writes code and reviews.

| Role | What it does | Focus in this project |
|---|---|---|
| **Product Owner** | Owns the backlog and its priority; accepts stories (DoD); speaks for the users; records investor feedback | Personas, success criteria, survey and interview |
| **Scrum Master / facilitator** | Runs the meetings, keeps the GitHub Projects board up to date, removes blockers, watches the process conventions | Board, sprint goals, retrospective actions |
| **Tech lead / architect** | Owns the architecture views and the ADRs; makes sure technical decisions are justified | Architecture, domain model, API outline |
| **QA lead** | Owns the test strategy and the quality evidence (assignment §8.3) | Test strategy, authorisation-isolation tests, AI evaluation dataset |
| **DevOps lead** | Owns the pipeline, containers, deployment and observability (assignment §8.4, §8.5, §6.7) | CI/CD, Docker, logs, health checks, dashboard |
| **AI lead** | Owns the AI-assisted workflow, its evaluation and the Responsible AI assessment (assignment §5.6) | AI help-request triage, fallback, evaluation report |

## 3. Work process

Defined in the README; summarised here for the presentation.

| Area | Convention | Source |
|---|---|---|
| Workflow | **GitHub Flow**: every piece of work starts from an issue; short-lived branch from `main`; PR to `main`; squash merge; branch deleted. `main` is always deployable, no direct pushes | README §1 |
| Branches | `<prefix>/<short-description>`, prefixes `feature/`, `bugfix/`, `refactor/`, `test/`, `docs/`, `ci/`, `chore/` | README §2 |
| Commits | `<prefix> (#<issue>): <Title>`, title ≤ 50 characters, imperative mood | README §3 |
| Pull requests | Title `<Prefix>: <Description>`; description with `Closes #<issue>`, summary and how it was tested. **Every PR has an issue** | README §4 |
| Code review | Two reviews: GitHub Copilot (automatic) and one team member; review within 1 working day; blocking comments start with `blocking:` | README §5 |
| Branch protection | No direct pushes, Copilot review, 1 approval, stale approvals dismissed, conversations resolved, CI must pass, branch up to date | README §6 |
| Definition of Ready / Done | See [`definition-of-ready-and-done.md`](definition-of-ready-and-done.md). One user story = one issue = one PR | — |
| Language | All branches, commits, PRs and repository documentation in **English**. Exceptions: survey questions and presentation speaker notes, which are for a Portuguese audience (decided 12 October 2026) | README (introduction) |

### Sprints and meetings

The assignment fixes **three sprints** (§1, §7). The team calls them A, B and C.

| Sprint | Objective (assignment §7) | Dates |
|---|---|---|
| Sprint A (1) | Discover, validate and design | Until the review on **12 October 2026** |
| Sprint B (2) | Build and operate the vertical slice | **2 November 2026 – 6 December 2026** |
| Sprint C (3) | Evaluate, harden and present | **7 December 2026 – 3 January 2027** |

The team meets **twice a week**:

| Meeting | When | Output |
|---|---|---|
| Planning | Weekly, during the class | Week's goal; Ready stories pulled from the backlog; at sprint start, the sprint goal; at sprint end, a short retrospective (1–3 actions) |
| Stand-up | Weekly, at the weekend | Progress and blockers, updated on the board |
| Sprint review (with investors) | End of each sprint | Feedback recorded on the board; at least one decision revised in response (assignment §5.7) |

### Tools

| Tool | Use | Status |
|---|---|---|
| GitHub (`tomasoliveira21/LABDSOF`) | Code, documentation, issues, pull requests, reviews | In use |
| **GitHub Projects** | Backlog, sprints, decisions, sprint goals, investor feedback (the "Jira or equivalent" of assignment §8.2) | Chosen on 8 October 2026; link to add to the README |
| GitHub Copilot | Automatic PR review | Defined in README §5 |

## 4. Process health check (what the repository shows today)

| Check | Finding | Action (agreed 8 October 2026) |
|---|---|---|
| Branch protection on `main` | Every item in README §6 is an unticked checkbox. The history also shows commits made directly on `main` without a PR (`5ef2c4a` and `3a4ba04` on 24 September, `e853854` "Project Assignment" on 8 October), so "no direct pushes" is not enforced | Tomás (repo owner) turns the rules on in the repository settings, then ticks the boxes in README §6 |
| Merge method | README §4 requires **squash merge**, but PRs #18 to #21, #23, #25, #27 and #30 were merged with merge commits | Tomás restricts the repository to squash merging (Settings → General → Pull Requests) |
| PR template | README §7 says to save it as `.github/pull_request_template.md`, but the file did not exist | Created in PR #21 |
| Issue templates | The DoR and the story-level DoD were not in the tool | Since 12 October 2026: issue forms in `.github/ISSUE_TEMPLATE/` (user story and task) |
| Commit message format | Several commits on `main` have no issue number or prefix (`3a4ba04`, `5159ff4`, `5ef2c4a`, `e853854`, and the commits of PR #21; later `58fb4e0`). Branch `feat/UpdateProductVisionPP` (PR #30) uses a prefix that does not exist | Agreed: every PR has an issue, and PR titles follow `<Prefix>: <Description>`. With squash merging, the PR title becomes the commit on `main`. Since 12 October 2026 the **Conventions** workflow (`.github/workflows/conventions.yml`) checks the branch name, the PR title and the `Closes #<issue>` line on every PR |
| Documentation language | The README requires English, but the three deliverables in `Docs/Deliverables/` are in Portuguese | **Decided 12 October 2026:** all documentation is in English and stays that way. The three deliverables were translated |

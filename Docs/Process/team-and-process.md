# Team Roles and Work Process

> LABDSOF 2026/27 · Sprint 1 · Assignment §8.1, §8.2 and §10
> **Status:** the **work process** is already defined in the [README](../../README.md) and is summarised here. **Team roles are not defined anywhere in the repository**: this document proposes a structure and leaves the names for the team to fill in.
> Legend: see [`product-vision.md`](../Product/product-vision.md#legend).

## 1. Team

Members identified from the repository's commit history (authors of commits on `main` up to 8 October 2026):

| Member | GitHub | Contributions so far (from commits) |
|---|---|---|
| Tomás Oliveira | `tomasoliveira21` | Repository owner; initial commit; Product Vision and User Research sections of the report (#17); merged PR #19 |
| Tiago Teixeira | `tteixeira30` | Problem and Opportunity Report (#11); student survey script (#11); merged PR #18; added the assignment PDF |
| Bruno de Abreu Lopes | — | Contributing guidelines (README); market and competitor analysis; merged PR #20 |

> ⚠️ **GAP: full team list.** Members who have not committed yet do not appear in the history, so this list may be incomplete. Needed: the complete list of team members.

## 2. Roles **[PROPOSED]**

The assignment asks the team to work as a startup (§1) and evaluates each student individually, including their contribution and understanding of the whole product (§10). The roles below are suggestions; one person can hold more than one, and everyone still writes code and reviews.

| Role | Responsibility | Suggested focus in this project | Member |
|---|---|---|---|
| **Product Owner** | Owns the backlog and its priority; accepts stories (DoD level 2); speaks for the users; records investor feedback | Personas, success criteria, survey and interview | **[DECISION NEEDED]** |
| **Scrum Master / facilitator** | Runs the ceremonies, keeps the board up to date, removes blockers, watches the process conventions | Jira, sprint goals, retrospective actions | **[DECISION NEEDED]** |
| **Tech lead / architect** | Owns the architecture views and the ADRs; makes sure technical decisions are justified | Architecture, domain model, API outline | **[DECISION NEEDED]** |
| **QA lead** | Owns the test strategy and the quality evidence (assignment §8.3) | Test strategy, authorisation-isolation tests, AI evaluation dataset | **[DECISION NEEDED]** |
| **DevOps lead** | Owns the pipeline, containers, deployment and observability (assignment §8.4, §8.5, §6.7) | CI/CD, Docker, logs, health checks, dashboard | **[DECISION NEEDED]** |
| **AI lead** | Owns the AI-assisted workflow, its evaluation and the Responsible AI assessment (assignment §5.6) | AI triage (if chosen), fallback, evaluation report | **[DECISION NEEDED]** |

> ⚠️ **GAP: roles not assigned.** Needed: the team assigns each role to a member (or merges roles if the team is small) and fills the *Member* column.

## 3. Work process

Defined in the README; summarised here for the presentation.

| Area | Convention | Source |
|---|---|---|
| Workflow | **GitHub Flow**: every piece of work starts from an issue; short-lived branch from `main`; PR to `main`; squash merge; branch deleted. `main` is always deployable, no direct pushes | README §1 |
| Branches | `<prefix>/<short-description>`, prefixes `feature/`, `bugfix/`, `refactor/`, `test/`, `docs/`, `ci/`, `chore/` | README §2 |
| Commits | `<prefix> (#<issue>): <Title>`, title ≤ 50 characters, imperative mood | README §3 |
| Pull requests | Title `<Prefix>: <Description>`; description with `Closes #<issue>`, summary and how it was tested | README §4 |
| Code review | Two reviews: GitHub Copilot (automatic) and one team member; review within 1 working day; blocking comments start with `blocking:` | README §5 |
| Branch protection | No direct pushes, Copilot review, 1 approval, stale approvals dismissed, conversations resolved, CI must pass, branch up to date | README §6 |
| Definition of Ready / Done | See [`definition-of-ready-and-done.md`](definition-of-ready-and-done.md) | New |
| Language | All branches, commits, PRs and repository documentation in **English** | README (introduction) |

### Sprints and ceremonies **[PROPOSED]**

The assignment fixes **three sprints** (§1, §7):

| Sprint | Objective (assignment §7) | Dates |
|---|---|---|
| Sprint 1 | Discover, validate and design | Review on **12 October 2026** |
| Sprint 2 | Build and operate the vertical slice | **[DECISION NEEDED]** |
| Sprint 3 | Evaluate, harden and present | **[DECISION NEEDED]** |

| Ceremony | When | Output |
|---|---|---|
| Sprint planning | Start of each sprint | Sprint goal; stories that meet the DoR pulled into the sprint |
| Stand-up (async allowed) | 2–3 times a week | Blockers raised on the board |
| Backlog refinement | Once per sprint, mid-sprint | Stories made Ready for the next sprint |
| Sprint review (with investors) | End of each sprint | Feedback recorded in Jira; at least one decision revised in response (assignment §5.7) |
| Retrospective | After each review | 1–3 actions, tracked on the board |

> ⚠️ **GAP: sprint dates and ceremony schedule.** Needed: dates for Sprints 2 and 3 and the team's actual meeting rhythm.

### Tools

| Tool | Use | Status |
|---|---|---|
| GitHub (`tomasoliveira21/LABDSOF`) | Code, documentation, issues, pull requests, reviews | In use |
| Jira or equivalent | Backlog, sprints, assignments, decisions, sprint goals, investor feedback (assignment §8.2, mandatory) | **Not referenced in the repository** |
| GitHub Copilot | Automatic PR review | Defined in README §5 |

> ⚠️ **GAP: project-management board.** The assignment requires Jira or an equivalent (§8.2). Needed: a link to the board in the README, or a note that GitHub Projects is used instead.

## 4. Process health check (what the repository shows today)

| Check | Finding | Action |
|---|---|---|
| Branch protection on `main` | Every item in README §6 is an unticked checkbox. The history also shows commits made directly on `main` without a PR (`5ef2c4a` and `3a4ba04` on 24 September, `e853854` "Project Assignment" on 8 October), so "no direct pushes" is not enforced | Turn the rules on in the repository settings, then tick the boxes in README §6 |
| Merge method | README §4 requires **squash merge**, but PRs #18, #19 and #20 were merged with merge commits | Restrict the repository to squash merging (Settings → General → Pull Requests) |
| PR template | README §7 says to save it as `.github/pull_request_template.md`, but the file did not exist | Created in this change (see CHANGELOG) |
| Commit message format | Excluding the initial commit, 3 of the 7 non-merge commits follow `<prefix> (#<issue>): <Title>` (`fd703b2`, `574e459`, `8e007ca`). The others have no issue number: `3a4ba04` and `5159ff4` use the PR-title format ("Docs: …"), `5ef2c4a` and `e853854` have no prefix | Follow the convention for new work. Squash merging also fixes this on `main`, because the PR title becomes the commit |
| Documentation language | The README requires English, but the three deliverables in `Docs/Deliverables/` are in Portuguese | **[DECISION NEEDED]** Either translate them or change the rule to allow Portuguese deliverables |

# Team Roles and Work Process

> LABDSOF 2026/27 · Sprint A (Sprint 1) · Assignment §8.1, §8.2 and §10
> **Status:** agreed in the team meeting of 8 October 2026 ([minutes](../Meetings/2026-10-08-sprint-1-gap-review.md)). The work process is defined in the [README](../README.md) and summarised here. Roles are identified by what they do and are not assigned to people: everyone codes, reviews and can take any role. Each member owns areas instead (§1).
> Legend: see [`product-vision.md`](../Product/product-vision.md#legend).

## 1. Team

Areas from the pull requests each member wrote and from the owners table of the [decisions guide](../Technical/architecture-decisions-guide.md#responsáveis-pelas-investigações-em-curso). Counts on 31 October 2026, from GitHub (46 merged pull requests): pull requests written and merged, and pull requests the member approved.

| Member | GitHub | Student Number | Areas (pull requests) | PRs merged | PRs approved |
|---|---|---|---|---:|---:|
| Tomás Oliveira | `tomasoliveira21` | 1230213 | Repository owner. Product vision (#19, #30); domain model, with Guerra (#37, #171); architecture research on deployment, observability and AI (#54, #146, #151, #152, #154); walking skeleton (#150, #167); CI with SonarQube Cloud (#169) | 13 | 14 |
| Tiago Teixeira | `tteixeira30` | 1240493 | Problem and Opportunity Report and survey script (#18); Sprint 1 presentation and personas (#21, #27); venue map, ADR-007 (#51); resilience, degraded modes and load testing (#52); role model (#57); backlog issues and board (#122, #140, #142); non-functional requirements (#139). Survey results and the Sprint A deck in PR #165, open | 10 | 5 |
| Bruno de Abreu Lopes | `BrunoLopes1190` | 1240441 | Contributing guidelines and process (#1, #33); market and competitor analysis (#20); main features (#25); decisions guide and ADR-002/003/004 (#36, #49); architecture diagrams and ADR-009 (#50, #157); meeting records (#23, #96); API outline (#124); one web app (#126); versioning, local delivery and ADR-010 CI/CD (#128, #130, #132); operating costs (#136); account deletion (#158); documentation corrections (#163) | 18 | 8 |
| António Guerra | `AntonioGuerra1190409` | 1190409 | User workflows slide (#31); identity and API gateway, ADR-006 and ADR-013 (#47); security and privacy assessment (#144, #160); Porto Digital simulator in WireMock (#156) | 5 | 9 |

## 2. Roles

The assignment asks the team to work as a startup (§1) and evaluates each student individually, including their contribution and understanding of the whole product (§10). These are the roles the project needs and what each one does. Roles are **not assigned to a member**: any member can take a role for a given task, and everyone writes code and reviews. Responsibility is held by area instead: each member owns the areas in §1, as in the owners table of the decisions guide.

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
| Issues | Title `[<Type>] <Title>`, e.g. `[Documentation] Architecture decisions`. Types: `[Documentation]`, `[Back Office]`, `[Front Office]`, `[Technicians]` | README §1 |
| Branches | `<prefix>/<short-description>`, prefixes `feature/`, `bugfix/`, `refactor/`, `test/`, `docs/`, `ci/`, `chore/` | README §2 |
| Commits | `<prefix> (#<issue>): <Title>`, title ≤ 50 characters, imperative mood | README §3 |
| Pull requests | Title `<Prefix>: <Description>`; description with `Closes #<issue>`, summary and how it was tested. **Every PR has an issue** | README §4 |
| Code review | Two reviews: GitHub Copilot (automatic) and one team member; review within 1 working day; blocking comments start with `blocking:` | README §5 |
| Branch protection | No direct pushes, Copilot review, 1 approval, stale approvals dismissed, conversations resolved, CI must pass, branch up to date. **Not active yet** (checked on 30 October 2026: `protected: false`). **To do: Tomás turns it on** (§4) | README §6 |
| Definition of Ready / Done | See [`definition-of-ready-and-done.md`](definition-of-ready-and-done.md). One user story = one issue = one PR | — |
| Language | All branches, commits, PRs and repository documentation in **English**. Exceptions: survey questions and presentation speaker notes, which are for a Portuguese audience (decided 12 October 2026) | README (introduction) |

### Sprints and meetings

The assignment fixes **three sprints** (§1, §7). The team calls them A, B and C.

| Sprint | Objective (assignment §7) | Dates |
|---|---|---|
| Sprint A (1) | Discover, validate and design | Until **1 November 2026** (milestone *Sprint A*; the Sprint 1 presentation was on 12 October 2026) |
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
| **GitHub Projects** | Backlog, sprints, decisions, sprint goals, investor feedback (the "Jira or equivalent" of assignment §8.2) | Chosen on 8 October 2026; in use: [board](https://github.com/users/tomasoliveira21/projects/1), linked from the README; field rules in [`features.md` §8.3](../Product/features.md#83-stories-on-the-board) |
| GitHub Copilot | Automatic PR review | Defined in README §5 |

### Deployment scope clarification (recorded 22 October 2026)

Tomás reported that the professor authorised **local-only delivery because of cloud costs**, which must be explained in the final report/presentation ([record](../Meetings/2026-10-22-local-deployment-clarification.md), [ADR-011](../Technical/adr/0011-deployment-and-infrastructure.md)). Keep the cloud cost/hosting evaluation as justification. Builds, tests, independently replaceable service images and local smoke/failure demonstrations remain required; the earlier automatic cloud-production deployment expectation is superseded. Bruno adapts the local delivery handoff; no unattended access to a developer's machine is assumed.

## 4. Process health check (what the repository shows today)

| Check | Finding | Action (agreed 8 October 2026) |
|---|---|---|
| Branch protection on `main` | Every item in README §6 is an unticked checkbox, and protection is still off (checked on 30 October 2026: the GitHub API returns `protected: false` for `main`). There were **no direct pushes**: every commit on `main` came through a PR, except the initial commit (checked on 30 October 2026). The commits once listed here as direct pushes belong to PRs: `5ef2c4a` and its merge commit `3a4ba04` to PR #1, `e853854` to PR #21 | Tomás (repo owner) turns the rules on in the repository settings, then ticks the boxes in README §6 |
| Merge method | README §4 requires **squash merge**, but PRs #18 to #21, #23, #25, #27 and #30 were merged with merge commits | Tomás restricts the repository to squash merging (Settings → General → Pull Requests) |
| PR template | README §7 says to save it as `.github/pull_request_template.md`, but the file did not exist | Created in PR #21 |
| Issue templates | The DoR and the story-level DoD were not in the tool | Since 12 October 2026: issue forms in `.github/ISSUE_TEMPLATE/` (user story and task) |
| Commit message format | Several commits on `main` have no issue number or prefix (`3a4ba04`, `5159ff4`, `5ef2c4a`, `e853854`, and the commits of PR #21; later `58fb4e0`). Branch `feat/UpdateProductVisionPP` (PR #30) uses a prefix that does not exist | Agreed: every PR has an issue, and PR titles follow `<Prefix>: <Description>`. With squash merging, the PR title becomes the commit on `main`. Since 12 October 2026 the **Conventions** workflow (`.github/workflows/conventions.yml`) checks the branch name, the PR title and the `Closes #<issue>` line on every PR |
| Documentation language | The README requires English, but the three deliverables in `Docs/Deliverables/` are in Portuguese | **Decided 12 October 2026:** all documentation is in English and stays that way. The three deliverables were translated |

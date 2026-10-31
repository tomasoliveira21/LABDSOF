# LABDSOF

Multi-event coordination platform for large academic festivals (pilot case: Queima das Fitas do Porto). LABDSOF 2026/27, ISEP. Assignment: [`LABDSOF-26-27-Assignment.pdf`](../LABDSOF-26-27-Assignment.pdf).

## Documentation

### Sprint A deliverables

One row per deliverable of assignment §7.1.2, with its real status on 31 October 2026 (#162). ✅ complete · ⚠️ partial: the sentence says what is missing · ❌ missing.

| # | Deliverable (assignment §7.1.2) | Where | Status |
| --- | --- | --- | --- |
| 1 | Problem and Opportunity Report: target community, problem statement, stakeholder map, problem evidence, assumptions and risks | [Problem and Opportunity Report](Deliverables/problem-opportunity-report-queima.md) §1–§5 · [Assumptions and dependencies](Deliverables/assumptions-and-dependencies.md) | ✅ All five parts written |
| 2 | Market and Competitor Analysis: relevant alternatives, strengths and weaknesses, opportunity for differentiation | [Market and Competitor Analysis](Deliverables/market-and-competitor-analysis.md) §2–§3 · [Product-specific annex](Deliverables/market-and-competitor-analysis-product-specific.md) | ✅ |
| 3 | User Research: research method, findings, limitations, resulting product decisions | [Problem report, *User Research*](Deliverables/problem-opportunity-report-queima.md#user-research) · [Survey script](Research/inquerito-estudantes-google-forms.gs) · [Research status](Product/product-vision.md#8-research-status) | ❌ Only the method is written. Findings, limitations and resulting decisions are "TBD", and no survey results are in the repository; they are in PR #165 (Tiago), not merged |
| 4 | Product Vision: personas, value proposition, principal user journeys, success criteria, MVP definition | [Product Vision](Product/product-vision.md) (§4 value, §5 success criteria, §6 MVP) · [Personas](Product/personas.md) · [User Workflows](Product/user-workflows.md) | ⚠️ All parts written; the personas are proto-personas, not yet checked against user research |
| 5 | Product Backlog: prioritised stories, acceptance criteria, sprint objectives, initial release plan | [Main Features and Backlog](Product/features.md) (§2 priority, §7 stories, §8 sprint goals and release plan) · [Backlog board](https://github.com/users/tomasoliveira21/projects/1) | ⚠️ 62 stories with acceptance criteria, one issue each; the sprint goals and the release plan are still marked [PROPOSED] |
| 6 | Responsible AI Opportunity Assessment: AI-assisted workflow, expected value, risks, evaluation approach, baseline or fallback | [Responsible AI assessment](Research/architecture-investigation/responsible-ai.md) · [ADR-005](Technical/adr/0005-ai-triage-provider-and-evaluation.md) · [Local AI decisions](Technical/plans/ai-provider-evaluation.md) · [Triage evaluation](Research/architecture-investigation/evaluation/README.md) · [`features.md` §5](Product/features.md#5-ai-assisted-workflow-help-request-triage) | ✅ All parts written, with a reproducible non-AI baseline; no model run yet (guide K1, K2) |
| 7 | Technical Design: system context, container or component view, data model, API outline, asynchronous workflow, external integration, deployment view | [High-Level Architecture](Technical/architecture.md) (§2 context, §3 containers, §6 asynchronous workflow, §7 external integration, §10 deployment, §14 diagrams) · [Domain Model](Technical/domain-model.md) · [API Outline](Technical/api-outline.md) · [Local delivery](Technical/local-delivery.md) | ✅ |
| 8 | Architecture Decision Records: choices, alternatives, rationale, consequences | [ADR-001 to ADR-013](Technical/architecture.md#13-architecture-decision-records) · [Decisions guide](Technical/architecture-decisions-guide.md) (PT) | ✅ 13 ADRs; ADR-007 (venue map) is still *Proposed*, waiting for Tiago's review |
| 9 | Security and Privacy Assessment: threat model, sensitive data, access control, data minimisation, retention | [Security and Privacy Assessment](Technical/security-and-privacy-assessment.md) §3–§7 | ✅ Written on 25 October 2026 (Guerra); retention and consent confirmed by the team on 26 October, the rest not yet reviewed by the team |
| 10 | Walking Skeleton: minimal end-to-end implementation, source repository, automated build, initial test, containerised deployment | [`backend/reports/`](../backend/reports/pom.xml) · [`infra/compose.yaml`](../infra/compose.yaml) · [`ci.yml`](../.github/workflows/ci.yml) · [Run instructions](Technical/plans/local-walking-skeleton.md) · [Evidence](Technical/evidence/walking-skeleton-2026-10-31.md) | ⚠️ One Reports service creates and reads synthetic help requests **in memory**; Maven build, 10 HTTP tests, a Docker image and a Compose smoke test run in CI on every PR and on `main` (#166, #167). No database, client app or second service yet: they are Sprint B |

Also asked for the deck by the professor (meeting of 22 October 2026): [Operating Cost Estimation](Deliverables/operating-cost-estimation.md), [Assumptions and dependencies](Deliverables/assumptions-and-dependencies.md), [Non-Functional Requirements](Product/non-functional-requirements.md), [Team and process](Process/team-and-process.md).

### All documents

| Area | Document | Language |
| --- | --- | --- |
| Problem | [Problem and Opportunity Report](Deliverables/problem-opportunity-report-queima.md) | EN |
| Market | [Market and Competitor Analysis](Deliverables/market-and-competitor-analysis.md) · [Product-specific annex](Deliverables/market-and-competitor-analysis-product-specific.md) | EN |
| Costs | [Operating Cost Estimation](Deliverables/operating-cost-estimation.md): salaries and infrastructure, and why delivery is local | EN |
| Assumptions | [Assumptions and Dependencies](Deliverables/assumptions-and-dependencies.md): one list for the deck | EN |
| Presentation | [Sprint 1 presentation, 12 October 2026](Presentation/Farol_First_Presentation.pptx) | EN (speaker notes in PT) |
| Research | [Student survey (Google Forms script)](Research/inquerito-estudantes-google-forms.gs) | EN (questions in PT) |
| Product | [Product Vision](Product/product-vision.md) · [Personas](Product/personas.md) · [Main Features and Backlog](Product/features.md) · [User Workflows](Product/user-workflows.md) · [Non-Functional Requirements](Product/non-functional-requirements.md) | EN |
| Process | [Team Roles and Work Process](Process/team-and-process.md) · [Definition of Ready and Done](Process/definition-of-ready-and-done.md) · [Spec-Driven Development rules](Process/spec-driven-development.md) (proposed) | EN |
| Backlog | [Backlog board (GitHub Projects)](https://github.com/users/tomasoliveira21/projects/1) (private, team members only) · how its fields are used: [`features.md` §8.3](Product/features.md#83-stories-on-the-board) | EN |
| Technical | [Domain Model](Technical/domain-model.md) · [High-Level Architecture](Technical/architecture.md) · [API Outline and Event Catalogue](Technical/api-outline.md) · [Sprint A skeleton: run](Technical/plans/local-walking-skeleton.md) and [evidence](Technical/evidence/walking-skeleton-2026-10-31.md) · [Local AI decisions](Technical/plans/ai-provider-evaluation.md) · [Local observability decisions](Technical/plans/local-observability.md) · [SonarQube Cloud CI setup](Technical/sonarqube-cloud.md) · [Local delivery and demo (Sprint B target)](Technical/local-delivery.md) · ADRs: [001](Technical/adr/0001-architectural-style.md) · [002](Technical/adr/0002-backend-and-frontend-technology.md) · [003](Technical/adr/0003-database-and-data-ownership.md) · [004](Technical/adr/0004-service-communication.md) · [006](Technical/adr/0006-identity-and-access.md) · [007](Technical/adr/0007-venue-map-representation.md) · [008](Technical/adr/0008-transport-data-source.md) · [009](Technical/adr/0009-architecture-documentation.md) · [010](Technical/adr/0010-cicd-strategy.md) · [013](Technical/adr/0013-api-gateway.md) · [Diagrams](Technical/architecture.md#14-architecture-diagrams) | EN |
| Security and privacy | [Security and Privacy Assessment](Technical/security-and-privacy-assessment.md): threat model, sensitive data, access control, data minimisation, retention, consent | EN |
| Architecture research | [Deployment, observability and AI](Research/architecture-investigation/README.md) ([deployment](Research/architecture-investigation/deployment.md), [observability](Research/architecture-investigation/observability.md)) · [Responsible AI assessment](Research/architecture-investigation/responsible-ai.md) · [Triage evaluation](Research/architecture-investigation/evaluation/README.md) · ADRs: [005, accepted architecture](Technical/adr/0005-ai-triage-provider-and-evaluation.md), [011, local delivery scope](Technical/adr/0011-deployment-and-infrastructure.md), [012, accepted architecture](Technical/adr/0012-observability.md) | EN |
| Technical (working document) | [Architecture decisions](Technical/architecture-decisions-guide.md): every architecture decision taken by the team, its status and owner, and next steps | PT |
| Meetings | [2026-10-08 Sprint A gap review and decisions](Meetings/2026-10-08-sprint-1-gap-review.md) · [2026-10-12 Presentation feedback](Meetings/2026-10-12-sprint-1-presentation-feedback.md) · [2026-10-22 Local deployment clarification](Meetings/2026-10-22-local-deployment-clarification.md) · [2026-10-22 Meeting with the professor](Meetings/2026-10-22-professor-meeting.md) · [2026-10-22 Identity and API gateway decisions](Meetings/2026-10-22-identity-and-gateway-decisions.md) | EN |
| History | [CHANGELOG](../CHANGELOG.md) | EN |

# Contributing Guidelines

These conventions keep the repository history clear and consistent, and make it easy to see when a change was made and who made it. All branches, commits, pull requests and repository documentation are written in **English** (team decision of 12 October 2026). The only exceptions are texts meant for a Portuguese audience: the survey questions and the presentation speaker notes.

## 1. Workflow

The team uses **GitHub Flow**.

1. Every piece of work starts from a **GitHub Issue** (`#<number>`). Issue title: `[<Type>] <Title>`, e.g. `[Documentation] Architecture decisions`. Types: `[Documentation]`, `[Back Office]`, `[Front Office]`, `[Technicians]`.
2. Create a short-lived branch from `main`.
3. Commit following the conventions below.
4. Open a pull request to `main`.
5. After approval and a green CI run, the PR is **squash-merged** and the branch is deleted.

`main` is always deployable. Direct pushes to `main` are not allowed. GitHub has enforced it since 30 October 2026: see §6.

## 2. Branch naming

Format: `<prefix>/<short-description>`

- Lowercase, kebab-case, short and descriptive.

| Prefix | Use for |
| --- | --- |
| `feature/` | New features or enhancements |
| `bugfix/` | Bug fixes |
| `refactor/` | Code restructuring with no behaviour change |
| `test/` | Adding or updating tests |
| `docs/` | Documentation updates |
| `ci/` | CI/CD pipelines and workflows |
| `chore/` | Dependencies, configuration, Docker, maintenance |

Example: `docs/add-conventions-to-global-report`

## 3. Commit messages

Format:

```
<prefix> (#<issue>): <Title>

[optional body]
```

| Rule | Detail |
| --- | --- |
| Prefix | Same as the branch prefix, without the slash (`feature`, `bugfix`, `refactor`, `test`, `docs`, `ci`, `chore`) |
| Issue | GitHub Issue number in parentheses, e.g. `(#1)` |
| Title | At most **50 characters**, first letter capitalised, imperative mood ("Add", not "Added"), no trailing full stop |
| Body | Optional. Separate it from the title with a blank line and explain *what* changed and *why* |

Example: `docs (#1): Add conventions chapter to global report`

## 4. Pull requests

| Item | Convention |
| --- | --- |
| Target | `main` |
| Title | `<Prefix>: <Concise description>` (capitalised prefix), e.g. `Docs: Add conventions to global report` |
| Description | Must reference the issue with `Closes #<issue>` so the issue closes automatically on merge. Include a summary of the changes and how they were tested |
| Merge method | **Squash merge**. The PR title becomes the commit on `main`, and GitHub adds the PR number automatically: `Docs: Add conventions to global report (#12)` |

## 5. Code review

A pull request can only be merged after **two reviews**:

1. **GitHub Copilot**: requested automatically when the PR is opened. *So far every Copilot review has failed for lack of quota (14 attempts on nine PRs up to #171, checked on 31 October 2026), so in practice only the team member's review has happened.*
2. **A team member**: 1 approval from someone other than the author.

### As the author

- One issue per PR. Keep it small.
- Review your own diff and wait for CI to pass before asking for a review.
- Address every Copilot comment: fix it, or reply explaining why not.
- Reply to every comment and resolve all conversations.
- Merge the PR yourself (squash) once both reviews are done.

### As the reviewer

- Review within **1 working day**.
- Read Copilot's comments first. Copilot helps, but does not replace your review.
- Be respectful: comment on the code, not the person.
- Check that:
  - it does what the issue asks
  - it works and has tests
  - it is easy to read
  - there are no secrets and no security issues
  - the conventions are followed
- Start blocking comments with **`blocking:`**. Everything else is a suggestion.
- **Approve** when no blocking comments remain; otherwise **Request changes**.

## 6. Branch protection on `main`

> **Active since 30 October 2026**, through the repository ruleset *[MAIN] Branch protection rules* (created at 18:43 UTC; checked on 31 October 2026: the GitHub API returns `protected: true` for `main`). The repository allows only squash merging. **Still to do: Tomás, the repository owner** (agreed on 8 October 2026, [`team-and-process.md` §4](Process/team-and-process.md#4-process-health-check-what-the-repository-shows-today)): the unticked items below. The list of required checks is empty; the checks to add are `CI` (the job of `ci.yml`, [ADR-010](Technical/adr/0010-cicd-strategy.md) point 9) and `conventions` (the name GitHub shows for the job of `conventions.yml`).

- [x] No direct pushes (a pull request is required; no force push, no deletion of `main`)
- [x] Copilot code review requested automatically on every PR (repository ruleset)
- [x] At least **1 approval** from another team member (authors do not approve their own PRs)
- [ ] Stale approvals are dismissed when new commits are pushed (off in the ruleset)
- [ ] All review conversations must be resolved (off in the ruleset)
- [ ] CI pipeline must pass: the required checks are **Conventions** (branch name, PR title, linked issue) and **CI** ([ADR-010](Technical/adr/0010-cicd-strategy.md)). The ruleset lists no required check yet
- [ ] Branch must be up to date with `main` before merging (on in the ruleset, but it belongs to the required-checks rule, which lists no check)
- [x] Head branch is deleted automatically after merge (repository setting)

## 7. Pull request template

Saved as [`.github/pull_request_template.md`](../.github/pull_request_template.md):

```markdown
## Summary
<!-- What does this PR change and why? -->

Closes #

## Type
- [ ] feature  - [ ] bugfix  - [ ] refactor  - [ ] test  - [ ] docs  - [ ] ci  - [ ] chore

## How was it tested?
<!-- Unit / integration / manual steps -->

## Checklist (Definition of Done)
- [ ] I reviewed my own diff before requesting a review
- [ ] CI pipeline is green
- [ ] Commits follow the conventions
- [ ] Tests added/updated and passing
- [ ] Documentation updated (if applicable)
- [ ] No secrets committed
```

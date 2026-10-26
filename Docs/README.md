# LABDSOF

Multi-event coordination platform for large academic festivals (pilot case: Queima das Fitas do Porto). LABDSOF 2026/27, ISEP. Assignment: [`LABDSOF-26-27-Assignment.pdf`](../LABDSOF-26-27-Assignment.pdf).

## Documentation

| Area | Document | Language |
| --- | --- | --- |
| Problem | [Problem and Opportunity Report](Deliverables/problem-opportunity-report-queima.md) | EN |
| Market | [Market and Competitor Analysis](Deliverables/market-and-competitor-analysis.md) · [Product-specific annex](Deliverables/market-and-competitor-analysis-product-specific.md) | EN |
| Costs | [Operating Cost Estimation](Deliverables/operating-cost-estimation.md): salaries and infrastructure, and why delivery is local | EN |
| Research | [Student survey (Google Forms script)](Research/inquerito-estudantes-google-forms.gs) | EN (questions in PT) |
| Product | [Product Vision](Product/product-vision.md) · [Personas](Product/personas.md) · [Main Features and Backlog](Product/features.md) · [User Workflows](Product/user-workflows.md) · [Non-Functional Requirements](Product/non-functional-requirements.md) | EN |
| Process | [Team Roles and Work Process](Process/team-and-process.md) · [Definition of Ready and Done](Process/definition-of-ready-and-done.md) · [Spec-Driven Development rules](Process/spec-driven-development.md) (proposed) | EN |
| Backlog | [Backlog board (GitHub Projects)](https://github.com/users/tomasoliveira21/projects/1) (private, team members only) · how its fields are used: [`features.md` §8.3](Product/features.md#83-stories-on-the-board) | EN |
| Technical | [Domain Model](Technical/domain-model.md) · [High-Level Architecture](Technical/architecture.md) · [API Outline and Event Catalogue](Technical/api-outline.md) · [Local delivery and demo](Technical/local-delivery.md) · ADRs: [001](Technical/adr/0001-architectural-style.md) · [002](Technical/adr/0002-backend-and-frontend-technology.md) · [003](Technical/adr/0003-database-and-data-ownership.md) · [004](Technical/adr/0004-service-communication.md) · [006](Technical/adr/0006-identity-and-access.md) · [007](Technical/adr/0007-venue-map-representation.md) · [008](Technical/adr/0008-transport-data-source.md) · [009](Technical/adr/0009-architecture-documentation.md) · [013](Technical/adr/0013-api-gateway.md) · [Diagrams](Technical/architecture.md#14-architecture-diagrams) | EN |
| Security and privacy | [Security and Privacy Assessment](Technical/security-and-privacy-assessment.md): threat model, sensitive data, access control, data minimisation, retention, consent | EN |
| Architecture research | [Deployment, observability and AI](Research/architecture-investigation/README.md) · [Responsible AI assessment](Research/architecture-investigation/responsible-ai.md) · [Triage evaluation](Research/architecture-investigation/evaluation/README.md) · ADRs: [005, proposed](Technical/adr/0005-ai-triage-provider-and-evaluation.md), [011, local delivery scope](Technical/adr/0011-deployment-and-infrastructure.md), [012, proposed](Technical/adr/0012-observability.md) | EN |
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

`main` is always deployable. Direct pushes to `main` are not allowed.

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

1. **GitHub Copilot**: requested automatically when the PR is opened.
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

- [ ] No direct pushes
- [ ] Copilot code review requested automatically on every PR (repository ruleset)
- [ ] At least **1 approval** from another team member (authors do not approve their own PRs)
- [ ] Stale approvals are dismissed when new commits are pushed
- [ ] All review conversations must be resolved
- [ ] CI pipeline must pass, including the **Conventions** check (branch name, PR title, linked issue)
- [ ] Branch must be up to date with `main` before merging
- [ ] Head branch is deleted automatically after merge

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

# Implementation specs

> **Status:** working agreement of Tiago Teixeira for the Events and Auth & Users services (owner by decision 13 of the [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md)), agreed on 16 November 2026. It adds to the team's [Definition of Done](../Process/definition-of-ready-and-done.md) and does not replace it. The gates apply only to `backend/auth-users` and `backend/events`.

Each implementation issue gets one spec, committed in the issue's pull request so it can be reviewed with the code: `Docs/specs/US-<nn>-<slug>.md` for a user story, `Docs/specs/<issue>-<slug>.md` for a task. Story specs follow the template of the [Spec-Driven Development rules](../Process/spec-driven-development.md) (proposed on 15 October 2026), and each acceptance criterion has a test whose name starts with `US-<nn> AC-<n>` (rule R5). Rule R2 (spec approved in a draft PR before any code) is not applied while those rules are only proposed; the spec and the code are reviewed together.

## What a spec holds

1. **Source:** the issue, its acceptance criteria and the documents it relies on (ADRs, API outline, domain model, meeting notes); for a story, the sections of the template (story, acceptance criteria, contracts, data, plan, out of scope)
2. **Decisions:** anything the issue leaves open, with its reason. Structural decisions not already settled by an ADR, the API outline or a meeting note are weighed with several options before they are made; a decision that changes something the team agreed goes to a meeting note
3. **Features:** the order the work is built in, each with its tests
4. **Edge-case matrix:** one row per case (bounds, null and empty, duplicates, concurrency, invalid state transitions, idempotent event handling, external failures), each linked to the name of the test that covers it
5. **Handoff and changelog:** what was done, what is left and why, appended as the work goes

## Quality gates

| Gate | Rule | Where it runs |
|---|---|---|
| Coverage | JaCoCo `check`: at least **90% of lines and 90% of branches** per module | `mvn verify`, local and CI; the build fails below it |
| Mutation | PIT, `DEFAULTS` mutators: **mutation score of at least 80%** on the `domain` and `application` packages | `mvn verify`, local and CI; the build fails below it |
| Integration | Testcontainers with real PostgreSQL and RabbitMQ and the same Flyway migrations as production | `mvn verify` |
| Security | On every endpoint: no token, expired token, wrong signature, wrong issuer or audience → `401`; wrong role → `403`; a user from another event → `404`, without revealing that the resource exists. Input validation: bounds, malformed payloads, injection, unknown or other-event IDs, mass assignment, upload type and size | `mvn verify` |
| Dependencies | `actions/dependency-review-action` on every pull request, failing on **high** severity or above | CI |

No gate is lowered, no test is skipped or disabled, and no exclusion is added to make a build pass.

## Workflow

- **One issue, one branch, one pull request** with `Closes #<issue>` (README §1, §3). A branch that needs an open pull request starts from that pull request's branch (stacked) and is rebased onto `main` once it is merged
- The pull request is opened with a green CI and a reviewer requested; it is merged by the team as the DoD says
- **Stories with front-end pages:** these pull requests deliver the backend and its OpenAPI contract; the pages are built in the web app (Tomás Oliveira, decision 13) and what they still need is written on the issue
- **Entity states:** until #198 is confirmed, the states proposed in §4 of the meeting of 15 November 2026 are used, and each pull request that relies on them says so

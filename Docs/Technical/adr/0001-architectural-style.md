# ADR-001 · Architectural style and evolution

## Status

**Accepted**: style decided by the team on 15 October 2026; evolution decided after the professor's answer to the email of 15 October (received by 21 October 2026). Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) A1, A2, A3 and A4.

**Deployment-scope clarification (recorded 22 October 2026):** as relayed to the team, the professor authorised local-only execution due to cost ([record](../../Meetings/2026-10-22-local-deployment-clarification.md), [ADR-011](0011-deployment-and-infrastructure.md)). Independent deployment in this ADR can be demonstrated with separate local service containers; no cloud deployment is required for the authorised delivery scope. The five-service architecture is retained.

**Sprint A delivery (recorded 31 October 2026):** Sprint A delivered one service, the R0 walking skeleton of Reports ([#166](https://github.com/tomasoliveira21/LABDSOF/issues/166), [ADR-010](0010-cicd-strategy.md) point 9). The five services, their databases and the broker are deployed from Sprint B, the sprint in which the assignment requires two independently deployable components (§7.2.2). Decision 3 was amended on 1 November 2026 to match ([record](../../Meetings/2026-11-01-sprint-a-close-decisions.md), #175).

## Context

- The assignment asks for at least two independently deployable backend components, or a modular monolith with convincing evidence that services would add unnecessary complexity (§6.2, architecture driver D7).
- A read peak (tens of thousands of participants at the end of a concert, driver D2) must not stop help requests.
- Each service is specified by a small contract (OpenAPI and events), the base of Spec-Driven Development.
- The report asks for an *architecture evolution narrative* (§7.3.2, item 11). The team proposed a modular monolith in Sprints A and B, split into services in Sprint C, and asked the professor whether the architecture should change between sprints or be defined from the start.
- **Professor's answer:** there is no imposed path. The architecture is defined from the start, and any evolution must be motivated by evidence, not by the calendar. Sprint B already requires two independently deployable backend components, or a properly justified modular monolith (§6.2).

## Decision

1. **Microservices** (A1), five services (A2): **Auth & Users**, **Events**, **Reports**, **Transports** and **Badges**.
2. Each service is its own **Maven project** in `backend/<service>/`, in the same repository; no service depends on another's code, only on the contracts (A3).
3. **Microservices from the start** (A4; amended on 1 November 2026, [record](../../Meetings/2026-11-01-sprint-a-close-decisions.md)): the architecture is five services from the first sprint, with no modular-monolith phase. Sprint A delivers the first one, the R0 skeleton of Reports (#166); from Sprint B every service is built, deployed and owns its database (assignment §7.2.2).
4. Any change to this structure is **motivated by evidence** (measurements, tests, failures), not by the calendar. Each change is a new ADR or one that supersedes an earlier one; together they are the evolution narrative.

## Consequences

- §6.2 is met once more than one service runs (Sprint B; Sprint A delivered one, see *Status*); no evidence against services is needed.
- A failure or read peak in one service does not stop the others (communication only through events, [ADR-004](0004-service-communication.md)). Locally all services share one machine and one PostgreSQL server, which stay a common point of failure ([ADR-011](0011-deployment-and-infrastructure.md), consequences).
- More to run from Sprint B: five deployables, five databases ([ADR-003](0003-database-and-data-ownership.md)) and the broker, with their pipeline jobs. Sprint A runs one service (R0, see *Status*).
- The ArchUnit boundary test planned for the monolith phase is no longer needed: separate Maven projects cannot import each other's classes.
- The evolution narrative depends on recording, in each ADR, the evidence that motivated the change.

## Rationale

- Services meet §6.2 by design, so no evidence against services has to be gathered later.
- The professor asked for the architecture to be defined from the start and changed only on evidence; a split planned for Sprint C would be a change driven by the calendar.
- Separate services keep a read peak from stopping help requests (driver D2), and separate Maven projects enforce the boundaries without an extra test.

## Alternatives considered

| Option | Why not chosen |
|---|---|
| Modular monolith in Sprints A and B, split into services in Sprint C | A change driven by the calendar, not by evidence; the professor asked for the architecture to be defined from the start. A split in Sprint C would also add risk at the end of the project |
| Modular monolith as the final state | Needs convincing evidence that services add unnecessary complexity (§6.2); a read peak would affect help requests |
| Monolith without enforced module boundaries | Ruled out by the team (guide §0) |

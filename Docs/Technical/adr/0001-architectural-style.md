# ADR-001 · Architectural style and evolution

## Status

**Accepted**: style decided by the team on 15 October 2026; evolution decided after the professor's answer to the email of 15 October (received by 21 October 2026). Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) A1, A2, A3 and A4.

## Context

- The assignment asks for at least two independently deployable backend components, or a modular monolith with convincing evidence that services would add unnecessary complexity (§6.2, architecture driver D7).
- A read peak (tens of thousands of participants at the end of a concert, driver D2) must not stop help requests.
- Each service is specified by a small contract (OpenAPI and events), the base of Spec-Driven Development.
- The report asks for an *architecture evolution narrative* (§7.3.2, item 11). The team proposed a modular monolith in Sprints A and B, split into services in Sprint C, and asked the professor whether the architecture should change between sprints or be defined from the start.
- **Professor's answer:** there is no imposed path. The architecture is defined from the start, and any evolution must be motivated by evidence, not by the calendar. Sprint B already requires two independently deployable backend components, or a properly justified modular monolith (§6.2).

## Decision

1. **Microservices** (A1), five services (A2): **Auth & Users**, **Events**, **Reports**, **Transports** and **Badges**.
2. Each service is its own **Maven project** in `backend/<service>/`, in the same repository; no service depends on another's code, only on the contracts (A3).
3. **Microservices from Sprint A** (A4): every service is built, deployed and owns its database from the first sprint. There is no modular-monolith phase.
4. Any change to this structure is **motivated by evidence** (measurements, tests, failures), not by the calendar. Each change is a new ADR or one that supersedes an earlier one; together they are the evolution narrative.

## Consequences

- §6.2 is met from Sprint A, before Sprint B requires it; no evidence against services is needed.
- A failure or read peak in one service does not stop the others (communication only through events, [ADR-004](0004-service-communication.md)).
- More to run from Sprint A: five deployables, five databases ([ADR-003](0003-database-and-data-ownership.md)) and the broker, with their pipeline jobs. The walking skeleton must already cover more than one service.
- The ArchUnit boundary test planned for the monolith phase is no longer needed: separate Maven projects cannot import each other's classes.
- The evolution narrative depends on recording, in each ADR, the evidence that motivated the change.

## Alternatives considered

| Option | Why not chosen |
|---|---|
| Modular monolith in Sprints A and B, split into services in Sprint C | A change driven by the calendar, not by evidence; the professor asked for the architecture to be defined from the start. A split in Sprint C would also add risk at the end of the project |
| Modular monolith as the final state | Needs convincing evidence that services add unnecessary complexity (§6.2); a read peak would affect help requests |
| Monolith without enforced module boundaries | Ruled out by the team (guide §0) |

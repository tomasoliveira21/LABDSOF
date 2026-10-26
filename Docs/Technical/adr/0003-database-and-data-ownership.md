# ADR-003 · Database and data ownership

## Status

**Accepted**: decided by the team on 15 October 2026. Updated on 21 October 2026: no modular-monolith phase (guide A4). Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) D1, D2, D3 and D6.

## Context

- The assignment asks for **one** database technology unless more are justified (§6.4).
- Farol is split into five services (guide A2), deployed independently from Sprint A (guide A4), that talk only through events (guide C4). A service that reads another service's tables would break that boundary.
- **One team must never see or change another event's data** (architecture driver D1). The report asks for automated tests that prove it.
- The venue map is an uploaded image with coordinates on it, not a geographic map (annex C3), so no spatial queries are needed.
- With continuous deployment (guide H5), a new version of a service starts while the old schema is still in use.

## Decision

1. **PostgreSQL** is the only database technology (D1). No PostGIS: the map is an image.
2. **One database per service**, which only that service reads and writes (D2). Data that a service needs from another arrives through events and is kept as a local copy ([ADR-004](0004-service-communication.md)). This applies from Sprint A: there is no shared-database monolith phase (guide A4).
3. **Per-event isolation:** every table that holds data of an event has an `event_id` column, and the application filters by it on every query (D3). **Each service has an integration test** that proves a member of event X cannot read or change data of event Y.
4. **Flyway** runs the migrations when the service starts (D6). Every migration stays compatible with the previous version of the service (*expand and contract*): add first, switch the code, remove in a later release.

## Consequences

- One technology to learn, run and back up. The same PostgreSQL runs locally, in the tests (Testcontainers) and in the cloud.
- Services cannot join across databases. Cross-service views (e.g. the event's teams and their permissions inside Reports) come from local copies and are **eventually consistent**.
- Isolation depends on the application filter being present in every query; a missing filter is a data leak. The per-service isolation test is the safety net, so it must cover every new event-scoped table.
- *Expand and contract* makes some changes (renames, type changes) take two releases.
- Five databases from Sprint A cost more than one. Locally they share **one PostgreSQL server** with Keycloak's database, six databases with one user each (guide G6, [`local-delivery.md`](../local-delivery.md)).

## Alternatives considered

| Option | Why not chosen |
|---|---|
| MongoDB or another document store | The data is relational (events, memberships, roles, help requests with audit) and needs transactions for the outbox ([ADR-004](0004-service-communication.md)) |
| PostgreSQL + PostGIS | The map is an image with its own coordinates (annex C3); no geographic queries are needed |
| One shared database for all services | Simpler to run, but services would couple through tables, breaking guide C4 and independent deployment (assignment §6.2) |
| Row-level security in PostgreSQL for event isolation | Enforced by the database, but harder to set up and test with a pooled connection per service; the application filter plus a mandatory test is enough for the MVP |
| One database or schema per event | Strong isolation, but events are created at run time; migrations and operations would multiply with every event |
| Liquibase | Equivalent to Flyway; Flyway's plain SQL migrations are simpler to review |

## Open points

- Number of PostgreSQL servers and where they run: ✅ closed on 26 October 2026. One PostgreSQL server on the local machine, with six databases and six users (five services and Keycloak); no user can read another's database (guide G6, ADR-011, [`local-delivery.md`](../local-delivery.md)).
- Account deletion (anonymise or delete per service): guide D7, Bruno.
- Cache for read peaks (D4) and retention and backup (D5): future improvements, outside the MVP.

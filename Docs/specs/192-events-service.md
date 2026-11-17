# #192 · Events service

> **Issue:** [#192](https://github.com/tomasoliveira21/LABDSOF/issues/192) · **Type:** chore · **Owner:** Tiago Teixeira · **Spec rules:** [README](README.md)
> **Sources:** [ADR-001](../Technical/adr/0001-architectural-style.md), [ADR-003](../Technical/adr/0003-database-and-data-ownership.md), [ADR-004](../Technical/adr/0004-service-communication.md), [ADR-006](../Technical/adr/0006-identity-and-access.md), [ADR-007](../Technical/adr/0007-venue-map-representation.md), [ADR-012](../Technical/adr/0012-observability.md), [ADR-013](../Technical/adr/0013-api-gateway.md), [API outline](../Technical/api-outline.md) §1, §3, §7 and §10.1 (D2, D5, D6), [domain model](../Technical/domain-model.md), [`features.md`](../Product/features.md) §4 and US-06, US-09, US-11, US-14, US-16 to US-21, [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md) decisions 5, 8, 13 and §4 (proposed states, #198 still open), the Auth & Users spec [#191](191-auth-users-service.md) (same structure, written the same day) and Reports #214 (Bruno Lopes), whose consumer pattern this service follows.

## 1. Acceptance criteria

| # | Given / when / then | Covered by |
|---|---|---|
| AC-1 | Given the Compose stack, when it starts, then the Events container is healthy, uses only its own database, and its schema (Event, Team, TeamPermission, VenueMapVersion, PointOfInterest, the membership copy, processed messages, outbox) comes from Flyway | `192 AC-1 …` in `MigrationIT`; CI *Build and start the stack* and the database isolation step |
| AC-2 | Given a request with a valid token, then the token is validated against Keycloak; a missing, expired, tampered, wrong-issuer or wrong-audience token gets `401` | `192 AC-2 …` in `SecurityIT`; CI step *Check that Events validates Keycloak tokens* (`infra/tests/events_smoke.py`, a real token through the gateway) |
| AC-3 | Given the CI pipeline, then the service is built, tested (gates of the README) and started in the Compose smoke test | CI *Build and test every module*, smoke steps |
| AC-4 | Given `contracts/openapi.yaml`, then it holds the Sprint B endpoints of API outline §3, and the service serves exactly those paths | `192 AC-4 …` in `ContractIT` |
| AC-5 | Given `GET /api/v1/events/{eventId}`, then a non-member (or an unknown event, or a FarolAdmin who is not a member) gets `404` without revealing the event exists; an EventAdmin gets the event in any status; a RegularUser gets it only when it is `Published` or `Closed`; the refused attempt is logged with the user, the event and the time (US-14) | `192 AC-5 …` in `EventQueryServiceTest`, `EventsIT`; CI smoke (a seeded user gets `404`) |
| AC-6 | Given `MembershipChanged` in `events.auth-users-events`, then the membership copy is upserted by version, duplicates (`messageId`) and older versions change nothing, an invalid message goes to its dead-letter queue, other event types are acknowledged and logged | `192 AC-6 …` in `LocalCopyServiceTest`, `LocalCopiesIT` |
| AC-7 | Given an outbox row, then it is published to `farol.events` with confirms (same publisher as #191) | `192 AC-7 …` in `OutboxIT` |
| AC-8 | Every request and message carries a correlation id in the structured logs (the consumer takes the AMQP `correlationId`); no name, e-mail or token is logged (M-10) | `192 AC-8 …` in `CorrelationIdFilterTest`, `LoggingIT`, `LocalCopiesIT` |

## 2. Decisions

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **Same structure as Auth & Users (#191)** and the same patterns as Reports (#214): JDBC, outbox and publisher, resource server, Problem Details, correlation filter, `openapi-generator` with no coverage exclusion, `*IT` in failsafe, image build with `package` | One way of building the services Tiago owns and the team reviews. The skeletons of both modules were identical on 17 November 2026 |
| D2 | **Consumer of `events.auth-users-events`** (pattern of #214 D7–D9): envelope and payload validated; `processed_message` + versioned upsert in one transaction; invalid → dead letter with `x-farol-error`; other failures tried 4 times in all (waits of 1 s, 2 s and 4 s), then dead-lettered; `MembershipChanged` stored now, the other four types acknowledged and logged until their stories (US-06 `GlobalRoleChanged`, US-07 `UserProfileChanged`, AC-6 of the assessment `UserStatusChanged`/`UserDeleted`) | The first use case needs memberships ("no copy means no access", ADR-006). Dead-lettering unhandled types would fill the DLQ panel with every first login |
| D3 | **`membership_copy` keeps `Removed` rows** (status `Active`/`Removed` of API outline §7.4) | Without them a late older version would bring a removed membership back. The minutes propose dropping `MembershipStatus` (#198 open); the outline and Reports still carry it, so the consumer accepts it |
| D4 | **Schema `V1__events.sql`**: `event` (`event_id uuid`, `name`, `organiser_name`, `start_date`/`end_date` `date` with `end ≥ start`, `description`, `status` check `Draft/Published/Suspended/Closed`, `previous_status`, `join_code`, `version`, who and when), `team` (`team_id`, `event_id`, `name`, unique per event ignoring case, `handled_categories text[]` limited to the five categories of decision 8, `deleted_at`, `version`), `team_permission` (`team_id`, `permission` from the fixed list of `features.md` §4.2, `report_types text[]`), `venue_map_version` (`event_id`, `version`, file references, size, content type, who and when), `point_of_interest` (`poi_id`, `event_id`, `map_version`, `type`, `name`, `x`/`y` in [0, 1]), `membership_copy`, `processed_message`, `outbox`. `event_id` on every event table (ADR-003) | The five entities the issue names, with the states of the 15 November proposal; teams are soft-deleted so `TeamChanged` can carry `deleted: true` with a higher version. Announcements and check-ins are Sprint C (US-44, US-58): not created now |
| D5 | **Visibility of an event** (US-06, US-09, US-11): EventAdmin → any status; RegularUser → `Published` or `Closed`; anyone else → `404` | "Saved as a draft that participants cannot see" (US-06); "participants can no longer open it, its EventAdmins see that it is suspended" (US-09) |
| D6 | **Contract**: every Sprint B path of API outline §3 (events, publish, teams and permissions, map versions and points of interest); only `GET /events/{eventId}` and the status are implemented here, the rest answer `501` naming their story. Report type codes (`QueueAtEntrance`, `CrowdedArea`, `WcProblem`, `NoWater`, `Hazard`, `TransportProblem`, `WrongInformation`) and point types (`Entrance`, `Stage`, `Wc`, `FirstAid`, `Information`, `TransportStop`, `Other`) are **proposed** until US-17 and US-20 | No document gives codes for them yet (`features.md` §6.1 has labels only). Members paths belong to Auth & Users (ADR-013) and are not served here |
| D7 | **Event contracts** in `contracts/events/`: the envelope, `event.changed` (payload `name`, `organiserName`, `dates {start, end}`, `status`, `version`), `team.changed` (compatible with what Reports reads, #214), and `membership.changed` as Events reads it | SDD rule R3; Auth & Users (#191) and Events are both Tiago's, so `EventChanged` is fixed here |
| D8 | **Refused cross-event attempts are logged** at `WARN` with `userId`, `eventId` and the time, never with names or e-mails | US-14 and assessment AC-10; the `sub` is pseudonymous (M-10) |

## 3. Features (build order)

1. Module, schema, domain (event visibility), membership copy and its consumer
2. Outbox and publisher (same as #191 F02)
3. Web: security, Problem Details, correlation id, controllers (`GET /events/{eventId}`)
4. Event schemas, Compose, CI smoke, docs

## 4. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| Non-member, unknown event, malformed UUID | `404`, `404`, `400` | `EventsIT` |
| Member of event X asks for event Y | `404` and a `WARN` log with user, event, time | `EventsIT`, `LoggingIT` |
| RegularUser on a `Draft` or `Suspended` event | `404` | `EventQueryServiceTest`, `EventsIT` |
| EventAdmin on each status | `200` with the status; join link only once published | `EventQueryServiceTest` |
| FarolAdmin who is not a member | `404` | `EventsIT` |
| Removed membership | `404` | `EventsIT` |
| `MembershipChanged` duplicate, older version, equal version | Nothing changes | `LocalCopyServiceTest`, `LocalCopiesIT` |
| `MembershipChanged` without `eventId`, unknown role, bad JSON, `schemaVersion` ≠ 1 | Dead-lettered with the reason, not retried | `LocalCopiesIT` |
| `MembershipChanged` without `status` | Read as `Active` (if #198 drops `MembershipStatus`, nothing breaks) | `LocalCopyServiceTest` or `LocalCopiesIT` |
| Database down while consuming | 4 attempts in all, then dead-lettered | `LocalCopiesIT` |
| Other event types (`UserProfileChanged`, …) | Acknowledged and logged | `LocalCopiesIT` |
| Service starts before its broker user exists | Starts; consumes once the user exists | `LocalCopiesIT` |
| Token cases (as #191) | `401` | `SecurityIT` |
| Operations of later stories | `501` with a token, `401` without | `ContractIT` / `SecurityIT` |

## 5. Handoff and changelog

- **17 Nov 2026:** research (10 min) while #191 was being implemented; findings in this spec.
- **Open points:** (1) the join link: `join_code` is stored, but `POST /events/{eventId}/members/me` of Auth & Users (#191) does not check a code, so anyone with a published event's id can join; decide with US-11 and US-12 whether the link carries only the id or a code. (2) `POST /events` needs a copy of the FarolAdmin grants (`GlobalRoleChanged`), which comes with US-06. (3) Report and point types of D6 are proposals until US-17 and US-20.

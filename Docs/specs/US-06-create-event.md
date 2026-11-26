# US-06 · Create an event and name its first EventAdmin

> **Issue:** [#61](https://github.com/tomasoliveira21/LABDSOF/issues/61) · **Type:** user story (F3, Event management) · **Services:** Events **and** Auth & Users · **Spec rules:** [README](README.md)
> **Sources:** issue #61; [`features.md`](../Product/features.md) US-06 and §4.1; [API outline](../Technical/api-outline.md) §2 (`GET /accounts?email=`, `POST /events/{eventId}/members`, and the note on D3: *the first EventAdmin of a new event is named right after the event is created in Events; the event reaches Auth & Users through `EventChanged`; until then the call returns `404` and the web app retries*), §3 (`POST /events`), §1.7 (Problem Details), §7.3 and §7.4 (`EventChanged`, `GlobalRoleChanged`, `MembershipChanged` and their consumers), §10.1; [domain model](../Technical/domain-model.md) (`EventStatus.Draft`, `GlobalRole`); [ADR-003](../Technical/adr/0003-database-and-data-ownership.md), [ADR-004](../Technical/adr/0004-service-communication.md), [ADR-006](../Technical/adr/0006-identity-and-access.md) (the role is never read from the token), [ADR-013](../Technical/adr/0013-api-gateway.md) M-10 (no names in the logs); [security assessment](../Technical/security-and-privacy-assessment.md) (the e-mail lookup); specs [#191](191-auth-users-service.md), [#192](192-events-service.md) (D1, D2, D5, D7, D8), [US-11](US-11-publish-event.md) (D4, D5, D7), [US-18](US-18-team-members.md) (D3, D4) and [US-12](US-12-join-event.md) (D5); decision 3 of the [meeting record of 21 November 2026](../Meetings/2026-11-21-report-type-codes-and-team-writes.md) (the version guard on every write); decision 13 of the [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md).
> **Base: `main`** (US-18, US-19 and US-12 merged, `13dd971`). Not stacked.
> **Entity states:** the proposal of 15 November 2026 §4 is used (#198 open), as every Events and Auth & Users pull request so far.
> **First spec that spans two services.** Its one acceptance criterion is only satisfied when both halves exist (D4), so it is one issue, one branch and one pull request, as the README's workflow requires.

## 1. Source

**Story:** as the **FarolAdmin** (persona P4 · Daniel), I want to create an event for a verified organisation and name its first EventAdmin, so that only real organisers run events on Farol.

**This PR:** the three operations the story needs, all of which answer `501` today — `POST /api/v1/events` in Events, and `GET /api/v1/accounts?email=` and `POST /api/v1/events/{eventId}/members` in Auth & Users — plus the **copy of the FarolAdmin role** that Events needs to recognise the caller at all (D1). With it, **both services reach zero `501`** for Sprint B except the map reads of US-20 (#69) and US-21 (#70).

**Not in it:** listing events as the FarolAdmin (US-07, #89), which is the only way the FarolAdmin reads an event back (D3); making or removing a *further* EventAdmin (US-15, #94) and naming one on an event that already has members (US-08, #90) — the same endpoint serves them, but their acceptance criteria and their `422` on the last EventAdmin are theirs; verifying that the requester really represents the organisation, which the story's *when I confirm* leaves to the FarolAdmin as a human step with no field in the contract (D9); the back-office page, which is the web app's (decision 13: what it needs goes on #61).

| # | Acceptance criterion | Tests (name starts with `US-06 AC-<n>`) |
|---|---|---|
| AC-1 | Given an organisation asks for a new event, when I confirm the requester represents it and create the event with a name and dates, then it is saved as a draft that participants cannot see, and the named user becomes its EventAdmin | `EventTest`, `EventServiceTest`, `CreateEventIT`, `JdbcEventsIT`, `EventContractIT` (Events); `NameEventAdminServiceTest`, `NameEventAdminIT`, `EventContractIT` (Auth & Users). *A draft participants cannot see*: the `RegularUser` who gets `404` on it is #192 D5's rule, asserted in `CreateEventIT`. *The named user becomes its EventAdmin*: `NameEventAdminIT` drives the whole flow — create in Events, the real `EventChanged`, then the name call — and reads the membership back through `GET /me/memberships` |
| AC-2 | Given a user who is not the FarolAdmin, when they try to create an event, then the action is refused | `EventServiceTest`, `CreateEventIT`, `SecurityIT` (Events); `NameEventAdminServiceTest`, `NameEventAdminIT`, `SecurityIT` (Auth & Users) — `403` for a signed-in non-FarolAdmin on all three operations, including an EventAdmin of some other event, and `401` for no token |

## 2. Quality gates

The gates of [`README.md`](README.md), unchanged, on **both** `backend/events` and `backend/auth-users`: `mvn clean verify` green, JaCoCo ≥ 90 % of lines **and** branches with the generated code included, PIT `DEFAULTS` ≥ 80 % on `domain` and `application`, Testcontainers with the production migrations, the security matrix on each new endpoint, `ContractIT` and `EventContractIT`.

Baselines this branch starts from (`13dd971`):

| Module | Unit | IT | JaCoCo (lines / branches) | PIT |
|---|---|---|---|---|
| `backend/events` | 304 | 605 | 1224/1227 · 291/292 | 204/204 |
| `backend/auth-users` | 193 | 290 | 850/852 · 206/208 | 115/115 |

## 3. Decisions

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **Events learns who the FarolAdmin is from a local copy of `GlobalRoleChanged`, never from the token.** The binding is already there — `farol.auth-users` → `events.auth-users-events` with routing key `global-role.changed` (#212) — and `LocalCopyListener` acknowledges the type today under *Event type not handled by Events yet*, with two integration tests asserting exactly that. This story makes it the **second handled type**, stored in a new `global_role_copy` table, and `POST /events` is gated on it | ADR-006 is explicit that an authorisation decision is never read from the token, and every endpoint of both services already follows it. *Reading `realm_access.roles` from the JWT:* it would make Events trust Keycloak's mapper for an authorisation decision and break the one rule ADR-006 sets. *Calling Auth & Users synchronously:* against ADR-004's asynchronous rule, and it would make creating an event fail whenever Auth & Users is down — the copy keeps working. *A static allow-list in configuration:* the bootstrap FarolAdmin is data (`MeService` grants it once the e-mail is verified, #179), and US-08/US-15 will change who holds the role, so configuration would go stale silently |
| D2 | **Flyway `V2__global_role_copy.sql`** in Events — its first migration after `V1` — one row per `(user_id, role)` with the `version`, applied exactly as `membership_copy` is: a message whose version is not newer is ignored, so a replay or a reorder is idempotent (#192 D2). A `granted: false` **keeps the row and sets `granted = false`**, it does not delete it | Mirroring `membership_copy` means one pattern to read, one to test and one to change. *Deleting the row on a revoke:* a later out-of-order message carrying the *older* `granted: true` would find no version to compare against and would silently re-grant the role; keeping the row makes the version guard work in both directions. *A `processed_message` row alone:* it records that a message arrived, not what the current state is, and the gate needs the state |
| D3 | **Creating the event does not make the creator a member.** `POST /events` writes the draft and nothing else; the first EventAdmin is named in Auth & Users (API outline D3). The consequence is deliberate and worth stating: the FarolAdmin **cannot read the event back** through `GET /events/{eventId}`, because they are not a member and #192 D5 gives them the same `404` as a stranger — there is already a test for it (`192 AC-5 a FarolAdmin who is not a member gets not-found, even after GlobalRoleChanged`). The `201` body is therefore the only time they see the event until US-07 (#89) lists it | The API outline fixes the split, and §4.1 of `features.md` keeps the FarolAdmin out of event data on purpose: they administer the platform, they are not a participant of every event. *Making the creator an EventAdmin:* it would contradict the story, which names *a* user, usually the organiser, not the FarolAdmin. *Making the creator a member so they can read it back:* it would give the platform admin standing access to every event's data, which is what US-14 (#64) exists to prevent. The real fix is US-07, and the limit is recorded in §6 |
| D4 | **One pull request across the two services.** AC-1 ends with *and the named user becomes its EventAdmin*, which no single service can satisfy | *Two stacked pull requests, Events then Auth & Users:* the first would merge with an acceptance criterion half met and no test able to prove AC-1, and `Closes #61` could only sit on the second. The modules are independent at build time, so one pull request costs nothing in CI; the diff is read service by service |
| D5 | **`EventChanged` is published for the new draft, at version 1** — the first `EventChanged` any event ever gets — through the outbox in the same transaction (ADR-004), from the `EventEvents.changed` factory US-11 (D5) already added. This is what unlocks the name call: until the copy arrives, `POST …/members` answers `404`, as the API outline says, and the web app retries | The schema and §7.4 fix the payload (`name`, `organiserName`, `dates`, `status`, `version`; no description, no join code). Auth & Users needs it to know the event exists (#241 already consumes `EventChanged`); Reports and Transports need it before they can refuse a suspended or closed event. *Publishing only on the publish (US-11):* Auth & Users could then never accept the name call, and the whole story would stall on a message that never comes |
| D6 | **Naming the same user EventAdmin again changes nothing and publishes nothing**, and still answers `201` with the membership. The version does not move | The contract declares one success code for `nameEventAdmin` (`201`), so the answer is `201` whether or not the row changed; a back office that retries a timed-out call must not get an error for work already done. *Bumping the version anyway:* it would publish a `MembershipChanged` that reports no change, and every consumer would process a no-op — the same reasoning as US-18 D3. *Answering `200` on the second call* (what US-12's join does): the join has `200` in its contract and this operation does not, and inventing a code the contract does not declare would break `ContractIT` |
| D7 | **`GET /accounts?email=` is an exact match, FarolAdmin-only, and audited.** It answers `AccountSummary` — `userId` and `displayName` only, never the e-mail back — and a miss is `404`. The lookup is recorded with the caller, the time and **a hash of the e-mail, never the address itself** | The contract's own summary asks for *exact e-mail match only, audited*. Exactness matters: a prefix or fuzzy search would turn the endpoint into a directory of every account on the platform. Answering `404` for a miss and `200` for a hit still makes it an existence oracle for one address at a time, which is why it is FarolAdmin-only and audited; bounding the *rate* is the gateway's (#202, ADR-013), noted on the issue because the gateway has no such filter today. *Logging the address:* against M-10 and the security assessment; a hash keeps the audit trail usable without storing personal data in the log pipeline of #148 |
| D8 | **The bounds live in the domain, not only in the generated DTO:** `name` and `organiserName` 1–100 characters once trimmed, `description` ≤ 2000, and `dates.start` ≤ `dates.end`. A blank name after trimming is `400`, as is a range that ends before it starts | The same trap US-16 recorded: `openapi.yaml` generation has silently changed validation semantics before (`uniqueItems` producing a `Set`), so the rule that must hold belongs in `Event`, with the DTO as a first line of defence. Dates are the one cross-field rule the generated bean validation cannot express at all |
| D9 | **No field records that the requester was confirmed to represent the organisation.** The story's *when I confirm* is a human step the FarolAdmin takes before calling; `organiserName` is free text, as the contract has it | Adding a `verified: true` flag would record a claim the system cannot check and would read as evidence in an audit. Nothing in `features.md` or the API outline asks for a verification record, and inventing one is a contract change that would need the team (README: a decision that changes something agreed goes to a meeting note) |
| D10 | **`global-role.changed.schema.json` is copied into `backend/events/contracts/events/consumed/`**, next to `membership.changed.schema.json`, and `EventContractIT` validates a real message against it | #192 D1 set the pattern: a consumer keeps the schema of what it consumes, so a producer change that breaks it fails this service's own build rather than production |

## 4. Features (build order)

| # | Feature | Tests |
|---|---|---|
| F1 | Events domain: `Event.created(name, organiserName, dates, description)` — version 1, status `Draft`, no join code — with the bounds and the date-range rule of D8 | `EventTest` |
| F2 | Events application: the `GlobalRoles` port and its copy use case (D1, D2), `EventService.create`, the `Events.insert` port, and the `FarolAdminOnly` gate that reads the copy | `EventServiceTest`, `LocalCopyServiceTest`, `InMemoryPorts` |
| F3 | Events adapters: `V2__global_role_copy.sql`, the second handled type in `LocalCopyListener`, `JdbcGlobalRoles`, `JdbcEvents.insert`, the controller method replacing its `501`, and `openapi.yaml`'s description | `JdbcGlobalRolesIT`, `JdbcEventsIT`, `CreateEventIT`, `ContractIT`, `SecurityIT`, `LoggingIT`, `MigrationIT` |
| F4 | Events: the first `EventChanged` of a draft through the real broker, and the consumed `GlobalRoleChanged` schema (D5, D10) | `EventContractIT` |
| F5 | Auth & Users: `GET /accounts?email=` with the audit of D7, and `POST …/members` with the FarolAdmin gate and the `404` until the event copy arrives (D6) | `AccountLookupServiceTest`, `NameEventAdminServiceTest`, `JdbcAccountsIT`, `AccountLookupIT`, `NameEventAdminIT`, `ContractIT`, `SecurityIT`, `LoggingIT` |
| F6 | Auth & Users: the `MembershipChanged` of a named EventAdmin through the real broker | `EventContractIT` |
| F7 | Documentation: this spec, `CHANGELOG.md`, `local-delivery.md` §0, the new checks in `infra/tests/events_smoke.py` and `auth_users_smoke.py`, the front-end note on #61 | — |

## 5. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| No token on any of the three operations | `401` Problem Details with `WWW-Authenticate: Bearer` | `SecurityIT` (both modules), both smokes |
| Expired token, wrong signature, wrong issuer, wrong audience | `401` | `SecurityIT` (both modules) |
| Signed-in user who is not the FarolAdmin | `403`, nothing written, nothing published | `US-06 AC-2` cases in `CreateEventIT`, `NameEventAdminIT` |
| An EventAdmin of another event creating an event | `403` — being an admin somewhere is not a platform role | `US-06 AC-2` case in `CreateEventIT` |
| A FarolAdmin whose `GlobalRoleChanged` has not arrived yet | `403`: the copy is the only source (D1), and the refusal is recorded | `CreateEventIT` |
| A revoked FarolAdmin (`granted: false`) | `403` | `JdbcGlobalRolesIT`, `CreateEventIT` |
| `GlobalRoleChanged` replayed, or arriving out of order | The older version is ignored, the row keeps the newer state | `LocalCopyServiceTest`, `JdbcGlobalRolesIT` |
| `GlobalRoleChanged` with an unknown `role`, or a missing `version` | Dead-lettered with a reason, nothing written | `LocalCopyListenerTest`, `DeadLettersTest` |
| `name` or `organiserName` empty, blank, or 101 characters | `400`, nothing written | `EventTest`, `CreateEventIT` |
| `name` of exactly 1 and exactly 100 characters | `201` | `EventTest` |
| `description` of 2001 characters; absent `description` | `400`; `201` with an empty description | `EventTest`, `CreateEventIT` |
| `dates.start` after `dates.end`; start equal to end | `400`; `201` | `EventTest`, `CreateEventIT` |
| Unknown field in the body (mass assignment: `status`, `eventId`, `joinCode`) | `400` — `additionalProperties: false` | `CreateEventIT` |
| Malformed JSON, wrong content type | `400`, `415` | `CreateEventIT` |
| Two creates with the same name | Both `201`: a name is not unique across events (only team names are, per event) | `CreateEventIT` |
| `?email=` malformed, absent, or 255 characters | `400` | `AccountLookupIT` |
| `?email=` of an account that does not exist | `404`, and the attempt is still audited | `AccountLookupIT` |
| `?email=` differing in case from the stored address | `200` — the match is exact but case-insensitive, as `releaseVerifiedEmail` already treats e-mail | `JdbcAccountsIT`, `AccountLookupIT` |
| Naming an EventAdmin on an event whose copy has not arrived | `404`, as the API outline says; the web app retries | `NameEventAdminIT` |
| Naming an EventAdmin on an unknown event id, or a malformed one | `404`; `400` | `NameEventAdminIT` |
| Naming a `userId` with no account | `404`, indistinguishable from an unknown event | `NameEventAdminIT` |
| Naming the same user EventAdmin twice | `201` both times, one `MembershipChanged`, version unchanged (D6) | `NameEventAdminServiceTest`, `NameEventAdminIT` |
| `role` other than `EventAdmin` in the body | `400` — the enum has one value | `NameEventAdminIT` |
| Two creates at once | Both commit: no unique key is contended, and each gets its own id and `EventChanged` | `JdbcEventsIT` |
| Two name calls for one user at once | One version, one `MembershipChanged`, the lock of US-18 D7 serialising them | `JdbcEventMembershipsIT` |
| The outbox write fails after the event row | Nothing is committed: one transaction (ADR-004) | `JdbcEventsIT` |
| The e-mail lookup, the create and the name call | Each writes one `INFO` line with the correlation id, the pseudonymous `userId` and the time; **no name, no e-mail address, no join code** | `LoggingIT` (both modules) |

## 6. Handoff and changelog

*(appended as the work goes)*

**Known limits to record in the pull request**

- **The FarolAdmin cannot read an event back** after creating it (D3): no membership, so `GET /events/{eventId}` is the same `404` as for a stranger. The `201` body is the only view until **US-07 (#89)**, which is in Sprint C. A back office that loses the `201` has no way to find the event's id again, which is worth saying on #61 for the web app.
- **The name call needs the `EventChanged` to have arrived**, so the back office's two steps are eventually consistent: the API outline's answer is a `404` and a retry, which this story implements rather than changes. A create followed immediately by a name call will usually need one retry.
- **The e-mail lookup is not rate-limited.** D7 keeps it FarolAdmin-only, exact and audited, but bounding how fast it can be asked is the gateway's (#202, ADR-013) and the gateway has no such filter today. The point belongs on #61 and #202.
- **US-08 (#90) and US-15 (#94) share `POST …/members`** with this story. What this PR serves is the *first* EventAdmin; the `422` that protects the last EventAdmin of an event belongs to US-15 and is deliberately not implemented here, so the endpoint is not yet complete for those stories.

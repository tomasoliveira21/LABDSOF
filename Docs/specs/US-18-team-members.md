# US-18 · Add and remove team members (#67)

> Spec written before the implementation, as [`README.md`](README.md) asks, and committed in the PR that closes the
> issue. Module: `backend/auth-users`. Branch: `feature/67-team-members`, from `main` (it needs the local copies of
> the events and their teams that [#241](https://github.com/tomasoliveira21/LABDSOF/pull/241) merged).

## 1. Source

- **Story** ([`features.md` §7](../Product/features.md), F4): *As an EventAdmin, I want to add RegularUsers of my
  event to a team, and remove them, so that the right people get the team's permissions.* AC-1 a member added to
  *Nursing* gets the team's permissions in this event; AC-2 a user who is not a member of my event is refused;
  AC-3 a user in two teams has the permissions of both; AC-4 a user removed from a team loses its permissions at
  their next action.
- **Issue** [#67](https://github.com/tomasoliveira21/LABDSOF/issues/67), persona P3 (Sofia, event admin).
- **Contract**: `backend/auth-users/contracts/openapi.yaml` already declares the three operations this story
  implements — `GET /api/v1/events/{eventId}/members` (`listMembers`), `PUT` and `DELETE` on
  `/api/v1/events/{eventId}/members/{userId}/teams/{teamId}` — and `MembersController` marks all three with
  `NotYetImplementedException("US-18")`.
- **API outline** [§2](../Technical/api-outline.md): Auth & Users owns the memberships **and the team
  assignments**; the teams themselves are Events' (US-16, #65), read here from the local copy; `PUT` is `404` *if
  the user is not a member or the team is not in the local copy*; `MembershipChanged` carries `userId`, `role`,
  `status` and `teamIds` (§7.3), which is how Events, Reports and Transports learn who is in which team.
- **Already in place**, so this story adds no table and no queue: `event_membership` and `team_assignment`
  (`V1__auth_users.sql`), `event_copy` and `event_team_copy` with the `EventChanged`/`TeamChanged` consumer
  (`V2__event_membership_read_model.sql`, `EventCopyListener`, #241), the outbox to `farol.auth-users` (#191) and
  the `membership.changed` binding of `events.auth-users-events` (#212).

## 2. Quality gates

The gates of [`README.md`](README.md), unchanged, on `backend/auth-users`: `mvn clean verify` green, JaCoCo ≥ 90 %
of lines **and** branches with the generated code included, PIT `DEFAULTS` ≥ 80 % on `domain` and `application`,
Testcontainers with the production migrations, the security matrix on the three operations (missing, expired, bad
signature, wrong issuer, wrong audience, `alg none`, non-canonical `sub` → `401`; a member who is not an
EventAdmin → `403`; another event's EventAdmin → `404`), `ContractIT` and `EventContractIT`. Where the branch ends:
183 unit and 280 integration tests, JaCoCo 790/792 lines and 190/192 branches, PIT 99/99.

## 3. Decisions

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **All three operations of the contract**, not only the two the AC name: `PUT` and `DELETE` of an assignment, and `GET …/members`. `listMembers` is `NotYetImplementedException("US-18")` in the code and *(US-18)* in the contract | The back office cannot add a member to a team without a list of members to pick from, and the outline gives the list to US-15 **and** US-18. US-15 (#64) still adds the role change (`PATCH …/members/{userId}`), which this story leaves alone |
| D2 | **EventAdmin of that event only.** A member who is not an EventAdmin gets `403`, a non-member — a `FarolAdmin` who is not a member included — gets the same `404` as an unknown event. `listMembers` gains a `403` in the contract, which it does not declare today | The outline's hiding rule (§1.7) and ADR-006: the permission is read from this service's own tables, never from the token. The `403` is what every other EventAdmin-only read answers (`listTeams` in Events, #65), and a `404` for a member who exists would say the event does not exist to someone who can see it elsewhere |
| D3 | **Both writes are idempotent and answer `204`**: a `PUT` of an assignment that is already there and a `DELETE` of one that is not change nothing, publish nothing and log nothing | The contract says so in both descriptions (*The member is in the team (idempotent)*). It is also what makes the web app's checkbox list safe to retry, and what keeps a duplicate `PUT` from inflating the membership's version and waking three consumers for nothing |
| D4 | **The team must be a live team of this event in the local copy** (`event_team_copy`, with `payload->>'deleted' = 'false'`), and the **user must be a member of this event** (`event_membership`): anything else is `404`. **Liveness is a rule of the read, not only of the write** (added in the review of PR #255): the team ids of a membership — the ones `MembershipChanged` carries, the ones `GET …/members` and `GET /me/memberships` show, and the ones the `teamId` filter matches — are the assignments whose team is live in the copy. Nothing deletes a `team_assignment` row when Events deletes the team, so a `DELETE` of such an assignment clears the row it left behind **without** raising the version or publishing anything: the team was already out of the member's teams, so nothing a consumer holds changes | The outline asks for exactly this on the write. The copy is the only thing this service knows about teams (ADR-006, §7.3), and a team of another event or a deleted one would give its permissions to nobody — Events ignores an assignment to a team it has deleted, so storing it would be a lie the back office would show. *Checking only on the write:* the first version did, and it published a deleted team's id in the **next** `MembershipChanged` for that member, against what `membership.changed.schema.json` promises its three consumers (*every live team of this event the member is in*), and showed the team in the picker. *Deleting the rows when the `TeamChanged` arrives:* a write in a message consumer, and it would have to publish a `MembershipChanged` for every member of the team to be honest — a question of its own, left on [#67](https://github.com/tomasoliveira21/LABDSOF/issues/67). Filtering the read needs no migration, no event and no consumer change, and it makes the row harmless while it is there |
| D5 | **Any member of the event may be put in a team, EventAdmin included**, though the AC name RegularUsers | Nothing in the role model forbids it, and the system itself produces that state: US-15 (#64) promotes a member who is already in a team. A team also carries the help categories a request is routed to (US-29), so an EventAdmin who triages has a reason to be in one. Refusing it would be a rule no story asks for, and one the promotion would then break |
| D6 | **Each change publishes `MembershipChanged` through the outbox in the same transaction** (ADR-004), with the **full state** of the membership — `userId`, `role`, `status: Active`, every `teamId` and the new `version` — and the membership's `version` is raised by one, with `updated_at`/`updated_by`. **It is the first `MembershipChanged` of the product**, so it brings `membership.changed.schema.json` next to the two schemas #191 wrote, and a case in `EventContractIT` | §7.3 is a full-state contract: Events upserts its copy and ignores a version not newer than the one it has, so a delta would be unreadable and a version that does not move would be dropped as a replay. `status` is sent as `Active` even though Events reads it as optional, because #198 has not dropped `MembershipStatus` yet. AC-1 and AC-4 *are* this event: the permissions live in the consumers' copies |
| D7 | **The membership row is locked (`SELECT … FOR UPDATE`) before the version is raised**, so two assignments of the same member queue instead of racing | Without it both read version *n*, both write *n + 1* and the two `MembershipChanged` are indistinguishable: a consumer keeps whichever arrives first and loses the other team. The lock is on one membership of one event, so it serialises against nothing else. As US-19 D7 learnt from #249's review, a conflict that aborts the transaction cannot be retried inside it, which is why this is a lock and not a unique violation to catch |
| D8 | **The assignment records who and when** (`team_assignment.assigned_at`/`assigned_by`, already in V1) and each change writes **one `INFO` line** with the correlation id, the actor's `userId`, the event, the member's `userId` and the team — **no e-mail and no display name** (M-10) | The story's Definition of Done asks for structured logs with a correlation id, and US-14 asks for the refusals to be recorded. The `userId` is the pseudonymous key the whole product logs; a display name in the log pipeline of #148 would be personal data with no use |
| D9 | **No migration.** `team_assignment` (V1) and `event_team_copy` (V2) already hold everything, including the cascade that removes the assignments with the membership | #191 D3 wrote `team_assignment` with this story in mind, and the version/`updated_by` columns of `event_membership` are the ones D6 raises |
| D10 | **The member list is read from one query** (`event_membership` joined to `user_account` for the display name, with the team ids aggregated as `JdbcMyMemberships` does), ordered by display name then `userId`, with the optional `role` and `teamId` filters applied in SQL | The list is the back office's picker: a stable order matters more than the insertion one, and the filters are declared in the contract. One query keeps it from N + 1 reads of the accounts |
| D11 | **The event's status is not checked**, unlike the team operations of US-16 and US-19, which refuse a `Suspended` or `Closed` event | It cannot be checked honestly yet: the status lives in `event_copy`, which only exists once Events has published an `EventChanged` for that event, and **no event is created through the API before US-06** (#61). Requiring a status would make this story unreachable today and would turn a message that has not arrived yet into a refusal — the same reason #241 answers a membership with no event name. The gap is named here and on #67: when US-06 lands, the check belongs with US-15's role change, so both writes of a membership follow one rule |

## 4. Features (build order)

| # | Feature | Tests |
|---|---|---|
| F1 | Domain: `EventRole`, `EventMembership` (the role, the teams and the version it publishes) | `EventMembershipTest` |
| F2 | Application: `TeamMemberService.add`/`remove` (D3 to D8), `MemberQueryService.list` (D10), `EventAdmins.require` (D2), the ports `EventMemberships` (read, lock, raise the version, assign, unassign), `TeamCopies` (a live team of this event), `MemberAudit`, and `OutgoingEvent.membershipChanged` (D6) | `TeamMemberServiceTest`, `MemberQueryServiceTest`, `InMemoryPorts` |
| F3 | Adapters: `JdbcEventMemberships`, `JdbcTeamCopies`, `MembersController`'s three methods, the `403` of D2 in the contract | `JdbcEventMembershipsIT`, `TeamMembersIT`, `MemberListIT`, `ContractIT`, `SecurityIT`, `LoggingIT` |
| F4 | `MembershipChanged` through the real broker to the bound queue, and its JSON Schema | `EventContractIT` |
| F5 | Documentation: this spec, `CHANGELOG.md`, `local-delivery.md` §0, the new checks in `infra/tests/auth_users_smoke.py`, the front-end note on #67 | — |

## 5. Edge-case matrix

Every test name starts with `US-18 AC-1` to `AC-4`, except where another story's test is named in full.

| Case | Expected | Test |
|---|---|---|
| An EventAdmin adds a member to a live team of the event | `204`; the assignment is stored with who and when, the membership's version is raised, one `MembershipChanged` carries every team, one log line | `TeamMembersIT` · `an EventAdmin adds a member to a team` |
| The same `PUT` again | `204`, nothing stored, nothing published, nothing logged (D3) | `TeamMembersIT` · `adding a member twice changes nothing` |
| A member in two teams | Both assignments stand and `MembershipChanged` carries both team ids (AC-3) | `TeamMembersIT` · `a member can be in two teams and both are published` |
| An EventAdmin removes a member from a team | `204`; the assignment is gone, the version is raised and `MembershipChanged` carries the teams that are left (AC-4) | `TeamMembersIT` · `an EventAdmin removes a member from a team` |
| A `DELETE` of an assignment that is not there | `204`, nothing changed and nothing published (D3) | `TeamMembersIT` · `removing a member who is not in the team changes nothing` |
| The user is not a member of the event (AC-2) | `404`, nothing stored | `TeamMembersIT` · `a user who is not a member of the event cannot be put in a team`; `TeamMemberServiceTest` · same name |
| The team belongs to another event / is deleted in the copy / is unknown | `404` in the three cases (D4) | `TeamMemberServiceTest` · `only a live team of this event takes members` |
| Events **deletes** a team the member was already assigned to | It leaves their teams at once: out of the next `MembershipChanged`, out of `GET …/members` and `GET /me/memberships`, and the `teamId` filter stops matching its old members. The assignment row stays until a `DELETE` clears it (D4) | `JdbcEventMembershipsIT` · `a team Events deletes after the assignment leaves the member's teams and the list`; `MemberListIT` · `a team Events deleted is no longer shown, and the DELETE clears the row it left`; `MembershipsIT` · `listsOnlyTheCallersMembershipsWithLatestEventDetailsAndAssignedTeamIds` (US-01's view, through the real `TeamChanged`) |
| A `DELETE` of an assignment to a team Events deleted | `204`, the row is cleared, the version does **not** move and nothing is published (D3, D4); the teams the member keeps are untouched | `MemberListIT` · `a team Events deleted is no longer shown, and the DELETE clears the row it left`; `TeamMemberServiceTest` · `a removal needs no team copy, and the teams the member keeps are untouched` |
| The event is unknown, or known only through its copy with no membership | `404`, the same problem as an unknown event (D2) | `TeamMembersIT` · `an unknown event and one the caller is not a member of are the same 404` |
| A `RegularUser` member, and a member of another event, call the three operations | `403` for the member, `404` for the stranger and for another event's EventAdmin (D2) | `TeamMembersIT` · `only an EventAdmin of the event may change its teams' members`; `MemberListIT` · same name |
| Two `PUT`s of the same member at once | Both stored, two `MembershipChanged` with consecutive versions, the second carrying both teams (D7) | `JdbcEventMembershipsIT` · `two assignments of one member get consecutive versions` |
| Two `PUT`s on **different** members at once | Independent, neither waits for the other | `JdbcEventMembershipsIT` · `assignments of different members do not block each other` |
| A malformed `eventId`, `userId` or `teamId` | `400` | `TeamMembersIT` · `a malformed id is refused with 400` |
| The member list of an event | The members with their display name, role and team ids, ordered by display name; only this event's (D10) | `MemberListIT` · `the list carries every member of the event with role and teams` |
| The list filtered by `role`, by `teamId`, by both, and by a team of another event | Only the matching members; an empty list where nothing matches | `MemberListIT` · `the list can be filtered by role and by team` |
| A member whose account is disabled or deletion-pending | Still in the list (the membership exists); their own calls are refused by #191's rules, not here | `MemberListIT` · `a disabled account is still a member of the event` |
| No token / expired / wrong signature / wrong issuer / wrong audience / `alg none` / HS256 / non-canonical `sub` | `401` | `SecurityIT` · `191 AC-2 every secured operation of the contract refuses a missing or invalid token with 401 Problem Details` |
| The log lines of D8 | One `INFO` line per change with the correlation id, the actor, the event, the member and the team — no e-mail, no display name | `LoggingIT` · `the team assignment is recorded with who made it and when, and never with a name` |
| `MembershipChanged` against its JSON Schema, through the real broker to the bound queue | Valid, with the role, the status, every team id and the new version | `EventContractIT` · `adding a member to a team publishes a MembershipChanged that matches its JSON Schema` |
| The contract | The three operations are served; the rest still answer `501` with their story | `ContractIT` · `191 AC-4 the service serves exactly the operations of openapi.yaml` |

## 6. What the web app still needs (decision 13)

To be left on #67 when the backend lands: the member picker is `GET /api/v1/events/{eventId}/members` with the
optional `role` and `teamId` filters; a checkbox per team calls `PUT` or `DELETE`
`…/members/{userId}/teams/{teamId}` and both answer `204` with no body, so the list can be updated optimistically
and both are safe to retry; `403` means the caller is not an EventAdmin of this event and `404` that the event, the
member or the team is not there (the three are deliberately indistinguishable); the permissions themselves are
Events' (`GET …/teams`, US-16), so the back office shows what a team grants next to its name.

## 7. Handoff and changelog

- **26 Nov 2026 · research.** Focused pass, as US-19's: the contract's three operations and the `NotYetImplemented`
  markers, the API outline §2 table and §7.3's full-state rule for `MembershipChanged`, `V1`'s `event_membership`
  and `team_assignment`, `V2`'s `event_copy`/`event_team_copy` and the `EventCopyListener` that fills them (#241),
  `TeamChanged`'s payload as Events publishes it (`deleted`, `name`, `permissions`), the outbox and
  `OutgoingEvent`'s two existing factories, and `JdbcMyMemberships`'s team-id aggregation, which D10 reuses.
- **26 Nov 2026 · implementation**, in the order of §4, with `mvn clean verify` (JDK 21) green at the end: **183
  unit and 280 integration tests**, JaCoCo **790/792 lines (99.7 %)** and **190/192 branches (99.0 %)**, generated
  code included and no exclusion, PIT **99/99 mutations killed (100 %)**. No threshold lowered, no test disabled,
  no new dependency and no migration.
- **Three traps, all of them PostgreSQL's.** The driver cannot bind a `java.time.Instant` (*Can't infer the SQL
  type*), so every timestamp goes in as `Timestamp.from(...)`, as the rest of this service already does. A filter
  left out needs its parameter **cast** (`CAST(:role AS text) IS NULL OR …`), or PostgreSQL cannot infer the type
  of the null and answers *bad SQL grammar*. And `jsonb::text` comes back with spaces (`"role": "RegularUser"`),
  so an assertion on the stored event has to parse the body instead of matching a string.
- **What PIT found.** `EventAdmins` returned the caller's membership and no caller used it, so replacing the
  return value with null survived: the method is now `void require(...)`, which says what it is for. The four
  other survivors were **not this story's** — US-01's `MyMembershipService` had integration coverage only, and
  one branch of `MeService` (an insert refused without a matching subject, #191) had none — so the PR adds
  `MyMembershipServiceTest` and one `MeServiceTest` case to leave the module at 100 %.
- **26 Nov 2026 · review of PR #255 (Bruno de Abreu Lopes), one finding, fixed.** D4 checked a team's liveness only
  when the assignment was *made*, so a team Events deleted afterwards stayed in the member's `teamIds`: the next
  `MembershipChanged` for that member carried a team that no longer exists — against the promise
  `membership.changed.schema.json` makes its three consumers — and the back office's picker still showed it, with
  `?teamId=<deleted>` still matching its old members. The aggregate of `JdbcEventMemberships` and the one
  `JdbcMyMemberships` reads for US-01 now both filter on the live copy, as does the `teamId` filter; D4 says so.
  A `DELETE` of such an assignment clears the leftover row with no version change and nothing published, which is
  also what keeps an undelete of the team from silently putting the member back in it. Written test first:
  `JdbcEventMembershipsIT · a team Events deletes after the assignment leaves the member's teams and the list` and
  `MemberListIT · a team Events deleted is no longer shown, and the DELETE clears the row it left` both failed on
  the old aggregate, and `MembershipsIT` now carries the US-01 case through a real `TeamChanged`. The existing
  cases of `JdbcEventMembershipsIT` had been assigning teams that were in no copy at all, which the filter made
  visible; they now seed the copy, as the use case requires one to assign. Still open on #67, and deliberately not
  decided here: whether deleting a team should **republish** a `MembershipChanged` for each of its members rather
  than let the next change of theirs carry the correction.
- **Open point, left on #67 and not decided here:** the event's status (D11). When US-06 (#61) creates events
  through the API, `event_copy` will always hold one, and refusing a team change in a `Suspended` or `Closed`
  event — which Events already does for the teams themselves — belongs with US-15's role change, so both writes
  of a membership follow one rule instead of two.

# US-31 (#78) · Field team sees routed requests

> **Issue:** [#78](https://github.com/tomasoliveira21/LABDSOF/issues/78) · **Type:** user story (F7, incident backoffice) · **Service:** Reports (owner area: Reports, the AI triage and observability, decision 13 of the [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md)) · **Spec rules:** [README](README.md)
> **Sources:** [`features.md`](../Product/features.md) US-31, §4.2 (`HelpRequests.Respond`: *receive the help requests routed to their team*) and the *≤ 5 s until a routed request appears on a responder's phone* target (polling, guide C5); [domain model](../Technical/domain-model.md) (FieldTask: dispatched to one team, targets exactly one request); [API outline](../Technical/api-outline.md) §4 (`GET …/field-tasks`, *live list of the tasks of my teams, each with its request and its point on the map*, `HelpRequests.Respond`) and §4.1 (the field task document); [`contracts/openapi.yaml`](../../backend/reports/contracts/openapi.yaml) (`listFieldTasks`, the `FieldTask` schema, and the two other entries marked *planned US-31*); [user workflow W3](../Product/user-workflows.md#w3--control-room-handles-a-help-request) step 3; spec [US-29](US-29-route-help-request.md) (the field tasks this story lists: D2 re-route, D6 team deletion, D7 close).
> **Base:** `main` (`6bb71f7`). US-29 (#76, PR #254), its dependency, is merged.

## 1. Source

**Story:** as a field-team member (`HelpRequests.Respond`), I want to see on my phone the requests routed to my team, so that I know where to go. Persona P5.

**This PR:** `GET …/field-tasks`, the live tasks of the caller's responding teams, each with its request's category, point on the venue map and description. **Not in it:** accepting and resolving a task (US-32, US-33), the phone page (the web app's owner, decision 13: what it needs is written on #78).

| # | Acceptance criterion (#78) | Tests (name starts with `US-31 AC-<n>`) |
|---|---|---|
| AC-1 | Given a request is routed to my team, then it appears on my phone within 5 s **[PROPOSED]**, with its category and its point on the venue map | `FieldTasksIT`, `HelpRequestServiceTest` |
| AC-2 | Given a request is routed to another team, then I do not see it | `FieldTasksIT`, `HelpRequestServiceTest`, `EventAccessTest`, `LocalCopyServiceTest` |

AC-1's *within 5 s* is the phone's polling interval plus one answer: the list is read from the database on every poll, with no cache, so a route committed before a poll is in that poll's answer (D4).

## 2. Quality gates

The gates of [#194](194-reports-postgres-outbox.md) §2 for `backend/reports`, unchanged: `mvn verify` green, JaCoCo ≥ 90 % lines and branches, PIT `DEFAULTS` ≥ 80 % on `domain` and `application`, Testcontainers with the Compose images, the production migrations and the broker set-up, `ContractIT`, the `ci.yml` smoke on a fresh stack, `DocumentationIT` for this spec's tests, the diff guard, an independent review and green CI with SonarQube. The new endpoint gets the full security matrix of G3 (`FieldTasksIT`).

## 3. Decisions

D3's description and D5's scope were decided by the story's owner on 27 November 2026, before the work started; D7 on 28 November 2026, after the G8 review found the gap it closes.

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **Who may list, and which tasks.** `HelpRequests.Respond` in the event (`403` without it; an EventAdmin has every permission, so passes). The list has the tasks of the caller's own teams that are not deleted and grant `HelpRequests.Respond`; an EventAdmin in no such team gets an empty list. A non-member, a removed member, a member of another event or a valid token without `sub` gets `404` with the same body; every bad token `401`; a malformed event ID `400` | API outline §4 and the contract (*`HelpRequests.Respond` in at least one team of the event*). *Every task of the event for an EventAdmin:* the story is *my team*, and the control room already sees every request with its `routedTeamId`. *A team that lost `HelpRequests.Respond` keeps showing its tasks:* the permission is what makes a team a field team (features §4.2) |
| D2 | **Every poll answers the whole list: the live tasks (`Open`, `Accepted`) of those teams, oldest dispatch first.** No `updatedSince`, no `serverTime`; a task that ends (cancelled by a re-route, a close or the team's deletion, US-29 D2, D6, D7; later accepted by another member or resolved) is simply no longer in the next answer | *A delta with `updatedSince`, as US-24 and US-26:* it misses the tasks dispatched before a member joined the team, keeps a member's tasks on the phone after they leave it or the team loses the permission, and needs the old team to be told of a re-route; each would need tracking of its own. A team has a handful of live tasks and a field team a few phones, so the whole list is small, and right in every case. The contract's `updatedSince` and `serverTime` are removed (an `updatedSince` sent is ignored) |
| D3 | **Each item is the contract's `FieldTask`, its target with the request's category (a member's, else the AI's suggestion, as the default team uses, US-34 D1), point on the map and description.** `purpose` is `Assist`, `acceptedBy` null until US-32 | The criterion's category and point; the description, decided by the story's owner on 27 November 2026: the responder needs to know what is wrong before they go. *The member's category only:* a request routed by hand before anybody confirmed the AI's category would show none |
| D4 | **The phone polls every 2 s while the screen is visible**, as the control room (US-26 D7), so a route is on the phone within one interval plus one answer, under the ≤ 5 s target. Read in a read-only transaction, no lock, no cache | features.md: *a refresh every 2–3 s on the open screen*; guide C5 (polling) |
| D5 | **The other two entries the contract marks *planned US-31* are not built:** the help-requests list *routed to the caller's teams* (with its `teamId` filter) and the routed team's read of one request. The field-task list carries what they would give the phone; the contract says so, and the `teamId` parameter, ignored until now, is removed | Decided by the story's owner on 27 November 2026. *Build all three:* two lists of the same requests for the same phone, and a third view of a request that no document defines |
| D6 | **No log line per poll**, as the other live lists (US-24, US-26); the correlation-ID filter tags the request, and the access log has the call | A poll every 2 s from each phone would flood the log with lines that say nothing new |
| D7 | **A team that no longer has `HelpRequests.Respond` is treated as deleted:** when its `TeamChanged` is applied, its routed requests go back to the control room's queue and its live tasks are cancelled, the path of US-29 D6. This replaces the last sentence of US-29 D6 (*a team that only loses the permission keeps its tasks*) | D1 lists only responding teams, so those tasks would reach no phone while the control room still saw the requests as routed (G8 review). *Keep listing a non-responding team's tasks:* a member with no other responding team gets `403`, so they would still be lost. *Leave it as a known limit:* the control room would have to notice by itself. A route already refuses a non-responding team (US-29 D1) |

## 4. Features (build order)

| # | Feature | Tests |
|---|---|---|
| F1 | Domain: `EventAccess.respondingTeamIds` (D1), `HelpRequest.effectiveCategory` (D3), the `FieldTask` read model | `EventAccessTest`, `HelpRequestTest` |
| F2 | Application: `HelpRequestService.fieldTasks` with `FieldTasks.live` (D1, D2) | `HelpRequestServiceTest` |
| F3 | Out adapter: `JdbcFieldTasks.live`, the task joined to its request (D2, D3) | `FieldTasksIT` |
| F3b | Application: `LocalCopyService` returns the requests of a team that stops responding (D7) | `LocalCopyServiceTest` |
| F4 | In adapter: the endpoint, the contract marked implemented, the two *planned US-31* entries settled (D1, D3, D5); a smoke check through the gateway | `FieldTasksIT`, `ContractIT` |
| F5 | Documentation | `DocumentationIT` |

## 5. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| A request routed to my team | Listed: `Assist`, `Open`, my team, the request's ID, category, point and description, `acceptedBy` null, the dispatch time | `HelpRequestServiceTest` · `US-31 AC-1 a member of a responding team gets its live tasks`; `FieldTasksIT` · `US-31 AC-1 a routed request appears in the team's list with its category, point and description` |
| A route between two polls | In the next poll | `FieldTasksIT` · `US-31 AC-1 a route is in the next poll` |
| A request with no category of a member's, but an AI suggestion | The suggested category; none at all → `null` | `HelpRequestTest` · `US-31 AC-1 the category is a member's, else the AI's`; `FieldTasksIT` · `US-31 AC-1 a routed request appears in the team's list with its category, point and description` |
| Several tasks | Oldest dispatch first | `FieldTasksIT` · `US-31 AC-1 a routed request appears in the team's list with its category, point and description` |
| A request routed to another team | Not in my list | `HelpRequestServiceTest` · `US-31 AC-2 tasks of other teams are not listed`; `FieldTasksIT` · `US-31 AC-2 a request routed to another team is not listed` |
| A request routed away from my team, closed, or my team deleted | Gone from the next poll | `FieldTasksIT` · `US-31 AC-2 a request routed away, closed or of a deleted team leaves the list` |
| My team loses `HelpRequests.Respond` (`TeamChanged`, not deleted) | Its routed requests back to the queue (`routedTeamId` null, `OnTheWay` → `Seen`), its live tasks `Cancelled`, no history entry; a change that keeps the permission leaves them | `LocalCopyServiceTest` · `US-31 AC-2 a team that stops responding sends its requests back to the queue and its tasks are cancelled` |
| I leave the team | Its tasks are no longer listed (`403` when no other team of mine responds) | `FieldTasksIT` · `US-31 AC-2 a member who leaves the team no longer sees its tasks` |
| My teams: one responding, one without `HelpRequests.Respond`, one deleted, one of another event | Only the responding team's tasks | `EventAccessTest` · `US-31 AC-2 only the member's own responding teams are listed`; `HelpRequestServiceTest` · `US-31 AC-2 tasks of other teams are not listed` |
| An EventAdmin in no responding team | `200`, empty list | `EventAccessTest` · `US-31 AC-2 only the member's own responding teams are listed`; `HelpRequestServiceTest` · `US-31 AC-2 only members with HelpRequests.Respond list tasks` |
| A member without `HelpRequests.Respond` (a participant, the control room) | `403` | `HelpRequestServiceTest` · `US-31 AC-2 only members with HelpRequests.Respond list tasks`; `FieldTasksIT` · `US-31 AC-2 a member without HelpRequests.Respond gets 403` |
| Non-member, removed member, member of another event (an EventAdmin there included), a valid token without `sub` | `404`, same body | `HelpRequestServiceTest` · `US-31 AC-2 only members with HelpRequests.Respond list tasks`; `FieldTasksIT` · `US-31 AC-2 another event's member gets 404 without revealing it` |
| No, expired, wrong-signature, wrong-issuer, wrong-audience, malformed token | `401` | `FieldTasksIT` · `US-31 AC-2 every bad token gets 401` |
| Malformed event ID | `400` | `FieldTasksIT` · `US-31 AC-2 a malformed event ID is rejected` |

## 6. Handoff

**28 November 2026.**

- Baseline on `main` (`6bb71f7`): `mvn -B -ntp -f backend/reports/pom.xml verify` green: 178 unit tests, 258 integration tests, JaCoCo 98.2 % lines and 96.4 % branches, PIT 257 of 257 mutations killed.
- D3 (the description in the task) and D5 (only the field-task list, the other two *planned US-31* entries settled in the contract) were asked before the work and answered by the story's owner on 27 November 2026; D7 was asked after the G8 review and answered on 28 November 2026.
- Built test first, feature by feature (§4). Red before green: the domain and service tests did not compile before `FieldTask`, `respondingTeamIds`, `effectiveCategory`, `fieldTasks` and `FieldTasks.live` existed; `FieldTasksIT` failed on 8 of its 14 tests with the adapter but without the endpoint (the six `401` cases already passed, as the security filter runs first); `ContractIT` failed until `listFieldTasks` was marked implemented; `DocumentationIT` failed until the changelog had the story.
- **G8 independent review** (`code-review` at `high` on `6bb71f7...HEAD`), 6 findings:
  - (1) *important, fixed test first:* a team that loses `HelpRequests.Respond` kept its tasks (US-29 D6), but no phone listed them any more. Asked and decided (D7): such a team is treated as deleted. `LocalCopyServiceTest` failed on the old code (the request stayed routed); the old `US-29 AC-2` test asserted the replaced rule for a team without permissions and now uses a responding team.
  - (2) *important, fixed:* the security and privacy assessment (AC-3) said a routed team sees only the category and the point; its row now names the description, the live task and the reason (D3).
  - (3) *nit, fixed:* the task list filters on `purpose = 'Assist'`, so a Verify or Fix task (US-52) is never shown as a help request.
  - (4) *nit, fixed:* `JdbcFieldTasks.live` builds on `JdbcHelpRequests.COLUMNS`, the columns `map` reads, instead of a copy.
  - (5) *nit, kept:* D3, D5 and D7 have no meeting record. They are the story owner's decisions inside the owner's area (decision 13 of 15 November 2026), recorded in this spec, the CHANGELOG and the API outline, as US-29's were; a meeting record follows if the team changes them.
  - (6) *nit, fixed:* `FieldTask.Status` lost `Answered`, which the contract does not list until US-52.
- **G6** guard: no blocking finding; one warning, the `@SuppressWarnings("java:S115")` on `FieldTask.Status`, the pattern of `Membership` (the constants are the contract values), with its reason in the comment above it. Test census 1379 → 1395.
- **G1** (after the G8 fixes) `mvn -B -ntp -f backend/reports/pom.xml verify`: 184 unit tests, 273 integration tests, JaCoCo 98.2 % lines and 96.4 % branches (≥ 90 %), PIT 263 of 263 mutations killed on `domain` and `application` (≥ 80 %).
- **G5** smoke on a fresh stack (`down --volumes`, every image rebuilt, `up -d --wait`, every check step of the `ci` job, the observability profile included, `down --volumes`) on `4e6ee2c`: 14 of 14 steps pass, with the two new field-task checks through the gateway (`401` without a token, `404` for a non-member). `smoke.sh` matched `ci.yml`.
- **G2** `FieldTasksIT`: real PostgreSQL and RabbitMQ (the Compose images, the production migrations, `setup.sh`); tasks are dispatched by real routes over HTTP, closed over HTTP, and the team deleted through `LocalCopyService`. No database or broker mock.
- **G3** `FieldTasksIT`: every bad token → 401; a participant and a control-room member → 403; an EventAdmin in no responding team → 200 with an empty list; a non-member, a removed member, a member of another event and a valid token without `sub` → 404 with the same body as an unknown event; a malformed event ID → 400.
- **G4** `contracts/openapi.yaml`: `listFieldTasks` implemented, without `updatedSince` and `serverTime`; the target's `description`; the help-requests list and the read of one request point the field team to it, and the ignored `teamId` filter is gone. `ContractIT` passes. No event schema changes.
- **G7** `CHANGELOG.md`, the API outline (§4 tables, the §4.1 example and a dated note), the security and privacy assessment (AC-3), a dated note on US-29 D6, this spec; `DocumentationIT` checks that every test named here exists (at least 14 names, both ACs covered).
- Not done, and not claimed: accepting and resolving a task (US-32, US-33), the phone page (what it needs is on #78).

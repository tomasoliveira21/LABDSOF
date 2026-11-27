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
| AC-2 | Given a request is routed to another team, then I do not see it | `FieldTasksIT`, `HelpRequestServiceTest`, `EventAccessTest` |

AC-1's *within 5 s* is the phone's polling interval plus one answer: the list is read from the database on every poll, with no cache, so a route committed before a poll is in that poll's answer (D4).

## 2. Quality gates

The gates of [#194](194-reports-postgres-outbox.md) §2 for `backend/reports`, unchanged: `mvn verify` green, JaCoCo ≥ 90 % lines and branches, PIT `DEFAULTS` ≥ 80 % on `domain` and `application`, Testcontainers with the Compose images, the production migrations and the broker set-up, `ContractIT`, the `ci.yml` smoke on a fresh stack, `DocumentationIT` for this spec's tests, the diff guard, an independent review and green CI with SonarQube. The new endpoint gets the full security matrix of G3 (`FieldTasksIT`).

## 3. Decisions

D3's description and D5's scope were decided by the story's owner on 27 November 2026, before the work started.

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **Who may list, and which tasks.** `HelpRequests.Respond` in the event (`403` without it; an EventAdmin has every permission, so passes). The list has the tasks of the caller's own teams that are not deleted and grant `HelpRequests.Respond`; an EventAdmin in no such team gets an empty list. A non-member, a removed member, a member of another event or a valid token without `sub` gets `404` with the same body; every bad token `401`; a malformed event ID `400` | API outline §4 and the contract (*`HelpRequests.Respond` in at least one team of the event*). *Every task of the event for an EventAdmin:* the story is *my team*, and the control room already sees every request with its `routedTeamId`. *A team that lost `HelpRequests.Respond` keeps showing its tasks:* the permission is what makes a team a field team (features §4.2) |
| D2 | **Every poll answers the whole list: the live tasks (`Open`, `Accepted`) of those teams, oldest dispatch first.** No `updatedSince`, no `serverTime`; a task that ends (cancelled by a re-route, a close or the team's deletion, US-29 D2, D6, D7; later accepted by another member or resolved) is simply no longer in the next answer | *A delta with `updatedSince`, as US-24 and US-26:* it misses the tasks dispatched before a member joined the team, keeps a member's tasks on the phone after they leave it or the team loses the permission, and needs the old team to be told of a re-route; each would need tracking of its own. A team has a handful of live tasks and a field team a few phones, so the whole list is small, and right in every case. The contract's `updatedSince` and `serverTime` are removed (an `updatedSince` sent is ignored) |
| D3 | **Each item is the contract's `FieldTask`, its target with the request's category (a member's, else the AI's suggestion, as the default team uses, US-34 D1), point on the map and description.** `purpose` is `Assist`, `acceptedBy` null until US-32 | The criterion's category and point; the description, decided by the story's owner on 27 November 2026: the responder needs to know what is wrong before they go. *The member's category only:* a request routed by hand before anybody confirmed the AI's category would show none |
| D4 | **The phone polls every 2 s while the screen is visible**, as the control room (US-26 D7), so a route is on the phone within one interval plus one answer, under the ≤ 5 s target. Read in a read-only transaction, no lock, no cache | features.md: *a refresh every 2–3 s on the open screen*; guide C5 (polling) |
| D5 | **The other two entries the contract marks *planned US-31* are not built:** the help-requests list *routed to the caller's teams* (with its `teamId` filter) and the routed team's read of one request. The field-task list carries what they would give the phone; the contract says so, and the `teamId` parameter, ignored until now, is removed | Decided by the story's owner on 27 November 2026. *Build all three:* two lists of the same requests for the same phone, and a third view of a request that no document defines |
| D6 | **No log line per poll**, as the other live lists (US-24, US-26); the correlation-ID filter tags the request, and the access log has the call | A poll every 2 s from each phone would flood the log with lines that say nothing new |

## 4. Features (build order)

| # | Feature | Tests |
|---|---|---|
| F1 | Domain: `EventAccess.respondingTeamIds` (D1), `HelpRequest.effectiveCategory` (D3), the `FieldTask` read model | `EventAccessTest`, `HelpRequestTest` |
| F2 | Application: `HelpRequestService.fieldTasks` with `FieldTasks.live` (D1, D2) | `HelpRequestServiceTest` |
| F3 | Out adapter: `JdbcFieldTasks.live`, the task joined to its request (D2, D3) | `FieldTasksIT` |
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
| I leave the team | Its tasks are no longer listed (`403` when no other team of mine responds) | `FieldTasksIT` · `US-31 AC-2 a member who leaves the team no longer sees its tasks` |
| My teams: one responding, one without `HelpRequests.Respond`, one deleted, one of another event | Only the responding team's tasks | `EventAccessTest` · `US-31 AC-2 only the member's own responding teams are listed`; `HelpRequestServiceTest` · `US-31 AC-2 tasks of other teams are not listed` |
| An EventAdmin in no responding team | `200`, empty list | `EventAccessTest` · `US-31 AC-2 only the member's own responding teams are listed`; `HelpRequestServiceTest` · `US-31 AC-2 only members with HelpRequests.Respond list tasks` |
| A member without `HelpRequests.Respond` (a participant, the control room) | `403` | `HelpRequestServiceTest` · `US-31 AC-2 only members with HelpRequests.Respond list tasks`; `FieldTasksIT` · `US-31 AC-2 a member without HelpRequests.Respond gets 403` |
| Non-member, removed member, member of another event (an EventAdmin there included), a valid token without `sub` | `404`, same body | `HelpRequestServiceTest` · `US-31 AC-2 only members with HelpRequests.Respond list tasks`; `FieldTasksIT` · `US-31 AC-2 another event's member gets 404 without revealing it` |
| No, expired, wrong-signature, wrong-issuer, wrong-audience, malformed token | `401` | `FieldTasksIT` · `US-31 AC-2 every bad token gets 401` |
| Malformed event ID | `400` | `FieldTasksIT` · `US-31 AC-2 a malformed event ID is rejected` |

## 6. Handoff

_Filled as the work goes._

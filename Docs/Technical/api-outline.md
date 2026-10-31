# Farol — API Outline and Event Catalogue

> Sprint A deliverable 7 (assignment §7.1.2; guide C8). Issue #123.
> What each service exposes over REST and which domain events it publishes and consumes. This is an **outline**: the complete `openapi.yaml` of each service and the formal event schemas are written per story, contract-first (guide C7, [`spec-driven-development.md`](../Process/spec-driven-development.md)).
> Stories follow the numbering in [`features.md` §7](../Product/features.md#7-initial-backlog-user-stories) (US-01 to US-62). Entities follow [`domain-model.md`](domain-model.md). Roles and permissions follow [`features.md` §4](../Product/features.md#4-roles-teams-and-permissions-22-october-2026).
> Items marked **[WAITS: On]** follow the current proposal and wait for another owner's decision On. The outline's own decisions and those still waiting are in §10.
> Aligned on 26 October 2026 with [ADR-006](adr/0006-identity-and-access.md) (identity and access), [ADR-013](adr/0013-api-gateway.md) (gateway, rate limits) and the [Security and Privacy Assessment](security-and-privacy-assessment.md) (AC-9, M-5).
> 27 October 2026: account deletion (guide D7, #135) in §1.4, §2 and §7.4.

## 1. Scope and conventions

### 1.1 Scope

- **Must** and **Should** features (F1–F10, F12–F16). The **Could** features (F11, F17, F18) are listed by name in §9.
- Per endpoint: method, path, purpose, the role or permission required, and the story. Request and response fields only for the core flow (help request, triage, field tasks).
- No service calls another service's REST API: services talk only through events (guide C4, [ADR-004](adr/0004-service-communication.md)).
- Not in this outline: the endpoints of the Sprint A walking skeleton in Reports (`GET /api/v1/status` and the synthetic `/api/v1/demo/help-requests`, only with the `demo` profile). They are diagnostic, not part of the product API ([`openapi.yaml`](../../backend/reports/contracts/openapi.yaml), [run instructions](plans/local-walking-skeleton.md), #166).

### 1.2 Paths

- Every path starts with `/api/v1` (guide C7).
- Paths are **per resource**, with no service prefix: `/api/v1/events/{eventId}/help-requests`. Each path belongs to exactly one service; the gateway routes by path, following the [route table of ADR-013](adr/0013-api-gateway.md#route-table) (guide C6). A new path must fit that table, or the table changes in the same PR.
- Anything that belongs to one event has `{eventId}` in its path, so every service can check membership and filter by event (guide D3, US-14).
- Resource names are plural and kebab-case, and match the entity names in [`domain-model.md`](domain-model.md).

### 1.3 Methods and state changes

- `GET` reads, `POST` creates, `PATCH` changes fields, `DELETE` removes.
- A change of state is an action: **`POST /{id}/<action>`** (e.g. `/publish`, `/close`, `/confirm`, `/accept`). Each action is one use case and, where another service needs it, one domain event.

### 1.4 Identity

- Login, logout and sign-up happen in Keycloak; Auth & Users has no `/login`, `/logout` or sign-up endpoint ([ADR-006](adr/0006-identity-and-access.md), guide E1).
- The account is created **just in time**, at the user's first authenticated `GET /me`; Auth & Users then publishes `UserProfileChanged` (§7.4). Never for a deleted `sub` (tombstone, §2): a token issued before the deletion gets `401`.
- `/me` stands for the account of the token's user. A user's `userId` is the token's `sub`.

### 1.5 Authorisation

- Checked **per event**: the user's role in that event (`EventAdmin` or `RegularUser`) and the permissions of their teams there. `FarolAdmin` is global and gives no access to any event's data.
- Each service decides from its own local copies: memberships and team assignments from Auth & Users (`MembershipChanged`), teams and their permissions from Events (`TeamChanged`). The token carries **only the identity** (`sub`, `email`, `name`, `aud = farol-api`); every service validates it and checks the permission in its use case ([ADR-006](adr/0006-identity-and-access.md), guide E2). A disabled or deleted account is refused at once (`UserStatusChanged`, `UserDeleted`).
- The tables use: **Authenticated** (any logged-in user), **Member** (any member of the event), **EventAdmin**, **FarolAdmin**, or a permission such as `HelpRequests.Triage`. An `EventAdmin` has every permission of their event.

### 1.6 Live lists and pagination

- Live lists (backoffice, request status, announcements, reports on the map) are polled. They take **`updatedSince`** (ISO-8601 timestamp) and return only what changed, plus the `serverTime` to send next time.
- Other lists take `limit` (default 50, max 200) and `cursor`.

### 1.7 Errors

- **Problem Details** (RFC 9457), `application/problem+json`.
- **Rate limits** ([ADR-013](adr/0013-api-gateway.md#rate-limits)), per account, never per IP: 120 calls per minute on every path and **5 help requests per 10 minutes**, at the gateway; 5 reports per 10 minutes per event, in Reports (US-48). Login and sign-up are protected by Keycloak's brute-force detection.
- `400` invalid input, `401` not logged in, `403` no permission, `404` not found **or not a member of the event** (so as not to reveal that the event exists), `409` state conflict (e.g. a field task already accepted), `422` breaks a business rule (e.g. removing the last EventAdmin), `413` file too large (map upload over 20 MB), `429` too many requests, with `Retry-After`, `503` service unavailable, with `Retry-After`.

## 2. Auth & Users

Stories: US-01 to US-05, US-08, US-12, US-15, US-18.

Owns accounts, the `FarolAdmin` grant, memberships of events and team assignments. Keeps local copies of the events (status, name) and their teams (`EventChanged`, `TeamChanged`).

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| — | _identity provider_ | Sign up, log in and log out happen in Keycloak; the web app sends its token on every call (§1.4). Sign-up asks for the terms on Keycloak's form | — | US-01, US-02, US-03 |
| `GET` | `/me` | My account: name, e-mail and global roles (`FarolAdmin`), so the web app knows what to show. The first call creates the account (§1.4) | Authenticated | US-01, US-03 |
| `GET` | `/me/memberships` | The events I am a member of, with my role, my teams and each event's name and status | Authenticated | US-01 |
| `PATCH` | `/me` | Change my display name. The password changes in Keycloak | Authenticated | US-04 |
| `DELETE` | `/me` | Delete my account; `422` if I am the last EventAdmin of an event or the last `FarolAdmin`. `202` while the deletion is pending (flow below) | Authenticated | US-05 |
| `GET` | `/accounts?email=` | Find the account to name as EventAdmin: exact match only, no partial search and no list; answers only the account id and display name; audited (assessment AC-9) | FarolAdmin | US-06, US-08 |
| `POST` | `/events/{eventId}/members` | Name a user EventAdmin of an event (`{ userId, role: EventAdmin }`), the first one or a new one; recorded with who and when | FarolAdmin | US-06, US-08 |
| `POST` | `/events/{eventId}/members/me` | Join the event as RegularUser (QR code or link); `404` if the event is not published in the local copy | Authenticated | US-12 |
| `GET` | `/events/{eventId}/members` | List the event's members with role and teams; filters `role`, `teamId` | EventAdmin | US-15, US-18 |
| `PATCH` | `/events/{eventId}/members/{userId}` | Make a member EventAdmin or RegularUser (`{ role }`); `422` for the last EventAdmin; recorded with who and when | EventAdmin | US-15 |
| `PUT` | `/events/{eventId}/members/{userId}/teams/{teamId}` | Add a member to a team; `404` if the user is not a member or the team is not in the local copy | EventAdmin | US-18 |
| `DELETE` | `/events/{eventId}/members/{userId}/teams/{teamId}` | Remove a member from a team | EventAdmin | US-18 |

- The first EventAdmin of a new event (US-06) is named with the same `POST /events/{eventId}/members`, right after the event is created in Events (D3). The event reaches Auth & Users through `EventChanged`; until then the call returns `404` and the web app retries. If it never succeeds, the event shows no EventAdmin in the FarolAdmin's list (US-07), who names one there (US-08).
- E-mails are not verified (ADR-006), so before naming an account EventAdmin the FarolAdmin confirms with the organisation, outside Farol, that it is theirs (assessment AC-9).
- **Account deletion** (ADR-006, guide D7, #135). In the MVP: US-05 is Must and the GDPR gives the right to erasure.
  1. Refused with `422` while the user is the last EventAdmin of an event (`features.md` §4.1; they name another first, US-15, or the FarolAdmin does, US-08), or the last `FarolAdmin`. Checked in the same transaction that changes memberships, so an event is never left without an EventAdmin.
  2. Otherwise Auth & Users marks the account *pending deletion*, refuses it, and publishes `UserStatusChanged` (`Disabled`), so every service refuses it at once.
  3. Deletes the user in Keycloak (Admin API, client credentials), which also ends its sessions. If Keycloak is down, answers `202`, and a scheduled job retries.
  4. In one transaction: deletes the account, the `FarolAdmin` grant, memberships, team assignments, friendships (both sides) and consent records; keeps a **tombstone** (only the `sub` and `deletedAt`); writes `UserDeleted` to the outbox. The audit records only that a deletion happened, and when (assessment AC-10).
  5. Each consumer deletes or anonymises what it keeps (§7.4) and keeps its own tombstone.
- **Tombstone:** Keycloak's `sub` is a UUID that is never reused and, once the account is gone, points to no one. It refuses a token issued before the deletion (still valid for up to 5 min) and makes late events about that `sub` (e.g. a retried `MembershipChanged`) be ignored, so no deleted copy comes back.
- Publishes `MembershipChanged`, `GlobalRoleChanged`, `UserProfileChanged`, `UserStatusChanged` and `UserDeleted` (§7).

## 3. Events

Stories: US-06, US-07, US-09 to US-11, US-13, US-16, US-17, US-19 to US-22, US-44, US-45, US-58.

Owns events, teams with their permissions and help categories, map versions and points of interest, announcements and check-ins. Keeps local copies of the memberships and team assignments (`MembershipChanged`) and of the `FarolAdmin` grants (`GlobalRoleChanged`).

**Events**

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| `POST` | `/events` | Create an event as a draft (`{ name, organiserName, dates }`); the FarolAdmin then names its first EventAdmin in Auth & Users (§2) | FarolAdmin | US-06 |
| `GET` | `/events` | List every event with its name, dates, organisation, EventAdmins and status only | FarolAdmin | US-07 |
| `GET` | `/events/{eventId}` | The event's details, status and, once published, its join link (the QR code encodes it) | Member, EventAdmin | US-10, US-11, US-12 |
| `PATCH` | `/events/{eventId}` | Edit the name, dates or description of a draft or published event | EventAdmin | US-10 |
| `POST` | `/events/{eventId}/publish` | Publish a draft; generates the join link | EventAdmin | US-11 |
| `POST` | `/events/{eventId}/close` | Close the event: no more help requests or reports | EventAdmin | US-13 |
| `POST` | `/events/{eventId}/suspend` | Suspend the event: participants can no longer open it | FarolAdmin | US-09 |
| `POST` | `/events/{eventId}/reactivate` | Return a suspended event to its previous status | FarolAdmin | US-09 |

**Teams and permissions**

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| `GET` | `/events/{eventId}/teams` | The event's teams with their permissions and help categories | EventAdmin, `HelpRequests.Triage`, `Reports.Review` | US-16, US-29, US-52 |
| `POST` | `/events/{eventId}/teams` | Create a team (`{ name }`), with no members and no permissions; `409` if the name is taken | EventAdmin | US-16 |
| `PATCH` | `/events/{eventId}/teams/{teamId}` | Rename a team or set the help categories it handles (`{ name, handledCategories }`) | EventAdmin | US-16, US-17 |
| `PUT` | `/events/{eventId}/teams/{teamId}/permissions` | Replace the team's permissions (`[{ permission, reportTypes }]`, from the fixed list in `features.md` §4.2); recorded with who and when | EventAdmin | US-17 |
| `DELETE` | `/events/{eventId}/teams/{teamId}` | Delete a team; Reports returns its routed requests to the control-room queue when it consumes `TeamChanged` | EventAdmin | US-16 |

**Venue map**

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| `POST` | `/events/{eventId}/map-versions` | Upload a map (multipart, image or single-page PDF); a PDF is rendered to PNG ([ADR-007](adr/0007-venue-map-representation.md)); `400` for any other file and `413` above 20 MB (ADR-013 L-4), and the current map stays | `Map.Edit` | US-19 |
| `GET` | `/events/{eventId}/map-versions/current` | The current map version with every point of interest (type, name, position) | Member | US-21, US-22 |
| `GET` | `/events/{eventId}/map-versions/{version}` | An older version, for the requests and reports that refer to it | Member | US-27 |
| `GET` | `/events/{eventId}/map-versions/{version}/image` | The map's PNG | Member | US-21 |
| `POST` | `/events/{eventId}/map-versions/{version}/points-of-interest` | Place a point (`{ type, name, position }`) | `Map.Edit` (`Transport.Edit` for a transport stop, §5) | US-20, US-37 |
| `PATCH` | `/events/{eventId}/map-versions/{version}/points-of-interest/{poiId}` | Move or rename a point | `Map.Edit` | US-20 |
| `DELETE` | `/events/{eventId}/map-versions/{version}/points-of-interest/{poiId}` | Remove a point | `Map.Edit` | US-20 |

- Filtering by type of place (US-22) happens in the app, over the points the current version already returns.
- A new map version starts with a copy of the previous version's points, which the team then moves.
- The files stay in a volume of the Events service and their references in its database (ADR-011, local deployment).

**Announcements and check-in**

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| `GET` | `/events/{eventId}/announcements?updatedSince=` | Live list of announcements; removed ones come back once as `removed: true`, so the web app drops them (polling every 10 s; SC6 ≤ 15 s) | Member | US-44, US-45 |
| `POST` | `/events/{eventId}/announcements` | Publish an announcement (`{ message, expiresAt }`) | `Announcements.Publish` | US-44 |
| `PATCH` | `/events/{eventId}/announcements/{announcementId}` | Edit it; it shows the time of the edit | `Announcements.Publish` | US-45 |
| `DELETE` | `/events/{eventId}/announcements/{announcementId}` | Remove it | `Announcements.Publish` | US-45 |
| `POST` | `/events/{eventId}/check-ins/me` | Check in with the venue QR code (`{ venueCode }`); a second check-in changes nothing; `422` if the event is not running | Member | US-58 |

- The venue QR code is not the join link: it carries a code shown only at the venue, so nobody checks in from home.
- Publishes `EventChanged`, `TeamChanged`, `VenueMapVersionPublished`, `PointOfInterestChanged` and `CheckInRecorded` (§7).

## 4. Reports

Stories: US-23 to US-36, US-47 to US-53.

Owns help requests, triage suggestions, community reports, field tasks and their audit history. Keeps local copies of the memberships and team assignments (`MembershipChanged`), the teams with their permissions, help categories and report types (`TeamChanged`), the event status (`EventChanged`) and the map versions (`VenueMapVersionPublished`). The AI triage runs inside Reports, through a queue (§7).

**Help requests**

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| `POST` | `/events/{eventId}/help-requests` | Ask for help with a category and a point on the map (§4.1); `422` if the event is closed or suspended. Sending the same `clientRequestId` again returns the request already saved, so the app can retry safely. After 5 in 10 minutes the gateway answers `429` with `Retry-After` (ADR-013 L-2) | Member | US-23, US-25 |
| `GET` | `/events/{eventId}/help-requests?updatedSince=` | Live list. Each caller sees only what they may: their own requests (`mine=true`), all of the event's (`HelpRequests.Triage`), or those routed to their teams (`HelpRequests.Respond`). Filters `status`, `teamId` | Member | US-24, US-26, US-27, US-31 |
| `GET` | `/events/{eventId}/help-requests/{requestId}` | One request. The participant sees only its status and the time of the last change; the control room also sees the triage suggestion and the default team | Owner, `HelpRequests.Triage`, routed team | US-24, US-29, US-34 |
| `PATCH` | `/events/{eventId}/help-requests/{requestId}` | Set the priority (`{ priority }`); recorded with who, when, old and new value | `HelpRequests.Triage` | US-28 |
| `POST` | `/events/{eventId}/help-requests/{requestId}/acknowledge` | Mark it *Seen* | `HelpRequests.Triage` | US-28 |
| `POST` | `/events/{eventId}/help-requests/{requestId}/route` | Route it to a team (`{ teamId }`); creates a field task; `422` if the team has no `HelpRequests.Respond` | `HelpRequests.Triage` | US-29 |
| `POST` | `/events/{eventId}/help-requests/{requestId}/accept-suggestion` | Take the AI's category, priority and team as the request's values; recorded as the member's decision | `HelpRequests.Triage` | US-35 |
| `POST` | `/events/{eventId}/help-requests/{requestId}/close` | Close it: it leaves the active list | `HelpRequests.Triage` | US-30 |
| `GET` | `/events/{eventId}/help-requests/{requestId}/history` | Full change history (who, when, old and new values) | `HelpRequests.Triage` | US-28 |
| `GET` | `/events/{eventId}/triage-status` | Whether the AI triage is available, for the banner of scenario D1 | `HelpRequests.Triage` | US-36 |

**Community reports**

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| `POST` | `/events/{eventId}/reports` | Report a problem (`{ type, location, clientRequestId }`), saved as *Pending*; `429` with `Retry-After` when the participant reports too often | Member | US-47, US-48 |
| `GET` | `/events/{eventId}/reports?updatedSince=` | Live list. Participants see the confirmed, active reports without the reporter, and their own; reviewers see every report of the types they review | Member | US-47, US-49, US-50 |
| `POST` | `/events/{eventId}/reports/{reportId}/confirm` | Confirm it: it goes on the map and counts for badges | `Reports.Review` for its type | US-50 |
| `POST` | `/events/{eventId}/reports/{reportId}/reject` | Reject it | `Reports.Review` for its type | US-50 |
| `POST` | `/events/{eventId}/reports/{reportId}/unconfirm` | Withdraw the confirmation: it leaves the map and stops counting; recorded with who and when | `Reports.Review` for its type | US-51 |
| `POST` | `/events/{eventId}/reports/{reportId}/dispatch` | Send it to a team to check or fix (`{ teamId, purpose: Verify \| Fix }`); creates a field task; `422` if the team has no `Reports.Respond` | `Reports.Review` for its type | US-52, US-53 |

- Reports expire on their own, after the time of their type (`features.md` §6.1); there is no endpoint for it.

**Field tasks**

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| `GET` | `/events/{eventId}/field-tasks?updatedSince=` | Live list of the tasks of my teams, each with its request or report and its point on the map | `HelpRequests.Respond`, `Reports.Respond` | US-31, US-52 |
| `POST` | `/events/{eventId}/field-tasks/{taskId}/accept` | Accept a task; the help request becomes *On the way*; `409` if another member already accepted it | Member of the task's team | US-32 |
| `POST` | `/events/{eventId}/field-tasks/{taskId}/answer` | Answer a *Verify* task (`{ found: true \| false }`); the answer goes back to the reviewer | Member who accepted it | US-52 |
| `POST` | `/events/{eventId}/field-tasks/{taskId}/resolve` | Mark the help request, or the report of a *Fix* task, resolved | Member who accepted it | US-33, US-53 |

- Publishes `HelpRequestSubmitted` (for its own triage consumer), `ReportConfirmed` and `ReportUnconfirmed` (§7).

### 4.1 Core flow: request and response fields

**Ask for help:** `POST /events/{eventId}/help-requests`

```json
{
  "clientRequestId": "6f1c…",          // generated by the app; makes a retry safe (US-25)
  "category": "Health",                // Health | Safety | LostPerson | Theft | Other
  "location": { "mapVersion": 3, "x": 0.42, "y": 0.77 },  // normalised position on that map version
  "description": "Friend fainted near the bar"            // optional
}
```

**A help request, as the control room sees it:** `GET /events/{eventId}/help-requests/{requestId}`

```json
{
  "id": "hr-81…",
  "eventId": "ev-12…",
  "category": "Health",
  "location": { "mapVersion": 3, "x": 0.42, "y": 0.77 },
  "description": "Friend fainted near the bar",
  "priority": "High",                  // scale: features §5.2, AI-3 [WAITS: O7]
  "status": "Submitted",               // Submitted | Seen | OnTheWay | Resolved | Closed
  "submittedAt": "2027-05-08T23:14:05Z",
  "updatedAt": "2027-05-08T23:14:05Z",
  "defaultTeamId": "tm-03…",           // the team that handles the category (US-29)
  "routedTeamId": null,
  "triage": {
    "state": "Suggested",              // Pending | Suggested | NotTriaged | Accepted | Changed
    "suggestedCategory": "Health",
    "suggestedPriority": "High",
    "suggestedTeamId": "tm-03…",
    "suggestedReason": "Fainting reported; needs first aid",  // short plain text from the AI, escaped when shown (ADR-005 decision 2; assessment T10)
    "notTriagedReason": null           // Timeout | ProviderError | InvalidOutput | Abstained | Unavailable | NoEligibleTeam (US-36; ADR-005)
  }
}
```

- The participant gets only `id`, `category`, `status` and `updatedAt`, which the app shows as *Sent*, *Seen*, *On the way*, *Resolved* or *Closed* (US-24).
- `Unavailable`: the AI was not called because the circuit was open or the request could not start in time ([AI plan](plans/ai-provider-evaluation.md#runtime-and-resilience-contract), *Admission*). `NoEligibleTeam`: no team of the event can respond to the category, so inference is skipped ([AI plan](plans/ai-provider-evaluation.md#event-specific-production-contract), item 3). Added on 31 October 2026 (#162).
- The request is saved and shown at once, with `triage.state: Pending`. If no valid suggestion arrives within 30 s it becomes `NotTriaged` (scenario D1, US-36). A suggestion never overwrites a value a member already set, and never lowers the default priority (NFR-22).

**A field task:** `GET /events/{eventId}/field-tasks?updatedSince=`

```json
{
  "items": [
    {
      "id": "ft-55…",
      "purpose": "Assist",             // Assist (help request) | Verify | Fix (report)
      "status": "Open",                // Open | Accepted | Answered | Resolved | Cancelled
      "teamId": "tm-03…",
      "target": { "kind": "HelpRequest", "id": "hr-81…", "category": "Health",
                  "location": { "mapVersion": 3, "x": 0.42, "y": 0.77 } },
      "acceptedBy": null,
      "dispatchedAt": "2027-05-08T23:15:10Z",
      "updatedAt": "2027-05-08T23:15:10Z"
    }
  ],
  "serverTime": "2027-05-08T23:15:12Z"
}
```

- A field task targets exactly one help request or report. `Cancelled` covers a team deleted while the task was open: the request goes back to the control-room queue (US-16).

## 5. Transports

Stories: US-37 to US-43.

Owns the event's transport options (shuttles and external bus routes), their departures, and the bus observations imported from Porto Digital ([ADR-008](adr/0008-transport-data-source.md)). Keeps local copies of the memberships and team assignments (`MembershipChanged`), the teams' permissions (`TeamChanged`), the event status (`EventChanged`) and the transport stops (`PointOfInterestChanged`).

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| `GET` | `/events/{eventId}/transport-options` | The event's shuttles with their stops, next departures and status (delayed or cancelled, with the time of the change), and the external bus routes | Member | US-39 |
| `POST` | `/events/{eventId}/transport-options` | Add a shuttle (`{ mode: Shuttle, name, stopPoiIds, departures }`) or an external bus route (`{ mode: ExternalBus, externalRoute, stopPoiIds }`) | `Transport.Edit` | US-37, US-41 |
| `PATCH` | `/events/{eventId}/transport-options/{optionId}` | Change its name, stops or departures | `Transport.Edit` | US-37 |
| `DELETE` | `/events/{eventId}/transport-options/{optionId}` | Remove it | `Transport.Edit` | US-37 |
| `POST` | `/events/{eventId}/transport-options/{optionId}/delay` | Mark a shuttle delayed (`{ delayMinutes, note }`); `0` clears it; stored with the time of the change | `Transport.Edit` | US-38 |
| `POST` | `/events/{eventId}/transport-options/{optionId}/cancel` | Mark a shuttle cancelled | `Transport.Edit` | US-38 |
| `GET` | `/events/{eventId}/bus-observations` | Latest bus positions on the event's external routes, with `dataAsOf` (time of the data) and `stale: true` when the source is down or the data is older than the freshness limit | Member | US-40, US-41, US-42 |
| `GET` | `/events/{eventId}/transport-source-status` | Whether the Porto Digital source is available, and the time of the last valid import, for the backoffice warning | EventAdmin, `Transport.Edit` | US-40, US-43 |

- The import from Porto Digital (US-40) is a scheduled job inside Transports, with a timeout and validation; it is never called inside a user's request, and there is no endpoint for it. An invalid import is rejected as a whole and the previous data stays.
- Shuttles are internal data: they are shown even when the source is down (scenario D2).
- Porto Digital gives bus positions, not timetables (domain model, `TransportDeparture`). US-41 ("next regular buses") can only show the buses on their way; departure times come from the shuttles entered by the team. **[WAITS: O6]**
- Stops are points of interest of type *transport stop* in Events, so a member with `Transport.Edit` can place them (US-37): Events accepts `Transport.Edit` for that type of point.
- Publishes nothing.

## 6. Badges

Stories: US-54 to US-57.

Owns each account's badge progress and its badge preferences. Keeps what it needs from the confirmed reports (`ReportConfirmed`, `ReportUnconfirmed`), the check-ins (`CheckInRecorded`) and the events' names (`EventChanged`).

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| `GET` | `/me/badges` | My badges: *Report history*, *Event attendance* and one *Event reporter* per event, each with its count and tier | Authenticated | US-54, US-55, US-57 |
| `GET` | `/me/badges/preferences` | Whether I earn badges and whether others can see them | Authenticated | US-56 |
| `PUT` | `/me/badges/preferences` | Change them (`{ earningEnabled, visibility }`); while earning is off, no new badges are earned | Authenticated | US-56 |

- The counts and tiers are computed from the events above; there is no endpoint to change them. Unconfirming a report lowers the count and recalculates the tier (US-54, US-55).
- The preferences move from Auth & Users (`EngagementPreferences` in the domain model) to Badges, so that Badges decides on its own. **[WAITS: O5]** If they stay in Auth & Users, Auth & Users publishes `EngagementPreferencesChanged` and the two `/me/badges/preferences` endpoints move to `/me/engagement-preferences`.
- There is no endpoint for another user's badges: no Must or Should story shows them (D9). It comes with the story that needs it, e.g. friends (F18), and then respects `visibility`.
- Publishes nothing.

## 7. Event catalogue

### 7.1 Envelope

Every message carries:

| Field | Meaning |
|---|---|
| `messageId` | Unique id; consumers keep the processed ids to ignore duplicates ([`architecture.md`](architecture.md) §6) |
| `type` | Event name, e.g. `HelpRequestSubmitted` |
| `schemaVersion` | Version of the payload schema |
| `occurredAt` | When it happened (ISO-8601, UTC) |
| `producer` | The service that published it |
| `eventId` | The Farol event it belongs to, when there is one |
| `actorUserId` | The `sub` of the user whose action caused it, for audit (ADR-006); empty when the system caused it (a scheduled job, an expiry) |
| `payload` | The event's data (§7.3) |

### 7.2 Topology (RabbitMQ)

- **One topic exchange per producer:** `farol.<service>` (e.g. `farol.reports`).
- **Routing key:** `<aggregate>.<past-tense-verb>` (e.g. `help-request.submitted`).
- **Event name:** PascalCase, past tense (e.g. `HelpRequestSubmitted`).
- **One queue per consumer and producer:** `<consumer>.<producer>-events` (e.g. `badges.reports-events`), with its dead-letter queue.

### 7.3 Payload rule

- **Full state** (event-carried state transfer): the payload has the whole current state the consumers need, with a `version`.
- The consumer upserts its local copy and ignores any version older than the one it has.
- **One exception:** `HelpRequestSubmitted` carries only `requestId`. Its producer and consumer are both Reports, so the triage worker reads the request from Reports' own database, and no help-request text or location sits in RabbitMQ, a dead-letter queue or a message trace (assessment M-5, T11).

### 7.4 Events

Every payload except `HelpRequestSubmitted` also carries `version` (§7.3). `eventId` is in the envelope.

| Event · routing key | Producer | Consumers (why) | Payload |
|---|---|---|---|
| `MembershipChanged` · `membership.changed` | Auth & Users | **Events**, **Reports**, **Transports**: decide who may call each endpoint of an event (role and teams, §1.5) and refuse non-members (US-14); Events also lists each event's EventAdmins (US-07) | `userId`, `role` (`EventAdmin` \| `RegularUser`), `status` (`Active` \| `Removed`), `teamIds` |
| `GlobalRoleChanged` · `global-role.changed` | Auth & Users | **Events**: only the FarolAdmin creates, lists, suspends and reactivates events (US-06, US-07, US-09) | `userId`, `role` (`FarolAdmin`), `granted` |
| `UserProfileChanged` · `user-profile.changed` | Auth & Users | **Reports**: the name of who accepted a field task, in the backoffice; **Events**: the names of the EventAdmins (US-07). Without it, both show only the `userId`. Also published when the account is created (§1.4) | `userId`, `displayName` |
| `UserStatusChanged` · `user-status.changed` | Auth & Users | **Events**, **Reports**, **Transports**, **Badges**: refuse a disabled account at once, without waiting for its token to expire (ADR-006; assessment AC-6). Also published with `Disabled` when the user asks to delete the account (§2) | `userId`, `status` (`Active` \| `Disabled`) |
| `UserDeleted` · `user.deleted` | Auth & Users | Every consumer keeps a tombstone of the `userId`, refuses it and ignores later events about it (§2), then: **Reports**: closes the user's open help requests (system action, empty `actorUserId`) and cancels their open field tasks; removes the requester from their help requests and the reporter from their reports, and deletes their text and map point, so only category or type, priority, status, times and team stay, as statistics; in audit entries, `acceptedBy` and field tasks, keeps the opaque `userId` and deletes the copied display name (the backoffice shows *Deleted user*); deletes the membership copies. **Events**: deletes the user's check-ins and the copies of memberships, `FarolAdmin` grant and display name; audit entries keep the opaque `userId`. **Transports**: deletes the membership copies. **Badges**: deletes progress, preferences and the copies of reports and check-ins. Published by Auth & Users after Keycloak and its own data are deleted (§2; guide D7) | `userId`, `deletedAt` |
| `EventChanged` · `event.changed` | Events | **Auth & Users**: knows the event exists before its first EventAdmin is named (US-06) and accepts joins only to published events (US-12); **Reports**: refuses help requests and reports in a closed or suspended event (US-13, US-09); **Transports**: refuses a suspended event; **Badges**: the event's name on the *Event reporter* badge (US-55) | `name`, `organiserName`, `dates`, `status` (`Draft` \| `Published` \| `Closed` \| `Suspended`) |
| `TeamChanged` · `team.changed` | Events | **Auth & Users**: a member can only be put in a team of the event (US-18); **Reports**: who triages, reviews and responds, the default team for a category (US-29), the teams the AI may suggest (US-34), and the requests to return to the queue when a team is deleted (US-16); **Transports**: who has `Transport.Edit` (US-37) | `teamId`, `name`, `permissions` (`[{ permission, reportTypes }]`), `handledCategories`, `deleted` |
| `VenueMapVersionPublished` · `venue-map-version.published` | Events | **Reports**: a help request or report must point to an existing map version of the event (US-23, US-47) | `mapVersion`, `publishedAt` |
| `PointOfInterestChanged` · `point-of-interest.changed` | Events | **Transports**: a shuttle's stops must be transport-stop points of the event, shown with their names (US-37, US-39) | `poiId`, `mapVersion`, `type`, `name`, `position`, `deleted` |
| `CheckInRecorded` · `check-in.recorded` | Events | **Badges**: counts the event as attended, once (US-57) | `userId`, `checkedInAt` |
| `HelpRequestSubmitted` · `help-request.submitted` | Reports | **Reports** (its own triage consumer): reads the request from its own database, asks the AI for a suggestion and saves it (US-34, §4.1). Published through the outbox, so a saved request is never lost ([`architecture.md`](architecture.md) §6) | `requestId` only (§7.3) |
| `ReportConfirmed` · `report.confirmed` | Reports | **Badges**: adds the report to the reporter's counts and recalculates the tiers (US-54, US-55) | `reportId`, `reporterUserId`, `type`, `confirmed: true`, `confirmedAt` |
| `ReportUnconfirmed` · `report.unconfirmed` | Reports | **Badges**: removes it from the counts and recalculates the tiers, which can go down (US-54, US-55) | `reportId`, `reporterUserId`, `type`, `confirmed: false`, `revokedAt` |

- Transports and Badges publish nothing. The status of a help request reaches the participant by polling (§1.6), not by an event.
- `ReportConfirmed` and `ReportUnconfirmed` carry the same full state, so Badges keeps one row per report and upserts it; a report confirmed, unconfirmed and confirmed again counts once. After its reporter deletes the account, a report is published with an empty `reporterUserId`, and Badges ignores it.
- If the badge preferences stay in Auth & Users (§6), Auth & Users also publishes `EngagementPreferencesChanged` to Badges.

### 7.5 Queues

| Queue | Bound to (routing keys) |
|---|---|
| `auth-users.events-events` | `farol.events`: `event.changed`, `team.changed` |
| `events.auth-users-events` | `farol.auth-users`: `membership.changed`, `global-role.changed`, `user-profile.changed`, `user-status.changed`, `user.deleted` |
| `reports.auth-users-events` | `farol.auth-users`: `membership.changed`, `user-profile.changed`, `user-status.changed`, `user.deleted` |
| `reports.events-events` | `farol.events`: `event.changed`, `team.changed`, `venue-map-version.published` |
| `reports.reports-events` | `farol.reports`: `help-request.submitted` (triage consumer) |
| `transports.auth-users-events` | `farol.auth-users`: `membership.changed`, `user-status.changed`, `user.deleted` |
| `transports.events-events` | `farol.events`: `event.changed`, `team.changed`, `point-of-interest.changed` |
| `badges.auth-users-events` | `farol.auth-users`: `user-status.changed`, `user.deleted` |
| `badges.events-events` | `farol.events`: `event.changed`, `check-in.recorded` |
| `badges.reports-events` | `farol.reports`: `report.confirmed`, `report.unconfirmed` |

Each queue has its dead-letter queue (`<queue>.dlq`); retries and backoff follow [`architecture.md`](architecture.md) §6.

## 8. Coverage

Every Must and Should story (US-01 to US-58, except the Could US-46) has at least one endpoint or event. `…/` stands for `/api/v1/events/{eventId}/`; the other paths start with `/api/v1`.

| Story | Endpoints | Events |
|---|---|---|
| US-01 | Keycloak (§1.4), `GET /me`, `GET /me/memberships` | — |
| US-02 | Keycloak (§1.4) | — |
| US-03 | Keycloak sign-up (§1.4), `GET /me` | `UserProfileChanged` |
| US-04 | `PATCH /me` | — |
| US-05 | `DELETE /me` | `UserDeleted` |
| US-06 | `GET /accounts`, `POST …/members`, `POST /events` | `GlobalRoleChanged`, `EventChanged` |
| US-07 | `GET /events` | `MembershipChanged`, `GlobalRoleChanged`, `UserProfileChanged` |
| US-08 | `GET /accounts`, `POST …/members` | — |
| US-09 | `POST …/suspend`, `POST …/reactivate` | `GlobalRoleChanged`, `EventChanged` |
| US-10 | `GET /events/{eventId}`, `PATCH /events/{eventId}` | — |
| US-11 | `GET /events/{eventId}`, `POST …/publish` | — |
| US-12 | `POST …/members/me`, `GET /events/{eventId}` | `EventChanged` |
| US-13 | `POST …/close` | `EventChanged` |
| US-14 | — | `MembershipChanged` |
| US-15 | `GET …/members`, `PATCH …/members/{userId}` | — |
| US-16 | `GET …/teams`, `POST …/teams`, `PATCH …/teams/{teamId}`, `DELETE …/teams/{teamId}` | `TeamChanged` |
| US-17 | `PATCH …/teams/{teamId}`, `PUT …/teams/{teamId}/permissions` | — |
| US-18 | `GET …/members`, `PUT …/members/{userId}/teams/{teamId}`, `DELETE …/members/{userId}/teams/{teamId}` | `TeamChanged` |
| US-19 | `POST …/map-versions` | — |
| US-20 | `POST …/map-versions/{version}/points-of-interest`, `PATCH …/map-versions/{version}/points-of-interest/{poiId}`, `DELETE …/map-versions/{version}/points-of-interest/{poiId}` | — |
| US-21 | `GET …/map-versions/current`, `GET …/map-versions/{version}/image` | — |
| US-22 | `GET …/map-versions/current` | — |
| US-23 | `POST …/help-requests` | `VenueMapVersionPublished` |
| US-24 | `GET …/help-requests`, `GET …/help-requests/{requestId}` | — |
| US-25 | `POST …/help-requests` | — |
| US-26 | `GET …/help-requests` | — |
| US-27 | `GET …/map-versions/{version}`, `GET …/help-requests` | — |
| US-28 | `PATCH …/help-requests/{requestId}`, `POST …/help-requests/{requestId}/acknowledge`, `GET …/help-requests/{requestId}/history` | — |
| US-29 | `GET …/teams`, `GET …/help-requests/{requestId}`, `POST …/help-requests/{requestId}/route` | `TeamChanged` |
| US-30 | `POST …/help-requests/{requestId}/close` | — |
| US-31 | `GET …/help-requests`, `GET …/field-tasks` | — |
| US-32 | `POST …/field-tasks/{taskId}/accept` | — |
| US-33 | `POST …/field-tasks/{taskId}/resolve` | — |
| US-34 | `GET …/help-requests/{requestId}` | `TeamChanged`, `HelpRequestSubmitted` |
| US-35 | `POST …/help-requests/{requestId}/accept-suggestion` | — |
| US-36 | `GET …/triage-status` | — |
| US-37 | `POST …/map-versions/{version}/points-of-interest`, `POST …/transport-options`, `PATCH …/transport-options/{optionId}`, `DELETE …/transport-options/{optionId}` | `TeamChanged`, `PointOfInterestChanged` |
| US-38 | `POST …/transport-options/{optionId}/delay`, `POST …/transport-options/{optionId}/cancel` | — |
| US-39 | `GET …/transport-options` | `PointOfInterestChanged` |
| US-40 | `GET …/bus-observations`, `GET …/transport-source-status` | — |
| US-41 | `POST …/transport-options`, `GET …/bus-observations` | — |
| US-42 | `GET …/bus-observations` | — |
| US-43 | `GET …/transport-source-status` | — |
| US-44 | `GET …/announcements`, `POST …/announcements` | — |
| US-45 | `GET …/announcements`, `PATCH …/announcements/{announcementId}`, `DELETE …/announcements/{announcementId}` | — |
| US-47 | `POST …/reports`, `GET …/reports` | `VenueMapVersionPublished` |
| US-48 | `POST …/reports` | — |
| US-49 | `GET …/reports` | — |
| US-50 | `GET …/reports`, `POST …/reports/{reportId}/confirm`, `POST …/reports/{reportId}/reject` | — |
| US-51 | `POST …/reports/{reportId}/unconfirm` | — |
| US-52 | `GET …/teams`, `POST …/reports/{reportId}/dispatch`, `GET …/field-tasks`, `POST …/field-tasks/{taskId}/answer` | — |
| US-53 | `POST …/reports/{reportId}/dispatch`, `POST …/field-tasks/{taskId}/resolve` | — |
| US-54 | `GET /me/badges` | `ReportConfirmed`, `ReportUnconfirmed` |
| US-55 | `GET /me/badges` | `EventChanged`, `ReportConfirmed`, `ReportUnconfirmed` |
| US-56 | `GET /me/badges/preferences`, `PUT /me/badges/preferences` | — |
| US-57 | `GET /me/badges` | `CheckInRecorded` |
| US-58 | `POST …/check-ins/me` | — |

- **US-14** (isolation) is also covered by the conventions: `{eventId}` in every event path (§1.2), the per-event check from local copies (§1.5) and `404` for non-members (§1.7). The integration test per service is in guide D3.
- **US-25** (request not sent) is mostly in the app; the API only makes the resend safe with `clientRequestId`.
- **US-40** (import) is a scheduled job inside Transports with no endpoint of its own; its result is what the two endpoints return.

**No service calls another's REST API** (guide C4): every row in §7.4 is a message on RabbitMQ, every consumer in §7.5 reads from its own queue, and each "Requires" column is checked against a local copy. The outbound HTTP calls go only to external or infrastructure systems, never to another Farol service: Transports → Porto Digital, from a scheduled job (§5); Reports → the AI provider, from the triage worker; Auth & Users → the Keycloak Admin API, on account deletion (§2); and every service → Keycloak's keys (JWKS), when they change ([`architecture.md` §9.1](architecture.md#91-resilience-patterns-and-values-j1)).

## 9. Future (Could features)

Not outlined; listed by name only.

- F11 · Weak-network mode (US-46)
- F17 · Entrance status (US-59, US-60)
- F18 · Friends (US-61, US-62)

## 10. Decisions to take

### 10.1 Outline decisions

Decided on 23 October 2026. The outline above already follows them.

| # | Question | Options | Recommended | Where | Answer |
|---|---|---|---|---|---|
| D1 | How are the lists that are not live paginated? | **A.** `limit` (default 50, max 200) and an opaque `cursor` · **B.** `page` and `size` | A: stable when rows are added while paging | §1.6 | **A** |
| D2 | What does a user who is not a member of the event get? | **A.** `404`, as if the event did not exist · **B.** `403` | A: does not reveal that the event exists (US-14) | §1.7 | **A** |
| D3 | How is the first EventAdmin of a new event created? | **A.** Auth & Users creates the membership when it consumes `EventChanged` with `firstEventAdminUserId` (one call by the FarolAdmin) · **B.** The FarolAdmin creates the event, then calls `POST /events/{eventId}/members` | A: the event is never left without an EventAdmin if the second call fails | §2, §7.4 | **B**: one flow for the first and later EventAdmins; an event left without one is fixed through US-08 |
| D4 | Do Reports and Events show people's names, or only their `userId`? | **A.** Names, through `UserProfileChanged` · **B.** Only the `userId` | A: the control room needs to see who is on the way, and the FarolAdmin who the EventAdmins are (US-07) | §7.4 | **A** |
| D5 | What points does a new map version start with? | **A.** A copy of the previous version's points · **B.** None | A: a corrected map rarely moves every point | §3 | **A** |
| D6 | Where are the map files kept? | **A.** A volume of the Events service, with the references in its database · **B.** Inside its database | A: keeps the database small; one volume in the local deployment (ADR-011) | §3 | **A** |
| D7 | What does the venue QR code for check-in carry? | **A.** A venue code, different from the join link · **B.** The join link | A: nobody checks in from home (US-58, `features.md` §6.3) | §3 | **A** |
| D8 | Who places the temporary transport stops? | **A.** Events, which accepts `Transport.Edit` for transport-stop points · **B.** Transports, which keeps its own stops outside the map | A: stops stay on the venue map with the other points (domain model) | §3, §5 | **A** |
| D9 | Is there an endpoint for another user's badges? | **A.** No: remove `GET /users/{userId}/badges` until a story needs it · **B.** Yes, `404` while hidden | A: no Must or Should story shows another user's badges, so hiding them (US-56) needs nothing | §6 | **A** |
| D10 | How does a help request become *Seen*? | **A.** A member with `HelpRequests.Triage` calls `/acknowledge` · **B.** Automatically, when one of them opens it | A: a `GET` changes nothing, and *Seen* is recorded with who saw it | §4 | **A** |
| D11 | How does a field team answer a report it was sent to check? | **A.** A field task with purpose `Verify` and `/answer` (`found: true \| false`) · **B.** A separate resource for report checks | A: one list and one flow for every field task | §4 | **A** |
| D12 | Can the app resend a help request or a report safely? | **A.** Yes, with a `clientRequestId` that returns what was already saved · **B.** No idempotency key | A: a resend after a lost answer never creates a duplicate (US-25) | §4 | **A** |

### 10.2 Decisions of other owners

The outline follows the current proposal and marks it **[WAITS: On]**. When it is decided, the outline is updated in the documentation review. O1 to O4 are closed.

| # | Decision | What the outline assumes | Where |
|---|---|---|---|
| O1 | Gateway routing (guide C6) | ✅ **Closed** (22 October 2026): the route table of [ADR-013](adr/0013-api-gateway.md#route-table) follows the outline's paths | §1.2 |
| O2 | Sign-up and login (guide E1) | ✅ **Closed** (22 October 2026, [ADR-006](adr/0006-identity-and-access.md)): sign-up, login and logout in Keycloak; `POST /accounts` removed, the account is created at the first `GET /me` | §1.4, §2 |
| O3 | What the token carries (guide E2) | ✅ **Closed** (22 October 2026, ADR-006): only the identity; roles and teams come from local copies (`MembershipChanged`, `TeamChanged`) | §1.5 |
| O4 | Account lookup by e-mail (privacy) | ✅ **Closed** (26 October 2026): `GET /accounts?email=` for the FarolAdmin, with the rules of the assessment's AC-9 | §2 |
| O5 | Where the badge preferences live (domain model) | In Badges (`/me/badges/preferences`); if they stay in Auth & Users, it publishes `EngagementPreferencesChanged` | §6 |
| O6 | US-41 and the Porto Digital data (backlog) | Shows the buses on the event's routes, not timetables; the story still says STCP | §5 |
| O7 | Priority scale (`features.md` §5.2, AI-3) | Named levels such as `High`; the values come from AI-3 | §4.1 |

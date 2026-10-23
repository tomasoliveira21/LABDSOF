# Farol — API Outline and Event Catalogue

> Sprint A deliverable 7 (assignment §7.1.2; guide C8). Issue #123. Owner: Bruno.
> What each service exposes over REST and which domain events it publishes and consumes. This is an **outline**: the complete `openapi.yaml` of each service and the formal event schemas are written per story, contract-first (guide C7, [`spec-driven-development.md`](../Process/spec-driven-development.md)).
> Stories follow the numbering in [`features.md` §7](../Product/features.md#7-initial-backlog-user-stories) (US-01 to US-62). Entities follow [`domain-model.md`](domain-model.md). Roles and permissions follow [`features.md` §4](../Product/features.md#4-roles-teams-and-permissions-22-october-2026).
> Items marked **[PROPOSED]** wait for the team; items that depend on another owner's open decision name it.

## 1. Scope and conventions

### 1.1 Scope

- **Must** and **Should** features (F1–F10, F12–F16). The **Could** features (F11, F17, F18) are listed by name in §9.
- Per endpoint: method, path, purpose, the role or permission required, and the story. Request and response fields only for the core flow (help request, triage, field tasks).
- No service calls another service's REST API: services talk only through events (guide C4, [ADR-004](adr/0004-service-communication.md)).

### 1.2 Paths

- Every path starts with `/api/v1` (guide C7).
- Paths are **per resource**, with no service prefix: `/api/v1/events/{eventId}/help-requests`. Each path belongs to exactly one service; the gateway routes by path (guide C6, Guerra).
- Anything that belongs to one event has `{eventId}` in its path, so every service can check membership and filter by event (guide D3, US-14).
- Resource names are plural and kebab-case, and match the entity names in [`domain-model.md`](domain-model.md).

### 1.3 Methods and state changes

- `GET` reads, `POST` creates, `PATCH` changes fields, `DELETE` removes.
- A change of state is an action: **`POST /{id}/<action>`** (e.g. `/publish`, `/close`, `/confirm`, `/accept`). Each action is one use case and, where another service needs it, one domain event.

### 1.4 Identity

- Login and logout happen in the identity provider (Keycloak); Auth & Users has no `/login` or `/logout` (guide E1, Guerra).
- `/me` stands for the account of the token's user.

### 1.5 Authorisation

- Checked **per event**: the user's role in that event (`EventAdmin` or `RegularUser`) and the permissions of their teams there. `FarolAdmin` is global and gives no access to any event's data.
- Each service decides from its own local copies: memberships and team assignments from Auth & Users (`MembershipChanged`), teams and their permissions from Events (`TeamChanged`). What the token carries is guide E2 (Guerra).
- The tables use: **Authenticated** (any logged-in user), **Member** (any member of the event), **EventAdmin**, **FarolAdmin**, or a permission such as `HelpRequests.Triage`. An `EventAdmin` has every permission of their event.

### 1.6 Live lists and pagination

- Live lists (backoffice, request status, announcements, reports on the map) are polled. They take **`updatedSince`** (ISO-8601 timestamp) and return only what changed, plus the `serverTime` to send next time.
- Other lists take `limit` (default 50, max 200) and `cursor`. **[PROPOSED]**

### 1.7 Errors

- **Problem Details** (RFC 9457), `application/problem+json`.
- `400` invalid input, `401` not logged in, `403` no permission, `404` not found **or not a member of the event** (so as not to reveal that the event exists), `409` state conflict (e.g. a field task already accepted), `422` breaks a business rule (e.g. removing the last EventAdmin), `429` too many requests, with `Retry-After` (US-48).

## 2. Auth & Users

Stories: US-01 to US-05, US-08, US-12, US-15, US-18.

Owns accounts, the `FarolAdmin` grant, memberships of events and team assignments. Keeps local copies of the events (status, name) and their teams (`EventChanged`, `TeamChanged`).

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| — | _identity provider_ | Log in and log out happen in Keycloak; the apps send its token on every call (§1.4) | — | US-01, US-02 |
| `GET` | `/me` | My account: name, e-mail and global roles (`FarolAdmin`), so the apps know what to show | Authenticated | US-01 |
| `GET` | `/me/memberships` | The events I am a member of, with my role, my teams and each event's name and status | Authenticated | US-01 |
| `POST` | `/accounts` | Sign up; refused without `termsAccepted` or if the e-mail is already used. Whether sign-up runs here or in Keycloak depends on guide E1 (Guerra) | Public | US-03 |
| `PATCH` | `/me` | Change my display name. The password changes in Keycloak | Authenticated | US-04 |
| `DELETE` | `/me` | Delete my account; `422` if I am the last EventAdmin of an event. Details in the account-deletion issue (guide D7) | Authenticated | US-05 |
| `GET` | `/accounts?email=` | Find the account to name as EventAdmin | FarolAdmin | US-06, US-08 |
| `POST` | `/events/{eventId}/members` | Name a user EventAdmin of an event (`{ userId, role: EventAdmin }`); recorded with who and when | FarolAdmin | US-08 |
| `POST` | `/events/{eventId}/members/me` | Join the event as RegularUser (QR code or link); `404` if the event is not published in the local copy | Authenticated | US-12 |
| `GET` | `/events/{eventId}/members` | List the event's members with role and teams; filters `role`, `teamId` | EventAdmin | US-15, US-18 |
| `PATCH` | `/events/{eventId}/members/{userId}` | Make a member EventAdmin or RegularUser (`{ role }`); `422` for the last EventAdmin; recorded with who and when | EventAdmin | US-15 |
| `PUT` | `/events/{eventId}/members/{userId}/teams/{teamId}` | Add a member to a team; `404` if the user is not a member or the team is not in the local copy | EventAdmin | US-18 |
| `DELETE` | `/events/{eventId}/members/{userId}/teams/{teamId}` | Remove a member from a team | EventAdmin | US-18 |

- The first EventAdmin of a new event (US-06) is not created through this API: Auth & Users creates the membership when it consumes `EventChanged` with the `firstEventAdminUserId` (§3). **[PROPOSED]**, to confirm with Tomás and Guerra.
- Publishes `MembershipChanged`, `GlobalRoleChanged` and, for account deletion, `UserDeleted` (§7).

## 3. Events

Stories: US-06, US-07, US-09 to US-11, US-13, US-16, US-17, US-19 to US-22, US-44, US-45, US-58.

Owns events, teams with their permissions and help categories, map versions and points of interest, announcements and check-ins. Keeps local copies of the memberships and team assignments (`MembershipChanged`) and of the `FarolAdmin` grants (`GlobalRoleChanged`).

**Events**

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| `POST` | `/events` | Create an event as a draft (`{ name, organiserName, dates, firstEventAdminUserId }`) | FarolAdmin | US-06 |
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
| `POST` | `/events/{eventId}/map-versions` | Upload a map (multipart, image or single-page PDF); a PDF is rendered to PNG ([ADR-007](adr/0007-venue-map-representation.md)); `400` for any other file, and the current map stays | `Map.Edit` | US-19 |
| `GET` | `/events/{eventId}/map-versions/current` | The current map version with every point of interest (type, name, position) | Member | US-21, US-22 |
| `GET` | `/events/{eventId}/map-versions/{version}` | An older version, for the requests and reports that refer to it | Member | US-27 |
| `GET` | `/events/{eventId}/map-versions/{version}/image` | The map's PNG | Member | US-21 |
| `POST` | `/events/{eventId}/map-versions/{version}/points-of-interest` | Place a point (`{ type, name, position }`) | `Map.Edit` | US-20 |
| `PATCH` | `/events/{eventId}/map-versions/{version}/points-of-interest/{poiId}` | Move or rename a point | `Map.Edit` | US-20 |
| `DELETE` | `/events/{eventId}/map-versions/{version}/points-of-interest/{poiId}` | Remove a point | `Map.Edit` | US-20 |

- Filtering by type of place (US-22) happens in the app, over the points the current version already returns.
- A new map version starts with a copy of the previous version's points, which the team then moves. **[PROPOSED]**
- The files stay in a volume of the Events service and their references in its database (ADR-011, local deployment). **[PROPOSED]**

**Announcements and check-in**

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| `GET` | `/events/{eventId}/announcements?updatedSince=` | Live list of announcements; removed ones come back once as `removed: true`, so the apps drop them (SC6, 10 s) | Member | US-44, US-45 |
| `POST` | `/events/{eventId}/announcements` | Publish an announcement (`{ message, expiresAt }`) | `Announcements.Publish` | US-44 |
| `PATCH` | `/events/{eventId}/announcements/{announcementId}` | Edit it; it shows the time of the edit | `Announcements.Publish` | US-45 |
| `DELETE` | `/events/{eventId}/announcements/{announcementId}` | Remove it | `Announcements.Publish` | US-45 |
| `POST` | `/events/{eventId}/check-ins/me` | Check in with the venue QR code (`{ venueCode }`); a second check-in changes nothing; `422` if the event is not running | Member | US-58 |

- The venue QR code is not the join link: it carries a code shown only at the venue, so nobody checks in from home. **[PROPOSED]**
- Publishes `EventChanged`, `TeamChanged`, `VenueMapVersionPublished`, `PointOfInterestChanged` and `CheckInRecorded` (§7).

## 4. Reports

Stories: US-23 to US-36, US-47 to US-53.

Owns help requests, triage suggestions, community reports, field tasks and their audit history. Keeps local copies of the memberships and team assignments (`MembershipChanged`), the teams with their permissions, help categories and report types (`TeamChanged`), the event status (`EventChanged`) and the map versions (`VenueMapVersionPublished`). The AI triage runs inside Reports, through a queue (§7).

**Help requests**

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| `POST` | `/events/{eventId}/help-requests` | Ask for help with a category and a point on the map (§4.1); `422` if the event is closed or suspended. Sending the same `clientRequestId` again returns the request already saved, so the app can retry safely | Member | US-23, US-25 |
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
  "priority": "High",                  // scale: features §5.2, AI-3 [PENDING]
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
    "notTriagedReason": null           // Timeout | ProviderError | InvalidOutput (US-36)
  }
}
```

- The participant gets only `id`, `category`, `status` and `updatedAt`, which the app shows as *Sent*, *Seen*, *On the way*, *Resolved* or *Closed* (US-24).
- The request is saved and shown at once, with `triage.state: Pending`. If no valid suggestion arrives within 30 s it becomes `NotTriaged` (scenario D1, US-36). A suggestion never overwrites a value a member already set, and never lowers the default priority (domain model I4).

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

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| | | _To fill in (step 3)_ | | |

## 6. Badges

Stories: US-54 to US-57.

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| | | _To fill in (step 3)_ | | |

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
| `payload` | The event's data (§7.3) |

### 7.2 Topology (RabbitMQ)

- **One topic exchange per producer:** `farol.<service>` (e.g. `farol.reports`).
- **Routing key:** `<aggregate>.<past-tense-verb>` (e.g. `help-request.submitted`).
- **Event name:** PascalCase, past tense (e.g. `HelpRequestSubmitted`).
- **One queue per consumer and producer:** `<consumer>.<producer>-events` (e.g. `badges.reports-events`), with its dead-letter queue.

### 7.3 Payload rule

- **Full state** (event-carried state transfer): the payload has the whole current state the consumers need, with a `version`.
- The consumer upserts its local copy and ignores any version older than the one it has.

### 7.4 Events

| Event | Producer | Consumers (why) | Payload |
|---|---|---|---|
| | | _To fill in (step 4)_ | |

## 8. Coverage

Every Must and Should story has at least one endpoint or event.

| Story | Endpoint or event |
|---|---|
| | _To fill in (step 5)_ |

## 9. Future (Could) and open points

### 9.1 Could features, not outlined

- F11 · Weak-network mode (US-46)
- F17 · Entrance status (US-59, US-60)
- F18 · Friends (US-61, US-62)

### 9.2 Open points

_To fill in as the outline is reviewed._

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
- `400` invalid input, `401` not logged in, `403` no permission, `404` not found **or not a member of the event** (so as not to reveal that the event exists), `409` state conflict (e.g. a field task already accepted), `422` breaks a business rule (e.g. removing the last EventAdmin).

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

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| | | _To fill in (step 3)_ | | |

## 4. Reports

Stories: US-23 to US-36, US-47 to US-53.

| Method | Path | Purpose | Requires | Story |
|---|---|---|---|---|
| | | _To fill in (step 3)_ | | |

### 4.1 Core flow: request and response fields

_To fill in (step 3): help request, triage suggestion, field tasks._

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

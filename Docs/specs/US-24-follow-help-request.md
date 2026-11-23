# US-24 · Follow my help request

> **Issue:** [#72](https://github.com/tomasoliveira21/LABDSOF/issues/72) · **Type:** user story (F6, Help request) · **Service:** Reports · **Spec rules:** [README](README.md)
> **Sources:** issue #72; [`features.md`](../Product/features.md) US-24 and the Sprint B cut line; [API outline](../Technical/api-outline.md) §1.5 (authorisation), §1.6 (live lists), §1.7 (errors), §4; [decisions guide](../Technical/architecture-decisions-guide.md) C5 (polling) and J3 (peak); [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md) decisions 6 (one status set) and 13 (ownership); specs [#194](194-reports-postgres-outbox.md) (participant view of `GET …/{requestId}`) and [US-26](US-26-live-help-request-list.md) (D1 leaves `mine=true` to this story, D3 overlap, D5 `updated_at`).
> **Base: `main`** (`d517d76`).
> **Dependencies:** US-23 (#71) is not closed, but what this story reads is already in `main`: `POST …/help-requests` and the participant view of `GET …/help-requests/{requestId}` (#194). The status changes of US-28 to US-33 are not all in `main`; the tests change the status in the database, as US-26's do.

## 1. Source

**Story:** as a participant, I want to follow the status of my help request, so that I know whether help is coming. Persona P1.

**This PR:** `GET /api/v1/events/{eventId}/help-requests?mine=true&updatedSince=` in Reports, in the participant view, and its contract. The participant view of one request (`GET …/help-requests/{requestId}`: description, status, time of the last change) is already in `main` (#194); this story adds its acceptance tests. **Not in it:** the participant's page (web app; decision 13: what it needs is written on #72); the filters `status` and `teamId` (US-27, US-31).

| # | Acceptance criterion | Tests (name starts with `US-24 AC-<n>`) |
|---|---|---|
| AC-1 | Given I submitted a request, when I open it, then I see its status (*Sent*, *Seen*, *On the way*, *Resolved* or *Closed*) and the time of the last change | `FollowRequestIT`, `HelpRequestServiceTest` |
| AC-2 | Given its status changes, then I see the new status within 5 s while the screen is open (polling, guide C5) | `FollowRequestIT`, `HelpRequestServiceTest` |

## 2. Quality gates

The gates of [#194](194-reports-postgres-outbox.md) §2 for `backend/reports`, unchanged: `mvn verify` green, JaCoCo ≥ 90 % lines and branches, PIT `DEFAULTS` ≥ 80 % on `domain` and `application`, Testcontainers with the Compose images and the production migrations, the security matrix on the new parameter, `ContractIT`, the `ci.yml` smoke on a fresh stack, `DocumentationIT` for this spec's test names. No threshold is lowered, no test disabled, no exclusion added.

## 3. Decisions

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **`mine=true` lists the caller's own requests of the event, in the participant view** (`id`, `description`, `status`, `updatedAt`), for **any active member**. A member with `HelpRequests.Triage` gets their own requests too: `mine` means mine. A user who is not an active member of the event gets **`404`**, as for the control-room list. Without `mine` (or `mine=false`) nothing changes: the control-room list of US-26, `403` without `HelpRequests.Triage` | US-26 D1 left this to US-24. *Control-room view for a triage member asking `mine`:* it would make the shape of the answer depend on who asks for the same query; the web app's participant page needs one shape |
| D2 | **First load** (no `updatedSince`): every own request, **`Closed` ones included**, in order of arrival. **Later polls:** every own request whose `updatedAt` is after `updatedSince` | The AC lists *Closed* among the statuses the participant sees, so a closed request stays on the participant's screen (unlike the control room's active list, US-26 D2). The triage deadline of US-26 D4 is not applied: the participant view does not show the triage, so the request would come back unchanged |
| D3 | **The time of the last change is `updatedAt`**, as the participant view of #194 already returns it. It moves on every change of the request (US-26 D5), including category, priority and triage changes the participant does not see | *A `status_changed_at` column:* exact, but a migration `V5` now clashes with the `V5` of #245 (US-28), and US-28 to US-33 would all have to set it. *Derive it from the history of US-28:* that history is not in `main` and does not record status yet. A time that moves while the status stays the same tells the participant that the control room is working on it, which is true; revisit if the page shows otherwise |
| D4 | **`serverTime` = server's time minus 5 s**, the same overlap as US-26 D3; the page keeps one row per `id` | Same reason as US-26 D3: `updated_at` is set before the commit |
| D5 | **Poll every 3 s while the request screen is open**, recommended to the web app; no polling when it is closed | AC-2 asks for 5 s. A change waits at most the interval, plus the read; 3 s leaves 2 s of margin. Only participants with the screen open poll, a small part of the J3 peak, whose 10 s polling is for the map and the lists |
| D6 | **No pagination** on `mine=true` | A participant can send 5 requests in 10 minutes (ADR-013 L-2), so a few dozen in an event at most. The query reads the unique index `(event_id, requester_id, client_request_id)` |
| D7 | **The status names are the contract's** (`Submitted`, `Seen`, `OnTheWay`, `Resolved`, `Closed`); the page shows *Sent* for `Submitted` and *On the way* for `OnTheWay` | Decision 6 of 15 November 2026 set one status set; the words of the AC are the page's labels |

## 4. Features (build order)

| # | Feature | Tests |
|---|---|---|
| F1 | Application: `HelpRequestService.mine`, port `ofRequester` (D1, D2, D4) | `HelpRequestServiceTest` |
| F2 | JDBC adapter, controller `mine=true`, contract | `FollowRequestIT`, `ContractIT` |
| F3 | Documentation: changelog, this spec | `DocumentationIT` |

## 5. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| The owner opens a request | Description, status and the time of the last change; no category, priority, location or triage | `FollowRequestIT` · `US-24 AC-1 the owner opens a request and sees its status and the time of the last change` |
| Every status | Each of the five is shown as stored | `FollowRequestIT` · `US-24 AC-1 every status is shown to the owner` |
| Another member opens someone's request | `404` | `FollowRequestIT` · `US-24 AC-1 another member gets 404 for someone's request` |
| First load with `mine=true` | Own requests only, `Closed` included, oldest first, participant view | `FollowRequestIT` · `US-24 AC-1 mine lists only my requests, closed ones included, in the participant view`; `HelpRequestServiceTest` · `US-24 AC-1 mine lists my requests and later polls list what changed` |
| Requests of another event, or of another user | Never listed | `FollowRequestIT` · `US-24 AC-1 mine lists only my requests, closed ones included, in the participant view` |
| A member with `HelpRequests.Triage` asks `mine` | Their own requests, participant view | `HelpRequestServiceTest` · `US-24 AC-1 a triage member asking mine gets only their own requests` |
| `mine=false` | The control-room list (`403` without `HelpRequests.Triage`) | `FollowRequestIT` · `US-24 AC-1 mine=false is the control-room list` |
| The status changes after the last poll | In the next poll with the new status and a later `updatedAt` | `FollowRequestIT` · `US-24 AC-2 a status change is in the next poll`; `HelpRequestServiceTest` · `US-24 AC-1 mine lists my requests and later polls list what changed` |
| Polling every 3 s | A status change is seen within 5 s | `FollowRequestIT` · `US-24 AC-2 polling every 3 s shows a status change within 5 s` |
| Changed exactly at `updatedSince` | Not listed (strictly after) | `HelpRequestServiceTest` · `US-24 AC-1 mine lists my requests and later polls list what changed` |
| A clock with nanoseconds | `serverTime` to the microsecond, 5 s back | `HelpRequestServiceTest` · `US-24 AC-2 serverTime overlaps the polls by 5 s, to the microsecond` |
| Triage deadline passes, nothing else changes | Not listed again (D2) | `HelpRequestServiceTest` · `US-24 AC-1 mine lists my requests and later polls list what changed` |
| No requests yet | Empty `items` with a `serverTime` | `FollowRequestIT` · `US-24 AC-1 a member with no requests gets an empty list` |
| Not a member, `Removed`, member of another event only | `404`, same body as an unknown event | `FollowRequestIT` · `US-24 AC-1 a non-member, a removed member or a member of another event gets 404`; `HelpRequestServiceTest` · `US-24 AC-1 a non-member asking mine gets 404` |
| `mine` or `updatedSince` malformed | `400` | `FollowRequestIT` · `US-24 AC-2 a malformed mine or updatedSince is rejected` |
| Description with SQL or HTML | Listed as plain text | `FollowRequestIT` · `US-24 AC-1 injection text is listed as plain text` |
| No token / expired / wrong signature / wrong issuer / wrong audience / not a JWT | `401` | `FollowRequestIT` · `US-24 AC-1 every bad token gets 401` |
| The contract | `mine` documented as implemented, both list shapes in `openapi.yaml` | `ContractIT` · `194 AC-6 every endpoint Reports serves is in openapi.yaml as implemented, and the reverse` |

## 6. Handoff and changelog

- **23 Nov 2026 · start.** Branch `feature/72-follow-help-request` from `main` (`d517d76`).

# US-30 (#77) · Close a request

> **Issue:** [#77](https://github.com/tomasoliveira21/LABDSOF/issues/77) · **Type:** user story (F7, incident backoffice) · **Service:** Reports (owner area: Reports, the AI triage and observability, decision 13 of the [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md)) · **Spec rules:** [README](README.md)
> **Sources:** [`features.md`](../Product/features.md) US-30 and §8 (cut line: US-30 is the last story dropped); the meeting of 15 November 2026, decision 6 and §4 (*any status → `Closed` (control room, US-30)*); [domain model](../Technical/domain-model.md) (RequestStatus: `Submitted`, `Seen`, `OnTheWay` or `Resolved` → `Closed`; `Closed` is final); [API outline](../Technical/api-outline.md) §4 (`POST …/help-requests/{requestId}/close`, `HelpRequests.Triage`); [`contracts/openapi.yaml`](../../backend/reports/contracts/openapi.yaml) (`closeHelpRequest`: `409` when it is already `Closed`); specs [US-28](US-28-priority-status-history.md) (D1 to D9: the acknowledge this action mirrors), [US-26](US-26-live-help-request-list.md) D2 (a closed request leaves the first load and is sent once in the next poll) and [US-24](US-24-follow-help-request.md) D2 (the participant keeps their closed requests).
> **Base:** `main` (`e020091`). US-28 (#75), its only dependency, is merged (PR #245).

## 1. Source

**Story:** as a control-room member, I want to close a request, so that finished cases leave the active queue. Persona P2.

**This PR:** `POST …/help-requests/{requestId}/close`, recorded as a `status` history entry with the member and their name; a closed request refuses the control room's later changes. **Not in it:** cancelling the request's open field task (there are no field tasks before US-29, #76; the story that creates them decides what a close does to them); the system close of a deleted user's requests (US-05); the backoffice page (the web app's owner, decision 13: what it needs is written on #77).

| # | Acceptance criterion (#77) | Tests (name starts with `US-30 AC-1`) |
|---|---|---|
| AC-1 | Given I close a request, then it leaves the active list, the participant sees it *Closed*, and the closing is recorded | `CloseRequestIT`, `HelpRequestServiceTest`, `HelpRequestTest` |

## 2. Quality gates

The gates of [#194](194-reports-postgres-outbox.md) §2 for `backend/reports`, unchanged: `mvn verify` green, JaCoCo ≥ 90 % lines and branches, PIT `DEFAULTS` ≥ 80 % on `domain` and `application`, Testcontainers with the Compose images, the production migrations and the broker set-up, `ContractIT`, the `ci.yml` smoke on a fresh stack, `DocumentationIT` for this spec's tests, the diff guard, an independent review and green CI with SonarQube. The new endpoint gets the full security matrix of G3 (`CloseRequestIT`).

## 3. Decisions

Decisions the documents leave open are marked **Proposed, for the team's review**.

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **Close: `POST …/close`, no body**, from `Submitted`, `Seen`, `OnTheWay` or `Resolved`: the status becomes `Closed`, one `status` history entry (old status → `Closed`) with the member and their name, `version` + 1, `updated_at` moves. An already `Closed` request is `409`, nothing written. The triage is untouched | Decision 6 (*any status → `Closed`*), the domain model's transitions and the planned contract (`409 when it is already Closed`). *Idempotent `200` on a closed request:* a retry after a lost response would be quiet, but two members closing at once could not tell who closed it, and the acknowledge already answers `409` the same way (US-28 D2); the web app reads the request again on a `409` |
| D2 | **Proposed, for the team's review: a closed request is final for the control room.** `PATCH` and `accept-suggestion` on a `Closed` request are `409`, nothing written; the acknowledge already is (US-28 D2) | The domain model ends the lifecycle at `Closed` (`Closed → [*]`) and the story says finished cases leave the queue; a change after the close would add history entries after the closing and send the request to the live list again. *Allow changes after the close* (to correct statistics): no story asks for it. The AI's late suggestion is not a member change and keeps its own rule (US-34 D6, D8): it can only land within 30 s of the submission, never changes the status, and writes no history entry |
| D3 | **The close takes the same locks as every other decision** (`findForUpdate`: triage, then request) and decides on what it reads, so two closes at the same time give one `200` and one `409`, one entry | US-35 D4 and US-28 D3: one lock order for every writer |
| D4 | **The live list and the participant need no change:** the first load of the control room's list leaves `Closed` out and the next poll sends it once, so the page drops it (US-26 D2); the participant keeps it, with `status: Closed`, in their own list and in the read of one request (US-24 D2) | Both rules shipped with US-26 and US-24; this story only makes the status reachable through the API |
| D5 | **Who may act:** `HelpRequests.Triage` in the request's event (`403` without it); a non-member, a removed member, a member of another event, a valid token without `sub`, a request of another event or unknown gets `404` with the same body; every bad token gets `401`; a malformed ID gets `400` | API outline §4 and US-28 D7; G3 |
| D6 | **The close is logged at `INFO` with the request ID and the status before and after**, never the description or the user; it answers `200` with the control-room view after it, default team included | US-28 D8, D9 and US-34 D4 |

## 4. Features (build order)

| # | Feature | Tests |
|---|---|---|
| F1 | Domain: `HelpRequest.close`; `Closed` refuses `acceptSuggestion` and `change` (D1, D2) | `HelpRequestTest` |
| F2 | Application: `HelpRequestService.close`, saved with the `Actor` through the acknowledge's path (D1, D3, D5, D6) | `HelpRequestServiceTest` |
| F3 | In adapter: the endpoint, the contract marked implemented, `409` on the `PATCH` (D1, D2, D5); smoke checks through the gateway | `CloseRequestIT`, `ContractIT` |
| F4 | Documentation | `DocumentationIT` |

## 5. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| Close a `Submitted`, `Seen`, `OnTheWay` or `Resolved` request, with any triage state | Status `Closed`, `version` + 1, `updatedAt` moves, the triage unchanged; one `status` entry from the old status to `Closed` | `HelpRequestTest` · `US-30 AC-1 closing moves any open status to Closed and records it` |
| Close a `Closed` request | `409`, nothing written | `HelpRequestTest` · `US-30 AC-1 a closed request cannot be closed again`; `CloseRequestIT` · `US-30 AC-1 closing a closed request is a conflict` |
| The close through the API | `200` with the control-room view, `Closed`; the entry has the member, their name and the time | `HelpRequestServiceTest` · `US-30 AC-1 a close is saved as the member's change with their name`; `CloseRequestIT` · `US-30 AC-1 the control room closes a request and the closing is recorded` |
| Active list after a close | Not in a first load; listed once, `Closed`, in the next poll | `CloseRequestIT` · `US-30 AC-1 a closed request leaves the active list` |
| Participant after a close | Reads `Closed` in their request and in their own list | `CloseRequestIT` · `US-30 AC-1 the participant sees the request Closed` |
| Change, accept or acknowledge a closed request | `409`, nothing written | `HelpRequestTest` · `US-30 AC-1 a closed request refuses the control room's changes`; `CloseRequestIT` · `US-30 AC-1 a closed request refuses the control room's changes` |
| Two closes at the same time | One `200`, one `409`; one entry | `CloseRequestIT` · `US-30 AC-1 two closes at the same time record one closing` |
| Log | `INFO` with the request ID and the statuses; no description or user | `HelpRequestServiceTest` · `US-30 AC-1 a close is logged without the description or the user` |
| Member without `HelpRequests.Triage` | `403`, nothing written | `HelpRequestServiceTest` · `US-30 AC-1 only the control room closes`; `CloseRequestIT` · `US-30 AC-1 a member without HelpRequests.Triage gets 403` |
| Non-member, removed member, member of another event, a valid token without `sub`, request of another event, unknown request | `404`, same body, nothing written | `HelpRequestServiceTest` · `US-30 AC-1 only the control room closes`; `CloseRequestIT` · `US-30 AC-1 another event's member or request gets 404 without revealing it` |
| No, expired, wrong-signature, wrong-issuer, wrong-audience, malformed token | `401` | `CloseRequestIT` · `US-30 AC-1 every bad token gets 401` |
| Malformed event or request ID | `400` | `CloseRequestIT` · `US-30 AC-1 a malformed ID is rejected` |
| A body sent with the close | Ignored: the action takes none | `CloseRequestIT` · `US-30 AC-1 the control room closes a request and the closing is recorded` |

## 6. Handoff

**24 November 2026.**

- Baseline on `main` (`e020091`): `mvn -B -ntp -f backend/reports/pom.xml verify` green: 142 unit tests, 212 integration tests, JaCoCo 98.0 % lines and 96.1 % branches, PIT 201 of 201 mutations killed.

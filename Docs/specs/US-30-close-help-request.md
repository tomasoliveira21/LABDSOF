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
| D2 | **Proposed, for the team's review: a closed request is final for the control room.** `PATCH` and `accept-suggestion` on a `Closed` request are `409`, nothing written; the acknowledge already is (US-28 D2) | The domain model ends the lifecycle at `Closed` (`Closed → [*]`) and the story says finished cases leave the queue; a change after the close would add history entries after the closing and send the request to the live list again. *Allow changes after the close* (to correct statistics): no story asks for it. **A closed request also takes no AI result** (G8 review): the worker does not call the model for it, and a result that arrives after the close is not written, so the request is not sent to the live list again, its triage stays as it was, and the triage metrics do not count a suggestion nobody can decide. The worker's save takes the members' locks in their order and reads the status after them (a condition in its `UPDATE` alone would read the status as it was before it waited on the close) |
| D3 | **The close takes the same locks as every other decision** (`findForUpdate`: triage, then request) and decides on what it reads, so two closes at the same time give one `200` and one `409`, one entry | US-35 D4 and US-28 D3: one lock order for every writer |
| D4 | **The live list and the participant:** the first load of the control room's list leaves `Closed` out and the next poll sends it once, so the page drops it (US-26 D2); the participant keeps it, with `status: Closed`, in their own list and in the read of one request (US-24 D2). A closed request still `Pending` is not sent again when its 30 s triage deadline passes (it awaits no triage; G8 review), so *once* holds | Both rules shipped with US-26 and US-24; until this story a request could not be closed within 30 s, so US-26 accepted a closed request coming back at the deadline as harmless; its test now expects it not to |
| D5 | **Who may act:** `HelpRequests.Triage` in the request's event (`403` without it); a non-member, a removed member, a member of another event, a valid token without `sub`, a request of another event or unknown gets `404` with the same body; every bad token gets `401`; a malformed ID gets `400` | API outline §4 and US-28 D7; G3 |
| D6 | **The close is logged at `INFO` with the request ID and the status before and after**, never the description or the user; it answers `200` with the control-room view after it, default team included | US-28 D8, D9 and US-34 D4 |

## 4. Features (build order)

| # | Feature | Tests |
|---|---|---|
| F1 | Domain: `HelpRequest.close`; `Closed` refuses `acceptSuggestion` and `change`; `HelpRequest.awaitsModel` (D1, D2, D4) | `HelpRequestTest` |
| F2 | Application: `HelpRequestService.close`, saved with the `Actor` through the acknowledge's path (D1, D3, D5, D6); the triage worker skips a closed request (D2) | `HelpRequestServiceTest`, `TriageServiceTest` |
| F2b | Out adapter: `JdbcHelpRequests.saveTriage` takes the members' locks and writes nothing on a closed request (D2) | `CloseRequestIT` |
| F3 | In adapter: the endpoint, the contract marked implemented, `409` on the `PATCH` (D1, D2, D5); smoke checks through the gateway | `CloseRequestIT`, `ContractIT` |
| F4 | Documentation | `DocumentationIT` |

## 5. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| Close a `Submitted`, `Seen`, `OnTheWay` or `Resolved` request, with any triage state (`Pending`, derived `NotTriaged`, `Suggested`, `Accepted`, `Changed`, stored `NotTriaged`) | Status `Closed`, `version` + 1, `updatedAt` moves, the triage unchanged; one `status` entry from the old status to `Closed` | `HelpRequestTest` · `US-30 AC-1 closing moves any open status to Closed and records it` |
| Close a `Closed` request | `409`, nothing written | `HelpRequestTest` · `US-30 AC-1 a closed request cannot be closed again`; `CloseRequestIT` · `US-30 AC-1 closing a closed request is a conflict` |
| The close through the API | `200` with the control-room view, `Closed`; the entry has the member, their name and the time | `HelpRequestServiceTest` · `US-30 AC-1 a close is saved as the member's change with their name`; `CloseRequestIT` · `US-30 AC-1 the control room closes a request and the closing is recorded` |
| Active list after a close | Not in a first load; listed once, `Closed`, in the next poll | `CloseRequestIT` · `US-30 AC-1 a closed request leaves the active list` |
| Participant after a close | Reads `Closed` in their request and in their own list | `CloseRequestIT` · `US-30 AC-1 the participant sees the request Closed` |
| Change, accept or acknowledge a closed request | `409`, nothing written | `HelpRequestTest` · `US-30 AC-1 a closed request refuses the control room's changes`; `CloseRequestIT` · `US-30 AC-1 a closed request refuses the control room's changes` |
| A `PATCH` without category or priority on a closed request | `400`, as on an open one: the body is checked before the status | `HelpRequestTest` · `US-30 AC-1 a closed request refuses the control room's changes` |
| Closed before the AI worker reads it | The model is not called; nothing written or counted | `TriageServiceTest` · `US-30 AC-1 a closed request is not triaged, and a result after the close is not saved` |
| Closed while the model answers (the worker's save waits on the close's locks) | The result is not written: triage unchanged, `version` and `updatedAt` the close's; not counted | `TriageServiceTest` · `US-30 AC-1 a closed request is not triaged, and a result after the close is not saved`; `CloseRequestIT` · `US-30 AC-1 an AI result that arrives after the close is not written` |
| A closed `Pending` request when its triage deadline passes | Not listed again by the control room's poll | `HelpRequestTest` · `US-30 AC-1 a closed request awaits no triage and is not listed again at its deadline` |
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
- Built test first, feature by feature (§4). Red before green: the domain and service tests did not compile before `close` existed; `CloseRequestIT` failed with `404` on 9 of its 15 tests before the endpoint was served (the `401` cases already passed, as the security filter runs first).
- **G8 independent review** (`code-review` at `high` on `e020091...HEAD`), 4 findings, all fixed, each test first (red against the code before the fix):
  - (1) *important:* the AI worker still wrote its result on a request closed within 30 s of its submission, which sent it to the live list again and counted a suggestion nobody could decide. Now a closed request awaits no triage (`HelpRequest.awaitsModel`), the worker skips it, and `saveTriage` takes the members' locks and reads the status after them (D2). The race test failed on the old code: the worker's save returned `true` after waiting on the close.
  - (2) a closed `Pending` request came back once more in the poll that crossed its triage deadline: no longer (D4). The US-26 service test `US-26 AC-1 the first load lists the open requests and later polls list what changed` asserted that it came back (*harmless*) and now asserts that it does not; the rule is stricter, not weaker.
  - (3) a `PATCH` without category or priority on a closed request answered `409`, not `400`: the body is now checked first.
  - (4) the close test covered only `Pending`, `Suggested` and `Accepted`: `Changed` and a stored `NotTriaged` added.
- **G5** smoke on a fresh stack (`down --volumes`, images built one by one, `up -d --wait`, every check step of the `ci` job, the observability profile included, `down --volumes`): 14 of 14 steps pass, with the two new close checks through the gateway. `smoke.sh` matched `ci.yml`.
- **G6** guard: no blocking finding, no warning; test census 963 → 980 before the G8 fixes.
- One unit run during the G8 fixes had `OllamaTriageModelTest` `timeout` red (one request seen instead of two) while the G5 stack was running on the same machine; it passed in the next run and in G1, and this PR does not touch that adapter.

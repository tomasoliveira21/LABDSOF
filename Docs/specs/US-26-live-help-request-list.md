# US-26 · Live list of help requests

> **Issue:** [#73](https://github.com/tomasoliveira21/LABDSOF/issues/73) · **Type:** user story (F7, Incident backoffice) · **Service:** Reports · **Spec rules:** [README](README.md)
> **Sources:** issue #73; [`features.md`](../Product/features.md) US-26 and §4 (roles, teams, permissions); [API outline](../Technical/api-outline.md) §1.5 (authorisation), §1.6 (live lists), §1.7 (errors), §4 and §4.1; [product vision](../Product/product-vision.md) SC2 (p95 ≤ 5 s); [decisions guide](../Technical/architecture-decisions-guide.md) C5 (polling) and J3 (peak, conditional polling); [ADR-006](../Technical/adr/0006-identity-and-access.md); [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md) decisions 1, 6 and 13; spec [#194](194-reports-postgres-outbox.md) (D11, D12, D13).
> **Base: `chore/194-reports-postgres-outbox` (PR #214, open).** The list reads the requests and the local copies of memberships and teams that #194 adds, so this branch is stacked on it and is rebased onto `main` once #214 is merged.
> **Dependencies:** US-17 (#66) and US-23 (#71) are not built yet. What this story needs from them is already in #194: the `TeamChanged` copies with each team's permissions (US-17 sets them in Events) and `POST …/help-requests` (US-23 adds the map-version and event-status checks to it). The tests set the copies directly, as #194's do.

## 1. Source

**Story:** as a control-room member (`HelpRequests.Triage`), I want to see new help requests in a list as they arrive, so that I can react quickly. Persona P2.

**This PR:** `GET /api/v1/events/{eventId}/help-requests?updatedSince=` in Reports and its contract. **Not in it:** the backoffice page (web app; decision 13 of 15 November 2026: what it needs is written on #73); the filters `mine` (US-24), `status` and `teamId` (US-27, US-31) and the `HelpRequests.Respond` view (US-31), which stay *planned* in the contract.

| # | Acceptance criterion | Tests (name starts with `US-26 AC-<n>`) |
|---|---|---|
| AC-1 | Given a request is submitted for my event, then it appears in my list within 5 s without reloading the page (SC2; polling, guide C5) | `LiveListIT`, `HelpRequestServiceTest`, `HelpRequestTest` |
| AC-2 | Given I do not have `HelpRequests.Triage` in this event, when I open the list, then it is refused | `LiveListIT`, `HelpRequestServiceTest` |

## 2. Quality gates

The gates of [#194](194-reports-postgres-outbox.md) §2 for `backend/reports`, unchanged: `mvn verify` green, JaCoCo ≥ 90 % lines and branches, PIT `DEFAULTS` ≥ 80 % on `domain` and `application`, Testcontainers with the Compose images and the production migrations, the security matrix on the new endpoint, `ContractIT`, the `ci.yml` smoke on a fresh stack, `DocumentationIT` for this spec's test names. No threshold is lowered, no test disabled, no exclusion added.

## 3. Decisions

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **Who sees the list:** a member with `HelpRequests.Triage` (role `EventAdmin`, or a non-deleted team of the event with that permission, #194 D12) gets **every request of the event** in the control-room view of API outline §4.1. A member without it gets **`403`**; a user who is not an active member of the event, or a member of another event, gets **`404`** (same body as an unknown event) | API outline §1.7 settles it: `403` is *no permission*, `404` is *not a member of the event*. A member already knows the event exists, so `403` reveals nothing. When US-24 (`mine=true`) and US-31 (`HelpRequests.Respond`) are built, those members get their own filtered list instead of `403` |
| D2 | **First load** (no `updatedSince`): every request of the event that is not `Closed`. **Later polls** (`updatedSince`): every request changed after it, `Closed` ones included, so the page drops them. Both in order of arrival (`submittedAt`, then `id`) | API outline §1.6 (live lists take `updatedSince` and return only what changed) and US-30 (*a closed request leaves the active list*). *All requests on first load:* simpler, but a long event fills the page with closed ones. *Order by priority:* the priority is null until the AI or a member sets it, and a NotTriaged request is shown *in order of arrival* (decision 1); the page can sort what it has |
| D3 | **`serverTime` = the server's time minus 5 s**, so consecutive polls overlap by 5 s and a request may come twice; the page keeps one row per `id` | `updated_at` is set before the transaction commits. With `serverTime` = now, a request whose transaction commits just after a poll has read the table, but whose `updated_at` is earlier than that poll's `serverTime`, would never be listed. *A sequence or the transaction id as cursor:* exact, but PostgreSQL does not assign them in commit order without snapshot arithmetic. A write that waits more than 5 s between setting `updated_at` and committing is still missed (none does today: a submit is two inserts) |
| D4 | **A request that turns `NotTriaged` comes back in the changes**: a request whose triage is still `Pending` and whose 30 s deadline falls after `updatedSince` (and not after now) is listed again, now `NotTriaged` with reason `Timeout` | `NotTriaged` is derived on read, never written (decision 6, #194 D13), so its `updated_at` does not change. Without this rule the page would keep showing `Pending` until another change. *Write NotTriaged with a job:* decision 6 chose no sweeper |
| D5 | **Every later change of a request moves `help_request.updated_at`**, including triage changes (US-34, US-35) and status changes (US-28 to US-33) | The list reads only `help_request.updated_at` (indexed with `event_id` since #194). Recorded for the stories that change a request (§6) |
| D6 | **No pagination** on this list | API outline §1.6: live lists take `updatedSince`, other lists `limit` and `cursor`. A first load returns every open request of the event; at the 1 help request per second of the J3 peak that is a few hundred rows. Add `limit` and `cursor` to the first load if it grows beyond that |
| D7 | **Polling every 2 s** while the page is visible, recommended to the web app | SC2 is p95 ≤ 5 s from submit to the backoffice. Saving takes p95 ≤ 2 s (NFR-06), the poll waits at most its interval, and the read adds its own time; 2 s leaves margin. A control room is a handful of operators, so the load is small next to the participants' 10 s polling (J3). No `ETag`: the changes are already empty on most polls |
| D8 | **`updatedSince`** is an ISO-8601 instant (`2027-05-08T23:14:05Z`, or with an offset); a malformed value is `400`; one in the future lists nothing | The value the page sends is the `serverTime` it received |

## 4. Features (build order)

| # | Feature | Tests |
|---|---|---|
| F1 | Domain: `HelpRequest.changedAfter(since, now)` (D2, D4) | `HelpRequestTest` |
| F2 | Application: `HelpRequestService.list`, ports `open` and `changedSince`, `ForbiddenException` (D1 to D4) | `HelpRequestServiceTest` |
| F3 | JDBC adapter (same rules in SQL), controller with `403`, contract | `LiveListIT`, `ContractIT` |
| F4 | Documentation: changelog, this spec | `DocumentationIT` |

## 5. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| A request is submitted after the last poll | In the next poll | `LiveListIT` · `US-26 AC-1 a request submitted for the event is in the next poll` |
| SC2 with the recommended polling | A poll every 2 s sees a new request within 5 s | `LiveListIT` · `US-26 AC-1 polling every 2 s shows a new request within 5 s` |
| First load | Open requests only, oldest first | `LiveListIT` · `US-26 AC-1 the first load lists the open requests in order of arrival`; `HelpRequestServiceTest` · `US-26 AC-1 the first load lists the open requests and later polls list what changed` |
| A request is closed | Listed once more, `Closed`, in the next poll; not in a first load | `LiveListIT` · `US-26 AC-1 a closed request comes back in the changes and leaves the first load` |
| A request whose transaction commits just after a poll | Listed in the next poll (D3 overlap) | `LiveListIT` · `US-26 AC-1 a request committed late is not lost` |
| Triage still `Pending` at the 30 s deadline | Listed again as `NotTriaged` (`Timeout`) in the poll after the deadline, not before | `HelpRequestTest` · `US-26 AC-1 a Pending request changes when its triage deadline passes`; `LiveListIT` · `US-26 AC-1 a request that turns NotTriaged comes back in the changes` |
| Changed exactly at `updatedSince` | Not listed (strictly after); listed 1 µs later | `HelpRequestTest` · `US-26 AC-1 only what changed after updatedSince is listed` |
| Deadline exactly at `updatedSince` / exactly now | Not listed / listed | `HelpRequestTest` · `US-26 AC-1 a Pending request changes when its triage deadline passes` |
| Requests of another event | Never listed | `LiveListIT` · `US-26 AC-1 requests of another event are never listed` |
| `EventAdmin`, or a member of a team with `HelpRequests.Triage` | Gets the list in the control-room view | `HelpRequestServiceTest` · `US-26 AC-1 an EventAdmin and a triage team member get the list`; `LiveListIT` · `US-26 AC-1 a request submitted for the event is in the next poll` |
| Description with SQL or HTML | Listed as plain text | `LiveListIT` · `US-26 AC-1 injection text is listed as plain text` |
| `updatedSince` malformed / in the future | `400` / empty `items` | `LiveListIT` · `US-26 AC-1 a malformed updatedSince is rejected and a future one lists nothing` |
| Member without `HelpRequests.Triage` | `403`, nothing listed | `LiveListIT` · `US-26 AC-2 a member without HelpRequests.Triage is refused with 403`; `HelpRequestServiceTest` · `US-26 AC-2 a member without HelpRequests.Triage is refused` |
| Triage only through a team of another event, or a deleted team | `403` | `HelpRequestServiceTest` · `US-26 AC-2 a team of another event or a deleted team does not grant the list` |
| Not a member, `Removed`, member of another event only | `404`, same body as an unknown event | `LiveListIT` · `US-26 AC-2 a non-member, a removed member or a member of another event gets 404` |
| No token / expired / wrong signature / wrong issuer / wrong audience / not a JWT | `401` | `LiveListIT` · `US-26 AC-2 every bad token gets 401` |
| Malformed `eventId` | `400` | `LiveListIT` · `US-26 AC-1 a malformed updatedSince is rejected and a future one lists nothing` |
| The endpoint and the contract | `GET …/help-requests` served and *implemented* in `openapi.yaml` | `ContractIT` · `194 AC-6 every endpoint Reports serves is in openapi.yaml as implemented, and the reverse` |

## 6. Handoff and changelog

- **17 Nov 2026 · start.** Branch stacked on `chore/194-reports-postgres-outbox` (`f01137f`, PR #214 open).

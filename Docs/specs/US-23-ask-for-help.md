# US-23 (#71) · Ask for help on the map

> **Issue:** [#71](https://github.com/tomasoliveira21/LABDSOF/issues/71) · **Type:** user story (F6, help request) · **Service:** Reports (owner area: Reports, decision 13 of the [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md)) · **Spec rules:** [README](README.md)
> **Sources:** [`features.md`](../Product/features.md) US-23 and F6; the meeting of 15 November 2026, decision 1 (no participant category); [ADR-013](../Technical/adr/0013-api-gateway.md) (rate limits per account, L-2) and its note of 24 November 2026; the [record of 24 November 2026](../Meetings/2026-11-24-help-request-rate-limit.md) (L-2 moves to Reports); [ADR-006](../Technical/adr/0006-identity-and-access.md) (EventAdmin and team permissions); [API outline](../Technical/api-outline.md) §4 and §4.1; [`contracts/openapi.yaml`](../../backend/reports/contracts/openapi.yaml) (`submitHelpRequest`); [security and privacy assessment](../Technical/security-and-privacy-assessment.md) T11, T15, M-10; specs [194](194-reports-postgres-outbox.md) (submit, retry with `clientRequestId`, the participant and control-room views) and [US-34 default team](US-34-default-team.md).
> **Base:** `main` (`d517d76`).

## 1. Source

**Story:** as a participant, I want to ask for help by describing briefly what is happening and sharing where I am, so that the team can reach me. Persona P1.

**Already on `main`** (#194, #229): `POST …/help-requests` with a description of 1 to 500 characters and a map point, `201` with the participant's view, a safe retry with the same `clientRequestId`, no category from the participant. **This PR:** the limit of 5 help requests per account in 10 minutes (L-2), answered `429` with `Retry-After`, in Reports; the acceptance tests of the submission and of who sees the location. **Not in it:** the phone's location (AC-2, from Sprint C: survey decision 4, phased on 1 November 2026, #175); the routed team seeing the location (routing is US-29, #76); the page, its confirmation, its message and the first-aid points on the map (the web app's owner, decision 13: what it needs is written on #71).

| # | Acceptance criterion (#71) | Tests (name starts with `US-23 AC-<n>`) |
|---|---|---|
| AC-1 | Given I write a short description (1 to 500 characters; an empty one is refused) and mark where I am on the map, when I submit, then I see a confirmation. I do not choose a category | `AskForHelpIT` |
| AC-2 | From Sprint C: the phone's location | Not in this PR (Sprint C) |
| AC-3 | Given I submitted a request, then my location is visible only to members with `HelpRequests.Triage`, to EventAdmins and to the team it is routed to | `AskForHelpIT` (the routed team: US-29) |
| AC-4 | Given I have sent 5 help requests in the last 10 minutes, when I submit another, then it is refused with a message saying when I can ask again (429 with `Retry-After`) | `SubmitLimitTest`, `HelpRequestServiceTest`, `AskForHelpIT` |

So this PR is `Part of #71`: AC-2 is Sprint C and the routed team's part of AC-3 comes with US-29.

## 2. Quality gates

The gates of [#194](194-reports-postgres-outbox.md) §2 for `backend/reports`, unchanged: `mvn verify` green, JaCoCo ≥ 90 % lines and branches, PIT `DEFAULTS` ≥ 80 % on `domain` and `application`, Testcontainers with the Compose images, the production migrations and the broker set-up, `ContractIT`, the `ci.yml` smoke on a fresh stack, `DocumentationIT` for this spec's tests, the diff guard, an independent review and green CI with SonarQube. No new endpoint: the new `429` of `POST …/help-requests` gets its own rows in §5.

## 3. Decisions

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **L-2 is enforced by Reports, not by the gateway.** Decided on 24 November 2026, with the gateway's owner's agreement ([record](../Meetings/2026-11-24-help-request-rate-limit.md)); ADR-013 gets a dated note | The gateway (ADR-013 as written) counts every `POST`, so a participant who retries after a network error, or whose request was refused as invalid, uses up their quota, and its counters are lost on a restart. Reports counts the help requests it saved, which is what the criterion says (*I have sent 5*), keeps them in the database, and computes `Retry-After` exactly. L-3 (reports) is already a Reports limit of the same kind |
| D2 | **Key: the account (the token's `sub`), across events** | ADR-013's key for L-2. Per account and event (as L-3) was weighed: hardly anyone is in two events at once, and the ADR's key needs no change |
| D3 | **Only saved requests count.** A retry with a `clientRequestId` already saved returns that request (`200`) even at the limit and counts nothing; a request refused as invalid (`400`), from a non-member (`404`) or by the limit (`429`) is not saved and counts nothing | Spec 194: a retry must be safe. Order of the checks: membership (`404`), the description and point (`400`), the retry, then the limit |
| D4 | **The window:** a request counts while `submitted_at` is less than 10 minutes old (strictly after *now − 10 min*); the 6th is refused. `Retry-After` is the whole number of seconds, rounded up and at least 1, until the oldest of the last 5 leaves the window | ADR-013: `429` with `Retry-After` in `application/problem+json`; the page says when the participant can ask again from it |
| D5 | **Two submissions at once cannot both pass at 4:** the submit transaction takes a lock per account (`pg_advisory_xact_lock`) before counting | Without it, two phones of the same account (or a double tap with two `clientRequestId`s) could both see 4 and save 6. A lock per account, not a table lock, so other participants never wait |
| D6 | **The values are settings:** `farol.help-requests.limit` (5) and `farol.help-requests.limit-window` (10 min) | ADR-013: *the values are settings, tuned with the k6 results in Sprint C*. The AI end-to-end profile of #234 (4 accounts, one request every 30 s) sends exactly 5 per account in 10 minutes, at the edge of the limit; a rerun can raise it with `FAROL_HELPREQUESTS_LIMIT` |
| D7 | **The location is in the control-room view only:** members with `HelpRequests.Triage` and EventAdmins (who have every permission, ADR-006) see it; the owner sees their own request without it; any other member gets `404` | Already so on `main` (spec 194 D5); this PR adds the US-23 tests. The routed team (US-29) is not built |

## 4. Features (build order)

1. Domain `SubmitLimit` (D4, D6): from the account's recent submission times, whether a new one is refused and the `Retry-After`. Unit tests first.
2. Ports `HelpRequests.lockRequester` and `HelpRequests.submittedSince` and their JDBC adapter (D2, D5).
3. `HelpRequestService.submit` with the limit (D3) and `TooManyHelpRequestsException`; the controller answers `429` with `Retry-After` (D4).
4. Settings and wiring (D6); contract (`429` on `submitHelpRequest`), API outline, ADR-013 note, the record of 24 November 2026, the assessment's T15, CHANGELOG.

## 5. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| A description and a point (AC-1) | `201`, the participant's view (id, description, `Submitted`, time) and a `Location`; no category in the request or the answer | `AskForHelpIT` · `US-23 AC-1 a description and a point give a confirmation, and no category is asked for` |
| Empty, blank or 501-character description; missing or out-of-range point (AC-1) | `400`, nothing saved | `AskForHelpIT` · `US-23 AC-1 an empty or too long description, or no point, is refused and nothing is saved` |
| 500 characters, counted as code points (AC-1) | `201` | `AskForHelpIT` · `US-23 AC-1 a description and a point give a confirmation, and no category is asked for` |
| The location: Triage member, EventAdmin, owner, member without Triage (AC-3, D7) | Triage and EventAdmin see `location`; the owner gets the participant view without it; another member gets `404` | `AskForHelpIT` · `US-23 AC-3 only the control room and the EventAdmin see the location` |
| The 6th request in 10 minutes (AC-4) | `429` `application/problem+json`, `Retry-After` in seconds, nothing saved, no outbox row | `AskForHelpIT` · `US-23 AC-4 the 6th help request in 10 minutes is refused with Retry-After and nothing is saved` |
| 4 saved, then the 5th (AC-4, bound) | `201` | `SubmitLimitTest` · `US-23 AC-4 the 5th request passes and the 6th is refused`; `AskForHelpIT` · `US-23 AC-4 the 6th help request in 10 minutes is refused with Retry-After and nothing is saved` |
| A request exactly 10 minutes old (D4, bound) | It no longer counts | `SubmitLimitTest` · `US-23 AC-4 a request leaves the window after exactly 10 minutes` |
| `Retry-After` (D4) | Whole seconds until the oldest of the last 5 leaves the window, rounded up, at least 1 | `SubmitLimitTest` · `US-23 AC-4 Retry-After is the time until the oldest counted request leaves the window` |
| Requests in other events (D2) | They count | `AskForHelpIT` · `US-23 AC-4 requests in another event count, and another account is not limited` |
| Another account (D2) | Not limited | `AskForHelpIT` · `US-23 AC-4 requests in another event count, and another account is not limited` |
| A retry of a saved request at the limit (D3) | `200` with the saved request, nothing counted | `HelpRequestServiceTest` · `US-23 AC-4 a retry of a saved request passes at the limit and counts nothing`; `AskForHelpIT` · `US-23 AC-4 a retry of a saved request is answered at the limit` |
| Refused requests (`400`, `404`, `429`) (D3) | Not counted | `HelpRequestServiceTest` · `US-23 AC-4 refused requests are not counted` |
| A non-member at the limit (D3) | `404`, as before: membership first | `HelpRequestServiceTest` · `US-23 AC-4 refused requests are not counted` |
| Two submissions at once with 4 saved (D5) | One `201`, one `429` | `AskForHelpIT` · `US-23 AC-4 two submissions at once with 4 saved give one 201 and one 429` |
| The limit and window as settings (D6) | Read from `farol.help-requests.*`; a limit below 1 or a window not positive is refused at start | `SubmitLimitTest` · `US-23 AC-4 a limit below 1 or a window that is not positive is refused` |
| The lock order (D5) | The account lock is taken before the count, inside the submit transaction | `HelpRequestServiceTest` · `US-23 AC-4 the account is locked before its requests are counted` |

## 6. Handoff

(Written as the work goes.)

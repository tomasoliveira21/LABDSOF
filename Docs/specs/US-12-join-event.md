# US-12 · Join an event by QR code or link (#63)

> Spec written before the implementation, as [`README.md`](README.md) asks, and committed in the PR that closes the
> issue. Module: `backend/auth-users`. Branch: `feature/63-join-event`, stacked on `feature/67-team-members`
> (US-18, #67): the same controller, the same `MembershipChanged` and the same audit port.

## 1. Source

- **Story** ([`features.md` §7](../Product/features.md), F3): *As a participant, I want to join an event by
  scanning its QR code or opening its link, so that I can use it.* AC-1 logged in, scanning the QR code of a
  **published** event makes me a RegularUser of it and it opens; AC-2 not logged in, I log in first and am then
  taken to that event; AC-3 a draft, closed or suspended event cannot be joined.
- **Issue** [#63](https://github.com/tomasoliveira21/LABDSOF/issues/63), persona P1 (Rita, participant), with the
  [phone mockup](https://github.com/tomasoliveira21/LABDSOF/blob/ca092ff5960e84cdacefd083bbd6f66832e86c88/Docs/Product/mockups/US-12-join-event.svg)
  of PR #240.
- **Contract**: `POST /api/v1/events/{eventId}/members/me` (`joinEvent`), already declared with **no body**,
  `201` for *I am now a member*, `200` for *I was already a member; nothing changed*, `401` and `404`.
- **API outline** [§2](../Technical/api-outline.md): *Join the event as RegularUser (QR code or link); `404` if
  the event is not published in the local copy* — the status comes from this service's copy of `EventChanged`
  (#241), never from a call to Events (ADR-006, §7.3).
- **AC-2 is the web app's**, not the backend's: Keycloak does the login (ADR-006) and the app returns to the
  event's URL afterwards. The backend's part is that the same `POST` works on the first call after a login.

## 2. Quality gates

The gates of [`README.md`](README.md), unchanged, on `backend/auth-users`: `mvn clean verify` green, JaCoCo ≥ 90 %
of lines **and** branches, PIT `DEFAULTS` ≥ 80 % on `domain` and `application`, Testcontainers with the production
migrations, the security matrix on the new operation, `ContractIT` and `EventContractIT`. The baseline is where
#67 left the module: 183 unit and 280 integration tests, JaCoCo 790/792 lines and 190/192 branches, PIT 99/99.
Where this branch ends: 193 unit and 290 integration tests, JaCoCo 850/852 lines and 206/208 branches, PIT
115/115.

## 3. Decisions

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **The join is by `eventId` and carries no join code**, exactly as the contract and the outline declare it | This is the open point left on #63 by US-11, and the recorded team decision answers it: the operation takes no body. Making the code a credential would need it in Auth & Users, and `event.changed.schema.json` deliberately does not carry it, so option 2 of that note is a **contract change for the team**, not a choice to make inside this story. It is written down as a known limit here, in the `CHANGELOG` and on the issue: today the link's secrecy protects nothing, and anyone logged in who knows an event's id can join it while it is published. US-11 D1 keeps the code unguessable (160 bits) so the team can still close that gap without touching Events |
| D2 | **Only a `Published` event can be joined** (AC-3): a `Draft`, `Suspended` or `Closed` event, an event with no copy yet, and an unknown event all answer the **same `404`** | The outline asks for the status to come from the copy. A copy that has not arrived yet is indistinguishable from an unknown event, and saying *not published yet* would leak that the event exists — the hiding rule of §1.7. `Closed` is included because the story says so, even though a closed event is still readable by its **existing** members (US-11 D-reads) |
| D3 | **Idempotent: `201` the first time, `200` afterwards**, with the membership either way, and **nothing is published or logged on the second call** | The contract declares both answers. A second scan of the same QR code is the normal case, and a `MembershipChanged` whose version did not move would be dropped by every consumer as a replay anyway (US-18 D6) |
| D4 | **A member who already belongs to the event keeps their role**, EventAdmin included: the join never downgrades anyone | The EventAdmin named by US-06 may well open the link of their own event. Overwriting the role would take the event away from its organiser, and the AC only promise RegularUser for someone who is *not* a member yet |
| D5 | **The insert is `ON CONFLICT DO NOTHING` and the row is then read back**, instead of a lock | Two scans at once are the only race here, and the membership's key already serialises them: the loser reads the winner's row and answers `200`. No lock is needed, and — as the review of [#249](https://github.com/tomasoliveira21/LABDSOF/pull/249) showed — a unique violation inside a transaction could not be retried |
| D6 | **The account must already exist**, and a disabled, pending-deletion or deleted account is refused as everywhere else (`403`, `401`, `401`) | `GET /me` is what creates an account (#191 D5), and the web app calls it at login (US-01, #241) before any event page, so the join always runs with an account. Provisioning one here would duplicate `MeService` and let a path that never asked for an e-mail create accounts; the token without an account gets the `401` of *the token does not identify an available account* |
| D7 | **The join publishes `MembershipChanged`** (US-18 D6's full state: `RegularUser`, `Active`, no team, version 1) and records one `INFO` line and `event_membership.created_by = created_at`'s actor — the member themselves | It is the only way Events, Reports and Transports learn the member exists, and US-14 refuses everyone who is not in their copy. The actor of a join is the joiner, which is what `actorUserId` carries in the envelope |
| D8 | **No migration.** `event_membership` (V1) and `event_copy` (V2) already hold everything | The row this story inserts is the one US-18 already reads and updates |

## 4. Features (build order)

| # | Feature | Tests |
|---|---|---|
| F1 | Application: `JoinEventService.join` (D2 to D7), `EventCopies.statusOf` and `EventMemberships.insertIfAbsent` | `JoinEventServiceTest`, `InMemoryPorts` |
| F2 | Adapters: the two queries, `MembersController.joinEvent` with its `201`/`200`, the audit line | `JoinEventIT`, `JdbcEventMembershipsIT`, `ContractIT`, `SecurityIT`, `LoggingIT` |
| F3 | `MembershipChanged` of a join against its schema, through the real broker | `EventContractIT` |
| F4 | Documentation: this spec, `CHANGELOG.md`, `local-delivery.md` §0, the new checks in `auth_users_smoke.py`, the front-end note on #63 | — |

## 5. Edge-case matrix

Every test name starts with `US-12 AC-1` to `AC-3`.

| Case | Expected | Test |
|---|---|---|
| A logged-in user joins a published event | `201` with the membership (`RegularUser`, no team, the event's name and status), the row stored with who and when, one `MembershipChanged`, one log line | `JoinEventIT` · `a logged-in user joins a published event` |
| The same `POST` again | `200` with the same membership, nothing stored, nothing published, nothing logged | `JoinEventIT` · `joining twice changes nothing and answers 200` |
| A `Draft`, `Suspended` or `Closed` event | `404`, nothing stored (AC-3) | `JoinEventServiceTest` · `only a published event can be joined`; `JoinEventIT` · same name |
| An event with no copy yet, and an unknown event id | `404`, the same problem as a draft (D2) | `JoinEventIT` · `an event with no copy and an unknown event are the same 404` |
| An EventAdmin of the event opens the link | `200`, still an EventAdmin, nothing published (D4) | `JoinEventServiceTest` · `an existing member keeps their role` |
| Two joins of one event at once | One row, one `MembershipChanged`, both calls answered (D5) | `JdbcEventMembershipsIT` · `two joins of one event store one membership` |
| A malformed event id | `400` | `SecurityIT` · `191 D9 a malformed UUID in a path gets 400 before the use case` |
| No account for the token / disabled / pending deletion / deleted | `401` / `403` / `401` / `401` (D6) | `JoinEventIT` · `an account that cannot act is refused`; `JoinEventServiceTest` · same name |
| No token / expired / wrong signature / wrong issuer / audience / `alg none` / non-canonical `sub` | `401` | `SecurityIT` · `191 AC-2 every secured operation of the contract refuses a missing or invalid token with 401 Problem Details` |
| The log line of D7 | One `INFO` line with the correlation id and the pseudonymous ids — no e-mail, no display name | `LoggingIT` · `the join is recorded with who joined and when` |
| `MembershipChanged` of a join against its JSON Schema, through the real broker | Valid, version 1, no team | `EventContractIT` · `joining an event publishes a MembershipChanged that matches its JSON Schema` |
| The contract | The operation is served; the rest still answer `501` | `ContractIT` · `191 AC-4 the service serves exactly the operations of openapi.yaml` |

## 6. What the web app still needs (decision 13)

To be left on #63: the join link of US-11 opens the event's page; if the user is not logged in, Keycloak's login
comes first and the app returns to the same URL (AC-2). The page then calls `POST
/api/v1/events/{eventId}/members/me` once: `201` means *welcome*, `200` *you were already in*, and both carry the
membership, so the app can go straight to the event. `404` means the event cannot be joined — it is not published,
or it does not exist — and the two are deliberately indistinguishable. `GET /api/v1/me` must have been called at
least once for the account to exist (US-01 does it at login).

## 7. Handoff and changelog

- **26 Nov 2026 · research.** Focused pass: the contract's `joinEvent` with its two success answers, the outline's
  §2 row, `event_copy` and the `EventChanged` consumer of #241, US-18's `MembershipChanged` and `MemberAudit`
  (this story reuses both), and the note US-11 left on #63 about the join code, which D1 answers.
- **26 Nov 2026 · implementation**, in the order of §4, with `mvn clean verify` (JDK 21) green at the end: **193
  unit and 290 integration tests**, JaCoCo **850/852 lines (99.8 %)** and **206/208 branches (99.0 %)**, PIT
  **115/115 mutations killed (100 %)**. No threshold lowered, no test disabled, no new dependency, no migration.
- **What the gates needed.** Two outcomes of D5 cannot be produced by a real sequence of calls — the loser of two
  scans, and a conflict with no row behind it — so `InMemoryPorts` gets a knob for each, as US-19's
  forced-outcome knobs do: one stores the winner's membership and refuses the insert, the other refuses it and
  stores nothing, which is the only way to reach the `orElseThrow` that says so. The refusal of a non-joinable
  event is recorded as its own reason (`NOT_JOINABLE`), not reused from US-18's `NOT_A_MEMBER`.
- **Open point, on #63 and not decided here:** the join code (D1). The contract's join takes no body, so the
  event id alone is enough; making the code a credential is a contract change for the team, and the PR asks for
  that decision explicitly rather than patching it.

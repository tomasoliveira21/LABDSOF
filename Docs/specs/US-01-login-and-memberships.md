# US-01 · Log in and see my events

> **Issue:** [#59](https://github.com/tomasoliveira21/LABDSOF/issues/59) · **Type:** user story · **Services:** Auth & Users and web app · **Spec rules:** [README](README.md)
> **Sources:** [API outline §2](../Technical/api-outline.md#2-auth--users), [ADR-004](../Technical/adr/0004-service-communication.md), [ADR-006](../Technical/adr/0006-identity-and-access.md), [Auth & Users #191](191-auth-users-service.md), [US-01 mockup](../Product/mockups/US-01-login-events.svg).
> **Dependencies:** Keycloak web login (#195), Auth & Users (#191), Events snapshot contract (#192). Event creation and joining provide real memberships in their respective stories. Tests supply their own fixtures.

## 1. Acceptance criteria

| # | Acceptance criterion | Verification |
|---|---|---|
| AC-1 | Given an authenticated user with event memberships, the app lists only that account's memberships, with its role and team assignments. | `MembershipsIT` exercises the HTTP endpoint and PostgreSQL. `infra/tests/auth_users_smoke.py` exercises a real Keycloak session through the gateway on a fresh stack. |
| AC-2 | Given invalid credentials, the login flow shows Keycloak's generic credentials error. | Browser check against the Compose Keycloak using the deliberately invalid credentials and built-in error message. |

## 2. Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | `GET /me/memberships` scopes the query to the token subject and reads Auth & Users memberships and team assignments. | The caller cannot choose whose memberships to read. `GET /me` handles provisioning. A subject with no local account or memberships gets an empty list. |
| D2 | A disabled account gets `403`. An account pending deletion, a tombstoned account or an unauthenticated caller gets `401`. | Matches the account availability rules used by `GET /me`. |
| D3 | A membership is returned even when its Events projection is missing. The OpenAPI response omits `eventName` and `eventStatus` in that case, and the web app shows that details are unavailable. | A lagging projection must not look like a user has no memberships. |
| D4 | `event_copy` and `event_team_copy` keep the latest snapshots from the shared Events queue. Upserts apply only higher entity versions, which handles redelivery and out-of-order delivery without a separate message ID ledger. | The consumer only applies snapshots. The team payload retains `deleted: true` as a tombstone. The team-management consumer must filter those entries when it starts reading this projection (#65). Teams and events can arrive independently. |
| D5 | Validate the envelope and the fields consumed for known types. Allow extra fields and acknowledge valid envelopes for unsupported types with a structured log. | Matches the Events and Reports consumer pattern and lets producers add data without breaking this consumer. |
| D6 | Dead-letter invalid messages with `x-farol-error` and preserve the correlation ID. Transient failures use four attempts with backoff of 1 s, 2 s and 4 s. The recoverer waits for the broker's confirm and keeps the default requeue behavior if handoff fails. | A failed handoff must not acknowledge the original message. Tests exercise rejection and confirm failures separately. |

## 3. Verification

- `MembershipsIT` covers caller isolation, pending details, versioned updates, access errors and a real RabbitMQ dead letter.
- `EventCopyListenerTest` covers invalid envelopes, correlation cleanup and acknowledgement of unknown valid types.
- `DeadLettersTest` covers the error header, persistent delivery, nack, returned publish, interrupted wait, exceptional confirm and timeout.
- Use the existing Auth & Users quality gates from [#191](191-auth-users-service.md), including JaCoCo and PIT, and the Compose smoke test.

# #191 · Auth & Users service

> **Issue:** [#191](https://github.com/tomasoliveira21/LABDSOF/issues/191) · **Type:** chore · **Owner:** Tiago Teixeira · **Spec rules:** [README](README.md)
> **Sources:** [ADR-001](../Technical/adr/0001-architectural-style.md), [ADR-003](../Technical/adr/0003-database-and-data-ownership.md), [ADR-004](../Technical/adr/0004-service-communication.md), [ADR-006](../Technical/adr/0006-identity-and-access.md), [ADR-012](../Technical/adr/0012-observability.md), [API outline](../Technical/api-outline.md) §1, §2 and §7, [`architecture.md`](../Technical/architecture.md) §6 and §8, [domain model](../Technical/domain-model.md), [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md) decision 7 and §4 (proposed states, #198 still open), [security assessment](../Technical/security-and-privacy-assessment.md) T4, AC-6, M-10, handoffs of [#189](189-postgres-keycloak-compose.md) and [#212](212-rabbitmq-topology-users.md), and the patterns of the Reports pull request #214 (Bruno Lopes, open), kept the same where nothing below says otherwise.

## 1. Acceptance criteria

| # | Given / when / then | Covered by |
|---|---|---|
| AC-1 | Given the Compose stack, when it starts, then the Auth & Users container is healthy, uses only its own database, and its schema comes from Flyway | `191 AC-1 …` in `MigrationIT`; CI *Build and start the stack* and the database isolation step |
| AC-2 | Given a request with a valid token, when it reaches the service, then the token is validated against Keycloak; a missing, expired, tampered, wrong-issuer or wrong-audience token gets `401` | `191 AC-2 …` in `SecurityIT`; CI step *Check that Auth & Users validates Keycloak tokens* (`infra/tests/auth_users_smoke.py`, a real token through the gateway) |
| AC-3 | Given the CI pipeline, when it runs, then the service is built, tested (gates of the README) and started in the Compose smoke test | CI *Build and test every module*, smoke steps |
| AC-4 | Given `contracts/openapi.yaml`, then it holds the Sprint B endpoints of API outline §2, and the service serves exactly those paths | `191 AC-4 …` in `ContractIT` |
| AC-5 | Given a first `GET /api/v1/me`, then the account is created from the token, `UserProfileChanged` is written to the outbox in the same transaction and published to `farol.auth-users`; later calls return the same account | `191 AC-5 …` in `MeServiceTest`, `MeIT`, `OutboxIT`; CI smoke `infra/tests/auth_users_smoke.py` (the outbox row of the first `GET /me` gets `published_at`; counting the queue would race with Events, #192) |
| AC-6 | Given the bootstrap e-mail (`FAROL_BOOTSTRAP_FAROL_ADMIN_EMAIL`), when an account with that e-mail **and** `email_verified: true` is created, then it gets `FarolAdmin` and `GlobalRoleChanged` is published; an unverified one does not (#179, T4) | `191 AC-6 …` in `MeServiceTest`, `MeIT` |
| AC-7 | Given a deleted (tombstone) or deletion-pending account, `GET /me` gets `401` and creates nothing; a disabled account gets `403` | `191 AC-7 …` in `MeServiceTest`, `MeIT` |
| AC-8 | Every request and published message carries a correlation id in the structured logs; no e-mail, name or token is logged (M-10) | `191 AC-8 …` in `CorrelationIdFilterTest`, `LoggingIT`, `OutboxIT` |

## 2. Decisions

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **Same patterns as Reports (#214)**: Spring JDBC (`JdbcClient`) with hand-written SQL, not JPA; outbox table, publisher (claim with `FOR UPDATE SKIP LOCKED`, publisher confirms, `mandatory`, backoff `2^attempts` s up to 60 s, pause when the broker is down); no `Queue`/`Exchange` beans; JWT with `issuer-uri` + `jwk-set-uri` + `audiences`; `*IT` tests in failsafe; the image build runs `mvn package` | One way of doing these across the services the team reviews. The reasons are in #214's spec (D1, D4, D5, D9, D10, D17); repeating them here would let them drift |
| D2 | **Contract-first with `openapi-generator`** (ADR-004 decision 4, accepted): interfaces and records generated at build time, never committed. `skipDefaultInterface=true`, so the controllers implement every operation; operations of later stories answer `501` Problem Details with the story that brings them. **Generated code is not excluded from coverage**: `ContractIT` round-trips every generated model and enum through the JSON mapper (unknown enum values and unknown fields refused) | *Hand-written contract, no generator* (#214 D14): keeps coverage simple but leaves the accepted ADR-004 decision 4 for later and lets the code drift from the contract. *Generator with a JaCoCo exclusion:* breaks the agreed rule "no exclusions". Tested on 17 November 2026: with the default interfaces the generator writes example-response code nobody calls; with `skipDefaultInterface` only records and enums with `fromValue` remain, which a contract test covers. Answers the open point of #214 D14 for this service |
| D3 | **Schema `V1__auth_users.sql`**: `user_account` (`user_id uuid` PK = token `sub`, `email`, `display_name`, `status` check `Active/Disabled/DeletionPending`, `version`, timestamps), `user_tombstone` (`user_id`, `deleted_at`), `global_role_grant` (`user_id`, `role` check `FarolAdmin`, `version`, `granted_at`, `granted_by` nullable = system), `event_membership` (`event_id`, `user_id`, `role` check `EventAdmin/RegularUser`, `version`, who and when), `team_assignment` (`event_id`, `user_id`, `team_id`, PK of the three, FK to the membership), `outbox` (as #214 D4, plus `correlation_id`). No `MembershipStatus` | The four entities the issue names, the tombstone of API outline §2, and the outbox. States: proposal of 15 November §4 (drop `MembershipStatus`; `UserAccount` `Active`, `Disabled`, `DeletionPending`), flagged in the PR while #198 is open. Local copies of events and teams, and `processed_message`, come with their first consumer (US-06, US-12, US-18): expand and contract |
| D4 | **`userId` is the token's `sub` as a UUID**. A token whose `sub` is not a UUID gets `401` | Keycloak's `sub` is a UUID; the contract and the events say `format: uuid`. Reports keeps `sub` as text (#214 D2), which accepts these values |
| D5 | **Just-in-time account** (ADR-006 decision 6): `INSERT … ON CONFLICT DO NOTHING`; only the call that inserted writes `UserProfileChanged` (and `GlobalRoleChanged` for the bootstrap). Display name from the `name` claim, trimmed and cut to 100 characters, or the part of the e-mail before `@` when `name` is missing or blank. The e-mail is refreshed from the token on later calls (Keycloak owns it); the display name is not (US-04 changes it in Farol). A token without `email` gets `401` | Two first calls at once create one account and one event (concurrency test). `UserProfileChanged` carries only `displayName` (§7.4), so an e-mail change publishes nothing |
| D6 | **Bootstrap FarolAdmin** only when the account is created, the e-mail equals `FAROL_BOOTSTRAP_FAROL_ADMIN_EMAIL` ignoring case and surrounding spaces, and `email_verified` is `true`; an empty setting grants nobody | Decision 7 of 15 November 2026 (#179), ADR-006 "at provisioning", T4 |
| D7 | **Account states on `GET /me`**: tombstone or `DeletionPending` → `401`, `Disabled` → `403`, as the contract says; nothing is created or published | API outline §1.4 and §2; AC-6 of the assessment |
| D8 | **Correlation id**: header `X-Correlation-Id`, kept when it matches `[A-Za-z0-9-]{8,64}`, otherwise a new UUID; put in the MDC, returned in the response, stored in the outbox row and sent as the AMQP `correlationId` | ADR-012 point 1; the pattern stops log injection through the header |
| D9 | **Errors**: Problem Details everywhere, including the security layer (`401` with `WWW-Authenticate: Bearer`, `403`); unknown JSON fields refused (`400`, mass assignment) | API outline §1.7 |
| D10 | **Event contracts** (SDD rule R3) in `contracts/events/`: the envelope (§7.1), `user-profile.changed` and `global-role.changed`, checked against the message the outbox sends; the other three events of §2 get theirs with the stories that publish them | Same library as #214 (`com.networknt:json-schema-validator`, test only) |
| D11 | **Dependency review**: `actions/dependency-review-action` in its own workflow on pull requests, failing on `high` | Gate of the README; a separate file keeps it out of the busy `ci.yml` |

## 3. Features (build order)

1. Module: `pom.xml` with the gates, generator, failsafe; Dockerfile runs `package`; `application.yaml`; Compose wiring (D1)
2. Schema and persistence adapters (D3) · `MigrationIT`
3. Domain and `GET /me` use case (D4–D7) · `UserAccountTest`, `MeServiceTest`
4. Outbox and publisher (D1, D8) · `OutboxIT`, `OutboxPublisherTest`
5. Web: security, Problem Details, correlation id, controllers (D2, D8, D9) · `SecurityIT`, `MeIT`, `ContractIT`, `CorrelationIdFilterTest`, `LoggingIT`
6. Event schemas (D10) · `EventContractIT`; CI: smoke script and dependency review (D11)
7. Docs: `local-delivery.md`, CHANGELOG

## 4. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| No token, expired token (beyond the 60 s skew), tampered signature, wrong issuer, wrong audience, `alg: none`, malformed header | `401` Problem Details with `WWW-Authenticate: Bearer` | `SecurityIT` · `191 AC-2 …` (one test per case) |
| Token signed by an unknown key (another JWKS) | `401` | `SecurityIT` |
| `sub` not a UUID | `401` | `MeIT` · `191 AC-2 a token whose sub is not a uuid is refused` |
| Token without `email` | `401`, nothing created | `MeServiceTest`, `MeIT` |
| Two first calls at once for the same `sub` | One account, one `UserProfileChanged` | `MeIT` · `191 AC-5 concurrent first calls create one account and one event` |
| Second call | Same account, no new event; e-mail refreshed, name kept | `MeServiceTest`, `MeIT` |
| `name` missing, blank, longer than 100 | E-mail local part; trimmed and cut to 100 | `UserAccountTest` |
| Bootstrap e-mail with other case or spaces, verified | `FarolAdmin` | `MeServiceTest` |
| Bootstrap e-mail not verified, or `email_verified` missing | No role (#179) | `MeServiceTest`, `MeIT` |
| Bootstrap setting empty | Nobody is granted | `MeServiceTest` |
| Existing account later matching the bootstrap e-mail | No role: only at provisioning | `MeServiceTest` |
| Tombstone, `DeletionPending` | `401`, nothing created | `MeServiceTest`, `MeIT` |
| `Disabled` | `403` | `MeServiceTest`, `MeIT` |
| Operation of a later story with a valid token | `501` naming the story; `401` without a token | `ContractIT` / `SecurityIT` |
| Unknown JSON field, malformed UUID in a path, invalid e-mail in `GET /accounts` | `400` before `501` | `SecurityIT` |
| Correlation id missing, malformed or too long, injection attempt with a newline | New UUID; the bad value is not logged | `CorrelationIdFilterTest` |
| Broker down when the outbox polls | Row stays, attempts and backoff recorded; sent once the broker returns | `OutboxIT` · `191 AC-5 a message waits while the broker is down and is sent once it returns` |
| Unroutable message, or nack | Row backs off alone, the batch goes on | `OutboxPublisherTest` |
| Crash between save and publish | Sent once after restart | `OutboxIT` |
| Two publishers at once | No row sent twice (`SKIP LOCKED`) | `OutboxIT` |
| Service user lacks a permission | Publish refused, row kept (no declare) | `OutboxIT` (broker set up with #212's `setup.sh`, connected as `auth-users`) |
| Service starts before its broker user exists | Starts; publishes once the user exists | `OutboxIT` |
| E-mail, name or token in the logs | Never | `LoggingIT` |

## 5. Handoff and changelog

- **17 Nov 2026:** research (10 min): issue, API outline §1, §2 and §7, architecture §6, ADR-003/004/006/012, domain model and the proposed states, #189 and #212 handoffs, Reports #214. Checked on this machine: PIT 1.30.2 with `pitest-junit5-plugin` 1.2.3 runs JUnit 6.0.3 (Boot 4.0.9) and fails the build below the threshold; Testcontainers 2.0.5 (`org.testcontainers.postgresql`, `org.testcontainers.rabbitmq`) with `@ServiceConnection` works with Docker 29.7; Boot 4.0.9 adds the issuer and audience validators when `jwk-set-uri` is set (read in its bytecode); `openapi-generator` 7.27 needs `ApiUtil` for default interfaces, hence D2.
- **For the web app (Tomás):** `GET /api/v1/me` once after login creates the account; `globalRoles` contains `FarolAdmin` for Daniel.
- **For Events (#192), Reports and Transports:** `UserProfileChanged` and `GlobalRoleChanged` follow `contracts/events/` here.
- **17 Nov 2026 · implementation.** Built in four steps, each with `mvn clean verify` green:
  - (1) module, Flyway `V1`, domain, `GET /me` use case, JDBC adapters, `ContractIT` for the generated models;
  - (2) outbox publisher;
  - (3) security, Problem Details, correlation id, controllers, logging;
  - (4) event schemas (`EventContractIT`), Compose wiring, `infra/tests/auth_users_smoke.py` and its CI step, `dependency-review.yml`, documents.

  Final `mvn clean verify` (JDK 21): 100 unit and 241 integration tests, JaCoCo 99.5 % lines (442/444; the missed lines are `AuthUsersApplication.main`) and 100 % branches, generated code included, PIT 41/41 killed. Local run of every check step of `ci.yml` on a fresh `farol-ci` stack: all green, then `down --volumes`.

  Deviations from this spec:
  - the `last_error` of a publish the `auth-users` user may not write is `not confirmed: Channel closed by application`, not `ACCESS_REFUSED`;
  - the `?role=` query enum is converted by an `@InitBinder` editor (a failed converter falls back to `Enum.valueOf`);
  - `WWW-Authenticate` starts with `Bearer` but carries Spring Security 7's `resource_metadata`.
- **Spec change, 17 Nov 2026:** AC-5's CI evidence is the outbox row's `published_at` (set after the broker's confirm), not a message counted in `events.auth-users-events`: once Events (#192) consumes that queue, counting would race.
- **Open points for the team:**
  - (1) #198 is still open: the proposed states are used and the PR says so.
  - (2) D2 answers #214 D14: with `skipDefaultInterface=true` the generator needs no `ApiUtil` and leaves only records and enums, which a contract test covers without any JaCoCo exclusion; the team decides whether Reports adopts it.
  - (3) Contract note for US-06: `GET /api/v1/accounts?email=` with an empty value reaches the `501`, because `@Email` accepts an empty string; add `minLength: 1` or refuse it in the use case.
  - (4) `/actuator/health` needs a token, so Compose keeps `/api/v1/status` (ADR-012 target unchanged).
  - (5) The `resource_metadata` URL of `WWW-Authenticate` is not served.
  - (6) The published body is the `jsonb` text, so its key order is PostgreSQL's; consumers must not rely on it.
  - (7) One access log line per request, health checks included (noise for #148).
  - (8) `dependency-review.yml` needs the repository's dependency graph switched on by the owner.

  Event schemas of `MembershipChanged`, `UserStatusChanged` and `UserDeleted` come with the stories that publish them (D10).

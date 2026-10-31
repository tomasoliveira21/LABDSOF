# ADR-006 · Identity and access control

## Status

**Accepted**: decided by the team in the meeting of 22 October 2026 ([record](../../Meetings/2026-10-22-identity-and-gateway-decisions.md)). Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) E1–E4.

## Context

- Login is mandatory for every user, participants included (F1; DM-1). Farol is one React web app, used on phones and laptops (guide F1).
- Auth & Users owns authentication, authorisation, accounts, memberships of events, team assignments and the `FarolAdmin` grant (guide A2). Teams and their permissions live in Events (role model of 22 October 2026, [`features.md` §4](../../Product/features.md#4-roles-teams-and-permissions-22-october-2026)).
- Services talk only through events and keep local copies of what they need ([ADR-004](0004-service-communication.md), guide C4).
- Delivery is local only; Azure is the studied cloud alternative ([ADR-011](0011-deployment-and-infrastructure.md), guide G1).
- Mocked authentication is allowed for the MVP, but security controls must not be switched off for the demo (assignment §6.6, §11).
- The top risk is a member of one event reading or changing another event's data (SC8, US-14).

## Decision

1. **Keycloak** is the OpenID Connect provider, self-hosted in a container with its own PostgreSQL database. In Sprint B its realm import with test accounts is the mocked authentication.
2. **Tokens carry identity only** (`sub`, `email`, `name`, `aud = farol-api`). Roles per event (`EventAdmin`, `RegularUser`), team assignments and the global `FarolAdmin` role live in Auth & Users; teams and their permissions live in Events. They reach the other services as events, and each service keeps a local copy.
3. **Every service validates the JWT** (Spring Security resource server: signature, issuer, audience, expiry); the API gateway validates it too ([ADR-013](0013-api-gateway.md)).
4. **Permissions are checked in the application layer** of each service, against its local copies, with an `event_id` filter on every query. An EventAdmin has every permission of the event; a RegularUser has the permissions of their teams. A non-member gets 404, a member without the permission 403. No copy means no access.
5. **`FarolAdmin`** is the only global role. It is never accepted for an event's operational data.
6. Accounts are created **just in time**, at the first authenticated `GET /api/v1/me`. Account deletion goes through the Keycloak Admin API with client credentials, then a deletion event to every service.
7. The web app uses **Authorization Code + PKCE** (public client), tokens in memory, access tokens of 5 min and refresh tokens rotated on every use. Session idle is 12 h, or 30 days with *Remember me*.
8. **Service-to-service:** no HTTP between services; one RabbitMQ user per service, limited to its own exchanges and queues.
9. **Secrets** come from environment variables: `.env` locally, GitHub Actions secrets in CI, Azure Key Vault if the cloud alternative is resumed. gitleaks runs as a GitHub Actions job on every PR, planned for Sprint B ([ADR-010](0010-cicd-strategy.md) point 9).

### Keycloak setup

| Item | Setting |
|---|---|
| Realm | `farol`, exported to `infra/keycloak/farol-realm.json` (test users only, never imported outside development and the demo) |
| Clients | `farol-web`: public, Authorization Code + PKCE, redirect URIs on the web app's origins · `farol-auth-users`: confidential, service account limited to managing users (account deletion) |
| Audience | A mapper adds `aud = farol-api` to `farol-web`; every service requires it |
| Login | Email as username; Portuguese and English pages; generic error on wrong credentials (US-01) |
| Brute-force detection | On, per account (not per IP: carrier-grade NAT at the venue) |
| Password policy | Minimum length 10, not equal to the email |
| Sign-up | Off in Sprint B (seeded accounts); on in Sprint C for US-03, with the terms accepted on the form |
| Email verification, reset by email | Out of the MVP: no SMTP server. Known limitation |
| Token lifetimes | Access token 5 min; refresh tokens rotated. Session idle 12 h (a night shift on a control-room laptop); 30 days with *Remember me* (P1 on their own phone) |
| Database | Its own `keycloak` database on the PostgreSQL server |
| Address | `localhost:8180` locally, its own hostname on Azure. **Not** behind the API gateway: the browser talks to it during login. `KC_HOSTNAME` set to that public URL |
| Start | `--import-realm`; `start-dev` while developing, `start` (production mode, HTTP on `localhost`) for the demo |
| First FarolAdmin | `FAROL_BOOTSTRAP_FAROL_ADMIN_EMAIL` grants the role at provisioning when the email matches; no screen for it in the MVP |

Each service sets the public issuer and the internal key address separately, so it starts even when Keycloak is down and fetches keys only when a token arrives (keys cached, refreshed on an unknown `kid`, #43):

```yaml
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: ${FAROL_ISSUER_URI}   # e.g. http://localhost:8180/realms/farol (checked against iss)
  jwk-set-uri: ${FAROL_JWKS_URI}    # e.g. http://keycloak:8080/realms/farol/protocol/openid-connect/certs
  audiences: farol-api
```

### Where each permission is checked

From [`features.md` §4](../../Product/features.md#4-roles-teams-and-permissions-22-october-2026).

| Capability | Requires | Checked by |
|---|---|---|
| Join a published event (QR code or link) | Authenticated | Auth & Users (US-12) |
| Ask for help, report a problem, see own requests and reports, check in, see own badges | Member (any role) | Reports, Events, Badges |
| See every help request; set priority and status, route, close | `HelpRequests.Triage` | Reports |
| Accept and resolve the requests routed to own team | `HelpRequests.Respond` | Reports |
| Confirm, reject and unconfirm reports of a type; send them to a team | `Reports.Review` (for that type) | Reports |
| Answer and resolve the reports sent to own team | `Reports.Respond` | Reports |
| Publish, edit and remove announcements | `Announcements.Publish` | Events |
| Upload the map, edit points of interest | `Map.Edit` | Events |
| Add and change shuttles and temporary stops | `Transport.Edit` | Transports |
| Update entrance status (Could, US-59) | `Entrances.Update` | Events |
| Edit, publish and close the event; create teams and set their permissions | EventAdmin | Events |
| Make members EventAdmin; add and remove team members | EventAdmin | Auth & Users |
| Create, list, suspend and reactivate events | FarolAdmin | Events |
| Name the first or a new EventAdmin | FarolAdmin | Auth & Users |
| See any event's help requests, reports or participants | ❌ never for FarolAdmin | Every service |

### Changes for the API outline and D7

The API outline draft (#123) and guide D7 are updated to match this decision (meeting of 22 October 2026):

- **O2:** login, logout and sign-up happen in Keycloak; `POST /accounts` goes, because the account is created at the first `GET /me`.
- **O3:** the token carries only the identity, as the outline already assumes.
- **O1:** closed by the gateway's route table ([ADR-013](0013-api-gateway.md#route-table)).
- **Event catalogue:** keep `MembershipChanged` (full state, with team ids), `TeamChanged` and `GlobalRoleChanged`; publish `UserProfileChanged` at just-in-time provisioning too; **add** an account-disabled event, so services deny at once; **define** `UserDeleted` with the flow below; **add** the acting user's `sub` to the envelope, for audit.
- **D7, account deletion:** refused while the user is the last EventAdmin of an event. Otherwise Auth & Users deletes the user in Keycloak (Admin API, client credentials), deletes its own data, then publishes `UserDeleted`. If Keycloak is down, the deletion stays pending and is retried.

## Consequences

- One more container and database to run on the local machine; its memory is part of the local sizing (ADR-011).
- The identity provider can be replaced by configuration (e.g. Entra External ID on Azure), because no domain data lives in it.
- Role and permission changes are eventually consistent: target **≤ 5 s**, measured in an integration test. This is how "applies at the member's next action" (features §4.2) is met.
- Every service that checks permissions consumes membership and team events and keeps copies of them.
- Account deletion spans Keycloak and Auth & Users; a pending deletion is retried when Keycloak is down.
- Email verification and password reset by email wait for an SMTP server. Keycloak's sign-up form reveals whether an email is taken; accepted for the MVP.
- Tokens in browser memory are exposed to XSS; a backend-for-frontend is a later hardening.

## Addendum · 31 October 2026 (#170)

Documentary alignment only: no new decision and no new team vote. [`domain-model.svg`](../domain-model.svg) was redrawn to show this ADR's split: memberships, team assignments and the `FarolAdmin` grant (`GlobalRoleGrant`) in Auth & Users; teams and their permissions (`Team`, `TeamPermission`) in Events. The question of which team is the default when two teams handle the same category stays open (see [`domain-model.md`](../domain-model.md)).

## Alternatives considered

| Option | Why not chosen |
|---|---|
| Mock only (seeded users, JWTs signed with a fixed key) | No real sign-up, brute-force protection or session handling; looks like a disabled security control (assignment §11) |
| Spring Authorization Server inside Auth & Users | Login, sign-up, reset pages and brute-force protection written by us; at least one extra week of security-critical code |
| Managed service (Entra External ID, Amazon Cognito, Auth0) | Needs a cloud tenant even for the local delivery; harder local development and testing. Stays the replacement if the Azure alternative is resumed |
| Roles and permissions as token claims | Every team change becomes a Keycloak Admin API call; tokens grow with events and teams; stale until refresh; domain split across two systems |
| Validation only at the gateway, with a trusted `X-User-Id` header | Anyone who reaches a service directly can forge the header |
| Token exchange at the gateway (phantom token, RFC 8693) | High effort for no MVP benefit |
| Mutual TLS between all services | There is no service-to-service HTTP to protect |

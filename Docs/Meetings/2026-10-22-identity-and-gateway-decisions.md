# Identity and API gateway decisions

**Date:** 22 October 2026 (team meeting) · **Recorded:** 24 October 2026, by António Guerra (owner of guide C6 and E1–E4).

## API gateway (guide C6)

Decision: [ADR-013](../Technical/adr/0013-api-gateway.md)

| # | Question | Decision |
|---|---|---|
| 1 | Which gateway | Spring Cloud Gateway, Server WebMvc |
| 2 | Routing | Resource paths; the route table follows the API outline (C8, Bruno) |
| 3 | CORS | Only at the gateway, for the web app's origins |
| 4 | Rate limits | As proposed: 120 requests/min per account, 20 MB on the map upload, and help requests are limited (5 per 10 min per account) |
| 5 | Report limit | A Reports business rule (US-48) |
| 6 | Hosting | Azure. Recorded with the local-only scope (G1, ADR-011): local `docker compose` for the delivery, Azure Container Apps if the cloud alternative is resumed |
| 7 | ADR number | A new ADR. 011 was asked for, but ADR-011 (deployment) and ADR-012 (observability) already exist, so it is **ADR-013** |

## Identity and access (guide E1–E4)

Decision: [ADR-006](../Technical/adr/0006-identity-and-access.md)

| # | Question | Decision |
|---|---|---|
| 1 | E1 · Identity provider | Keycloak, self-hosted |
| 2 | Principle | Roles, teams and permissions stay out of the token and reach the services as events; applied to the role model of 22 October |
| 3 | E2 | Every service and the gateway validate the JWT; the permission is checked in each use case; `FarolAdmin` is a global role in Auth & Users |
| 4 | Token lifetimes and revocation | Accepted: 5 min access token, rotated refresh tokens, 12 h / 30 days of idle session, revocation ≤ 5 s |
| 5 | E3 and E4 | Accepted, with GitHub Actions secrets in CI |
| 6 | Deletion flow (D7) and event catalogue (C8) | Bruno updates them to match ADR-006 |
| 7 | Hosting | Azure, recorded as above: local delivery, Azure Container Apps as the cloud alternative |

## Actions

| Action | Owner |
|---|---|
| Update the API outline (O1, O2, O3), the event catalogue and D7 as listed in [ADR-006](../Technical/adr/0006-identity-and-access.md#changes-for-the-api-outline-and-d7) | Bruno |
| Acceptance criterion for the 429 answer on the help-request page (US-23); confirm the 20 MB map upload limit | Tiago: done in #141 (24 October 2026) |
| Keycloak, its realm export and the gateway in the walking skeleton | Guerra, with Tomás (walking skeleton) |
| Security and Privacy Assessment (E5) | Guerra: written on 25 October 2026 ([assessment](../Technical/security-and-privacy-assessment.md)), to review with the team |

## Linked records

[Decisions guide](../Technical/architecture-decisions-guide.md) C6, E1–E4 · [Architecture §8](../Technical/architecture.md#8-security-authentication-and-authorisation) · [ADR-006](../Technical/adr/0006-identity-and-access.md) · [ADR-013](../Technical/adr/0013-api-gateway.md)

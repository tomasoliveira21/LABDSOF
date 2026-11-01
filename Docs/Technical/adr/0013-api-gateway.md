# ADR-013 · API gateway, CORS and rate limiting

## Status

**Accepted**: decided by the team in the meeting of 22 October 2026 ([record](../../Meetings/2026-10-22-identity-and-gateway-decisions.md)). Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) C6. Numbered 013 because ADR-011 (deployment) and ADR-012 (observability) were already taken.

## Context

- One React web app (guide F1) calls five services deployed independently from Sprint B (guide A2, A4; Sprint A delivered one service, [ADR-001](0001-architectural-style.md) decision 3). Any later change of shape must be driven by evidence, and should not change the public API.
- Every request is authenticated ([ADR-006](0006-identity-and-access.md)). Reports must be rate-limited per account (US-48); help requests needed a decision.
- At the venue, thousands of phones share a few public IP addresses (carrier-grade NAT), so limits per IP would block crowds.
- The web app must be told to slow down at the read peak (429 or 503 with `Retry-After`, #43).
- Services talk only through events ([ADR-004](0004-service-communication.md)); the gateway must not compose answers from several services.
- Delivery is local only; Azure is the studied cloud alternative ([ADR-011](0011-deployment-and-infrastructure.md)).

## Decision

1. A **Spring Cloud Gateway (Server WebMvc)** application, `backend/gateway/`, is the **only public entry point** for the API. Spring Cloud 2025.1 with Spring Boot 4, virtual threads, no database.
2. **Routing by resource path** (`/api/v1/events/{eventId}/help-requests`), each path owned by exactly one service. The paths come from the API outline (guide C8); a test checks that each path reaches its service.
3. **CORS only at the gateway**, for the web app's origins. Never `*`, no credentials.
4. The gateway **validates the JWT** (services validate again) and checks no event roles or permissions: it holds no business logic.
5. **Rate limits per account** (the token's `sub`), never per IP, with Bucket4j. A limit answers 429 with `Retry-After` in `application/problem+json`.
6. **Reports per account** (5 per 10 min, US-48) are a **business rule in the Reports service**, not gateway configuration.
7. The gateway creates the correlation ID and returns it as `X-Correlation-Id`; there is no distributed tracing ([ADR-012](0012-observability.md), decision 1). It counts 429s per policy.
8. **Hosting:** local `docker compose`, with only the gateway (`localhost:8080`), Keycloak and the web app published. If the cloud alternative is resumed, **Azure Container Apps**: gateway, Keycloak and web app with external ingress, services with internal ingress.

### Route table

Paths from the API outline (#123, merged on 26 October 2026 in #124). The outline owns the paths; the table follows it, most specific first.

| Order | Path | Service |
|---|---|---|
| 1 | `/api/v1/me/badges/**` | Badges |
| 2 | `/api/v1/me/**`, `/api/v1/accounts/**`, `/api/v1/events/*/members/**` | Auth & Users |
| 3 | `/api/v1/events/*/help-requests/**`, `/api/v1/events/*/reports/**`, `/api/v1/events/*/field-tasks/**`, `/api/v1/events/*/triage-status` | Reports |
| 4 | `/api/v1/events/*/transport-options/**`, `/api/v1/events/*/bus-observations/**`, `/api/v1/events/*/transport-source-status` | Transports |
| 5 | `/api/v1/events/**` (event, teams, map versions, points of interest, announcements, check-in) | Events |
| — | anything else | 404 at the gateway |

Each route's target comes from an environment variable (e.g. `FAROL_ROUTE_REPORTS_URI=http://reports:8080`). If evidence later moves a resource to another service, only that target changes.

### Rate limits

| # | What | Key | Limit | Where |
|---|---|---|---|---|
| L-1 | Every `/api/v1/**` call | account | 120 requests/min | Gateway |
| L-2 | `POST …/help-requests` | account | **5 per 10 min** | Gateway |
| L-3 | `POST …/reports` | account + event | 5 per 10 min (US-48) | Reports service |
| L-4 | Venue map upload | — | body ≤ 20 MB (default 1 MB elsewhere) | Gateway, that route only |
| L-5 | Login, sign-up | account | Keycloak brute-force detection | Keycloak |

Counters are kept in memory (Bucket4j with Caffeine) while there is one gateway instance. The values are settings, tuned with the k6 results in Sprint C.

### CORS and edge settings

- **Allowed origins:** the web container's origin and `http://localhost:5173` (Vite dev server), from configuration.
- **Headers:** allow `Authorization`, `Content-Type`, `If-None-Match`; expose `ETag`, `Retry-After`, `X-Correlation-Id`, `X-RateLimit-Remaining`.
- **Timeouts:** connect 2 s, response 10 s (longer on the upload route). An upstream that is down gives 503 with `Retry-After`; no retries at the gateway.
- `X-Forwarded-*` trusted only from the entry point in front; client identity headers are never passed on. Spring Security default response headers. The actuator is on a management port that is not published.

## Consequences

- One more container, and a single point of failure while there is one instance (guide C5). It is stateless apart from the rate counters and restarts in seconds.
- The public API does not change when a resource moves to another service: only the route table changes.
- The route table must follow the API outline; the ownership test catches drift.
- Rate counters are lost on restart; if the gateway is scaled out, they move to Bucket4j's PostgreSQL backend (no Redis).
- A participant who sends more than 5 help requests in 10 minutes is refused. The page must say when they can ask again and show the first-aid points (backlog, US-23). Accepted over no limit plus the operator's *Duplicate* status.
- The gateway is part of the k6 load scenarios (#43); they show whether it or a service saturates first.

## Alternatives considered

| Option | Why not chosen |
|---|---|
| No gateway | CORS and limits repeated in five services; clients see every service's address |
| Managed cloud gateway (Azure API Management, AWS API Gateway) | Cannot run in the local delivery or be tested in CI; per-user limits depend on product and tier |
| Generic reverse proxy (NGINX, Traefik, Envoy, Caddy) | Per-account limits need JWT handling that is missing or a plugin; new configuration languages for the team |
| Spring Cloud Gateway WebFlux | Reactive model new to the team; higher ceiling not needed if the k6 scenarios confirm WebMvc |
| Kong Gateway open source | Uncertain availability of open-source images after 3.9 |
| Service prefixes in paths (`/api/v1/reports/...`) | Clients would see the service split; moving a resource would change its URL |
| No limit on help requests | Floods reach the control room; the operator's *Duplicate* status alone was judged not enough |

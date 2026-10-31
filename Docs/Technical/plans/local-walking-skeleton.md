# Sprint A walking skeleton and Sprint B handoff

**Issue:** [#58](https://github.com/tomasoliveira21/LABDSOF/issues/58) · **Correction/implementation:** [#166](https://github.com/tomasoliveira21/LABDSOF/issues/166) · **Updated:** 31 October 2026

## 1. Sprint A boundary: one service, not the full application

A review on 31 October identified deliverable 10 as a blocker and asked for the smallest executable cut before Sprint B. This supersedes the earlier multi-service/authenticated request slice as an **R0 requirement**. That complete design remains the Sprint B target, not missing work to hide behind a documentation-only closure.

R0 is **Swagger/client → create a synthetic help request in Reports → store it in memory → read it by ID**. `POST /api/v1/demo/help-requests` accepts category/description and returns a generated ID, `status: Submitted` and `triage.state: NotTriaged`; `GET /api/v1/demo/help-requests/{id}` returns the stored request. `/api/v1/status` remains the technical health check. This replaces the status-only demonstration as that review asked, while retaining one service/container.

The identity is fixed on the server to `demo-participant`, event `demo-event`, and every response marks `demo: true`. Demo routes exist only with Spring profile `demo`, explicitly enabled in Compose, and are published on loopback only. They are a synthetic prototype, not production endpoints with authentication disabled. No frontend app, database, Keycloak, gateway, broker/outbox, AI or dashboard is required. Data disappears on restart; never submit real incidents or expect dispatch.

Course references supplied earlier: *Product_Management_Slides.pdf* pp.23–25 and *Mapping_Prioritization_Metrics_Slides.pdf* pp.4–5. The skeleton is a small executable slice evolved through later increments; documentation is not a substitute for the first running increment. The current deliberately narrower cut follows the 31 October review, not a claim that the lecture examples prescribe this exact endpoint.

## 2. Concrete contract, code and test

| Artefact | Implemented slice |
|---|---|
| Service | `backend/reports`, Java 21, Spring Boot 4.0.9, independent Maven build |
| REST contract | [OpenAPI](../../../backend/reports/contracts/openapi.yaml): synthetic create/read routes (201/200), validation (400) and missing requests (404); `/api/v1/status` remains the diagnostic (200) |
| Automated test | Random-port HTTP tests cover status/UI/contract, create→read stored synthetic data, invalid input/spoofed identity, missing requests and absence of demo routes without the demo profile |
| Image | `backend/reports/Dockerfile`: builds/tests the JAR in Maven, then runs it as a non-root user in a JRE image |
| Deployment | `infra/compose.yaml`: one Reports container with profile `demo`, loopback port 8080, diagnostic health check, bounded memory and rotated container logs |
| CI | `.github/workflows/ci.yml`: build/test plus container startup and JSON smoke check on **every PR**, main push, release tag and manual run; no docs/draft skip in this minimum |
| Ignore rules | `.gitignore`: `infra/.env`, `__pycache__/`, `backups/`, `target/` and local editor output |

The diagnostic and demo contracts are hand-written before implementation. This isolated demo is not the US-23/US-24 production API; generated business interfaces/clients follow C7 when those stories and their auth/persistence/location/idempotency contracts are implemented. Empty domain/application classes and scaffolds for the other four services are not Sprint A prerequisites.

## 3. Run and demonstrate from the repository root

Requirements: Docker Engine/Desktop with Compose v2; Java 21 and Maven 3.6.3+ only for the independent host build. Docker builds need no host JDK. No secrets, cloud account or database is needed.

```sh
mvn --batch-mode --no-transfer-progress -f backend/reports/pom.xml verify
docker compose -p farol-skeleton -f infra/compose.yaml up --build -d --wait --wait-timeout 120
curl --fail http://127.0.0.1:8080/api/v1/status
docker compose -p farol-skeleton -f infra/compose.yaml ps
docker compose -p farol-skeleton -f infra/compose.yaml logs --tail=50 reports
docker compose -p farol-skeleton -f infra/compose.yaml down
```

Open **[Swagger UI](http://localhost:8080/swagger-ui.html)**, then under **Demo help requests**:

1. Expand `POST /api/v1/demo/help-requests`, choose **Try it out**, retain the synthetic example (category/description), then **Execute**.
2. Expect **201**, a generated `id`, `status: Submitted` and `triage.state: NotTriaged` (shown as **Not triaged**). The event/identity are fixed demo values; callers cannot set them.
3. Copy `id` to `GET /api/v1/demo/help-requests/{requestId}`, then execute: expect **200** with the same stored record.
4. Restart Reports to clear the temporary in-memory data; reading the old ID now returns **404**. `/api/v1/status` still responds 200.

Input categories are Health/Safety/LostPerson/Theft/Other; description is non-blank, at most 500 characters. Invalid/unknown fields return 400. To run the JAR outside Compose, explicitly activate `--spring.profiles.active=demo`; otherwise the demo routes return 404. No credentials are needed for this fixed synthetic identity. The UI loads the existing handwritten contract at `/openapi.yaml`, packaged by Maven from `backend/reports/contracts/openapi.yaml`; there is no second maintained copy or generated competing specification. Springdoc 3.0.3 follows its [Spring Boot 4.0 compatibility line](https://springdoc.org/). Swagger assets are served locally; no external validator is configured.

Expected HTTP response: `{"service":"reports","status":"ok"}`. To avoid a port already in use, run Compose with `REPORTS_PORT=18080` and call that loopback port. Optional local overrides: copy `infra/.env.example` to `infra/.env` and pass `--env-file infra/.env`; no credentials are required. An unavailable/conflicting port must fail startup, not be mistaken for a successful demonstration of another process.

The health check means this diagnostic HTTP endpoint responds; it does not certify a database, auth provider, broker or SLO. A named Compose project limits cleanup to this demo's resources. No volume/backups exist for the in-memory demo; a restart deliberately clears synthetic records. This does not satisfy production durability, auth, per-event isolation, idempotency or SC3/SC5.

## 4. Review evidence and honest issue status

[Execution record](../evidence/walking-skeleton-2026-10-31.md) records the actual commands/results on the #166 branch. CI must also pass on the PR. Reopen #58 and #148 because merged Markdown-only #150/#151/#154 did not implement their runtime acceptance. The corrective PR closes **only #166**; #58 stays open until runnable evidence is reviewed, and #148 stays open until telemetry/dashboard/alert evidence exists. Do not mark board items Done just because their documentation merged.

## 5. Sprint B handoff

The full [local delivery runbook](../local-delivery.md) and [ADR-011](../adr/0011-deployment-and-infrastructure.md) remain the target: five service images, gateway, Keycloak, one PostgreSQL with six owned databases/users, RabbitMQ and WireMock; React UI plus optional AI/observability profiles. Add them incrementally to the existing Compose file instead of adding non-existent dependencies now.

First authenticated business slice: seeded synthetic event and users, one help-request button, Reports persistence/outbox, stored-status read and broker round trip. Implement domain/security/isolation and recovery tests with that slice. No cross-service shared database or synchronous domain calls are introduced. Identity/gateway work stays coordinated with ADR-006 and ADR-013; platform/CI decisions with ADR-010.

The [minimal observability plan](local-observability.md) remains two extra containers, Prometheus/Grafana plus Actuator/Micrometer and correlated logs; #148 owns its actual implementation. The [AI decision](ai-provider-evaluation.md) remains local Ollama/Qwen3 with human approval and measured evaluation. Neither is claimed running by this one-service demo.

## 6. Infrastructure answers retained

G1–G3: local only, cloud costs/alternatives retained, no cloud region or paid provisioning; explain the professor's cost authorisation in the report. G4–G6: isolated local dev/test/demo projects and versioned Compose/Dockerfiles; full stack/configuration comes in Sprint B. F2/C1: local React and RabbitMQ then, not deployed in this Sprint A cut. G7: R0 rollback rebuilds the previous reviewed tag; database dumps, expand/contract migrations and measured restore/RPO apply when persistence exists. Existing NFR recovery targets are not measured by this in-memory demo.

# Walking skeleton execution record · 31 October 2026

**Issue:** #166 · **Branch:** `chore/166-minimal-walking-skeleton` · **Scope:** minimum Sprint A diagnostic Reports service; not the Sprint B application.

## Host build and automated HTTP test

Command: `mvn --batch-mode --no-transfer-progress -f backend/reports/pom.xml verify`.

Observed locally on Java 21.0.5, Maven 3.9.9: **BUILD SUCCESS**, one test, zero failures/errors/skips; first build 26.024 s. `StatusEndpointTest` started the real Spring Boot 4.0.9 HTTP server on a random port and verified the diagnostic JSON response. Maven created the executable `target/reports.jar`; build output is ignored by Git.

## Container and CI evidence

Observed locally with Docker Engine 27.4.0:

```sh
docker compose -p farol-skeleton-166 -f infra/compose.yaml up --build -d --wait --wait-timeout 120
curl --fail --include http://127.0.0.1:8080/api/v1/status
docker compose -p farol-skeleton-166 -f infra/compose.yaml ps
```

- Multi-stage build completed, including the same one-test Maven verification inside the builder; runtime image ID `sha256:490a74e399c7f7b3df0fb99b1df70c4c15e221f6b3d4d1cdca92c05c0273777a`.
- Container `farol-skeleton-166-reports-1`: **healthy**, user `10001:10001`, published only at `127.0.0.1:8080`.
- Live response: **HTTP 200**, `Content-Type: application/json`, `{"service":"reports","status":"ok"}`.
- One idle `docker stats --no-stream` sample: 110.1 MiB / 512 MiB limit, CPU 0.85%. This is an observation, not a capacity benchmark or minimum-RAM guarantee.
- The Docker Desktop credential helper stalled on public-image metadata lookup. The local verification used an isolated temporary anonymous Docker config pointed at the same daemon; no credentials/settings were changed. Standard commands above apply normally when the user's Docker helper is functioning. Both official base manifests are pinned by digest in the Dockerfile.

The CI workflow executes the build/test and container smoke on each PR. Its GitHub run result is linked from the corrective PR after it completes; local success alone is not reported as remote CI success. No model, database, auth, dashboard, resilience or SLO result is supplied by this slice.

## Review checks

Markdown links/anchors, XML/YAML syntax, Compose configuration and ignore rules were checked. #58/#148 remain open; the board was corrected from Done to In progress (#58) and Backlog (#148). #166's PR closes only the corrective issue and requires the repository's normal Copilot/team review.

## Swagger UI follow-up (31 October 2026, #166)

At Tomás's request, Springdoc 3.0.3 adds `/swagger-ui.html` with the existing handwritten contract served at `/openapi.yaml`. Maven packages that file directly from `contracts/`; Docker includes the same input. Automatic `/v3/api-docs` generation is disabled while the UI configuration endpoint remains available.

Host `mvn verify` passed **two HTTP tests, zero failures/errors/skips** (14.881 s): the existing status response plus UI redirect/HTML, configuration pointing to the handwritten contract, exact served-contract contents and absence of a competing generated spec. CI also checks UI HTML and the packaged YAML against the repository file. The running local container is rebuilt for browser verification; CI results are linked in PR #167.

## Product-related synthetic slice (31 October 2026, #166)

Tomás requested a meaningful Reports operation instead of using only the diagnostic as the demonstration. The new demo-profile routes create and read a synthetic help request in memory. Status is `Submitted`; `triage.state` is `NotTriaged`, matching the production terminology without claiming AI ran. Identity/event are fixed on the server; every record marks `demo: true`. No real request is sent, persisted durably or dispatched.

- Host `mvn verify`: **10 HTTP test cases, 0 failures/errors/skips**, 23.350 s. Coverage includes create→read the same record, invalid category, missing category, blank/oversized description, forged identity field rejection, missing record, disabled demo routes without the profile, and the existing status/UI/contract checks.
- Actual rebuilt Compose container: POST `/api/v1/demo/help-requests` returned **201** with generated UUID, demo identity/event, Submitted and NotTriaged. GET of the Location returned **200** and the exact same record.
- Reports was restarted and waited healthy; GET of the old ID returned **404**, confirming temporary in-memory storage. The restarted demo is left running.
- CI now performs the same synthetic create/read flow against the actual image, in addition to status and Swagger/contract checks. The latest GitHub result is recorded on PR #167; earlier CI success is not substituted for this follow-up's result.

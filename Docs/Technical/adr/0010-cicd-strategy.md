# ADR-010 · CI/CD strategy

## Status

**Accepted**: decided by the team on 23 October 2026 (#131, Bruno). Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) H1–H8. It brings together the versioning decision (H7, #127) and the local delivery procedure ([`local-delivery.md`](../local-delivery.md), #129, [ADR-011](0011-deployment-and-infrastructure.md)). **Tomás** implements it in the walking skeleton (R0, #58); the minimum Sprint A scope was narrowed by the 31 October review relayed by Tomás (#166); the full pipeline remains the Sprint B target.

## Context

- The assignment asks for an automated pipeline that compiles, tests, runs static analysis and dependency or security checks, builds container images and deploys to a suitable environment, with rapid and visible feedback when a change breaks something (§8.5). Builds must be reproducible, secrets handled securely and rollback documented (§8.4).
- Five Spring Boot services and the API gateway ([ADR-013](0013-api-gateway.md), `backend/gateway/`), each its own Maven project in `backend/<name>/` with its own Dockerfile (ADR-001, ADR-002), and one React web app in `frontend/`, all planned in one monorepo (originally private, now public; guide H2, H8 and point 10). The initial workflow was `.github/workflows/conventions.yml` (branch name, PR title, linked issue on every PR); R0 now also has executable CI (point 9).
- Delivery is **local only**, because cloud hosting costs were not justified ([ADR-011](0011-deployment-and-infrastructure.md)). The earlier "deploy to a cloud production environment on every merge" (guide H4, H5) no longer applies. Delivery means building from the release tag on a laptop with `docker compose` and running the smoke test by hand ([`local-delivery.md`](../local-delivery.md)). CI never reaches anyone's machine.
- Versioning (guide H7): one SemVer version for the monorepo, an annotated tag `vX.Y.Z` on `main`, images `farol-<service>:X.Y.Z` (other builds `dev`), **no registry** and no GitHub Releases. Rolling back means building the previous tag.
- The [Security and Privacy Assessment](../security-and-privacy-assessment.md) found no dependency or image scanning (finding 12, threat T18) and left the answer to this ADR.
- The 23 October pipeline was designed around a private repository and limited Actions minutes. On 31 October Tomás made the repository **public** and requested SonarQube Cloud analysis (#168). The earlier blanket claim that Sonar requires payment for private repositories was incorrect: its current Free offering also permits private projects up to 50k lines. See the [activation guide](../sonarqube-cloud.md); plan eligibility is confirmed in the owner account.

## Decision

1. **Tool and layout (H1, H2, H3, H8).** **GitHub Actions** in the monorepo (now public; point 10). **Maven** with **Java 21** and **Spring Boot 4.x** for every service; every image is built from a **multi-stage Dockerfile** in its own folder (`backend/<name>/Dockerfile`, `frontend/Dockerfile`).

2. **Two workflows.**
   - `conventions.yml`, unchanged, on every PR. Dependabot PRs are exempt, because Dependabot cannot follow the branch, title and issue conventions.
   - `ci.yml` with the jobs below. Its required check is one final job, `CI`, that passes only if every other job passed or was skipped. Branch protection then needs only two required checks, **Conventions** and **CI**, and adding a service does not change them.

3. **Jobs and when they run (H4).** The whole repository is always checked: there are no per-service path filters, every service runs in every run that runs code. The one exception is a pull request or push that only changes documentation (every file under `Docs/` or ending in `.md`): it skips the code jobs.

   | Job | What it does | PR | Push to `main` | Tag `vX.Y.Z`, manual run |
   |---|---|---|---|---|
   | `changes` | Decides whether the change is documentation only | ✓ | ✓ | not needed: always everything |
   | `secrets` | **gitleaks** over the PR's commits; fails on any secret | ✓ (also docs) | | |
   | `backend` (matrix, one entry per service and the gateway) | `mvn -B verify`: compile, unit tests, integration tests with **Testcontainers** (PostgreSQL, RabbitMQ), **Checkstyle** (Google rules, fails the build) | ✓ | ✓ | ✓ |
   | `web` | `npm ci`, **ESLint**, tests, `npm run build` | ✓ | ✓ | ✓ |
   | `delivery` | Builds the seven images (five services, gateway, web app) with `docker compose -f infra/compose.yaml build` (`VERSION` is `X.Y.Z` on a tag, `dev` otherwise), scans them with **Trivy**, starts them with `up -d --wait` from `infra/.env.example`, runs the smoke test of [`local-delivery.md`](../local-delivery.md) §7 without the phone and the login (all containers healthy, web app answers HTTP 200, the gateway answers `401` without a token, one RabbitMQ round trip), then `down -v` | | ✓ | ✓ |
   | `CI` | Required check: fails if any job above failed or was cancelled | ✓ | ✓ | ✓ |

   - **Pull requests get the fast part:** compile, tests, static analysis and secrets. **`main`, tags and manual runs get everything**, including images, the image scan and the smoke test. A Dockerfile or Compose error is found on `main` right after the merge; whoever merged fixes it forward straight away (`main` stays deployable).
   - **Draft PRs** skip `backend`, `web` and `delivery`; they run when the PR is marked ready for review.
   - **Manual run** (`workflow_dispatch`) runs everything on any branch. It is how the full pipeline is shown live in a sprint review.
   - Runs of the same PR cancel the older run (`concurrency`). Every job has a `timeout-minutes`. Maven and npm downloads are cached (`setup-java`, `setup-node`).

4. **Tags (H7).** A tag runs the same full pipeline. It also checks that the tagged commit is on `main`. The images are built and tested but **not published or kept**: there is no registry and no image artefact. Delivering a release, and rolling back, mean building that tag locally ([`local-delivery.md`](../local-delivery.md) §4, §5).

5. **Delivery (H5).** CD stops at a tested, tagged commit. The release is delivered by building that tag locally with `docker compose` and running the smoke test by hand ([`local-delivery.md`](../local-delivery.md)). The pipeline never connects to a team member's machine. Cloud deployment is **not part of the current scope** ([ADR-011](0011-deployment-and-infrastructure.md)); the pipeline's `delivery` job runs the same Compose file and smoke test on the CI runner instead.

6. **Secrets and permissions (H6).** No registry or cloud deployment credentials are needed. **#168 adds `SONAR_TOKEN` as a repository secret solely for Sonar analysis**, with organization/project identifiers in repository variables; fork/Dependabot code never receives it. Workflows run with `permissions: contents: read` (`conventions.yml` with none). The CI smoke test uses the harmless values of `infra/.env.example`. Any secret added later (for example, a hosted AI key, guide K1) is a GitHub Actions secret, never a file in the repository.

7. **Dependency and security checks.**
   - **Dependabot** (`.github/dependabot.yml`): security alerts and security update PRs on; version update PRs **monthly**, one grouped PR per ecosystem (Maven per service, npm, Docker, GitHub Actions), so they do not eat the Actions minutes.
   - **Trivy** in `delivery`: fails on a CRITICAL vulnerability that has a fix available; the others are reported in the job log.
   - **gitleaks** in `secrets`, on every PR.
   - **Dependabot and Trivy answer finding 12 of the [security assessment](../security-and-privacy-assessment.md#10-findings-and-proposed-actions)** (T18, no dependency or image scanning): Dependabot covers the dependencies of every ecosystem, Trivy the images, including the pinned third-party ones (Keycloak, PostgreSQL, RabbitMQ). Guerra marks the finding as answered in the assessment.

8. **Branch protection (H8, README §6).** On `main`, the required checks are **Conventions** and **CI**, plus the review rules of README §5 and §6. **Not active yet** (checked on 30 October 2026: the GitHub API returns `protected: false` for `main`). **To do: Tomás, the repository owner, turns it on** ([`team-and-process.md` §4](../../Process/team-and-process.md#4-process-health-check-what-the-repository-shows-today), agreed on 8 October 2026). The **CI** check can be required only once `ci.yml` exists (point 9).

9. **Walking skeleton R0 — narrowed 31 October 2026 (#166).** Implement one Reports service, a synthetic help-request create/read demo plus diagnostic health, real HTTP tests, multi-stage Dockerfile and Compose. The actual `.github/workflows/ci.yml` has one required job named `CI`: Maven verify, container build/start, JSON smoke check and cleanup on **every PR**, main push, tag and manual run, including drafts/documentation changes. No path filters, cloud/registry access or deployment to a personal machine. The broader jobs in points 2–7 (matrix/web, Testcontainers, Checkstyle/ESLint, gitleaks, Trivy, Dependabot and release-tag ancestry/version handling) are the accepted **Sprint B target**, not implemented capabilities of the minimum. Two independently deployable backend components are a Sprint B requirement, not a Sprint A blocker. The first reviewed release tag remains `v0.1.0`; no release/tag is created by #166.

10. **SonarQube Cloud addition (#168, 31 October 2026).** At Tomás's request, add an independent `SonarQube Cloud` job for same-repository PRs into main, main pushes and manual main runs. It builds/tests Reports with JaCoCo XML, then runs the pinned Maven scanner and waits for the quality gate; missing configuration or a failed gate fails the job. Fork/Dependabot PRs and tags/feature-branch manual runs skip this job. No documentation/draft filters apply to R0. This implements Java analysis for the current Reports module only, not the full matrix/security pipeline above. The owner has created the Sonar project and both GitHub identifier variables are configured; `SONAR_TOKEN`, CI-based analysis setup and the first successful authenticated PR analysis remain pending. [Setup, scope and verification](../sonarqube-cloud.md). This is a user-requested implementation update, not a new team vote.

## Consequences

- The R0 correction intentionally builds/tests and runs one container on every PR to provide visible evidence. The documentation/draft skip policy above applies when the full Sprint B pipeline is implemented, not to this minimum.
- A code PR costs roughly one runner-minute per service plus the web app and gitleaks; a full run on `main` adds the image builds and the smoke test. These are estimates: R0 measures the real durations and writes them here. Usage is checked at each sprint review; if it gets close to the limit, the next cut is to run `delivery` only on tags and manual runs.
- Image, Trivy and Compose failures appear after the merge, not before. That is the price of saving minutes: the team fixes `main` straight away instead of starting new work.
- The smoke test runs on a clean runner every time something reaches `main`, so `local-delivery.md` stays executable. The manual smoke test on the demo laptop is still needed: CI does not cover the phones, the LAN or the laptop's resources.
- Nothing is published, so a release is only as reproducible as its build. Base images are pinned in the Dockerfiles and Compose file (ADR-011); a rebuilt tag can still pick up newer OS packages.
- The documentation-only rule is narrow on purpose (`Docs/` and `*.md` only): a change to a workflow, a Dockerfile, the Compose file or any code runs the code jobs. Tags and manual runs never skip anything.
- No paid GitHub feature is required by this implementation. SonarQube Cloud adds Java static analysis and measured coverage (#168); Checkstyle/ESLint remain planned style checks, and gitleaks/Trivy/Dependabot remain separate planned security checks.

## Alternatives considered

| Option | Why not chosen |
|---|---|
| Everything on every PR (the original H4) | Simplest, catches image and Compose errors before the merge, but the full run on every push of every PR would use up the Actions minutes of a private repository |
| A long-lived `dev` branch: light checks on PRs into `dev`, everything on `dev`→`main` | Saves the same minutes, but breaks GitHub Flow (README §1), needs merge commits instead of squash for `dev`→`main`, and finds errors later, mixed with other people's work |
| One workflow per service, with path filters | Fewer minutes per PR, but a change to a shared contract would not test the services that use it, and the required checks would change with every service (H4) |
| Publishing images to GitHub Container Registry on each tag | Lets the demo laptop pull instead of build, but needs a registry, credentials and retention; guide H7 chose git tags only |
| Keeping the images as workflow artefacts | Around 1–2 GB per run in Actions storage, and still a second way to deliver besides building the tag |
| Deploying to the cloud from the pipeline | Out of scope because of cost ([ADR-011](0011-deployment-and-infrastructure.md)) |
| A self-hosted runner on a team laptop, to deploy there | The pipeline would reach a personal machine and depend on it being on; rejected in ADR-011 |
| SonarQube Cloud | Originally excluded under an incorrect private-project pricing assumption; now selected by Tomás (#168), subject to account setup and a real quality-gate result. Other security checks retain their own scope. |
| SpotBugs next to Checkstyle | More findings, more false positives and more minutes; can be added if Checkstyle proves too weak |
| OWASP Dependency-Check | Slow (downloads the NVD database) and overlaps with Dependabot and Trivy |

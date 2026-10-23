# ADR-010 · CI/CD strategy

## Status

**Accepted**: decided by the team on 23 October 2026 (#131, Bruno). Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) H1–H8. It brings together the versioning decision (H7, #127) and the local delivery procedure ([`local-delivery.md`](../local-delivery.md), #129, [ADR-011](0011-deployment-and-infrastructure.md)). **Tomás** implements it in the walking skeleton (R0, #58); the scope of R0 below is still to be confirmed with him.

## Context

- The assignment asks for an automated pipeline that compiles, tests, runs static analysis and dependency or security checks, builds container images and deploys to a suitable environment, with rapid and visible feedback when a change breaks something (§8.5). Builds must be reproducible, secrets handled securely and rollback documented (§8.4).
- Five Spring Boot services, each its own Maven project in `backend/<service>/` with its own Dockerfile (ADR-001, ADR-002), and one React web app in `frontend/`, all in one private repository (guide H2, H8). The only workflow today is `.github/workflows/conventions.yml` (branch name, PR title, linked issue on every PR).
- Delivery is **local only**, because cloud hosting costs were not justified ([ADR-011](0011-deployment-and-infrastructure.md)). The earlier "deploy to a cloud production environment on every merge" (guide H4, H5) no longer applies. Delivery means building from the release tag on a laptop with `docker compose` and running the smoke test by hand ([`local-delivery.md`](../local-delivery.md)). CI never reaches anyone's machine.
- Versioning (guide H7): one SemVer version for the monorepo, an annotated tag `vX.Y.Z` on `main`, images `farol-<service>:X.Y.Z` (other builds `dev`), **no registry** and no GitHub Releases. Rolling back means building the previous tag.
- The repository is **private**, so GitHub Actions minutes are limited (2,000 per month on GitHub Free, 3,000 on Pro). Four people open many pull requests, and during Sprint A almost all of them only change documentation. CodeQL, GitHub secret scanning and SonarCloud are paid for private repositories.

## Decision

1. **Tool and layout (H1, H2, H3, H8).** **GitHub Actions** in the private monorepo. **Maven** with **Java 21** and **Spring Boot 4.x** for every service; every image is built from a **multi-stage Dockerfile** in its own folder (`backend/<service>/Dockerfile`, `frontend/Dockerfile`).

2. **Two workflows.**
   - `conventions.yml`, unchanged, on every PR. Dependabot PRs are exempt, because Dependabot cannot follow the branch, title and issue conventions.
   - `ci.yml` with the jobs below. Its required check is one final job, `CI`, that passes only if every other job passed or was skipped. Branch protection then needs only two required checks, **Conventions** and **CI**, and adding a service does not change them.

3. **Jobs and when they run (H4).** The whole repository is always checked: there are no per-service path filters, every service runs in every run that runs code. The one exception is a pull request or push that only changes documentation (every file under `Docs/` or ending in `.md`): it skips the code jobs.

   | Job | What it does | PR | Push to `main` | Tag `vX.Y.Z`, manual run |
   |---|---|---|---|---|
   | `changes` | Decides whether the change is documentation only | ✓ | ✓ | not needed: always everything |
   | `secrets` | **gitleaks** over the PR's commits; fails on any secret | ✓ (also docs) | | |
   | `backend` (matrix, one entry per service) | `mvn -B verify`: compile, unit tests, integration tests with **Testcontainers** (PostgreSQL, RabbitMQ), **Checkstyle** (Google rules, fails the build) | ✓ | ✓ | ✓ |
   | `web` | `npm ci`, **ESLint**, tests, `npm run build` | ✓ | ✓ | ✓ |
   | `delivery` | Builds the six images with `docker compose -f infra/compose.yaml build` (`VERSION` is `X.Y.Z` on a tag, `dev` otherwise), scans them with **Trivy**, starts them with `up -d --wait` from `infra/.env.example`, runs the smoke test of [`local-delivery.md`](../local-delivery.md) §7 without the phone (all containers healthy, web app answers HTTP 200, one RabbitMQ round trip), then `down -v` | | ✓ | ✓ |
   | `CI` | Required check: fails if any job above failed or was cancelled | ✓ | ✓ | ✓ |

   - **Pull requests get the fast part:** compile, tests, static analysis and secrets. **`main`, tags and manual runs get everything**, including images, the image scan and the smoke test. A Dockerfile or Compose error is found on `main` right after the merge; whoever merged fixes it forward straight away (`main` stays deployable).
   - **Draft PRs** skip `backend`, `web` and `delivery`; they run when the PR is marked ready for review.
   - **Manual run** (`workflow_dispatch`) runs everything on any branch. It is how the full pipeline is shown live in a sprint review.
   - Runs of the same PR cancel the older run (`concurrency`). Every job has a `timeout-minutes`. Maven and npm downloads are cached (`setup-java`, `setup-node`).

4. **Tags (H7).** A tag runs the same full pipeline. It also checks that the tagged commit is on `main`. The images are built and tested but **not published or kept**: there is no registry and no image artefact. Delivering a release, and rolling back, mean building that tag locally ([`local-delivery.md`](../local-delivery.md) §4, §5).

5. **Delivery (H5).** CD stops at a tested, tagged commit. The release is delivered by building that tag locally with `docker compose` and running the smoke test by hand ([`local-delivery.md`](../local-delivery.md)). The pipeline never connects to a team member's machine. Cloud deployment is **not part of the current scope** ([ADR-011](0011-deployment-and-infrastructure.md)); the pipeline's `delivery` job runs the same Compose file and smoke test on the CI runner instead.

6. **Secrets and permissions (H6).** No registry and no cloud deployment, so the pipeline needs **no credentials and no repository secrets**. Workflows run with `permissions: contents: read` (`conventions.yml` with none). The CI smoke test uses the harmless values of `infra/.env.example`. Any secret added later (for example, a hosted AI key, guide K1) is a GitHub Actions secret, never a file in the repository.

7. **Dependency and security checks.**
   - **Dependabot** (`.github/dependabot.yml`): security alerts and security update PRs on; version update PRs **monthly**, one grouped PR per ecosystem (Maven per service, npm, Docker, GitHub Actions), so they do not eat the Actions minutes.
   - **Trivy** in `delivery`: fails on a CRITICAL vulnerability that has a fix available; the others are reported in the job log.
   - **gitleaks** in `secrets`, on every PR.

8. **Branch protection (H8, README §6).** Active on `main`: the required checks are **Conventions** and **CI**, plus the review rules of README §5 and §6.

9. **Walking skeleton (R0, Tomás).** R0 implements all of the above for the services it creates (at least two, to show independent deployment): `ci.yml`, the Dependabot file, the Dependabot exemption in `conventions.yml` and the round-trip command in the smoke test. Every new service is added to the `backend` matrix and to `infra/compose.yaml` when it is created. The first tag is `v0.1.0` at the end of Sprint A.

## Consequences

- During Sprint A almost every PR is documentation only and costs seconds of Actions time; the code jobs exist and run as soon as there is code.
- A code PR costs roughly one runner-minute per service plus the web app and gitleaks; a full run on `main` adds the image builds and the smoke test. These are estimates: R0 measures the real durations and writes them here. Usage is checked at each sprint review; if it gets close to the limit, the next cut is to run `delivery` only on tags and manual runs.
- Image, Trivy and Compose failures appear after the merge, not before. That is the price of saving minutes: the team fixes `main` straight away instead of starting new work.
- The smoke test runs on a clean runner every time something reaches `main`, so `local-delivery.md` stays executable. The manual smoke test on the demo laptop is still needed: CI does not cover the phones, the LAN or the laptop's resources.
- Nothing is published, so a release is only as reproducible as its build. Base images are pinned in the Dockerfiles and Compose file (ADR-011); a rebuilt tag can still pick up newer OS packages.
- The documentation-only rule is narrow on purpose (`Docs/` and `*.md` only): a change to a workflow, a Dockerfile, the Compose file or any code runs the code jobs. Tags and manual runs never skip anything.
- No paid GitHub feature is needed: gitleaks replaces secret scanning, Checkstyle and ESLint replace CodeQL or SonarCloud, Trivy covers the images.

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
| SonarCloud, CodeQL, GitHub secret scanning | Paid for private repositories |
| SpotBugs next to Checkstyle | More findings, more false positives and more minutes; can be added if Checkstyle proves too weak |
| OWASP Dependency-Check | Slow (downloads the NVD database) and overlaps with Dependabot and Trivy |

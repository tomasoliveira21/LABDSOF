# SonarQube Cloud analysis in CI

**Issue:** [#168](https://github.com/tomasoliveira21/LABDSOF/issues/168) · **31 October 2026**

## What this implements

The existing `.github/workflows/ci.yml` keeps its build/test/container-smoke **CI** job and adds a separate **SonarQube Cloud** job. It analyzes the implemented Maven project, `backend/reports`, using SonarScanner for Maven **5.8.0.7211** and real **JaCoCo 0.8.16** XML coverage. Maven supplies source/test/bytecode metadata; no invented result, coverage exclusion or reduced quality threshold is configured. A failed Sonar quality gate fails that job; the scanner waits up to 300 s. Analysis uploads code/metadata to SonarQube Cloud; application execution/deployment remains local.

Current scope is Reports, including its demo classes and tests. There is no root Maven aggregator or frontend yet; adding services/frontend requires extending analysis deliberately, not claiming this project already covers nonexistent components or all repository artefacts. The scanner version is pinned in the POM, and `sonar:sonar` uses that declared plugin.

## One-time activation by the repository owner

The repository is **public** (verified on 31 October, after the change). This PR does not change visibility. SonarQube Cloud's current Free offering also supports private projects up to 50k lines; the old ADR claim that private analysis always requires payment was outdated. Choose the appropriate Free plan and confirm project visibility matches the repository; no paid subscription/trial is required by this implementation. Making GitHub public alone does not create a Sonar project or authentication token.

1. Sign in to [SonarQube Cloud EU](https://sonarcloud.io/) with GitHub. Import the GitHub personal account/organization and select **only LABDSOF** for the project binding. The owner completes any account terms or GitHub App permissions; do not publish credentials in chat, Git or logs.
2. Create/import the project, with **main** as its main branch and the GitHub binding enabled for PR decoration. Copy the actual **organization key** and **project key** from the project setup; do not guess them from the GitHub name.
3. Select **CI-based analysis / GitHub Actions / Maven**, and disable **Automatic Analysis**. Sonar explicitly disallows running both analysis methods on the same project.
4. In GitHub **Settings → Secrets and variables → Actions**, set:

| Kind | Name | Value |
|---|---|---|
| Repository variable | `SONAR_ORGANIZATION` | `tomasoliveira21` (configured) |
| Repository variable | `SONAR_PROJECT_KEY` | `tomasoliveira21_LABDSOF` (configured) |
| Repository secret | `SONAR_TOKEN` | A token with Execute Analysis permission for this project |

The token belongs only in the secret; never provide it as a `-Dsonar.token=...` CLI argument, repository variable or committed file. Keys are identifiers, not tokens. The workflow passes them through quoted environment variables and uses the EU endpoint `https://sonarcloud.io`.

5. Bootstrap the main-branch baseline before expecting a successful PR quality gate: once this workflow is available on main, use its manual **Run workflow** on **main** (or the first main push). A first setup PR may need review before that bootstrap; do not fabricate a successful PR analysis when the base has no Sonar analysis yet. If testing before merge, the owner can run the same pinned Maven scanner on a clean main checkout, using these actual keys and SONAR_TOKEN in the environment.
6. Re-run the job on the setup PR. Confirm a real report in Sonar's **Pull requests** page, coverage import and the returned quality gate before treating analysis as operational. Then make **SonarQube Cloud** a required check if the team wants it to block merges. The documented required checks are **Conventions** / **CI**; branch protection has been active since 30 October 2026, with no required check yet (ADR-010 §8). This PR does not modify it.

The [public project](https://sonarcloud.io/project/overview?id=tomasoliveira21_LABDSOF) was provided on 31 October. Its public API confirmed organization/project identifiers and a main analysis of commit `b298b1e` at 12:32 UTC. Both GitHub variables above are now configured. `SONAR_TOKEN` was added as a repository secret. The existing main analysis was not proof of this CI integration (its quality-gate API initially returned `NONE`). The authenticated CI rerun below now validates the integration. The configuration check intentionally fails with missing **names only**; it does not silently skip trusted analyses or print secret values. A green build/test job alone is not evidence of Sonar analysis.

## Observed activation evidence — 31 October 2026

[GitHub Actions run 41202153038, attempt 2](https://github.com/tomasoliveira21/LABDSOF/actions/runs/41202153038/attempts/2), PR #169 at commit `d02ec25977a0812be42623c7a786335ad5220ea7`:

- Configuration check passed with the repository secret; no token value was collected or committed.
- All 10 HTTP tests passed; JaCoCo generated XML and the Sonar JaCoCo XML importer ran successfully. Local line coverage measured 25/27 (92.6%).
- The Maven scanner completed authenticated PR analysis and returned **QUALITY GATE STATUS: PASSED** at 12:46 UTC. [PR report](https://sonarcloud.io/dashboard?id=tomasoliveira21_LABDSOF&pullRequest=169).
- The existing **CI** container/demo/Swagger smoke and **Conventions** checks passed too.

The initial missing-configuration failure is retained in Actions history. The successful scanner run confirms that Automatic Analysis did not block CI-based analysis. This validates the current setup PR; it does not assert a clean historical main baseline or future quality gates. This PR adds build/CI configuration rather than Java features, so its passing new-code gate does not certify all existing code. Branch-protection activation and normal peer review remain separate.

## Triggers and secret boundary

| Event | Existing CI | SonarQube Cloud |
|---|---|---|
| PR from this repository into main (including drafts/docs changes) | Build/test/container smoke | Analyze PR head with full Git history; fail explicitly if setup is missing |
| Push to main | Build/test/container smoke | Main baseline analysis |
| Manual run on main | Build/test/container smoke | Main analysis/bootstrap |
| Fork PR or Dependabot PR | Build/test/container smoke | **Skipped deliberately**: no Sonar secret exposed to that code |
| Tag or manual run on a feature branch | Existing CI behaviour | Skipped: no unintended paid branch-analysis requirement |

There is no `pull_request_target`, extra write permission, secret fallback, dummy token or `continue-on-error`. Sonar handles PR decoration through the imported GitHub binding; the workflow token permissions remain read-only. Fork/Dependabot skips are a visible limit, not completed static analysis of those changes. Use trusted review/another safe process before relying on analysis of outside contributions; do not rerun untrusted code with secrets to bypass this boundary.

## Local verification and reports

```sh
mvn --batch-mode --no-transfer-progress -f backend/reports/pom.xml verify
```

This produces `backend/reports/target/site/jacoco/jacoco.xml` and an HTML report under the same directory. Build output remains ignored. Sonar does not generate test coverage; JaCoCo measures the actual test execution first. The scanner reads the explicit XML path from the POM. Coverage/quality gate results must be reported as observed; do not exclude demo code or lower the gate just to obtain a pass.

For an authenticated local scan of a clean main checkout, with the three actual values already in the environment:

```sh
mvn --batch-mode --no-transfer-progress -f backend/reports/pom.xml verify sonar:sonar \
  -Dsonar.host.url=https://sonarcloud.io \
  "-Dsonar.organization=$SONAR_ORGANIZATION" \
  "-Dsonar.projectKey=$SONAR_PROJECT_KEY" \
  -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=300
```

Do not use a modified working tree as the main baseline. Rotate a leaked token through the owner; no token is collected or saved by these instructions.

## References and decision update

- [Official Maven scanner](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/scanners/sonarscanner-for-maven): Maven lifecycle, authentication and pinned versions.
- [Java coverage](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/test-coverage/java-test-coverage): generate/import JaCoCo XML before analysis.
- [GitHub Actions integration](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/ci-based-analysis/github-actions-for-sonarcloud): full checkout and quality-gate wait.
- [CI versus automatic analysis](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/ci-based-analysis/overview-of-integrated-cis): use one method.
- [Current pricing](https://www.sonarsource.com/plans-and-pricing/): Free private-project allowance; public/OSS plan eligibility is selected in the owner's account.

[ADR-010](adr/0010-cicd-strategy.md) records this user-requested Sonar addition. Checkstyle remains a separate possible style check; Sonar is not described as a dependency-CVE or container-image scanner, and those security checks are not claimed implemented here.

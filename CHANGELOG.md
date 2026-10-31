# Changelog

All notable changes to the repository documentation. Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased] · 2026-10-31 · SonarQube Cloud CI (#168)

### Added
- Separate SonarQube Cloud job for trusted PRs into main and main analyses: pinned Maven scanner, measured JaCoCo XML coverage and quality-gate wait/failure. Existing container CI remains in place.
- Owner activation guide with GitHub variables/secret, main baseline, automatic-analysis setting and explicit fork/Dependabot limits. Missing configuration fails visibly. After owner setup, run 41202153038 attempt 2 completed authenticated PR #169 analysis with quality gate PASSED; activation evidence is linked in the guide.

### Corrected
- ADR-010 and decisions-guide addendum record Tomás's Sonar request and public repository; correct the blanket private-project pricing claim. Application delivery remains local; full security/style checks and branch-protection activation remain separate work.

## [Unreleased] · 2026-10-31 · Deliver minimal executable skeleton (#166)

### Added
- One Java 21 / Spring Boot 4.0.9 Reports service, diagnostic OpenAPI contract and real random-port HTTP test; multi-stage Dockerfile, loopback-only Compose deployment and CI build/test/container smoke on every PR.
- Ignore rules for local env, Python caches, backups and Maven output; repeatable R0 commands and an actual branch execution record.
- Swagger UI at `/swagger-ui.html` loads the existing handwritten `/openapi.yaml`; HTTP/CI checks cover the UI, configuration and packaged contract.
- At Tomás's request, the meaningful R0 slice now creates/reads synthetic help requests in memory, with fixed demo identity/event and NotTriaged state. Demo routes require the explicit demo profile; status remains a health check. Production persistence/auth/outbox and US-23/24 acceptance stay in Sprint B.

### Corrected
- Reopened #58/#148: prior Markdown-only merges were documentary delivery, not runnable software. The corrective PR closes only #166 and leaves those runtime issues open for reviewed evidence.
- ADR-005/012 now record Accepted architecture based on the existing guide/NFR agreement from 26–27 October; Tiago's J1 approval is explicitly timeout/circuit policy, not model/telemetry validation. K2/privacy and runtime/SLO evidence remain pending.
- Narrowed Sprint A R0 to one endpoint/test/service. The full Keycloak/gateway/web/database/outbox/broker stack and full ADR-010 pipeline remain Sprint B increments; no cloud provisioning or complete application delivery is claimed.

## [Unreleased] · 2026-10-30 · Diagrams and false claims corrected (#161, Bruno Lopes)

**Why:** a review of the documentation against `main` and GitHub found diagrams that no longer followed the decisions in force (the local AI profile of 27 October 2026, WireMock in front of the local AI, J4) and process claims that GitHub does not show (branch protection, CI runs, the Sprint A dates, direct pushes). Each claim was checked again on 30 October 2026.

### Changed
- `process-view-level-{1,2,3,4}-help-request`: the AI call has one attempt and no retry; the branch labels are "[AI unavailable]", "[AI timeout or error]" and "[Timeout or error]"; the arrows are "[HTTP, timeout, one attempt]" (level 3) and "[HTTP, timeout, one attempt, circuit breaker]" (level 4). Aligned with `architecture.md` §9.1 (local AI, accepted on 27 October 2026), guide J1 and K1, NFR-11, and the AI plan (Ollama on `http://ollama:11434`, private network). The broker's "Reject, 4 attempts with backoff" is unchanged.
- `physical-view-level-{2,3}` and `mapping-logical-physical-level-{2,3}`: Reports → WireMock → Ollama (`HTTP (proxy)`) replaces the direct Reports → Ollama link; WireMock is the simulator "for Porto Digital and the AI provider"; the mappings link the logical AI provider to WireMock, as for Porto Digital. The hosted provider stays as the alternative. Aligned with guide J4 (confirmed on 26 October 2026, #155), `architecture.md` §9.2 and NFR-31.
- `local-delivery.md` §1: WireMock is in front of the local AI; the `ai` container is Ollama with `qwen3:4b` (guide K1). §8: D1 is shown with a WireMock stub, as in the demo script (`architecture.md` §9.3).
- Branch protection notes in `Docs/README.md` (§1 and §6), `team-and-process.md` (§3 rules table and §4), ADR-010 point 8 and guide H8: not active yet, with the action for Tomás, the repository owner (agreed on 8 October 2026). The H8 decision is unchanged.
- `2026-10-22-professor-meeting.md`: Bruno's two diagram actions ticked, with a note. `physical-view-level-3` shows six databases, not five (five services and Keycloak, ADR-003), and no diagram mentions a mobile app (#157, #55).

### Corrected
- ADR-010 point 8 said branch protection was "Active on `main`". The GitHub API returns `protected: false` for `main`.
- `local-delivery.md` §7 said the smoke checks "run in CI … on every push to `main`". No CI workflow exists yet: the only workflow is `conventions.yml`, and it runs only on pull requests (`gh run list`). Now written in the future tense, once `ci.yml` exists (ADR-010 point 9).
- `team-and-process.md` §3 said Sprint A ran "Until the review on 12 October 2026". The *Sprint A* milestone is due on 1 November 2026, and Sprint B starts on 2 November; 12 October was the Sprint 1 presentation.
- `team-and-process.md` §4, and the entry *2026-10-08 · Sprint 1 presentation artifacts* below, said `5ef2c4a`, `3a4ba04` and `e853854` were pushed directly to `main`. They were not: `5ef2c4a` is the commit of PR #1 and `3a4ba04` its merge commit, and `e853854` is the first commit of PR #21 (`gh pr view 1`, `gh pr view 21`, `git log --first-parent`). Every commit on `main` came through a PR, except the initial commit. Protection is still off.
- The entry *2026-10-23 · Local delivery and demo procedure* below put WireMock in front of "a hosted AI provider". It is in front of the local Ollama container (guide J4).

### Not changed
- The older entries of this changelog and the meeting records: the corrections are recorded here.
- `adr/0011-deployment-and-infrastructure.md` point 5 ("CI still builds and tests the images") is a decision, not a status.
- `architecture.md` §7 still describes the AI provider with "Timeout, retry and circuit breaker" and **[PROPOSED for review]**, against §9.1. Left for the documentation clean-up.

## [Unreleased] · 2026-10-29 · Assessment retention aligned with the NFRs (#159, António Guerra)

**Why:** the Security and Privacy Assessment still had its own proposal of 25 October 2026: application logs 14 days, traces 7 days, no backups. The team decided other values for NFR-19 and NFR-13 (non-functional requirements §5, 26 October 2026), and the telemetry ones changed again with the simplified observability plan (#153, 27 October 2026). The decisions guide (D5, G7) already had them.

### Changed
- `security-and-privacy-assessment.md`:
  - Summary and §7: retention is decided, no longer **[PROPOSED]**. Container logs rotated at 10 MiB × 2 files, sanitised log exports deleted within 14 days, metrics 1 day or 512 MiB, no traces stored. A dump of the six databases before every demo and every update, each kept at most 30 days.
  - §7: what restoring a dump means. Data deleted after the dump comes back, accounts included; accepted while the data is synthetic.
  - §3: the logs row names Docker and Prometheus, with no traces; new row for the database dumps.
  - §9: the backup laptop also needs full-disk encryption, and dumps are never committed or copied elsewhere.
  - §10, finding 13: decided; the clean-up jobs stay in Sprint C. §12: "No backups" replaced by the limits of the dumps.
  - Status and sources: update date; the NFRs, `local-delivery.md` and the observability plan added as sources.

### Not changed
- `non-functional-requirements.md` §5 keeps "logs 14 days, traces 7 days" in its 26 October 2026 entry: it records what was validated that day, and the later entries of the same section replace it.

## [Unreleased] · 2026-10-26 · Porto Digital simulator with recorded responses (#155, António Guerra)

**Why:** the professor asked for a simulator of the Porto Digital API, or recorded real responses, under the team's control (21 and 22 October 2026). Guide J4, owned by Guerra, had WireMock as Tiago's proposal (#43) waiting for confirmation. Real responses were recorded on 25 October 2026 to check whether WireMock can replay them as Transports will call it.

### Decided
- **J4: WireMock** is the simulator of the Porto Digital API, confirmed by Guerra, owner of J4. Evidence: the stubs below, played on WireMock 3.13.2.

### Added
- `infra/wiremock/recordings/porto_bus_responses_20261025.zip`: 300 real responses of the whole-city query (`q=vehicleType==bus&limit=1000`), Sunday 25 October 2026, 15:15–20:14 UTC, about one a minute with no gaps, and `manifest.csv` with the SHA-256 of each. 89–118 STCP buses per response; no invalid entries; no personal data (the driver's shift is always `na`).
- `infra/wiremock/scripts/convert_porto_digital.py`: turns the zip into stubs. It keeps only the buses within 2 km of the Queimódromo (`41.1724,-8.6841`, OpenStreetMap), as `georel=near;maxDistance:2000` would; renders each `observationDateTime` as `{{now offset='-N seconds'}}` with the bus's recorded age, so the data passes the 5 min freshness limit whenever it is replayed; and chains the snapshots as a WireMock scenario where each answers two calls, so the buses move at their real speed with the 30 s import. Same output on every run.
- `infra/wiremock/mappings/porto-digital/` (600 mappings) and `infra/wiremock/__files/porto-digital/` (300 bodies, 2–13 buses and about 9 KB each), generated from the zip.
- `infra/wiremock/scripts/check_porto_digital.py`: plays the whole scenario on a running WireMock and checks every answer against the zip. Run on 26 October 2026: all 600 calls right (snapshot and order, 2 km, freshness, each bus within 3 s of its recorded age), the loop back to the first snapshot, the percent-encoded query, 404 for another point, another distance and the whole-city query, and the scenario reset; median latency 45 ms.
- `infra/wiremock/README.md`: what Transports must send, how the replay works, how to regenerate and check, where the recordings come from, and the limits.

### Changed
- `architecture-decisions-guide.md`: J4 ✅ with the evidence; J2's status, owners table, *Feito* and *Em aberto* item 1 updated; status date.
- `architecture.md` §9 and §9.2 (recorded responses), §12 (technology table): WireMock confirmed.
- `ADR-008` (status and decision 4) and `ADR-004` (open points): the simulator tool is resolved.
- `local-delivery.md` §1: the `wiremock` container's root is `infra/wiremock/`; §9: the J4 open point removed.
- `features.md` §3.1, D1 and D2: WireMock is the simulator, no longer a proposal.
- `2026-10-22-professor-meeting.md`: Guerra's J4 action ticked.
- `security-and-privacy-assessment.md` §10, finding 5: done.

### Not changed
- The D2 failure stubs (503, timeout, invalid data for US-42 and US-43): written by the tests themselves and added by the presenter through the admin API during the demo (`local-delivery.md` §8).
- `infra/compose.yaml` does not exist yet (walking skeleton, #58); until then the README shows how to run WireMock on its own.
- The Porto Digital terms of use and attribution are not checked yet; the repository is private (guide H8).
## [Unreleased] · 2026-10-27 · Account deletion and data ownership (#135, Bruno Lopes)

**Why:** guide D7 was the last open data decision. The team needs to know whether account deletion (F2, US-05) is in the MVP and how a deleted user's data leaves each service. ADR-006 had fixed the flow, but left what each service does on `UserDeleted` to D7, and the assessment's proposal (§7) said Transports does nothing, while the outline has it consuming `UserDeleted`.

### Changed
- `architecture-decisions-guide.md`: D7 decided (✅). Account deletion is in the MVP: US-05 is Must (R2), and the GDPR gives the right to erasure (NFR-19). Also updated the flow, the per-service actions and what stays open; *Próximos passos*; status date.
- `api-outline.md`: §2 has the deletion flow in five steps: `422` for the last EventAdmin and for the last `FarolAdmin`; account disabled at once with `UserStatusChanged`; Keycloak, then Auth & Users, then `UserDeleted` through the outbox; a tombstone with only the `sub` in every service. §1.4: no just-in-time account for a deleted `sub`. §7.4: what each consumer of `UserDeleted` deletes or anonymises; `UserStatusChanged` is also published at the deletion request; `ReportConfirmed` and `ReportUnconfirmed` with no reporter are ignored by Badges.
- `security-and-privacy-assessment.md` §7: the per-service table is decided and no longer **[PROPOSED]**. A deleted user's help requests and reports lose their text and map point at once, not after 30 days; open help requests are closed; Transports deletes its membership copies.
- `adr/0003-database-and-data-ownership.md`: decision 5 (each service deletes or anonymises its own data and keeps a tombstone); open point closed.
- `features.md` §4.1: the last EventAdmin cannot delete their account either.

### Not changed
- `architecture.md` and the diagrams: the *account deletion [Admin API]* arrow from Auth & Users to Keycloak is already right.
- `domain-model.md`: the pending deletion is a state of `AccountStatus`, whose values the model does not list.
- Retention and backups (guide D5), out of scope; dead-letter messages and Keycloak login events that mention the `sub` expire within the D5 periods.

### Merged
- `main` merged (#133 architecture diagrams). Conflicts in this CHANGELOG (both kept, this entry first) and ADR-003 *Open points* (PostgreSQL servers closed from `main`, account deletion closed from this branch).

## [Unreleased] · 2026-10-27 · Architecture diagrams updated (#133, Bruno Lopes)

**Why:** the diagrams still showed the React Native app (removed on 22 October 2026, #125), had no API gateway or Keycloak (ADR-006, ADR-013) and showed a cloud platform still to choose. The professor asked for physical level 3 with the databases (meeting of 22 October 2026; guide *Em aberto* item 5).

### Changed
- **Physical view:** levels 1–3 show the local delivery (ADR-011, `local-delivery.md`). Level 3 has one machine with the web app, the API gateway, Keycloak, the five services (one instance each), RabbitMQ, WireMock and one PostgreSQL container with six databases, one user each (five services and Keycloak, Keycloak's marked third-party); the optional local AI model (K1) and Prometheus/Grafana (ADR-012) are dashed; a browser (phone or laptop) outside the machine; the AI provider and Porto Digital reached through WireMock. No ports and no HTTPS note (kept in `local-delivery.md`).
- **Logical view:** no mobile app; one web app with phone and laptop pages used by all five personas (level 3: one *User* Person, also in its two mappings); API gateway (Spring Cloud Gateway) without `[PENDING]`; Keycloak and its database as containers from level 2 (not external, so not in level 1); account deletion through the Admin API. The web app is opened at level 3 (`logical-view-level-3-web`): level 3 with the phone and laptop pages, data and polling, login (`oidc-client-ts`) and the API client in place of its box.
- **Implementation view:** the web app is `frontend/` itself (guide H2, ADR-010), `backend/gateway/` added, `infra/` holds the Compose file, the Keycloak realm and the PostgreSQL init script; "deploys" removed (local delivery). `implementation-view-level-3-web` is level 3 with `frontend/` opened into `pages/`, `data/`, `api/` and `auth/` (layout still `[PENDING: Tomás]`); event contracts are `contracts/events/` (one JSON Schema per event, `spec-driven-development.md` R3).
- **Process view, help request:** the request goes through the gateway (JWT, rate limit); the event carries only the request id and triage reads the request from Reports' database (assessment M-5); outbox every 1 s; an AI failure ends in the "Not triaged" fallback and an acknowledgement, while a processing failure gets 4 attempts (1 s, 2 s, 4 s) and then the dead-letter queue, moved back by hand (`architecture.md` §6).
- **Process view, transports:** web app instead of mobile app; import every 30 s and freshness limit 5 min (J1); bus positions and event shuttles instead of "regular lines" (ADR-008, API outline §5).
- **Role names** in every diagram: Field-team member, EventAdmin, FarolAdmin (`features.md` §4).
- **Mappings** rebuilt from the updated views; logical → physical links the AI provider and Porto Digital to WireMock and to the real system.
- `architecture.md` §14 without the mobile links and the *TO ALIGN* notes; §2 explains why Keycloak is not in the system context. ADR-009: 32 diagrams, physical view and identity-provider open points closed. ADR-003: one PostgreSQL server with six databases (open point closed). Guide *Em aberto* item 5 ✅.
- **Pending markers checked against `main`:** the AI provider is the local Ollama model (qwen3:4b, guide K1 ✅); in the physical view a hosted provider is dashed as the alternative, behind WireMock. Prometheus and Grafana only (#153), optional `observability` profile, without `[PENDING]`. WireMock stays without a marker (confirmed for J4 with the NFRs, #139). The only `[PENDING]` left is the `frontend/` folder layout.
- **Physical view, levels 2 and 3** (and logical → physical): each container shows its name first in bold and *Docker container* after it.
- **Every diagram** re-exported with draw.io so the content is centred, with equal margins.
- `architecture.md` §14 links the *3 web* diagrams; the system-context sketch and the *Not drawn* note no longer say `[PENDING]` for the AI model and observability. ADR-009: level 4 per service, the web app at level 3; only the `frontend/` layout marker left.

### Added
- `logical-view-level-3-web`, `implementation-view-level-3-web` and `mapping-logical-implementation-level-3-web`.

### Removed
- `logical-view-level-4-mobile`, `implementation-view-level-4-mobile` and `mapping-logical-implementation-level-4-mobile`.
- `logical-view-level-4-web`, `implementation-view-level-4-web` and `mapping-logical-implementation-level-4-web` (replaced by the *3 web* diagrams).

### Merged
- `main` merged (#123 API outline, #127 versioning, #129/#130 local delivery, #131/#132 ADR-010, #145 AI triage coverage, #58/#150 walking-skeleton decisions, #148/#151 local observability, #149/#152 AI evaluation, #153 simplified local demo tools). Conflicts in this CHANGELOG (both kept, this entry first) and the guide (*Em aberto* item 4 from `main`, item 5 ✅ from this branch).
- `main` merged again (#139 NFR list). Conflicts in this CHANGELOG (both kept, this entry first) and the guide (*Em aberto* item 5 from this branch, item 6 from `main`, marked merged). K1 (✅ Ollama + qwen3:4b) and I1 (✅ stack) are now closed in the guide, so the diagrams and `architecture.md` drop the *to review: K1* marker.

## [Unreleased] · 2026-10-24 · Non-functional requirements list (#137, Tiago Teixeira)

**Why:** in the meeting with the professor on 22 October 2026 the NFRs were *missing as one list* for the Sprint A deck (*Presentation contents*); the action was Tiago's.

### Added
- `Docs/Product/non-functional-requirements.md`: 34 NFRs in one table (ID, ISO/IEC 25010 category, requirement, measurable target, how it is verified, source), collected from the architecture drivers, the success criteria SC1–SC11, assignment §6, the degraded modes D1 and D2, ADR-004, ADR-005, ADR-008, ADR-011, ADR-012 and the architecture research. Values from the open PRs #52 (resilience, #43) and #47 (identity and gateway, #46) are marked as pending those PRs; targets that do not exist yet are **[PENDING]** with their owner. Open points at the end.

### Changed
- `Docs/README.md`: the page added to the Product row.
- `architecture.md` §1: link to the NFR list.
- `2026-10-22-professor-meeting.md`: NFR row of *Presentation contents* and Tiago's NFR action ticked.

### Decided (Tiago, 24 October 2026)
- Success-criteria targets SC1, SC2, SC4, SC6 and SC11 confirmed (`product-vision.md` §5), and the report rate limit of US-48 (`features.md`).
- **SC6 raised from 10 s to 15 s**, so the 10 s polling of guide J3 fits; US-44 and US-45 (*within 15 s*) and NFR-04 updated.
- Outbox alert: oldest pending row above **60 s for 2 min** (NFR-34; `observability.md` alert table; `architecture.md` §6 in PR #52).
- Accessibility (NFR-25): **WCAG 2.1 AA** on the main pages, owner Tiago.
- Availability and recovery measured locally (NFR-12, NFR-13); weak-network mode stays out while F11 is a Could.
- NFR-15, NFR-17 and NFR-18 take their values from ADR-006 and ADR-013 (identity and gateway, merged with #47 on 24 October 2026) instead of the open PR.

### Merged a third time and aligned with the simplified plans (27 October 2026)
- `main` merged (#131 CI/CD strategy, #153 simplified local demo tools and local AI). Conflicts: this CHANGELOG (both kept, this entry first); `architecture.md` §9.1 (the local AI row and its *why* bullet from #153, plus the hosted alternative); the guide's I1–I3, K1–K2 and *Feito* (rows from `main` with the confirmations of 26 October added).
- Tiago keeps the NFRs following Tomás's plans (`non-functional-requirements.md` §5): **NFR-11** takes the local AI profile (response 20 s, one attempt, slow above 16 s, at most 5 s waiting, start only with ≥ 23 s left) instead of 10 s and 2 attempts; **NFR-20** Ollama with `qwen3:4b`, no hosted fallback; **NFR-33** only Prometheus and Grafana, correlation IDs instead of tracing; **NFR-34** three Grafana alerts, the other signals as dashboard and acceptance checks; **NFR-19** metrics 1 day or 512 MiB, no traces stored.
- `architecture.md` §9.1: the local AI profile accepted by Tiago on 27 October 2026; the hosted row no longer says *to confirm with K1*. Guide: J1 and J2 status, the J1 table (hosted AI and Keycloak rows) and the local AI profile no longer wait for K1, E1 or Guerra's J4 confirmation; D5 takes the new telemetry retention.
- Not changed: ADR-005 and ADR-012 keep *Proposed* until the team review the plans ask for; the Security and Privacy Assessment §7 still says logs 14 days and traces 7 days (Guerra).

### Aligned with Tomás's plans (26 October 2026)
- NFR-19, NFR-20, NFR-33 and NFR-34 follow the merged observability plan (#148) and AI evaluation plan (#149), which Tiago chose as the reference: traces 24 h, container logs rotated at 10 MiB × 2 with exports deleted within 14 days, metrics 7 days or 2 GiB; Tempo, Collector and Alertmanager in the stack; the alert rules of the plan §4 (create-failure alert dropped; useful triage, urgent failure, missing telemetry and stale transport added); local Ollama for now, final choice through #149. Recorded in `non-functional-requirements.md` §5 and in guide D5, I1, I3 and K1.
- Not changed: the Security and Privacy Assessment §7 still says logs 14 days and traces 7 days (Guerra).

### Merged again (26 October 2026)
- `main` merged (#58 walking skeleton, #123 API outline, #127 versioning, #129 local delivery, #148 observability plan, #149 AI evaluation plan). Conflicts: this CHANGELOG (both kept, this entry first); `observability.md` (outbox row from this PR); the guide's header, G4–G7, I1–I3, *Feito* and *Em aberto* (rows from `main` with the confirmations of 26 October added; J4 and the Keycloak J1 values no longer open).
- `local-delivery.md` is now in `main`: NFR-13 and NFR-30 link it instead of PR #130, and the PR #130 open point is removed. NFR-13 and guide G7 say, as `local-delivery.md` §6 does, that on disk loss the last dump is recovered and what came after it is lost (no RPO guaranteed). Guide D5: retention and local backup decided, backup in production still 🔮.

### Validated with the team (26 October 2026)
- Tiago went through every **[PROPOSED]** target with Tomás, Guerra and Bruno; all confirmed (`non-functional-requirements.md` §5): NFR-03 (≥ 95 % in 30 s over 1 h; **abstentions count as misses**, also on a request with no text), NFR-06, NFR-11 (AI call values, whatever model K1 picks), NFR-12, NFR-13 (rollback and restore targets), NFR-19 (help-request consent, account data, retention periods), NFR-31 (WireMock) and NFR-33 (observability stack).
- Then the items that had no value: **NFR-13** backup as in PR #130 (dump of the six databases before every demo and update, kept at most 30 days; disk-loss RPO is the last dump); **NFR-30** the procedure and smoke test of PR #130, with no time limit; **NFR-34** the six other alerts of the observability research, on the demo machine's Grafana only, no spend alert; **NFR-20** local Ollama for now (K1), so no hosted-provider review. No NFR target is left open.
- `architecture-decisions-guide.md`: D5 (retention and backup), E5, G6, G7, I1, I2, I3, J4 and K1 record the confirmation; *Feito* and *Em aberto* updated.
- `architecture.md` §9.1: the AI values are recorded as confirmed (NFR-11) with local Ollama for now (K1); if the model needs more than 10 s on the demo machine, Tomás and Tiago review them. §9.2 and the technology table (§12): WireMock confirmed for J4. The ADR-012 dashboard is no longer called *proposed* in §9.1–§9.3.
- `architecture.md` §9.1, identity provider row: Keycloak in the local Compose (E1, ADR-006) instead of *only if external* and *mocked authentication*; the key timeouts (connect 2 s, response 3 s, no retry or circuit breaker) confirmed with Guerra on 26 October 2026. The guide's open action *J1 values for Keycloak with E1* (Tiago) is removed.
- Not changed: ADR-005, ADR-012, the observability research and the Security and Privacy Assessment (its §7 still says *no backups in the MVP*) still mark these values as proposed; their owners update them.

### Merged (26 October 2026)
- `main` merged (#43 resilience, #134 cost estimation, #138 board, #141 gateway limits, #143 Security and Privacy Assessment, #145 triage coverage). Conflicts in this CHANGELOG (both kept, this entry first) and in the *Presentation contents* of `2026-10-22-professor-meeting.md` (costs and backlog rows from `main`, NFR row from this PR).
- On-time AI triage: Tomás aligned ADR-005 and the observability research at **≥ 95 % within 30 s** over a one-hour window (#145). NFR-03 now links that definition, and the open point on 90 % vs 95 % is removed.
- Resilience values (#43, PR #52 merged on 25 October 2026): checked against `architecture.md` §6, §9.1 and §10.1, all unchanged. The *(PR #52)* marks and their *otherwise [PENDING]* removed from NFR-02, NFR-04, NFR-05, NFR-07, NFR-08, NFR-10, NFR-11, NFR-17, NFR-31 and NFR-34, with links to those sections instead of the PR; the AI values of NFR-11 stay to confirm with K1 (Tomás), as in §9.1. The *Merge PR #52* open point removed.
- NFR-19 takes the **[PROPOSED]** retention periods, help-request consent and account data of the Security and Privacy Assessment (#143, §6–§8): help-request content 30 days after the event closes, logs 14 days, traces 7 days, Keycloak login events 30 days. Its open point now says they wait for the team's review.

## [Unreleased] · 2026-10-23 · CI/CD strategy (#131, Bruno Lopes)

**Why:** close guide H4–H8 in one ADR, so the walking skeleton (R0) builds the pipeline from it (assignment §8.4, §8.5; ADR-011 point 5).

### Added
- `adr/0010-cicd-strategy.md`: `conventions.yml` kept (Dependabot PRs exempt) plus one `ci.yml`. PRs run build, unit and Testcontainers integration tests, Checkstyle, ESLint, the web build and gitleaks; pushes to `main`, tags and manual runs also build the images with Compose, scan them with Trivy and run the `local-delivery.md` smoke test on the runner. Documentation-only and draft PRs skip the code jobs, and older runs of a PR are cancelled, to save the private repository's Actions minutes. Tags run the same pipeline and nothing is published (no registry, no secrets, `contents: read`). Dependabot monthly grouped updates. Required checks: **Conventions** and **CI**. R0 implements all of it; alternatives (everything on every PR, a `dev` branch, GHCR, artefacts, self-hosted runner, paid scanners) recorded.

### Changed
- `architecture-decisions-guide.md`: section H closed (H4–H8 ✅, H6 does not apply); status date; *Próximos passos* (done, Bruno's open items, walking skeleton).
- `architecture.md` §10, §12 and §13 (ADR-010 *Accepted*, linked). `ADR-011` status and point 5: CI runs the smoke test on its own runner. `Docs/README.md`: ADR-010 in the index; §6 names the two required checks.
- `definition-of-ready-and-done.md`: the "deployed to the shared environment" item now asks for a green pipeline on `main` (delivery is local).

### Not changed
- No workflow file: `ci.yml`, `dependabot.yml` and the Dependabot exemption in `conventions.yml` belong to the walking skeleton (Tomás). The real job durations are measured there.

### Aligned with `main` (26 October 2026)
- **API gateway** (ADR-013): built and tested like the services (`backend` matrix); `delivery` builds seven images and the CI smoke test also checks that the gateway answers `401` without a token.
- **Security assessment, finding 12 (T18):** ADR-010 point 7 and guide H8 say that Dependabot and Trivy answer it; Guerra marks it in the assessment.
- **R0 scope** (point 9) marked as a proposal to agree with Tomás; guide *Em aberto* item 4 says the same.

### Merged
- `main` merged (#43 resilience, #46 identity and gateway, #125 mobile app removed, #134 operating cost estimation, #138 board, #141 gateway limits, #143 security and privacy assessment). Conflicts in this CHANGELOG (both kept, this entry first), `Docs/README.md` (ADR-010 added to main's *Technical* row) and the guide (status date, H9 and *Feito*/*Em aberto* from main; H4–H8 and the pipeline item in *Feito* from #131; H6 and H7 removed from Bruno's areas).
- `main` merged again (#127 versioning, #129 local delivery, #123 API outline, #145 AI triage coverage, #58 walking-skeleton decisions). Conflicts in this CHANGELOG (both kept, this entry first), `Docs/README.md` (ADR-010 added to main's *Technical* row), `ADR-011` (status and points 2–4 and 6 from #129; point 5 from #129 with the CI sentence from #131), `architecture.md` §12 (CI/CD bullet and row from #131, versioning bullet and row from #127) and §13 (ADR-010 row from #131, ADR-011 row from #129), and the guide (H4–H6 and H8 from #131; H7 from #127 with the CI sentence from #131; *Em aberto* items 1 and 2 from main, item 4 from main plus the pipeline). `local-delivery.md`, the research overview and the cost estimation now link ADR-010 instead of calling it not written.
- `main` merged a third time (#153 simplified local demo tools). Conflict only in this CHANGELOG (both kept, this entry first).

## [Unreleased] · 2026-10-26 · Simplify local demonstration tools (#153)

### Changed
- At Tomás's request, reduce local observability to Prometheus/Grafana, Actuator health/metrics and correlated Docker logs. Grafana-managed rules replace the separate Alertmanager; Collector/Tempo and distributed tracing become optional future extensions. Assignment §6.7's correlation-or-trace requirement is mapped to concrete minimum demo evidence.
- Keep one Ollama/model and one evaluation on the existing 20/80 dataset split; repeated/cross-provider/concurrency studies and a new blinded dataset are optional extensions. Preserve label/reason review, human control and the 95%/30 s and SC5 targets.
- Remove the fixed 24 GiB machine planning assumption from demo acceptance; use and measure the existing local machine. No runtime, capacity or quality result is fabricated; team review remains required.

## [Unreleased] · 2026-10-26 · Complete walking-skeleton and local delivery decisions (#58)

### Changed
- Replaced the broad draft plan with the concrete Scenario A-style thin slice from the supplied course slides: fixed synthetic request, real persistence and displayed stored state; AI/dispatch/transport business functionality remains in later increments.
- Answered G1–G7, F2 and C1: local Compose, no cloud region/provisioning, dev/test/demo isolation, local React/RabbitMQ, six owned databases including Keycloak, tag-based delivery and consistent dump/rollback procedure. Retained the original cloud/cost evidence.
- Defined the minimum R0 cut, endpoint/data/smoke contract, resource planning budgets and recovery targets. No runtime, model or measured sizing result is fabricated; executable #58 remains open for evidence.
## [Unreleased] · 2026-10-23 · Local delivery and demo procedure (#129, Bruno Lopes)

**Why:** delivery is local only (#53). The team needed one written procedure to start, update, roll back, check and demonstrate the system before the walking skeleton is built (guide G4–G7 and H5).

### Added
- `Docs/Technical/local-delivery.md`: one `infra/compose.yaml` with `ai` and `observability` profiles; one PostgreSQL instance with five databases and five users; `infra/.env.example` versioned and `infra/.env` ignored; synthetic demo data from demo-only Flyway migrations; only `docker compose` commands (no script or Makefile) to start, update, replace one service, roll back (build the previous tag, guide H7) and reset; a manual smoke-test checklist (containers healthy, web app opens, one RabbitMQ round trip); `pg_dump` of the five databases before every demo and update; demo on a team member's laptop with a backup laptop, phones on the laptop's LAN IP over plain HTTP; D1, D2, broker and service outages shown with `docker compose stop`, always using the J4 simulator.

### Changed
- `architecture-decisions-guide.md`: G4–G7 and H5 decided (Bruno defines, Tomás applies in the walking skeleton); owners table, status date and *Próximos passos* updated. The RTO and the minimum RAM are measured with the walking skeleton.
- `ADR-011`: status and points 2 and 4–6 now state the decided procedure. One PostgreSQL instance instead of separate containers. The mobile app line is replaced by phones opening the web app (#125).
- `architecture.md` §10, §12 and §13: link the procedure and summarise it.
- `deployment.md` (G6): the five-PostgreSQL-containers suggestion is marked as not taken.
- `2026-10-22-local-deployment-clarification.md`: follow-up line. `Docs/README.md`: the procedure in the index.

### Not changed
- D5 (retention and backup) stays deferred: the dumps only cover the synthetic demo data. Running the smoke test in CI belongs to ADR-010 (Bruno, not written yet).

### Aligned with `main` (26 October 2026)
- **Gateway and Keycloak** (ADR-013, ADR-006) added to the Compose file; the `web` container no longer proxies `/api/`: the app calls the gateway and Keycloak directly. **Six databases** on the one PostgreSQL instance (Keycloak's added), dumped before every demo; one RabbitMQ user per service.
- **Ports:** only the gateway, Keycloak and the web app reach the network (ADR-013); RabbitMQ management, WireMock's admin API and Grafana are published on `127.0.0.1` only; full-disk encryption on the demo laptop (assessment §9).
- **HTTPS for the phones left open for Guerra** (assessment §9, finding 2): over plain HTTP the PKCE login fails off `localhost` and tokens are readable on the network. The mkcert and device-mode proposals are listed; until Guerra decides, the phone pages are shown in device mode on `localhost`.
- **WireMock** (#43, J2; J4 proposal) replaces the generic simulator, in front of Porto Digital and a hosted AI provider; D1 and D2 shown with its failure stubs.
- **Smoke test:** the gateway answers 401 without a token; login works (on a phone once HTTPS is decided).
- **H6 closed** (✅): with no registry there is nothing to publish and no publishing credential.
- `ADR-011` points 2 (eleven containers, ports, HTTPS open), 5 and 6; guide G4, G5, G6, G7, H6, *Feito* and Bruno's areas; `architecture.md` §10 and §12; `features.md` §8.2 no longer says every merge to `main` is deployed.

### Merged
- `main` merged (#43 resilience, #46 identity and gateway, #125 mobile app removed, #138 board, #141 gateway limits, #143 security and privacy assessment). Conflicts in this CHANGELOG (both kept, this entry first), `Docs/README.md` (procedure added to main's *Technical* row), `architecture.md` §10 (procedure from #129, gateway and Keycloak sentence from main) and the guide (status date, G3, Guerra's row and *Em aberto* from main; G4–G7, Tomás's row and the procedure in *Feito* from #129; G4–G7 removed from Tomás's areas, local delivery added to Bruno's).
- `main` merged again (#134 operating cost estimation). Conflicts in this CHANGELOG (both kept) and the guide's *Feito* (both items kept). The cost estimation's two `local-delivery.md` mentions, marked "proposed in #130", are links again.
- `main` merged a third time (#145 AI triage coverage, #124 API outline). Conflicts in this CHANGELOG (both kept, this entry first), `Docs/README.md` (local delivery and API outline both in the *Technical* row) and the guide's *Em aberto* (item 2 from main; G4–G7 out of Tomás's areas, C8 and H6 out of Bruno's).
- `main` merged a fourth time (#128 versioning). Conflicts in this CHANGELOG (both kept, this entry first), `architecture.md` §12 (local delivery sentence from #129, versioning bullet from main) and §13 (ADR-010 row from main, ADR-011 row from #129), and the guide (H5 and H6 from #129, H7 from main; *Feito* keeps both items; Bruno's areas now only D7).

## [Unreleased] · 2026-10-23 · Versioning and release tags (#127, Bruno Lopes)

**Why:** close guide H7 so the pipeline (ADR-010) and the local delivery (ADR-011) know which version a build is and how to go back to an earlier one.

### Changed
- `architecture-decisions-guide.md`: H7 ✅. One SemVer version for the monorepo, annotated tag `vX.Y.Z` on `main`, images `farol-<service>:X.Y.Z` (other builds `dev`); 0.x during the sprints (`v0.1.0` Sprint A, `v0.2.0` Sprint B, `v1.0.0` Sprint C), PATCH only to fix a release already demonstrated, tagged on `main` with no release branches; git tags only, no registry and no GitHub Releases; release notes in this CHANGELOG. *Próximos passos*: H7 moved to done.
- `architecture.md`: versioning in §10 and §12; ADR-010 row in §13 says it includes versioning.

### Not changed
- No new ADR: versioning goes into ADR-010 (CI/CD), still to be written. H6 stays open, although with no registry there are no images to publish.

### Aligned with `main` (26 October 2026)
- **API gateway** (ADR-013): versioned with the services and the web app, so seven images of ours share the version.
- **Third-party images** (Keycloak from ADR-006, PostgreSQL, RabbitMQ, WireMock from #43, the observability stack): not versioned with Farol; exact versions pinned in the Compose file (ADR-011, point 4), changed only in a PR.
- **Releases:** `v0.1.0`, `v0.2.0` and `v1.0.0` are R0, R1 and R2 of `features.md` §8.2.
- Same changes in `architecture.md` §10 and §12.

### Merged
- `main` merged (#43 resilience, #46 identity and gateway, #125 mobile app removed, #138 board, #141 gateway limits, #143 security and privacy assessment). Conflicts in this CHANGELOG (both kept, this entry first) and the guide (status date and *Em aberto* from main, H7 removed from Bruno's areas; H7 added to *Feito*).
- `main` merged again (#134 operating cost estimation, #145 AI triage coverage). Conflicts in this CHANGELOG (both kept, this entry first) and the guide's *Feito* (both items kept).
- `main` merged a third time (#124 API outline). Conflicts in this CHANGELOG (both kept, this entry first) and the guide's *Em aberto* (item 2 from main; C8 and H7 both removed from Bruno's areas).

## [Unreleased] · 2026-10-23 · API outline and event catalogue (#123, Bruno Lopes)

**Why:** Sprint A deliverable 7 (assignment §7.1.2; guide C8). The team needs an agreed outline of each service's REST endpoints and of the events between services before writing the OpenAPI contracts and the stories (guide C7).

### Added
- `Docs/Technical/api-outline.md`: conventions (resource paths under `/api/v1` with `{eventId}`, state changes as `POST /{id}/<action>`, `updatedSince` polling, Problem Details errors, per-event authorisation from local copies); endpoints of Auth & Users, Events, Reports, Transports and Badges with the role or permission and the story; request and response fields of the core flow (help request, triage, field tasks); event catalogue (envelope, one topic exchange per producer, full-state payloads with a version, 12 events with producer, consumers and why, queue bindings); coverage matrix of the Must and Should stories; Could features; §10 decisions: D1–D12 taken by the outline owner on 23 October 2026 (the FarolAdmin names the first EventAdmin with a second call, D3; no endpoint for another user's badges, D9), and O1–O7 waiting for other owners.

### Changed
- `architecture-decisions-guide.md`: C8 decided (✅, #123); status date; *Próximos passos*.
- `architecture.md`: §5 links the outline; the §13 gap now covers only the walking skeleton.
- `Docs/README.md`: outline added to the index.

### Not changed
- `domain-model.md`: `EngagementPreferences` stays in Auth & Users until the team accepts moving it to Badges (outline §6, decision O5 in §10.2).
- `features.md`: US-41 still says STCP; left to its owner (Tiago).

### Merged
- `main` merged (#43 resilience, #46 identity and gateway, #125 mobile app removed, #138 board, #141 gateway limits, #143 security and privacy assessment). Conflicts in this CHANGELOG (both kept, this entry first), `Docs/README.md` (outline added to main's *Technical* row) and the guide (status date and *Em aberto* from main, C8 removed from Bruno's areas; outline added to *Feito*). The outline is then aligned with ADR-006 and ADR-013 (next section).
- `main` merged again (#134 operating cost estimation, #145 AI triage coverage). Conflict only in this CHANGELOG (both kept, this entry first).

### Aligned with ADR-006, ADR-013 and the Security and Privacy Assessment (26 October 2026)
**Why:** guide *Em aberto* item 2 and the action for Bruno in the [meeting of 22 October 2026](Docs/Meetings/2026-10-22-identity-and-gateway-decisions.md): the outline still followed the proposals that ADR-006 and ADR-013 decided.

- **O1–O3 closed:** paths follow ADR-013's route table; sign-up, login and logout in Keycloak, `POST /accounts` removed and the account created at the first `GET /me` (US-03); the token carries only the identity, and `userId` is the token's `sub`.
- **O4 closed:** `GET /accounts?email=` follows the assessment's AC-9 (exact match, id and display name only, audited; the FarolAdmin confirms the account with the organisation outside Farol).
- **Limits and errors:** ADR-013 rate limits in §1.7; `429` after 5 help requests in 10 minutes, `413` for a map over 20 MB, `503` with `Retry-After`.
- **Event catalogue:** new `UserStatusChanged` (`Active` | `Disabled`), so every service refuses a disabled account at once; `UserDeleted` defined (`userId`, `deletedAt`, consumed by Events, Reports, Transports and Badges); `UserProfileChanged` also at account creation; `actorUserId` (the acting user's `sub`) in the envelope, for audit. Queue bindings updated; 13 events.
- **Account deletion flow** (ADR-006) in §2: refused for the last EventAdmin, Keycloak then Auth & Users, retried while Keycloak is down. What each service deletes or anonymises stays with D7 (account-deletion issue).
- **M-5 decided (Bruno):** `HelpRequestSubmitted` carries only `requestId`; the triage worker reads the request from Reports' database, so no help-request text or location sits in RabbitMQ. The assessment's M-5 and finding 3 marked decided.
- `architecture-decisions-guide.md`: C8 row and *Em aberto* item 2 (✅). `2026-10-22-identity-and-gateway-decisions.md`: Bruno's action done.
- *Not changed:* O5–O7 still wait for their owners; the assessment's other proposals (AC-7, T6) are not in the outline.

## [Unreleased] · 2026-10-26 · Align AI triage coverage and observability (#145)

### Changed
- ADR-005 and the evaluation package use **≥95% valid suggestions stored within 30 s**, replacing the earlier 90% proposal and matching observability/NFR-03 in PR #139. The operational cohort is one hour; offline expected abstentions use a separate denominator and check.
- ADR-005 defines deadline/window accounting, failure/abstention misses, semantic validity, no-data handling and the independent zero-urgent-failure / SC5 visibility checks; ADR-012 and research documents reference it.
- The research overview and decisions guide distinguish delivered research from pending model choice, independent labels, runtime measurement, instrumentation, dashboards/alerts and team acceptance. The NFR branch remains with its owner.

### Verified
- Offline scorer regression checks exercise exact 95% coverage, below-threshold coverage, the inclusive 30 s deadline and invalid semantic outputs. Original synthetic inputs, prompt/schema and baseline evidence are preserved; no model or operational compliance result is claimed.

## [Unreleased] · 2026-10-23 · Operating cost estimation (#134, Bruno Lopes)

**Why:** the professor asked for an operating cost estimation with salaries and infrastructure for the Sprint A presentation (meeting of 22 October 2026, P7), and the report must explain that cost is why the delivery is local (ADR-011).

### Added
- `Docs/Deliverables/operating-cost-estimation.md`: six roles (one person per role, `team-and-process.md` §2) at Junior or Middle salaries (Teamlyzer, 23 October 2026) plus employer social security (23.75%), from 24 September 2026 to 3 January 2027: about 58,510 € for development, with 341 € of local infrastructure (Copilot Business only, notional: the team uses Copilot free through GitHub Education). Maintenance: 1 FTE plus continuous cloud, about 3,410 € per month. Cloud scenario from #53 (prices of 22 October 2026, converted at the ECB rate of that day): about 301 € for the Sprint B and C demo and 358 € per month always on, against a one-off credit of 89 €. AI cost per request from ADR-005. One summary table for the slide.

### Changed
- `architecture-decisions-guide.md`: G2 points to the estimate, and in *Próximos passos* the cost estimation moved from open to done.
- `2026-10-22-professor-meeting.md`: the operating costs row of the presentation contents exists; P7 action ticked.
- `Docs/README.md`: new *Costs* row in the index.

### Review fixes
- Copilot priced as Copilot Business ($19 per user per month, GitHub's official plans page) instead of Pro from a third-party blog; marked as notional, real cost 0 € through GitHub Education.
- "0 € extra" locally now says "0 € beyond Copilot, which is the same in both scenarios"; A8 Porto difference 0.5%, not 1%.
- Links to `local-delivery.md` and ADR-010 marked "proposed in #130" and "proposed in #132" until those PRs are merged; the "no registry" row marked "proposed in #127" (guide H7 is still open).

### Merged
- `main` merged (#43 resilience, #46 identity and gateway, #125 mobile app removed, #138 board, #141 gateway limits, security and privacy assessment). Conflicts in this CHANGELOG (both kept, this entry first), the professor meeting record (cost row from #134, backlog row from main) and the guide (G2 and the cost item in *Feito* from #134; status date 25 October, G3, *Feito* and *Em aberto* from main, with the cost item removed from *Em aberto*).

## [Unreleased] · 2026-10-25 · Security and Privacy Assessment (António Guerra)

**Why:** Sprint A deliverable 9 (assignment §7.1.2: initial threat model, sensitive data, access-control requirements, data-minimisation measures, retention considerations) was the open guide item E5, owned by Guerra since 8 October 2026. Assignment §6.4 and §6.6 also ask for retention, deletion, audit records and consent management, and US-03 to US-05 waited for it to write the sign-up terms.

### Added
- `Docs/Technical/security-and-privacy-assessment.md`: scope and method; trust boundaries TB1–TB8; inventory of the personal data per service, with help requests as possible special-category data (GDPR art. 9); STRIDE threat model T1–T21; access-control requirements AC-1–AC-11, with who may read and change each object inside an event; data-minimisation measures M-1–M-14; proposed retention periods and what each service does on `UserDeleted`; lawful basis, help-request consent and what the sign-up terms must say; a checklist before a hosted AI provider receives real text (ADR-005, item 5); the local demo environment; 15 findings with who decides; the security tests; known limitations. Every new control and number is marked **[PROPOSED]**.

### Proposed (to review with the team)
- **T4:** grant the bootstrap `FarolAdmin` only when the token says `email_verified: true`. Without it, whoever first signs up with the bootstrap e-mail becomes FarolAdmin, because e-mails are not verified. Amends ADR-006.
- **§9:** HTTPS for the local demo (e.g. mkcert), because phones on Wi-Fi would send tokens in clear text and the browser does not allow PKCE on plain-HTTP pages.
- **T11 / M-5:** the triage message carries only `requestId`, so the help-request text and map point never sit in RabbitMQ or a dead-letter queue (change to the API outline draft, #123).
- **T6:** a within-event case in the isolation test of US-14 (a participant opening another participant's help request).
- **AC-7:** operational actions need a login in the last 12 h (`auth_time`, RFC 9470 step-up), so a lost field-team phone with a 30-day session cannot act on help requests.
- **AC-9:** answer to outline O4, the FarolAdmin's account lookup by e-mail (exact match, id and name only, audited, confirmed with the organisation outside Farol).
- **§7:** help-request content deleted 30 days after the event closes, then anonymous statistics; Keycloak events 30 days; logs 14 days; traces 7 days.
- **§8.3:** help-request consent asked when joining an event (US-12), with 112 and the first-aid points shown if it is declined.

### Changed
- `architecture-decisions-guide.md`: E5 written (✅, team review 🔍); G3 privacy points to the assessment; owners table, *Feito* and *Em aberto* item 1 updated.
- `architecture.md` §8: the *GAP* note replaced by a link to the assessment.
- `features.md` §8.5: the sign-up terms follow the assessment §8.
- `2026-10-22-identity-and-gateway-decisions.md` (*Actions*): E5 written.
- `Docs/README.md`: *Security and privacy* row.

### Not changed
- ADR-006, ADR-007, the API outline draft (#123), the domain model and the stories: the changes the assessment proposes for them are listed in its §10, with who decides each.

## [Unreleased] · 2026-10-24 · Backlog criteria for the gateway limits (#141, Tiago Teixeira)

**Why:** the team meeting of 22 October 2026 on identity and the API gateway ([record](Docs/Meetings/2026-10-22-identity-and-gateway-decisions.md), ADR-013) left Tiago two backlog actions: tell a participant what to do when the help-request limit answers 429, and confirm the 20 MB limit on the venue-map upload.

### Changed
- `features.md` §7, **US-23** (#71): new criterion: after 5 help requests in 10 minutes, the next one is refused with a message saying when the participant can ask again, and the first-aid points are shown on the map (ADR-013 L-2, 429 with `Retry-After`).
- `features.md` §7, **US-19** (#68): the **20 MB** upload limit is confirmed, with a criterion for a file that is too large (ADR-013 L-4).
- Issues #71 and #68: same criteria in their *Acceptance criteria*.
- `architecture-decisions-guide.md` (*Próximos passos*, item 3) and `2026-10-22-identity-and-gateway-decisions.md` (*Actions*): Tiago's action marked done.

## [Unreleased] · 2026-10-24 · Backlog board linked, field rules applied (#138, Tiago Teixeira)

**Why:** the GitHub Projects board exists and holds issues #58 to #121, but the documents still said it did not exist. Sprint B (2 November) must be planned from the board, so its fields need rules.

### Added
- `Docs/README.md`: *Backlog* row with the [board](https://github.com/users/tomasoliveira21/projects/1) link (private, team members only).
- `features.md` §8.3: the board link and how its fields are used, approved by Tiago and applied on the board on 24 October 2026: *Status* (*Ready* only when no dependency blocks the story, §8.4), *Priority* (Must in the next sprint's release → P0, other Must → P1, Should and Could → P2), *Size* (from the number of acceptance criteria, one step up for an open or external dependency), sprint from the milestones instead of an *Iteration* field. Result: P0 28, P1 28, P2 7; XS 16, S 22, M 16, L 9; only #58 *Ready*.

### Changed
- `definition-of-ready-and-done.md`: the ⚠️ note now says what is done on the board (fields, workflows, issues, README link) and the field values applied, and what remains (board access for the professor).
- `features.md` §7.1: DoR row *On the board* no longer says the board does not exist.
- `team-and-process.md` (*Tools*), `2026-10-12-sprint-1-presentation-feedback.md` (T1, open action ticked) and `2026-10-22-professor-meeting.md` (backlog row): the board exists, holds the issues and has its fields applied.

### On the board (approved by Tiago, 24 October 2026)
- *Priority* and *Size* set on #58 to #121 with the rules of `features.md` §8.3; #58 moved to *Ready*, the other 62 stories stay in *Backlog*.
- Sprint shown with the milestones, not an *Iteration* field; the *Sprint A* milestone is due on 1 November 2026.
- DoR box "On the GitHub Projects board" ticked in the 62 story issues (#59 to #121; #58 is a task and has no such box).
- *Ready* and *Done* options described as "Meets the DoR" and "Meets the DoD", linking to `definition-of-ready-and-done.md`.
- Old feature issues #2 to #10 and #12 to #16 archived from the board (covered by the stories or out of scope); #11 closed as delivered.

### Not changed
- The professor's access to the private board (still to do, see `definition-of-ready-and-done.md`).
- `features.md` §8.4: still lists all the blockers as open, which is true on 24 October 2026 (its F3 row still points to #44, closed, while ADR-007 waits for Tiago's review).

## [Unreleased] · 2026-10-24 · Resilience, degraded modes and load testing (#43)

**Why:** Tiago's investigation of area J (resilience and degradation) and of the retry values left open in C3. It meets assignment §6.8, applies the professor's answer (Porto Digital as the source, with a simulator under the team's control) and prepares the Sprint C performance evidence and degraded-mode demonstration (§7.3.2, items 7 and 8). It also sets the x and y asked for in the meeting with the professor on 22 October (P4). Every value is recorded with the reason for it.

### Decided
- **J1:** **Resilience4j 2.4** (`resilience4j-spring-boot4`) in the outgoing adapters; timeout, retry and circuit-breaker values for the AI provider, the Porto Digital API (import every **30 s**, the meeting's *x*; only buses within 2 km of the venue) and the identity provider (if external), each with its reason (SC3's 30 s budget, measured API latency, low traffic at night).
- **C3:** 4 attempts in the consumer with backoff 1 s → 2 s → 4 s, then one DLQ per queue; DLQ messages are alerted on, never reprocessed automatically, and moved back by hand once the cause is fixed. An AI failure is a fallback, not a message failure.
- **J2:** D1 and D2 are triggered, in the demo and in the tests, by **WireMock** placed in front of the AI provider and the Porto Digital API (proxy to the real system, failure stubs on demand), with no fault-injection code in the services. Tested on every PR (US-36 for D1 and SC5; US-42 and US-43 for D2). Freshness limit of **5 min** for bus positions (the meeting's *y*); the 24 h proposed for a static GTFS timetable no longer applies. Step-by-step demo script.
- **J3:** design peak of ≈ 1,300 read requests/s for 10 min plus 1 help request/s, with its sources and assumptions; scaling order under one instance per service (Events scaled out first when C5 changes); load test with **k6**, scenarios L1–L5 and their pass criteria, run by hand on the local machine.

### Proposed
- **J4:** **WireMock** as the simulator of the Porto Digital API (proxy, recorded real responses, failure stubs), also used for the AI provider. Guerra, owner of J4 since the meeting of 22 October, confirms or changes it.

### Changed
- `architecture-decisions-guide.md`: C3 and J1–J3 marked ✅, J4 with the WireMock proposal, a table of values per external call and the reasons for them; *Próximos passos*: x and y done, moved to *Feito*.
- `architecture.md`: §6 retries, dead letters and outbox publisher; §7 AI row; §9.1–9.3 (patterns, values and reasons; how D1 and D2 are triggered, observed and tested; demo script); §10.1 (peak load, scaling, load test, pass criteria); resilience, simulator and load testing in the technology table.
- `features.md` §3.1: D1 and D2 detection, freshness limit, recovery, demo and test.
- ADR-004 and ADR-008: retry values, import interval, freshness limit and simulator recorded; their open points on these closed, except the simulator tool (Guerra).
- `2026-10-22-professor-meeting.md`: Tiago's x and y action ticked (done in #43) and the transport decision row filled in.

### Aligned with `main` (24 October 2026)
- **Local-only delivery** (#53, ADR-011): the demo and the load test run on the local machine with `docker compose`, WireMock as one more container. The load test no longer runs from GitHub Actions against a deployed environment (a public runner cannot reach a team member's machine); k6 runs from a second computer or with CPU limits on the Farol containers, and the results describe that machine. Local scaling is Compose limits and `--scale`; managed cloud scaling stays an evaluated alternative only. "Production" wording removed.
- **One React web app** (#125): the demo uses the web app's phone pages instead of the mobile app; polling only while the page is visible.
- **Observability** (ADR-012, proposed): the dashboard, DLQ alert and degraded-mode panels point to ADR-012, with the Actuator endpoints as the stand-in until it exists.
- **Story numbers** (features §7.1, 22 October): the D1 test is US-36 (SC5) and the D2 test US-42 and US-43, instead of SC5 alone and US-15 (now *Make or remove an EventAdmin*).
- **J4 owner** (#55): WireMock recorded as a proposal for Guerra instead of a closed decision.

### Decided on 24 October 2026 (Tiago, with the NFR list #137)
- **Outbox alert:** oldest pending row above **60 s for 2 min** (`architecture.md` §6), joining the 60 s of this PR and the 2 min window of the observability research, so a short peak does not alert.
- **SC6 raised to ≤ 15 s:** participant pages keep polling every 10 s, so the J3 peak (≈ 1,300 req/s) does not change; `architecture.md` §10.1 and load-test scenario L2 updated.

### Merged
- `main` merged (#45 backlog issues, #53 local delivery and research, #55 meeting with the professor, #56 role model, #44 ADR-007, #125 mobile app removed). Conflicts in this CHANGELOG (both kept, this entry first), `features.md` §3.1 (#43 values with main's team wording and story numbers), `architecture.md` §7 (AI row from #43, story numbers from main) and the guide (C3 and J from #43, C4 and *Próximos passos* from main).

### To confirm
- AI timeouts with K1 (Tomás), identity provider values with E1 (Guerra), the WireMock proposal for J4 (Guerra), the local Compose set-up for the demo and the load test with G5 and G6 (Tomás); the busiest-night figure with the organisers.

## [Unreleased] · 2026-10-24 · Identity and API gateway decided (#46, António Guerra)

**Why:** guide C6 and E1–E4 were open. The team decided them in the meeting of 22 October 2026.

### Added

- `adr/0006-identity-and-access.md` (**Accepted**): Keycloak; tokens carry identity only; roles, teams and permissions reach the services as events; every service validates the JWT and checks the permission in its use cases; `FarolAdmin` global in Auth & Users; Authorization Code + PKCE; one RabbitMQ user per service; secrets in `.env`, GitHub Actions secrets and gitleaks. Also the Keycloak setup, where each permission is checked, and the changes Bruno makes to the API outline (#123) and D7.
- `adr/0013-api-gateway.md` (**Accepted**): Spring Cloud Gateway Server WebMvc; the route table from the API outline draft (#123); CORS only at the gateway; rate limits per account (120/min, **5 help requests per 10 min**, 20 MB map upload); the report limit stays in Reports (US-48). Numbered 013 because 011 and 012 were taken.
- `Docs/Meetings/2026-10-22-identity-and-gateway-decisions.md`: the decisions of the meeting, and actions for Bruno and Tiago.

### Changed

- Both decisions follow the decisions of 22 October: one web app, the role model with teams and permissions (`FarolAdmin`, `EventAdmin`, `RegularUser`), services independent from Sprint A, local-only delivery, and the renumbered backlog.
- Hosting answered "Azure" and recorded within the local-only scope (G1, ADR-011): local `docker compose` for the delivery, Azure Container Apps if the cloud alternative is resumed.
- Token lifetimes for one web app: 12 h idle session, 30 days with Keycloak's *Remember me* (the accepted values were for two apps).
- `architecture-decisions-guide.md`: C6, E1–E4 ✅; owners; *Próximos passos* (done; Bruno aligns the API outline, event catalogue and D7; Tiago adds the 429 criterion to US-23).
- `architecture.md`: Keycloak and the gateway in the container view; §8, §10, §12 and §13 (ADR-006 and ADR-013).
- `features.md` §8.4: E1 and the E2 part of the role-model blocker closed.
- `Docs/README.md`: ADR-006, ADR-013 and the meeting record.

### Not changed

- The API outline (#123) and the account-deletion work (D7) are Bruno's; the changes they need are listed in ADR-006.
- The draw.io diagrams do not show Keycloak yet.

## [Unreleased] · 2026-10-23 · Mobile app removed from the documentation (#125, Bruno Lopes)

**Why:** in the meeting with the professor on 22 October 2026 (P9) the React Native mobile app was dropped. Farol is one React web app, and each page is built for the phone or for a laptop.

### Changed

- `adr/0002-backend-and-frontend-technology.md`: one React web app for every user; React Native moved to *Alternatives considered*; the app-store open point removed.
- `adr/0007-venue-map-representation.md`: rewritten for the web. The image with an SVG overlay inside `react-zoom-pan-pinch`, a spike on a real iPhone and Android phone, Pointer Events as the fallback. Coordinates, PDFBox conversion and storage unchanged. Back to **Proposed** until Tiago reviews it.
- `adr/0009-architecture-documentation.md`: level 4 per container without the mobile app; F4 removed from the **[PENDING]** list.
- `architecture.md`: context and container Mermaid diagrams with one web app, §10, §12 (frontend, venue map; React Native setup row removed), §13 (ADR-007 status) and a **[TO ALIGN]** note in §14 for the diagrams.
- `architecture-decisions-guide.md`: §0, B3, F1 and F3 updated; F4 and H9 closed (no longer apply); owners and *Próximos passos*.
- `features.md`: §7.1 (split rule 3 and the issue-type rule) and the Sprint B deliverables table in §8.5 speak of phone pages and laptop pages instead of two apps.
- `product-vision.md`, `Research/architecture-investigation/README.md`: app stores and mobile setup removed.
- `2026-10-22-professor-meeting.md`: P9, the decision and the open actions.

### Not changed

- The draw.io diagrams still show the mobile app (levels 1 to 3 and `*-level-4-mobile`); Bruno updates them later.
- Earlier meeting notes and CHANGELOG entries, which record what was decided at the time; competitor analyses, where "native apps" describe other products.

## [Unreleased] · 2026-10-22 · Backlog loaded as GitHub issues (#45)

**Why:** the stories in `features.md` §7 are now GitHub issues, so the backlog can be planned and tracked (assignment §8.2, DoR item "has a GitHub issue").

### Added (on GitHub)

- Issues **#58 to #121**: the walking skeleton (`task`, milestone *Sprint A*) and US-01 to US-62 (`user story`), from the user-story form, with persona, acceptance criteria, linked dependencies, DoR and DoD. Milestones *Sprint B* (27 stories) and *Sprint C* (30); the 5 Could stories have none.
- Milestones **Sprint B** (due 6 December 2026) and **Sprint C** (due 3 January 2027); label **`user story`**.

### Changed

- `features.md`: issue numbers in §8.3; walking skeleton in Sprint A, owner Tomás (§8.2, §8.4, §8.5); transport source no longer a blocker (Porto Digital, ADR-008; PoC #48 as reference); §7.1 and the status line say the issues exist.
- `2026-10-12-sprint-1-presentation-feedback.md`: T1 done; creating the board left as an open action.
- `2026-10-22-professor-meeting.md`: backlog row points to the issues.

### Not changed

- The old feature issues #2 to #16 stay open as Sprint A feature discovery. The GitHub Projects board does not exist yet, so the README has no board link.

## [Unreleased] · 2026-10-22 · Meeting with the professor (#55, Bruno Lopes)

**Why:** record the points discussed with the professor on 22 October 2026 and what is still without an owner.

### Added

- `Docs/Meetings/2026-10-22-professor-meeting.md`: diagrams (physical level 4 and view mappings to delete, five databases in physical level 3), AI hosting (K1, Tomás), degraded modes and the transport poll (x and y), the presentation contents mapped to the existing documents, one PDF with the Sprint A documents, operating cost estimation (salaries + infrastructure), walking skeleton.

### Changed

- `architecture-decisions-guide.md`: status date, history, owners table, J4 owner (Guerra), G2 linked to the cost estimation, and *Próximos passos* rewritten as done / open with an owner / without an owner (left for the end).
- `Docs/README.md`: meeting added to the index.
- Owners assigned: cost estimation (Bruno), walking skeleton (Tomás), NFR list (Tiago), Porto Digital simulator J4 (Guerra). Left for the end, without an owner: the deck, the single PDF and the list of assumptions and dependencies.

### Removed

- `physical-view-level-4-{mobile,web,reports,transports}` and `mapping-logical-physical-level-4-{mobile,web,reports,transports}` (professor, 22 October 2026: the physical view stops at level 3). ADR-009 and `architecture.md` §14 updated. Showing the five databases in physical level 3 is still to do (Bruno).

### Merged

- `main` merged (#53: local-only deployment). Conflicts in the guide (G1–G7 from #53; G2 and *Próximos passos* link the local-only scope to the cost estimation), `architecture.md` §14 (no physical level-4 links, label from #53), `Docs/README.md` (both meetings of 22 October) and this CHANGELOG.

## [Unreleased] · 2026-10-22 · Local-only deployment authorised because of costs (#53)

### Changed

- Recorded Tomás's report that the professor authorised local-only execution because cloud deployment costs are not justified for this project; the report and presentation must explain this reason.
- Kept the complete hosting/cost evaluation as supporting evidence and a possible future alternative. Cloud provisioning, paid hosting and automatic cloud deployment are not current delivery requirements.
- Aligned the guide, architecture, ADR-011, research summaries, observability/AI proposals and process notes. Independent service containers, five service-owned databases, build/test evidence and local smoke/failure tests remain in scope.
- Merged current `main`, preserving the updated backlog, role/team model and ADR-007 alongside the investigation.

### Added

- `Docs/Meetings/2026-10-22-local-deployment-clarification.md`: source, scope and the cost justification to reuse in the final report/presentation.

## [Unreleased] · 2026-10-22 · Deployment, observability and AI research (#53)

### Added

- `Docs/Research/architecture-investigation/`: sourced investigation covering guide G1–G7, I1–I3, K1–K2, F2, RabbitMQ hosting (C1) and PostgreSQL server topology (D2), with a dated public Azure price snapshot, budget gates, recovery limitations, SLI/SLO and dashboard/alert specifications.
- Responsible AI Opportunity Assessment, proposed triage prompt/output schema, 100 synthetic unreviewed cases (20 development / 80 test), offline scorer/checks and deterministic non-AI baseline. No model/provider benchmark or deployed performance is claimed.
- Proposed ADR-005 (AI), ADR-011 (deployment) and ADR-012 (observability); ADR-010 stays reserved for Bruno's pipeline work.

### Changed

- Architecture guide, architecture index, product AI checklist and documentation index link the research. Pending team decisions stay pending; no meeting or acceptance is invented.

## [Unreleased] · 2026-10-22 · Documents aligned with the role model (#56)

**Why:** the role, team and permission model of `features.md` §4 (#45, to confirm with the team) was only in `features.md`. The other documents still used the fixed roles (`Owner`, `Admin`, `Operator`, `Responder`, `PlatformAdmin`), the four fixed response teams and, in places, the old story numbers. This closes the *Still to align* list of #45.

### Changed

- `personas.md`: a *role or job* for each persona. P4 is the **FarolAdmin** (global role). P3 is an EventAdmin, P1 a RegularUser. P2 and P5 are **jobs given by team permissions**, not roles (`HelpRequests.Triage` and `Reports.Review` for P2; `HelpRequests.Respond` and `Reports.Respond` for P5). **PE-5** answered by features §4.1 (only the FarolAdmin creates events); **PE-6** updated (the `Responder` role is dropped). Both stay open only until the team confirms the role model.
- `domain-model.md` (owners Tomás and Guerra, to review): `EventMembership` with role `EventAdmin` or `RegularUser`, at least one EventAdmin per event; `GlobalRoleGrant` holds `FarolAdmin`; `ResponseTeam` (four fixed teams) replaced by **`Team`** (created per event, with its handled help categories) and **`TeamPermission`** (fixed list, report types for `Reports.Review`). Reports are no longer tied to one responsible team: they go to the members with `Reports.Review` for their type. The draw.io export `domain-model.svg` still shows the old model and is flagged for redrawing.
- `architecture.md` (§2 context, §3, §4 responsibilities, §7, §8): FarolAdmin and EventAdmin in the context diagram; Auth & Users holds the FarolAdmin grant, memberships and team assignments; Events holds teams, their permissions and help categories; new *Where roles and permissions live* note; authorisation row points to E2.
- `architecture-decisions-guide.md` (PT, owner Guerra for E2, to review): E2 records the role model and what is still open (what the token carries, where role + teams + permissions are joined); A2, C4 and the domain-model owners row aligned.
- `user-workflows.md`: W3 *Control room handles a help request* and W4 *FarolAdmin creates an event, EventAdmin sets it up* (teams, permissions and help categories, members join before they are added to teams); actors in W2 and W5 and the W7 steps use the permissions; story numbers added; broken anchors to the old features §4 fixed. The BPMN lanes stay as personas.
- `product-vision.md`: event team and platform team rows, SC3 wording.
- `features.md`: "response team" wording in §1, F8, D1 and §5 (AI-2) replaced by the event's teams.
- `ADR-003`, `ADR-004`: local-copy examples use teams and memberships.
- Old story numbers: ADR-007 (US-06, US-07, US-08, US-09, US-17, US-18 of 16 October → US-19 to US-23, US-27, US-46, US-47), guide F3 (US-07 → US-22), `architecture.md` §7 (US-09 → US-42, US-43).

### Merged

- `main` merged into this branch (it was cut from `docs/initial-backlog`, #45): conflicts in `features.md` (§3 and D2: the Porto Digital text from `main`, the role model and new story numbers from #45) and in this CHANGELOG.

### Not changed

- Meeting notes and earlier CHANGELOG entries keep the old names and numbers: they record what was decided at the time.
- The architecture diagrams (`Docs/Technical/diagrams/*.drawio.svg`) and the BPMN files name personas (e.g. *Responder (P5)*, *platform admins*), not roles; renaming them needs draw.io and is left to the diagram owner.

## [Unreleased] · 2026-10-22 · Backlog checked against the DoR, role model, sprint plan (#45)

**Why:** teacher feedback T1 ([`2026-10-12-sprint-1-presentation-feedback.md`](Docs/Meetings/2026-10-12-sprint-1-presentation-feedback.md)): every backlog item must meet the DoR before it goes to GitHub Projects. The assignment also asks for sprint objectives and an initial release plan (§7.1.2, item 5). On 22 October Tiago (backlog owner) asked for smaller stories and defined a new role model.

### Changed

- `features.md` §4: **roles, teams and permissions** (to confirm with the team). Global `FarolAdmin`; per-event `EventAdmin` and `RegularUser`, so the same user can be EventAdmin in one event and RegularUser in another. EventAdmins create their event's teams (e.g. Security, Nursing), give each team permissions from a fixed list (`HelpRequests.Triage`, `HelpRequests.Respond`, `Reports.Review`, `Reports.Respond`, `Announcements.Publish`, `Map.Edit`, `Transport.Edit`, `Entrances.Update`) and add RegularUsers to them. Replaces the fixed roles (`Owner`, `Admin`, `Operator`, `Responder`) and the four fixed response teams of 9 October; suggested pilot set-up in §4.4. §2 (F3, F4, F7), §3 and §6.1–§6.2 aligned.
- `features.md` §7: every user story checked against the DoR (persona, dependencies, testable criteria, AI fallback), then **split into 62 smaller stories, renumbered US-01 to US-62**, each one action and one PR. Old-to-new table, split rules, new numbers, rewritten criteria and issue types in §7.1.
- `.github/ISSUE_TEMPLATE/user-story.yml`: persona P5 (responder) added, P4 named FarolAdmin; several personas can be picked.
- `user-workflows.md` W4: EventAdmin instead of Owner, US-06 instead of US-25.
- `2026-10-12-sprint-1-presentation-feedback.md`: T1 DoR check done; issue creation left open.

### Proposed (to confirm with the team)

- `features.md` §8: sprint goals for Sprints B and C, initial release plan (R0 walking skeleton, R1 vertical slice with 27 stories, R2 MVP with 28), the Sprint C cut line, the story-to-issue table and the dependencies that still block *Ready*. §8.5 explains the Sprint B goal (mapped to every §7.2.2 deliverable), capacity and risks, the placement of the stories and the Sprint C cut order. Replaces the backlog GAP note.

### Still to align (other documents)

- The new role model is not yet in `personas.md` (PE-5, PE-6), `domain-model.md`, `architecture.md` or the architecture decisions guide (E2).

## [Unreleased] · 2026-10-22 · Venue map library and ADR-007 (#44)

**Why:** decisions guide F3 (venue map library, owner Tiago) was the last open item for the map (F5). The app is mainly mobile, the map is an uploaded image with no geo data (D1), and F4 (Expo or bare React Native) is still open, so the choice had to work on iOS and Android, with Expo and with bare React Native.

### Decided

- Mobile venue map: **`react-native-svg`** markers inside a **`react-native-zoom-toolkit`** zoom/pan container (Gesture Handler + Reanimated), normalised `MapPosition` coordinates, filtering by type with React state. Works on iOS and Android, with Expo and bare React Native.
- A **spike on real iOS and Android devices** checks `react-native-zoom-toolkit` with Gesture Handler 3 before the map is built; if it fails, pan and zoom are written directly with Gesture Handler and Reanimated.
- PDF maps (US-06) are **rendered to a PNG on upload** by the Events service with Apache PDFBox: single-page PDFs only, at most 4096 px on the long side; the original file and the rendered image are both kept.

### Added

- `Docs/Technical/adr/0007-venue-map-representation.md`: ADR-007 (Nygard format + *Alternatives considered*: WebView + Leaflet `CRS.Simple`, image-zoom components, `react-native-zoomable-view`, `react-native-maps`, Skia, PDF conversion on the device).

### Changed

- `architecture-decisions-guide.md`: F3 marked ✅ with the decision.
- `architecture.md`: §12 venue map row filled in; §13 ADR-007 marked *Accepted* and linked.
- `Docs/README.md`: ADR-007 added to the index.

## [Unreleased] · 2026-10-21 · Architecture diagrams: level 4 split and view mappings (#39, Bruno Lopes)

**Why:** review of the 4+1 diagrams. Legends added noise, separate personas at the top levels added nothing, and each level-4 diagram mixed several containers in one picture.

### Added

- Level 4 split into one diagram per container, in the logical, implementation and physical views: `*-level-4-mobile`, `-web`, `-reports` and `-transports`. The logical ones show the container's neighbours (personas, API gateway, database, RabbitMQ, AI provider or Porto Digital); the implementation ones show only what is inside. Transports gets its own components (bus import job, Porto Digital client) for the first time.
- 14 view-mapping diagrams: `mapping-logical-implementation-level-{1,2,3,4-*}` and `mapping-logical-physical-level-{1,2,3,4-*}`. Each one copies the full logical diagram on the left and the full implementation or physical diagram of the same level on the right, with dashed arrows linking each logical element to its counterpart. External systems have no counterpart in the code.

### Changed

- Legends removed from every diagram.
- Logical levels 1 and 2: the five personas become one **User**.
- Physical level 1 shows only Farol, the AI provider and Porto Digital. Levels 2 and 3 show a **Mobile app** and a **Web app** node instead of a smartphone, a computer and static hosting.

### Removed

- `logical-view-level-4`, `implementation-view-level-4` and `physical-view-level-4` (replaced by the per-container diagrams).

### Added (documentation decision)

- `Docs/Technical/adr/0009-architecture-documentation.md`: C4 inside the 4+1 views without scenarios, four levels per view with one level-4 diagram per container, view mappings, draw.io `.drawio.svg` files (source and image in one), no legends.
- `architecture.md` §14 links every diagram; §13 marks ADR-009 as accepted. `Docs/README.md` links the ADRs and the diagrams.

## [Unreleased] · 2026-10-21 · Professor's answer: ADR-001 and ADR-008 (#39, Bruno Lopes)

**Why:** the professor answered the email of 15 October. The Porto Digital urban platform (FIWARE NGSI v2 bus API) is accepted as the external integration, as long as the app handles unavailable, late and invalid data and the team keeps a simulator or recorded responses under its control. There is no imposed path: the architecture is defined from the start and any evolution is motivated by evidence, not by the calendar; Sprint B already requires two independently deployable backend components (§6.2). This closes guide A4 and the transport source (A2, B2).

### Decided

- **Microservices from Sprint A**; no modular-monolith phase. The ArchUnit test and the schema-per-module rule of that phase are dropped (guide A3, D2).
- Transport source: **Porto Digital urban platform**; STCP GTFS and the weather plan B are dropped.
- **Simulator of the Porto Digital API, or recorded real responses** (guide J4): for automated tests, the degraded-mode demo and presentation day. Form and tool still open, no owner.

### Added

- `Docs/Technical/adr/0001-architectural-style.md` and `0008-transport-data-source.md`.

### Changed

- `architecture-decisions-guide.md`: A2, A3, A4, B2 and D2 updated; *À espera do professor* becomes *Respostas do professor*.
- `architecture.md` (§2, §3, §7, §11, §13), `features.md` (F9, §3, degraded mode D2), ADR-003, `README.md` and the meeting record of 8 October updated to match.
- Diagrams (logical levels 1–3, physical levels 1–3, transport process views): the transport source is named Porto Digital (FIWARE NGSI v2), and the monolith note is replaced by "Each service is deployed on its own from Sprint A".

## [Unreleased] · 2026-10-21 · Professor's answer: transport source and microservices from the start (#38, Bruno Lopes)

**Why:** the professor answered the email of 15 October, and the team decided A4 from that answer. Summary in [`Docs/Meetings/2026-10-08-sprint-1-gap-review.md`](Docs/Meetings/2026-10-08-sprint-1-gap-review.md) (*Professor's answer and follow-up decisions, 21 October 2026*).

### Decided

- Transport source: **Porto Digital API** (FIWARE NGSI v2). The app must handle unavailability, delays and invalid data; an **API simulator or recorded responses** under our control is required (new guide item J4, no owner yet).
- **Microservices from the start** (A4): the five services deploy independently from Sprint A; the modular-monolith phase is dropped. Any later evolution needs evidence, not the calendar.

### Changed

- `architecture-decisions-guide.md`: A2, A3, A4, B2, D2, new J4, *Resposta do professor* replaces *À espera do professor*, *Próximos passos*.
- `architecture.md`: status, §2 context diagram, §3, §7, §9, §11, ADR list (ADR-001 and ADR-008 ready to write).
- ADR-002: services with their own deployment from Sprint A; hosting cost from the start.
- ADR-003: monolith-phase schema option removed; one database per service from Sprint A.
- ADR-004: context and consequences for independent deployment; new decision 5, external sources read by a scheduled poll with a configurable base URL for the simulator.

## [Unreleased] · 2026-10-16 · ADR-002, ADR-003 and ADR-004 (#38, Bruno Lopes)

**Why:** the technology, data and communication decisions confirmed on 11 and 15 October lived only in the decisions guide. The assignment asks for them as Architecture Decision Records (§7.1.2, item 8).

### Added

- `Docs/Technical/adr/` in the Michael Nygard format plus *Alternatives considered* (guide B7):
  - `0002-backend-and-frontend-technology.md`: Java 21, Spring Boot 4.x, Maven; React web app; React Native mobile app. Reason: the stacks the team already uses.
  - `0003-database-and-data-ownership.md`: PostgreSQL only, one database per service, `event_id` filtering with an isolation test per service, Flyway with *expand and contract*.
  - `0004-service-communication.md`: RabbitMQ with Spring AMQP, events only between services, outbox table, idempotent consumers, dead-letter queue, contract-first OpenAPI under `/api/v1`, polling.
- Each ADR cites the guide ids it records and lists its open points with their owner. Alternatives come from the options in the old `architecture.md` §12; reviewers should confirm the reasons given.

### Changed

- `architecture.md` §13: ADR-002, 003 and 004 marked *Accepted* and linked. `Docs/README.md`: ADRs added to the index.

## [Unreleased] · 2026-10-16 · Move README into Docs

### Changed

- Moved the repository README to `Docs/README.md` and adjusted its relative links.
- Updated README paths in the project instructions, process documentation, meeting notes and changelog.

## [Unreleased] · 2026-10-15 · Architecture decisions confirmed by the team (#35)

**Why:** the team meeting of 15 October confirmed the proposals below and split the remaining investigations. Summary in [`Docs/Meetings/2026-10-08-sprint-1-gap-review.md`](Docs/Meetings/2026-10-08-sprint-1-gap-review.md) (*Follow-up decisions, 15 October 2026, team meeting*).

### Decided

- Proposals of areas A, B, C, D and H are team decisions, except **A4** (evolution between sprints) and the **transport data source**, which wait for the professor.
- Diagrams in **draw.io**; **polling** for the MVP; cache, retention and backup are **future improvements**.
- Frontend: **React web app** for event management only, **React Native mobile app** for the rest.
- Deployment platform **reopened** (Azure, ECS or Vercel; Tomás investigates).

### Changed

- `architecture-decisions-guide.md`: rewritten as **Decisões Arquiteturais**: background, options and practice questions removed; only decisions, their status and owners; decisions credited to the team; *Próximos passos* added.
- `Docs/README.md`: index entry updated; issue title format `[<Type>] <Title>` (§1), also in `team-and-process.md` §4.
- `architecture.md`: rewritten to match the team decisions: five services, React web + React Native mobile, RabbitMQ with outbox, events only between services, polling, PostgreSQL per service, style and evolution (§11), technology table and ADR list with status. Open items marked **[PENDING]** with their owner.
- Owners assigned to the open items (gateway, Security & Privacy assessment, map library, mobile setup, account deletion, API outline, Responsible AI). Walking skeleton moved to a later phase (no owner yet); Bruno writes the ADRs that are ready (002, 003, 004, 009).

## [Unreleased] · 2026-10-15 · Architecture decisions proposed and SDD rules (Bruno Lopes)

**Why:** the architecture questions in the decisions guide blocked the walking skeleton. Bruno answered areas A, B, C, D and H; Tiago validates them and decides the rest. Summary in [`Docs/Meetings/2026-10-08-sprint-1-gap-review.md`](Docs/Meetings/2026-10-08-sprint-1-gap-review.md) (*Follow-up, 15 October 2026*).

### Proposed (to validate with Tiago)

- Microservices as the final state (Auth & Users, Events, Reports, Transports, Badges), built as a modular monolith in Sprints A–B and split in Sprint C.
- RabbitMQ with Spring AMQP, outbox table, events only between services, contract-first OpenAPI; PostgreSQL with one database per service, `event_id` filtering, Flyway.
- GitHub Actions in the current monorepo, Maven, Java 21, Spring Boot 4.x, multi-stage Dockerfiles, continuous deployment.

### Added

- `Docs/Process/spec-driven-development.md`: Spec-Driven Development rules and spec template (proposed).

### Changed

- `architecture-decisions-guide.md`: **Decisão** lines filled; items left to Tiago marked.
- `Docs/README.md`: SDD rules added to the documentation index.

## [Unreleased] · 2026-10-15 · Consolidated domain model and Porto Digital bus source

**Why:** consolidate current product decisions, consider the architecture-decisions-guide branch without merging it, and represent the project owner's selected bus-context API.

### Changed

- `Docs/Technical/domain-model.md`: one consolidated conceptual class diagram covering accounts, event permissions, reports, field dispatch, badges, check-in and transport; a high-level entity/responsibility table and a short notation guide below it. The introduction names the services without tying the document to a branch.
- Domain ownership follows the guide's proposed Auth & Users, Events, Reports, Transports and Badges split, preserving its pending-validation status.
- Porto Digital FIWARE appears as an external source in the same diagram, with bus observations and geographic coordinates separate from venue-map positions. The endpoint does not supply schedules/ETAs.

### Added

- `Docs/Meetings/2026-10-15-domain-model-transport-source.md`: project-owner source choice and technical review notes, without claiming a team meeting or approval of the architecture proposals.

## [Unreleased] · 2026-10-13 · Presentation feedback: BPMN journeys, report handling, no 112 (Bruno Lopes)

**Why:** after the first presentation on 12 October 2026, the teacher asked for BPMN diagrams of the main journeys, for no mention of 112, and, as the only feedback on the talk itself, for a clearer explanation of who receives the reports and how they are handled. The feedback is recorded in [`2026-10-12-sprint-1-presentation-feedback.md`](Docs/Meetings/2026-10-12-sprint-1-presentation-feedback.md).

### Added

- `Docs/Meetings/2026-10-12-sprint-1-presentation-feedback.md`: the six feedback items (T1–T6), the decisions and the open actions.
- `Docs/Product/bpmn/`: two BPMN 2.0 diagrams with one lane per actor (participant, Farol, operator, responder), each as `.bpmn` (editable in bpmn.io or Camunda Modeler) and `.svg`:
  - `help-request-journey`: W2 + W3 end to end. The AI triage runs in parallel and never holds the request back; the operator dispatches it or handles it remotely; the responder accepts it, goes to the place and resolves it.
  - `report-journey`: W7 end to end. The report goes to the team responsible for its type; the operator confirms or rejects it, if needed after a responder checks it on site; a responder fixes it when action is needed; otherwise it expires.
- `user-workflows.md`: section *Complete journeys (BPMN)* with both diagrams.

### Proposed (to confirm with the group, PE-6)

- `personas.md`: the incident operator is split into **P2 · Miguel, the control-room operator** (decides) and new **P5 · Inês, the responder** (in the field, acts).
- `features.md`: new §4.1 *Who receives what, and who acts*. New role `Responder` (F4) and story **US-26**. F7 and F13 list P5. US-05, US-10 and US-19 updated. §6.2 says who confirms a report, adds the on-site check and the *Resolved* status, and lists the report statuses.
- `user-workflows.md`: W2 sequence and W3 lifecycle with the responder (new status `OnTheWay`); W7 flow with the operator, the on-site check and the fix.
- `domain-model.md`: role `Responder`; `HelpRequest.acceptedBy`. `architecture.md` §2 and `product-vision.md` §3: responder added.

### Removed

- Every mention of **112**, as the teacher asked: `features.md` (F6, §6.1, US-08), `user-workflows.md` (W2, W6, W7), `personas.md` (P1, P2), `architecture.md` (system context, degraded modes), the report (§5: the app must make clear it is not an emergency service) and both market analyses (the 112 row and the "Call 112 first" items).

### Not changed on purpose

- `Docs/Presentation/Farol_First_Presentation.pptx`: it stays as presented. The BPMN diagrams are not for slides (too detailed to read on a projector).

## [Unreleased] · 2026-10-12 · Status slide, English docs and process setup (Bruno Lopes)

**Why:** the teacher asked by email for the state of four topics: DoR/DoD, team roles and work process, domain model and high-level architecture. Preparing that status showed what still kept the process topics from green: the DoR and DoD were not in the tool, the conventions kept drifting, and the language rule was undecided. The team decided on 12 October 2026 that **all documentation is in English and stays that way**.

### Added

- `Docs/Presentation/Farol_First_Presentation.pptx`: section *Status overview*, at the end, with one slide, "Process and technical status". One card per topic with a green/yellow/red status, what is done and what comes next; speaker notes in Portuguese (~45 s).
  - 🟢 DoR / DoD: agreed on 8 October and now in the issue forms and the PR template.
  - 🟡 Team roles and process: roles, GitHub Flow, sprints and meetings defined; the board, branch protection and squash-only merging are still to turn on.
  - 🟡 Domain model: DM-1 decided; DM-2 to DM-5 open; Report, Badge and PlatformAdmin not modelled.
  - 🟡 Architecture: work has started (views and the triage flow exist), but services vs modular monolith, technology stack, ADRs, API outline and walking skeleton are not decided yet.
- Deck review: the personas slide shows Sofia's success as "event configured and published"; the main-features slide no longer mentions 112 (the "Ask for help" item and its speaker notes).
- `.github/ISSUE_TEMPLATE/user-story.yml`: issue form with the DoR as required fields plus a checklist, and the story-level DoD pre-filled as a checklist.
- `.github/ISSUE_TEMPLATE/task.yml` (work that is not a story) and `config.yml` (blank issues disabled).
- `.github/workflows/conventions.yml`: the **Conventions** check fails a PR whose branch name, title or `Closes #133` line does not follow README §2 and §4.

### Changed

- **Translated to English:** the Problem and Opportunity Report, the Market and Competitor Analysis and its product-specific annex. Content unchanged.
- `Docs/Research/inquerito-estudantes-google-forms.gs`: comments in English; the survey questions stay in Portuguese, for Portuguese students.
- Quotes from the report in `features.md`, `product-vision.md`, `user-workflows.md`, `domain-model.md` and `architecture.md` now use the English text and section names.
- `Docs/README.md`: language rule recorded, with two exceptions (survey questions, speaker notes); documentation index marks every document as EN; branch protection includes the Conventions check.
- `team-and-process.md`: language decision recorded; process health check updated (merge commits up to PR #30, the `feat/` branch, the new check and issue forms).
- `definition-of-ready-and-done.md`: the "Open items" gap is replaced by *In the tool*, which says where each rule lives and what is left to set up on the board.

## [Unreleased] · 2026-10-11 · Technology decisions and architecture decisions guide (Bruno Lopes)

**Why:** the team chose the main technologies, so the next step is preparing the high-level architecture and its ADRs. Decisions listed in [`Docs/Meetings/2026-10-08-sprint-1-gap-review.md`](Docs/Meetings/2026-10-08-sprint-1-gap-review.md) (*Follow-up decisions, 11 October 2026*).

### Decided

- Backend **Spring Boot**, frontend **React**, deployment on **Azure** (Azure for Students; Azure details owned by Tiago).
- Architectural style: **microservices or modular monolith**, chosen after a documented evaluation (ADR-001); a monolith without enforced boundaries is ruled out.
- Architecture views follow the **C4 model**, all four levels. A full **CI/CD** pipeline is in scope.

### Added

- `Docs/Technical/architecture-decisions-guide.md` (Portuguese, working document): background (C4, microservices vs modular monolith, messaging, data, Kubernetes and Azure Container Apps, Azure for Students, Spring, React, CI/CD, observability, identity), open questions with options per area (A–K), and practice questions for the individual evaluation (assignment §10).

### Changed

- `architecture.md`: status line, §10 (deployment target gap closed), §11 and §12 updated with the decisions above.
- `Docs/README.md`: guide added to the documentation index.

## [Unreleased] · 2026-10-11 · User workflows slide (António Guerra)

**Why:** the workflows owner prepared the topic's slide for the presentation of 12 October 2026 (presentation plan, meeting §9). The talk lasts at most **6 minutes**, and the deck already takes about 3 min 05 s by its speaker-note timings, before the problem-and-vision slide is added. So the topic gets **one slide of about 60 s**.

### Added

- `Docs/Presentation/Farol_First_Presentation.pptx`: section *User workflows (António Guerra)*, after *Main features*, as in the presentation plan. It has one slide, "Help for people, reports for places".
  - Two four-step rows: W2 + W3 (ask for help: 112 first, AI suggestion, the operator routes, the participant follows the status) and W7 (report a problem: the responsible team confirms it, everyone sees it until it expires, it counts toward badges), as listed in the meeting plan.
  - Speaker notes in Portuguese (~60 s).
  - Chosen over a W2 + W3-only version (swimlanes plus the request statuses), because this one also covers the engagement journey.

### Not changed on purpose

- `user-workflows.md`: the journeys on the slide (W2, W3, W7) are already described there.

## [Unreleased] · 2026-10-10 · Personas slide and platform admin (Tiago Teixeira, #26)

**Why:** the personas owner prepared the topic's slide for the presentation of 12 October 2026 (presentation plan, meeting §9). While doing it, a gap showed up: Farol hosts many festivals (Queima do Porto, Braga, Coimbra, Lisboa, …) and each organisation manages only its own event, but nobody was defined to create events, name their Owner, or step in when the Owner leaves (board terms are annual). The `Owner`/`Admin`/`Operator` roles exist only inside an event.

> ⚠️ **To confirm with the group.** Everything marked **[PROPOSED]** below changes topics owned by other members (features: Bruno; workflows: António; vision: Tomás). Nothing here is agreed yet. If the group rejects P4, revert the items listed under *Proposed* and remove the fourth card from the slide.

### Added

- `Docs/Presentation/Farol_First_Presentation.pptx`: personas slide "Who we design for" after the cover. Four cards (Rita, Miguel, Sofia, Daniel), each with goal, pain and success, taken from `personas.md`. A band states that they are proto-personas and names what validates each one: student survey (Rita), security-volunteer interview (Miguel), organiser conversation (Sofia). Speaker notes in Portuguese (~70 s).

### Proposed (to confirm with the group)

- `personas.md`: new persona **P4 · Daniel, the platform admin**, an internal user from the Farol team. He creates events for verified organisations, names their first Owner, transfers the Owner role and suspends events. He **never sees** an event's help requests, reports or participants. Sofia (P3) now states that she manages only her own event. New open item **PE-5**.
- `features.md`:
  - F3: only the platform admin creates events; the Owner edits, publishes and closes them.
  - F4: new platform-wide role `PlatformAdmin`, outside every event.
  - New story **US-25** (platform admin creates an event and names its Owner). US-03 no longer includes "create". Stories are not renumbered.
- `user-workflows.md` W4: starts with the organisation's request, verification and creation by the platform admin; persona column is now P4 → P3.
- `product-vision.md` §3: new row *Platform team (internal)*.
- **Decision for the group:** events created by the platform admin only (proposed for the MVP, simpler and blocks fake events) or requested by organisations and approved by him.

### Changed

- `Docs/Presentation/Farol_First_Presentation.pptx`: deck sections are now *Cover*, *Personas* and *Main features*. The problem-and-vision slide (Tomás) still goes between the cover and the personas slide.

### Not changed on purpose

- `domain-model.md`: the `PlatformAdmin` role (global, outside `Membership`) is not modelled yet. The domain model is evaluated after the presentation (decision of 8 October); add it then if P4 is accepted.

## [Unreleased] · 2026-10-09 · Main features review (Bruno Lopes)

**Why:** the main-features owner closed the open items of the topic before the presentation of 12 October 2026. Decisions listed in [`Docs/Meetings/2026-10-08-sprint-1-gap-review.md`](Docs/Meetings/2026-10-08-sprint-1-gap-review.md) (*Follow-up decisions*).

### Added

- `Docs/Presentation/Farol_First_Presentation.pptx`: cover (name + team) and two main-features slides, speaker notes in Portuguese.
- `features.md`: login/logout, personal account management, event management, event role and response-team management, report confirmation, event check-in, friends (Could), chat (Won't); response teams (§4); degraded-mode scenarios D1 and D2 (§3.1); AI items still to define (§5.2); badge tracks and tiers (§6.3).
- `user-workflows.md` W8: check-in at the venue.
- `domain-model.md`: `UserAccount` entity.

### Changed

- Product name: **Farol**.
- Login is mandatory for every user (DM-1 → account): `features.md`, `user-workflows.md`, `personas.md`, `product-vision.md`, `architecture.md`, `domain-model.md`, report and market analysis updated.
- `features.md`: feature list renumbered F1–F20; F11 (offline mode) moved to Could; user stories rewritten as US-01 to US-24 (to validate).
- `product-vision.md`: vision statement no longer promises weak-network operation (F11 is a Could).

### Removed

- The anonymous participant web app feature (former F2).
- The separate project-context file and all references to it; `CLAUDE.md` now points to the README.

## [Unreleased] · 2026-10-08 · Team meeting decisions

**Why:** the team reviewed the gap analysis on 8 October 2026 and took the decisions listed in [`Docs/Meetings/2026-10-08-sprint-1-gap-review.md`](Docs/Meetings/2026-10-08-sprint-1-gap-review.md). This change applies them.

### Added

- `Docs/Meetings/2026-10-08-sprint-1-gap-review.md`: the meeting guide, with the decisions taken.
- `features.md` §5: community reports (Waze-style) and badges, with the five answers of assignment §5.5; stories US-12 to US-14.
- `user-workflows.md` W7: report a problem and earn a badge.
- `product-vision.md` §1: product name candidates.

### Changed

- `team-and-process.md`:
  - roles are described, not assigned;
  - António Guerra added to the team;
  - GitHub Projects chosen as the board;
  - sprints renamed A/B/C;
  - two meetings a week;
  - process-drift actions agreed.
- `definition-of-ready-and-done.md`:
  - removed the estimate, UI sketch and privacy items from the DoR;
  - DoD Level 3 removed;
  - new rule: one story = one issue = one PR.
- `features.md`:
  - MoSCoW agreed;
  - AI workflow = help-request triage;
  - external integration = STCP GTFS if public, otherwise a weather API.
- `product-vision.md`:
  - statement and pitch agreed;
  - success criteria rewritten as SC1–SC11, so they prove the product works (e.g. AI triage ≤ 30 s);
  - survey metrics moved to "validation of the problem".
- `personas.md`: success references updated to the new criteria.

### Not changed on purpose

- `domain-model.md` and `architecture.md`: they are evaluated after the Sprint A presentation. They will need Report/Badge entities and the integration decision.

## 2026-10-08 · Sprint 1 presentation artifacts

**Branch:** `docs/sprint-1-presentation-artifacts` (based on `main` at `e853854`)
**Why:** a gap analysis against the eight topics of the Sprint 1 presentation (12 October 2026) found that the product side was largely covered, while the process and technical artifacts were missing or incomplete. This change fills those gaps **using only information already in the repository** (the three deliverables, the survey script and the assignment PDF) and flags in each document what is still missing.
**Prepared with AI assistance** (Claude, Anthropic): the AI read the repository, drafted the documents and marked every inferred item as **[PROPOSED]**. Nothing in these documents is user-research evidence. All content needs review and agreement by the team before it is presented as the team's position.

### Status of the eight presentation topics

| # | Topic | Before | After (pending team agreement) | What still blocks a Green |
| --- | --- | --- | --- | --- |
| 1 | Product vision | 🟢 | 🟢 | Product name; baselines for the success criteria (survey, interview) |
| 2 | Personas | 🟡 | 🟡 | Proto-personas only: they need the survey results and the interview to be validated |
| 3 | Main features | 🟡 | 🟡 | AI workflow and engagement mechanism decisions; backlog in Jira |
| 4 | Workflows | 🟡 | 🟢 | Help-request statuses to confirm in the interview |
| 5 | DoR / DoD | 🔴 | 🟡 | Team agreement; set up in the project tool |
| 6 | Team roles and work process | 🟡 | 🟡 | Roles must be assigned by the team; Jira link; sprint dates |
| 7 | Domain model | 🔴 | 🟡 | Open questions DM-1 to DM-5 |
| 8 | High-level architecture | 🔴 | 🟡 | Technology choices, ADRs, target environment |

### Added

| File | Content | Topic |
| --- | --- | --- |
| [`Docs/Product/product-vision.md`](Docs/Product/product-vision.md) | Vision statement and one-line pitch **[PROPOSED]**; problem summary; users; value proposition; **measurable success criteria S1–S9** with metric, method, proposed target and baseline; MVP boundary; key risks; research status. Also defines the **legend** used by all new documents (**[PROPOSED]**, **[DECISION NEEDED]**, ⚠️ **GAP**) | 1 |
| [`Docs/Product/personas.md`](Docs/Product/personas.md) | Three **proto-personas**: P1 Rita (participant), P2 Miguel (incident operator), P3 Sofia (event admin). Splits the report's "event team" into operator and admin because their journeys and permissions differ. Every attribute is sourced or marked as a hypothesis linked to a survey question | 2 |
| [`Docs/Product/features.md`](Docs/Product/features.md) | Mapping root cause → feature; features F1–F12 (from components C1–C11) with MoSCoW and build order; traceability to the assignment's mandatory capabilities (§5.5, §5.6, §6.3, §6.5, §6.6, §6.8); **AI workflow options A (triage) and B (assistant)** with a recommendation for A; **ethical engagement options E1–E3** with a recommendation for E1 and draft answers to the five questions of §5.5; **11 draft user stories (US-01 to US-11)** with Given/When/Then acceptance criteria | 3 |
| [`Docs/Product/user-workflows.md`](Docs/Product/user-workflows.md) | Six journeys with Mermaid diagrams: W1 find information, **W2 ask for help (core journey, sequence diagram)**, **W3 handle a request (help-request lifecycle state diagram)**, W4 set up an event, W5 publish a live update, W6 degraded mode (network loss). Indicates which journeys to use in a 5–7 minute presentation | 4 |
| [`Docs/Process/definition-of-ready-and-done.md`](Docs/Process/definition-of-ready-and-done.md) | Definition of Ready (9 items); Definition of Done on three levels: PR (kept from README §7), user story, sprint increment | 5 |
| [`Docs/Process/team-and-process.md`](Docs/Process/team-and-process.md) | Team members found in the commit history; **six proposed roles** (PO, Scrum Master, tech lead, QA lead, DevOps lead, AI lead) with the *Member* column left for the team; summary of the README process; sprints and ceremonies; tools; **process health check** based on the git history | 6 |
| [`.github/pull_request_template.md`](.github/pull_request_template.md) | The PR template from README §7, which the README said to save but did not exist. GitHub will now pre-fill new PRs with it | 5, 6 |
| [`Docs/Technical/domain-model.md`](Docs/Technical/domain-model.md) | Two bounded contexts (Event Configuration, Incident Management); **Mermaid class diagram**; 11 entities and value objects, each traced to its source in the repository; invariants I1–I7; data lifecycle and privacy; open questions DM-1 to DM-5 | 7 |
| [`Docs/Technical/architecture.md`](Docs/Technical/architecture.md) | Architecture drivers D1–D9 traced to the repository and the assignment; **system context and container diagrams**; responsibilities and data ownership; sync/async communication; the **asynchronous triage workflow** with retries, idempotency, fallback and outbox; external integrations; security and authorisation; degraded modes; deployment view; services vs modular monolith trade-off; technology decisions (no technology chosen); list of ADRs to write | 8 |
| [`CHANGELOG.md`](CHANGELOG.md) | This file | — |

### Changed

| File | Change | Why |
| --- | --- | --- |
| [`Docs/README.md`](Docs/README.md) | Added a short project description and a **Documentation** index (with the language of each document) at the top | The README had no link to any document |
| [`Docs/README.md`](Docs/README.md) §7 | "Save as `.github/pull_request_template.md`" → "Saved as [link]" | The file now exists |

### Fixed

| File | Fix |
| --- | --- |
| [`Docs/Deliverables/market-and-competitor-analysis.md`](Docs/Deliverables/market-and-competitor-analysis.md) (line 4) | Reference to `02a-Anexo-Mercado-Especifico-do-Produto.md`, a file that does not exist, replaced with a link to `market-and-competitor-analysis-product-specific.md` |
| [`Docs/Deliverables/market-and-competitor-analysis-product-specific.md`](Docs/Deliverables/market-and-competitor-analysis-product-specific.md) (line 4) | Reference to `02-Analise-Mercado-e-Concorrencia.md`, a file that does not exist, replaced with a link to `market-and-competitor-analysis.md` |

No other content in the existing deliverables was changed.

### Not changed on purpose

- **Language of the existing deliverables.** The README requires English, but the three deliverables are in Portuguese. The new documents are in English, following the README. Translating the deliverables, or changing the rule, is a team decision (see *Decisions needed*).
- **"Sprint A" vs "Sprint 1".** The two market documents say "Sprint A"; the report and the assignment say "Sprint 1". Left as is because it may be intentional; the team should align it.
- **The report's "Visão de produto" section** was not rewritten. The new product documents summarise it and link back to it, so the report stays the source of truth for the problem.

---

### Open gaps flagged in the documents

Each item is marked ⚠️ **GAP** in the document listed, with what is needed to close it.

| # | Gap | Where it is flagged | Needed |
| --- | --- | --- | --- |
| G1 | User research findings are all "TBD" (results, limitations, product decisions) | `product-vision.md` §8 | Survey results (even preliminary, with *n*) and interview notes |
| G2 | No baselines for success criteria S1, S3, S5, S9 | `product-vision.md` §5 | Same as G1 |
| G3 | Success-criteria targets are proposals | `product-vision.md` §5 | Team confirms or changes them |
| G4 | The product has no name | `product-vision.md` §1 | A working name |
| G5 | Persona P2 (operator) is almost entirely hypothesis | `personas.md` | Interview with the security volunteer |
| G6 | Persona P3 (admin) and assumption A1 not validated | `personas.md` | One conversation with an event organiser |
| G7 | Feature priorities are proposals | `features.md` §2 | Team confirms in backlog refinement |
| G8 | AI workflow not chosen; Responsible AI assessment missing | `features.md` §4 | Decision A or B; assessment document; evaluation dataset description |
| G9 | Ethical engagement mechanism not chosen | `features.md` §5 | Decision; answers to the five questions of assignment §5.5 |
| G10 | Backlog not in Jira; no release plan or sprint goals | `features.md` §6 | Stories in Jira with estimates; sprint goals; release plan |
| G11 | Help-request statuses and response teams are assumptions | `user-workflows.md` W3 | Interview with the security volunteer |
| G12 | Degraded-mode scenario to demonstrate not chosen | `user-workflows.md` W6; `architecture.md` §9 | Team picks the scenario |
| G13 | DoR and DoD not agreed or set up in the tool | `definition-of-ready-and-done.md` | Team agreement; issue template and board rule |
| G14 | No one holds the Product Owner role (needed to accept stories) | `definition-of-ready-and-done.md` | Role assignment (G16) |
| G15 | Team list may be incomplete | `team-and-process.md` §1 | Full list of members |
| G16 | Roles not assigned | `team-and-process.md` §2 | Team assigns roles |
| G17 | Dates for Sprints 2 and 3 and ceremony schedule | `team-and-process.md` §3 | Team decision |
| G18 | No Jira (or equivalent) board referenced | `team-and-process.md` §3 | Link to the board |
| G19 | Retention period for help-request data | `domain-model.md` §5 | Number of days; part of the security and privacy assessment |
| G20 | Transport data source not confirmed | `architecture.md` §7 | Check whether the STCP GTFS feed is usable, or build a simulator |
| G21 | Security and Privacy Assessment missing (Sprint 1 item 9) | `architecture.md` §8 | The assessment document |
| G22 | Target deployment environment unknown | `architecture.md` §10 | Where the MVP runs and how teachers access it |
| G23 | ADRs, API outline and walking skeleton missing (Sprint 1 items 7, 8, 10) | `architecture.md` §13 | ADR-001 to ADR-008; API outline; minimal end-to-end code with build, test and container |

### Decisions needed from the team

| # | Decision | Where |
| --- | --- | --- |
| DN-1 | AI workflow: A (triage, recommended) or B (assistant) | `features.md` §4 |
| DN-2 | Engagement mechanism: E1 (confirm or flag information, recommended), E2 or E3 | `features.md` §5 |
| DN-3 | External integration: STCP GTFS or simulator | `features.md` §3; `architecture.md` §7 |
| DN-4 | Separate services or modular monolith (ADR-001) | `architecture.md` §11 |
| DN-5 | Technology stack (backend, frontend, database, broker, AI provider, identity, CI/CD) | `architecture.md` §12 |
| DN-6 | Domain questions DM-1 to DM-5 (participant identity, request vs incident, operator-created requests, categories, per-night configuration) | `domain-model.md` §6 |
| DN-7 | Role assignment | `team-and-process.md` §2 |
| DN-8 | Language of repository documentation (translate the deliverables or change the rule) | `team-and-process.md` §4 |

### Process findings from the git history

Recorded in `team-and-process.md` §4, not fixed here because they depend on repository settings:

- Commits made directly on `main` without a PR (`5ef2c4a`, `3a4ba04`, `e853854`): the "no direct pushes" rule is not enforced.
- PRs #18–#20 were merged with merge commits, although README §4 requires squash merge.
- Excluding the initial commit, 4 of 7 non-merge commits do not follow the commit-message convention.

### Notes for whoever opens the PR

- The commits on this branch have **no issue number**, because there is no issue for this work yet. Create one in `tomasoliveira21/LABDSOF` and use `Closes #133` in the PR description; with squash merge, the PR title becomes the commit on `main` (README §4).
- Suggested PR title: `Docs: Add Sprint 1 product, process and technical artifacts`.
- Mermaid diagrams render on GitHub. They were checked with the Mermaid CLI before committing.

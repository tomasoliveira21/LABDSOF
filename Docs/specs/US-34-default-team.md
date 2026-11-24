# US-34 (#81 AC-2) · Propose the event's team for the AI category

> **Issue:** [#81](https://github.com/tomasoliveira21/LABDSOF/issues/81) (its last acceptance criterion), part of [#149](https://github.com/tomasoliveira21/LABDSOF/issues/149) (AC-3) · **Type:** user story (F8, AI help-request triage) · **Service:** Reports (owner area: Reports and the AI triage, decision 13 of the [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md)) · **Spec rules:** [README](README.md)
> **Sources:** [`features.md`](../Product/features.md) §4.2 (a team's handled categories give the default team), F8 and US-34; the meeting of 15 November 2026, decision 1 (the team is a rule, not a model output); [local AI decisions](../Technical/plans/ai-provider-evaluation.md), runtime contract item 3 and its required scenarios; [domain model](../Technical/domain-model.md) (Team: *which team is the default when two teams handle the same category is still open*); [API outline](../Technical/api-outline.md) §4, §4.1 (`defaultTeamId`) and §7 (`TeamChanged`); [`contracts/openapi.yaml`](../../backend/reports/contracts/openapi.yaml) and [`consumed/team.changed.schema.json`](../../backend/reports/contracts/events/consumed/team.changed.schema.json); [ADR-004](../Technical/adr/0004-service-communication.md) (local copies, no cross-service calls); [ADR-005](../Technical/adr/0005-ai-triage-provider-and-evaluation.md); specs [US-16](US-16-teams.md), [US-17](US-17-team-permissions.md) (D5: several teams may handle one category; the tie-break stays in Reports), [US-34](US-34-ai-triage-suggestion.md), [US-35](US-35-accept-or-change-suggestion.md) (D7) and [US-26](US-26-live-help-request-list.md).
> **Base:** `main` (`d517d76`). US-17 (#66, PR #243) is merged, so `TeamChanged` carries each team's permissions and handled categories with strictly increasing versions.

## 1. Source

**Story:** as a control-room member, I want each new help request to arrive with a suggested category and priority, so that I see at once what is urgent and route it faster. Persona P2.

**This PR:** the control-room view of a request (`GET …/help-requests`, `GET …/help-requests/{requestId}`, `POST …/accept-suggestion`, `PATCH …/help-requests/{requestId}`) answers `defaultTeamId`: the event's only team that handles the request's category and has `HelpRequests.Respond`, from Reports' own copy of the teams. **Not in it:** the route action, which takes a team and refuses another event's or an ineligible team with `404` (US-29, #76); the tie-break when several teams handle the category (open product decision); the backoffice page (the web app's owner, decision 13).

| # | Acceptance criterion (#81) | Tests (name starts with `US-34 AC-2`) |
|---|---|---|
| AC-2 | Given the AI suggests a category, then the event's team that handles it (US-17) is proposed as the default team; the AI does not choose the team | `HelpRequestTest`, `HelpRequestServiceTest`, `DefaultTeamIT` |

AC-1 and AC-3 of #81 were delivered by #229 (PR #230); this PR closes the issue. The model's offline quality gate still fails (#224, #227): the suggestion is not release evidence, and the tests use suggestions written by the test, not by a model.

#149 AC-3 (*the integration contract only permits eligible teams from the current event, rejects cross-event or ineligible values and handles a missing eligible team through manual routing*), as far as the proposal goes: each row of §5 marked *AC-3* is a test. Refusing a team chosen by a member is the route action's (US-29), so #149 AC-3 is complete only with US-29.

## 2. Quality gates

The gates of [#194](194-reports-postgres-outbox.md) §2 for `backend/reports`, unchanged: `mvn verify` green, JaCoCo ≥ 90 % lines and branches, PIT `DEFAULTS` ≥ 80 % on `domain` and `application`, Testcontainers with the Compose images, the production migrations and the broker set-up, `ContractIT`, the `ci.yml` smoke on a fresh stack, `DocumentationIT` for this spec's tests, the diff guard, an independent review and green CI with SonarQube. No new endpoint, so the security matrix of G3 is unchanged; the new rule's own security row (another event's team) is in §5.

## 3. Decisions

Decisions the documents leave open are marked **Proposed, for the team's review**.

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **Eligible team:** a team of the request's event, not deleted, with `HelpRequests.Respond`, whose handled categories contain the category | Runtime contract item 3 (*a team that handles the suggested category and has `HelpRequests.Respond`*); US-16 (a deleted team takes no requests). Only Reports' copy from `TeamChanged` is read (ADR-004 decision 2): no call to Events, and no team name from the participant's text |
| D2 | **One eligible team → it is the default; none or several → `null`**, and the operator routes by hand | Runtime contract item 3 (*several qualify without a configured default: empty*). `TeamChanged` has no configured default, and the tie-break is an open product decision (domain model; US-17 D5 leaves it to Reports). Picking one of several (oldest, by name) would close that decision by accident. **Proposed, for the team's review** until the tie-break is decided |
| D3 | **The category used:** the request's category when a member set one, else the AI's suggested category; neither → `null` | The AC proposes a team for the suggested category; API outline §4 says the default team *follows from the category* after an accept, and a member's category wins over the AI's (NFR-22). Pending, `NotTriaged` or an abstention have no suggested category |
| D4 | **Derived when read, not stored:** every control-room read and decision computes `defaultTeamId` from the current team copy | Stored at triage time, it goes stale when a team changes after the suggestion (runtime contract scenario *stale projection*), and keeping it current would mean rewriting every open request of the event on each `TeamChanged`, bumping versions that a member's edit checks. One query of the event's teams per read (a poll reads them once). The `default_team_id` column of `V1` stays unused (always `null`); dropping it needs a migration, left out here so this PR does not race PR #245's `V5`. The live list after a team change: D6 |
| D5 | **The participant never sees it** | API outline §4: the participant sees description, status and the time of the last change only (`ParticipantView`) |
| D6 | **A poll after a team change lists every open request again.** Flyway `V6` adds `team_copy.changed_at`, set by the application's clock when a `TeamChanged` is applied (not when it is stale); a poll with `updatedSince` before that time answers the event's open requests plus the ones that changed, so a default team already on screen follows the team change | Found by the independent review (G8): a poll lists only requests whose own data changed (US-26), so a team deleted after the suggestion stayed proposed until a full reload. Weighed: bumping `help_request.updated_at` on each `TeamChanged` (moves the participant's *time of the last change*, US-24, for a change that is not theirs); the client reloading on a timer (the contract would not say when). Team changes are rare, so a full list after one costs little. `V6`, not `V5`, because PR #245 adds a `V5`; whichever of the two merges second keeps its number only if it is higher than main's |

## 4. Features (build order)

1. `Team.handles(Category)` and `HelpRequest.withDefaultTeam(List<Team>)` (D1 to D3), domain tests first.
2. Port `LocalCopies.teams(UUID eventId)` (every team of the event) and its JDBC adapter.
2b. Flyway `V6` `team_copy.changed_at` and `LocalCopies.teamsChangedSince` (D6), after the G8 review.
3. `HelpRequestService`: the control-room `get`, `list`, `acceptSuggestion` and `change` answer the default team (D4, D5).
4. Contract and documents: `openapi.yaml` (`defaultTeamId` description; `accept-suggestion` no longer *planned*), API outline, `ai-provider-evaluation.md` (routing scenarios run), ADR-005 acceptance note, CHANGELOG.

## 5. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| One team of the event handles the suggested category, with `Respond` (*AC-3*) | It is `defaultTeamId` | `HelpRequestTest` · `US-34 AC-2 the event's only eligible team for the suggested category is the default team`; `DefaultTeamIT` · `US-34 AC-2 a suggestion proposes the event's team for its category in the list and the request` |
| No team handles the category (*AC-3*, manual routing) | `null`; the suggestion is still shown | `HelpRequestTest` · `US-34 AC-2 no team for the category, or several, gives no default team`; `DefaultTeamIT` · `US-34 AC-2 no team for the category leaves the default team empty and the suggestion shown` |
| Several teams handle the category (D2, *AC-3*) | `null` | `HelpRequestTest` · `US-34 AC-2 no team for the category, or several, gives no default team`; `DefaultTeamIT` · `US-34 AC-2 two teams for the category give no default team` |
| A deleted team, or a team without `HelpRequests.Respond` (*AC-3*, ineligible) | Never the default; a single eligible team beside it still is | `HelpRequestTest` · `US-34 AC-2 a deleted team or a team without HelpRequests.Respond is never the default` |
| A team of another event handles the category (*AC-3*, cross-event) | Never the default, whatever the list given | `HelpRequestTest` · `US-34 AC-2 a team of another event is never the default`; `DefaultTeamIT` · `US-34 AC-2 two events with a team for the same category each propose their own` |
| The team changes after the suggestion (*AC-3*, stale projection) | The next read uses the current copy: a team deleted or losing `Respond` disappears, a new one appears | `DefaultTeamIT` · `US-34 AC-2 the default team follows a TeamChanged after the suggestion` |
| No category and no suggestion (Pending, `NotTriaged`, abstention) | `null` | `HelpRequestTest` · `US-34 AC-2 without a category or a suggestion there is no default team` |
| A member set the category (accept, or a change to another category) (D3) | The default team follows the member's category | `HelpRequestTest` · `US-34 AC-2 a member's category wins over the suggested one`; `DefaultTeamIT` · `US-34 AC-2 accepting and changing the category move the default team with it` |
| A member set only the priority | The suggested category still gives the default team | `HelpRequestTest` · `US-34 AC-2 a member's category wins over the suggested one` |
| Injection text naming a team (*prompt injection inventing a team*) | The description is never read for the team: only the category picks it | `DefaultTeamIT` · `US-34 AC-2 a description that names a team does not choose the team` |
| A poll with `updatedSince` after a team change (D6) | Every open request again, with the current default team, plus the requests that changed; a stale `TeamChanged` or another event's team change lists nothing new | `HelpRequestServiceTest` · `US-34 AC-2 a poll after a team change lists the open requests again, with those that changed`; `DefaultTeamIT` · `US-34 AC-2 a poll after a TeamChanged lists the open requests again with the new default team` |
| The spec's test names | Each exists, and the changelog has the entry | `DocumentationIT` · `US-34 AC-2 the changelog has the default team, and every test its spec names exists` |
| Every control-room read and decision | Each answers the default team; the participant's view has none (D5) | `HelpRequestServiceTest` · `US-34 AC-2 every control-room read and decision answers the default team`; `DefaultTeamIT` · `US-34 AC-2 a suggestion proposes the event's team for its category in the list and the request` |

## 6. Handoff

**24 November 2026.**

- Baseline on `main` (`d517d76`): `mvn -B -ntp -f backend/reports/pom.xml verify` green: 120 unit tests, 168 integration tests, JaCoCo 97.78 % lines and 95.90 % branches, PIT 160 of 172 mutations killed (93 %).
- Built test first (§4). Red before green: the domain tests did not compile before `withDefaultTeam`; with the service change set aside, `HelpRequestServiceTest` failed on the default team and `DefaultTeamIT` failed 5 of its 7 tests (the two *no default* cases pass either way, as they should); the D6 service test failed before `changes(…)` existed. One test of US-26 needed its set-up changed: `LiveListIT` now dates its control-room team an hour back, because a team created during the test is a team change (D6) and lists the open requests again, which is the new rule, not a defect.
- **G1** `mvn -B -ntp -f backend/reports/pom.xml verify`: 128 unit tests, 177 integration tests, JaCoCo 97.82 % lines and 96.12 % branches (≥ 90 %), PIT 187 of 187 mutations killed on `domain` and `application` (≥ 80 %).
- **G2** `DefaultTeamIT`: real PostgreSQL and RabbitMQ (the Compose images, production migrations including `V6`, `setup.sh`); team changes go through `LocalCopyService`, as a `TeamChanged` does (the broker path is `LocalCopiesIT`'s).
- **G3** no new endpoint; the existing matrices are unchanged and green. The rule's own security row: another event's team is never proposed (`HelpRequestTest`, `DefaultTeamIT`).
- **G4** `contracts/openapi.yaml`: `defaultTeamId` described, the list operation says a poll after a team change lists the open requests again, `acceptTriageSuggestion` no longer *planned*. `ContractIT` passes. No event schema changes.
- **G5** smoke on a fresh stack (`down --volumes`, images built one by one, `up -d --wait`, every check step of the `ci` job, `down --volumes`): 14 of 14 steps pass, after the G8 fixes (the Reports image runs `V6`).
- **G6** guard: no blocking finding, test census 910 → 927, no warning.
- **G7** CHANGELOG entry; dated notes in the API outline §4.1, ADR-005, the local AI decisions (routing scenarios) and `responsible-ai.md`; `DocumentationIT` checks every test this spec names (AC-2 only: AC-1 and AC-3 of #81 were delivered under #229's names).
- **G8 independent review** (`code-review` at `high` on `d517d76...HEAD`), 6 findings:
  - Important, fixed test first: a poll with `updatedSince` never sent an already listed request again after a team change, so a deleted team stayed proposed (D6, Flyway `V6`). A mutant that survived the first run (a request closed in the window, after a team change) got its own assertion.
  - Nits fixed: `DocumentationIT` had no case for this spec; the API outline's example comment for `defaultTeamId` was out of date; this §6 was empty; `Team.handles` hid the `HelpRequests.Respond` check and is now `canRespondTo`.
  - Nit kept: no meeting record for D2 and D6. They are proposals for the team's review, recorded here and in the CHANGELOG as other specs' proposals are; a record in `Docs/Meetings/` is written when the team decides (as for #75's D5 and D6).
- Not done, and not claimed: refusing another event's or an ineligible team when a member routes, and the route action (US-29, #76), so #149 AC-3 is complete only with US-29; the tie-break when several teams handle a category (open product decision, D2); dropping the unused `default_team_id` column (D4); the backoffice page (the web app's owner). The model's offline gate fails (#224, #227), so the suggestions are not release evidence.
- Merge note: PR #245 (Flyway `V5`) merged first, so `V6` keeps its number and nothing was renumbered (`spring.flyway.out-of-order` is off, so a later-resolved `V5` would have been refused).
- **24 Nov 2026 · review round** (PR #248), after merging `main` (#245 and #247): the poll after a team change concatenated the open requests and the changed ones, so a request closed in the window, which arrived first, came last; the list is sorted again in order of arrival (`submitted_at`, then the id compared as the database compares a `uuid`), and `HelpRequestServiceTest` asserts the order with a closed request that arrived before the open ones. The `acknowledge` of #245 answers the same control-room view, so it carries `defaultTeamId` too (`HelpRequestServiceTest`, `DefaultTeamIT`).

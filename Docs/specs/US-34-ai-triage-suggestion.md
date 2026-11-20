# US-34 (part, #229) · AI triage suggestion without the default team

> **Issue:** [#229](https://github.com/tomasoliveira21/LABDSOF/issues/229), part of US-34 [#81](https://github.com/tomasoliveira21/LABDSOF/issues/81) (its AC-1 and AC-3) · **Type:** user story (F8, AI help-request triage) · **Service:** Reports (owner area: Reports and the AI triage, decision 13 of the [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md)) · **Spec rules:** [README](README.md)
> **Sources:** [`features.md`](../Product/features.md) US-34, §5.1, §5.2 (AI-1 to AI-3), §5.3; [ADR-005](../Technical/adr/0005-ai-triage-provider-and-evaluation.md) decisions 1 to 3 and 5; [local AI decisions](../Technical/plans/ai-provider-evaluation.md) (K1, runtime and resilience contract, event-specific production contract); NFR-11 and NFR-22 ([NFRs](../Product/non-functional-requirements.md)); [API outline](../Technical/api-outline.md) §4.1 (triage states and reasons), §7.4 and §7.5 (`HelpRequestSubmitted`, queue `reports.reports-events`, the triage consumer); specs [194](194-reports-postgres-outbox.md) (outbox, D13) and [US-26](US-26-live-help-request-list.md) (D4, D5); [evaluation package](../Research/architecture-investigation/evaluation/README.md) and [spec 227](227-critical-ceiling-holdout.md) (the frozen prompt v8).
> **Base:** `docs/227-critical-ceiling-holdout` (PR #228, stacked on PR #225), because the prompt and schema come from its evaluation package. Rebase onto `main` once those are merged.

## 1. Source

**Story:** as a control-room member, I want each new help request to arrive with a suggested category and priority, so that I see at once what is urgent and route it faster. Persona P2.

**This PR:** the Reports triage consumer of `HelpRequestSubmitted`, the local Ollama adapter, the validation of the model's output, the stored suggestion (or the reason there is none), the opt-in `ai` Compose service. **Not in it:** #81 AC-2, the default team (it needs US-17, #66, and the Events service, PR #218, of another owner); the circuit breaker, the triage-status banner and the rest of scenario D1 (US-36, #83); accepting the suggestion (US-35, #82); the backoffice page (web app, decision 13: what it needs is written on #229).

| # | Acceptance criterion (#229) | From #81 | Tests (name starts with `229 AC-<n>`) |
|---|---|---|---|
| AC-1 | Given a submitted help request and an available AI, then within 30 s the request reads `triage.state: Suggested` with the suggested category, priority and reason, and its own category and priority stay empty | AC-1 (SC3) | `TriageIT`, `TriageServiceTest`, `TriageSuggestionTest` |
| AC-2 | Given a help request, then the model receives only `{"text": <description>}` with the fixed prompt, schema and settings; no location, event, request, user or correlation ID enters the call | AC-3 (§5.2 AI-1) | `TriageIT`, `OllamaTriageModelTest`, `TriageServiceTest` |
| AC-3 | Given the AI times out, fails, returns invalid output or abstains, or is not configured, then the request has no suggestion, reads `NotTriaged` with the reason, and stays in the control room's list for manual triage | D1 (ADR-005 decision 3) | `TriageIT`, `TriageServiceTest`, `TriageSuggestionTest` |

The model's offline quality gate still fails (#224, #227), so a suggestion shown by this code is **not release evidence**: it is an operator aid under human control (NFR-22), and SC3 is not claimed from these tests.

## 2. Quality gates

The gates of [#194](194-reports-postgres-outbox.md) §2 for `backend/reports`, unchanged: `mvn verify` green, JaCoCo ≥ 90 % lines and branches, PIT `DEFAULTS` ≥ 80 % on `domain` and `application`, Testcontainers with the Compose images and the production migrations and broker set-up, `ContractIT`, the `ci.yml` smoke on a fresh stack, `DocumentationIT` for this spec's tests, the diff guard, an independent review and green CI with SonarQube. No new endpoint, so the security matrix of G3 has nothing new to run on: the triage fields are read through the existing endpoints, whose matrix (#194, US-26) is unchanged. The Ollama double is a local stub HTTP server, not a database or broker mock. CI and the default stack never download the model.

## 3. Decisions

Decisions the documents leave open are marked **Proposed, for the team's review**.

| # | Decision | Options weighed and why |
|---|---|---|
| D1 | **The triage worker is Reports' consumer of its own `HelpRequestSubmitted`** on `reports.reports-events`, one message at a time, reading the request from its own database | Fixed by API outline §7.4/§7.5 and K1 (Reports owns the asynchronous worker; the browser never calls the model). One consumer matches `OLLAMA_NUM_PARALLEL=1`. The listener starts only when `farol.triage.enabled` is true (the default); the shared integration-test context turns it off, because `OutboxIT` reads that queue to check what the outbox published |
| D2 | **Ollama adapter with the JDK `HttpClient`**: `POST {FAROL_AI_URL}/api/chat`, `stream: false`, `think: false`, schema in `format`, temperature 0, seed 42, `num_ctx` 4096, `num_predict` 256, `keep_alive` 10 m; connect timeout 2 s, one attempt, the whole call capped at 20 s and by the time left before the 30 s deadline | Runtime contract of the local AI decisions and NFR-11, as the evaluation runner (`run_ollama.py`) sends it. No new dependency: the JDK client and Jackson are already there |
| D3 | **Prompt and schema are copies of the evaluation package** (`prompts/v8.txt`, frozen by #227, and `output.schema.json`) in `src/main/resources/triage/`, kept in LF by `.gitattributes`; a test fails if their SHA-256 differs from the package files | The plan of this work: the service sends what was evaluated. Options: read the files from `Docs/` at run time (not in the image), or copy them with a hash test (chosen). v8 is the latest frozen version; its gate failed too, as v4's did (#227) |
| D4 | **The model's output is validated in the domain** (`TriageSuggestion.fromModel`): exactly the four keys, a known category and priority, a reason of 1 to 240 characters without control characters, `abstain` a boolean, and an abstention with null category and priority (or a suggestion with both). Not repaired: an invalid output is `InvalidOutput`, an abstention `Abstained` | ADR-005 decision 3 and production contract items 2 and 4: invalid output becomes fallback, never a repaired success. In the domain, so PIT covers it |
| D5 | **Proposed, for the team's review: a failed triage is stored as `Pending` with its `notTriagedReason`, and reads `NotTriaged` with that reason at once** | `NotTriaged` is never stored (decision 6 of 15 November 2026, #177; the table's check allows only `Pending`, `Suggested`, `Accepted`, `Changed`). Options: write nothing and let the read derive `NotTriaged`/`Timeout` 30 s later (loses the real reason and makes the operator wait), or store the reason with `Pending` (chosen): the stored state stays within decision 6 and the operator sees the reason as soon as it is known |
| D6 | **Admission and late results**: inference starts only with at least 23 s of the 30 s left, otherwise `Unavailable` without a call; a reply that arrives after the deadline is discarded, so the read shows `NotTriaged`/`Timeout` as before; the save is conditional on the triage being still `Pending` without a reason, so a redelivered message never triages twice | Runtime contract (*Admission*, *Completion*). The suggestion is written only to `triage_suggestion`, never to the request's own category or priority, so it cannot overwrite a member's value (NFR-22) |
| D7 | **Proposed, for the team's review: AI not configured (`FAROL_AI_URL` empty, the default stack and CI) → `Unavailable`, no call** | Options: leave it `Pending` until it reads `Timeout` 30 s later, or mark it `Unavailable` at once (chosen): API outline §4.1 defines `Unavailable` as *the AI was not called*, which is what happens, and the operator sees it at once |
| D8 | **Every triage write also moves `help_request.updated_at`** (not `version`, which versions the request's own fields), so the live list shows the change; a `Pending` request whose reason is stored is not listed again at its deadline | Spec US-26 D5 requires it. The version is not exposed and no operator write exists yet (US-28) |
| D9 | **Proposed, for the team's review: the reason column and the contract allow 240 characters** (Flyway `V2`, `suggestedReason` `maxLength` 240) | #194 used 200; the output schema and production contract item 2 of the local AI decisions say 1 to 240. Options: truncate to 200 (a repaired output, against D4) or widen to the contract (chosen) |
| D10 | **Proposed, for the team's review: `ai` Compose service in the `ai` profile**: `ollama/ollama:0.40.16` pinned by digest (the image of #224 and #227), `OLLAMA_NUM_PARALLEL=1`, `OLLAMA_MAX_LOADED_MODELS=1`, `OLLAMA_MAX_QUEUE=1`, `OLLAMA_NO_CLOUD=1`, a model volume, no published port. Reports calls it directly when `FAROL_AI_URL=http://ai:11434` is set in `infra/.env` | K1 and `local-delivery.md` §1 place WireMock between Reports and the model, but no WireMock service exists in the stack yet; it belongs to the degraded-mode demo (guide J2, J4, US-36). Options: add WireMock now (more than this story needs) or call `ai` directly and leave WireMock to US-36 (chosen). The model is pulled by hand before a demo, never by CI |
| D11 | **No retry and no circuit breaker in this part** | One attempt is the contract. The circuit breaker, the triage-status endpoint and the banner are scenario D1, US-36 (#83) |
| D12 | **Logs carry metadata only**: request and event ID, outcome, `provider_ms`, the message's correlation ID; never the description, the reason or the model's text | ADR-005 decision 5 (log metadata rather than request bodies) |

## 4. Features (build order)

| # | Feature | Tests |
|---|---|---|
| F1 | Domain: `TriageSuggestion.fromModel`, `suggested`, `notTriaged`; `asSeenAt` and `HelpRequest.changedAfter` with a stored reason (D4, D5, D8) | `TriageSuggestionTest`, `HelpRequestTest` |
| F2 | Application: `TriageService.triage`, ports `TriageModel` and `HelpRequests.saveTriage` (D1, D5 to D8) | `TriageServiceTest` |
| F3 | Out adapters: `OllamaTriageModel` (D2, D3), `JdbcHelpRequests.saveTriage`, Flyway `V2` (D9) | `OllamaTriageModelTest`, `TriageIT` |
| F4 | In adapter: `TriageListener` on `reports.reports-events` (D1, D12) | `TriageIT` |
| F5 | Contract, Compose `ai` profile, `infra/.env.example`, documentation | `ContractIT`, `ComposeConfigIT`, `DocumentationIT` |

## 5. Edge-case matrix

| Case | Expected | Test |
|---|---|---|
| Valid suggestion | `Suggested` with category, priority, reason; the request's own category and priority stay null | `TriageSuggestionTest` · `229 AC-1 a valid model output is a suggestion`; `TriageIT` · `229 AC-1 a submitted request shows the AI suggestion within 30 s` |
| What the model receives | `{"text": description}` only, the fixed prompt, schema and settings; no ID, location or correlation ID | `TriageIT` · `229 AC-2 the model receives only the description`; `OllamaTriageModelTest` · `229 AC-2 the call carries the prompt, schema and settings of the runtime contract` |
| Copies of the prompt and schema | Same SHA-256 as the evaluation package | `OllamaTriageModelTest` · `229 AC-2 the prompt and schema are the evaluated package files` |
| Abstention | `NotTriaged`, `Abstained` | `TriageSuggestionTest` · `229 AC-3 an abstention is NotTriaged Abstained`; `TriageIT` · `229 AC-3 an abstention leaves the request for manual triage` |
| Output with an extra key, a missing key, an unknown category or priority, a reason empty or over 240 characters or with a control character, `abstain` not a boolean, an abstention with a category, a suggestion without a priority, not an object | `NotTriaged`, `InvalidOutput`; nothing repaired | `TriageSuggestionTest` · `229 AC-3 an invalid model output is NotTriaged InvalidOutput` |
| Reply not JSON, HTTP error, connection refused | `NotTriaged`, `InvalidOutput` / `ProviderError` | `OllamaTriageModelTest` · `229 AC-3 a provider error or a reply that is not JSON is a failure`; `TriageIT` · `229 AC-3 a provider error leaves the request for manual triage` |
| Call longer than the response timeout | `NotTriaged`, `Timeout`; one attempt | `OllamaTriageModelTest` · `229 AC-3 a call over the timeout is a timeout`; `TriageIT` · `229 AC-3 a slow model leaves the request NotTriaged Timeout` |
| AI not configured | `Unavailable`, no call | `TriageServiceTest` · `229 AC-3 without a configured AI the request is Unavailable` |
| Message processed with less than 23 s of the deadline left | `Unavailable`, no call | `TriageServiceTest` · `229 AC-3 a request that cannot start in time is Unavailable` |
| Reply after the 30 s deadline | Discarded; reads `NotTriaged`/`Timeout` | `TriageServiceTest` · `229 AC-3 a late suggestion is discarded` |
| Redelivered message, or a triage already written | No second call, no overwrite | `TriageServiceTest` · `229 AC-1 a request already triaged is not triaged again` |
| Request not found (another event, deleted) | Acknowledged, nothing written | `TriageServiceTest` · `229 AC-1 an unknown request is ignored` |
| Invalid message (bad envelope, unknown type, no requestId) | Dead-letter queue (spec 194 D8); other types acknowledged | `TriageIT` · `229 AC-1 an invalid triage message goes to the dead-letter queue` |
| Failure stored as `Pending` with a reason | Reads `NotTriaged` with that reason at once; not listed again at the deadline | `TriageSuggestionTest` · `229 AC-3 a stored reason reads NotTriaged at once`; `HelpRequestTest` · `229 AC-3 a request with a stored reason is not listed again at its deadline` |
| Live list | A triage write moves `updated_at`, so the next poll lists it | `TriageIT` · `229 AC-1 the live list shows the suggestion in the next poll` |
| Prompt injection in the description | Sent as data in `{"text": …}`; the output is validated like any other | `TriageIT` · `229 AC-2 the model receives only the description` (injection text) |
| Logs | No description or reason in the log | `TriageServiceTest` · `229 AC-2 the log has no description or reason` |
| Reason with 240 characters | Stored and served | `TriageIT` · `229 AC-1 a reason of 240 characters is stored` |

## 6. Handoff

**20 November 2026.**

- Baseline on `docs/227-critical-ceiling-holdout` (79dd273): `mvn -B -ntp -f backend/reports/pom.xml verify` green: 42 unit tests, 112 integration tests, JaCoCo 97.8 % lines and 97.4 % branches, PIT 63 of 63 mutations killed.

# Local AI: provider, Responsible AI and evaluation decision

**Issue:** [#153](https://github.com/tomasoliveira21/LABDSOF/issues/153), continuation of #149 / merged PR #152 · **Tracker:** [#147](https://github.com/tomasoliveira21/LABDSOF/issues/147) · **Updated:** 26 October 2026

**Architecture accepted; execution/evaluation evidence pending.** Status reconciled on 31 October (#166) against guide/NFR decisions recorded on 26–27 October. Select local Ollama with `qwen3:4b` Q4_K_M for the academic delivery. This is an architectural selection, not a measured quality claim. The professor's cost clarification authorises local delivery; it does not certify this model. Application implementation stays with [#81](https://github.com/tomasoliveira21/LABDSOF/issues/81), [#82](https://github.com/tomasoliveira21/LABDSOF/issues/82) and [#83](https://github.com/tomasoliveira21/LABDSOF/issues/83).

## K1: selected platform and cost

Run Ollama in the optional Compose `ai` profile on the demonstration machine, on a private network at `http://ai:11434` (the Compose service `ai`, reached by Reports through `wiremock`, [`local-delivery.md` §1](../local-delivery.md#1-what-runs)), with a persistent model volume and no published host port. Reports owns the asynchronous triage worker; the browser never calls Ollama. Use synthetic inputs until the privacy review. Disable cloud features with `OLLAMA_NO_CLOUD=1`; do not configure API credentials or a hosted fallback. Download the model before the demo and test offline operation. No inference VM or API-token subscription is needed; existing hardware, electricity, storage and initial download still have costs.

Use one Ollama runtime, one model and one request at a time. No hosted-provider comparison, GPU server or machine purchase is required for this local demo. Model download size is not runtime RAM: load the model before presentation and measure usage on the actual laptop with the application. The observed machine has 16 GiB physical RAM and about 4 GiB allocated to Docker; no full-stack capacity result is claimed. Run the optional AI and observability scenarios sequentially if needed. R0 can run with AI off, but that alone does not demonstrate working inference.

Hosted Mistral and cloud CPU/GPU remain the dated alternatives in the [assessment](../../Research/architecture-investigation/responsible-ai.md). They add account, network, privacy and variable-cost dependencies and are excluded from the current default. A later change needs new cost/privacy approval and an ADR update. Failure of the local quality gates means retaining manual operation and reporting a failed candidate, not silently selecting a paid service or relaxing a target.

## Runtime and resilience contract

| Setting | Selected local profile |
|---|---|
| API | `POST /api/chat`, `stream: false`, schema supplied through `format`, `think: false`; no tools or retrieval |
| Generation | Temperature 0, seed 42, context 4096, `num_predict: 256`, `keep_alive: "10m"`; freeze these in each run manifest |
| Capacity | `OLLAMA_NUM_PARALLEL=1`, `OLLAMA_MAX_LOADED_MODELS=1`, `OLLAMA_MAX_QUEUE=1`; a bounded application admission queue, with expired work discarded before inference |
| Deadline | 30 s from durable submission to stored valid suggestion, including outbox/queue, HTTP, validation and storage; never reset this clock on retry or redelivery |
| Admission | At most 5 s waiting before starting inference; start only with at least 23 s of the original budget remaining. Otherwise mark unavailable and leave manual triage visible |
| HTTP | Connect timeout 2 s; response timeout 20 s; one attempt, no retry. Total HTTP call also capped at 22 s and by the remaining overall deadline |
| Circuit | Last 10 calls, minimum 4; open at >=50% failures or slow calls (>16 s), remain open 30 s, allow one probe; queue-expired requests remain coverage misses |
| Completion | Recheck deadline, event eligibility and operator version atomically before saving; discard stale/late results. Fallback never overwrites an operator decision |

This selects J1's already identified local 20 s / single-attempt alternative for review. The hosted 10 s / two-attempt profile remains a researched alternative, not the local default. The 16 s slow threshold is 80% of the local response timeout; actual timing and circuit configuration must be verified in implementation. No retry storm or blocking AI call is allowed on request submission. A separate warm-up uses synthetic text before measurement; report cold start separately.

Sources checked for this recommendation: [Ollama chat API](https://docs.ollama.com/api/chat), [structured outputs](https://docs.ollama.com/capabilities/structured-outputs), [runtime configuration](https://docs.ollama.com/faq) and [model entry](https://ollama.com/library/qwen3:4b). Pin the actual full model digest and Ollama image digest when downloading; a mutable tag or a short web digest is insufficient. No model has been downloaded or run by this documentation change.

## Event-specific production contract

The existing [prompt/schema/dataset](../../Research/architecture-investigation/evaluation/README.md) are a frozen single-event research fixture. Preserve their hashes/baseline; Medical/Security/PCR are **not** global product roles. The application contract is:

1. Reports obtains a trusted snapshot of this event's teams, handled categories and `HelpRequests.Respond` permissions from its local projection of domain events. Do not perform cross-service REST calls or trust team names in participant text. If required configuration is missing/stale, abstain and retain manual routing.
2. Supply only participant category, redacted optional text (maximum 500 characters), current priority floor, and opaque eligible team IDs with handled categories. IDs identify teams only within the supplied allowlist; no event/account/request ID, coordinates, names or contact details enter the prompt. Keep the correlation ID outside it.
3. Return exactly `category`, `priority`, `teamId`, `reason` and `abstain`, with no extra fields. Categories/priorities follow the fixture enums. `reason` has 1–240 characters. Generate `teamId`'s allowlist from the trusted snapshot, permitting null for abstention; if no team is eligible, bypass inference and use manual triage.
4. Independently validate that a non-abstaining team belongs to this event, can respond to the suggested category, and still has permission at persistence time. Reject unknown, cross-event, revoked, malformed or category-incompatible choices. Do not repair invalid output into a scored success.
5. If several teams qualify, use an explicitly configured category default when available; otherwise abstain for operator choice. An abstention has `teamId: null`. The fixture's old `team` output stays only in the frozen evaluator; an explicit adapter/new version is required for integration, never silently reinterpret its scores.
6. Retain the original text/category and raw proposal, label it **AI suggestion**, and require operator approval before dispatch. Store effective priority as the maximum of current priority, baseline floor and accepted suggestion; score raw predictions before this protection.

Required routing scenarios: two events with different team IDs/names; identical display names across events; cross-event ID; revoked permission; incompatible category; no eligible team; multiple teams without a default; stale projection; prompt injection inventing a team; operator edit during inference. Expected invalid/missing-configuration outcome is abstention/manual routing, never unauthorised dispatch. These are implementation acceptance cases, not tests claimed to have run.

## K2: Responsible AI and dataset answers

| Concern | Decision and acceptance evidence |
|---|---|
| Purpose and people | Operational assistance to a festival incident operator; no diagnosis, treatment advice, autonomous dispatch or access to tools/privileged data |
| Input and prompt | Minimise/redact fields above; trusted system rules and JSON user data separated. Treat incident text as untrusted, including instructions embedded in it |
| Rubric | Critical: immediate serious danger; High: ongoing credible threat/vulnerable person lost; Medium: assistance without explicit immediate danger; Low: non-urgent support. Missing information means abstention, not invented facts |
| Priority floors | Health=Medium, Safety=High, LostPerson=Medium, Theft=Medium, Other=Low; preserve any higher existing priority. Operational proposals, not clinical guidance |
| Human control | Original request visible immediately, AI label and short reason, operator can reject/change; no numerical confidence without calibration and no overwrite after operator action |
| Safety | Zero urgent under-prioritisations, urgent missing/invalid/late/abstained outputs; >=80% category/team accuracy, >=95% coverage; human review of every reason in the holdout |
| Bias and abuse | Report PT-PT slang/typos, English/mixed language, ambiguous/multiple issues and injection slices with counts; small synthetic sets do not prove fairness or safety |
| Privacy | Local-only inference; no prompt/response bodies, identity or coordinates in telemetry. Synthetic-only research. The security assessment reviews real-data lawful basis, access, retention and deletion before real use |
| Retention | Synthetic evaluation artefacts versioned; telemetry follows the observability plan (rotated logs, 14-day maximum exports; no persistent trace store required). Incident records follow the separately reviewed product retention policy, not an invented AI retention period |
| Failure and accountability | AI-off/slow/malformed tests must retain 100% request visibility; record model/config version and sanitised failure metadata; operator feedback triggers review |
| Changes | Re-run evaluation when model, prompt, schema, rubric or event routing changes; no automatic update of mutable model tags |

The dataset is the existing **100 authored synthetic cases: 20 development / 80 test**, with stable IDs and explicit provenance. It is for repeatable regression and comparison with the non-AI baseline, not training or proof of real-world accuracy. Do not add invented expert approval. For the academic demo, ask one teammate to review the proposed labels/rubric, prioritising every urgent/abstention case, and record reviewer/date and corrections truthfully. Review the remaining labels before using aggregate accuracy as accepted quality evidence. Keep the 20/80 split; do not require another dataset or add 20 mandatory cases. Blinded independent labelling and fresh holdout collection are future strengthening, not demo prerequisites. No review is claimed to have occurred.

## Reproducible evaluation and release gate

1. Validate the existing package using the commands in its [README](../../Research/architecture-investigation/evaluation/README.md). Keep `cases.jsonl`, the prompt/schema and committed baseline unchanged by this decision.
2. Freeze code commit, dataset/split hashes, prompt/schema hashes, full model/runtime digests, settings above, OS/CPU/RAM/Docker allocation and experiment time. Prompt tuning uses the 20 development cases only; do not tune against test results. The visible repository test file is not blinded evidence.
3. Run the frozen 80-case test split once sequentially using input fields only, with the existing fixture prompt/schema. Record every selected ID including errors as `output: null`; never replace failed predictions with reference labels or the deterministic baseline. Store raw outputs and sanitised reasons for review.
4. Score that run using `evaluate.py --predictions /path/to/run-1.jsonl --split test --out /path/to/report-1.json`. Record provider-only time separately as `provider_ms`; omit `elapsed_ms` until actual submission-to-persistence instrumentation exists. A direct Ollama benchmark cannot certify SC3.
5. For the basic demo, preload the model, show one successful synthetic suggestion and then stop Ollama to show manual fallback/request visibility. Record actual elapsed time and container memory. Three repeated evaluations, cold-start studies and 5/10-concurrent-load experiments are optional extensions; never claim they ran.
6. Evaluate the reviewed fixture and a small manual dynamic-team checklist (valid team, wrong event, no eligible team); report counts, all errors, expected abstentions and reason review. Release gates are >=80% category/team accuracy, >=95% total coverage on non-abstention references, all reference abstentions correct, no priority-floor violations and zero urgent misses. Failures stay in denominators. This offline run has no `elapsed_ms` (step 4), so the scorer cannot compute valid on-time coverage: that half of the gate is decided by the instrumented one-hour cohort of step 7.
7. Measure the integrated rolling one-hour cohort: all pre-defined AI-eligible requests form the denominator; finalise after their 30 s deadlines. Require >=95% valid suggestions stored by 30,000 ms, inclusive. Errors and abstentions remain operational misses. Check SC5 separately: 100% durable request visibility with AI disabled. A zero denominator means no data.

Run artefacts: `manifest.json`, `predictions-run-1.jsonl`, scorer reports, routing-case report, human reason-review/adjudication record, and observed latency/memory. Record failures and limitations alongside successes; no raw real incidents or credentials in Git. Suggested output directory is a dated `evaluation/runs/YYYY-MM-DD-local-qwen3/`, created only when a real run exists.

## What is closed and what remains

K1 has a selected local platform/model, cost position, sizing experiment, runtime settings, deadline and fallback. K2 has purpose, data provenance/split, input/output and event routing, human safeguards, risk controls and an executable evaluation procedure using the existing scorer. **These documentary questions are answered for review.** Label review, one actual model evaluation, observed capacity and integrated SC3/SC5 evidence remain execution/review work; no benchmark, real-data privacy clearance or implemented application is claimed. [ADR-005](../adr/0005-ai-triage-provider-and-evaluation.md) records architectural acceptance; evaluation/release evidence remains pending.

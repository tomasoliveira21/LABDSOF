# Local AI: provider, Responsible AI and evaluation decision

**Issue:** [#153](https://github.com/tomasoliveira21/LABDSOF/issues/153), continuation of #149 / merged PR #152 · **Tracker:** [#147](https://github.com/tomasoliveira21/LABDSOF/issues/147) · **Updated:** 26 October 2026; triage design amended on 15 November 2026 ([meeting of 15 November 2026](../../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md), decisions 1, 2 and 8)

**Architecture accepted; execution/evaluation evidence pending.** Status reconciled on 31 October (#166) against guide/NFR decisions recorded on 26–27 October. Select local Ollama with `qwen3:4b` Q4_K_M for the academic delivery. This is an architectural selection, not a measured quality claim. The professor's cost clarification authorises local delivery; it does not certify this model. Application implementation stays with [#81](https://github.com/tomasoliveira21/LABDSOF/issues/81), [#82](https://github.com/tomasoliveira21/LABDSOF/issues/82) and [#83](https://github.com/tomasoliveira21/LABDSOF/issues/83).

**Change of 15 November 2026:** the participant no longer chooses a category and writes a required short description; the AI reads only that description and suggests **category and priority**, not the team; `Theft` is replaced by `Infrastructure`. The event-specific contract, rubric and gates below follow this. The evaluation package (dataset, prompt, schema, scorer and baseline) was updated to this design on 17 November 2026 (#197).

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
| Completion | Recheck deadline and operator version atomically before saving; discard stale/late results. Fallback never overwrites an operator decision |

This selects J1's already identified local 20 s / single-attempt alternative for review. The hosted 10 s / two-attempt profile remains a researched alternative, not the local default. The 16 s slow threshold is 80% of the local response timeout; actual timing and circuit configuration must be verified in implementation. No retry storm or blocking AI call is allowed on request submission. A separate warm-up uses synthetic text before measurement; report cold start separately.

Sources checked for this recommendation: [Ollama chat API](https://docs.ollama.com/api/chat), [structured outputs](https://docs.ollama.com/capabilities/structured-outputs), [runtime configuration](https://docs.ollama.com/faq) and [model entry](https://ollama.com/library/qwen3:4b). Pin the actual full model digest and Ollama image digest when downloading; a mutable tag or a short web digest is insufficient. No model has been downloaded or run by this documentation change.

## Event-specific production contract

The existing [prompt/schema/dataset](../../Research/architecture-investigation/evaluation/README.md) are a research fixture, updated on 17 November 2026 (#197) to the contract below: description-only input and no team in the output, so routing is checked by the integration scenarios below, not by the scorer. The application contract, amended on 15 November 2026:

1. Supply only the participant's redacted description (required, 1 to 500 characters). No participant category, team, event/account/request ID, coordinates, names or contact details enter the prompt. Keep the correlation ID outside it.
2. Return exactly `category`, `priority`, `reason` and `abstain`, with no extra fields. Categories: `Health`, `Safety`, `LostPerson`, `Infrastructure`, `Other`. Priorities: `Low`, `Medium`, `High`, `Critical` (rubric below). `reason` has 1–240 characters. An abstention has null category and priority.
3. **The team is not a model output.** After a valid suggestion, Reports derives the default team from its local projection of this event's teams: a team that handles the suggested category and has `HelpRequests.Respond`. Do not perform cross-service REST calls or trust team names in participant text. If no team qualifies, or several qualify without a configured default, the default team is empty and the operator routes by hand; inference still runs, because category and priority are useful without a team.
4. Do not repair invalid output into a scored success; invalid output becomes fallback (D1): the request shows its description with no category and no priority, marked *Not triaged*.
5. Retain the original description and raw proposal, label it **AI suggestion**, and require operator approval before dispatch. A suggestion never overwrites a member's value (NFR-22); score raw predictions before this protection.

Required scenarios: two events with different teams for the same category; no team for the suggested category; several teams without a default; stale projection; prompt injection inventing a team or a priority; operator edit during inference. These are implementation acceptance cases, not tests claimed to have run.

## K2: Responsible AI and dataset answers

| Concern | Decision and acceptance evidence |
|---|---|
| Purpose and people | Operational assistance to a festival incident operator; no diagnosis, treatment advice, autonomous dispatch or access to tools/privileged data |
| Input and prompt | Minimise/redact fields above; trusted system rules and JSON user data separated. Treat incident text as untrusted, including instructions embedded in it |
| Rubric | The scale of the [meeting of 15 November 2026](../../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md): **Critical**, a life or physical safety at risk now (e.g. someone needing emergency medical help); **High**, hurt or at risk and needs a team within minutes (e.g. a lost child); **Medium**, needs a team but nobody is in danger (e.g. a stolen phone); **Low**, can wait (e.g. a spilled drink). Missing information means abstention, not invented facts |
| Priority floors | Dropped on 15 November 2026: with no participant category there is no per-category default. Each evaluation case keeps its own minimum acceptable priority (#197). Operational proposals, not clinical guidance |
| Human control | Original request visible immediately, AI label and short reason, operator can reject/change; no numerical confidence without calibration and no overwrite after operator action |
| Safety | First, zero urgent under-prioritisations and zero urgent missing/invalid/late/abstained outputs; then >=80% category accuracy (no team gate since 15 November 2026), >=95% coverage; human review of every reason in the holdout |
| Bias and abuse | Report PT-PT slang/typos, English/mixed language, ambiguous/multiple issues and injection slices with counts; small synthetic sets do not prove fairness or safety |
| Privacy | Local-only inference; no prompt/response bodies, identity or coordinates in telemetry. Synthetic-only research. The security assessment reviews real-data lawful basis, access, retention and deletion before real use |
| Retention | Synthetic evaluation artefacts versioned; telemetry follows the observability plan (rotated logs, 14-day maximum exports; no persistent trace store required). Incident records follow the separately reviewed product retention policy, not an invented AI retention period |
| Failure and accountability | AI-off/slow/malformed tests must retain 100% request visibility; record model/config version and sanitised failure metadata; operator feedback triggers review |
| Changes | Re-run evaluation when model, prompt, schema, rubric or event routing changes; no automatic update of mutable model tags |

The dataset is **121 authored synthetic cases: 24 development / 97 test** (the 100 cases of 22 October 2026, with the `Theft` cases relabelled `Safety`, plus 15 `Infrastructure` cases added on 17 November 2026, #197, and 6 more not derived from the scale's examples on 18 November 2026, review of PR #217), with stable IDs and explicit provenance. It is for repeatable regression and comparison with a non-AI baseline, not training or proof of real-world accuracy. Do not add invented expert approval. For the academic demo, ask one teammate to review the proposed labels/rubric, prioritising every urgent/abstention case, and record reviewer/date and corrections truthfully. Review the remaining labels before using aggregate accuracy as accepted quality evidence. Keep the split rule (every fifth ID is development); do not require another dataset. Blinded independent labelling and fresh holdout collection are future strengthening, not demo prerequisites. No review is claimed to have occurred. The old baseline kept the participant's category, which no longer exists; since 17 November 2026 (#197) the comparison baseline is keyword rules on the description ([evaluation README](../../Research/architecture-investigation/evaluation/README.md#reproduce-the-offline-checks)).

## Reproducible evaluation and release gate

1. Validate the existing package using the commands in its [README](../../Research/architecture-investigation/evaluation/README.md). Keep `cases.jsonl`, the prompt/schema and committed baseline unchanged during a run; a change to any of them regenerates the baseline report (`--self-test` fails until it matches).
2. Freeze code commit, dataset/split hashes, prompt/schema hashes, full model/runtime digests, settings above, OS/CPU/RAM/Docker allocation and experiment time. Prompt tuning uses the 23 development cases only; do not tune against test results. The visible repository test file is not blinded evidence.
3. Run the frozen 92-case test split once sequentially using input fields only, with the existing fixture prompt/schema. Record every selected ID including errors as `output: null`; never replace failed predictions with reference labels or the deterministic baseline. Store raw outputs and sanitised reasons for review.
4. Score that run using `evaluate.py --predictions /path/to/run-1.jsonl --split test --out /path/to/report-1.json`. Record provider-only time separately as `provider_ms`; omit `elapsed_ms` until actual submission-to-persistence instrumentation exists. A direct Ollama benchmark cannot certify SC3.
5. For the basic demo, preload the model, show one successful synthetic suggestion and then stop Ollama to show manual fallback/request visibility. Record actual elapsed time and container memory. Three repeated evaluations, cold-start studies and 5/10-concurrent-load experiments are optional extensions; never claim they ran.
6. Evaluate the reviewed fixture and a small manual default-team checklist (team found, no team for the category, several teams without a default); report counts, all errors, expected abstentions and reason review. Release gates, in this order: zero urgent misses; then >=80% category accuracy, >=95% total coverage on non-abstention references, all reference abstentions correct and no case below its minimum priority. *18 November 2026 (review of PR #217): the scorer also fails a `Low` or `Medium` reference rated `Critical`, so that inflating every priority does not pass.* Failures stay in denominators. This offline run has no `elapsed_ms` (step 4), so the scorer cannot compute valid on-time coverage: that half of the gate is decided by the instrumented one-hour cohort of step 7.
7. Measure the integrated rolling one-hour cohort: all pre-defined AI-eligible requests form the denominator; finalise after their 30 s deadlines. Require >=95% valid suggestions stored by 30,000 ms, inclusive. Errors and abstentions remain operational misses. Check SC5 separately: 100% durable request visibility with AI disabled. A zero denominator means no data.

Run artefacts: `manifest.json`, `predictions-run-1.jsonl`, scorer reports, routing-case report, human reason-review/adjudication record, and observed latency/memory. Record failures and limitations alongside successes; no raw real incidents or credentials in Git. Suggested output directory is a dated `evaluation/runs/YYYY-MM-DD-local-qwen3/`, created only when a real run exists.

## What is closed and what remains

K1 has a selected local platform/model, cost position, sizing experiment, runtime settings, deadline and fallback. K2 has purpose, data provenance/split, input/output and event routing, human safeguards, risk controls and an executable evaluation procedure using the existing scorer. **These documentary questions are answered for review.** Label review, one actual model evaluation, observed capacity and integrated SC3/SC5 evidence remain execution/review work; no benchmark, real-data privacy clearance or implemented application is claimed. [ADR-005](../adr/0005-ai-triage-provider-and-evaluation.md) records architectural acceptance; evaluation/release evidence remains pending.

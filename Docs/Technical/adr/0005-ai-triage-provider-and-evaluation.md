# ADR-005 · AI triage provider and evaluation

## Status

**Accepted — architectural decision; runtime/evaluation evidence pending.** Status reconciled on 31 October 2026 (#166) with the existing [guide K1/J1](../architecture-decisions-guide.md) and [NFRs §5](../../Product/non-functional-requirements.md#5-validated-with-the-team-on-26-october-2026): team-confirmed targets on 26 October and the 27 October local Ollama/Qwen3, no-cloud-fallback and single-attempt profile. This records those existing decisions, not a new team vote or model-quality approval. K2 label/reason/routing evaluation, privacy gates before real data and measured SC3/SC5 remain outstanding; acceptance of the architecture does not waive them. Covers K1–K2 / AI-1–AI-11.

**Delivery-scope update:** the professor authorised local-only application execution due to cost ([clarification](../../Meetings/2026-10-22-local-deployment-clarification.md)). Local Ollama `qwen3:4b` Q4_K_M is now the selected local architecture, specified in the [complete local AI decision](../plans/ai-provider-evaluation.md); hosted inference remains a researched alternative. The authorisation itself does not approve a model. Fixed team names in the synthetic schema are an event fixture, not the production role model: derive eligible teams from the event's configured permissions/categories and validate isolation.

## Context

F8 suggests category, priority and response team; an operator decides. F7 must work without AI. SC3 allows 30 s from submission to stored suggestion and SC5 requires all requests visible with AI off. NFR-22 forbids lowering priorities or overwriting operator decisions. The complete [Responsible AI assessment](../../Research/architecture-investigation/responsible-ai.md) records sources, costs, input/output, risks and evidence gaps.

## Decision

1. Select local Ollama `qwen3:4b` Q4_K_M, private Compose AI profile, cloud features disabled and no paid API fallback. Use the fixed runtime settings, single 20 s attempt, event-specific contract and single-model, single-run demo evaluation in the [implementation decision](../plans/ai-provider-evaluation.md). Retain hosted `mistral-small-2603` and cloud GPU as assessed alternatives; no provisioning is authorised.
2. Minimise input to participant category, sanitised optional text and current priority floor. Fixed categories/priorities, event-derived eligible team IDs, a short operator-facing reason and abstention; no tools, identifiers, exact location or autonomous dispatch.
3. Enforce schema/semantic validation, immediate F7 visibility and a total 30 s inference budget. Late/error/abstained results become fallback. Protect operator updates and clamp stored priorities atomically; measure raw predictions separately so safety guards cannot conceal errors.
4. Require zero urgent under-prioritisations on the reviewed holdout, proposed category/team accuracy ≥80%, valid on-time coverage ≥95% within 30 s (the operational target uses a one-hour window), reason review and an AI-off integration test. The supplied labels require documented team review; a passing synthetic set cannot establish clinical safety.
5. Synthetic data only until the security assessment confirms actual endpoint, processing/retention/training controls and privacy assessment. Freeze versions/settings and log metadata rather than request bodies.

## Coverage definition (aligned 26 October 2026, #145)

The **confirmed common target is at least 95% valid suggestions stored within 30 s end to end**. This replaces the earlier 90% evaluation proposal and agrees with [observability I2](../../Research/architecture-investigation/observability.md#slis-and-proposed-slos-i2) and NFR-03 in [PR #139](https://github.com/tomasoliveira21/LABDSOF/pull/139). The team confirmation recorded in NFRs §5 supports the architectural acceptance above; target alignment itself does not establish measured compliance.

- **Valid and on time:** schema and semantic checks pass, the result is not an abstention, the team is eligible for that event, the raw priority respects its floor, and the suggestion is stored no later than 30,000 ms after submission. Queue/outbox wait, all inference attempts, validation and persistence consume that same deadline. Valid suggestions at exactly 30,000 ms count; later ones do not.
- **Operational SLI:** valid on-time suggestions / **all AI-eligible requests submitted in a one-hour window**, including provider/circuit errors, timeouts, refusals, malformed output and abstentions. Those failures remain misses; a working non-AI fallback is not an AI suggestion. Eligibility is determined by the workflow before the provider result, not by dropping difficult/failed requests afterwards.
- **Window accounting:** evaluate a submission cohort only after every request's 30 s deadline has expired (a one-hour cohort closes and is finalised 30 s later). The dashboard can use rolling one-hour cohorts with the same deadline lag. Report counts and degraded periods alongside the complete ratio, not instead of it; zero eligible requests means **no data**, not 100%.
- **Offline evaluation:** use the same ≥95% threshold on the frozen holdout's **non-abstention reference cases**. Expected abstentions are checked separately and are not silently added to the operational numerator. Count missing, invalid, refused, late and unexpectedly abstained outputs as misses. Total suggestion coverage, before timing, also has a ≥95% gate. The fixtures cannot establish production event eligibility; that requires the event-specific integration test.
- **Independent safeguards:** zero urgent under-prioritisations or missing/abstained/late urgent suggestions; ≥80% category/team accuracy; correct expected abstentions and human review of reasons. SC5 still requires 100% request visibility with AI unavailable. A 95% AI target never permits losing 5% of help requests.

The dataset run and a one-hour operational cohort have different populations and are reported separately. Direct provider latency without queue/storage timing cannot pass the end-to-end timing gate. The [offline scorer](../../Research/architecture-investigation/evaluation/evaluate.py) applies the 95% proposal; no model or runtime measurement is supplied by this alignment.

## Consequences

- **Selected local model.** No help-request text leaves the demo machine (NFR-20) and there is no API-token cost. Why `qwen3:4b` Q4_K_M: it can run on a laptop CPU without token charges (published model file about 2.5 GB; prototype with 8 GiB host RAM, 4 CPU cores, context 4096, concurrency 1) ([Responsible AI assessment](../../Research/architecture-investigation/responsible-ai.md), provider table). Latency on the demo machine's CPU and full-stack memory remain unmeasured; download size is not runtime RAM.
- **One request at a time.** Ollama runs with `OLLAMA_NUM_PARALLEL=1` and a queue of 1 ([plan](../plans/ai-provider-evaluation.md), capacity row), so a burst of help requests waits in the application's admission queue, and requests that cannot start in time become fallback (D1).
- **Hosted alternative, not selected:** token cost would avoid an always-on inference host but adds network, account, privacy and quota dependencies.
- Evaluation and monitoring become release evidence; release eligibility stays conditional until those measurements exist; the selected documentary recommendation is explicit.

## Alternatives considered

- Cloud Ollama as default: greater infrastructure/memory cost and unknown CPU latency; retain as a candidate if measurements justify it.
- Hosted model selected solely by headline accuracy/price: insufficient evidence for Portuguese triage or sensitive-data handling.
- Only deterministic routing: remains the fallback/baseline; cannot provide the required AI-assisted workflow by itself.
- Autonomous classification/dispatch: rejected by the existing operator-control decision and NFR-22.

## Acceptance evidence pending

Documented label review and one local model evaluation; deployed SC3/SC5 evidence; privacy review for real data; runtime/evaluation review. Paid-provider clearance/spend applies only if a hosted alternative is reconsidered. Research artefacts and offline baseline are linked in the [evaluation package](../../Research/architecture-investigation/evaluation/README.md).

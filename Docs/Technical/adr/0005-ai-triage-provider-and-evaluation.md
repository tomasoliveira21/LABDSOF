# ADR-005 · AI triage provider and evaluation

## Status

**Proposed**, 22 October 2026, research for [#53](https://github.com/tomasoliveira21/LABDSOF/issues/53). Not accepted by the team; no model performance or provider/privacy approval claimed. Covers guide K1–K2 and features AI-1–AI-11.

**Delivery-scope update:** the professor authorised local-only application execution due to cost ([clarification](../../Meetings/2026-10-22-local-deployment-clarification.md)). The hosted-demo preference below remains a researched alternative; no provider/model is selected by this authorisation. Fixed team names in the synthetic schema are an event fixture, not the production role model: derive eligible teams from the event's configured permissions/categories and validate isolation.

## Context

F8 suggests category, priority and response team; an operator decides. F7 must work without AI. SC3 allows 30 s from submission to stored suggestion and SC5 requires all requests visible with AI off. Domain invariant I4 forbids lowering priorities or overwriting operator decisions. The complete [Responsible AI assessment](../../Research/architecture-investigation/responsible-ai.md) records sources, costs, input/output, risks and evidence gaps.

## Proposed decision

1. Benchmark hosted `mistral-small-2603` against local Ollama `qwen3:4b` with the versioned prompt/schema and synthetic dataset. Prefer hosted inference for a low-volume synthetic cloud demo only after the same quality/latency/cost gates pass. Do not deploy GPU inference without a measured need and an approved quote.
2. Minimise input to participant category, sanitised optional text and current priority floor. Fixed category/team enums, a short operator-facing reason and abstention; no tools, identifiers, exact location or autonomous dispatch.
3. Enforce schema/semantic validation, immediate F7 visibility and a total 30 s inference budget. Late/error/abstained results become fallback. Protect operator updates and clamp stored priorities atomically; measure raw predictions separately so safety guards cannot conceal errors.
4. Require zero urgent under-prioritisations on the reviewed holdout, proposed category/team accuracy ≥80%, valid on-time coverage ≥95% within 30 s (the operational target uses a one-hour window), reason review and an AI-off integration test. The supplied labels require independent review; a passing synthetic set cannot establish clinical safety.
5. Synthetic data only until Guerra confirms actual endpoint, processing/retention/training controls and privacy assessment. Freeze versions/settings and log metadata rather than request bodies.

## Coverage definition (aligned 26 October 2026, #145)

The **proposed common target is at least 95% valid suggestions stored within 30 s end to end**. This replaces the earlier 90% evaluation proposal and agrees with [observability I2](../../Research/architecture-investigation/observability.md#slis-and-proposed-slos-i2) and NFR-03 in [PR #139](https://github.com/tomasoliveira21/LABDSOF/pull/139). Alignment makes the specification consistent; it does not accept the provider/ADR on behalf of the team or establish measured compliance.

- **Valid and on time:** schema and semantic checks pass, the result is not an abstention, the team is eligible for that event, the raw priority respects its floor, and the suggestion is stored no later than 30,000 ms after submission. Queue/outbox wait, all inference attempts, validation and persistence consume that same deadline. Valid suggestions at exactly 30,000 ms count; later ones do not.
- **Operational SLI:** valid on-time suggestions / **all AI-eligible requests submitted in a one-hour window**, including provider/circuit errors, timeouts, refusals, malformed output and abstentions. Those failures remain misses; a working non-AI fallback is not an AI suggestion. Eligibility is determined by the workflow before the provider result, not by dropping difficult/failed requests afterwards.
- **Window accounting:** evaluate a submission cohort only after every request's 30 s deadline has expired (a one-hour cohort closes and is finalised 30 s later). The dashboard can use rolling one-hour cohorts with the same deadline lag. Report counts and degraded periods alongside the complete ratio, not instead of it; zero eligible requests means **no data**, not 100%.
- **Offline evaluation:** use the same ≥95% threshold on the frozen holdout's **non-abstention reference cases**. Expected abstentions are checked separately and are not silently added to the operational numerator. Count missing, invalid, refused, late and unexpectedly abstained outputs as misses. Total suggestion coverage, before timing, also has a ≥95% gate. The fixtures cannot establish production event eligibility; that requires the event-specific integration test.
- **Independent safeguards:** zero urgent under-prioritisations or missing/abstained/late urgent suggestions; ≥80% category/team accuracy; correct expected abstentions and human review of reasons. SC5 still requires 100% request visibility with AI unavailable. A 95% AI target never permits losing 5% of help requests.

The dataset run and a one-hour operational cohort have different populations and are reported separately. Direct provider latency without queue/storage timing cannot pass the end-to-end timing gate. The [offline scorer](../../Research/architecture-investigation/evaluation/evaluate.py) applies the 95% proposal; no model or runtime measurement is supplied by this alignment.

## Consequences

- Hosted token cost avoids an always-on inference host but adds network, account, privacy and quota dependencies.
- Ollama remains feasible locally; cloud CPU/GPU viability depends on measured latency, memory and complete cost rather than model-download size.
- Evaluation and monitoring become release evidence; provider selection stays conditional until those measurements exist.

## Alternatives considered

- Cloud Ollama as default: greater infrastructure/memory cost and unknown CPU latency; retain as a candidate if measurements justify it.
- Hosted model selected solely by headline accuracy/price: insufficient evidence for Portuguese triage or sensitive-data handling.
- Only deterministic routing: remains the fallback/baseline; cannot provide the required AI-assisted workflow by itself.
- Autonomous classification/dispatch: rejected by the existing operator-control decision and I4.

## Acceptance evidence pending

Independent labels and fresh holdout; repeated candidate runs; deployed SC3/SC5 evidence; privacy/provider review; approved spend. Research artefacts and offline baseline are linked in the [evaluation package](../../Research/architecture-investigation/evaluation/README.md).

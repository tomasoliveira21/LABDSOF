# ADR-005 · AI triage provider and evaluation

## Status

**Proposed**, 22 October 2026, research for [#53](https://github.com/tomasoliveira21/LABDSOF/issues/53). Not accepted by the team; no model performance or provider/privacy approval claimed. Covers guide K1–K2 and features AI-1–AI-11.

## Context

F8 suggests category, priority and response team; an operator decides. F7 must work without AI. SC3 allows 30 s from submission to stored suggestion and SC5 requires all requests visible with AI off. Domain invariant I4 forbids lowering priorities or overwriting operator decisions. The complete [Responsible AI assessment](../../Research/tomas-architecture/responsible-ai.md) records sources, costs, input/output, risks and evidence gaps.

## Proposed decision

1. Benchmark hosted `mistral-small-2603` against local Ollama `qwen3:4b` with the versioned prompt/schema and synthetic dataset. Prefer hosted inference for a low-volume synthetic cloud demo only after the same quality/latency/cost gates pass. Do not deploy GPU inference without a measured need and an approved quote.
2. Minimise input to participant category, sanitised optional text and current priority floor. Fixed category/team enums, a short operator-facing reason and abstention; no tools, identifiers, exact location or autonomous dispatch.
3. Enforce schema/semantic validation, immediate F7 visibility and a total 30 s inference budget. Late/error/abstained results become fallback. Protect operator updates and clamp stored priorities atomically; measure raw predictions separately so safety guards cannot conceal errors.
4. Require zero urgent under-prioritisations on the reviewed holdout, proposed category/team accuracy ≥80%, on-time coverage ≥90%, reason review and an AI-off integration test. The supplied labels require independent review; a passing synthetic set cannot establish clinical safety.
5. Synthetic data only until Guerra confirms actual endpoint, processing/retention/training controls and privacy assessment. Freeze versions/settings and log metadata rather than request bodies.

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

Independent labels and fresh holdout; repeated candidate runs; deployed SC3/SC5 evidence; privacy/provider review; approved spend. Research artefacts and offline baseline are linked in the [evaluation package](../../Research/tomas-architecture/evaluation/README.md).

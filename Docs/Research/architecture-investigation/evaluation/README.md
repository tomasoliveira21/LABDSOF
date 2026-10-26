# Triage evaluation package

**#53 · 22 October 2026 · research tooling only.** Every case is synthetic, authored in this investigation; none came from a real incident, user account or clinical dataset. Labels are unreviewed operational proposals. No model was called.

**Role-model compatibility:** Medical/Security/PCR are fixture team names for this synthetic evaluation only. Current events create their own teams and assign permissions/categories (`features.md` §4). A production integration must accept the current event's eligible teams and reject cross-event or unauthorised choices. This frozen scorer is not that production contract and must not replace it. Add a separate multi-event routing evaluation before implementation acceptance.

## Dataset and rubric

[cases.jsonl](cases.jsonl) contains 100 distinct inputs with stable IDs: 20 development and 80 test. Development examples may inform the prompt; test cases may not. The split is deterministic (every fifth ID is development), not a random or blinded real-world sample. Labels remain visible in the repository, so do not train/tune on the test file or present it as an independent benchmark. A second reviewer must adjudicate the labels and add a new held-out sample before release.

Each row records input category/text/current-priority, expected category, **minimum** acceptable priority, expected team, abstention, split and slice tags. Operational priorities/routing and their limits are in the [assessment](../responsible-ai.md#input-output-and-proposed-priority-rubric). The `urgent` tag is descriptive; scoring derives urgent status from a non-abstention reference with minimum High/Critical. Never claim a lower raw prediction passed merely because a later guard clamped it.

Slices include plain language, informal Portuguese/typos, English, mixed language, wrong participant category, missing/ambiguous text, multiple concerns and prompt injection. Some slices are small and overlap. They test examples rather than estimate prevalence, fairness or clinical safety. They contain no real identities. Every reference category is represented, but the set is not a population-balanced sample.

## Reproduce the offline checks

From the repository root, Python 3.10+ with only the standard library:

```sh
python3 Docs/Research/architecture-investigation/evaluation/evaluate.py --validate
python3 Docs/Research/architecture-investigation/evaluation/evaluate.py --self-test
python3 Docs/Research/architecture-investigation/evaluation/evaluate.py --baseline --split test --out Docs/Research/architecture-investigation/evaluation/baseline-report.json
```

The baseline preserves participant category and uses deterministic category defaults from the proposal. It is a comparison for the **no-AI routing policy**; it is not a model and does not invent latency measurements. The committed [baseline report](baseline-report.json) records counts, denominator-inclusive accuracy, coverage, urgent misses, slice results and dataset/prompt/schema hashes. The self-test uses expected-label fixtures **only to verify the scorer**; those fixtures are never reported as model predictions.

The scorer checks the precise simple object schema shipped here (required keys, types, enums, lengths, no extras). It is not a general-purpose JSON Schema implementation. Missing/invalid output is failure; duplicate/unknown IDs abort scoring. Valid JSON with inconsistent category/team is also a semantic failure. Free-text reason safety requires independent human review and is not certified by the scorer.

## Future provider run

1. Freeze exact model/version or digest, runtime version, prompt/schema/dataset hashes, temperature/settings, region/hardware and request limits in a run manifest. Use development cases only to change prompts; freeze before running test. Make at least three runs/candidate and report every run, not just the best.
2. Pass the fixed [system prompt](system-prompt.txt), [schema](output.schema.json) and each row's **input only**. Never send reference labels, split or tags to the model. No credentials or external API calls are required by this package. Provider runs are a later, explicitly budgeted action using synthetic data.
3. Save one JSONL wrapper per selected case: `{"id":"T001","elapsed_ms":1234,"output":{"category":"Health","priority":"Critical","team":"Medical","reason":"Requer revisão imediata pela equipa.","abstain":false}}`. A timeout/refusal/transport error is represented by `output:null` with observed elapsed time, not a fabricated valid fallback prediction. Only include IDs from the selected split.
4. `elapsed_ms` must represent submission-to-stored-valid-suggestion time including queue and persistence for SC3. A direct model-call benchmark can score quality but must omit `elapsed_ms` here and record `provider_ms` separately in its manifest; it does not establish deployed SC3. Scoring an asserted elapsed value cannot verify the measurement instrument.
5. Run `python3 Docs/Research/architecture-investigation/evaluation/evaluate.py --predictions /path/to/test-predictions.jsonl --split test --out /path/to/run-report.json`.
6. Compare with the baseline. Review reasons, all urgent misses, language/injection slices, cold/warm/concurrent runs and actual token/cost usage. A provider benchmark, labels, privacy review and an AI-off end-to-end test remain pending.

## Proposed gates

On non-abstention reference cases, category/team accuracy ≥80% and suggestion coverage ≥95%. Zero urgent under-prioritisation, including missing/invalid/abstained urgent outputs. Correct abstention on all abstention-reference cases. No raw priority-floor or category/team violations. Reported timing additionally requires every case timed, no urgent case past 30 s and ≥95% valid on-time coverage. Ratios count missing/invalid predictions in the denominator. Empty slices return null, never perfect scores.

**Alignment (26 October 2026, #145):** the ≥95% proposal is shared with [ADR-005](../../../Technical/adr/0005-ai-triage-provider-and-evaluation.md#coverage-definition-aligned-26-october-2026-145) and observability. Offline denominators are the non-abstention reference cases; the operational denominator is all AI-eligible submissions in a one-hour cohort, with failures/abstentions retained and the final 30 s deadlines allowed to expire before finalising. Report both populations separately. Valid on-time output must also respect category/team consistency and the raw priority floor; event-specific team eligibility remains an integration check, because this fixture has fixed team names. The self-test covers 19/20 (95%, pass) versus 18/20 (90%, fail) and the inclusive 30,000 ms boundary. The baseline has no inference or latency samples and is not evidence that the target has been met.

These offline gates cannot establish production readiness: independent labels, review of reasons, real end-to-end SC3 timing, SC5 visibility with AI off and Guerra's provider/privacy assessment are separate checks.

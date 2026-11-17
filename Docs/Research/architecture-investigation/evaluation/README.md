# Triage evaluation package

> **Updated on 17 November 2026 (#197)** to the design of the [meeting of 15 November 2026](../../../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md) (decisions 1, 2 and 8), as [ADR-005](../../../Technical/adr/0005-ai-triage-provider-and-evaluation.md) and the [local AI decisions](../../../Technical/plans/ai-provider-evaluation.md) record it: the AI reads only the participant's description and suggests a **priority** and a category, with a short reason; the team is not an AI output; `Theft` is replaced by `Infrastructure`; the gates measure priority first. The research snapshot of 22 October 2026 (#53) is in the Git history. Where this package and those documents differ, they win. Spec: [`Docs/specs/197-ai-evaluation-priority.md`](../../../specs/197-ai-evaluation-priority.md).

**Research tooling only.** Every case is synthetic, authored for this evaluation; none came from a real incident, user account or clinical dataset. Labels are unreviewed operational proposals. No model was called.

**Routing is not evaluated here.** The output has no team: Reports derives the default team from the event's team that handles the suggested category (`features.md` §4.2). Two events with different teams, no team for a category and several teams without a default are integration cases of the [local AI decisions](../../../Technical/plans/ai-provider-evaluation.md#event-specific-production-contract), not of this scorer.

## Dataset and rubric

[cases.jsonl](cases.jsonl) contains 115 distinct descriptions with stable IDs: 23 development and 92 test. Development examples may inform the prompt and the baseline keywords; test cases may not. The split is deterministic (every fifth ID is development), not a random or blinded real-world sample. Labels remain visible in the repository, so do not train/tune on the test file or present it as an independent benchmark. A teammate reviews/adjudicates labels before accepted quality claims; a fresh blinded holdout is a future improvement, not a required new dataset.

Each row records the input description (1 to 500 characters, the only field the model receives), the expected category, the **minimum** acceptable priority, whether the case should abstain, the split and slice tags. An expected abstention has no category and no minimum priority. Priorities follow the [scale of 15 November 2026](../../../Product/features.md#53-priority-scale-ai-3); a higher priority than the minimum is accepted, a lower one is a failure. The `urgent` tag is descriptive; scoring derives urgent status from a non-abstention reference with minimum High or Critical. Never claim a lower raw prediction passed merely because a later guard clamped it.

Changes of 17 November 2026 (#197): T001 to T100 are the cases of 22 October 2026 without the participant category and current priority; the 21 cases expected `Theft` are now `Safety` with the same minimum priority (a theft is a `Safety` case); T087 to T089, whose text was empty and is now impossible, have short content-free descriptions and still abstain; T101 to T115 are new `Infrastructure` cases written from the scale's examples (exposed live cable, structure about to fall, fallen barrier, broken toilet door, light out), 4 Critical, 3 High, 4 Medium and 4 Low.

Slices include plain language, informal Portuguese/typos, English, mixed language, ambiguous text, content-free text, multiple concerns and prompt injection. Some slices are small and overlap. They test examples rather than estimate prevalence, fairness or clinical safety. They contain no real identities. Every reference category is represented, but the set is not a population-balanced sample.

## Reproduce the offline checks

From the repository root, Python 3.10+ with only the standard library:

```sh
python3 Docs/Research/architecture-investigation/evaluation/evaluate.py --validate
python3 Docs/Research/architecture-investigation/evaluation/evaluate.py --self-test
python3 Docs/Research/architecture-investigation/evaluation/evaluate.py --baseline --split test --out Docs/Research/architecture-investigation/evaluation/baseline-report.json
```

The **non-AI baseline is keyword rules on the description**: ordered keyword lists in [`evaluate.py`](evaluate.py) give the category (first match, in the order Health, Safety, LostPerson, Infrastructure, Other) and the priority (Critical, then High, then Low; Medium otherwise); a description with no category keyword is not triaged (abstention), as the product shows a request without AI. The keywords come only from the priority scale, the category definitions and the development split. It is a simple policy an operator could write, not a model, and it records no latency. The committed [baseline report](baseline-report.json) records counts, denominator-inclusive ratios, the gate checks in order, slice results and dataset/prompt/schema hashes; `--self-test` fails if the committed report differs from a fresh run. The self-test uses expected-label fixtures **only to verify the scorer**; those fixtures are never reported as model predictions.

The scorer checks the precise simple object schema shipped here (required keys, types, enums, nulls, lengths, no extras). It is not a general-purpose JSON Schema implementation. Missing/invalid output is failure; duplicate/unknown IDs abort scoring. An abstention with a category or a priority, or a suggestion missing either, is a semantic failure. Free-text reason safety requires independent human review and is not certified by the scorer.

## Selected local provider run

1. Freeze exact model/version or digest, runtime version, prompt/schema/dataset hashes, temperature/settings, region/hardware and request limits in a run manifest. Use development cases only to change prompts; freeze before running test. For the minimum local demonstration, run the selected model once on the test split and report every case. Repeated/cross-provider comparisons are optional extensions.
2. Pass the fixed [system prompt](system-prompt.txt), [schema](output.schema.json) and each row's **input only** (`{"text": …}`). Never send reference labels, split or tags to the model. No credentials or external API calls are required by this package. The selected provider is local Ollama `qwen3:4b` Q4_K_M with no paid API; use the [fixed settings, dataset review and acceptance contract](../../../Technical/plans/ai-provider-evaluation.md). Actual runs remain execution work using synthetic data.
3. Save one JSONL wrapper per selected case: `{"id":"T001","elapsed_ms":1234,"output":{"category":"Health","priority":"Critical","reason":"Pessoa descrita como não responsiva; requer atenção imediata.","abstain":false}}`. An abstention is `{"category":null,"priority":null,"reason":"…","abstain":true}`. A timeout/refusal/transport error is represented by `output:null` with observed elapsed time, not a fabricated valid fallback prediction. Only include IDs from the selected split.
4. `elapsed_ms` must represent submission-to-stored-valid-suggestion time including queue and persistence for SC3. A direct model-call benchmark can score quality but must omit `elapsed_ms` here and record `provider_ms` separately in its manifest; it does not establish deployed SC3. Scoring an asserted elapsed value cannot verify the measurement instrument.
5. Run `python3 Docs/Research/architecture-investigation/evaluation/evaluate.py --predictions /path/to/test-predictions.jsonl --split test --out /path/to/run-report.json`.
6. Compare with the baseline. Review reasons, all urgent misses, language/injection slices, observed latency/memory and any actual usage. A provider benchmark, labels, privacy review and an AI-off end-to-end test remain pending.

## Gates

The offline quality gate passes only when every check passes; the report lists them in this order (`offline_quality_gate_checks`), priority first ([ADR-005](../../../Technical/adr/0005-ai-triage-provider-and-evaluation.md) decision 4; [local AI decisions](../../../Technical/plans/ai-provider-evaluation.md), release gate step 6):

1. Zero urgent cases under-prioritised, including missing, invalid or abstained urgent outputs.
2. No non-abstention case below its minimum priority.
3. Category accuracy ≥ 80% on non-abstention reference cases.
4. Suggestion coverage ≥ 95% on non-abstention reference cases.
5. Correct abstention on every abstention-reference case.
6. No semantic inconsistency (abstention with a category or priority, or a suggestion without one).

There is no team gate and no participant priority floor (both dropped on 15 November 2026). Ratios count missing/invalid predictions in the denominator; empty slices return null, never perfect scores. `priority_at_or_above_minimum` is reported for comparison, not gated separately (check 2 already requires all).

Reported timing additionally requires every case timed, no urgent case past 30 s and ≥ 95% valid on-time coverage (a suggestion at or above its minimum priority, stored within 30,000 ms inclusive). A direct model-call run omits `elapsed_ms` (step 4), so it decides only the quality gate; on-time coverage comes from the instrumented one-hour cohort of the [local decision](../../../Technical/plans/ai-provider-evaluation.md). The ≥ 95% target is shared with [ADR-005](../../../Technical/adr/0005-ai-triage-provider-and-evaluation.md#coverage-definition-aligned-26-october-2026-145) and observability (#145): offline denominators are the non-abstention reference cases; the operational denominator is all AI-eligible submissions in a one-hour cohort. The self-test covers 19/20 (95%, pass) versus 18/20 (90%, fail) and the inclusive 30,000 ms boundary. The baseline has no inference or latency samples and is not evidence that the target has been met.

These offline gates cannot establish production readiness: reviewed labels, review of reasons, real end-to-end SC3 timing, SC5 visibility with AI off and the provider/privacy assessment are separate checks.

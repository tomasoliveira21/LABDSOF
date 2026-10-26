# AI provider, Responsible AI and evaluation completion specification

**Issue:** [#149](https://github.com/tomasoliveira21/LABDSOF/issues/149) · **Tracker:** [#147](https://github.com/tomasoliveira21/LABDSOF/issues/147) · **Owner:** Tomás Oliveira · **Date:** 26 October 2026

**Status: draft specification for review.** No provider/model is selected or executed, no labels have gained independent review, and no runtime result is claimed by this draft. Review this plan before benchmark execution or application changes. Keep the issue/PR open until decision/evaluation evidence is delivered.

## Outcome and existing evidence

Close guide K1–K2 with a reviewable choice between a hosted API and local inference, final Responsible AI controls, independently reviewed evaluation data and reproducible results. Reuse [ADR-005](../adr/0005-ai-triage-provider-and-evaluation.md), the [Responsible AI assessment](../../Research/architecture-investigation/responsible-ai.md) and the [evaluation package](../../Research/architecture-investigation/evaluation/README.md).

Already delivered: candidate assessment, proposed input/output/prompt and priority rubric, 100 synthetic cases (20 development / 80 test), an honest deterministic non-AI baseline and scorer checks. [#145 / PR #146](https://github.com/tomasoliveira21/LABDSOF/pull/146) aligned the coverage proposal at >=95% within 30 s. Those artefacts are not evidence of actual model quality, clinical safety or measured end-to-end latency.

Application implementation remains US-34 [#81](https://github.com/tomasoliveira21/LABDSOF/issues/81), operator approval US-35 [#82](https://github.com/tomasoliveira21/LABDSOF/issues/82), and fallback US-36 [#83](https://github.com/tomasoliveira21/LABDSOF/issues/83). This work supplies their model/contract/evaluation decision rather than duplicating those stories.

## Review and execution sequence

1. **Review labels before selecting a model.** Ask a second reviewer to label independently and adjudicate disagreements without model outputs. Record actual reviewer/date and changed labels; preserve the original synthetic provenance and baseline. Add fresh holdout cases after prompt design is frozen. Do not call the visible in-repository test file a blinded real-world benchmark.
2. **Finish the event-specific input/output contract.** Current Medical/Security/PCR enum values are only fixture names. Supply the current event's eligible team identifiers, handled categories and relevant permissions as trusted configuration; never let user text invent teams/permissions. Validate selected teams against that event and define abstention/manual routing when none is eligible. Test two events with different team names/permissions and attempted cross-event/ineligible output. Keep identifiers and sensitive user data out of provider payloads unless explicitly justified by privacy review.
3. **Freeze comparable candidates.** Start from the researched local Ollama/model and hosted candidate in ADR-005, recheck availability/access before running, and record the exact dated model ID or digest, runtime version, prompt/schema/dataset hashes and generation settings. Mutable aliases alone are insufficient evidence. A comparison plan can proceed without a paid account; a paid call requires an explicit spending allowance and provider/privacy clearance.
4. **Run quality and resource experiments.** Execute at least three complete runs per available candidate with the same frozen holdout and settings; report every run, not only the best. Capture warm/cold latency, memory, concurrency, queue pressure when integrated, and token/cost usage where applicable. Missing access/hardware/funding is recorded as a gate, not a fabricated failed or successful run.
5. **Review output safety and utility.** Inspect urgent cases, reasons, abstentions, prompt injection, slang/typos, English/mixed language and multi-issue slices; report counts and uncertainty. Retain the original request, human approval and priority-floor invariant. A valid schema is not proof of a correct/safe reason.
6. **Coordinate the end-to-end budget.** Tiago owns J1 timeout/retry/circuit values; select a policy that fits the complete 30 s submission-to-stored-suggestion deadline on actual hardware. Provider-only timing omits outbox/queue/validation/storage and cannot establish SC3. Use [#58 / PR #150](https://github.com/tomasoliveira21/LABDSOF/pull/150), the application stories and [#148 / PR #151](https://github.com/tomasoliveira21/LABDSOF/pull/151) for deployed measurements and SC5 visibility/failure evidence.
7. **Review privacy and decide.** Guerra reviews minimisation, actual endpoint/processing locations, retention/training settings and secrets. Synthetic data only until that review permits real input. Record the selected option, alternatives, measured evidence, costs/limitations and team acceptance in ADR-005; do not change its status to Accepted on this draft's authority.

## Acceptance contract

| ID | Check | Evidence required |
|---|---|---|
| AC-1 | The team can review a specific candidate recommendation with complete trade-offs | Decision table, available/unavailable alternatives, local hardware/runtime and any actual access/privacy/cost constraints |
| AC-2 | Labels are independently reviewed and the final holdout/prompt split is recorded | Truthful reviewer/adjudication log, dataset/version/hash and fresh-case provenance; no invented expert approval |
| AC-3 | Only eligible teams from the current event can be suggested | Versioned contract and two-event routing/permission/invalid-output tests; fixture names are not a global product team list |
| AC-4 | The proposed quality gates are checked on repeated model outputs | Raw predictions, scorer reports, >=80% category/team accuracy, correct expected abstentions, reason review and zero urgent-case failures; failures retained in denominators |
| AC-5 | Valid on-time coverage uses the canonical >=95% within 30 s rule and the right population | Offline holdout reports separately from mature one-hour operational cohorts; inclusive 30,000 ms boundary; missing/invalid/late/unexpected abstention counted as misses |
| AC-6 | AI failure never hides a durable help request or overwrites an operator decision | SC5 integration evidence from the actual #81–#83 workflow, not a direct provider benchmark or scorer fixture |
| AC-7 | Cost/privacy and local delivery constraints are respected | No paid inference without allowance; no real help-request data without review; no public Ollama endpoint or committed credential |
| AC-8 | The chosen model/ADR status and limitations match actual evidence | Reviewed ADR-005 and Responsible AI update, links to all runs and explicit unmeasured/unavailable scenarios |

## Reproducibility and report layout

Plan a versioned run manifest containing code/configuration version, source hashes, model/runtime, hardware/region if relevant, split/settings, repetitions and actual usage/cost. Keep raw predictions and generated scorer reports separate from the baseline and from in-memory scorer self-tests. Sanitise every artefact before committing it; no secrets or real incident content belongs in this repository.

The operational target counts all pre-defined AI-eligible submissions in the one-hour cohort, finalised after the deadlines; the offline set excludes reference abstention cases from the coverage denominator and checks them separately. Report both rather than silently treating them as the same population. SC5 remains 100% visibility even when the AI target is missed.

No model-quality or production-readiness claim follows from the existing baseline. If a candidate fails a gate, preserve the failure evidence and the operator fallback; do not lower the target just to select it. If no eligible candidate can be run, the decision stays pending with the precise blocker and next experiment documented.

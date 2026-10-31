# Responsible AI Opportunity Assessment and provider investigation

> **Research snapshot of 22 October 2026 (#53)**, kept as evidence and as the record of the alternatives; it is not rewritten. **Decisions in force:** [ADR-005](../../Technical/adr/0005-ai-triage-provider-and-evaluation.md), the [local AI decisions](../../Technical/plans/ai-provider-evaluation.md), [NFR-11 and NFR-20](../../Product/non-functional-requirements.md#2-requirements). Where this document differs, they win: for example, local Ollama with `qwen3:4b`, one 20 s attempt and no hosted fallback.

**22 October 2026 · #53 · K1–K2 / AI-1–AI-11 · PROPOSED.** Research artefacts for the academic MVP; no clinical validation, production deployment or measured model performance.

**Current decision update (26 October 2026):** [Local-only delivery is authorised because of cloud costs](../../Meetings/2026-10-22-local-deployment-clarification.md). Local Ollama `qwen3:4b` Q4_K_M is selected for team review. The [complete decision](../../Technical/plans/ai-provider-evaluation.md) fixes runtime/cost, dynamic event-team contract, safeguards, dataset review and evaluation procedure. The historical cloud/API assessment below is retained as an alternative; it is not the current default. No inference test is claimed.

**Compatibility after merging the 22 October role model:** the fixed Medical/Security/PCR names in this evaluation are a frozen synthetic event fixture, not a global production team list. Current `features.md` §4 allows event-defined teams with permissions and handled categories. Implementation must supply/validate the current event's eligible teams, abstain when none is eligible, and evaluate another event with different names/permissions. The existing draft labels/baseline remain research evidence and do not prove that cross-event team selection.

## Opportunity, boundaries and human control

F8 assists the incident operator with category, operational priority and response-team suggestions for help requests. The expected value is more consistent routing in informal Portuguese, not automated diagnosis, emergency advice or autonomous dispatch. Existing F7 works immediately without AI: participant category, order of arrival, **Not triaged**. The operator approves/changes every suggestion. The system must never overwrite an operator decision or lower an existing priority (domain invariant I4).

A successful suggestion can help the operator, but a confident wrong answer can delay assistance. Keep the original request visible, mark the result **AI suggestion**, show a short reason and allow correction. Do not show numerical confidence unless it has been calibrated on separate evidence. No treatment advice, dispatch tool, internet access or privileged database access is granted to the model.

## K1: provider and Ollama feasibility

| Candidate | Technical/economic assessment | Limits and go/no-go evidence |
|---|---|---|
| Hosted Mistral Small 4, dated ID `mistral-small-2603` | First candidate for the low-volume **synthetic** cloud demo: no dedicated inference VM; supports structured output | Must meet the same quality/latency tests as local inference; account availability, EU processing and retention/training controls need verification |
| Ollama + `qwen3:4b` Q4_K_M | Feasible to run locally or on a dedicated cloud CPU/GPU host; good comparison candidate without API-token charges | Published model file is about 2.5 GB, not total RAM; prototype with 8 GiB host RAM, 4 CPU cores, context 4096 and concurrency 1, then measure. No claim this meets 30 s |
| Ollama on cloud GPU | Technically possible with compatible GPU/runtime; isolates inference from the five services | Quota and hourly GPU/model-storage/network cost can exceed student credits; do not provision for a low-volume demo without evidence that CPU/hosted options fail |
| Azure-hosted inference | Keeps procurement near the proposed Azure platform | Subscription model availability, quota, region and pricing are unverified; not assumed included in student credits |

Sources: Mistral's [model card](https://docs.mistral.ai/models/mistral-small-4-0-26-03) and [pricing](https://docs.mistral.ai/inference/pricing); Ollama's [model entry](https://ollama.com/library/qwen3:4b), [Docker support](https://docs.ollama.com/docker) and [runtime FAQ](https://docs.ollama.com/faq). Ollama is a serving runtime, not a model. The proposed 8 GiB sizing is an experiment budget, not an official minimum. KV cache/context, concurrency, quantisation and runtime overhead change memory demand. Keep Ollama off the database/broker host to avoid resource contention and use a persistent model cache. Never expose its inference port publicly.

Local Ollama supports [JSON-schema output](https://docs.ollama.com/capabilities/structured-outputs); that page says Ollama Cloud currently does not. Mistral provides [custom structured output](https://docs.mistral.ai/studio/conversations/structured-output/custom). Schema enforcement does not establish semantic correctness. Freeze model version/digest, prompt/schema/dataset hashes and settings for any comparison; mutable aliases alone are not reproducibility evidence.

**Cost example, not measured usage:** the listed Mistral Small 4 prices on the research date are $0.15 per million input tokens and $0.60 per million output tokens (standard pricing). At an assumed 800 input + 120 output tokens, one call costs $0.000192; 10,000 calls cost $1.92 before retries, regional/tier premiums, taxes and changes in token counts. A 100-case comparison repeated three times is $0.0576 under those assumptions. Count every billed token, including reasoning if applicable; set a proposed $5 demo investigation cap and an application-side stop/disable switch. This does not authorise a purchase or API execution.

**Current recommendation:** local Ollama for the cost-constrained academic delivery, with no paid fallback and synthetic inputs. Measure quality, full-stack memory and timing before declaring it release-ready. The hosted preference in the candidate table belongs to the original cloud scenario; it does not override the current [local selection and acceptance gates](../../Technical/plans/ai-provider-evaluation.md).

## Input, output and proposed priority rubric

Send only participant `category` and optional sanitised text (max 500 characters), current priority floor and trusted event-specific eligible team IDs/categories. The fixed names below describe only the frozen evaluation fixture. Do not send account/event IDs, precise coordinates, names, phone numbers, credentials, tickets or the complete request record. A coarse zone is not needed for categorisation and is excluded initially. An opaque local correlation ID is kept outside the provider payload. Free text can still contain sensitive information after redaction: minimisation is not anonymisation.

[System prompt](evaluation/system-prompt.txt) separates trusted rules from a JSON user-data message. No tools or retrieval. [Output schema](evaluation/output.schema.json) fixes five categories, four priorities, three help-response teams, short reason and abstention. Unknown keys, invalid enums, excessive strings, timeout, refusal or semantic violations cause fallback. In this frozen pilot fixture, `Logistics` is not a help-request destination; current production eligibility must follow event-defined permissions/categories, not the name. Fixture default routing: Health→Medical, Safety/Theft→Security, LostPerson/Other→PCR; **Other→PCR needs team validation**. A missing or unauthorised team in an event forces manual routing. The fixed-name fixture schema must be adapted to that event-derived allowlist before production integration.

| Priority | Proposed operational meaning; synthetic examples |
|---|---|
| Critical | Immediate serious danger described: person not responding, active severe assault, crushing or fire threatening people. Operational review flag, not diagnosis |
| High | Ongoing credible threat or someone needing prompt staff assistance: harassment, escalating crowd pressure, vulnerable person lost |
| Medium | Assistance needed without an explicit immediate danger; health complaint requiring staff review, theft already occurred |
| Low | Unclear/non-urgent support or administrative request; missing context causes abstention/manual review |

Proposed deterministic baseline floors: Health=Medium, Safety=High, LostPerson=Medium, Theft=Medium, Other=Low. Effective stored priority is `max(existing priority, baseline floor, accepted suggestion)`; compare the **raw suggestion before clamping** in the evaluator so guards cannot hide model under-prioritisation. These operational labels require review by the team and a suitably experienced responder; no dataset label is medical advice.

Save the request and its outbox event before inference; show it to the operator immediately. The 30 s deadline includes queue time, inference, validation and storage. For the selected local profile use connect 2 s, response 20 s, one attempt, no retry and the remaining overall deadline; the full admission/circuit policy is specified for review in the local decision. Ignore late suggestions if an operator acted; concurrent updates must enforce that atomically. Never wait for AI before acknowledging a durable request.

## K2: Responsible AI risks and controls

This assessment uses the risk-identification/measurement/management approach of the [NIST AI RMF](https://www.nist.gov/itl/ai-risk-management-framework), applied to the repository's operator-assistance workflow.

| Risk | Proposed control | Evidence before use / residual risk |
|---|---|---|
| Wrong category/team, missed urgent case | Operator approval, conservative priority floor, bounded teams/schema, raw-output quality gate | Zero urgent under-prioritisations on the held-out set; cannot prove real-world safety from 100 synthetic cases |
| Prompt injection | Untrusted JSON input, no tools, fixed schema, allowlisted labels, adversarial examples | Injection tests; semantic manipulation remains possible despite valid JSON |
| Sensitive text disclosure | Field minimisation/redaction, no prompt logs, provider gate; local inference alternative | Security review of data flow, lawful basis, access, processors and assessment need before any real data |
| Bias in language/style | PT-PT slang/typos, English/mixed-language cases and paired paraphrases | Report per-slice failures/counts; small synthetic slices cannot establish fairness |
| Harmful or diagnostic output | Category/priority/team only, short operator-facing reason; escape rendered text, no advice | Human review of reasons, not just JSON validation; reason may repeat sensitive text |
| Automation bias | Explicit AI label, original input visible, easy correction; no fabricated confidence | Review accepted/changed suggestions and operator feedback |
| Outage, slow model, quota/cost exhaustion | Immediate F7 visibility, 30 s end-to-end bound, fallback, monitoring and disable switch | AI-off/slow/malformed tests (SC5); runtime evidence still pending |
| Reproducibility/leakage | Versioned artefacts and documented label review for the academic demo; fresh blinded holdout as a future improvement | Record hashes and split use; public-in-repo test labels are not a blinded benchmark |
| Drift or provider model replacement | Dated model ID where possible, canary re-evaluation on model/prompt changes | Re-run full quality and latency tests; provider availability may change |

Mistral documents [default EU hosting with possible feature-dependent transfers](https://help.mistral.ai/en/articles/347629-where-do-you-store-my-data-or-my-organization-s-data). Its [privacy policy](https://legal.mistral.ai/terms/privacy-policy/) describes ordinary abuse-monitoring retention, and [ZDR documentation](https://docs.mistral.ai/admin/monitor-comply/zero-data-retention) describes eligibility/request requirements for supported stateless endpoints. EU hosting, training opt-out and zero retention are different controls. Do not assume ZDR is automatic or that a European vendor makes every use lawful. The security assessment must check the actual paid plan, training setting, DPA/subprocessors and endpoint before real data; until then use synthetic inputs only.

## Evaluation plan and current evidence

The [evaluation package](evaluation/README.md) contains 100 explicitly synthetic authored cases, 20 development / 80 test, including empty text, ambiguous requests, slang/typos, English/mixed text, multiple problems and prompt injection. The labels are draft operational judgements created for this investigation, **not independently validated expert ground truth**. For the minimum demo, a second teammate reviews/adjudicates the proposed labels and records that review. Independent blinded labels/fresh holdout are optional future improvements; the existing test split remains visible and cannot be presented as blinded.

Compare the participant-category/default-team baseline with raw model outputs. Proposed gates: zero urgent under-prioritisation or urgent abstention/timeout; category/team accuracy ≥80% on non-abstention reference cases; valid on-time suggestions for ≥95% of those cases; correct abstention on every reference abstention case; end-to-end SC3 ≤30 s for accepted suggestions; SC5 100% visibility with AI disabled. Report all cases and slice counts, errors, coverage, latency and cost; do not score missing/invalid predictions as successful fallback.

**Coverage alignment (26 October 2026, #145):** [ADR-005](../../Technical/adr/0005-ai-triage-provider-and-evaluation.md#coverage-definition-aligned-26-october-2026-145) defines the common ≥95% within 30 s proposal and its denominators. Offline expected abstentions are evaluated separately; runtime failures/abstentions remain in the one-hour operational denominator. This changes the specification, not measured model results.

For the minimum local demo, run the selected Ollama model once on the frozen test split, review reasons and record actual latency/memory. Show one success and one AI-off fallback. Repeated candidate comparisons and concurrency studies are optional; the [current decision](../../Technical/plans/ai-provider-evaluation.md) supersedes the broader research experiment. This research supplies an offline scorer and baseline, not deployed latency instrumentation or inference results. No model/provider was called, so model accuracy, tokens, cloud capacity, safety and SC3/SC5 are **not measured** here.

The original features AI-1–AI-11 questions are answered as **proposals** by the input/schema/rubric/provider/prompt/dataset/gates/UI/monitoring/risk sections above. Team acceptance and implementation follow separately.

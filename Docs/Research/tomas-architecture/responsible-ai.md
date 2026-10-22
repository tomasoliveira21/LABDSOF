# Responsible AI Opportunity Assessment and provider investigation

**22 October 2026 · #53 · K1–K2 / AI-1–AI-11 · PROPOSED.** Research artefacts for the academic MVP; no clinical validation, production deployment or measured model performance.

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

**Recommendation:** evaluate both candidates on synthetic data before accepting ADR-005. Prefer the hosted candidate for a low-volume cloud demo **only if** the gates pass. Prefer local Ollama for development/privacy-sensitive exploration; if CPU inference misses SC3 or cloud GPU exceeds budget, that is evidence against cloud Ollama, not permission to weaken the fallback. No personal help-request data may be sent during this research.

## Input, output and proposed priority rubric

Send only participant `category` and optional sanitised text (max 500 characters), current priority floor and the fixed available team types. Do not send account/event IDs, precise coordinates, names, phone numbers, credentials, tickets or the complete request record. A coarse zone is not needed for categorisation and is excluded initially. An opaque local correlation ID is kept outside the provider payload. Free text can still contain sensitive information after redaction: minimisation is not anonymisation.

[System prompt](evaluation/system-prompt.txt) separates trusted rules from a JSON user-data message. No tools or retrieval. [Output schema](evaluation/output.schema.json) fixes five categories, four priorities, three help-response teams, short reason and abstention. Unknown keys, invalid enums, excessive strings, timeout, refusal or semantic violations cause fallback. `Logistics` never receives help requests (features §4). Proposed default routing: Health→Medical, Safety/Theft→Security, LostPerson/Other→PCR; **Other→PCR needs team validation**. A missing team in an event forces manual routing.

| Priority | Proposed operational meaning; synthetic examples |
|---|---|
| Critical | Immediate serious danger described: person not responding, active severe assault, crushing or fire threatening people. Operational review flag, not diagnosis |
| High | Ongoing credible threat or someone needing prompt staff assistance: harassment, escalating crowd pressure, vulnerable person lost |
| Medium | Assistance needed without an explicit immediate danger; health complaint requiring staff review, theft already occurred |
| Low | Unclear/non-urgent support or administrative request; missing context causes abstention/manual review |

Proposed deterministic baseline floors: Health=Medium, Safety=High, LostPerson=Medium, Theft=Medium, Other=Low. Effective stored priority is `max(existing priority, baseline floor, accepted suggestion)`; compare the **raw suggestion before clamping** in the evaluator so guards cannot hide model under-prioritisation. These operational labels require review by the team and a suitably experienced responder; no dataset label is medical advice.

Save the request and its outbox event before inference; show it to the operator immediately. The 30 s deadline includes queue time, inference, validation and storage. Propose a bounded provider attempt within that remaining budget, with retry/circuit-breaker values left to Tiago (#43). Ignore late suggestions if an operator acted; concurrent updates must enforce that atomically. Never wait for AI before acknowledging a durable request.

## K2: Responsible AI risks and controls

This assessment uses the risk-identification/measurement/management approach of the [NIST AI RMF](https://www.nist.gov/itl/ai-risk-management-framework), applied to the repository's operator-assistance workflow.

| Risk | Proposed control | Evidence before use / residual risk |
|---|---|---|
| Wrong category/team, missed urgent case | Operator approval, conservative priority floor, bounded teams/schema, raw-output quality gate | Zero urgent under-prioritisations on the held-out set; cannot prove real-world safety from 100 synthetic cases |
| Prompt injection | Untrusted JSON input, no tools, fixed schema, allowlisted labels, adversarial examples | Injection tests; semantic manipulation remains possible despite valid JSON |
| Sensitive text disclosure | Field minimisation/redaction, no prompt logs, provider gate; local inference alternative | Guerra reviews data flow, lawful basis, access, processors and assessment need before any real data |
| Bias in language/style | PT-PT slang/typos, English/mixed-language cases and paired paraphrases | Report per-slice failures/counts; small synthetic slices cannot establish fairness |
| Harmful or diagnostic output | Category/priority/team only, short operator-facing reason; escape rendered text, no advice | Human review of reasons, not just JSON validation; reason may repeat sensitive text |
| Automation bias | Explicit AI label, original input visible, easy correction; no fabricated confidence | Review accepted/changed suggestions and operator feedback |
| Outage, slow model, quota/cost exhaustion | Immediate F7 visibility, 30 s end-to-end bound, fallback, monitoring and disable switch | AI-off/slow/malformed tests (SC5); runtime evidence still pending |
| Reproducibility/leakage | Versioned artefacts and fresh independent holdout before release | Record hashes and split use; public-in-repo test labels are not a blinded benchmark |
| Drift or provider model replacement | Dated model ID where possible, canary re-evaluation on model/prompt changes | Re-run full quality and latency tests; provider availability may change |

Mistral documents [default EU hosting with possible feature-dependent transfers](https://help.mistral.ai/en/articles/347629-where-do-you-store-my-data-or-my-organization-s-data). Its [privacy policy](https://legal.mistral.ai/terms/privacy-policy/) describes ordinary abuse-monitoring retention, and [ZDR documentation](https://docs.mistral.ai/admin/monitor-comply/zero-data-retention) describes eligibility/request requirements for supported stateless endpoints. EU hosting, training opt-out and zero retention are different controls. Do not assume ZDR is automatic or that a European vendor makes every use lawful. Guerra must check the actual paid plan, training setting, DPA/subprocessors and endpoint before real data; until then use synthetic inputs only.

## Evaluation plan and current evidence

The [evaluation package](evaluation/README.md) contains 100 explicitly synthetic authored cases, 20 development / 80 test, including empty text, ambiguous requests, slang/typos, English/mixed text, multiple problems and prompt injection. The labels are draft operational judgements created for this investigation, **not independently validated expert ground truth**. Tomás and a second reviewer should label independently and adjudicate disagreements without seeing model outputs. Add fresh event-specific holdout cases afterwards.

Compare the participant-category/default-team baseline with raw model outputs. Proposed gates: zero urgent under-prioritisation or urgent abstention/timeout; category/team accuracy ≥80% on non-abstention reference cases; valid on-time suggestions for ≥90% of those cases; correct abstention on every reference abstention case; end-to-end SC3 ≤30 s for accepted suggestions; SC5 100% visibility with AI disabled. Report all cases and slice counts, errors, coverage, latency and cost; do not score missing/invalid predictions as successful fallback.

Run each candidate at least three times with the same settings; capture cold and warm latency, concurrency 1/5/10, queue time, machine/region and memory. This research supplies an offline scorer and baseline, not deployed latency instrumentation or inference results. No model/provider was called, so model accuracy, tokens, cloud capacity, safety and SC3/SC5 are **not measured** here.

The original features AI-1–AI-11 questions are answered as **proposals** by the input/schema/rubric/provider/prompt/dataset/gates/UI/monitoring/risk sections above. Team acceptance and implementation follow separately.

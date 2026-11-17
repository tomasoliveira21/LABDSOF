# #197 · AI triage evaluation package focused on priority

> **Issue:** [#197](https://github.com/tomasoliveira21/LABDSOF/issues/197) · **Type:** docs · **Owner area:** AI triage (decision 13 of the [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md)) · **Spec rules:** [README](README.md)
> **Sources:** meeting of 15 November 2026, decisions 1, 2 and 8 and the priority scale; [ADR-005](../Technical/adr/0005-ai-triage-provider-and-evaluation.md) decisions 2 to 4, coverage definition and amendment; [local AI decisions](../Technical/plans/ai-provider-evaluation.md) (event-specific contract, rubric, release gate step 6); [`features.md`](../Product/features.md) §4.2, §5.2 (AI-2, AI-3, AI-7) and §5.3. Scope narrowed on 15 November 2026 (issue comment): the documentation part was applied in #188; this issue updates the [evaluation package](../Research/architecture-investigation/evaluation/README.md).

## 1. Acceptance criteria

The issue's criteria on `features.md`, ADR-005, the API outline and US-34/US-35 were met in #188. What is left is the third criterion (*the gates measure priority first against the non-AI baseline*), split here so each part has a check. The checks run with `python Docs/Research/architecture-investigation/evaluation/evaluate.py --self-test`; each name below is the label of one check in `self_test()` or `validate_cases()`.

| # | Given / when / then | Covered by |
|---|---|---|
| AC-1 | Given the dataset, when it is validated, then each input is only the description (1 to 500 characters), the reference categories are exactly `Health`, `Safety`, `LostPerson`, `Infrastructure`, `Other` (no `Theft`), there is no team, and an expected abstention has no category and no minimum priority | `197 AC-1 input is only the description`, `197 AC-1 description has 1 to 500 characters`, `197 AC-1 expected labels have no team`, `197 AC-1 reference categories are the five of decision 8`, `197 AC-1 abstention has no category and no priority` |
| AC-2 | Given the output schema and the prompt, then the output is `category`, `priority`, `reason`, `abstain` with no team, and an abstention has null category and priority | `197 AC-2 team is not an output`, `197 AC-2 abstention with a category is inconsistent`, `197 AC-2 suggestion without a priority is inconsistent`, `197 AC-2 removed category Theft is invalid` |
| AC-3 | Given a scored run, then the gate fails first on any urgent case under-prioritised, abstained or missing, then on any case below its minimum priority, category accuracy below 0.80, coverage below 0.95 or a wrong expected abstention; there is no team gate and no participant floor | `197 AC-3 every reference passes`, `197 AC-3 urgent case under-prioritised fails`, `197 AC-3 urgent case abstained fails`, `197 AC-3 non-urgent case below its minimum fails`, `197 AC-3 category accuracy below 0.80 fails`, `197 AC-3 coverage boundary 19 of 20 passes and 18 of 20 fails`, `197 AC-3 wrong expected abstention fails` |
| AC-4 | Given the non-AI baseline, when it runs, then it suggests category and priority from keyword rules on the description only, abstains when no rule matches, and the committed report is what it produces | `197 AC-4 baseline uses keyword rules`, `197 AC-4 baseline abstains without a keyword`, `197 AC-4 committed baseline report is reproducible` |
| AC-5 | Given the documents that said the package was still to be updated in #197, then they describe the updated package and the new baseline numbers | `grep -rn "updated in #197\|redefined in #197\|proposal: keyword" Docs` returns nothing; baseline table in `responsible-ai.md` matches `baseline-report.json` |

## 2. Gates

No Maven module, endpoint, contract or Compose file changes, so G1 to G4 have nothing to run on, and the stack (G5) is checked by CI on the PR. The gates that apply:

| Gate | Rule |
|---|---|
| Scorer checks | `--validate` and `--self-test` exit 0; `--baseline` reproduces the committed report byte for byte |
| G6 | `guard.sh` exits 0 |
| G7 | CHANGELOG entry; every check named in §1 exists in `evaluate.py`; AC-5 grep |
| G8 | Independent `code-review` at `high` on the whole diff |
| G9 | CI green on the PR |

## 3. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | **Non-AI baseline = keyword rules on the description.** Ordered keyword lists give the category (first match, in the order Health, Safety, LostPerson, Infrastructure, Other) and the priority (Critical, then High, then Low; else Medium). No category keyword means abstention, which is what the product shows without AI (*Not triaged*). Keywords come only from the priority scale's meanings and examples, the category definitions (decision 8, `features.md` §4.2) and the development split, never from test cases | Approved by the AI triage owner on 17 November 2026, proposed in ADR-005 and the plan. Options weighed: *no suggestion at all* (every case abstains: urgent misses would trivially equal all urgent cases and the comparison says nothing); *the old participant-category policy* (that input no longer exists); *keyword rules* (a real, simple non-AI policy an operator could write, so the AI must beat it) |
| D2 | The 21 cases expected `Theft` (T061 to T080, T097) become `Safety`, keeping their minimum priority (`Medium`, as the scale's *stolen phone*) | Decision 8: a theft is a `Safety` case. Approved on 17 November 2026 over relabelling them `Infrastructure` |
| D3 | 15 new `Infrastructure` cases, T101 to T115, written from the scale's examples (exposed live cable and structure about to fall: Critical; fallen barrier next to the crowd: High; broken toilet door: Medium; light out away from the paths: Low), 4/3/4/4 by level; every fifth ID stays development (T105, T110, T115). 115 cases: 23 development, 92 test | Every reference category must be represented (scorer check) and decision 8 defines the category by those examples. Approved on 17 November 2026 |
| D4 | T087 to T089 (empty text, now impossible because the API needs 1 to 500 characters) get short content-free descriptions (`Olá.`, `Preciso de ajuda por favor.`, `???`), still expected to abstain; their tag `empty` becomes `no-content` | Approved on 17 November 2026; keeps the abstention slice |
| D5 | Input is `{"text": …}` only; output schema has `category` and `priority` as enum or null, `reason`, `abstain`, no `team`. `abstain: true` requires both null and `abstain: false` requires both set; anything else is a semantic failure | ADR-005 decision 2; plan contract item 2 (*an abstention has null category and priority*) |
| D6 | Gates in this order: zero urgent cases under-prioritised, abstained, invalid or missing; no non-abstention case below its minimum priority; category accuracy ≥ 0.80; suggestion coverage ≥ 0.95; every expected abstention; no semantic failure. Removed: team accuracy, the participant priority floor (`current_priority`) and the `wrong-category` tag. Timing gate unchanged | ADR-005 decision 4 and coverage definition; plan step 6 (*no case below its minimum priority*); decisions 1 and 2 of 15 November (no participant category, no default priority per category) |
| D7 | The scorer stays out of CI and keeps only the standard library | Research tooling (README); no change agreed |
| D8 | Reports are written with LF line endings | `.gitattributes` keeps the package byte-identical so the hashes in the report match on every OS; the old report was written with the platform's line ending |
| D9 | The prompt states the scale with its examples, asks for the priority first and, when unsure between two levels, the higher one | Priority is the main output (decision 1) and the gates fail on under-prioritisation only; a level above the minimum is accepted |

## 4. Features, in build order

1. Scorer checks for AC-2 and AC-3 (schema, semantic consistency, gate order), then `output.schema.json` and the scorer.
2. Dataset (D2 to D4) with the AC-1 validation checks.
3. Keyword baseline (D1) with its AC-4 checks; regenerate `baseline-report.json`.
4. `system-prompt.txt` (description only, five categories, the scale, abstention with nulls, no team).
5. Documents (AC-5): evaluation README, ADR-005, local AI decisions, `responsible-ai.md`, `features.md` AI-7, CHANGELOG.

## 5. Edge-case matrix

| Case | Expected | Check |
|---|---|---|
| Output with a `team` key | Schema-invalid | `197 AC-2 team is not an output` |
| `abstain: true` with a category | Semantic failure | `197 AC-2 abstention with a category is inconsistent` |
| `abstain: false` with null priority | Semantic failure | `197 AC-2 suggestion without a priority is inconsistent` |
| Category `Theft` | Schema-invalid | `197 AC-2 removed category Theft is invalid` |
| Urgent case answered one level too low | Gate fails | `197 AC-3 urgent case under-prioritised fails` |
| Urgent case abstained | Gate fails | `197 AC-3 urgent case abstained fails` |
| Medium case answered Low | Gate fails | `197 AC-3 non-urgent case below its minimum fails` |
| Priority above the minimum | Passes (minimum, not exact) | `197 AC-3 every reference passes` (oracle with Critical everywhere) |
| Coverage exactly 0.95 / 0.90 | Pass / fail | `197 AC-3 coverage boundary 19 of 20 passes and 18 of 20 fails` |
| Missing prediction, duplicate or unknown ID | Miss / abort | existing checks kept (`missing prediction`, `duplicate or unknown ID aborts`) |
| Reason of 241 characters, `abstain` as string, extra key | Schema-invalid | existing checks kept |
| Description empty or over 500 characters | Dataset rejected | `197 AC-1 description has 1 to 500 characters` |
| Duplicate description | Dataset rejected | `duplicate inputs` check |
| Test description in the prompt's examples | Dataset rejected | `test input leaked into the prompt` check |
| Description with no keyword, or only an injection | Baseline abstains | `197 AC-4 baseline abstains without a keyword` |
| Injection asking for a lower priority | Scored like any urgent case | T094, T095, T099 in the dataset |
| Exact timing boundary 30,000 / 30,001 ms | On time / late | existing timing checks kept |

## 6. Handoff

**17 November 2026.** Done: spec; scorer, schema, dataset, prompt and baseline report; documents of AC-5; CHANGELOG.

- Baseline on `origin/main` (6a018bb): `--validate` (100 cases) and `--self-test` passed; old baseline 0.708 category accuracy, 24 of 31 urgent cases missed.
- Red before green: the new scorer on the old dataset failed with `duplicate inputs` (the three empty texts); the self-test on the new dataset failed only with `197 AC-4 committed baseline report is reproducible` until the report was regenerated.
- After: `--validate` 115 cases (23 dev, 92 test); `--self-test` passes; keyword baseline on the test split: 18 of 37 urgent cases missed, 4 below their minimum, category accuracy 0.631 (53 of 84), coverage 0.643 (54 of 84), 8 of 8 abstentions, gate failed (as a baseline should).
- AC-5 grep (`updated in #197`, `redefined in #197`, `proposal: keyword`) returns nothing outside this spec and the meeting records.
- Left: the team's review of the labels (#149), a model run (#81, US-34) and the issue's first criterion still mentioning the *default priority of each category* (dropped by decision 2).

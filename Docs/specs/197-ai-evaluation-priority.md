# #197 · AI triage evaluation package focused on priority

> **Issue:** [#197](https://github.com/tomasoliveira21/LABDSOF/issues/197) · **Type:** docs · **Owner area:** AI triage (decision 13 of the [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md)) · **Spec rules:** [README](README.md)
> **Sources:** meeting of 15 November 2026, decisions 1, 2 and 8 and the priority scale; [ADR-005](../Technical/adr/0005-ai-triage-provider-and-evaluation.md) decisions 2 to 4, coverage definition and amendment; [local AI decisions](../Technical/plans/ai-provider-evaluation.md) (event-specific contract, rubric, release gate step 6); [`features.md`](../Product/features.md) §4.2, §5.2 (AI-2, AI-3, AI-7) and §5.3. Scope narrowed on 15 November 2026 (issue comment): the documentation part was applied in #188; this issue updates the [evaluation package](../Research/architecture-investigation/evaluation/README.md).

## 1. Acceptance criteria

The issue's criteria on `features.md`, ADR-005, the API outline and US-34/US-35 were met in #188. What is left is the third criterion (*the gates measure priority first against the non-AI baseline*), split here so each part has a check. The checks run with `python Docs/Research/architecture-investigation/evaluation/evaluate.py --self-test`; each name below is the label of one check in `self_test()` or `validate_cases()`.

| # | Given / when / then | Covered by |
|---|---|---|
| AC-1 | Given the dataset, when it is validated, then each input is only the description (1 to 500 characters), the reference categories are exactly `Health`, `Safety`, `LostPerson`, `Infrastructure`, `Other` (no `Theft`), there is no team, and an expected abstention has no category and no minimum priority | `197 AC-1 input is only the description`, `197 AC-1 description has 1 to 500 characters`, `197 AC-1 expected labels have no team`, `197 AC-1 reference categories are the five of decision 8`, `197 AC-1 abstention has no category and no priority` |
| AC-2 | Given the output schema and the prompt, then the output is `category`, `priority`, `reason`, `abstain` with no team, and an abstention has null category and priority | `197 AC-2 team is not an output`, `197 AC-2 abstention with a category is inconsistent`, `197 AC-2 suggestion without a priority is inconsistent`, `197 AC-2 removed category Theft is invalid` |
| AC-3 | Given a scored run, then the gate fails first on any urgent case under-prioritised, abstained or missing, then on any case below its minimum priority, any `Low` or `Medium` case rated `Critical` (review of PR #217), category accuracy below 0.80, coverage below 0.95 or a wrong expected abstention; there is no team gate and no participant floor | `197 AC-3 every reference passes`, `197 AC-3 urgent case under-prioritised fails`, `197 AC-3 urgent case abstained fails`, `197 AC-3 non-urgent case below its minimum fails`, `197 review always-Critical model fails the ceiling`, `197 review exact priority and over-prioritisation are reported`, `197 AC-3 category accuracy below 0.80 fails`, `197 AC-3 coverage boundary 19 of 20 passes and 18 of 20 fails`, `197 AC-3 wrong expected abstention fails` |
| AC-4 | Given the non-AI baseline, when it runs, then it suggests category and priority from keyword rules on the description only, abstains when no rule matches, and the committed report is what it produces | `197 AC-4 baseline uses keyword rules`, `197 AC-4 baseline keywords match only at the start of a word`, `197 AC-4 baseline abstains without a keyword`, `197 AC-4 committed baseline report is reproducible` |
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
| D1 | **Non-AI baseline = keyword rules on the description.** Ordered keyword lists give the category (first match, in the order Health, Safety, LostPerson, Infrastructure, Other) and the priority (Critical, then High, then Low; else Medium). No category keyword means abstention, which is what the product shows without AI (*Not triaged*). Keywords match at the start of a word and come only from the words of the priority scale's meanings and examples and of the category definitions (decision 8, `features.md` §4.2), in English and their direct Portuguese forms, and from the development split, never from test cases | Approved by the AI triage owner on 17 November 2026, proposed in ADR-005 and the plan. Options weighed: *no suggestion at all* (every case abstains: urgent misses would trivially equal all urgent cases and the comparison says nothing); *the old participant-category policy* (that input no longer exists); *keyword rules* (a real, simple non-AI policy an operator could write, so the AI must beat it) |
| D2 | The 21 cases expected `Theft` (T061 to T080, T097) become `Safety`, keeping their minimum priority (`Medium`, as the scale's *stolen phone*) | Decision 8: a theft is a `Safety` case. Approved on 17 November 2026 over relabelling them `Infrastructure` |
| D3 | 15 new `Infrastructure` cases, T101 to T115, written from the scale's examples (exposed live cable and structure about to fall: Critical; fallen barrier next to the crowd: High; broken toilet door: Medium; light out away from the paths: Low), 4/3/4/4 by level; every fifth ID stays development (T105, T110, T115). 115 cases: 23 development, 92 test | Every reference category must be represented (scorer check) and decision 8 defines the category by those examples. Approved on 17 November 2026. *18 November 2026 (review of PR #217): these cases restate the examples the prompt quotes, so T101 to T107 and T109 to T114 are tagged `scale-example` and reported as their own slice; T116 to T121 (T120 development) are `Infrastructure` cases not derived from the examples, 1 Critical, 1 High, 3 Medium, 1 Low. 121 cases: 24 development, 97 test* |
| D4 | T087 to T089 (empty text, now impossible because the API needs 1 to 500 characters) get short content-free descriptions (`Olá.`, `Preciso de ajuda por favor.`, `???`), still expected to abstain; their tag `empty` becomes `no-content` | Approved on 17 November 2026; keeps the abstention slice |
| D5 | Input is `{"text": …}` only; output schema has `category` and `priority` as enum or null, `reason`, `abstain`, no `team`. `abstain: true` requires both null and `abstain: false` requires both set; anything else is a semantic failure | ADR-005 decision 2; plan contract item 2 (*an abstention has null category and priority*) |
| D6 | Gates in this order: zero urgent cases under-prioritised, abstained, invalid or missing; no non-abstention case below its minimum priority; category accuracy ≥ 0.80; suggestion coverage ≥ 0.95; every expected abstention; no semantic failure. Removed: team accuracy, the participant priority floor (`current_priority`) and the `wrong-category` tag. Timing gate unchanged | ADR-005 decision 4 and coverage definition; plan step 6 (*no case below its minimum priority*); decisions 1 and 2 of 15 November (no participant category, no default priority per category) |
| D7 | The scorer stays out of CI and keeps only the standard library | Research tooling (README); no change agreed |
| D8 | Reports are written with LF line endings | `.gitattributes` keeps the package byte-identical so the hashes in the report match on every OS; the old report was written with the platform's line ending |
| D9 | The prompt states the scale with its examples, asks for the priority first and, when unsure between two levels, the higher one; since the review of PR #217 it also says to choose `Critical` only when its meaning applies | Priority is the main output (decision 1); a level above the minimum is accepted, but a false `Critical` takes a team away from a real emergency (D10) |
| D10 | Review of PR #217: a ceiling gate after the two priority gates, **no `Low` or `Medium` reference rated `Critical`**; the report adds `priority_exact` (share answered exactly at the minimum) and `over_prioritised` (count above it), not gated | Without a ceiling, a model answering `Critical` for everything passed gates 1 and 2, and the self-test asserted it. Labels are minimums, so one level above stays accepted (High for a Medium case); two levels above into `Critical` is the inflation the reviewer named. Exact agreement is not gated because the labels are not exact levels |
| D11 | Review of PR #217: T082 (*Não sei qual é a categoria certa…*) becomes *Onde posso deixar a mochila guardada durante os concertos?*, still `Other`/`Low`, tag `plain` instead of `ambiguous`; `vedac` removed from the baseline keywords | T082 described no incident, like T088 which abstains, and asked about a category the participant no longer chooses (decision 1); a real question for the staff keeps the `Other` slice. `vedac` came only from test case T022, against the D1 source rule (no effect on the numbers: T022 matches `Safety` first) |

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
| Priority one level above the minimum | Passes (minimum, not exact) | `197 AC-3 every reference passes` (oracle one level up) |
| `Critical` for every case | Gate 3 fails | `197 review always-Critical model fails the ceiling` |
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
- After: `--validate` 115 cases (23 dev, 92 test); `--self-test` passes; keyword baseline on the test split: 20 of 37 urgent cases missed, 6 below their minimum, category accuracy 0.607 (51 of 84), coverage 0.631 (53 of 84), 8 of 8 abstentions, gate failed (as a baseline should).
- AC-5 grep (`updated in #197`, `redefined in #197`, `proposal: keyword`) returns nothing outside this spec and the meeting records.
- G6 `guard.sh`: no BLOCK, no WARN; census 13 → 13. G7: the 20 check names of §1 exist in `evaluate.py`.
- G8 independent review (`code-review` at `high`), 10 findings:
  - Fixed, test first: (1) keywords matched inside other words (*acabou* → *cabo*, *slightly* → *light*): now at the start of a word, check `197 AC-4 baseline keywords match only at the start of a word`; (2) keywords found only in test cases (*esmag*, *crush*, *collaps*, *desmai*, *filh*) removed and the source rule made explicit (D1); (6) the report check now compares bytes; (7) restored the checks that invalid outputs fail the timing gate and that an incoherent output is not valid on time; (10) the order of release gate step 6 in the local AI decisions is back to its recorded wording (the scorer lists the checks with priority first, which that wording allows). Baseline numbers after the fix replace the earlier 18 of 37 / 0.631.
  - Kept: (3) `newline=` needs Python 3.10, which the README already requires; (4) the new `Infrastructure` test cases illustrate the scale the prompt quotes: the scale is the shared rubric (decision 2: people and the AI apply the same scale), so the prompt must carry it; only verbatim test text is rejected, and this is listed as a limit; (5) the abstention rule is not encoded as `oneOf` in the schema: the flat schema keeps the stdlib validator and Ollama's `format` simple, the scorer counts the case as a semantic failure, and the production validator (US-34, #81) enforces it; (8) repeated hashing in `--self-test` costs milliseconds; (9) no meeting record: the choices were approved by the AI triage owner (decision 13), not in a team meeting, and meeting records are not rewritten; they are in this spec, the CHANGELOG and the PR for the team's review.
- Left: the team's review of the labels (#149), a model run (#81, US-34) and the issue's first criterion still mentioning the *default priority of each category* (dropped by decision 2).

**18 November 2026, review of PR #217** (Tiago Teixeira, changes requested; five points in the review body, no inline comments):

1. *Over-prioritisation never penalised* (blocking): fixed (D10). Gate 3, no `Low` or `Medium` reference rated `Critical`; `priority_exact` and `over_prioritised` reported; the self-test now asserts that the always-`Critical` model fails (`197 review always-Critical model fails the ceiling`) and that one level above still passes. The prompt adds *choose Critical only when its meaning applies* (D9). ADR-005 decision 4, release gate step 6, the evaluation README and `responsible-ai.md` record the ceiling with a dated note. Not done: gating exact agreement, because the labels are minimums.
2. *Infrastructure cases paraphrase the scale examples*: fixed (D3). Checking the 12 `Infrastructure` test cases found 11 restate an example (only T108, the broken tap, does not), more than the four named; they are tagged `scale-example` (dev T105 and T110 too) and the report has that slice. Six new cases T116 to T121 are not derived from the examples. The keyword baseline gets 0 of the 5 new test cases, a first sign that its keywords follow the examples.
3. *T082 vs T088*: fixed (D11), T082 rewritten as a real question for the staff.
4. *`vedac` only from a test case*: fixed (D11).
5. *Old text in `responsible-ai.md` before the baseline table*: fixed, a superseded note above the two paragraphs of 22 October 2026; the limits list now says 121 cases.

Red before green: the self-test with the new checks failed with `KeyError: 'priority_exact'`, then only with `197 AC-4 committed baseline report is reproducible` until the report was regenerated. After: `python -I evaluate.py --validate` 121 cases (24 dev, 97 test); `--self-test` passes; keyword baseline on the test split: 22 of 39 urgent cases missed, 6 below their minimum, 0 `Low`/`Medium` rated `Critical`, category accuracy 0.573 (51 of 89), coverage 0.596 (53 of 89), exact priority 0.494 (44 of 89), 3 over-prioritised, 8 of 8 abstentions; gate failed. `scale-example` slice: 11 cases, 4 urgent missed, category accuracy 0.818. The 22 check names of §1 exist in `evaluate.py`.

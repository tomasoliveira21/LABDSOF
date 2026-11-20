# #227 · Correct the Critical over-rating on a fresh holdout

> **Issue:** [#227](https://github.com/tomasoliveira21/LABDSOF/issues/227), part of [#149](https://github.com/tomasoliveira21/LABDSOF/issues/149) (its AC-4 repeated runs, and the fresh evaluation split of its AC-2) · **Type:** docs · **Owner area:** AI triage (decision 13 of the [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md)) · **Stacked on:** PR #225 (#224), not merged yet · **Spec rules:** [README](README.md)
> **Sources:** [spec 224](224-ollama-evaluation-run.md) and the review of PR #225 (19 November 2026: the five ceiling failures T006, T007, T013, T017 and T119 are known, so a fix must be tuned on the development split or on new cases, never on the test split; keep each prompt version as its own file); [ADR-005](../Technical/adr/0005-ai-triage-provider-and-evaluation.md) decision 4; [local AI decisions](../Technical/plans/ai-provider-evaluation.md) (runtime contract, release gate steps 5 and 6); [evaluation package](../Research/architecture-investigation/evaluation/README.md); local-only inference, [ADR-011](../Technical/adr/0011-deployment-and-infrastructure.md).

## 1. Acceptance criteria

The checks run with `python -I` from `Docs/Research/architecture-investigation/evaluation/`: `evaluate.py --self-test` and `--validate`, `run_ollama.py --self-test` and `run_ollama.py --verify <run dir>` (each name below is printed by `verify()` or is an assertion of `self_test()`).

| # | Given / when / then | Covered by |
|---|---|---|
| AC-1 | Given HEAD, then the #224 run still passes `--verify`, the #224 prompt (`system-prompt.txt`) and `cases.jsonl` are unchanged, each new prompt version is its own file in `prompts/`, and a report hashes the cases and prompt files its run used | `run_ollama.py --verify runs/2026-11-19-local-qwen3` (eight checks); `227 AC-1 a report hashes the cases and prompt files the run used` |
| AC-2 | Given the holdout, then it is committed with its SHA-256 before the first prompt change, has at least 60 cases with `H001`-style IDs, repeats no case of `cases.jsonl`, and none of its inputs is in a prompt; its provenance and unreviewed labels are stated | `227 AC-2 a holdout under 60 cases is rejected`, `227 AC-2 a holdout input in a prompt is rejected`, `227 AC-2 a holdout case repeating cases.jsonl is rejected`, `227 AC-2 a holdout ID outside H001 style is rejected`, `227 AC-2 a holdout file with another split is rejected`; `evaluate.py --validate --cases holdout.jsonl`; commit order in §6 |
| AC-3 | Given the holdout runs, then the gate rule for repeated runs and the run manifest are committed before the first holdout run, and the manifest names the number of runs | `227 AC-3 manifest pre-registers the gate rule and the number of runs`; commit order in §6 |
| AC-4 | Given the four holdout runs (frozen prompt three times, #224 prompt once), then each committed report is what the scorer produces from its predictions, the run files are numbered without gaps, the repeated-run gate passes only when every run passes, and the documents state every gate per run, failures included, with the spread and the comparisons | `227 AC-4 the run files are numbered from 1 without gaps`, `227 AC-4 the repeated-run gate passes only when every run passes`, and the `224 AC-2`/`224 AC-3` checks for every run |

## 2. Gates

No Maven module, endpoint, contract or Compose file changes, so G1 to G5 have nothing to run on; CI checks the stack on the PR.

| Gate | Rule |
|---|---|
| Scorer checks | `--validate` (with and without `--cases holdout.jsonl dev-extra.jsonl`) and `--self-test` exit 0 with `python -I`; `--baseline` reproduces the committed reports byte for byte |
| Run checks | `run_ollama.py --verify` exits 0 on the #224 run directory and on both new run directories; `run_ollama.py --self-test` exits 0 |
| G6 | `guard.sh` exits 0 |
| G7 | CHANGELOG entry; the documents named by the issue state the real evidence with dated notes; every check named in §1 exists |
| G8 | Independent `code-review` at `high` on the whole diff |
| G9 | CI and SonarQube Cloud green on the PR |

The offline quality gate (seven checks, priority first) is **reported, never lowered**: a failing gate is a result.

## 3. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | **Separate files**: `system-prompt.txt` stays the #224 prompt (v4); each new version is `prompts/v5.txt`, `prompts/v6.txt`…; the holdout is `holdout.jsonl` (split `holdout`, IDs `H001`…) and the new development cases `dev-extra.jsonl` (split `dev`, IDs `D001`…). `evaluate.py` and `run_ollama.py` take `--cases` and `--prompt`; a report hashes the files its run used; a run manifest names them under `files` and `split` | Options: edit the package files in place (breaks the #224 `--verify` by design, and the review of PR #225 asked for prompt versions as files), or add files and options. The second keeps every earlier run checkable at HEAD; the defaults are the #224 package, so earlier commands and reports are unchanged |
| D2 | **Holdout written blind** by a separate agent given only the rubric (categories of `features.md` §4.2, the scale of §5.3, the minimum-priority rule, the slice list and the row format), told not to read any prompt, run or report. 72 cases, slices in about the proportions of the test split, with more `Low` cases than the test split has (the ceiling is measured on `Low`/`Medium` references). The author of this spec checks it only with scripts that print counts, slices, validation and similarity to `cases.jsonl`, never the texts | Plan of the run; the review of PR #225 asked for new cases. A holdout read by whoever tunes the prompt is not blind. Its labels are a proposal of the same kind as `cases.jsonl` and are **not independently reviewed** (#149 AC-2 stays open) |
| D3 | **Tuning on the development split only**: the 24 development cases of `cases.jsonl` plus `dev-extra.jsonl`, new cases written for this issue, never derived from test or holdout cases and not about the five known failures. The #224 predictions and test cases are not opened. At most four versions, each with its development results in §6; the best is frozen even if it fails on development | Review of PR #225. The #224 development split had no ceiling failure, so it could not show the problem: new development cases with alarming wording but a `Low`/`Medium` minimum are needed |
| D4 | **Repeated-run gate rule, pre-registered**: the release gate of the local AI decisions (step 6) does not say how repeated runs combine, so the offline quality gate passes only when it passes in **every** run (three runs of the frozen prompt). The #224 prompt runs once on the holdout as the comparison, not as a gate | Plan of the run; the strictest reading, so a lucky run cannot pass the gate. #149 AC-4 asks for repeated runs |
| D5 | **Runtime and settings as #224**: `ollama/ollama:0.40.16` by digest, `qwen3:4b` Q4_K_M, the same container settings, options, 20 s timeout and warm-up; the model volume of #224. Runs are sequential, each after a container restart; the old test split is not run again | Keeps the only change the prompt; repeating the test split would reuse a split whose failures are now known |
| D6 | **Timing is `provider_ms` in the manifest, never `elapsed_ms`** | As spec 224 D5: a direct model call decides only the quality gate |

## 4. Features, in build order

1. `--cases`/`--prompt` in the scorer and the runner, multi-run `--verify`, self-test checks (§1 AC-1, AC-2 checks).
2. Holdout written blind and committed with its hash (AC-2).
3. Development cases and prompt versions on the development split; frozen prompt (D3).
4. Gate rule (D4) and manifests committed before the runs (AC-3).
5. Four holdout runs, scoring, `--verify` (AC-4).
6. Documents: evaluation README, ADR-005, local AI decisions, `responsible-ai.md`, guide K2, `Docs/README.md`, CHANGELOG.

## 5. Edge-case matrix

| Case | Expected | Check |
|---|---|---|
| Holdout under 60 cases, an ID outside `H001` style, a second split in the file | Rejected | `227 AC-2 a holdout under 60 cases is rejected`, `227 AC-2 a holdout ID outside H001 style is rejected`, `227 AC-2 a holdout file with another split is rejected` |
| Holdout or extra development text equal to a `cases.jsonl` text | Rejected | `227 AC-2 a holdout case repeating cases.jsonl is rejected` |
| A holdout text pasted into any prompt version | Rejected | `227 AC-2 a holdout input in a prompt is rejected` |
| A test text pasted into any prompt version (not only `system-prompt.txt`) | Rejected | `evaluate.py --validate` (`test input leaked into the prompt`) |
| A report scored with other files than the run used | Hash mismatch | `227 AC-1 a report hashes the cases and prompt files the run used`, `224 AC-1 manifest hashes match the package` |
| #224 run directory after this change | Still verifies | `run_ollama.py --verify runs/2026-11-19-local-qwen3` |
| A missing run (`predictions-run-2` absent while run 3 exists) | Fails | `227 AC-4 the run files are numbered from 1 without gaps` |
| Manifest with fewer runs than files, or without a gate rule | Fails | `227 AC-3 manifest pre-registers the gate rule and the number of runs` |
| Manifest says the gate passed while one run failed | Fails | `227 AC-4 the repeated-run gate passes only when every run passes` |
| Timeout, cut-off or invalid reply in any run | `output: null`, counts as a failure | `224 AC-3` checks, per run |

## 6. Handoff

**20 November 2026.**

- Baseline on the #224 branch (2684782): `evaluate.py --validate` (121, 24, 97) and `--self-test`, `run_ollama.py --self-test` and `--verify runs/2026-11-19-local-qwen3` (eight checks) passed.

# #224 · Local Ollama triage evaluation run

> **Issue:** [#224](https://github.com/tomasoliveira21/LABDSOF/issues/224), part of [#149](https://github.com/tomasoliveira21/LABDSOF/issues/149) (its AC-1 and AC-4 for the local run) · **Type:** docs · **Owner area:** AI triage (decision 13 of the [meeting of 15 November 2026](../Meetings/2026-11-15-sprint-a-review-and-sprint-b-planning.md)) · **Spec rules:** [README](README.md)
> **Sources:** [ADR-005](../Technical/adr/0005-ai-triage-provider-and-evaluation.md) decisions 2 to 4; [local AI decisions](../Technical/plans/ai-provider-evaluation.md) (K1, runtime and resilience contract, release gate steps 1 to 6); [evaluation package](../Research/architecture-investigation/evaluation/README.md) and its spec [#197](197-ai-evaluation-priority.md); local-only inference, [ADR-011](../Technical/adr/0011-deployment-and-infrastructure.md).

## 1. Acceptance criteria

The checks run with `python -I Docs/Research/architecture-investigation/evaluation/run_ollama.py --verify <run dir>` and `run_ollama.py --self-test` (each name below is printed by `verify()` or `self_test()`), and with the scorer's `--validate` and `--self-test`. `--verify` compares the run with the package files on disk, so it passes only while the package is the one the run was frozen with; a later change to the dataset, prompt or schema makes it fail by design, because the run must then be redone (local AI decisions, K2 *Changes*).

| # | Given / when / then | Covered by |
|---|---|---|
| AC-1 | Given the committed run directory, then its manifest names the Ollama image, version and digest, the model, its digest and quantisation, the settings of the local AI decisions, the SHA-256 of `cases.jsonl`, `system-prompt.txt` and `output.schema.json`, the runner commit, the hardware and the date, and they match the package and the runner | `224 AC-1 manifest hashes match the package`, `224 AC-1 manifest settings match the runner`, `224 AC-1 manifest names runtime, model, runner commit, hardware and date` |
| AC-2 | Given the test run, then the predictions hold every test case once, input only and without `elapsed_ms`, and the committed scorer report is exactly what `evaluate.py --predictions … --split test` produces from them; the documents state its gate results apart from the keyword baseline and the scorer self-tests | `224 AC-2 predictions cover every case once, without elapsed_ms`, `224 AC-2 predictions are the raw replies`, `224 AC-2 committed report is what the scorer produces`; `evaluate.py --validate` and `--self-test` |
| AC-3 | Given a timeout, a transport error or invalid output, then the prediction is `output: null`, the raw file keeps the error, and the scorer counts it as a failure; if the runtime or hardware cannot complete the run, the failure is a gate with its evidence, not a pass | `224 AC-3 every timeout or invalid output is null, has its error and counts as a failure`, `224 AC-3 no output is accepted after the 20 s timeout`; runner `--self-test`: `224 AC-3 a reply after the timeout is null`, `224 AC-3 a reply that is not a chat object is null`, `224 AC-3 a chat reply without a message is null`, `224 AC-3 a cut-off reply is null` |

## 2. Gates

No Maven module, endpoint, contract or Compose file changes, so G1 to G5 have nothing to run on; CI checks the stack on the PR.

| Gate | Rule |
|---|---|
| Scorer checks | `--validate` and `--self-test` exit 0 with `python -I`; `--baseline` reproduces the committed report byte for byte |
| Run checks | `run_ollama.py --verify` exits 0 on the committed run directory; `run_ollama.py --self-test` exits 0 |
| G6 | `guard.sh` exits 0 |
| G7 | CHANGELOG entry; the documents named by the issue state the real evidence; every check named in §1 exists in `run_ollama.py` |
| G8 | Independent `code-review` at `high` on the whole diff |
| G9 | CI and SonarQube Cloud green on the PR |

The offline quality gate of the package (seven checks, priority first) is **reported, never lowered**: a failing gate is a result, not a reason to change the gate.

## 3. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | **Runtime: Ollama in Docker**, `ollama/ollama:0.40.16` pinned by digest, container with `OLLAMA_NUM_PARALLEL=1`, `OLLAMA_MAX_LOADED_MODELS=1`, `OLLAMA_MAX_QUEUE=1`, `OLLAMA_NO_CLOUD=1`, a named model volume and the port bound to `127.0.0.1` only | Options weighed, in this order: Docker (same image everywhere, as the Compose `ai` profile of K1); native Ollama from the release zip (only if Docker failed for a recorded reason); a recorded hardware gate (if both failed). Docker worked, so the others were not needed |
| D2 | **Runner kept and extended**: `run_ollama.py`, standard library only, one request at a time, input only, settings of the runtime contract (temperature 0, seed 42, `num_ctx` 4096, `num_predict` 256, `think: false`, `keep_alive` 10 m, schema through `format`), 20 s single-attempt response timeout on the whole call. Added: an unscored warm-up with its own 300 s timeout (also before `--resume`), `--resume` for the missing IDs after an interruption, `--verify` and `--self-test` for §1 | Options: write a new runner, or keep the reviewed one. It already followed the contract; the dev experiments showed that a warm-up cut at 20 s left the server busy and timed out the first scored case, so the warm-up (cold start, reported apart, plan step 5) gets a longer timeout. The scored calls keep 20 s |
| D3 | **Prompt changed on the development split only**, then frozen before the test run (four versions, §6): clearer category definitions as `features.md` §4.2 gives them (a theft is `Safety`; `Infrastructure` includes something loose or falling that puts people at risk), *not responding* added to Critical, *a lost person who cannot look after themselves* added to High, a past incident and a question for the staff are usable descriptions, the reason in the Portuguese of Portugal, and two development cases (T065, T105) added as examples | Allowed by the README (*development examples may inform the prompt*) and the plan (*prompt tuning uses the development cases only*). Options: run the #197 prompt as it was (dev gate failed: 2 urgent misses, category 17 of 22), or tune on dev. Every wording restates the scale or decision 8; no test text enters the prompt verbatim (the scorer checks exact text only). T065 and T105 now pass on dev by construction, so the dev numbers are optimistic. *Independent review: the two examples are close to test cases, so for those the test split is not a clean holdout: T065 (a stolen coat) is the situation of test T074, and test T119 (a wobbly table, minimum `Low`) was rated `Critical` with a reason that copies the T105 example's reason almost word for word, so that example probably caused one of the five ceiling failures. Recorded as a limit of this run; the run is not repeated (D4).* The baseline report is regenerated for the new prompt hash |
| D4 | **One gated run on the test split, no repeated runs** | Decision of the AI triage owner of 18 November 2026; release gate step 5 makes repeated runs optional. #149 AC-4's repeated runs are not done and are not claimed |
| D5 | **Timing is `provider_ms` in the manifest, never `elapsed_ms`** | README step 4: a direct model call is not submission-to-storage time, so it decides only the quality gate; SC3 needs the instrumented one-hour cohort (release gate step 7) |
| D6 | Run directory `evaluation/runs/2026-11-19-local-qwen3/` with `manifest.json` (committed before the test run), `predictions-run-1.jsonl`, `raw-run-1.jsonl`, `report-run-1.json` | Plan, run artefacts list |

## 4. Features, in build order

1. Runner checks (§1) and the runner changes of D2.
2. Dev experiments and frozen prompt (D3); regenerated baseline report.
3. Manifest, committed before the test run.
4. Gated test run, scoring, `provider_ms`, cold start and peak memory added to the manifest.
5. Documents: evaluation README, ADR-005, local AI decisions, `responsible-ai.md`, CHANGELOG.

## 5. Edge-case matrix

| Case | Expected | Check |
|---|---|---|
| Call longer than 20 s, even when no single read is | `output: null`, error kept, counts as a failure | `224 AC-3 a reply after the timeout is null`, `224 AC-3 no output is accepted after the 20 s timeout` |
| Reply that is not JSON, cut at 256 tokens, cut off by the server, not an object or without a message | `output: null`, error kept; the run goes on | `224 AC-3 a reply that is not a chat object is null`, `224 AC-3 a chat reply without a message is null`, `224 AC-3 a cut-off reply is null`, `224 AC-3 every timeout or invalid output is null, has its error and counts as a failure` |
| Prediction edited by hand, or without its raw reply | Mismatch | `224 AC-2 predictions are the raw replies` |
| Valid JSON that breaks the schema (e.g. reason over 240 characters) | Kept as returned; the scorer marks it schema-invalid | `224 AC-2 committed report is what the scorer produces` |
| Run interrupted | `--resume` runs only the missing IDs with the same settings; the interruption is recorded in the manifest | `224 AC-2 predictions cover every case once, without elapsed_ms` |
| Prediction with `elapsed_ms`, a missing or a duplicate ID | Rejected | `224 AC-2 predictions cover every case once, without elapsed_ms` |
| Prompt, dataset or schema changed after the manifest | Hash mismatch | `224 AC-1 manifest hashes match the package` |
| Runner settings changed after the manifest | Mismatch | `224 AC-1 manifest settings match the runner` |
| Report edited by hand | Byte mismatch | `224 AC-2 committed report is what the scorer produces` |
| Cold model on the first call, or on a resumed run | Unscored warm-up with its own timeout; cold start reported apart | warm-up row in `raw-run-1.jsonl` |
| Prompt injection in a description | Sent as data in `{"text": …}`; scored like any case; reasons reviewed for copied injection | `injection` slice of the report |

## 6. Handoff

**19 November 2026.**

- Baseline on `origin/main` (94c5631): `python -I evaluate.py --validate` (121 cases, 24 dev, 97 test) and `--self-test` passed.
- Runtime (D1): Docker Desktop 28.4.0 engine, VM with 8 GiB and 12 CPUs; `ollama/ollama:0.40.16` (`sha256:910d0d8107b538fb0894e63163eb195de0cc7aefcbc666dc62d3b18f71124592`); `qwen3:4b`, digest `359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7`, Q4_K_M confirmed by `/api/show`. No native fallback was needed.
- Dev experiments (D3), development split only, 22 non-abstention and 2 abstention cases, about 9 to 15 s per call:

| Prompt | Urgent misses | Below minimum | Category | Coverage | Abstentions | Dev gate |
|---|---|---|---|---|---|---|
| v1, as in #197 | 2 (T005, T060) | 3 | 17 of 22 | 20 of 22 | 2 of 2 | failed |
| v2, category and abstention wording | 2 (T005 timed out behind the 20 s warm-up, T060) | 4 | 16 of 22 | 20 of 22 | 2 of 2 | failed |
| v3, category definitions and examples T065, T105 | 1 (T060) | 1 | 21 of 22 | 21 of 22 | 2 of 2 | failed |
| v4, v3 plus a lost person who cannot look after themselves in High | 0 | 0 | 21 of 22 | 21 of 22 | 2 of 2 | passed (only T085, a question for the staff, abstained) |

  v4 is frozen. Red before green: after the prompt change `--self-test` failed only with `197 AC-4 committed baseline report is reproducible`, then passed with the regenerated report (only the prompt hash changed; the baseline numbers are the same).
- Manifest committed before the test run (1705048), with the runner commit 37dbba0. The container was restarted first, so the model was unloaded and the cgroup memory counters reset.
- **Gated run**, 19 November 2026, 07:23:37 to 07:41:06 UTC, once, 97 test cases, no interruption, no timeout, no transport or JSON error, every reply ended with `stop` (at most 63 output tokens). `provider_ms`: minimum 5,924, median 10,021, p95 12,811, maximum 13,833 ms; none over the 16 s slow threshold. Cold start (unscored warm-up): 58,541 ms, of which 5,068 ms model load; the rest is the first evaluation of the ~790-token prompt, which later calls reuse. Container `memory.peak` 3,398,074,368 bytes (includes the page cache of the mapped model); `/api/ps` 3,186,108,660 bytes on CPU.
- **Scorer report** (`report-run-1.json`), offline quality gate **failed**:

| Check, in order | Keyword baseline | `qwen3:4b` run 1 | Result |
|---|---|---|---|
| 1. Urgent cases missed | 22 of 39 | 0 of 39 | pass |
| 2. Below the minimum priority | 6 | 0 | pass |
| 3. `Low`/`Medium` rated `Critical` | 0 | 5 (T006, T007, T013, T017, T119) | **fail** |
| 4. Category accuracy | 0.573 | 0.966 (86 of 89; T081, T084 abstained, T101 `Safety` for `Infrastructure`) | pass |
| 5. Suggestion coverage | 0.596 | 0.978 (87 of 89; T081, T084) | pass |
| 6. Expected abstentions | 8 of 8 | 8 of 8 | pass |
| 7. Semantic inconsistencies | 0 | 0 | pass |

  Exact priority 0.629 (56 of 89); 31 above the minimum. Slices: urgent 39, none missed; English 7, mixed 4, injection 8, no-content 3, multiple 1: no failure; slang 7 and ambiguous 2: one ceiling failure each (T006, T007); plain 35: T013, T017, T119 rated `Critical`, T081 and T084 abstained; `scale-example` 11: no urgent miss, category 10 of 11 (T101). Timeouts did not occur, so no diagnostic run with a longer timeout was needed.
- Reasons, before any human review: 13 use Brazilian rather than European Portuguese forms (gerunds such as *não respondendo*, *em andamento*, *acessar*, *estandes*), T107 has a non-Portuguese word (*multitudine*), T074 says there is physical risk but gives `Medium`, T027 puts the risk on the team; no treatment advice and no copied injection seen. Human review of every reason is pending.
- Run checks: `run_ollama.py --verify` passes its six checks; red before green: each check failed on a tampered copy (a changed hash, temperature 0.6, an empty runner commit, a missing prediction, a report edited by hand, a null output without an error with the report regenerated). `evaluate.py --validate` (121, 24, 97) and `--self-test` pass with `python -I`.
- Not done, and not claimed: repeated runs (D4; #149 AC-4); the label review (#149 AC-2, needs another teammate); routing and the event-specific contract (#149 AC-3, depends on US-34, #81); SC3 and SC5 (#58, #81, #83). The container is stopped and removed; the model volume `farol-224-ollama-models` is kept for #81.
- **G8 independent review** (`code-review` at `high` on `94c5631...HEAD`), 10 findings:
  - Fixed, test first: (2) `--verify` documented as checking the run against the package it was frozen with (a later package change makes it fail by design: the run must be redone); (3) the 20 s timeout of `urlopen` applies to each read, so a slow reply could be kept: the runner now caps the whole call, and `224 AC-3 no output is accepted after the 20 s timeout` checks it; (4) `--verify` now checks that every prediction is its raw reply and that every prediction has one (`224 AC-2 predictions are the raw replies`), and reports a missing raw row as a failed check instead of a `KeyError`; (5) a cut-off body (`IncompleteRead`), a reply that is not an object, or a reply without a message no longer stop the run: each is that case's `output: null` (runner `--self-test`, against a local stub server); (6) `--resume` warms the model up first; (7) the decisions guide K2 row said 23/92 cases: now 24/97; (8) the `responsible-ai.md` limit is now *End-to-end latency not measured*, as direct-call latency is; (10) the package file list is one constant in `evaluate.py`, and the runner reuses `evaluate.check`. Red before green: on copies of the run, a prediction edited by hand (T119 `Critical` to `Low`, report regenerated) and a valid output at 21,000 ms passed the old `--verify`, and a null prediction without its raw row raised `KeyError`; the old runner kept a valid reply that took over 6 s with a 5 s timeout. After the fix each fails with its check name, and the committed run passes all eight `--verify` checks; the run itself is unaffected, as no call took over 13.8 s and none failed.
  - Recorded as a limit: (1) the two development examples are close to test cases (D3 note); the run is not repeated (D4). (6, second part) a line cut by killing the runner mid-write stops `--resume` with a JSON error: remove that line by hand first; not handled in code, since an interrupted line is rare and visible.
  - Kept: (9) D4 is not in `Docs/Meetings/`: it is a choice of the AI triage owner within plan step 5, which already makes repeated runs optional, recorded in this spec, the manifest, the CHANGELOG and the PR for the team's review, as #197 did; meeting records are not rewritten.
- The runner changed after the run (review fixes above). The manifest's `runner_commit` (37dbba0) is the runner that produced the run; the settings `--verify` checks did not change.

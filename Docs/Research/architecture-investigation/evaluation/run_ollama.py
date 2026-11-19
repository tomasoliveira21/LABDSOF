"""Direct model-call run against a local Ollama (#224, part of #149). Research tooling, standard library only.

Sends each selected case's input only ({"text": ...}) with the fixed system prompt and output schema, one request at
a time, with the settings of the local AI decisions (Docs/Technical/plans/ai-provider-evaluation.md). Writes the
predictions in the README step 3 format without elapsed_ms (a direct call is not SC3), and a raw file with the model's
text, provider_ms and Ollama's own counters for the manifest and the review of reasons. A timeout, transport error or
non-JSON text is output null, which the scorer counts as a failure.

--verify RUN_DIR checks a committed run (spec 224 §1): the manifest matches the package, the predictions cover the
split once without elapsed_ms, and the committed report is what the scorer produces from them.
"""
import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))  # python -I leaves the script's folder out of the path
import evaluate  # noqa: E402
OPTIONS = {"temperature": 0, "seed": 42, "num_ctx": 4096, "num_predict": 256}
TIMEOUT_S = 20  # Response timeout of the local profile, one attempt, no retry; no bytes come back before the answer.
# The warm-up loads the model (cold start) and is not scored, so it may take longer; a warm-up cut at 20 s keeps the
# server busy and delays the first scored case.
WARM_UP_TIMEOUT_S = 300
KEEP_ALIVE = "10m"
# Synthetic warm-up text, not in the dataset; its reply is never scored.
WARM_UP_TEXT = "Teste de aquecimento: uma pessoa pede informações sobre o horário dos concertos."
PACKAGE = ["cases.jsonl", "system-prompt.txt", "output.schema.json"]


def call(url, model, prompt, schema, text, timeout):
    body = {"model": model, "stream": False, "think": False, "keep_alive": KEEP_ALIVE, "format": schema, "options": OPTIONS,
            "messages": [{"role": "system", "content": prompt},
                         {"role": "user", "content": json.dumps({"text": text}, ensure_ascii=False)}]}
    request = urllib.request.Request(url + "/api/chat", json.dumps(body).encode(), {"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.load(response)


def attempt(args, prompt, schema, case_id, text, timeout=TIMEOUT_S):
    start = time.perf_counter()
    reply, error, output = {}, None, None
    try:
        reply = call(args.url, args.model, prompt, schema, text, timeout)
        output = json.loads(reply["message"]["content"])
    except (urllib.error.URLError, OSError, ValueError, KeyError, TypeError) as e:  # timeout, transport, non-JSON text
        error = type(e).__name__ + ": " + str(e)
    provider_ms = round((time.perf_counter() - start) * 1000)
    raw = {"id": case_id, "provider_ms": provider_ms, "error": error,
           "content": reply.get("message", {}).get("content"), "done_reason": reply.get("done_reason"),
           "prompt_eval_count": reply.get("prompt_eval_count"), "eval_count": reply.get("eval_count"),
           "load_duration_ns": reply.get("load_duration"), "total_duration_ns": reply.get("total_duration")}
    return output, raw


def run(args):
    prompt = (HERE / "system-prompt.txt").read_text(encoding="utf-8")
    schema = json.loads((HERE / "output.schema.json").read_text(encoding="utf-8"))
    cases = [c for c in evaluate.read_jsonl(HERE / "cases.jsonl") if c["split"] == args.split]
    # --resume after an interruption: keep the IDs already written and run only the missing ones, same settings.
    done = {row["id"] for row in evaluate.read_jsonl(args.out)} if args.resume and args.out.exists() else set()
    mode = "a" if args.resume else "w"
    with args.out.open(mode, encoding="utf-8", newline="\n") as out, args.raw.open(mode, encoding="utf-8", newline="\n") as raw:
        if args.warm_up:
            _, row = attempt(args, prompt, schema, "warm-up", WARM_UP_TEXT, WARM_UP_TIMEOUT_S)
            raw.write(json.dumps(row, ensure_ascii=False) + "\n")
            print("warm-up", row["provider_ms"], "ms", row["error"] or "", flush=True)
        for case in (c for c in cases if c["id"] not in done):
            output, row = attempt(args, prompt, schema, case["id"], case["input"]["text"])
            out.write(json.dumps({"id": case["id"], "output": output}, ensure_ascii=False) + "\n")
            raw.write(json.dumps(row, ensure_ascii=False) + "\n")
            out.flush(); raw.flush()
            print(case["id"], row["provider_ms"], "ms", row["error"] or output, flush=True)


def check(condition, name):
    if not condition:
        raise AssertionError("FAILED: " + name)
    print("ok", name)


def verify(run_dir, split):
    """Checks of spec 224 §1 on a committed run directory."""
    manifest = json.loads((run_dir / "manifest.json").read_text(encoding="utf-8"))
    settings = manifest["settings"]
    check({n: hashlib.sha256((HERE / n).read_bytes()).hexdigest() for n in PACKAGE} == manifest["package_sha256"],
          "224 AC-1 manifest hashes match the package")
    check(settings["options"] == OPTIONS and settings["think"] is False and settings["stream"] is False
          and settings["response_timeout_s"] == TIMEOUT_S and settings["keep_alive"] == KEEP_ALIVE,
          "224 AC-1 manifest settings match the runner")
    check(all(manifest["runtime"].get(k) for k in ("image", "image_digest", "version"))
          and all(manifest["model"].get(k) for k in ("name", "digest", "quantization"))
          and all(manifest.get(k) for k in ("runner_commit", "hardware", "date")),
          "224 AC-1 manifest names runtime, model, runner commit, hardware and date")
    cases = [c for c in evaluate.read_jsonl(HERE / "cases.jsonl") if c["split"] == split]
    predictions = evaluate.read_jsonl(run_dir / "predictions-run-1.jsonl")
    check(sorted(p["id"] for p in predictions) == sorted(c["id"] for c in cases) and all(set(p) == {"id", "output"} for p in predictions),
          "224 AC-2 predictions cover every case once, without elapsed_ms")
    check((run_dir / "report-run-1.json").read_bytes() == evaluate.render(evaluate.score(cases, predictions, "reported-provider-predictions")).encode(),
          "224 AC-2 committed report is what the scorer produces")
    raw = {r["id"]: r for r in evaluate.read_jsonl(run_dir / "raw-run-1.jsonl")}
    nulls = [p["id"] for p in predictions if p["output"] is None]
    report = json.loads((run_dir / "report-run-1.json").read_text(encoding="utf-8"))
    check(all(raw[i]["error"] for i in nulls) and set(nulls) <= set(report["failing_case_ids"])
          and report["overall"]["schema_invalid"] >= len(nulls),
          "224 AC-3 every timeout or invalid output is null, has its error and counts as a failure")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--split", choices=["dev", "test"], default="test")
    parser.add_argument("--verify", type=Path, metavar="RUN_DIR")
    parser.add_argument("--model", default="qwen3:4b")
    parser.add_argument("--url", default="http://127.0.0.1:11434")
    parser.add_argument("--out", type=Path)
    parser.add_argument("--raw", type=Path)
    parser.add_argument("--warm-up", action="store_true", help="one synthetic call first, recorded in the raw file only")
    parser.add_argument("--resume", action="store_true", help="append, running only the IDs missing from --out")
    args = parser.parse_args()
    if args.verify:
        verify(args.verify, args.split)
    elif args.out and args.raw:
        run(args)
    else:
        parser.error("--out and --raw are required to run")


if __name__ == "__main__":
    main()

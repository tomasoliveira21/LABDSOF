"""Offline research scorer. No network calls, provider credentials or production code."""
import argparse
import hashlib
import json
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
LEVEL = {"Low": 0, "Medium": 1, "High": 2, "Critical": 3}
TEAM = {"Health": "Medical", "Safety": "Security", "LostPerson": "PCR", "Theft": "Security", "Other": "PCR"}
FLOOR = {"Health": "Medium", "Safety": "High", "LostPerson": "Medium", "Theft": "Medium", "Other": "Low"}
SCHEMA = json.loads((HERE / "output.schema.json").read_text(encoding="utf-8"))
# ADR-005 coverage definition, aligned with observability in #145.
MIN_SUGGESTION_COVERAGE = 0.95
MIN_ON_TIME_COVERAGE = 0.95
DEADLINE_MS = 30000


def read_jsonl(path):
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def valid_output(output):
    # Implements the exact simple object schema stored beside this script.
    if not isinstance(output, dict) or set(output) != set(SCHEMA["required"]):
        return False
    for key, rule in SCHEMA["properties"].items():
        value = output[key]
        if rule["type"] == "boolean" and type(value) is not bool:
            return False
        if rule["type"] == "string":
            if not isinstance(value, str):
                return False
            if "enum" in rule and value not in rule["enum"]:
                return False
            if not rule.get("minLength", 0) <= len(value) <= rule.get("maxLength", math.inf):
                return False
    return True


def validate_cases(cases):
    assert len(cases) >= 100, "At least 100 cases required"
    assert len({x["id"] for x in cases}) == len(cases), "Duplicate IDs"
    assert len({(x["input"]["category"], x["input"]["text"]) for x in cases}) == len(cases), "Duplicate inputs"
    assert {x["split"] for x in cases} == {"dev", "test"}
    assert {x["expected"]["category"] for x in cases} == set(TEAM)
    for case in cases:
        data, expected = case["input"], case["expected"]
        assert data["category"] in TEAM and data["current_priority"] in LEVEL
        assert isinstance(data["text"], str) and len(data["text"]) <= 500
        assert expected["category"] in TEAM and expected["minimum_priority"] in LEVEL
        assert expected["team"] == TEAM[expected["category"]]
        assert type(expected["abstain"]) is bool and case["origin"].startswith("synthetic-")
        assert case["tags"] and set(case["tags"]) <= {"plain", "urgent", "wrong-category", "slang", "ambiguous", "english", "mixed", "multiple", "abstain", "empty", "injection"}
    # Prompt few-shot examples are development-only.
    prompt = (HERE / "system-prompt.txt").read_text(encoding="utf-8")
    for case in cases:
        if case["split"] == "test" and len(case["input"]["text"]) > 12:
            assert case["input"]["text"] not in prompt, "Test input leaked into few-shot prompt"


def baseline(case):
    category = case["input"]["category"]
    priority = max((FLOOR[category], case["input"]["current_priority"]), key=LEVEL.get)
    return {"id": case["id"], "output": {"category": category, "priority": priority, "team": TEAM[category], "reason": "Participant category and deterministic default; no model.", "abstain": False}}


def score(cases, predictions, mode="predictions"):
    known = {x["id"] for x in cases}
    indexed = {}
    for row in predictions:
        if row.get("id") not in known:
            raise ValueError("Unknown prediction ID: " + str(row.get("id")))
        if row["id"] in indexed:
            raise ValueError("Duplicate prediction ID: " + row["id"])
        indexed[row["id"]] = row
    rows = []
    for case in cases:
        row = indexed.get(case["id"], {})
        output = row.get("output")
        valid = valid_output(output)
        ms = row.get("elapsed_ms")
        timing_valid = type(ms) in (int, float) and math.isfinite(ms) and ms >= 0
        on_time = timing_valid and ms <= DEADLINE_MS
        e = case["expected"]
        usable = valid and not output["abstain"]
        urgent = not e["abstain"] and LEVEL[e["minimum_priority"]] >= LEVEL["High"]
        priority_ok = usable and LEVEL[output["priority"]] >= LEVEL[e["minimum_priority"]]
        floor_ok = valid and LEVEL[output["priority"]] >= LEVEL[case["input"]["current_priority"]]
        consistent = valid and output["team"] == TEAM[output["category"]]
        rows.append({"id": case["id"], "tags": case["tags"], "valid": valid, "missing": not bool(row), "timing_valid": timing_valid, "on_time": on_time,
                     "elapsed_ms": ms if timing_valid else None, "expected_abstain": e["abstain"], "urgent": urgent,
                     "category_correct": usable and output["category"] == e["category"], "team_correct": usable and output["team"] == e["team"],
                     "coverage": usable, "urgent_failure": urgent and not priority_ok,
                     "abstain_correct": valid and output["abstain"] == e["abstain"], "floor_violation": valid and not floor_ok,
                     "semantic_violation": valid and not consistent})
    def ratio(n, d):
        return round(n / d, 6) if d else None
    def aggregate(group):
        semantic = [x for x in group if not x["expected_abstain"]]
        abstain = [x for x in group if x["expected_abstain"]]
        times = sorted(x["elapsed_ms"] for x in group if x["timing_valid"])
        return {"cases": len(group), "non_abstention_reference_cases": len(semantic),
                "missing_predictions": sum(x["missing"] for x in group), "schema_invalid": sum(not x["valid"] for x in group),
                "category_accuracy": ratio(sum(x["category_correct"] for x in semantic), len(semantic)),
                "team_accuracy": ratio(sum(x["team_correct"] for x in semantic), len(semantic)),
                "suggestion_coverage": ratio(sum(x["coverage"] for x in semantic), len(semantic)),
                "urgent_cases": sum(x["urgent"] for x in group), "urgent_failures_including_abstention_or_missing": sum(x["urgent_failure"] for x in group),
                "reference_abstention_cases": len(abstain), "correct_reference_abstentions": sum(x["abstain_correct"] for x in abstain),
                "priority_floor_violations": sum(x["floor_violation"] for x in group), "category_team_inconsistencies": sum(x["semantic_violation"] for x in group),
                "latency_samples": len(times), "latency_p95_ms_nearest_rank": times[math.ceil(.95 * len(times)) - 1] if times else None,
                "valid_on_time_coverage": ratio(sum(x["valid"] and x["coverage"] and not x["floor_violation"] and not x["semantic_violation"] and x["on_time"] for x in semantic), len(semantic)) if times else None,
                "urgent_late_or_missing_timing": sum(x["urgent"] and not x["on_time"] for x in group)}
    overall = aggregate(rows)
    quality = (overall["urgent_failures_including_abstention_or_missing"] == 0
               and overall["category_accuracy"] is not None and overall["category_accuracy"] >= .8
               and overall["team_accuracy"] >= .8 and overall["suggestion_coverage"] >= MIN_SUGGESTION_COVERAGE
               and overall["correct_reference_abstentions"] == overall["reference_abstention_cases"]
               and overall["priority_floor_violations"] == 0 and overall["category_team_inconsistencies"] == 0)
    timing = (overall["latency_samples"] == len(cases) and overall["urgent_late_or_missing_timing"] == 0
              and overall["valid_on_time_coverage"] is not None and overall["valid_on_time_coverage"] >= MIN_ON_TIME_COVERAGE)
    return {"kind": mode, "dataset_labels": "synthetic draft, not independently reviewed", "split": sorted({x["split"] for x in cases}),
            "hashes_sha256": {name: hashlib.sha256((HERE / name).read_bytes()).hexdigest() for name in ["cases.jsonl", "system-prompt.txt", "output.schema.json"]},
            "overall": overall, "slices": {tag: aggregate([x for x in rows if tag in x["tags"]]) for tag in sorted({t for x in rows for t in x["tags"]})},
            "offline_quality_gate": quality, "reported_timing_gate": timing,
            "production_readiness": "NOT ESTABLISHED: independent labels, provider/privacy review, reason review and deployed SC3/SC5 tests still required",
            "failing_case_ids": [x["id"] for x in rows if not x["valid"] or x["urgent_failure"] or x["floor_violation"] or x["semantic_violation"] or not x["abstain_correct"] or (not x["expected_abstain"] and not (x["category_correct"] and x["team_correct"]))]}


def self_test(cases):
    import copy
    oracle = [{"id": c["id"], "elapsed_ms": 1000, "output": {"category": c["expected"]["category"], "priority": max((c["expected"]["minimum_priority"], c["input"]["current_priority"]), key=LEVEL.get), "team": c["expected"]["team"], "reason": "Synthetic scorer fixture only", "abstain": c["expected"]["abstain"]}} for c in cases]
    assert score(cases, oracle)["offline_quality_gate"]
    assert score(cases, oracle)["reported_timing_gate"]
    missing = score(cases, oracle[1:])
    assert missing["overall"]["missing_predictions"] == 1 and not missing["offline_quality_gate"]
    bad = copy.deepcopy(oracle); bad[0]["output"]["priority"] = "Low"
    assert score(cases, bad)["overall"]["urgent_failures_including_abstention_or_missing"] == 1
    bad = copy.deepcopy(oracle); bad[0]["output"]["team"] = "Logistics"
    assert score(cases, bad)["overall"]["schema_invalid"] == 1
    bad = copy.deepcopy(oracle); bad[0]["elapsed_ms"] = 30001
    assert not score(cases, bad)["reported_timing_gate"]
    bad = copy.deepcopy(oracle); bad[0]["output"]["abstain"] = True
    assert score(cases, bad)["overall"]["urgent_failures_including_abstention_or_missing"] == 1
    bad = copy.deepcopy(oracle); bad[0]["output"]["team"] = "PCR"
    assert score(cases, bad)["overall"]["category_team_inconsistencies"] == 1
    for invalid in [oracle + [oracle[0]], oracle + [{"id": "UNKNOWN"}]]:
        try:
            score(cases, invalid)
        except ValueError:
            pass
        else:
            raise AssertionError("Duplicate/unknown prediction accepted")
    assert not valid_output({**oracle[0]["output"], "reason": "x" * 241})
    assert not valid_output({**oracle[0]["output"], "abstain": "false"})
    assert not valid_output({**oracle[0]["output"], "extra": 1})
    # A small non-urgent fixture makes exactly 95% representable: 19/20.
    # It tests policy boundaries, never model quality or runtime performance.
    template = next(c for c in cases if not c["expected"]["abstain"] and LEVEL[c["expected"]["minimum_priority"]] < LEVEL["High"])
    boundary_cases = []
    boundary_predictions = []
    for i in range(20):
        case = copy.deepcopy(template); case["id"] = f"BOUNDARY-{i:02}"
        boundary_cases.append(case)
        expected = case["expected"]
        boundary_predictions.append({"id": case["id"], "elapsed_ms": DEADLINE_MS,
                                     "output": {"category": expected["category"], "priority": max((expected["minimum_priority"], case["input"]["current_priority"]), key=LEVEL.get),
                                                "team": expected["team"], "reason": "Synthetic threshold fixture only", "abstain": False}})
    at_deadline = score(boundary_cases, boundary_predictions)
    assert at_deadline["overall"]["valid_on_time_coverage"] == 1.0
    one_late = copy.deepcopy(boundary_predictions); one_late[0]["elapsed_ms"] = DEADLINE_MS + 1
    assert score(boundary_cases, one_late)["overall"]["valid_on_time_coverage"] == .95
    assert score(boundary_cases, one_late)["reported_timing_gate"]
    two_late = copy.deepcopy(one_late); two_late[1]["elapsed_ms"] = DEADLINE_MS + 1
    assert not score(boundary_cases, two_late)["reported_timing_gate"]
    one_invalid = copy.deepcopy(boundary_predictions); one_invalid[0]["output"] = None
    assert score(boundary_cases, one_invalid)["offline_quality_gate"]
    assert score(boundary_cases, one_invalid)["reported_timing_gate"]
    two_invalid = copy.deepcopy(one_invalid); two_invalid[1]["output"] = None
    assert not score(boundary_cases, two_invalid)["offline_quality_gate"]
    assert not score(boundary_cases, two_invalid)["reported_timing_gate"]
    inconsistent = copy.deepcopy(boundary_predictions)
    inconsistent[0]["output"]["team"] = next(t for t in set(TEAM.values()) if t != inconsistent[0]["output"]["team"])
    assert score(boundary_cases, inconsistent)["overall"]["valid_on_time_coverage"] == .95
    below_floor = copy.deepcopy(boundary_predictions); below_floor[0]["output"]["priority"] = "Low"
    assert score(boundary_cases, below_floor)["overall"]["valid_on_time_coverage"] == .95
    untimed = copy.deepcopy(boundary_predictions)
    for row in untimed: row.pop("elapsed_ms")
    assert score(boundary_cases, untimed)["overall"]["valid_on_time_coverage"] is None
    assert not score(boundary_cases, untimed)["reported_timing_gate"]
    print("Dataset and scorer checks passed; fixtures are NOT model results.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--validate", action="store_true")
    action.add_argument("--self-test", action="store_true")
    action.add_argument("--baseline", action="store_true")
    action.add_argument("--predictions", type=Path)
    parser.add_argument("--split", choices=["all", "dev", "test"], default="test")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    cases = read_jsonl(HERE / "cases.jsonl")
    validate_cases(cases)
    if args.self_test:
        self_test(cases); return
    if args.validate:
        print(json.dumps({"cases": len(cases), "dev": sum(c["split"] == "dev" for c in cases), "test": sum(c["split"] == "test" for c in cases)}, indent=2)); return
    selected = [c for c in cases if args.split == "all" or c["split"] == args.split]
    predictions = [baseline(c) for c in selected] if args.baseline else read_jsonl(args.predictions)
    # Supply only predictions from the selected split; unexpected IDs fail explicitly.
    result = score(selected, predictions, "deterministic-baseline-not-model" if args.baseline else "reported-provider-predictions")
    rendered = json.dumps(result, indent=2) + "\n"
    if args.out:
        args.out.write_text(rendered)
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()

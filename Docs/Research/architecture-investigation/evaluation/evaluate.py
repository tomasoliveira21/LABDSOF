"""Offline research scorer. No network calls, provider credentials or production code."""
import argparse
import copy
import hashlib
import json
import math
import re
import unicodedata
from pathlib import Path

HERE = Path(__file__).resolve().parent
LEVEL = {"Low": 0, "Medium": 1, "High": 2, "Critical": 3}
# Help categories of decision 8 of the meeting of 15 November 2026: Theft removed, Infrastructure added.
CATEGORIES = ("Health", "Safety", "LostPerson", "Infrastructure", "Other")
TAGS = {"plain", "urgent", "slang", "ambiguous", "english", "mixed", "multiple", "abstain", "no-content", "injection", "scale-example"}
SCHEMA = json.loads((HERE / "output.schema.json").read_text(encoding="utf-8"))
# The files a run is frozen with; their SHA-256 goes in every report and run manifest.
PACKAGE_FILES = ("cases.jsonl", "system-prompt.txt", "output.schema.json")
# #227: the fresh holdout and the extra development cases live in their own files, and each prompt version after the
# #224 one is its own file in prompts/, so the #224 run keeps its hashes (and its --verify) at HEAD.
EXTRA_SPLITS = {"holdout": "H", "dev": "D"}
MIN_HOLDOUT_CASES = 60
# ADR-005 decision 4 and coverage definition (aligned with observability in #145).
MIN_CATEGORY_ACCURACY = 0.80
MIN_SUGGESTION_COVERAGE = 0.95
MIN_ON_TIME_COVERAGE = 0.95
DEADLINE_MS = 30000

# Non-AI baseline (#197, spec D1): keyword rules on the description, matched at the start of a word on lower-case
# text without accents. Keywords come only from the words of the priority scale (features.md §5.3) and the category
# definitions (§4.2), in English and their direct Portuguese forms, and from the development split; never from test
# cases. The first category that matches wins, in this order; none means abstain.
CATEGORY_KEYWORDS = {
    "Health": ("inconsciente", "unconscious", "nao responde", "not respond", "acordar", "medic",
               "emergencia", "emergency", "sangr", "bleed", "ferid", "wound", "injur", "queda", "bebad", "drunk", "beber",
               "mal disposto", "unwell"),
    "Safety": ("agress", "assault", "fight", "luta", "bater", "violent", "ameac", "threat", "assedi", "harass", "persegu",
               "empurr", "pression", "furt", "roub", "stole", "theft", "thief", "tirar"),
    "LostPerson": ("perdi", "lost", "encontr", "find", "procur", "crianca", "child"),
    "Infrastructure": ("cabo", "cable", "barreira", "barrier", "grade", "estrutura", "structure", "partid",
                       "broken", "avaria", "danific", "damag", "luz", "light", "porta", "door", "casa de banho", "toilet", "wc"),
    "Other": ("duvida", "question", "pergunt", "organizac"),
}
# First level that matches; Medium when none does.
PRIORITY_KEYWORDS = (
    ("Critical", ("inconsciente", "unconscious", "nao responde", "not respond", "acordar", "emergencia", "emergency",
                  "sangrar muito", "bleeding heavily", "violent", "bater", "empurr", "pression", "cabo",
                  "cable", "prestes a cair", "about to fall", "pode cair")),
    ("High", ("crianca", "child", "bebad", "drunk", "sangr", "bleed", "assedi", "harass", "ameac", "threat",
              "persegu", "barreira", "barrier", "supervis")),
    ("Low", ("entorn", "spill", "luz", "light", "duvida", "question", "pergunt")),
)


def word_starts(words):
    return re.compile(r"\b(?:" + "|".join(map(re.escape, words)) + ")")


CATEGORY_PATTERNS = {category: word_starts(words) for category, words in CATEGORY_KEYWORDS.items()}
PRIORITY_PATTERNS = tuple((level, word_starts(words)) for level, words in PRIORITY_KEYWORDS)


def ratio(n, d):
    return round(n / d, 6) if d else None


def check(condition, name):
    if not condition:
        raise AssertionError(name)


def read_jsonl(path):
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def valid_output(output):
    # Implements the exact simple object schema stored beside this script.
    if not isinstance(output, dict) or set(output) != set(SCHEMA["required"]):
        return False
    for key, rule in SCHEMA["properties"].items():
        value = output[key]
        types = rule["type"] if isinstance(rule["type"], list) else [rule["type"]]
        if value is None:
            if "null" not in types:
                return False
        elif type(value) is bool:
            if "boolean" not in types:
                return False
        elif isinstance(value, str):
            if "string" not in types or ("enum" in rule and value not in rule["enum"]):
                return False
            if not rule.get("minLength", 0) <= len(value) <= rule.get("maxLength", math.inf):
                return False
        else:
            return False
    return True


def consistent(output):
    # Spec D5: an abstention has null category and priority; a suggestion has both.
    return output["abstain"] == (output["category"] is None) == (output["priority"] is None)


def package_files(cases_files=("cases.jsonl",), prompt="system-prompt.txt"):
    """The files a run uses, in report order: its cases files, its prompt and the schema."""
    return tuple(cases_files) + (prompt, "output.schema.json")


def prompt_files():
    return [HERE / "system-prompt.txt"] + sorted((HERE / "prompts").glob("*.txt"))


def validate_case(case):
    data, expected = case["input"], case["expected"]
    check(set(data) == {"text"}, "197 AC-1 input is only the description")
    check(isinstance(data["text"], str) and data["text"].strip() and len(data["text"]) <= 500,
          "197 AC-1 description has 1 to 500 characters")
    check(set(expected) == {"category", "minimum_priority", "abstain"} and type(expected["abstain"]) is bool,
          "197 AC-1 expected labels have no team")
    if expected["abstain"]:
        check(expected["category"] is None and expected["minimum_priority"] is None,
              "197 AC-1 abstention has no category and no priority")
    else:
        check(expected["category"] in CATEGORIES and expected["minimum_priority"] in LEVEL, "known reference labels")
    check(case["origin"].startswith("synthetic-"), "synthetic origin")
    check(case["tags"] and set(case["tags"]) <= TAGS, "known tags")


def validate_extra(rows, main, prompts=None):
    """#227: a holdout or extra development file. Its texts are new, and a holdout text is never in a prompt."""
    splits = {x["split"] for x in rows}
    check(len(splits) == 1 and splits <= set(EXTRA_SPLITS), "227 AC-2 an extra cases file holds one split, holdout or dev")
    split = splits.pop()
    check(split != "holdout" or len(rows) >= MIN_HOLDOUT_CASES, "227 AC-2 the holdout has at least 60 cases")
    check(all(re.fullmatch(EXTRA_SPLITS[split] + r"\d{3}", x["id"]) for x in rows), "227 AC-2 extra case IDs are H001 or D001 style")
    texts = [x["input"]["text"] for x in rows]
    check(len(set(texts)) == len(texts) and len({x["id"] for x in rows}) == len(rows), "duplicate IDs or inputs")
    check(not set(texts) & {x["input"]["text"] for x in main}, "227 AC-2 no extra case repeats a case of cases.jsonl")
    for case in rows:
        validate_case(case)
    if split == "holdout":
        prompts = [p.read_text(encoding="utf-8") for p in prompt_files()] if prompts is None else prompts
        check(not any(t in p for t in texts if len(t) > 12 for p in prompts), "227 AC-2 no holdout input is in a prompt")


def load_cases(files=("cases.jsonl",)):
    """Reads and validates cases.jsonl plus any extra cases file (#227); returns the rows of the given files, in order."""
    main = read_jsonl(HERE / "cases.jsonl")
    validate_cases(main)
    rows = []
    for name in files:
        if name == "cases.jsonl":
            rows += main
        else:
            extra = read_jsonl(HERE / name)
            validate_extra(extra, main)
            rows += extra
    check(len({x["id"] for x in rows}) == len(rows), "duplicate IDs")
    return rows


def validate_cases(cases):
    check(len(cases) >= 100, "at least 100 cases")
    check(len({x["id"] for x in cases}) == len(cases), "duplicate IDs")
    check(len({x["input"]["text"] for x in cases}) == len(cases), "duplicate inputs")
    check({x["split"] for x in cases} == {"dev", "test"}, "both splits present")
    check({x["expected"]["category"] for x in cases if not x["expected"]["abstain"]} == set(CATEGORIES),
          "197 AC-1 reference categories are the five of decision 8")
    for case in cases:
        validate_case(case)
        check(case["split"] == ("dev" if int(case["id"][1:]) % 5 == 0 else "test"), "every fifth ID is development")
    # Prompt few-shot examples are development-only, in every prompt version (#227).
    for prompt in (p.read_text(encoding="utf-8") for p in prompt_files()):
        for case in cases:
            if case["split"] == "test" and len(case["input"]["text"]) > 12:
                check(case["input"]["text"] not in prompt, "test input leaked into the prompt")


def normalise(text):
    return "".join(c for c in unicodedata.normalize("NFKD", text.lower()) if not unicodedata.combining(c))


def baseline(case):
    text = normalise(case["input"]["text"])
    category = next((c for c, pattern in CATEGORY_PATTERNS.items() if pattern.search(text)), None)
    if category is None:
        output = {"category": None, "priority": None, "reason": "No keyword matched; not triaged.", "abstain": True}
    else:
        priority = next((p for p, pattern in PRIORITY_PATTERNS if pattern.search(text)), "Medium")
        output = {"category": category, "priority": priority, "reason": "Keyword rules on the description; no model.", "abstain": False}
    return {"id": case["id"], "output": output}


def score(cases, predictions, mode="predictions", files=PACKAGE_FILES):
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
        coherent = valid and consistent(output)
        ms = row.get("elapsed_ms")
        timing_valid = type(ms) in (int, float) and math.isfinite(ms) and ms >= 0
        e = case["expected"]
        usable = coherent and not output["abstain"]
        urgent = not e["abstain"] and LEVEL[e["minimum_priority"]] >= LEVEL["High"]
        scored = usable and not e["abstain"]
        priority_ok = scored and LEVEL[output["priority"]] >= LEVEL[e["minimum_priority"]]
        rows.append({"id": case["id"], "tags": case["tags"], "valid": valid, "missing": not bool(row), "timing_valid": timing_valid,
                     "on_time": timing_valid and ms <= DEADLINE_MS, "elapsed_ms": ms if timing_valid else None,
                     "expected_abstain": e["abstain"], "urgent": urgent, "coverage": usable, "priority_ok": priority_ok,
                     "category_correct": usable and output["category"] == e["category"],
                     "urgent_failure": urgent and not priority_ok, "under_prioritised": scored and not priority_ok,
                     "priority_exact": scored and output["priority"] == e["minimum_priority"],
                     "over_prioritised": scored and LEVEL[output["priority"]] > LEVEL[e["minimum_priority"]],
                     # Review of PR #217: the ceiling, a Low or Medium reference rated Critical.
                     "low_or_medium_rated_critical": scored and e["minimum_priority"] in ("Low", "Medium") and output["priority"] == "Critical",
                     "abstain_correct": coherent and output["abstain"] == e["abstain"], "semantic_violation": valid and not coherent})

    def aggregate(group):
        semantic = [x for x in group if not x["expected_abstain"]]
        abstain = [x for x in group if x["expected_abstain"]]
        times = sorted(x["elapsed_ms"] for x in group if x["timing_valid"])
        return {"cases": len(group), "non_abstention_reference_cases": len(semantic),
                "missing_predictions": sum(x["missing"] for x in group), "schema_invalid": sum(not x["valid"] for x in group),
                "urgent_cases": sum(x["urgent"] for x in group),
                "urgent_failures_including_abstention_or_missing": sum(x["urgent_failure"] for x in group),
                "under_prioritised": sum(x["under_prioritised"] for x in group),
                "priority_at_or_above_minimum": ratio(sum(x["priority_ok"] for x in semantic), len(semantic)),
                "priority_exact": ratio(sum(x["priority_exact"] for x in semantic), len(semantic)),
                "over_prioritised": sum(x["over_prioritised"] for x in group),
                "low_or_medium_rated_critical": sum(x["low_or_medium_rated_critical"] for x in group),
                "category_accuracy": ratio(sum(x["category_correct"] for x in semantic), len(semantic)),
                "suggestion_coverage": ratio(sum(x["coverage"] for x in semantic), len(semantic)),
                "reference_abstention_cases": len(abstain), "correct_reference_abstentions": sum(x["abstain_correct"] for x in abstain),
                "semantic_inconsistencies": sum(x["semantic_violation"] for x in group),
                "latency_samples": len(times), "latency_p95_ms_nearest_rank": times[math.ceil(.95 * len(times)) - 1] if times else None,
                "valid_on_time_coverage": ratio(sum(x["priority_ok"] and x["on_time"] for x in semantic), len(semantic)) if times else None,
                "urgent_late_or_missing_timing": sum(x["urgent"] and not x["on_time"] for x in group)}
    overall = aggregate(rows)
    # Priority first (meeting of 15 November 2026, decision 1; ADR-005 decision 4), then category and coverage.
    gate_checks = {
        "1_zero_urgent_failures": overall["urgent_failures_including_abstention_or_missing"] == 0,
        "2_no_case_below_its_minimum_priority": overall["under_prioritised"] == 0,
        "3_no_low_or_medium_case_rated_critical": overall["low_or_medium_rated_critical"] == 0,
        "4_category_accuracy_at_least_0_80": overall["category_accuracy"] is not None and overall["category_accuracy"] >= MIN_CATEGORY_ACCURACY,
        "5_suggestion_coverage_at_least_0_95": overall["suggestion_coverage"] is not None and overall["suggestion_coverage"] >= MIN_SUGGESTION_COVERAGE,
        "6_every_expected_abstention": overall["correct_reference_abstentions"] == overall["reference_abstention_cases"],
        "7_no_semantic_inconsistency": overall["semantic_inconsistencies"] == 0,
    }
    timing = (overall["latency_samples"] == len(cases) and overall["urgent_late_or_missing_timing"] == 0
              and overall["valid_on_time_coverage"] is not None and overall["valid_on_time_coverage"] >= MIN_ON_TIME_COVERAGE)
    return {"kind": mode, "dataset_labels": "synthetic draft, not independently reviewed", "split": sorted({x["split"] for x in cases}),
            "hashes_sha256": {name: hashlib.sha256((HERE / name).read_bytes()).hexdigest() for name in files},
            "overall": overall, "slices": {tag: aggregate([x for x in rows if tag in x["tags"]]) for tag in sorted({t for x in rows for t in x["tags"]})},
            "offline_quality_gate_checks": gate_checks, "offline_quality_gate": all(gate_checks.values()), "reported_timing_gate": timing,
            "production_readiness": "NOT ESTABLISHED: independent labels, provider/privacy review, reason review and deployed SC3/SC5 tests still required",
            "failing_case_ids": [x["id"] for x in rows if not x["valid"] or x["semantic_violation"] or x["urgent_failure"] or x["under_prioritised"]
                                 or x["low_or_medium_rated_critical"]
                                 or not x["abstain_correct"] or (not x["expected_abstain"] and not x["category_correct"])]}


def run_baseline(cases, split, files=PACKAGE_FILES):
    selected = [c for c in cases if split == "all" or c["split"] == split]
    return score(selected, [baseline(c) for c in selected], "deterministic-keyword-baseline-not-model", files)


def render(report):
    return json.dumps(report, indent=2) + "\n"


def self_test(cases):
    def oracle(group, raise_by=0):
        # raise_by levels above each minimum, capped at Critical; 3 is the "always Critical" model.
        names = list(LEVEL)
        return [{"id": c["id"], "elapsed_ms": 1000, "output": {
            "category": c["expected"]["category"],
            "priority": None if c["expected"]["abstain"] else names[min(LEVEL[c["expected"]["minimum_priority"]] + raise_by, 3)],
            "reason": "Synthetic scorer fixture only", "abstain": c["expected"]["abstain"]}} for c in group]

    def first(predicate):
        return next(i for i, c in enumerate(cases) if predicate(c["expected"]))

    reference = oracle(cases)
    for fixture in (reference, oracle(cases, 1)):
        result = score(cases, fixture)
        check(result["offline_quality_gate"] and result["reported_timing_gate"], "197 AC-3 every reference passes")
    references = [i for i, c in enumerate(cases) if not c["expected"]["abstain"]]
    exact, one_up = score(cases, reference)["overall"], score(cases, oracle(cases, 1))["overall"]
    check(exact["priority_exact"] == 1.0 and exact["over_prioritised"] == 0
          and one_up["over_prioritised"] == sum(cases[i]["expected"]["minimum_priority"] != "Critical" for i in references)
          and one_up["priority_exact"] == ratio(sum(cases[i]["expected"]["minimum_priority"] == "Critical" for i in references), len(references)),
          "197 review exact priority and over-prioritisation are reported")
    result = score(cases, oracle(cases, 3))
    check(result["overall"]["low_or_medium_rated_critical"] == sum(cases[i]["expected"]["minimum_priority"] in ("Low", "Medium") for i in references)
          and not result["offline_quality_gate_checks"]["3_no_low_or_medium_case_rated_critical"] and not result["offline_quality_gate"],
          "197 review always-Critical model fails the ceiling")
    urgent = first(lambda e: not e["abstain"] and LEVEL[e["minimum_priority"]] >= LEVEL["High"])
    medium = first(lambda e: not e["abstain"] and e["minimum_priority"] == "Medium")
    expected_abstention = first(lambda e: e["abstain"])

    bad = copy.deepcopy(reference)
    bad[urgent]["output"]["priority"] = next(p for p, n in LEVEL.items() if n == LEVEL[cases[urgent]["expected"]["minimum_priority"]] - 1)
    result = score(cases, bad)
    check(result["overall"]["urgent_failures_including_abstention_or_missing"] == 1
          and not result["offline_quality_gate_checks"]["1_zero_urgent_failures"] and not result["offline_quality_gate"],
          "197 AC-3 urgent case under-prioritised fails")
    bad = copy.deepcopy(reference)
    bad[urgent]["output"].update(category=None, priority=None, abstain=True)
    result = score(cases, bad)
    check(result["overall"]["urgent_failures_including_abstention_or_missing"] == 1 and not result["offline_quality_gate"],
          "197 AC-3 urgent case abstained fails")
    bad = copy.deepcopy(reference)
    bad[medium]["output"]["priority"] = "Low"
    result = score(cases, bad)
    check(result["overall"]["under_prioritised"] == 1 and result["overall"]["urgent_failures_including_abstention_or_missing"] == 0
          and not result["offline_quality_gate"], "197 AC-3 non-urgent case below its minimum fails")
    bad = copy.deepcopy(reference)
    for i in references[:math.ceil(.21 * len(references))]:
        bad[i]["output"]["category"] = next(c for c in CATEGORIES if c != cases[i]["expected"]["category"])
    result = score(cases, bad)
    check(result["overall"]["category_accuracy"] < MIN_CATEGORY_ACCURACY and result["offline_quality_gate_checks"]["2_no_case_below_its_minimum_priority"]
          and not result["offline_quality_gate"], "197 AC-3 category accuracy below 0.80 fails")
    bad = copy.deepcopy(reference)
    bad[expected_abstention]["output"].update(category="Other", priority="Low", abstain=False)
    result = score(cases, bad)
    check(result["overall"]["correct_reference_abstentions"] == result["overall"]["reference_abstention_cases"] - 1
          and not result["offline_quality_gate"], "197 AC-3 wrong expected abstention fails")

    check(not valid_output({**reference[urgent]["output"], "team": "Medical"}), "197 AC-2 team is not an output")
    check(not valid_output({**reference[urgent]["output"], "category": "Theft"}), "197 AC-2 removed category Theft is invalid")
    bad = copy.deepcopy(reference)
    bad[expected_abstention]["output"]["category"] = "Other"
    check(score(cases, bad)["overall"]["semantic_inconsistencies"] == 1, "197 AC-2 abstention with a category is inconsistent")
    bad = copy.deepcopy(reference)
    bad[medium]["output"]["priority"] = None
    result = score(cases, bad)
    check(result["overall"]["semantic_inconsistencies"] == 1 and not result["offline_quality_gate"], "197 AC-2 suggestion without a priority is inconsistent")
    check(not valid_output({**reference[urgent]["output"], "reason": "x" * 241}), "reason over 240 characters is invalid")
    check(not valid_output({**reference[urgent]["output"], "abstain": "false"}), "abstain as a string is invalid")
    check(not valid_output({**reference[urgent]["output"], "extra": 1}), "extra key is invalid")

    missing = score(cases, reference[1:])
    check(missing["overall"]["missing_predictions"] == 1 and not missing["offline_quality_gate"], "missing prediction")
    for invalid in [reference + [reference[0]], reference + [{"id": "UNKNOWN"}]]:
        try:
            score(cases, invalid)
        except ValueError:
            pass
        else:
            raise AssertionError("duplicate or unknown ID aborts")
    bad = copy.deepcopy(reference)
    bad[urgent]["elapsed_ms"] = DEADLINE_MS + 1
    check(not score(cases, bad)["reported_timing_gate"], "late urgent case fails the timing gate")

    # A small non-urgent fixture makes exactly 95% representable: 19/20.
    # It tests policy boundaries, never model quality or runtime performance.
    template = cases[medium]
    boundary_cases = []
    for i in range(20):
        case = copy.deepcopy(template)
        case["id"] = f"BOUNDARY-{i:02}"
        boundary_cases.append(case)
    boundary = oracle(boundary_cases)
    for row in boundary:
        row["elapsed_ms"] = DEADLINE_MS
    check(score(boundary_cases, boundary)["overall"]["valid_on_time_coverage"] == 1.0, "30,000 ms is on time")
    one_late = copy.deepcopy(boundary); one_late[0]["elapsed_ms"] = DEADLINE_MS + 1
    check(score(boundary_cases, one_late)["overall"]["valid_on_time_coverage"] == .95 and score(boundary_cases, one_late)["reported_timing_gate"],
          "one late of 20 passes the timing gate")
    two_late = copy.deepcopy(one_late); two_late[1]["elapsed_ms"] = DEADLINE_MS + 1
    check(not score(boundary_cases, two_late)["reported_timing_gate"], "two late of 20 fail the timing gate")
    one_invalid = copy.deepcopy(boundary); one_invalid[0]["output"] = None
    two_invalid = copy.deepcopy(one_invalid); two_invalid[1]["output"] = None
    check(score(boundary_cases, one_invalid)["offline_quality_gate"] and not score(boundary_cases, two_invalid)["offline_quality_gate"],
          "197 AC-3 coverage boundary 19 of 20 passes and 18 of 20 fails")
    check(score(boundary_cases, one_invalid)["reported_timing_gate"] and not score(boundary_cases, two_invalid)["reported_timing_gate"],
          "invalid outputs are not on time")
    incoherent = copy.deepcopy(boundary); incoherent[0]["output"]["category"] = None
    check(score(boundary_cases, incoherent)["overall"]["valid_on_time_coverage"] == .95, "an incoherent output is not valid on time")
    below = copy.deepcopy(boundary); below[0]["output"]["priority"] = "Low"
    check(score(boundary_cases, below)["overall"]["valid_on_time_coverage"] == .95, "a case below its minimum is not valid on time")
    untimed = copy.deepcopy(boundary)
    for row in untimed:
        row.pop("elapsed_ms")
    check(score(boundary_cases, untimed)["overall"]["valid_on_time_coverage"] is None and not score(boundary_cases, untimed)["reported_timing_gate"],
          "untimed run has no on-time coverage")

    def suggest(text):
        output = baseline({"id": "X", "input": {"text": text}})["output"]
        return output["category"], output["priority"]
    check(suggest("Está uma pessoa INCONSCIENTE aqui.") == ("Health", "Critical")
          and suggest("Roubaram-me o telemóvel.") == ("Safety", "Medium")
          and suggest("A light is out.") == ("Infrastructure", "Low"),
          "197 AC-4 baseline uses keyword rules")
    check(suggest("I feel slightly unwell.") == ("Health", "Medium") and suggest("O meu amigo acabou de ser roubado.") == ("Safety", "Medium")
          and suggest("É importante, agradeço.") == (None, None),
          "197 AC-4 baseline keywords match only at the start of a word")
    check(baseline({"id": "X", "input": {"text": "Olá."}})["output"]
          == {"category": None, "priority": None, "reason": "No keyword matched; not triaged.", "abstain": True},
          "197 AC-4 baseline abstains without a keyword")
    check(render(run_baseline(cases, "test")).encode("utf-8") == (HERE / "baseline-report.json").read_bytes(),
          "197 AC-4 committed baseline report is reproducible")

    # #227: extra cases files (holdout, extra development cases) and the files a report hashes.
    def rejects(rows, prompts, name):
        try:
            validate_extra(rows, cases, prompts)
        except AssertionError as e:
            return str(e) == name
        return False
    holdout = []
    for i in range(MIN_HOLDOUT_CASES):
        case = copy.deepcopy(template)
        case.update(id=f"H{i + 1:03}", split="holdout", input={"text": f"Synthetic holdout fixture number {i + 1}."})
        holdout.append(case)
    validate_extra(holdout, cases, [])
    check(rejects(holdout[1:], [], "227 AC-2 the holdout has at least 60 cases"), "227 AC-2 a holdout under 60 cases is rejected")
    check(rejects(holdout, [holdout[5]["input"]["text"]], "227 AC-2 no holdout input is in a prompt"),
          "227 AC-2 a holdout input in a prompt is rejected")
    copied = copy.deepcopy(holdout); copied[0]["input"]["text"] = cases[medium]["input"]["text"]
    check(rejects(copied, [], "227 AC-2 no extra case repeats a case of cases.jsonl"), "227 AC-2 a holdout case repeating cases.jsonl is rejected")
    renamed = copy.deepcopy(holdout); renamed[0]["id"] = "T999"
    check(rejects(renamed, [], "227 AC-2 extra case IDs are H001 or D001 style"), "227 AC-2 a holdout ID outside H001 style is rejected")
    mixed = copy.deepcopy(holdout); mixed[0]["split"] = "test"
    check(rejects(mixed, [], "227 AC-2 an extra cases file holds one split, holdout or dev"), "227 AC-2 a holdout file with another split is rejected")
    check(package_files() == PACKAGE_FILES and package_files(["holdout.jsonl"], "prompts/v5.txt") == ("holdout.jsonl", "prompts/v5.txt", "output.schema.json")
          and list(score(cases, reference, files=("cases.jsonl", "output.schema.json"))["hashes_sha256"]) == ["cases.jsonl", "output.schema.json"],
          "227 AC-1 a report hashes the cases and prompt files the run used")
    files = package_files(["holdout.jsonl"])
    check(render(run_baseline(load_cases(files[:1]), "holdout", files)).encode("utf-8") == (HERE / "baseline-holdout-report.json").read_bytes(),
          "227 AC-4 committed holdout baseline report is reproducible")
    print("Dataset and scorer checks passed; fixtures are NOT model results.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--validate", action="store_true")
    action.add_argument("--self-test", action="store_true")
    action.add_argument("--baseline", action="store_true")
    action.add_argument("--predictions", type=Path)
    parser.add_argument("--split", choices=["all", "dev", "test", "holdout"], default="test")
    parser.add_argument("--cases", nargs="+", default=["cases.jsonl"], metavar="FILE",
                        help="cases files in this folder (#227: holdout.jsonl, dev-extra.jsonl); default cases.jsonl")
    parser.add_argument("--prompt", default="system-prompt.txt", metavar="FILE",
                        help="prompt file the run used, in this folder, for the report hashes (#227: prompts/...)")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    files = package_files(args.cases, args.prompt)
    cases = load_cases(args.cases)
    if args.self_test:
        self_test(read_jsonl(HERE / "cases.jsonl"))
        return
    if args.validate:
        print(json.dumps(dict(cases=len(cases), **{s: sum(c["split"] == s for c in cases) for s in sorted({c["split"] for c in cases})}), indent=2))
        return
    if args.baseline:
        result = run_baseline(cases, args.split, files)
    else:
        selected = [c for c in cases if args.split == "all" or c["split"] == args.split]
        # Supply only predictions from the selected split; unexpected IDs fail explicitly.
        result = score(selected, read_jsonl(args.predictions), "reported-provider-predictions", files)
    if args.out:
        args.out.write_text(render(result), encoding="utf-8", newline="\n")
    else:
        print(render(result), end="")


if __name__ == "__main__":
    main()

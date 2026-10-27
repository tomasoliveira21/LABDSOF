"""Check the Porto Digital stubs on a running WireMock, calling it as Transports will.

Plays the whole scenario (every snapshot, every call, then the loop back to the first),
and checks each answer against the recordings zip: the right snapshot in the right
order, only buses within 2 km, positions fresh for the 5 min limit, each bus with its
recorded age. Also checks the percent-encoded query, the requests that must get 404,
and the scenario reset. Standard library only; Python 3.11 or later.

Usage (from the repository root, WireMock on port 8090):
  python infra/wiremock/scripts/check_porto_digital.py \
      --zip infra/wiremock/recordings/porto_bus_responses_20261025.zip \
      --base http://127.0.0.1:8090 --coords 41.1724,-8.6841
"""
import argparse
import csv
import io
import json
import math
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from datetime import datetime, timezone

ap = argparse.ArgumentParser()
ap.add_argument("--zip", required=True)
ap.add_argument("--base", required=True)
ap.add_argument("--coords", required=True)
ap.add_argument("--max-distance", type=int, default=2000)
ap.add_argument("--calls-per-snapshot", type=int, default=2)
args = ap.parse_args()
lat, lon = (float(x) for x in args.coords.split(","))


def parse_time(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def distance_m(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(a))


# Expected answer of each snapshot (bus id -> recorded age), straight from the recordings.
expected = []
with zipfile.ZipFile(args.zip) as archive:
    manifest = next(n for n in archive.namelist() if n.endswith("manifest.csv"))
    folder = manifest[: -len("manifest.csv")]
    rows = sorted(csv.DictReader(io.TextIOWrapper(archive.open(manifest), encoding="utf-8")),
                  key=lambda r: int(r["index"]))
    for r in rows:
        fetched = parse_time(r["fetched_utc"])
        ages = {}
        for e in json.loads(archive.read(folder + r["file"])):
            blon, blat = e["location"]["value"]["coordinates"][:2]
            if distance_m(lat, lon, blat, blon) <= args.max_distance:
                ages[e["id"]] = (fetched - parse_time(e["observationDateTime"]["value"])).total_seconds()
        expected.append(ages)
signatures = [frozenset(a) for a in expected]

params = {"q": "vehicleType==bus", "georel": f"near;maxDistance:{args.max_distance}",
          "geometry": "point", "coords": args.coords, "limit": "1000"}
PLAIN = "/v2/entities?" + "&".join(f"{k}={v}" for k, v in params.items())
ENCODED = "/v2/entities?" + urllib.parse.urlencode(params, safe="")


def get(path):
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(args.base + path, timeout=5) as resp:
            return resp.status, resp.headers.get("Content-Type", ""), resp.read(), time.perf_counter() - started
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Content-Type", ""), e.read(), time.perf_counter() - started


failures, latencies = [], []


def fail(msg):
    failures.append(msg)
    print("FAIL", msg)


def check_call(label, path, snapshot):
    status, ctype, body, seconds = get(path)
    latencies.append(seconds)
    now = datetime.now(timezone.utc)
    if status != 200 or not ctype.startswith("application/json"):
        return fail(f"{label}: HTTP {status} {ctype}")
    data = json.loads(body)
    ids = frozenset(e["id"] for e in data)
    # Consecutive snapshots can hold the same buses; accept any snapshot with the same set.
    if ids != signatures[snapshot - 1]:
        return fail(f"{label}: expected snapshot {snapshot}")
    for e in data:
        blon, blat = e["location"]["value"]["coordinates"][:2]
        if distance_m(lat, lon, blat, blon) > args.max_distance:
            return fail(f"{label}: {e['id']} outside {args.max_distance} m")
        age = (now - parse_time(e["observationDateTime"]["value"])).total_seconds()
        if age > 300:
            return fail(f"{label}: {e['id']} is {age:.0f} s old, over the 5 min limit")
        if abs(age - expected[snapshot - 1][e["id"]]) > 3:
            return fail(f"{label}: {e['id']} age {age:.0f} s, recorded {expected[snapshot - 1][e['id']]:.0f} s")


def reset():
    req = urllib.request.Request(args.base + "/__admin/scenarios/reset", method="POST")
    urllib.request.urlopen(req, timeout=5).read()


reset()
calls = 0
for snapshot in range(1, len(expected) + 1):
    for call in range(1, args.calls_per_snapshot + 1):
        check_call(f"snapshot {snapshot} call {call}", PLAIN, snapshot)
        calls += 1
check_call("loop back to snapshot 1", PLAIN, 1)
check_call("percent-encoded query", ENCODED, 1)
for label, path in [
    ("other coordinates", PLAIN.replace(args.coords, "41.1486,-8.6110")),
    ("whole-city query", "/v2/entities?q=vehicleType==bus&limit=1000"),
    ("other distance", PLAIN.replace(f"maxDistance:{args.max_distance}", "maxDistance:5000")),
]:
    status = get(path)[0]
    if status != 404:
        fail(f"{label}: HTTP {status}, expected 404")
reset()
check_call("after reset", PLAIN, 1)
reset()

latencies.sort()
print(f"{calls} scenario calls over {len(expected)} snapshots, plus loop, encoded query, 3 refusals and reset")
print(f"latency p50 {latencies[len(latencies) // 2] * 1000:.0f} ms, max {latencies[-1] * 1000:.0f} ms")
print("RESULT:", "all checks passed" if not failures else f"{len(failures)} failed")
raise SystemExit(1 if failures else 0)

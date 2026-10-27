"""Turn recorded Porto Digital responses into WireMock stubs (guide J4).

Reads the recordings zip (manifest.csv plus one NGSI v2 response per minute) and, for
each snapshot:
  1. keeps only the buses within --max-distance metres of --coords, as the broker's
     georel=near;maxDistance query would; WireMock cannot filter by distance;
  2. replaces every observationDateTime with {{now offset='-N seconds'}}, N being the
     bus's age when it was recorded, so the data passes the 5 min freshness limit
     whenever it is replayed;
  3. writes one mapping per call, chained as a WireMock scenario: each snapshot answers
     --calls-per-snapshot calls, then the next one; after the last, the first again.

The generated files replace everything in mappings/porto-digital/ and
__files/porto-digital/ under --out. Standard library only; Python 3.11 or later.

Usage (from the repository root):
  python infra/wiremock/scripts/convert_porto_digital.py \
      --zip infra/wiremock/recordings/porto_bus_responses_20261025.zip \
      --out infra/wiremock --coords 41.1724,-8.6841
"""
import argparse
import csv
import hashlib
import io
import json
import math
import os
import re
import zipfile
from datetime import datetime

SCENARIO = "porto-digital-replay"
SUBDIR = "porto-digital"
AGE_TOKEN = re.compile(r'"@@AGE(-?\d+)@@"')


def parse_time(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def distance_m(lat1, lon1, lat2, lon2):
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(a))


def state_name(snapshot, call):
    return "Started" if (snapshot, call) == (1, 1) else f"snapshot-{snapshot:03d}-call-{call}"


def read_manifest(archive):
    names = [n for n in archive.namelist() if n.endswith("/manifest.csv") or n == "manifest.csv"]
    if len(names) != 1:
        raise SystemExit("the zip must hold exactly one manifest.csv")
    folder = names[0][: -len("manifest.csv")]
    rows = list(csv.DictReader(io.TextIOWrapper(archive.open(names[0]), encoding="utf-8")))
    return folder, rows


def clean(directory):
    os.makedirs(directory, exist_ok=True)
    for name in os.listdir(directory):
        if name.endswith(".json"):
            os.remove(os.path.join(directory, name))


def write_json(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--zip", required=True, help="recordings zip with manifest.csv")
    ap.add_argument("--out", required=True, help="WireMock root (holds mappings/ and __files/)")
    ap.add_argument("--coords", required=True, help="lat,lon exactly as Transports sends it")
    ap.add_argument("--max-distance", type=int, default=2000, help="metres (default 2000)")
    ap.add_argument("--calls-per-snapshot", type=int, default=2,
                    help="calls each snapshot answers (default 2: imports every 30 s, snapshots ~60 s apart)")
    args = ap.parse_args()

    lat, lon = (float(x) for x in args.coords.split(","))  # NGSI v2 coords: latitude first
    mappings_dir = os.path.join(args.out, "mappings", SUBDIR)
    files_dir = os.path.join(args.out, "__files", SUBDIR)
    clean(mappings_dir)
    clean(files_dir)

    with zipfile.ZipFile(args.zip) as archive:
        folder, rows = read_manifest(archive)
        rows.sort(key=lambda r: int(r["index"]))
        if [int(r["index"]) for r in rows] != list(range(1, len(rows) + 1)):
            raise SystemExit("manifest indexes must run 1..N without gaps")
        last = len(rows)
        total_buses = total_bytes = 0

        for r in rows:
            i = int(r["index"])
            raw = archive.read(folder + r["file"])
            if hashlib.sha256(raw).hexdigest() != r["sha256"]:
                raise SystemExit(f"checksum mismatch: {r['file']}")
            fetched = parse_time(r["fetched_utc"])

            kept = []
            for entity in json.loads(raw):
                blon, blat = entity["location"]["value"]["coordinates"][:2]  # GeoJSON: longitude first
                if distance_m(lat, lon, blat, blon) > args.max_distance:
                    continue
                age = round((fetched - parse_time(entity["observationDateTime"]["value"])).total_seconds())
                entity["observationDateTime"]["value"] = f"@@AGE{age}@@"
                kept.append(entity)

            body = json.dumps(kept, ensure_ascii=False, separators=(",", ":"))
            body = AGE_TOKEN.sub(lambda m: "\"{{now offset='%d seconds'}}\"" % -int(m.group(1)), body)
            body_name = f"{SUBDIR}/snapshot-{i:03d}.json"
            write_json(os.path.join(args.out, "__files", body_name), body + "\n")
            total_buses += len(kept)
            total_bytes += len(body.encode())

            for call in range(1, args.calls_per_snapshot + 1):
                if call < args.calls_per_snapshot:
                    nxt = (i, call + 1)
                else:
                    nxt = (1 if i == last else i + 1, 1)
                mapping = {
                    "name": f"Porto Digital snapshot {i:03d}, call {call} ({r['fetched_utc'][:19]}Z)",
                    "scenarioName": SCENARIO,
                    "requiredScenarioState": state_name(i, call),
                    "newScenarioState": state_name(*nxt),
                    "request": {
                        "method": "GET",
                        "urlPath": "/v2/entities",
                        "queryParameters": {
                            "q": {"equalTo": "vehicleType==bus"},
                            "georel": {"equalTo": f"near;maxDistance:{args.max_distance}"},
                            "geometry": {"equalTo": "point"},
                            "coords": {"equalTo": args.coords},
                        },
                    },
                    "response": {
                        "status": 200,
                        "headers": {"Content-Type": "application/json"},
                        "bodyFileName": body_name,
                        "transformers": ["response-template"],
                    },
                    "metadata": {
                        "source": r["file"],
                        "sha256": r["sha256"],
                        "fetchedUtc": r["fetched_utc"],
                        "vehicles": len(kept),
                    },
                }
                write_json(os.path.join(mappings_dir, f"snapshot-{i:03d}-call-{call}.json"),
                           json.dumps(mapping, indent=2) + "\n")

    print(f"{last} snapshots, {last * args.calls_per_snapshot} mappings, "
          f"{total_buses / last:.1f} buses per snapshot on average, {total_bytes} body bytes")


if __name__ == "__main__":
    main()

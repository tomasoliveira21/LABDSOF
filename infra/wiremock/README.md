# WireMock: simulator of the Porto Digital API

WireMock is the simulator of the Porto Digital urban platform (guide J4, [ADR-008](../../Docs/Technical/adr/0008-transport-data-source.md)). This folder is its root: the `wiremock` container of `infra/compose.yaml` mounts it at `/home/wiremock` ([`local-delivery.md`](../../Docs/Technical/local-delivery.md) §1). It replays real bus positions recorded on 25 October 2026, so the tests and the demo do not depend on the real API.

| Path | What |
|---|---|
| `recordings/porto_bus_responses_20261025.zip` | The raw recordings: 300 responses of the real API and `manifest.csv` (time, vehicle count, SHA-256 of each file). Never edited |
| `scripts/convert_porto_digital.py` | Turns the zip into the stubs below |
| `scripts/check_porto_digital.py` | Plays the stubs on a running WireMock as Transports will call it |
| `mappings/porto-digital/` | Generated: 600 mappings, one per call (300 snapshots × 2 calls) |
| `__files/porto-digital/` | Generated: 300 response bodies, about 9 KB each |

Do not edit the generated files by hand: change the script and run it again. The output is the same on every run.

## What Transports must send

```
GET /v2/entities?q=vehicleType==bus&georel=near;maxDistance:2000&geometry=point&coords=41.1724,-8.6841
```

- `coords` is the Queimódromo (OpenStreetMap), **latitude first**, written exactly like this: the stubs match the text, so another point, or the same point written differently, gets 404 instead of wrong data. GeoJSON positions in the answers are the other way round: longitude first.
- Other parameters, such as `limit=1000`, are ignored.
- Percent-encoded and plain query strings both match.
- Any other request (the whole-city query of the proof of concept, another distance) gets 404.

## How the replay works

- **Only buses within 2 km.** WireMock cannot filter by distance, so the converter keeps, in each snapshot, the buses the real geo-query would return: 2 to 13 per snapshot (routes 200, 202, 205, 208, 500–502, 504–507).
- **Always fresh.** Each `observationDateTime` is a template, `{{now offset='-57 seconds'}}`, with the age the bus had when it was recorded. The data passes the 5 min freshness limit (J1, D2) whenever it is replayed, and each bus keeps its real delay. The rendered value has no fraction of a second (`2026-10-26T20:02:11Z`, where the real API sends `.00Z`): Transports must accept both.
- **Real speed.** A WireMock scenario, `porto-digital-replay`, answers each snapshot twice, then moves to the next. Transports imports every 30 s and the snapshots are about 60 s apart, so the buses move as they did. The 300 snapshots cover 5 h; after the last, the first comes again.
- **One shared state.** The scenario state belongs to the WireMock instance. Tests reset it first: `POST /__admin/scenarios/reset`.

## Regenerate and check

From the repository root, with Python 3.11 or later (standard library only):

```bash
python infra/wiremock/scripts/convert_porto_digital.py --zip infra/wiremock/recordings/porto_bus_responses_20261025.zip --out infra/wiremock --coords 41.1724,-8.6841
```

`--calls-per-snapshot` (default 2) and `--max-distance` (default 2000 m) change the pace and the radius; `--coords` must be what Transports sends.

Until `infra/compose.yaml` exists, WireMock runs on its own, published only on this machine like the admin API in `local-delivery.md` §1 (in Git Bash on Windows, prefix the command with `MSYS_NO_PATHCONV=1`):

```bash
docker run --rm -p 127.0.0.1:8090:8080 -v "$PWD/infra/wiremock:/home/wiremock:ro" wiremock/wiremock:3.13.2
```

Then play the whole scenario (600 calls, about 30 s):

```bash
python infra/wiremock/scripts/check_porto_digital.py --zip infra/wiremock/recordings/porto_bus_responses_20261025.zip --base http://127.0.0.1:8090 --coords 41.1724,-8.6841
```

It checks every call against the zip: the right snapshot in order, only buses within 2 km, every position under 5 min old, each bus within 3 s of its recorded age, the loop, the encoded query, the three requests that must get 404, and the reset. Run on 26 October 2026 with WireMock 3.13.2: all checks passed, median latency 45 ms.

## The recordings

- **Source:** `https://broker.fiware.urbanplatform.portodigital.pt/v2/entities?q=vehicleType==bus&limit=1000`, the whole-city query of the proof of concept (#48): 89 to 118 STCP buses per response, 170 different buses and 56 routes.
- **When:** Sunday 25 October 2026, 15:15 to 20:14 UTC, one response about every 60 s (55–69 s), with no gaps.
- **Content:** NGSI v2 `Vehicle` entities, all with the same 17 attributes. No invalid entries: no missing or 0,0 positions, no duplicates, no positions in the future or older than 5 min.
- **Personal data:** none. The driver's shift (`stcp:nr_turno`) is always `na`; vehicle and trip numbers do not identify a person ([Security and Privacy Assessment](../../Docs/Technical/security-and-privacy-assessment.md), M-13).
- **Terms of use:** not checked yet. The repository is private (guide H8); check Porto Digital's terms and attribution before the data leaves the team.

## Limits

- **Daytime only.** Sunday-afternoon service: day routes only, none of the night network that runs at Queima's hours.
- **No failures in the data.** The 503, timeout and invalid-data cases of D2 (US-42, US-43) are not here: the tests write them as their own stubs, and the presenter adds them through the admin API during the demo (`local-delivery.md` §8).
- **Proxy mode** to the real API, the third use in J4, is configured with the Compose file, not here.

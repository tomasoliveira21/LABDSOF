# ADR-008 · Transport data source

## Status

**Accepted**: proposed by the team on 15 October 2026 and accepted by the professor (answer received by 21 October 2026), who also asked for a simulator or recorded responses under the team's control. Updated on 26 October 2026: WireMock confirmed as that simulator (guide J4, #155). Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) A2 and B2.

## Context

- The assignment asks for integration with at least one external system or a realistic simulator, handling unavailable, late, incomplete or invalid data (§6.5, architecture driver D8).
- F9 shows participants how to get to and from the venue; transport source unavailable is one of the two degraded-mode scenarios (D2, [`features.md` §3.1](../../Product/features.md#31-degraded-mode-scenarios-decided-9-october-2026)).
- The **Porto Digital urban platform** publishes open data through a FIWARE broker (NGSI v2), including the buses in circulation in Porto: `GET /v2/entities?q=vehicleType==bus` ([`domain-model.md`](../domain-model.md)).

## Decision

1. The **Transports** service imports bus positions from the **Porto Digital urban platform** (FIWARE NGSI v2) with a scheduled job **every 30 s** (the *x* of the [meeting with the professor](../../Meetings/2026-10-22-professor-meeting.md) on 22 October 2026, P4) and stores the result; users only read the stored data, never the API. It asks only for the buses within 2 km of the venue (NGSI geo-query). Timeout, retry and circuit-breaker values: guide J1, [`architecture.md`](../architecture.md) §9.1.
2. The import handles the three failure cases: **unavailable** (timeout, HTTP error), **late** (observation older than the freshness limit of **5 min**, the *y* of the same meeting: past it, the app shows a warning) and **invalid or incomplete** data. Invalid data never replaces the last valid data, and each value keeps its observation and retrieval time.
3. Event shuttles and temporary stops are entered by the event team and do not depend on the external source.
4. The team keeps a **simulator of the API, or recorded real responses**, under its control (guide J4). Tool: **WireMock** (proposed in guide J2, #43; confirmed in guide J4 on 26 October 2026, #155), run as a container next to the local system, which proxies to the real API, replays recorded real responses and returns failures on demand. The responses recorded on 25 October 2026 and the stubs made from them are in [`infra/wiremock/`](../../../infra/wiremock/README.md). The source URL is configuration, so Transports can point at either. It is used for repeatable automated tests, to cause failures, delays and invalid data in the degraded-mode demo, and so the presentation does not depend on the real API. The real integration stays the Porto Digital API.

## Consequences

- Real, live data from an open API, with a controlled replacement for tests and the demo.
- One more component to maintain (the simulator or the recordings), and it must stay close to the real NGSI v2 format.
- The source gives **bus positions only**, not timetables or predicted arrivals (domain model, `TransportDeparture`); departures come from manual data or another validated source.
- When the source is down, participants see the last valid data with its time and a warning ([`architecture.md`](../architecture.md) §7 and §9).
- Positions are geographic coordinates, separate from the venue-map coordinates (`GeoPosition` vs `MapPosition`).

## Rationale

- The Porto Digital API gives real, live bus positions from an open source, which is what participants need to leave the venue (F9); the GTFS feed gives static timetables only.
- The professor asked for the real integration and also for a simulator under the team's control; WireMock serves both the repeatable tests and the degraded-mode demo, and the presentation does not depend on the real API.
- A scheduled import with a freshness limit keeps users away from the external API and lets the app show the last valid data with its time when the source fails (§6.5).

## Alternatives considered

| Option | Why not chosen |
|---|---|
| STCP GTFS feed | Static timetables only; public availability was not confirmed |
| Weather API (e.g. IPMA) | Plan B only; less useful to participants than transport data |
| Simulator only, no real API | Fully under our control, but the professor asked for the real integration; the simulator is kept for tests and the demo (decision 4) |

## Open points

- Import interval (x = 30 s) and freshness limit (y = 5 min) decided on 22 October 2026 (guide J1 and J2), as asked in the meeting with the professor (P4).
- Resolved on 26 October 2026: the simulator tool is **WireMock** (decision 4, #155).

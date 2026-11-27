import assert from "node:assert/strict";
import test from "node:test";
import { busLines, formatDistance, formatTime, keepPolling, nextSection, sectionState } from "../src/transport/busView.ts";

const venue = { longitude: -8.6841, latitude: 41.1724 };
const at = "2026-11-26T22:14:00Z";

function bus(vehicleId, routeId, distanceMetres, fleetVehicleId) {
  return { vehicleId, routeId, distanceMetres, fleetVehicleId, position: venue, observedAt: at };
}

function view(routes, buses, dataAsOf = at) {
  return { venue, routes, buses, dataAsOf, stale: false };
}

test("US-41 D11 distances under 1 km in metres rounded to 10 m, otherwise in km with one decimal", () => {
  assert.equal(formatDistance(0), "0 m");
  assert.equal(formatDistance(994), "990 m");
  assert.equal(formatDistance(995), "1,0 km");
  assert.equal(formatDistance(999), "1,0 km");
  assert.equal(formatDistance(1000), "1,0 km");
  assert.equal(formatDistance(1649), "1,6 km");
});

test("US-41 D11 times are Lisbon's HH:MM, in summer and in winter", () => {
  assert.equal(formatTime("2026-07-01T23:59:00Z"), "00:59");
  assert.equal(formatTime("2026-12-01T23:59:00Z"), "23:59");
});

test("US-41 AC-1 one line per bus, nearest first, then one per route without a bus", () => {
  const lines = busLines(view(
    [{ externalRoute: "500", busCount: 2 }, { externalRoute: "502", busCount: 0 },
      { externalRoute: "200", name: "Hospital S. João", busCount: 1 }],
    [bus("a", "500", 350, "3412"), bus("b", "200", 640), bus("c", "500", 1649, "3398")]));

  assert.deepEqual(lines, [
    "Linha 500 · autocarro 3412 · a 350 m · posição às 22:14",
    "Linha 200 (Hospital S. João) · a 640 m · posição às 22:14",
    "Linha 500 · autocarro 3398 · a 1,6 km · posição às 22:14",
    "Linha 502 · nenhum autocarro perto agora",
  ]);
});

test("US-41 D11 a route name with HTML stays text", () => {
  const name = "<img src=x onerror=alert(1)>";

  assert.deepEqual(busLines(view([{ externalRoute: "500", name, busCount: 0 }], [])),
    [`Linha 500 (${name}) · nenhum autocarro perto agora`]);
});

test("US-41 D11 the section shows a list, or says why it cannot", () => {
  assert.equal(sectionState(200, view([{ externalRoute: "500", busCount: 0 }], [])), "list");
  assert.equal(sectionState(200, view([], [])), "noRoutes");
  const noImportYet = view([{ externalRoute: "500", busCount: 0 }], []);
  delete noImportYet.dataAsOf;
  assert.equal(sectionState(200, noImportYet), "noData");
  assert.equal(sectionState(404), "gone");
  assert.equal(sectionState(500), "error");
  assert.equal(sectionState(429), "error");
  assert.equal(sectionState("error"), "error");
  assert.equal(sectionState(200), "error");
});

test("US-41 D12 a 404 stops the polling only after the section has had an answer", () => {
  // Before any answer, Transports' copy of a member who has just joined may still be on its way
  assert.equal(keepPolling("gone", false), true);
  assert.equal(keepPolling("gone", true), false);
  for (const state of ["list", "noRoutes", "noData", "error"]) {
    assert.equal(keepPolling(state, true), true, state);
    assert.equal(keepPolling(state, false), true, state);
  }
});

const listed = view([{ externalRoute: "500", busCount: 1 }], [bus("a", "500", 350, "3412")]);
const loading = { status: "loading", retryNow: true };

test("US-41 D11 a failed poll keeps the last list on screen, never a 'no routes' or 'no data' answer", () => {
  const afterList = nextSection(nextSection(loading, 200, listed), 500);
  assert.equal(afterList.status, "error");
  assert.equal(afterList.view, listed);
  assert.equal(nextSection(afterList, "error").view, listed);

  const noRoutes = nextSection(loading, 200, view([], []));
  assert.equal(noRoutes.status, "noRoutes");
  assert.equal(nextSection(noRoutes, 500).view, undefined);
  assert.equal(nextSection(loading, "error").view, undefined);
});

test("US-41 D12 no 'Tentar novamente' while the gateway asks to wait (429, 503)", () => {
  const shown = nextSection(loading, 200, listed);
  assert.equal(nextSection(shown, 429).retryNow, false);
  assert.equal(nextSection(shown, 503).retryNow, false);
  assert.equal(nextSection(shown, 500).retryNow, true);
  assert.equal(nextSection(shown, "error").retryNow, true);
  assert.equal(nextSection(nextSection(shown, 429), 200, listed).retryNow, true);
});

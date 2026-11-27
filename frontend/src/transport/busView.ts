/** The answer of GET /api/v1/events/{eventId}/bus-observations (Transports contract, US-41 D6). */
export type GeoPosition = { longitude: number; latitude: number };
export type BusObservation = {
  vehicleId: string;
  fleetVehicleId?: string;
  routeId: string;
  position: GeoPosition;
  observedAt: string;
  distanceMetres: number;
};
export type BusRouteSummary = { externalRoute: string; name?: string; busCount: number };
export type BusObservations = {
  venue: GeoPosition;
  routes: BusRouteSummary[];
  dataAsOf?: string;
  stale: boolean;
  buses: BusObservation[];
};

/** What the section shows (D11): the list, one of its messages, or that the event is no longer there. */
export type SectionState = "list" | "noRoutes" | "noData" | "error" | "gone";

const kilometres = new Intl.NumberFormat("pt-PT", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const clock = new Intl.DateTimeFormat("pt-PT", { hour: "2-digit", minute: "2-digit", timeZone: "Europe/Lisbon" });

/** D11: under 1 km in metres rounded to 10 m, otherwise in km with one decimal. */
export function formatDistance(metres: number): string {
  const rounded = Math.round(metres / 10) * 10;
  return rounded < 1000 ? `${rounded} m` : `${kilometres.format(metres / 1000)} km`;
}

/** D11: the time of a position, HH:MM in Lisbon. */
export function formatTime(iso: string): string {
  return clock.format(new Date(iso));
}

function routeLabel(route: string, name?: string) {
  return name ? `Linha ${route} (${name})` : `Linha ${route}`;
}

/** D11: one line per bus, nearest first as the API sends them, then one per route with no bus shown. */
export function busLines(view: BusObservations): string[] {
  const names = new Map(view.routes.map(route => [route.externalRoute, route.name]));
  const buses = view.buses.map(bus => routeLabel(bus.routeId, names.get(bus.routeId))
    + (bus.fleetVehicleId ? ` · autocarro ${bus.fleetVehicleId}` : "")
    + ` · a ${formatDistance(bus.distanceMetres)} · posição às ${formatTime(bus.observedAt)}`);
  const empty = view.routes.filter(route => route.busCount === 0)
    .map(route => `${routeLabel(route.externalRoute, route.name)} · nenhum autocarro perto agora`);
  return [...buses, ...empty];
}

/** D12: how many 404s before any answer the section waits out (about a minute) before saying the event is gone. */
export const EARLY_NOT_FOUND_LIMIT = 6;

/**
 * What the section holds: what it shows, the last list it may keep showing, whether retrying at once is fair, whether
 * it has had an answer, and how many 404s came before the first one.
 */
export type SectionView = {
  status: SectionState | "loading" | "preparing";
  view?: BusObservations;
  retryNow: boolean;
  answered: boolean;
  earlyNotFound: number;
};

export function initialSection(): SectionView {
  return { status: "loading", retryNow: true, answered: false, earlyNotFound: 0 };
}

/**
 * D11, D12: the section after one answer.
 * - A 404 after an answer means the event is gone for this user (removed, suspended). Before any answer it may only
 *   mean that Transports' copy of a member who has just joined is on its way: "preparing", up to
 *   {@link EARLY_NOT_FOUND_LIMIT} times, then "gone".
 * - A failed poll keeps the last list on screen (never a "no routes" or "no data" answer, which would show an empty
 *   map), and offers no immediate retry while the gateway asks to wait (429, 503).
 */
export function nextSection(previous: SectionView, status: number | "error", view?: BusObservations): SectionView {
  const state = sectionState(status, view);
  const base = { answered: previous.answered, earlyNotFound: previous.earlyNotFound, retryNow: true };
  if (state === "gone") {
    const earlyNotFound = previous.answered ? previous.earlyNotFound : previous.earlyNotFound + 1;
    const waiting = !previous.answered && earlyNotFound < EARLY_NOT_FOUND_LIMIT;
    return { ...base, status: waiting ? "preparing" : "gone", earlyNotFound };
  }
  if (state !== "error") return { ...base, status: state, view, answered: true };
  const kept = previous.status === "list" || previous.status === "error" ? previous.view : undefined;
  return { ...base, status: state, view: kept, retryNow: status !== 429 && status !== 503 };
}

/** D12: the polling goes on until the section says the event is gone. */
export function keepPolling(section: SectionView): boolean {
  return section.status !== "gone";
}

/** D11, D12: what one answer shows; a 404 is "gone" (the event is not, or no longer, there for this user). */
export function sectionState(status: number | "error", view?: BusObservations): SectionState {
  if (status === 404) return "gone";
  if (status !== 200 || !view) return "error";
  if (view.routes.length === 0) return "noRoutes";
  if (!view.dataAsOf) return "noData";
  return "list";
}

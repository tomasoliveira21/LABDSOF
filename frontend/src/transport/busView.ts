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

/** D11, D12: a 404 means the event is gone for this user (removed, suspended), so the section stops polling. */
export function sectionState(status: number | "error", view?: BusObservations): SectionState {
  if (status === 404) return "gone";
  if (status !== 200 || !view) return "error";
  if (view.routes.length === 0) return "noRoutes";
  if (!view.dataAsOf) return "noData";
  return "list";
}

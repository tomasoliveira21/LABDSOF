/** A bus position as published by the FIWARE broker, normalised for the app. */
export interface Bus {
  /** NGSI entity id, e.g. `urn:ngsi-ld:Vehicle:porto:stcp:bus:3459`. */
  id: string;
  /** Fleet number painted on the bus, e.g. `3459`. */
  fleetId: string;
  /** Human name from the broker, e.g. `STCP 200 3459`. */
  name: string;
  /** Route (line) code, e.g. `200`. Empty string when unknown. */
  route: string;
  /** Direction of travel on the route (`0` / `1`), or null when unknown. */
  direction: string | null;
  /** Trip identifier (`nr_viagem`), or null. */
  trip: string | null;
  lat: number;
  lon: number;
  /** Speed in km/h. */
  speed: number;
  /** Heading in degrees clockwise from north, or null. */
  heading: number | null;
  tripCount: number | null;
  observedAt: Date | null;
  provider: string;
}

/** Administrative zone a bus is currently in. */
export interface ZoneRef {
  parishId: string;
  parish: string;
  municipalityId: string;
  municipality: string;
}

export interface LocatedBus extends Bus {
  zone: ZoneRef | null;
}

/** Buses at or above this speed (km/h) count as moving; below it, GPS jitter at stops. */
export const MOVING_THRESHOLD_KMH = 2;

export const isMoving = (bus: Bus): boolean => bus.speed >= MOVING_THRESHOLD_KMH;

/** Seconds since the bus last reported its position (null when unknown). */
export function ageSeconds(bus: Bus, now: number): number | null {
  if (!bus.observedAt) return null;
  return Math.max(0, (now - bus.observedAt.getTime()) / 1000);
}

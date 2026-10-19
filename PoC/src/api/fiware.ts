import type { Bus } from '../domain/bus';

/** The endpoint the app reads bus positions from (NGSI v2, normalised representation). */
export const BUSES_URL =
  import.meta.env.VITE_BUSES_URL ??
  'https://broker.fiware.urbanplatform.portodigital.pt/v2/entities?q=vehicleType==bus&limit=1000';

/** Orion's maximum page size. A full page means there may be more entities to fetch. */
const PAGE_SIZE = 1000;
const MAX_PAGES = 10;
const TIMEOUT_MS = 15_000;

type NgsiEntity = Record<string, unknown> & { id: string; type: string };

/** Reads an attribute's value from either the normalised (`{type, value}`) or keyValues shape. */
function attr(entity: NgsiEntity, name: string): unknown {
  const raw = entity[name];
  if (raw !== null && typeof raw === 'object' && 'value' in raw) {
    return (raw as { value: unknown }).value;
  }
  return raw;
}

const asString = (v: unknown): string | null =>
  typeof v === 'string' ? v : typeof v === 'number' ? String(v) : null;

const asNumber = (v: unknown): number | null => {
  const n = typeof v === 'string' ? Number(v) : v;
  return typeof n === 'number' && Number.isFinite(n) ? n : null;
};

/**
 * Parses STCP annotations such as `stcp:route:200` or `stcp:sentido:0` into a key/value map.
 * The value may itself contain colons, so only the first two separators are significant.
 */
export function parseAnnotations(value: unknown): Record<string, string> {
  const out: Record<string, string> = {};
  if (!Array.isArray(value)) return out;
  for (const item of value) {
    if (typeof item !== 'string') continue;
    const match = /^[^:]+:([^:]+):(.*)$/.exec(item);
    if (match) out[match[1]] = match[2];
  }
  return out;
}

const meaningful = (v: string | undefined): string | null =>
  v && v.toLowerCase() !== 'na' ? v : null;

/** Converts one NGSI `Vehicle` entity into a Bus, or null if it has no usable position. */
export function parseVehicle(entity: NgsiEntity): Bus | null {
  const location = attr(entity, 'location') as { type?: string; coordinates?: unknown } | null;
  const coords = location?.type === 'Point' ? location.coordinates : null;
  if (!Array.isArray(coords) || coords.length < 2) return null;
  const lon = asNumber(coords[0]);
  const lat = asNumber(coords[1]);
  if (lon === null || lat === null || (lon === 0 && lat === 0)) return null;

  const annotations = parseAnnotations(attr(entity, 'annotations'));
  const name = asString(attr(entity, 'name')) ?? '';
  const fleetId =
    asString(attr(entity, 'fleetVehicleId')) ?? entity.id.split(':').pop() ?? entity.id;
  // Fall back to the route embedded in the name ("STCP 200 3459") if annotations lack it.
  const route = meaningful(annotations.route) ?? name.split(/\s+/)[1] ?? '';

  const observed = asString(attr(entity, 'observationDateTime'));
  const observedAt = observed ? new Date(observed) : null;

  return {
    id: entity.id,
    fleetId,
    name,
    route: route === fleetId ? '' : route,
    direction: meaningful(annotations.sentido),
    trip: meaningful(annotations.nr_viagem),
    lat,
    lon,
    speed: asNumber(attr(entity, 'speed')) ?? 0,
    heading: asNumber(attr(entity, 'heading')) ?? asNumber(attr(entity, 'bearing')),
    tripCount: asNumber(attr(entity, 'currentTripCount')),
    observedAt: observedAt && !Number.isNaN(observedAt.getTime()) ? observedAt : null,
    provider: asString(attr(entity, 'dataProvider')) ?? '',
  };
}

function withOffset(url: string, offset: number): string {
  const u = new URL(url);
  u.searchParams.set('offset', String(offset));
  return u.toString();
}

/** Fetches every bus from the broker, following pagination if there are more than 1000. */
export async function fetchBuses(signal?: AbortSignal): Promise<Bus[]> {
  const timeout = AbortSignal.timeout(TIMEOUT_MS);
  const combined = signal ? AbortSignal.any([signal, timeout]) : timeout;

  const entities: NgsiEntity[] = [];
  for (let page = 0; page < MAX_PAGES; page++) {
    const url = page === 0 ? BUSES_URL : withOffset(BUSES_URL, page * PAGE_SIZE);
    const res = await fetch(url, { headers: { Accept: 'application/json' }, signal: combined });
    if (!res.ok) throw new Error(`Broker answered HTTP ${res.status} ${res.statusText}`.trim());
    const batch = (await res.json()) as unknown;
    if (!Array.isArray(batch)) throw new Error('Unexpected response from the broker');
    entities.push(...(batch as NgsiEntity[]));
    if (batch.length < PAGE_SIZE) break;
  }

  const buses: Bus[] = [];
  const seen = new Set<string>();
  for (const entity of entities) {
    const bus = parseVehicle(entity);
    if (bus && !seen.has(bus.id)) {
      seen.add(bus.id);
      buses.push(bus);
    }
  }
  return buses;
}

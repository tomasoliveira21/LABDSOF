import { ageSeconds, isMoving, type LocatedBus } from './bus';

export type StatusFilter = 'all' | 'moving' | 'stopped';

export interface CircleArea {
  lat: number;
  lon: number;
  radiusM: number;
}

/**
 * Faceted filter state. An empty set means "no restriction" for that facet, so buses on
 * routes that start running after the user picked a filter are handled predictably.
 */
export interface FilterState {
  search: string;
  routes: Set<string>;
  /** Parish ids. Selecting a whole municipality selects all of its parishes. */
  zones: Set<string>;
  directions: Set<string>;
  status: StatusFilter;
  /** Hide buses whose last report is older than this many minutes (null = keep all). */
  maxAgeMinutes: number | null;
  area: CircleArea | null;
}

export type Facet = 'search' | 'routes' | 'zones' | 'directions' | 'status' | 'age' | 'area';

export const emptyFilters = (): FilterState => ({
  search: '',
  routes: new Set(),
  zones: new Set(),
  directions: new Set(),
  status: 'all',
  maxAgeMinutes: null,
  area: null,
});

export function searchTokens(search: string): string[] {
  return search
    .toLowerCase()
    .split(/[\s,;]+/)
    .filter(Boolean);
}

/** Great-circle distance in metres. */
export function distanceM(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6_371_000;
  const toRad = Math.PI / 180;
  const dLat = (lat2 - lat1) * toRad;
  const dLon = (lon2 - lon1) * toRad;
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * toRad) * Math.cos(lat2 * toRad) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

/** Tests a bus against every facet except `skip` (used to compute facet counts). */
export function matches(bus: LocatedBus, f: FilterState, now: number, skip?: Facet): boolean {
  if (skip !== 'search') {
    const tokens = searchTokens(f.search);
    if (tokens.length) {
      const haystack = `${bus.fleetId} ${bus.route} ${bus.name}`.toLowerCase();
      if (!tokens.some((t) => haystack.includes(t))) return false;
    }
  }
  if (skip !== 'routes' && f.routes.size && !f.routes.has(bus.route)) return false;
  if (skip !== 'zones' && f.zones.size && !(bus.zone && f.zones.has(bus.zone.parishId))) return false;
  if (skip !== 'directions' && f.directions.size && !(bus.direction && f.directions.has(bus.direction))) {
    return false;
  }
  if (skip !== 'status' && f.status !== 'all' && isMoving(bus) !== (f.status === 'moving')) return false;
  if (skip !== 'age' && f.maxAgeMinutes !== null) {
    const age = ageSeconds(bus, now);
    if (age === null || age > f.maxAgeMinutes * 60) return false;
  }
  if (skip !== 'area' && f.area && distanceM(f.area.lat, f.area.lon, bus.lat, bus.lon) > f.area.radiusM) {
    return false;
  }
  return true;
}

export function applyFilters(buses: LocatedBus[], f: FilterState, now: number): LocatedBus[] {
  return buses.filter((bus) => matches(bus, f, now));
}

export interface FacetCounts {
  routes: Map<string, number>;
  parishes: Map<string, number>;
  municipalities: Map<string, number>;
  directions: Map<string, number>;
  status: { moving: number; stopped: number };
}

const bump = (map: Map<string, number>, key: string) => map.set(key, (map.get(key) ?? 0) + 1);

/**
 * How many buses each option would show, given all the *other* active filters — the usual
 * faceted-search behaviour, so counts never claim results the user cannot get.
 */
export function facetCounts(buses: LocatedBus[], f: FilterState, now: number): FacetCounts {
  const counts: FacetCounts = {
    routes: new Map(),
    parishes: new Map(),
    municipalities: new Map(),
    directions: new Map(),
    status: { moving: 0, stopped: 0 },
  };
  for (const bus of buses) {
    if (matches(bus, f, now, 'routes')) bump(counts.routes, bus.route);
    if (bus.zone && matches(bus, f, now, 'zones')) {
      bump(counts.parishes, bus.zone.parishId);
      bump(counts.municipalities, bus.zone.municipalityId);
    }
    if (bus.direction && matches(bus, f, now, 'directions')) bump(counts.directions, bus.direction);
    if (matches(bus, f, now, 'status')) counts.status[isMoving(bus) ? 'moving' : 'stopped']++;
  }
  return counts;
}

export function activeFilterCount(f: FilterState): number {
  return (
    (searchTokens(f.search).length ? 1 : 0) +
    (f.routes.size ? 1 : 0) +
    (f.zones.size ? 1 : 0) +
    (f.directions.size ? 1 : 0) +
    (f.status !== 'all' ? 1 : 0) +
    (f.maxAgeMinutes !== null ? 1 : 0) +
    (f.area ? 1 : 0)
  );
}

// --- persistence -------------------------------------------------------------------------

interface StoredFilters {
  search?: string;
  routes?: string[];
  zones?: string[];
  directions?: string[];
  status?: StatusFilter;
  maxAgeMinutes?: number | null;
  area?: CircleArea | null;
}

export function serializeFilters(f: FilterState): string {
  const stored: StoredFilters = {
    search: f.search,
    routes: [...f.routes],
    zones: [...f.zones],
    directions: [...f.directions],
    status: f.status,
    maxAgeMinutes: f.maxAgeMinutes,
    area: f.area,
  };
  return JSON.stringify(stored);
}

export function deserializeFilters(json: string | null): FilterState {
  const f = emptyFilters();
  if (!json) return f;
  try {
    const s = JSON.parse(json) as StoredFilters;
    const strings = (v: unknown) => (Array.isArray(v) ? v.filter((x) => typeof x === 'string') : []);
    f.search = typeof s.search === 'string' ? s.search : '';
    f.routes = new Set(strings(s.routes));
    f.zones = new Set(strings(s.zones));
    f.directions = new Set(strings(s.directions));
    f.status = s.status === 'moving' || s.status === 'stopped' ? s.status : 'all';
    f.maxAgeMinutes = typeof s.maxAgeMinutes === 'number' && s.maxAgeMinutes > 0 ? s.maxAgeMinutes : null;
    const a = s.area;
    f.area =
      a && [a.lat, a.lon, a.radiusM].every((n) => typeof n === 'number' && Number.isFinite(n))
        ? { lat: a.lat, lon: a.lon, radiusM: a.radiusM }
        : null;
  } catch {
    // Corrupt or outdated storage: start with no filters.
  }
  return f;
}

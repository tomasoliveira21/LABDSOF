import { describe, expect, it } from 'vitest';
import type { LocatedBus } from '../src/domain/bus';
import {
  activeFilterCount,
  applyFilters,
  deserializeFilters,
  emptyFilters,
  facetCounts,
  serializeFilters,
  type FilterState,
} from '../src/domain/filters';
import { compareRoutes, groupRoutes, routeColor } from '../src/domain/routes';

const NOW = Date.parse('2026-10-11T18:45:00Z');

function bus(partial: Partial<LocatedBus> & { id: string }): LocatedBus {
  return {
    fleetId: partial.id,
    name: `STCP ${partial.route ?? '200'} ${partial.id}`,
    route: '200',
    direction: '0',
    trip: null,
    lat: 41.1486,
    lon: -8.611,
    speed: 0,
    heading: 90,
    tripCount: null,
    observedAt: new Date(NOW - 30_000),
    provider: 'STCP',
    zone: { parishId: 'P1', parish: 'Centro', municipalityId: 'M1', municipality: 'Porto' },
    ...partial,
  };
}

const fleet: LocatedBus[] = [
  bus({ id: '3459', route: '200', speed: 20 }),
  bus({ id: '3555', route: '200', direction: '1' }),
  bus({ id: '1001', route: '500', speed: 35, zone: { parishId: 'P2', parish: 'Leça', municipalityId: 'M2', municipality: 'Matosinhos' }, lat: 41.18, lon: -8.69 }),
  bus({ id: '1002', route: '901', observedAt: new Date(NOW - 20 * 60_000), zone: null }),
];

const filters = (patch: Partial<FilterState>): FilterState => ({ ...emptyFilters(), ...patch });
const ids = (list: LocatedBus[]) => list.map((b) => b.id);

describe('applyFilters', () => {
  it('returns every bus when no filter is active', () => {
    expect(ids(applyFilters(fleet, emptyFilters(), NOW))).toEqual(['3459', '3555', '1001', '1002']);
  });

  it('filters by route, zone, direction and status', () => {
    expect(ids(applyFilters(fleet, filters({ routes: new Set(['200']) }), NOW))).toEqual(['3459', '3555']);
    expect(ids(applyFilters(fleet, filters({ zones: new Set(['P2']) }), NOW))).toEqual(['1001']);
    expect(ids(applyFilters(fleet, filters({ directions: new Set(['1']) }), NOW))).toEqual(['3555']);
    expect(ids(applyFilters(fleet, filters({ status: 'moving' }), NOW))).toEqual(['3459', '1001']);
    expect(ids(applyFilters(fleet, filters({ status: 'stopped' }), NOW))).toEqual(['3555', '1002']);
  });

  it('combines facets with AND', () => {
    const f = filters({ routes: new Set(['200']), status: 'moving' });
    expect(ids(applyFilters(fleet, f, NOW))).toEqual(['3459']);
  });

  it('searches several comma-separated bus numbers or routes', () => {
    expect(ids(applyFilters(fleet, filters({ search: '3459, 1001' }), NOW))).toEqual(['3459', '1001']);
    expect(ids(applyFilters(fleet, filters({ search: '901' }), NOW))).toEqual(['1002']);
  });

  it('hides stale reports and buses outside a custom area', () => {
    expect(ids(applyFilters(fleet, filters({ maxAgeMinutes: 5 }), NOW))).toEqual(['3459', '3555', '1001']);
    const area = { lat: 41.1486, lon: -8.611, radiusM: 1000 };
    expect(ids(applyFilters(fleet, filters({ area }), NOW))).toEqual(['3459', '3555', '1002']);
  });
});

describe('facetCounts', () => {
  it('counts each facet ignoring its own selection but honouring the others', () => {
    const counts = facetCounts(fleet, filters({ routes: new Set(['200']), status: 'moving' }), NOW);
    // Route counts ignore the route filter but apply "moving".
    expect(Object.fromEntries(counts.routes)).toEqual({ '200': 1, '500': 1 });
    // Status counts ignore the status filter but apply route 200.
    expect(counts.status).toEqual({ moving: 1, stopped: 1 });
    expect(Object.fromEntries(counts.municipalities)).toEqual({ M1: 1 });
  });
});

describe('filter persistence', () => {
  it('round-trips through JSON and ignores garbage', () => {
    const f = filters({
      search: 'x',
      routes: new Set(['200']),
      zones: new Set(['P1']),
      status: 'stopped',
      maxAgeMinutes: 5,
      area: { lat: 1, lon: 2, radiusM: 300 },
    });
    expect(deserializeFilters(serializeFilters(f))).toEqual(f);
    expect(deserializeFilters('{not json')).toEqual(emptyFilters());
    expect(deserializeFilters('{"status":"flying","routes":[1,"205"]}')).toEqual(
      filters({ routes: new Set(['205']) }),
    );
    expect(activeFilterCount(f)).toBe(6);
  });
});

describe('routes', () => {
  it('sorts naturally and groups by series', () => {
    expect(['901', '13M', '200', '1M', 'ZC', '205'].sort(compareRoutes)).toEqual(['1M', '13M', '200', '205', '901', 'ZC']);
    expect(groupRoutes(['205', '200', '901', '1M', 'ZC', '200']).map((g) => [g.label, g.routes])).toEqual([
      ['200s', ['200', '205']],
      ['900s', ['901']],
      ['Night lines (M)', ['1M']],
      ['Other lines', ['ZC']],
    ]);
  });

  it('gives each route a stable colour', () => {
    expect(routeColor('200')).toBe(routeColor('200'));
    expect(routeColor('200')).not.toBe(routeColor('201'));
  });
});

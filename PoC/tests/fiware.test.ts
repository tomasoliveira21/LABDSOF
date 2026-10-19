import { describe, expect, it } from 'vitest';
import { parseAnnotations, parseVehicle } from '../src/api/fiware';

// Shape of a real entity returned by the Porto Digital broker (trimmed).
const entity = {
  id: 'urn:ngsi-ld:Vehicle:porto:stcp:bus:3459',
  type: 'Vehicle',
  annotations: {
    type: 'Array',
    value: ['stcp:route:200', 'stcp:nr_turno:na', 'stcp:nr_viagem:200_0_1|313|D3|T1|N20', 'stcp:sentido:0'],
    metadata: {},
  },
  bearing: { type: 'Number', value: 205, metadata: {} },
  currentTripCount: { type: 'Number', value: 19, metadata: {} },
  dataProvider: { type: 'Text', value: 'STCP', metadata: {} },
  fleetVehicleId: { type: 'Text', value: '3459', metadata: {} },
  heading: { type: 'Number', value: 205, metadata: {} },
  location: {
    type: 'geo:json',
    value: { type: 'Point', coordinates: [-8.608510017, 41.147819519] },
    metadata: {},
  },
  name: { type: 'Text', value: 'STCP 200 3459', metadata: {} },
  observationDateTime: { type: 'DateTime', value: '2026-10-11T18:42:00.00Z', metadata: {} },
  speed: { type: 'Number', value: 12.5, metadata: {} },
  vehicleType: { type: 'Text', value: 'bus', metadata: {} },
};

describe('parseAnnotations', () => {
  it('maps STCP annotations to key/value pairs, keeping colons and pipes in values', () => {
    expect(parseAnnotations(['stcp:route:200', 'stcp:nr_viagem:a|b:c', 'garbage', 42])).toEqual({
      route: '200',
      nr_viagem: 'a|b:c',
    });
  });

  it('returns an empty map for non-arrays', () => {
    expect(parseAnnotations(undefined)).toEqual({});
  });
});

describe('parseVehicle', () => {
  it('normalises a broker entity', () => {
    const bus = parseVehicle(entity)!;
    expect(bus).toMatchObject({
      id: entity.id,
      fleetId: '3459',
      name: 'STCP 200 3459',
      route: '200',
      direction: '0',
      trip: '200_0_1|313|D3|T1|N20',
      lat: 41.147819519,
      lon: -8.608510017,
      speed: 12.5,
      heading: 205,
      tripCount: 19,
      provider: 'STCP',
    });
    expect(bus.observedAt?.toISOString()).toBe('2026-10-11T18:42:00.000Z');
  });

  it('falls back to the route in the name when annotations are missing', () => {
    const { annotations: _omit, ...rest } = entity;
    expect(parseVehicle(rest)?.route).toBe('200');
    expect(parseVehicle(rest)?.direction).toBeNull();
  });

  it('accepts keyValues-style attributes', () => {
    const bus = parseVehicle({
      id: 'x',
      type: 'Vehicle',
      location: { type: 'Point', coordinates: [-8.6, 41.1] },
      name: 'STCP 501 1234',
      speed: 3,
    });
    expect(bus).toMatchObject({ route: '501', fleetId: 'x', speed: 3, lat: 41.1, lon: -8.6 });
  });

  it('rejects entities without a usable position', () => {
    expect(parseVehicle({ ...entity, location: { type: 'geo:json', value: null, metadata: {} } })).toBeNull();
    expect(
      parseVehicle({ ...entity, location: { type: 'geo:json', value: { type: 'Point', coordinates: [0, 0] } } }),
    ).toBeNull();
  });
});

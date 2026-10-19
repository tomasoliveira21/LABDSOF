import { describe, expect, it } from 'vitest';
import zonesData from '../src/data/zones.json';
import { pointInPolygon, ZoneIndex, type ZoneCollection } from '../src/domain/zones';

const zones = new ZoneIndex(zonesData as unknown as ZoneCollection);

describe('pointInPolygon', () => {
  const square: [number, number][][] = [
    [
      [0, 0],
      [10, 0],
      [10, 10],
      [0, 10],
      [0, 0],
    ],
    [
      [4, 4],
      [6, 4],
      [6, 6],
      [4, 6],
      [4, 4],
    ],
  ];

  it('respects the outer ring and holes', () => {
    expect(pointInPolygon(1, 1, square)).toBe(true);
    expect(pointInPolygon(5, 5, square)).toBe(false); // inside the hole
    expect(pointInPolygon(11, 5, square)).toBe(false);
  });
});

describe('ZoneIndex', () => {
  it('lists the six municipalities served by STCP, each with parishes', () => {
    expect(zones.municipalities.map((m) => m.name).sort()).toEqual(
      ['Gondomar', 'Maia', 'Matosinhos', 'Porto', 'Valongo', 'Vila Nova de Gaia'].sort(),
    );
    for (const m of zones.municipalities) expect(m.parishes.length).toBeGreaterThan(0);
  });

  it.each([
    ['Avenida dos Aliados', 41.1486, -8.6110, 'Porto'],
    ['Casa da Música', 41.1588, -8.6307, 'Porto'],
    ['Matosinhos beach', 41.1781, -8.6896, 'Matosinhos'],
    ['Fórum da Maia', 41.2357, -8.6199, 'Maia'],
    ['Jardim do Morro (Gaia)', 41.1376, -8.6094, 'Vila Nova de Gaia'],
    ['Ermesinde station', 41.2170, -8.5531, 'Valongo'],
    ['Rio Tinto', 41.1785, -8.5590, 'Gondomar'],
  ])('places %s in %s', (_name, lat, lon, municipality) => {
    expect(zones.locate(lon, lat)?.municipality).toBe(municipality);
  });

  it('returns null outside the mapped municipalities', () => {
    expect(zones.locate(-9.1393, 38.7223)).toBeNull(); // Lisbon
  });
});

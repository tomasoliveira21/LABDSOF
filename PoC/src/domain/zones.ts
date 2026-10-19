import type { ZoneRef } from './bus';

type Ring = [number, number][];
type PolygonCoords = Ring[];

export interface ZoneGeometry {
  type: 'Polygon' | 'MultiPolygon';
  coordinates: PolygonCoords | PolygonCoords[];
}

export interface ZoneFeature {
  type: 'Feature';
  properties: {
    kind: 'municipality' | 'parish';
    id: string;
    name: string;
    fullName?: string;
    municipalityId?: string;
    municipality?: string;
  };
  geometry: ZoneGeometry;
}

export interface ZoneCollection {
  type: 'FeatureCollection';
  attribution?: string;
  features: ZoneFeature[];
}

export interface Parish {
  id: string;
  name: string;
  fullName: string;
  municipalityId: string;
  municipality: string;
}

export interface Municipality {
  id: string;
  name: string;
  parishes: Parish[];
}

type BBox = [minLon: number, minLat: number, maxLon: number, maxLat: number];

interface IndexedParish {
  parish: Parish;
  polygons: PolygonCoords[];
  bbox: BBox;
}

/** Ray-casting point-in-ring test. Coordinates are [lon, lat]. */
export function pointInRing(lon: number, lat: number, ring: Ring): boolean {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i];
    const [xj, yj] = ring[j];
    if (yi > lat !== yj > lat && lon < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) {
      inside = !inside;
    }
  }
  return inside;
}

/** True when the point is inside the outer ring and not inside any hole. */
export function pointInPolygon(lon: number, lat: number, polygon: PolygonCoords): boolean {
  if (!polygon.length || !pointInRing(lon, lat, polygon[0])) return false;
  for (let h = 1; h < polygon.length; h++) {
    if (pointInRing(lon, lat, polygon[h])) return false;
  }
  return true;
}

function polygonsOf(geometry: ZoneGeometry): PolygonCoords[] {
  return geometry.type === 'Polygon'
    ? [geometry.coordinates as PolygonCoords]
    : (geometry.coordinates as PolygonCoords[]);
}

function bboxOf(polygons: PolygonCoords[]): BBox {
  const box: BBox = [Infinity, Infinity, -Infinity, -Infinity];
  for (const polygon of polygons) {
    for (const [lon, lat] of polygon[0] ?? []) {
      box[0] = Math.min(box[0], lon);
      box[1] = Math.min(box[1], lat);
      box[2] = Math.max(box[2], lon);
      box[3] = Math.max(box[3], lat);
    }
  }
  return box;
}

/** Looks up which municipality/parish a coordinate falls in. */
export class ZoneIndex {
  readonly municipalities: Municipality[];
  private readonly parishes: IndexedParish[];
  private readonly parishById = new Map<string, Parish>();

  constructor(collection: ZoneCollection) {
    const municipalities = new Map<string, Municipality>();
    for (const f of collection.features) {
      if (f.properties.kind === 'municipality') {
        municipalities.set(f.properties.id, { id: f.properties.id, name: f.properties.name, parishes: [] });
      }
    }
    this.parishes = [];
    for (const f of collection.features) {
      const p = f.properties;
      if (p.kind !== 'parish' || !p.municipalityId) continue;
      const parish: Parish = {
        id: p.id,
        name: p.name,
        fullName: p.fullName ?? p.name,
        municipalityId: p.municipalityId,
        municipality: p.municipality ?? municipalities.get(p.municipalityId)?.name ?? '',
      };
      const polygons = polygonsOf(f.geometry);
      this.parishes.push({ parish, polygons, bbox: bboxOf(polygons) });
      this.parishById.set(parish.id, parish);
      municipalities.get(p.municipalityId)?.parishes.push(parish);
    }
    const byName = (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name, 'pt');
    this.municipalities = [...municipalities.values()].sort(byName);
    for (const m of this.municipalities) m.parishes.sort(byName);
  }

  parish(id: string): Parish | undefined {
    return this.parishById.get(id);
  }

  locate(lon: number, lat: number): ZoneRef | null {
    for (const { parish, polygons, bbox } of this.parishes) {
      if (lon < bbox[0] || lon > bbox[2] || lat < bbox[1] || lat > bbox[3]) continue;
      if (polygons.some((polygon) => pointInPolygon(lon, lat, polygon))) {
        return {
          parishId: parish.id,
          parish: parish.name,
          municipalityId: parish.municipalityId,
          municipality: parish.municipality,
        };
      }
    }
    return null;
  }
}

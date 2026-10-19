// Builds src/data/zones.json: municipality and parish (freguesia) boundaries for the
// municipalities served by STCP, from the official CAOP (DGT) data published by geoapi.pt.
//
// Usage: npm run zones
//
// Geometries are simplified (Ramer–Douglas–Peucker) and rounded so the bundled file stays
// small; ~10 m of precision is plenty for assigning bus positions to zones.

import { writeFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const MUNICIPALITIES = ['Porto', 'Matosinhos', 'Maia', 'Vila Nova de Gaia', 'Gondomar', 'Valongo'];
const API = 'https://json.geoapi.pt/municipio/';
const TOLERANCE = 0.0001; // degrees (~8–11 m at Porto's latitude)
const DECIMALS = 5;

const OUT = resolve(dirname(fileURLToPath(import.meta.url)), '../src/data/zones.json');

function perpendicularDistance([px, py], [ax, ay], [bx, by]) {
  const dx = bx - ax;
  const dy = by - ay;
  if (dx === 0 && dy === 0) return Math.hypot(px - ax, py - ay);
  const t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}

function rdp(points, tolerance) {
  if (points.length < 3) return points;
  const keep = new Uint8Array(points.length);
  keep[0] = keep[points.length - 1] = 1;
  const stack = [[0, points.length - 1]];
  while (stack.length) {
    const [start, end] = stack.pop();
    let maxDist = 0;
    let index = -1;
    for (let i = start + 1; i < end; i++) {
      const d = perpendicularDistance(points[i], points[start], points[end]);
      if (d > maxDist) {
        maxDist = d;
        index = i;
      }
    }
    if (maxDist > tolerance && index !== -1) {
      keep[index] = 1;
      stack.push([start, index], [index, end]);
    }
  }
  return points.filter((_, i) => keep[i]);
}

const round = (n) => Number(n.toFixed(DECIMALS));

function simplifyRing(ring) {
  // A closed ring has first === last; split it so RDP has a real baseline.
  const open = ring.slice(0, -1);
  const mid = Math.floor(open.length / 2);
  const a = rdp(open.slice(0, mid + 1), TOLERANCE);
  const b = rdp(open.slice(mid).concat([open[0]]), TOLERANCE);
  const out = a.concat(b.slice(1)).map(([x, y]) => [round(x), round(y)]);
  return out.length >= 4 ? out : null;
}

function simplifyGeometry(geometry) {
  const simplifyPolygon = (rings) => {
    const out = [];
    for (const [i, ring] of rings.entries()) {
      const s = simplifyRing(ring);
      if (s) out.push(s);
      else if (i === 0) return null; // outer ring collapsed: drop the whole polygon
    }
    return out;
  };
  if (geometry.type === 'Polygon') {
    return { type: 'Polygon', coordinates: simplifyPolygon(geometry.coordinates) };
  }
  if (geometry.type === 'MultiPolygon') {
    const polys = geometry.coordinates.map(simplifyPolygon).filter(Boolean);
    return polys.length === 1
      ? { type: 'Polygon', coordinates: polys[0] }
      : { type: 'MultiPolygon', coordinates: polys };
  }
  throw new Error(`Unsupported geometry type ${geometry.type}`);
}

async function fetchMunicipality(name) {
  const res = await fetch(API + encodeURIComponent(name), { headers: { Accept: 'application/json' } });
  if (!res.ok) throw new Error(`${name}: HTTP ${res.status}`);
  return res.json();
}

const features = [];
for (const name of MUNICIPALITIES) {
  const data = await fetchMunicipality(name);
  const municipalityId = String(data.codigoine ?? data.geojson.properties.Dicofre);
  features.push({
    type: 'Feature',
    properties: { kind: 'municipality', id: municipalityId, name: data.nome },
    geometry: simplifyGeometry(data.geojson.geometry),
  });
  for (const parish of data.geojsons.freguesias) {
    const p = parish.properties;
    const fullName = p.Des_Simpli || p.freguesia || p.nome;
    features.push({
      type: 'Feature',
      properties: {
        kind: 'parish',
        id: String(p.dtmnfr ?? p.Dicofre),
        // "União das freguesias de A, B e C" -> "A, B e C" for compact UI labels.
        name: fullName.replace(/^União das freguesias de\s+/i, ''),
        fullName,
        municipalityId,
        municipality: data.nome,
      },
      geometry: simplifyGeometry(parish.geometry),
    });
  }
  console.log(`${data.nome}: ${data.geojsons.freguesias.length} parishes`);
}

const collection = {
  type: 'FeatureCollection',
  attribution: 'Administrative boundaries: CAOP © Direção-Geral do Território, via geoapi.pt',
  generated: new Date().toISOString().slice(0, 10),
  features,
};

await mkdir(dirname(OUT), { recursive: true });
await writeFile(OUT, JSON.stringify(collection));
console.log(`Wrote ${features.length} features to ${OUT}`);

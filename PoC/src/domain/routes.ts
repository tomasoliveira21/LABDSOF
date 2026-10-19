export interface RouteGroup {
  key: string;
  label: string;
  routes: string[];
}

/**
 * STCP numbers its lines in series (200s, 300s, … 900s); night lines end in "M".
 * The series is used to group the route filter so 70+ lines stay scannable.
 */
export function routeGroupKey(route: string): string {
  if (!route) return 'unknown';
  if (/^\d+M$/i.test(route)) return 'night';
  if (/^\d{3}$/.test(route)) return `${route[0]}00`;
  return 'other';
}

export function routeGroupLabel(key: string): string {
  if (key === 'night') return 'Night lines (M)';
  if (key === 'other') return 'Other lines';
  if (key === 'unknown') return 'No route';
  return `${key}s`;
}

/** Natural order: numeric part first, then suffix ("1M" < "200" < "ZC"). */
export function compareRoutes(a: string, b: string): number {
  return a.localeCompare(b, 'en', { numeric: true, sensitivity: 'base' });
}

/** Numbered series first, then night lines, other lines and buses without a route. */
const groupRank = (key: string): number =>
  key === 'night' ? 10_000 : key === 'other' ? 10_001 : key === 'unknown' ? 10_002 : Number(key);

export function groupRoutes(routes: Iterable<string>): RouteGroup[] {
  const groups = new Map<string, string[]>();
  for (const route of new Set(routes)) {
    const key = routeGroupKey(route);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(route);
  }
  return [...groups.entries()]
    .sort(([a], [b]) => groupRank(a) - groupRank(b))
    .map(([key, list]) => ({ key, label: routeGroupLabel(key), routes: list.sort(compareRoutes) }));
}

/**
 * Stable, well-spread colour per route (same route → same colour across reloads).
 * Numeric routes step round the colour wheel by the golden angle, so consecutive lines
 * (200, 201, 202…) — which usually share a corridor — get clearly different hues.
 */
export function routeColor(route: string): string {
  if (!route) return 'hsl(0 0% 45%)';
  const digits = /^(\d+)/.exec(route);
  let seed: number;
  if (digits) {
    seed = Number(digits[1]) * 137.508 + (route.length > digits[1].length ? 61 : 0);
  } else {
    let hash = 2166136261;
    for (let i = 0; i < route.length; i++) {
      hash ^= route.charCodeAt(i);
      hash = Math.imul(hash, 16777619);
    }
    seed = hash >>> 0;
  }
  const h = Math.round(seed % 360);
  // Yellow/green hues look washed out with white text, so darken them a little more.
  const l = h > 40 && h < 170 ? 34 : 42;
  return `hsl(${h} 70% ${l}%)`;
}

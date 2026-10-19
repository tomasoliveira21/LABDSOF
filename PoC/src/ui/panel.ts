import { ageSeconds, isMoving, type LocatedBus } from '../domain/bus';
import type { FacetCounts, StatusFilter } from '../domain/filters';
import { routeColor, type RouteGroup } from '../domain/routes';
import type { Municipality } from '../domain/zones';
import { compass, esc, formatAge, formatShortAge, formatSpeed, formatTime } from './format';

const lastHtml = new WeakMap<HTMLElement, string>();
const FOCUS_KEYS = ['route', 'group', 'municipality', 'parish', 'expand', 'value', 'id', 'card'] as const;

/**
 * Replaces a container's markup only when it actually changed, and puts keyboard focus back
 * on the equivalent control afterwards (lists are re-rendered on every data refresh).
 */
function setHtml(container: HTMLElement, html: string): void {
  if (lastHtml.get(container) === html) return;
  lastHtml.set(container, html);
  const active = document.activeElement;
  let selector: string | null = null;
  if (active instanceof HTMLElement && container.contains(active)) {
    const key = FOCUS_KEYS.find((k) => active.dataset[k] !== undefined);
    if (key) selector = `[data-${key}="${CSS.escape(active.dataset[key]!)}"]`;
  }
  container.innerHTML = html;
  if (selector) container.querySelector<HTMLElement>(selector)?.focus({ preventScroll: true });
}

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : /(s|sh|ch|x)$/.test(word) ? 'es' : 's'}`;

export function renderRouteGroups(
  container: HTMLElement,
  groups: RouteGroup[],
  counts: Map<string, number>,
  selected: Set<string>,
  query: string,
): void {
  const q = query.trim().toLowerCase();
  const html: string[] = [];
  for (const group of groups) {
    const routes = q ? group.routes.filter((r) => r.toLowerCase().includes(q)) : group.routes;
    if (!routes.length) continue;
    const total = group.routes.reduce((sum, r) => sum + (counts.get(r) ?? 0), 0);
    const picked = group.routes.filter((r) => selected.has(r)).length;
    const state = picked === 0 ? 'none' : picked === group.routes.length ? 'all' : 'some';
    html.push(
      `<div class="route-group">`,
      `<button type="button" class="group-head" data-group="${esc(group.key)}" data-state="${state}"` +
        ` aria-pressed="${state === 'all'}" title="Select every ${esc(group.label)} route">` +
        `<span class="group-check" aria-hidden="true"></span>${esc(group.label)}` +
        `<span class="muted">${plural(total, 'bus')}</span></button>`,
      `<div class="chips">`,
    );
    for (const route of routes) {
      const n = counts.get(route) ?? 0;
      const on = selected.has(route);
      html.push(
        `<button type="button" class="chip${n ? '' : ' is-empty'}" data-route="${esc(route)}" aria-pressed="${on}"` +
          ` style="--route-color:${routeColor(route)}" title="Route ${esc(route || 'unknown')}: ${plural(n, 'bus')}">` +
          `<span class="chip-dot"></span>${esc(route || '—')}<span class="chip-count">${n}</span></button>`,
      );
    }
    html.push(`</div></div>`);
  }
  setHtml(container, html.join('') || `<p class="empty">No route matches “${esc(query)}”.</p>`);
}

export function renderZoneList(
  container: HTMLElement,
  municipalities: Municipality[],
  counts: FacetCounts,
  selected: Set<string>,
  expanded: Set<string>,
  unlocated: number,
): void {
  const html: string[] = [];
  for (const m of municipalities) {
    const picked = m.parishes.filter((p) => selected.has(p.id)).length;
    const n = counts.municipalities.get(m.id) ?? 0;
    const open = expanded.has(m.id);
    html.push(
      `<div class="zone${open ? ' is-open' : ''}">`,
      `<div class="zone-row">`,
      `<label class="check"><input type="checkbox" data-municipality="${esc(m.id)}"` +
        `${picked && picked === m.parishes.length ? ' checked' : ''}` +
        `${picked && picked < m.parishes.length ? ' data-indeterminate' : ''} />` +
        `<span>${esc(m.name)}</span></label>`,
      `<span class="count${n ? '' : ' is-zero'}">${n}</span>`,
      `<button type="button" class="icon-btn expand" data-expand="${esc(m.id)}" aria-expanded="${open}"` +
        ` aria-label="${open ? 'Hide' : 'Show'} parishes of ${esc(m.name)}">▾</button>`,
      `</div>`,
    );
    if (open) {
      html.push(`<div class="parishes">`);
      for (const p of m.parishes) {
        const pn = counts.parishes.get(p.id) ?? 0;
        html.push(
          `<div class="zone-row parish-row"><label class="check" title="${esc(p.fullName)}">` +
            `<input type="checkbox" data-parish="${esc(p.id)}"${selected.has(p.id) ? ' checked' : ''} />` +
            `<span>${esc(p.name)}</span></label><span class="count${pn ? '' : ' is-zero'}">${pn}</span></div>`,
        );
      }
      html.push(`</div>`);
    }
    html.push(`</div>`);
  }
  if (unlocated) {
    html.push(`<p class="hint">${plural(unlocated, 'bus')} outside these municipalities.</p>`);
  }
  setHtml(container, html.join(''));
  container.querySelectorAll<HTMLInputElement>('input[data-indeterminate]').forEach((input) => {
    input.indeterminate = true;
  });
}

interface SegmentOption {
  value: string;
  label: string;
  count?: number;
}

export function renderSegmented(container: HTMLElement, options: SegmentOption[], current: string): void {
  const html = options
    .map(
      (o) =>
        `<button type="button" data-value="${esc(o.value)}" aria-pressed="${o.value === current}">` +
        `${esc(o.label)}${o.count === undefined ? '' : ` <span class="seg-count">${o.count}</span>`}</button>`,
    )
    .join('');
  setHtml(container, html);
}

export function directionOptions(counts: FacetCounts): SegmentOption[] {
  const known = [...counts.directions.keys()].sort();
  const values = known.length ? known : ['0', '1'];
  const total = [...counts.directions.values()].reduce((a, b) => a + b, 0);
  return [
    { value: '', label: 'Both', count: total },
    ...values.map((v) => ({ value: v, label: `Dir. ${v}`, count: counts.directions.get(v) ?? 0 })),
  ];
}

export function statusOptions(counts: FacetCounts): SegmentOption[] {
  const { moving, stopped } = counts.status;
  return [
    { value: 'all' satisfies StatusFilter, label: 'All', count: moving + stopped },
    { value: 'moving' satisfies StatusFilter, label: 'Moving', count: moving },
    { value: 'stopped' satisfies StatusFilter, label: 'Stopped', count: stopped },
  ];
}

export function renderBusList(container: HTMLElement, buses: LocatedBus[], selectedId: string | null, now: number): void {
  if (!buses.length) {
    setHtml(container, `<li class="empty">No buses match the current filters.</li>`);
    return;
  }
  const html = buses
    .map((bus) => {
      const age = ageSeconds(bus, now);
      const where = bus.zone ? `${esc(bus.zone.parish)} · ${esc(bus.zone.municipality)}` : 'Outside mapped zones';
      return (
        `<li><button type="button" class="bus-row${bus.id === selectedId ? ' is-selected' : ''}" data-id="${esc(bus.id)}">` +
        `<span class="route-tag" style="--route-color:${routeColor(bus.route)}">${esc(bus.route || '—')}</span>` +
        `<span class="row-main"><strong>Bus ${esc(bus.fleetId)}</strong><span class="muted">${where}</span></span>` +
        `<span class="row-side"><span class="${isMoving(bus) ? 'moving' : 'stopped'}">` +
        `${isMoving(bus) ? formatSpeed(bus.speed) : 'Stopped'}</span>` +
        `<span class="muted">${formatShortAge(age)}</span></span></button></li>`
      );
    })
    .join('');
  setHtml(container, html);
}

export interface CardContext {
  bus: LocatedBus;
  now: number;
  hiddenByFilters: boolean;
  following: boolean;
  onlyThisRoute: boolean;
}

export function renderBusCard(container: HTMLElement, ctx: CardContext): void {
  const { bus, now } = ctx;
  const age = ageSeconds(bus, now);
  const moving = isMoving(bus);
  const rows: [string, string][] = [
    ['Status', moving ? `Moving · ${formatSpeed(bus.speed)}` : `Stopped${bus.speed ? ` · ${formatSpeed(bus.speed)}` : ''}`],
    ['Heading', bus.heading === null ? '—' : `${Math.round(bus.heading)}° ${compass(bus.heading)}`],
    ['Direction', bus.direction ?? '—'],
    ['Zone', bus.zone ? `${esc(bus.zone.parish)}, ${esc(bus.zone.municipality)}` : 'Outside mapped zones'],
    ['Trip', bus.trip ? `<code>${esc(bus.trip)}</code>` : '—'],
    ['Trips today', bus.tripCount === null ? '—' : String(bus.tripCount)],
    [
      'Last report',
      bus.observedAt
        ? `<span data-age>${formatAge(age)}</span> <span class="muted">(${formatTime(bus.observedAt)})</span>`
        : 'unknown',
    ],
    ['Position', `${bus.lat.toFixed(5)}, ${bus.lon.toFixed(5)}`],
  ];
  const html =
    `<header class="card-head">` +
    `<span class="route-tag large" style="--route-color:${routeColor(bus.route)}">${esc(bus.route || '—')}</span>` +
    `<div><h2>Bus ${esc(bus.fleetId)}</h2><p class="muted">${esc(bus.name || bus.id)}${bus.provider ? ` · ${esc(bus.provider)}` : ''}</p></div>` +
    `<button type="button" class="icon-btn" data-card="close" aria-label="Close details">✕</button>` +
    `</header>` +
    (ctx.hiddenByFilters ? `<p class="card-note">This bus is hidden by the current filters.</p>` : '') +
    `<dl class="card-grid">${rows.map(([k, v]) => `<dt>${k}</dt><dd>${v}</dd>`).join('')}</dl>` +
    `<div class="card-actions">` +
    `<button type="button" class="btn${ctx.following ? ' is-active' : ''}" data-card="follow" aria-pressed="${ctx.following}">` +
    `${ctx.following ? 'Following' : 'Follow'}</button>` +
    (bus.route
      ? `<button type="button" class="btn${ctx.onlyThisRoute ? ' is-active' : ''}" data-card="route" aria-pressed="${ctx.onlyThisRoute}">` +
        `${ctx.onlyThisRoute ? 'Showing only' : 'Only'} route ${esc(bus.route)}</button>`
      : '') +
    `<button type="button" class="btn" data-card="zoom">Zoom to bus</button>` +
    `</div>`;
  setHtml(container, html);
}

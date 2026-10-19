import 'leaflet/dist/leaflet.css';
import './styles.css';
import L from 'leaflet';

import { fetchBuses } from './api/fiware';
import { isMoving, type LocatedBus } from './domain/bus';
import {
  activeFilterCount,
  applyFilters,
  deserializeFilters,
  emptyFilters,
  facetCounts,
  serializeFilters,
  type FacetCounts,
  type StatusFilter,
} from './domain/filters';
import { compareRoutes, groupRoutes } from './domain/routes';
import { ZoneIndex, type ZoneCollection } from './domain/zones';
import zonesData from './data/zones.json';
import { AreaTool } from './map/areaTool';
import { BusLayer } from './map/busLayer';
import { ZoneLayer } from './map/zoneLayer';
import { formatAge, formatDistance, formatTime } from './ui/format';
import {
  directionOptions,
  renderBusCard,
  renderBusList,
  renderRouteGroups,
  renderSegmented,
  renderZoneList,
  statusOptions,
} from './ui/panel';

// --- persistence (best effort: storage can be unavailable in private modes) ---------------

const KEYS = { filters: 'porto-buses.filters', prefs: 'porto-buses.prefs' };

const storage = {
  get(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  },
  set(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      /* ignore */
    }
  },
};

interface Prefs {
  refreshSec: number;
  showTrails: boolean;
  showBoundaries: boolean;
  basemap: string | null;
}

function loadPrefs(): Prefs {
  const defaults: Prefs = { refreshSec: 20, showTrails: true, showBoundaries: false, basemap: null };
  try {
    return { ...defaults, ...(JSON.parse(storage.get(KEYS.prefs) ?? '{}') as Partial<Prefs>) };
  } catch {
    return defaults;
  }
}

// --- state ---------------------------------------------------------------------------------

const zones = new ZoneIndex(zonesData as unknown as ZoneCollection);

const state = {
  buses: [] as LocatedBus[],
  filters: deserializeFilters(storage.get(KEYS.filters)),
  prefs: loadPrefs(),
  lastUpdated: null as number | null,
  lastError: null as string | null,
  loading: false,
  selectedId: null as string | null,
  following: false,
  routeQuery: '',
  expanded: new Set<string>(),
  firstLoad: true,
};

const savePrefs = () => storage.set(KEYS.prefs, JSON.stringify(state.prefs));

const $ = <T extends HTMLElement = HTMLElement>(id: string) => document.getElementById(id) as T;

const ui = {
  app: $('app'),
  liveDot: $('live-dot'),
  liveText: $('live-text'),
  refreshInterval: $<HTMLSelectElement>('refresh-interval'),
  refreshNow: $<HTMLButtonElement>('refresh-now'),
  statVisible: $('stat-visible'),
  statVisibleLabel: $('stat-visible-label'),
  statMoving: $('stat-moving'),
  statRoutes: $('stat-routes'),
  statSpeed: $('stat-speed'),
  search: $<HTMLInputElement>('search'),
  activeFilters: $('active-filters'),
  activeFiltersText: $('active-filters-text'),
  clearAll: $<HTMLButtonElement>('clear-all'),
  routesBadge: $('routes-badge'),
  routeQuery: $<HTMLInputElement>('route-query'),
  routesClear: $<HTMLButtonElement>('routes-clear'),
  routeGroups: $('route-groups'),
  zonesBadge: $('zones-badge'),
  showBoundaries: $<HTMLInputElement>('show-boundaries'),
  zonesClear: $<HTMLButtonElement>('zones-clear'),
  zoneList: $('zone-list'),
  areaStatus: $('area-status'),
  areaPlace: $<HTMLButtonElement>('area-place'),
  areaClear: $<HTMLButtonElement>('area-clear'),
  areaRadius: $<HTMLInputElement>('area-radius'),
  areaRadiusOut: $<HTMLOutputElement>('area-radius-out'),
  busesBadge: $('buses-badge'),
  directionFilter: $('direction-filter'),
  statusFilter: $('status-filter'),
  ageFilter: $<HTMLSelectElement>('age-filter'),
  showTrails: $<HTMLInputElement>('show-trails'),
  listBadge: $('list-badge'),
  busList: $('bus-list'),
  busCard: $('bus-card'),
  sidebarOpen: $<HTMLButtonElement>('sidebar-open'),
  sidebarOpenCount: $('sidebar-open-count'),
  sidebarClose: $<HTMLButtonElement>('sidebar-close'),
  placingBanner: $('placing-banner'),
  toast: $('toast'),
};

// --- map -----------------------------------------------------------------------------------

const PORTO: L.LatLngExpression = [41.1579, -8.6291];
const map = L.map('map', { zoomControl: false }).setView(PORTO, 13);
if (import.meta.env.DEV) Object.assign(window, { __map: map }); // handy for debugging in the console
L.control.zoom({ position: 'topright' }).addTo(map);
L.control.scale({ position: 'bottomright', imperial: false }).addTo(map);

// OpenStreetMap's standard tiles, plus muted and dark renditions of them made with CSS
// filters (see styles.css) so the coloured bus pins stand out. No API key required.
const OSM_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
const osm = (className: string) =>
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: OSM_ATTRIBUTION,
    maxZoom: 19,
    className,
  });
const basemaps: Record<string, L.TileLayer> = {
  Light: osm('tiles-muted'),
  Dark: osm('tiles-dark'),
  OpenStreetMap: osm('tiles-standard'),
};
const prefersDark = window.matchMedia('(prefers-color-scheme: dark)');
const initialBasemap =
  state.prefs.basemap && basemaps[state.prefs.basemap] ? state.prefs.basemap : prefersDark.matches ? 'Dark' : 'Light';
basemaps[initialBasemap].addTo(map);
L.control.layers(basemaps, undefined, { position: 'topright' }).addTo(map);
map.on('baselayerchange', (e: L.LayersControlEvent) => {
  state.prefs.basemap = e.name;
  savePrefs();
});
prefersDark.addEventListener('change', (e) => {
  if (state.prefs.basemap) return; // the user picked a basemap explicitly
  const next = e.matches ? 'Dark' : 'Light';
  for (const [name, layer] of Object.entries(basemaps)) if (name !== next) layer.remove();
  basemaps[next].addTo(map);
});

/** "Fit to visible buses" button under the zoom control. */
const FitControl = L.Control.extend({
  onAdd() {
    const bar = L.DomUtil.create('div', 'leaflet-bar fit-control');
    const button = L.DomUtil.create('a', '', bar);
    button.href = '#';
    button.title = 'Fit map to the buses shown';
    button.setAttribute('role', 'button');
    button.setAttribute('aria-label', 'Fit map to the buses shown');
    button.innerHTML =
      '<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path fill="currentColor" d="M4 4h6v2H6v4H4V4Zm10 0h6v6h-2V6h-4V4ZM4 14h2v4h4v2H4v-6Zm14 0h2v6h-6v-2h4v-4Z"/></svg>';
    L.DomEvent.disableClickPropagation(bar);
    L.DomEvent.on(button, 'click', (e) => {
      L.DomEvent.preventDefault(e);
      fitToVisible();
    });
    return bar;
  },
});
new FitControl({ position: 'topright' }).addTo(map);

// Registered before AreaTool's click handler on purpose: a click that places the area must
// not also clear the selected bus, and Leaflet calls handlers in registration order.
map.on('click', () => {
  if (!areaTool.isPlacing && state.selectedId) select(null);
});

const busLayer = new BusLayer(map, (id) => select(id));
const zoneLayer = new ZoneLayer(map, zonesData as unknown as ZoneCollection, (id) => {
  toggle(state.filters.zones, id);
  update();
});
const areaTool = new AreaTool(
  map,
  (area) => {
    state.filters.area = area;
    update();
  },
  (placing) => {
    zoneLayer.interactive = !placing;
    ui.placingBanner.hidden = !placing;
    ui.areaPlace.textContent = placing ? 'Cancel' : state.filters.area ? 'Move to…' : 'Place on map';
    ui.areaPlace.classList.toggle('is-active', placing);
  },
);

// Animate marker moves only right after fresh data, never during zoom (it would lag behind).
const mapEl = map.getContainer();
let animTimer: number | undefined;
function animateNextMoves(): void {
  mapEl.classList.add('animate-moves');
  window.clearTimeout(animTimer);
  animTimer = window.setTimeout(() => mapEl.classList.remove('animate-moves'), 1400);
}
map.on('zoomstart', () => mapEl.classList.remove('animate-moves'));

// --- rendering -----------------------------------------------------------------------------

let visible: LocatedBus[] = [];

const byRouteThenFleet = (a: LocatedBus, b: LocatedBus) =>
  compareRoutes(a.route, b.route) || compareRoutes(a.fleetId, b.fleetId);

function update(): void {
  const now = Date.now();
  const f = state.filters;
  visible = applyFilters(state.buses, f, now).sort(byRouteThenFleet);
  const counts = facetCounts(state.buses, f, now);

  busLayer.render(visible, now);
  busLayer.setSelected(state.selectedId);
  zoneLayer.setSelection(f.zones);
  renderSidebar(counts, now);
  renderCard(now);
  storage.set(KEYS.filters, serializeFilters(f));
}

function renderSidebar(counts: FacetCounts, now: number): void {
  const f = state.filters;
  const total = state.buses.length;
  const moving = visible.filter(isMoving);
  const loaded = state.lastUpdated !== null;

  ui.statVisible.textContent = loaded ? String(visible.length) : '–';
  ui.statVisibleLabel.textContent = loaded && visible.length !== total ? `of ${total} shown` : 'buses live';
  ui.statMoving.textContent = loaded ? String(moving.length) : '–';
  ui.statRoutes.textContent = loaded ? String(new Set(visible.map((b) => b.route)).size) : '–';
  ui.statSpeed.textContent = moving.length
    ? (moving.reduce((sum, b) => sum + b.speed, 0) / moving.length).toFixed(1)
    : '–';

  const active = activeFilterCount(f);
  ui.activeFilters.hidden = active === 0;
  ui.activeFiltersText.textContent = `${active} filter${active === 1 ? '' : 's'} active`;
  ui.sidebarOpenCount.hidden = active === 0;
  ui.sidebarOpenCount.textContent = String(active);

  // Routes: always list selected routes too, even if they are not running right now.
  const groups = groupRoutes([...state.buses.map((b) => b.route), ...f.routes]);
  renderRouteGroups(ui.routeGroups, groups, counts.routes, f.routes, state.routeQuery);
  ui.routesBadge.textContent = f.routes.size ? `${f.routes.size} selected` : 'All';
  ui.routesBadge.classList.toggle('is-active', f.routes.size > 0);

  const unlocated = state.buses.filter((b) => !b.zone).length;
  renderZoneList(ui.zoneList, zones.municipalities, counts, f.zones, state.expanded, unlocated);
  const zoneBadge = [
    f.zones.size ? `${f.zones.size} parish${f.zones.size === 1 ? '' : 'es'}` : '',
    f.area ? 'area' : '',
  ].filter(Boolean);
  ui.zonesBadge.textContent = zoneBadge.length ? zoneBadge.join(' + ') : 'All';
  ui.zonesBadge.classList.toggle('is-active', zoneBadge.length > 0);

  ui.areaStatus.textContent = f.area ? `${formatDistance(f.area.radiusM)} radius` : 'Not set';
  ui.areaClear.disabled = !f.area;
  if (!areaTool.isPlacing) ui.areaPlace.textContent = f.area ? 'Move to…' : 'Place on map';

  renderSegmented(ui.directionFilter, directionOptions(counts), f.directions.size === 1 ? [...f.directions][0] : '');
  renderSegmented(ui.statusFilter, statusOptions(counts), f.status);
  const busFilters = (f.directions.size ? 1 : 0) + (f.status !== 'all' ? 1 : 0) + (f.maxAgeMinutes !== null ? 1 : 0);
  ui.busesBadge.textContent = busFilters ? `${busFilters} active` : 'All';
  ui.busesBadge.classList.toggle('is-active', busFilters > 0);

  renderBusList(ui.busList, visible, state.selectedId, now);
  ui.listBadge.textContent = String(visible.length);
}

function renderCard(now: number): void {
  const bus = state.selectedId ? state.buses.find((b) => b.id === state.selectedId) : undefined;
  if (!bus) {
    ui.busCard.hidden = true;
    return;
  }
  renderBusCard(ui.busCard, {
    bus,
    now,
    hiddenByFilters: !visible.some((b) => b.id === bus.id),
    following: state.following,
    onlyThisRoute: state.filters.routes.size === 1 && state.filters.routes.has(bus.route),
  });
  ui.busCard.hidden = false;
}

function renderLive(): void {
  const interval = state.prefs.refreshSec;
  let status: 'ok' | 'loading' | 'stale' | 'error' | 'paused' = 'ok';
  let text: string;
  if (state.loading && state.lastUpdated === null) {
    status = 'loading';
    text = 'Loading buses…';
  } else if (state.lastError) {
    status = 'error';
    text = `Update failed${interval ? ' · retrying' : ''}`;
  } else if (state.lastUpdated !== null) {
    const age = (Date.now() - state.lastUpdated) / 1000;
    text = `Updated ${formatAge(age)}`;
    if (!interval) status = 'paused';
    else if (age > interval * 3) status = 'stale';
  } else {
    text = 'Connecting…';
  }
  if (state.loading && state.lastUpdated !== null) {
    status = 'loading';
    text = 'Updating…';
  }
  ui.liveDot.dataset.status = status;
  ui.liveText.textContent = text;
  ui.liveText.title = state.lastError ?? (state.lastUpdated ? `Last update ${formatTime(new Date(state.lastUpdated))}` : '');
  ui.refreshNow.disabled = state.loading;
}

// --- selection -----------------------------------------------------------------------------

function select(id: string | null, opts: { fly?: boolean } = {}): void {
  if (id !== state.selectedId) state.following = false;
  state.selectedId = id;
  const bus = id ? state.buses.find((b) => b.id === id) : undefined;
  if (bus && opts.fly) map.flyTo([bus.lat, bus.lon], Math.max(map.getZoom(), 16), { duration: 0.8 });
  busLayer.setSelected(id);
  ui.busList.querySelectorAll('.bus-row').forEach((row) => {
    row.classList.toggle('is-selected', (row as HTMLElement).dataset.id === id);
  });
  renderCard(Date.now());
}

function fitToVisible(animate = true): void {
  const points = visible.map((b) => L.latLng(b.lat, b.lon));
  if (!points.length) {
    const area = areaTool.bounds() ?? zoneLayer.selectionBounds();
    if (area) map.fitBounds(area, { padding: [40, 40], animate });
    return;
  }
  if (points.length === 1) map.setView(points[0], 16, { animate });
  else map.fitBounds(L.latLngBounds(points), { padding: [48, 48], maxZoom: 16, animate });
}

/** Runs `fn` once the map container has a real size (it can be 0×0 in a hidden tab/pane). */
function whenMapHasSize(fn: () => void): void {
  map.invalidateSize();
  const size = map.getSize();
  if (size.x > 0 && size.y > 0) fn();
  else map.once('resize', () => whenMapHasSize(fn));
}

// --- data loading --------------------------------------------------------------------------

let refreshTimer: number | undefined;
let inflight: AbortController | null = null;

async function refresh(): Promise<void> {
  window.clearTimeout(refreshTimer);
  inflight?.abort();
  const controller = new AbortController();
  inflight = controller;
  state.loading = true;
  renderLive();
  try {
    const buses = await fetchBuses(controller.signal);
    const now = Date.now();
    state.buses = buses.map((bus) => ({ ...bus, zone: zones.locate(bus.lon, bus.lat) }));
    state.lastUpdated = now;
    state.lastError = null;
    ui.toast.hidden = true;
    busLayer.recordPositions(state.buses, now);
    if (!state.firstLoad) animateNextMoves();
    update();
    if (state.firstLoad) {
      state.firstLoad = false;
      if (visible.length) whenMapHasSize(() => fitToVisible(false));
    }
    const followed = state.following && visible.find((b) => b.id === state.selectedId);
    if (followed) map.panTo([followed.lat, followed.lon], { animate: true, duration: 1 });
  } catch (err) {
    if (controller.signal.aborted) return; // superseded by a newer refresh
    state.lastError = err instanceof Error ? err.message : String(err);
    if (err instanceof DOMException && err.name === 'TimeoutError') state.lastError = 'The broker took too long to answer';
    showToast(`Could not load bus positions: ${state.lastError}`);
  } finally {
    if (inflight === controller) {
      inflight = null;
      state.loading = false;
      renderLive();
      scheduleRefresh();
    }
  }
}

function scheduleRefresh(): void {
  window.clearTimeout(refreshTimer);
  if (state.prefs.refreshSec > 0 && !document.hidden) {
    refreshTimer = window.setTimeout(refresh, state.prefs.refreshSec * 1000);
  }
}

// Don't poll the broker from a background tab; catch up as soon as the tab is visible again.
document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    window.clearTimeout(refreshTimer);
    return;
  }
  const age = state.lastUpdated ? (Date.now() - state.lastUpdated) / 1000 : Infinity;
  if (state.prefs.refreshSec > 0 && age >= state.prefs.refreshSec) void refresh();
  else scheduleRefresh();
});

let toastTimer: number | undefined;
function showToast(message: string): void {
  ui.toast.textContent = message;
  ui.toast.hidden = false;
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => (ui.toast.hidden = true), 8000);
}

// --- controls ------------------------------------------------------------------------------

function toggle<T>(set: Set<T>, value: T): void {
  if (set.has(value)) set.delete(value);
  else set.add(value);
}

function syncControls(): void {
  const f = state.filters;
  ui.search.value = f.search;
  ui.ageFilter.value = f.maxAgeMinutes === null ? '' : String(f.maxAgeMinutes);
  ui.refreshInterval.value = String(state.prefs.refreshSec);
  ui.showTrails.checked = state.prefs.showTrails;
  ui.showBoundaries.checked = state.prefs.showBoundaries;
  if (f.area) ui.areaRadius.value = String(f.area.radiusM);
  ui.areaRadiusOut.textContent = formatDistance(Number(ui.areaRadius.value));
  areaTool.setArea(f.area);
}

let searchTimer: number | undefined;
ui.search.addEventListener('input', () => {
  window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(() => {
    state.filters.search = ui.search.value;
    update();
  }, 150);
});

ui.clearAll.addEventListener('click', () => {
  areaTool.stopPlacing();
  state.filters = emptyFilters();
  syncControls();
  update();
});

ui.routeQuery.addEventListener('input', () => {
  state.routeQuery = ui.routeQuery.value;
  update();
});
ui.routesClear.addEventListener('click', () => {
  state.filters.routes.clear();
  update();
});
ui.routeGroups.addEventListener('click', (e) => {
  const target = e.target as HTMLElement;
  const chip = target.closest<HTMLElement>('[data-route]');
  if (chip) {
    toggle(state.filters.routes, chip.dataset.route!);
    update();
    return;
  }
  const head = target.closest<HTMLElement>('[data-group]');
  if (head) {
    const group = groupRoutes([...state.buses.map((b) => b.route), ...state.filters.routes]).find(
      (g) => g.key === head.dataset.group,
    );
    if (!group) return;
    const all = group.routes.every((r) => state.filters.routes.has(r));
    for (const r of group.routes) {
      if (all) state.filters.routes.delete(r);
      else state.filters.routes.add(r);
    }
    update();
  }
});

ui.zoneList.addEventListener('change', (e) => {
  const input = e.target as HTMLInputElement;
  const { municipality, parish } = input.dataset;
  if (parish) toggle(state.filters.zones, parish);
  if (municipality) {
    const m = zones.municipalities.find((x) => x.id === municipality);
    for (const p of m?.parishes ?? []) {
      if (input.checked) state.filters.zones.add(p.id);
      else state.filters.zones.delete(p.id);
    }
  }
  update();
});
ui.zoneList.addEventListener('click', (e) => {
  const button = (e.target as HTMLElement).closest<HTMLElement>('[data-expand]');
  if (!button) return;
  toggle(state.expanded, button.dataset.expand!);
  update();
});
ui.zonesClear.addEventListener('click', () => {
  state.filters.zones.clear();
  update();
});
ui.showBoundaries.addEventListener('change', () => {
  state.prefs.showBoundaries = ui.showBoundaries.checked;
  savePrefs();
  zoneLayer.setVisible(state.prefs.showBoundaries);
});

ui.areaPlace.addEventListener('click', () => {
  if (areaTool.isPlacing) {
    areaTool.stopPlacing();
    return;
  }
  areaTool.startPlacing(Number(ui.areaRadius.value));
  closeSidebarOnMobile();
});
ui.areaClear.addEventListener('click', () => {
  state.filters.area = null;
  areaTool.setArea(null);
  update();
});
let radiusFrame = 0;
ui.areaRadius.addEventListener('input', () => {
  const radiusM = Number(ui.areaRadius.value);
  ui.areaRadiusOut.textContent = formatDistance(radiusM);
  if (!state.filters.area) return;
  state.filters.area = { ...state.filters.area, radiusM };
  areaTool.setArea(state.filters.area);
  cancelAnimationFrame(radiusFrame);
  radiusFrame = requestAnimationFrame(update);
});

ui.directionFilter.addEventListener('click', (e) => {
  const button = (e.target as HTMLElement).closest<HTMLElement>('[data-value]');
  if (!button) return;
  state.filters.directions = new Set(button.dataset.value ? [button.dataset.value] : []);
  update();
});
ui.statusFilter.addEventListener('click', (e) => {
  const button = (e.target as HTMLElement).closest<HTMLElement>('[data-value]');
  if (!button) return;
  state.filters.status = button.dataset.value as StatusFilter;
  update();
});
ui.ageFilter.addEventListener('change', () => {
  state.filters.maxAgeMinutes = ui.ageFilter.value ? Number(ui.ageFilter.value) : null;
  update();
});
ui.showTrails.addEventListener('change', () => {
  state.prefs.showTrails = ui.showTrails.checked;
  savePrefs();
  busLayer.setShowTrails(state.prefs.showTrails, visible);
});

ui.busList.addEventListener('click', (e) => {
  const row = (e.target as HTMLElement).closest<HTMLElement>('[data-id]');
  if (!row) return;
  select(row.dataset.id!, { fly: true });
  closeSidebarOnMobile();
});

ui.busCard.addEventListener('click', (e) => {
  const action = (e.target as HTMLElement).closest<HTMLElement>('[data-card]')?.dataset.card;
  const bus = state.buses.find((b) => b.id === state.selectedId);
  if (!action || !bus) return;
  switch (action) {
    case 'close':
      select(null);
      break;
    case 'follow':
      state.following = !state.following;
      if (state.following) map.flyTo([bus.lat, bus.lon], Math.max(map.getZoom(), 16), { duration: 0.8 });
      renderCard(Date.now());
      break;
    case 'route': {
      const only = state.filters.routes.size === 1 && state.filters.routes.has(bus.route);
      state.filters.routes = new Set(only ? [] : [bus.route]);
      update();
      break;
    }
    case 'zoom':
      map.flyTo([bus.lat, bus.lon], 17, { duration: 0.8 });
      break;
  }
});

ui.refreshInterval.addEventListener('change', () => {
  state.prefs.refreshSec = Number(ui.refreshInterval.value);
  savePrefs();
  renderLive();
  scheduleRefresh();
});
ui.refreshNow.addEventListener('click', () => void refresh());

// Off-canvas sidebar on small screens.
const mobile = window.matchMedia('(max-width: 820px)');
function closeSidebarOnMobile(): void {
  if (mobile.matches) ui.app.classList.remove('sidebar-visible');
}
ui.sidebarOpen.addEventListener('click', () => ui.app.classList.add('sidebar-visible'));
ui.sidebarClose.addEventListener('click', () => ui.app.classList.remove('sidebar-visible'));

// Keep relative times ("12 s ago") fresh between data refreshes.
window.setInterval(() => {
  renderLive();
  const age = ui.busCard.querySelector<HTMLElement>('[data-age]');
  const bus = state.buses.find((b) => b.id === state.selectedId);
  if (age && bus?.observedAt) age.textContent = formatAge((Date.now() - bus.observedAt.getTime()) / 1000);
}, 1000);

// --- boot ----------------------------------------------------------------------------------

syncControls();
zoneLayer.setVisible(state.prefs.showBoundaries);
busLayer.setShowTrails(state.prefs.showTrails, []);
update();
void refresh();

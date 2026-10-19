import L from 'leaflet';
import { ageSeconds, isMoving, type Bus, type LocatedBus } from '../domain/bus';
import { routeColor } from '../domain/routes';
import { esc, formatShortAge, formatSpeed } from '../ui/format';

/** Reports older than this are drawn faded so stale positions are obvious on the map. */
const STALE_AFTER_S = 5 * 60;
const TRAIL_WINDOW_MS = 15 * 60 * 1000;
const TRAIL_MAX_POINTS = 30;

interface TrailPoint {
  latlng: L.LatLng;
  t: number;
}

/** Draws buses as rotated, route-coloured pins and keeps a short position trail per bus. */
export class BusLayer {
  private readonly markers = new Map<string, L.Marker>();
  private readonly trails = new Map<string, TrailPoint[]>();
  private readonly trailLines = new Map<string, L.Polyline>();
  private readonly markerGroup = L.layerGroup();
  private readonly trailGroup = L.layerGroup();
  private selectedId: string | null = null;
  private showTrails = true;

  constructor(
    map: L.Map,
    private readonly onSelect: (id: string) => void,
  ) {
    this.trailGroup.addTo(map);
    this.markerGroup.addTo(map);
  }

  /** Appends the latest position of every bus to its trail (call once per data refresh). */
  recordPositions(buses: Bus[], now: number): void {
    const seen = new Set<string>();
    for (const bus of buses) {
      seen.add(bus.id);
      const trail = this.trails.get(bus.id) ?? [];
      const last = trail[trail.length - 1];
      const latlng = L.latLng(bus.lat, bus.lon);
      if (!last || !last.latlng.equals(latlng)) trail.push({ latlng, t: now });
      while (trail.length > TRAIL_MAX_POINTS || (trail.length && now - trail[0].t > TRAIL_WINDOW_MS)) {
        trail.shift();
      }
      this.trails.set(bus.id, trail);
    }
    for (const id of this.trails.keys()) if (!seen.has(id)) this.trails.delete(id);
  }

  render(visible: LocatedBus[], now: number): void {
    const keep = new Set(visible.map((b) => b.id));
    for (const [id, marker] of this.markers) {
      if (!keep.has(id)) {
        this.markerGroup.removeLayer(marker);
        this.markers.delete(id);
      }
    }
    for (const bus of visible) {
      const marker = this.markers.get(bus.id);
      if (marker) {
        marker.setLatLng([bus.lat, bus.lon]);
        this.decorate(marker, bus, now);
      } else {
        this.markers.set(bus.id, this.create(bus, now));
      }
    }
    this.renderTrails(visible);
  }

  setSelected(id: string | null): void {
    if (this.selectedId === id) return;
    const previous = this.selectedId && this.markers.get(this.selectedId);
    if (previous) {
      previous.getElement()?.classList.remove('is-selected');
      previous.setZIndexOffset(0);
    }
    this.selectedId = id;
    const current = id && this.markers.get(id);
    if (current) {
      current.getElement()?.classList.add('is-selected');
      current.setZIndexOffset(1000);
    }
  }

  setShowTrails(show: boolean, visible: LocatedBus[]): void {
    this.showTrails = show;
    this.renderTrails(visible);
  }

  private create(bus: LocatedBus, now: number): L.Marker {
    const icon = L.divIcon({
      className: 'bus-icon',
      html:
        '<div class="bus-pin"><div class="bus-heading"><span></span></div>' +
        '<div class="bus-body"><span class="bus-label"></span></div></div>',
      iconSize: [32, 32],
      iconAnchor: [16, 16],
    });
    const marker = L.marker([bus.lat, bus.lon], { icon, keyboard: true, riseOnHover: true });
    marker.bindTooltip('', { direction: 'top', offset: [0, -14], opacity: 0.95 });
    marker.on('click', () => this.onSelect(bus.id));
    marker.addTo(this.markerGroup);
    this.decorate(marker, bus, now);
    if (bus.id === this.selectedId) marker.setZIndexOffset(1000);
    return marker;
  }

  /** Updates an existing marker in place so position changes can be animated with CSS. */
  private decorate(marker: L.Marker, bus: LocatedBus, now: number): void {
    const el = marker.getElement();
    const age = ageSeconds(bus, now);
    if (el) {
      el.style.setProperty('--route-color', routeColor(bus.route));
      el.classList.toggle('is-stopped', !isMoving(bus));
      el.classList.toggle('is-stale', age !== null && age > STALE_AFTER_S);
      el.classList.toggle('is-selected', bus.id === this.selectedId);
      el.setAttribute('aria-label', `Route ${bus.route || 'unknown'}, bus ${bus.fleetId}`);
      const heading = el.querySelector<HTMLElement>('.bus-heading');
      if (heading) {
        heading.hidden = bus.heading === null;
        heading.style.transform = `rotate(${bus.heading ?? 0}deg)`;
      }
      const label = el.querySelector<HTMLElement>('.bus-label');
      if (label) {
        label.textContent = bus.route || '?';
        label.classList.toggle('is-long', (bus.route || '?').length > 3);
      }
    }
    const details = [
      isMoving(bus) ? formatSpeed(bus.speed) : 'stopped',
      age === null ? null : `${formatShortAge(age)} ago`,
      bus.zone ? esc(bus.zone.parish) : null,
    ].filter(Boolean);
    marker.setTooltipContent(
      `<strong>${esc(bus.route || '—')}</strong> · bus ${esc(bus.fleetId)}<br>` +
        `<span class="muted">${details.join(' · ')}</span>`,
    );
  }

  private renderTrails(visible: LocatedBus[]): void {
    const wanted = new Set(this.showTrails ? visible.map((b) => b.id) : []);
    for (const [id, line] of this.trailLines) {
      if (!wanted.has(id)) {
        this.trailGroup.removeLayer(line);
        this.trailLines.delete(id);
      }
    }
    if (!this.showTrails) return;
    for (const bus of visible) {
      const points = (this.trails.get(bus.id) ?? []).map((p) => p.latlng);
      const existing = this.trailLines.get(bus.id);
      if (points.length < 2) {
        if (existing) {
          this.trailGroup.removeLayer(existing);
          this.trailLines.delete(bus.id);
        }
        continue;
      }
      if (existing) {
        existing.setLatLngs(points);
        existing.setStyle({ color: routeColor(bus.route) });
      } else {
        const line = L.polyline(points, {
          color: routeColor(bus.route),
          weight: 3,
          opacity: 0.55,
          interactive: false,
          className: 'bus-trail',
        });
        line.addTo(this.trailGroup);
        this.trailLines.set(bus.id, line);
      }
    }
  }
}

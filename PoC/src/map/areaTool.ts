import L from 'leaflet';
import type { CircleArea } from '../domain/filters';

/** A user-placed circular area: click the map to place it, drag the handle to move it. */
export class AreaTool {
  private circle: L.Circle | null = null;
  private handle: L.Marker | null = null;
  private placing = false;
  private placingRadius = 1000;
  private area: CircleArea | null = null;

  constructor(
    private readonly map: L.Map,
    private readonly onChange: (area: CircleArea | null) => void,
    private readonly onPlacingChange: (placing: boolean) => void,
  ) {
    map.on('click', (e: L.LeafletMouseEvent) => {
      if (!this.placing) return;
      this.stopPlacing();
      this.commit({ lat: e.latlng.lat, lon: e.latlng.lng, radiusM: this.placingRadius });
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && this.placing) this.stopPlacing();
    });
  }

  get isPlacing(): boolean {
    return this.placing;
  }

  startPlacing(radiusM: number): void {
    this.placing = true;
    this.placingRadius = radiusM;
    this.map.getContainer().classList.add('is-placing');
    this.onPlacingChange(true);
  }

  stopPlacing(): void {
    if (!this.placing) return;
    this.placing = false;
    this.map.getContainer().classList.remove('is-placing');
    this.onPlacingChange(false);
  }

  /** Syncs the drawing with the filter state (without emitting a change). */
  setArea(area: CircleArea | null): void {
    this.area = area;
    if (!area) {
      this.circle?.remove();
      this.handle?.remove();
      this.circle = this.handle = null;
      return;
    }
    const center = L.latLng(area.lat, area.lon);
    if (!this.circle) {
      this.circle = L.circle(center, { radius: area.radiusM, className: 'area-circle', interactive: false });
      this.circle.addTo(this.map);
      this.handle = L.marker(center, {
        draggable: true,
        icon: L.divIcon({ className: 'area-handle', iconSize: [18, 18], iconAnchor: [9, 9] }),
        title: 'Drag to move the area',
      }).addTo(this.map);
      this.handle.on('drag', (e) => this.circle?.setLatLng((e.target as L.Marker).getLatLng()));
      this.handle.on('dragend', (e) => {
        const p = (e.target as L.Marker).getLatLng();
        this.commit({ lat: p.lat, lon: p.lng, radiusM: this.area?.radiusM ?? area.radiusM });
      });
    } else {
      this.circle.setLatLng(center).setRadius(area.radiusM);
      this.handle?.setLatLng(center);
    }
  }

  bounds(): L.LatLngBounds | null {
    return this.circle ? this.circle.getBounds() : null;
  }

  private commit(area: CircleArea): void {
    this.setArea(area);
    this.onChange(area);
  }
}

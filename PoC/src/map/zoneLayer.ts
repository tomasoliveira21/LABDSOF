import L from 'leaflet';
import type { ZoneCollection } from '../domain/zones';
import { esc } from '../ui/format';

/**
 * Municipality outlines and clickable parish polygons. Selected parishes are always
 * highlighted (so the active zone filter is visible); the rest only when boundaries are on.
 */
export class ZoneLayer {
  private readonly parishes = new Map<string, L.GeoJSON>();
  private readonly municipalityGroup: L.GeoJSON;
  private readonly parishGroup = L.layerGroup();
  private visible = false;
  private selection = new Set<string>();
  /** When false, parish clicks are ignored (e.g. while placing a custom area). */
  interactive = true;

  constructor(
    private readonly map: L.Map,
    collection: ZoneCollection,
    onToggleParish: (id: string) => void,
  ) {
    const municipalities = collection.features.filter((f) => f.properties.kind === 'municipality');
    // `className` must be a top-level option: Leaflet only applies it when the SVG path is
    // created, never through setStyle(). Colours come from CSS so they follow the theme.
    this.municipalityGroup = L.geoJSON(municipalities as unknown as GeoJSON.FeatureCollection, {
      className: 'zone-municipality',
      interactive: false,
      style: { weight: 2, fill: false },
    } as L.GeoJSONOptions);

    for (const feature of collection.features) {
      if (feature.properties.kind !== 'parish') continue;
      const layer = L.geoJSON(feature as unknown as GeoJSON.Feature, {
        className: 'zone-parish',
        style: this.styleFor(false),
      } as L.GeoJSONOptions);
      const p = feature.properties;
      layer.bindTooltip(`<strong>${esc(p.name)}</strong><br><span class="muted">${esc(p.municipality)}</span>`, {
        sticky: true,
        opacity: 0.95,
      });
      layer.on('click', (e) => {
        if (!this.interactive) return;
        L.DomEvent.stopPropagation(e);
        onToggleParish(p.id);
      });
      this.parishes.set(p.id, layer);
    }
    this.parishGroup.addTo(map);
  }

  setVisible(visible: boolean): void {
    this.visible = visible;
    if (visible) this.municipalityGroup.addTo(this.map);
    else this.municipalityGroup.remove();
    this.refresh();
  }

  setSelection(selection: Set<string>): void {
    this.selection = selection;
    this.refresh();
  }

  /** Bounds of the selected parishes, for zooming to the active zone filter. */
  selectionBounds(): L.LatLngBounds | null {
    let bounds: L.LatLngBounds | null = null;
    for (const id of this.selection) {
      const b = this.parishes.get(id)?.getBounds();
      if (b?.isValid()) bounds = bounds ? bounds.extend(b) : L.latLngBounds(b.getSouthWest(), b.getNorthEast());
    }
    return bounds;
  }

  private refresh(): void {
    for (const [id, layer] of this.parishes) {
      const selected = this.selection.has(id);
      const show = this.visible || selected;
      if (show && !this.parishGroup.hasLayer(layer)) this.parishGroup.addLayer(layer);
      if (!show && this.parishGroup.hasLayer(layer)) this.parishGroup.removeLayer(layer);
      if (!show) continue;
      layer.setStyle(this.styleFor(selected));
      layer.eachLayer((path) => (path as L.Path).getElement()?.classList.toggle('is-selected', selected));
    }
    this.municipalityGroup.bringToFront();
  }

  private styleFor(selected: boolean): L.PathOptions {
    return selected
      ? { weight: 2.5, fillOpacity: 0.14, opacity: 0.95, dashArray: undefined }
      : { weight: 1, fillOpacity: 0.02, opacity: 0.5, dashArray: '3 4' };
  }
}

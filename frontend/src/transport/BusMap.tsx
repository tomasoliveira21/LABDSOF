import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import type { BusObservation, GeoPosition } from "./busView";

/** US-41 D10: the OpenStreetMap tiles, with the attribution their policy requires. */
const TILES = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
const ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
/** The import asks for the buses within 2 km of the venue: the map shows that square. */
const AREA_METRES = 4000;

/** A marker's label as text, never HTML: route numbers come from the source (T10). */
function label(text: string, className: string) {
  const element = document.createElement("span");
  element.className = className;
  element.textContent = text;
  return element;
}

/**
 * US-41 D10: a street map centred on the venue, with one marker per bus labelled with its route. It never asks for
 * the device's location. The list below it (BusSection) is its text equivalent and works without the tiles.
 */
export default function BusMap({ venue, buses }: { venue: GeoPosition; buses: BusObservation[] }) {
  const container = useRef<HTMLDivElement>(null);
  const markers = useRef<L.LayerGroup | undefined>(undefined);

  useEffect(() => {
    if (!container.current) return;
    const map = L.map(container.current, { zoomControl: true });
    L.tileLayer(TILES, { maxZoom: 19, attribution: ATTRIBUTION }).addTo(map);
    map.fitBounds(L.latLng(venue.latitude, venue.longitude).toBounds(AREA_METRES));
    L.marker([venue.latitude, venue.longitude], {
      icon: L.divIcon({ className: "bus-venue", html: label("Recinto", "bus-venue-label"), iconSize: [72, 26] }),
      interactive: false, keyboard: false,
    }).addTo(map);
    markers.current = L.layerGroup().addTo(map);
    // Removed on unmount, so opening the page again never finds the container already initialised
    return () => {
      markers.current = undefined;
      map.remove();
    };
  }, [venue.latitude, venue.longitude]);

  useEffect(() => {
    const group = markers.current;
    if (!group) return;
    // Replaced, never added to: a bus that left with the last import leaves the map too
    group.clearLayers();
    for (const bus of buses) {
      L.marker([bus.position.latitude, bus.position.longitude], {
        icon: L.divIcon({ className: "bus-marker", html: label(bus.routeId, "bus-marker-label"), iconSize: [44, 24] }),
        interactive: false, keyboard: false,
      }).addTo(group);
    }
  }, [buses, venue.latitude, venue.longitude]);

  return <div ref={container} className="bus-map" role="region" aria-label="Mapa dos autocarros perto do recinto"/>;
}

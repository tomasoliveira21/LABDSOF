# ADR-007: Venue map representation

- **Status:** Accepted (16 October 2026)
- **Owner:** Tiago (decisions guide F3)
- **Issue:** #44
- **Related:** guide D1 (PostgreSQL, no PostGIS), A2 (the Events service owns the venue map), F4 (React Native setup, open), G1 (deployment platform, open); US-06, US-07, US-08, US-17, US-18

## Context

The venue map (F5, Must) is the shared spatial reference of the product: points of interest (US-06, US-07), help requests (US-08), community reports (US-18) and the operator's view (US-09) are all placed on it.

- The map is **an image uploaded by the event admin**, as an image file or a PDF (US-06). There is **no geographic data**: D1 decided against PostGIS, and the domain model stores positions as `MapPosition`, **normalised coordinates** (x and y between 0 and 1) on a specific `VenueMapVersion`. Bus positions (`GeoPosition`) are a separate coordinate system and are not drawn on this map.
- The map is shown mainly in the **React Native mobile app**. It needs pan and zoom, markers with an icon per type, filtering by type (US-07: *"only those points are highlighted"*), and tapping to mark a location (US-08, US-18).
- Expected load per map: tens to a few hundred points of interest, plus active reports and requests.
- The map must keep working offline once it has been opened (US-17, Could), so a single file that can be cached is preferred.
- F4 (Expo or bare React Native, Bruno) is still open, so the choice must work with both.
- Uploads and point placement happen in the **React web app** (event management, F1).

## Decision

1. **Mobile rendering:** `react-native-svg` for the markers and overlays, inside a zoom/pan container from **`react-native-zoom-toolkit`** (`ResumableZoom`), which is built on `react-native-gesture-handler` and `react-native-reanimated`.
   - The map image is the base layer. Each point is drawn at `x · width`, `y · height` of the displayed image, and its size is divided by the current zoom so icons stay readable.
   - Filtering by type (US-07) is ordinary React state: the selected types are highlighted, the rest are dimmed or hidden.
   - A tap is converted back to normalised coordinates before it is sent to the API, so positions do not depend on screen size or image resolution.
   - If `react-native-zoom-toolkit` falls short, the pinch and pan layer is written directly with Gesture Handler and Reanimated. The dependencies stay the same.
2. **Web app:** no map library. An `<img>` with absolutely positioned markers, using the same normalised coordinates, is enough to place and edit points.
3. **PDF maps become an image on upload, in the backend.** The Events service renders the PDF with **Apache PDFBox 3.x** into a PNG. Rules for every upload:
   - only **single-page** PDFs are accepted, with a file-size limit (the input is untrusted);
   - the rendered image, and any uploaded image larger than that, is scaled down to at most **4096 px on the long side** (some Android devices cannot display larger images);
   - the `VenueMapVersion` keeps both the **original file** and the **rendered image**; clients only ever download the rendered image.
4. Where the files are stored (database, object storage) is **not** decided here; it depends on the deployment platform (G1).

## Consequences

- **Works with any F4 outcome.** `react-native-svg`, Gesture Handler and Reanimated are part of the Expo SDK, and `react-native-zoom-toolkit` is TypeScript on top of them, so the map runs in Expo Go, Expo development builds and bare React Native.
- **Gestures run on the UI thread** (Reanimated), so pan and zoom stay smooth while the JavaScript thread updates markers.
- **One coordinate model end to end:** the backend, the web app and the mobile app all use normalised `MapPosition` on a `VenueMapVersion`; no geo projection is needed.
- **All clients see the same raster**, and the offline cache (US-17) is one PNG per map version.
- **We write the map behaviour ourselves:** marker scaling, hit areas and tap-to-coordinate conversion are our code, and need component tests. There is no built-in clustering; if a map ever has too many points, clustering or a switch to Skia has to be added.
- **The Events service parses untrusted PDFs.** PDFBox runs with size and page limits, and a failed conversion rejects the upload with a clear error instead of saving a broken map version.
- **Library risk:** `react-native-zoom-toolkit` is maintained by a single author. The fallback in Decision 1 limits that risk.

## Alternatives considered

| Option | Why not chosen |
|---|---|
| **WebView + Leaflet `CRS.Simple`** | Mature map behaviour (markers, popups, clustering) for free, and works with Expo and bare React Native. Rejected because it runs a second runtime inside the app: filters and taps go through a `postMessage` bridge, the WebView cannot be tested with React Native Testing Library, accessibility and gesture feel are worse, and the image and Leaflet files must be bundled for offline use. Leaflet's last stable release is 1.9.4 (2023), and 2.0 has been in alpha since 2025. **Fallback** if we need clustering later. |
| **Image-zoom components** (`react-native-image-zoom-viewer`, `react-native-image-pan-zoom`) | Unmaintained since 2020, and built to zoom one image, not an image with interactive markers. |
| **`@likashefqet/react-native-image-zoom`** | Uses Reanimated, but zooms a single image and has no overlay support; last release December 2024. |
| **`@openspacelabs/react-native-zoomable-view`** | Zooms any content, but handles gestures on the JavaScript thread and version 3 is still in beta. |
| **`react-native-maps` with an image overlay** | Needs geographic coordinates (fake ones, here) and a Google Maps API key on Android. Contradicts D1 and the normalised `MapPosition`. |
| **`@shopify/react-native-skia` canvas** | Best performance for thousands of markers, but a heavy dependency and lower-level drawing. Not needed for a few hundred points. |
| **PDF converted on the device** | Uploads happen in the browser, so each client would need its own PDF renderer; on mobile that means a native module (`react-native-pdf`) that does not run in Expo Go. Every client would convert again and could produce a different raster, and offline caching gets harder. |

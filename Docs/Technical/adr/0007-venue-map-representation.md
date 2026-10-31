# ADR-007 · Venue map representation

## Status

**Proposed**: investigated in issue #44 for the React Native app and approved on 22 October 2026. The same day the React Native app was removed and the product became **one React web app** (meeting with the professor, 22 October 2026, P9; [ADR-002](0002-backend-and-frontend-technology.md); #125), so on 23 October 2026 the rendering part (*Decision* 1 and 4) was rewritten for the browser. It waits for review. The rest (normalised coordinates, PDF converted on upload, file storage) does not change. Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) F3; related: D1 (PostgreSQL, no PostGIS), A2 (the Events service owns the venue map), F1 and G1. User stories (numbering of 22 October 2026): US-19 to US-23, US-27, US-46, US-47 ([`features.md`](../../Product/features.md)).

## Context

The venue map (F5, Must) is the shared spatial reference of the product: points of interest (US-20 to US-22), help requests (US-23), community reports (US-47) and the control room's view (US-27) are all placed on it.

- **One React web app** (F1). Participants and field teams open its phone pages in the browser of their phone (iPhone or Android); event management and the control room use its desktop pages on a laptop. The map must work with **touch on phones** and with **mouse and wheel on a laptop**.
- The map is **an image uploaded by an EventAdmin** (or a member with `Map.Edit`), as an image file or a PDF (US-19). There is **no geographic data**: D1 decided against PostGIS, and the domain model stores positions as `MapPosition`, **normalised coordinates** (x and y between 0 and 1) on a specific `VenueMapVersion`. Bus positions (`GeoPosition`) are a separate coordinate system and are not drawn on this map.
- The map needs **pan and zoom**, markers with an **icon per type**, **filtering by type** (US-22: *"only those points are highlighted"*), and **tapping to mark a location** (US-23, US-47).
- Expected load per map: tens to a few hundred points of interest, plus active reports and requests.
- The map should keep working offline once it has been opened (US-46, Could), so a single file that can be cached is preferred.

### What was compared

| Criterion | Why it matters here |
|---|---|
| Touch on phone browsers (Safari on iOS, Chrome on Android) and mouse on a laptop | Same map on phone pages and desktop pages |
| Pan and zoom quality | Participants read a dense map on a small screen, often in a crowd |
| Markers and filtering on top of the image | US-20 to US-23, US-47 |
| Performance | A few hundred markers while zooming, on mid-range phones |
| Maintenance | The app must still build at the end of the project (Sprint C) |
| Testability | The Definition of Done asks for tests |

## Decision

1. **Rendering: the image plus an SVG overlay, inside `react-zoom-pan-pinch`.** The map image is an `<img>`, and the markers are elements of an inline `<svg>` of the same size on top of it. Both sit inside the `TransformWrapper` / `TransformComponent` of `react-zoom-pan-pinch`. The same map component is used by the phone pages (view, filter, mark a location) and by the desktop pages (place and edit points, control room).
   - **Why SVG:** the browser draws it with no dependency. Markers are ordinary React components, so filtering, icons and tests are plain React code, and each marker can have an accessible label.
   - **Why `react-zoom-pan-pinch`:** it zooms **any content**, not only an image, which is what a map with markers needs. It gives pinch and pan on touch, wheel zoom and drag with a mouse, and double-tap or double-click out of the box. It is MIT-licensed and maintained (version 4.2.0, September 2026).
   - **Coordinates:** each point is drawn at `x · width`, `y · height` of the displayed image, and its size is divided by the current zoom so icons stay readable. A tap or click is converted back to normalised coordinates before it is sent to the API, so positions do not depend on the screen size, the device or the image resolution.
   - **Filtering by type (US-22):** React state. The selected types are highlighted and the rest are dimmed.
2. **PDF maps become an image on upload, in the backend.** The Events service renders the PDF with **Apache PDFBox 3.x** (Apache 2.0, Java, so it fits the Spring Boot backend) into a PNG.
   - Only **single-page** PDFs are accepted, with a file-size limit, because the input is untrusted.
   - The rendered image is at most **4096 px on the long side**, and larger uploaded images are scaled down to the same limit. A larger image only costs mobile data and memory on the phone.
   - The `VenueMapVersion` keeps the **original file** and the **rendered image**. Clients only ever download the rendered image.
   - **Why on upload, not in the browser:** see *Alternatives considered*. Converting once means every page, on a phone or a laptop, shows the same raster, which normalised coordinates depend on.
3. Where the files are stored (database or files on the local machine) is **not** decided here; it depends on the deployment (G1, [ADR-011](0011-deployment-and-infrastructure.md)).
4. **Check before the map is built.** Before US-21 and US-22 are implemented, a **spike** runs the map component with a few hundred markers on a **real iPhone (Safari)** and a **real Android phone (Chrome)**: pinch, pan, tap to mark a location, and that the page itself does not zoom instead of the map. **If the spike fails**, the pinch and pan layer is written directly with Pointer Events (about one component). The rest of this decision, the SVG overlay and the coordinate model, stays the same.

## Consequences

- **Nothing to install and one codebase:** the map runs in any current phone or laptop browser, and the phone pages and the desktop pages share the same component.
- **One coordinate model end to end:** the backend and the web app use normalised `MapPosition` on a `VenueMapVersion`; no geo projection is needed.
- **All pages see the same raster.** Offline use (US-46, Could) needs the PNG of the map version cached by a service worker; that is decided with US-46.
- **Testable:** markers, filters and the tap-to-coordinate conversion are React components and functions, tested like the rest of the web app; the PDF conversion is a backend unit test.
- **We write the map behaviour ourselves:** marker scaling, hit areas and tap-to-coordinate conversion are our code and need component tests. There is no built-in clustering; if a map ever has too many points, clustering or a move to a canvas must be added.
- **The Events service parses untrusted PDFs.** PDFBox runs with size and page limits, and a failed conversion rejects the upload with a clear error instead of saving a broken map version.
- **Library risk:** one dependency for pan and zoom. If it stops being maintained or fails the spike, the fallback in *Decision* 4 replaces it without touching the markers.

## Alternatives considered

| Option | Why not chosen |
|---|---|
| **Leaflet `CRS.Simple`** (1.9.4, BSD-2-Clause) | Mature map behaviour (markers, popups, clustering) for free. Rejected because it is a geographic map library used for a flat image, it keeps its own layer model outside React, and its React wrapper `react-leaflet` 5.0.0 uses the Hippocratic licence, which is not an OSI open-source licence. Leaflet 2.0 is still in alpha. **Second fallback** if we need clustering later. |
| **Pinch and pan written by hand** with Pointer Events | No dependency, but the gesture maths (two-finger pinch centre, bounds, wheel zoom on a laptop) becomes our code to test. Kept as the fallback in *Decision* 4. |
| **Canvas drawing** (e.g. a canvas library) | Best performance for thousands of markers, but markers are no longer DOM elements, so tests and accessibility get harder. Not needed for a few hundred points. |
| **PDF converted in the browser** (e.g. pdf.js) | Every page would convert again on every device and could produce a different raster; offline caching gets harder. |
| **React Native app** with `react-native-svg` and `react-native-zoom-toolkit` | The version of this ADR approved on 22 October 2026. Dropped with the React Native app (ADR-002, #125). |

## Open points

- Phone spike on a real iPhone and a real Android phone (*Decision* 4): before US-21 and US-22; owner to be set when the stories are planned.
- Where the original and rendered files are stored: guide G1.
- Upload file-size limit: set when US-19 is specified.

## Sources (checked 23 October 2026)

- npm registry: `react-zoom-pan-pinch` 4.2.0 (MIT), `leaflet` 1.9.4 (BSD-2-Clause), `react-leaflet` 5.0.0 (Hippocratic-2.1).
- Maven Central: Apache PDFBox 3.0.7.

# ADR-007 · Venue map representation

## Status

**Accepted** on 4 November 2026 (#44). The web version of 23 October 2026 was reviewed, as the meeting with the professor of 22 October 2026 asked (P9, guide F3). The review keeps decisions 1 to 4. It adds three things to them:

- **Decision 5**, accessibility, because NFR-25 asks for WCAG 2.1 AA on the venue map.
- **The upload hardening in decision 2.** The security assessment names this ADR as the place where it is decided (T9, finding 10, M-11).
- **The rule for new map versions in decision 3.** It was already taken in the API outline (D5); this ADR now points to it.

The phone location and the phone spike stay open (*Open points*).

History: investigated in issue #44 for the React Native app and approved on 22 October 2026. The same day the React Native app was removed and the product became **one React web app** (meeting with the professor, 22 October 2026, P9; [ADR-002](0002-backend-and-frontend-technology.md); #125), so on 23 October 2026 the rendering part (*Decision* 1 and 4) was rewritten for the browser. The rest (normalised coordinates, PDF converted on upload, file storage) did not change. Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) F3; related: D1 (PostgreSQL, no PostGIS), A2 (the Events service owns the venue map), F1 and G1. User stories (numbering of 22 October 2026): US-19 to US-23, US-27, US-46, US-47 ([`features.md`](../../Product/features.md)).

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
   - **Upload hardening** (added in the review of 4 November 2026; [security assessment](../security-and-privacy-assessment.md) T9, finding 10, M-11). The uploaded file is untrusted:
     - its type is read from the file content, not from its name or its `Content-Type`; only PNG, JPEG and PDF are accepted, and SVG is never accepted, because it can carry scripts;
     - an image's width and height are read from its header before it is decoded, and an image above a pixel limit is refused, so a small file cannot expand into a huge bitmap in memory. The limit is set with US-19 (*Open points*);
     - every upload, image or PDF, is re-encoded to PNG. This drops EXIF data (such as the GPS position of a photo), PDF metadata and any active content;
     - PDFBox runs with a memory limit and a render timeout; a conversion that fails or takes too long rejects the upload with a clear error;
     - the rendered image is served with a fixed `Content-Type: image/png` and `X-Content-Type-Options: nosniff`.
3. Two related points are **not** decided here. The API outline decided both on 23 October 2026:
   - **Where the files are stored** (D6 = A): a volume of the Events service, with the references in its database ([`api-outline.md`](../api-outline.md) §10.1; local deployment, [ADR-011](0011-deployment-and-infrastructure.md)).
   - **What a new map version starts with** (D5 = A): a copy of the previous version's points, which the team then moves.

   Older versions stay available for the help requests and reports that refer to them (`GET …/map-versions/{version}`, US-27). This works because every `MapPosition` names its map version.
4. **Check before the map is built.** Before US-21 and US-22 are implemented, a **spike** runs the map component with a few hundred markers on a **real iPhone (Safari)** and a **real Android phone (Chrome)**: pinch, pan, tap to mark a location, and that the page itself does not zoom instead of the map. **If the spike fails**, the pinch and pan layer is written directly with Pointer Events (about one component). The rest of this decision, the SVG overlay and the coordinate model, stays the same.
5. **Accessibility** (added in the review of 4 November 2026). [NFR-25](../../Product/non-functional-requirements.md) asks for WCAG 2.1 AA on the venue map, checked with an automated checker and by hand with a keyboard and a screen reader. So every task on the map can also be done without a pinch or a drag:
   - **Zoom and pan without a gesture.** Visible zoom-in, zoom-out and reset buttons, reachable with the keyboard, call the library's own controls. The arrow keys pan the map while it has focus. WCAG 2.5.1 asks for a single-pointer alternative to pinch, and 2.1.1 asks for keyboard access.
   - **Every marker can take the focus** and has an accessible name: its type and its name (for example "First aid: Posto 2").
   - **A list of the points** of the current version sits next to the map. It is grouped by type, and the map's filter (US-22) applies to it too. It is the text alternative for screen readers (WCAG 1.1.1), and it also helps on a small screen. It uses the points the current version already returns, so no new endpoint is needed.
   - **Marking a location without a pointer** (US-23, US-47): choosing a point from that list uses that point's position as the location.
   - **Browser zoom stays on.** The page never sets `user-scalable=no` or `maximum-scale` (WCAG 1.4.4). Only the map's own container takes the pinch, with `touch-action: none`, and the spike of decision 4 checks that this works.

   Checked in the accessibility review of Sprint C (assignment §7.3.2, item 5).

## Consequences

- **Nothing to install and one codebase:** the map runs in any current phone or laptop browser, and the phone pages and the desktop pages share the same component.
- **One coordinate model end to end:** the backend and the web app use normalised `MapPosition` on a `VenueMapVersion`; no geo projection is needed.
- **All pages see the same raster.** Offline use (US-46, Could) needs the PNG of the map version cached by a service worker; that is decided with US-46.
- **Testable:** markers, filters and the tap-to-coordinate conversion are React components and functions, tested like the rest of the web app; the PDF conversion is a backend unit test.
- **We write the map behaviour ourselves:** marker scaling, hit areas and tap-to-coordinate conversion are our code and need component tests. There is no built-in clustering; if a map ever has too many points, clustering or a move to a canvas must be added.
- **The Events service parses untrusted PDFs.** PDFBox runs with size and page limits, and a failed conversion rejects the upload with a clear error instead of saving a broken map version.
- **Every upload is decoded and re-encoded on the server**, within the limits of decision 2. It costs some CPU per upload, and uploads are rare (a few map versions per event).
- **The accessible map is our code too.** The zoom buttons, the keyboard pan, the focusable markers and the list of points need component tests, like the rest of the map.
- **Library risk:** one dependency for pan and zoom. If it stops being maintained or fails the spike, the fallback in *Decision* 4 replaces it without touching the markers.

## Alternatives considered

| Option | Why not chosen |
|---|---|
| **Leaflet `CRS.Simple`** (1.9.4, BSD-2-Clause) | Mature map behaviour (markers, popups, clustering) for free. Rejected because it is a geographic map library used for a flat image, it keeps its own layer model outside React, and its React wrapper `react-leaflet` 5.0.0 uses the Hippocratic licence, which is not an OSI open-source licence. Leaflet 2.0 is still in alpha. **Second fallback** if we need clustering later. |
| **Pinch and pan written by hand** with Pointer Events | No dependency, but the gesture maths (two-finger pinch centre, bounds, wheel zoom on a laptop) becomes our code to test. Kept as the fallback in *Decision* 4. |
| **Canvas drawing** (e.g. a canvas library) | Best performance for thousands of markers, but markers are no longer DOM elements, so tests and accessibility get harder. Not needed for a few hundred points. |
| **PDF converted in the browser** (e.g. pdf.js) | Every page would convert again on every device and could produce a different raster; offline caching gets harder. |
| **Map only, with no list of points** | Less to build, but zoom and pan by gesture and markers drawn on an image do not meet WCAG 2.1 AA on their own (NFR-25). A list of the places is the usual text alternative for a map. |
| **React Native app** with `react-native-svg` and `react-native-zoom-toolkit` | The version of this ADR approved on 22 October 2026. Dropped with the React Native app (ADR-002, #125). |

## Open points

- **Phone location (1 November 2026, [record](../../Meetings/2026-11-01-sprint-a-close-decisions.md), #175):** product decision 4 of 30 October 2026 (phone location first, map as the fallback) is phased. Sprint B uses map points only. Before the phone's location arrives in Sprint C, this ADR must say how a phone position (latitude, longitude, accuracy) is placed on a map version with no geographic data, e.g. two or three reference points set by the EventAdmin; assessment M-1 changes with it.
- Phone spike on a real iPhone and a real Android phone (*Decision* 4): before US-21 and US-22; owner to be set when the stories are planned.
- **Image size against SC1** (found in the review of 4 November 2026). [SC1](../../Product/product-vision.md#5-measurable-success-criteria) asks for the venue map in at most 5 s on a slow 3G profile, cold. A PNG of up to 4096 px can weigh far more than fits in that time. The spike measures it with a real venue map. If it does not fit, the Events service also renders a smaller image that phones load first, and the full image only when the map is zoomed in.
- **Pixel limit for uploaded images** (*Decision* 2): set when US-19 is specified.
- ~~Where the original and rendered files are stored: guide G1.~~ Closed: a volume of the Events service ([`api-outline.md`](../api-outline.md) §10.1, decision D6).
- ~~Upload file-size limit: set when US-19 is specified.~~ Closed: 20 MB at the gateway for the map upload route ([ADR-013](0013-api-gateway.md), limit L-4).

## Sources (checked 23 October 2026)

- npm registry: `react-zoom-pan-pinch` 4.2.0 (MIT), `leaflet` 1.9.4 (BSD-2-Clause), `react-leaflet` 5.0.0 (Hippocratic-2.1).
- Maven Central: Apache PDFBox 3.0.7.
- W3C, [WCAG 2.1](https://www.w3.org/TR/WCAG21/) (checked 4 November 2026): 1.1.1 Non-text Content, 1.4.4 Resize Text, 2.1.1 Keyboard and 2.5.1 Pointer Gestures.

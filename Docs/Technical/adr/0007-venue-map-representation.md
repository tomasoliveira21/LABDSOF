# ADR-007 · Venue map representation

## Status

**Accepted**: investigated by Tiago (issue #44) and approved on 22 October 2026, subject to the compatibility check in *Decision* 5. Source: [`architecture-decisions-guide.md`](../architecture-decisions-guide.md) F3; related: D1 (PostgreSQL, no PostGIS), A2 (the Events service owns the venue map), F1, F4 (React Native setup, open) and G1 (deployment platform, open). User stories (numbering of 22 October 2026): US-19 to US-23, US-27, US-46, US-47 ([`features.md`](../../Product/features.md)).

## Context

The venue map (F5, Must) is the shared spatial reference of the product: points of interest (US-20 to US-22), help requests (US-23), community reports (US-47) and the control room's view (US-27) are all placed on it.

- **The product is mainly a mobile app.** Participants, operators and responders use the React Native app on iOS and Android (F1). The map library must therefore work well on **both iOS and Android**; the web app only needs it for event management.
- The map is **an image uploaded by an EventAdmin** (or a member with `Map.Edit`), as an image file or a PDF (US-19). There is **no geographic data**: D1 decided against PostGIS, and the domain model stores positions as `MapPosition`, **normalised coordinates** (x and y between 0 and 1) on a specific `VenueMapVersion`. Bus positions (`GeoPosition`) are a separate coordinate system and are not drawn on this map.
- The mobile map needs **pan and zoom**, markers with an **icon per type**, **filtering by type** (US-22: *"only those points are highlighted"*), and **tapping to mark a location** (US-23, US-47).
- Expected load per map: tens to a few hundred points of interest, plus active reports and requests.
- The map must keep working offline once it has been opened (US-46, Could), so a single file that can be cached is preferred.
- **F4 (Expo or bare React Native, Bruno) is still open**, so the choice must work with both.
- Uploads and point placement happen in the **React web app** (event management, F1).

### What was compared

| Criterion | Why it matters here |
|---|---|
| iOS and Android support | The app is mobile first |
| Expo and bare React Native | F4 is open |
| Pan and zoom quality | Participants read a dense map on a small screen, often in a crowd |
| Markers and filtering on top of the image | US-20 to US-23, US-47 |
| Performance | A few hundred markers while zooming, on mid-range phones |
| Maintenance | The app must still build at the end of the project (Sprint C) |
| Testability | The Definition of Done asks for tests |

## Decision

1. **Mobile rendering: `react-native-svg` + `react-native-zoom-toolkit`.** The map image is the base layer, and the markers are `react-native-svg` elements on top of it, inside a `ResumableZoom` container from `react-native-zoom-toolkit`. The zoom library is built on `react-native-gesture-handler` and `react-native-reanimated`.
   - **Why `react-native-svg`:** it is the standard vector library for React Native (Software Mansion, MIT, version 15.16.0 released on 15 October 2026). It has native components for **iOS and Android** and supports the New Architecture. Expo includes it in its SDK. Markers are ordinary React components, so filtering, icons and tests are plain React code.
   - **Why `react-native-zoom-toolkit`:** it zooms **any content**, not only an image, which is what a map with markers needs. It gives pinch, pan, double-tap and tap callbacks out of the box. Gestures run on the **UI thread** through Reanimated, so zooming stays smooth on iOS and Android while JavaScript updates the markers. It is MIT-licensed and maintained (version 5.1.1, August 2026), and it is listed as Expo-compatible because it only uses modules from the Expo SDK.
   - **Coordinates:** each point is drawn at `x · width`, `y · height` of the displayed image, and its size is divided by the current zoom so icons stay readable. A tap is converted back to normalised coordinates before it is sent to the API, so positions do not depend on the screen size, the platform or the image resolution.
   - **Filtering by type (US-22):** React state. The selected types are highlighted and the rest are dimmed.
2. **Web app: no map library.** An `<img>` with absolutely positioned markers, using the same normalised coordinates, is enough to place and edit points. It does not justify another dependency.
3. **PDF maps become an image on upload, in the backend.** The Events service renders the PDF with **Apache PDFBox 3.x** (Apache 2.0, Java, so it fits the Spring Boot backend) into a PNG.
   - Only **single-page** PDFs are accepted, with a file-size limit, because the input is untrusted.
   - The rendered image is at most **4096 px on the long side**, and larger uploaded images are scaled down to the same limit. Some Android devices cannot display bigger images, and a larger file only costs mobile data.
   - The `VenueMapVersion` keeps the **original file** and the **rendered image**. Clients only ever download the rendered image.
   - **Why on upload, not on the device:** see *Alternatives considered*. Converting once means every client, on iOS, Android or the web, shows the same raster, which normalised coordinates depend on.
4. Where the files are stored (database, object storage) is **not** decided here; it depends on the deployment platform (G1).
5. **Compatibility check before the map is built.** `react-native-zoom-toolkit` 5.1.1 is tested by its author with Expo SDK 55 (React Native 0.83, Gesture Handler 2.30, Reanimated 4.2). The current Expo SDK (58) ships React Native 0.88 with **Gesture Handler 3**. Gesture Handler 3 introduces a new hook-based API but still accepts the older gesture API that the library uses, so it is expected to work. That has not been proven yet, so:
   - before US-21 and US-22 are implemented, a **spike** runs a `ResumableZoom` with SVG markers on a **real iOS device and a real Android device**, using the setup chosen in F4;
   - dependency versions are installed with `npx expo install` (or pinned to the same versions in bare React Native);
   - the `onSwipe` and `onOverPanning` props are not used: they crash under Reanimated 4 (upstream issue #136, open);
   - **if the spike fails**, the pinch and pan layer is written directly with Gesture Handler and Reanimated (about one component). The rest of this decision, `react-native-svg` and the coordinate model, stays the same.

## Consequences

- **Works on iOS and Android, with any F4 outcome.** `react-native-svg`, Gesture Handler and Reanimated are part of the Expo SDK and have native code for both platforms, and `react-native-zoom-toolkit` is TypeScript on top of them. The map runs in Expo Go, Expo development builds and bare React Native.
- **Requires the New Architecture.** Reanimated 4 and Gesture Handler 3 no longer support the old architecture. This is the default in current Expo and React Native, so it does not restrict F4.
- **One coordinate model end to end:** the backend, the web app and the mobile app all use normalised `MapPosition` on a `VenueMapVersion`; no geo projection is needed.
- **All clients see the same raster**, and the offline cache (US-46) is one PNG per map version.
- **Testable:** markers and filters are React Native components and can be tested with React Native Testing Library; the PDF conversion is a backend unit test.
- **We write the map behaviour ourselves:** marker scaling, hit areas and tap-to-coordinate conversion are our code and need component tests. There is no built-in clustering; if a map ever has too many points, clustering or a move to Skia must be added.
- **The Events service parses untrusted PDFs.** PDFBox runs with size and page limits, and a failed conversion rejects the upload with a clear error instead of saving a broken map version.
- **Library risk:** `react-native-zoom-toolkit` has a single maintainer, does not yet declare its peer dependencies (upstream issue #134) and has not been tested by its author with Gesture Handler 3. The spike and the fallback in *Decision* 5 contain that risk.

## Alternatives considered

| Option | iOS / Android | Expo / bare | Why not chosen |
|---|---|---|---|
| **WebView + Leaflet `CRS.Simple`** | ✅ / ✅ | ✅ / ✅ | Mature map behaviour (markers, popups, clustering) for free. Rejected because it runs a web page inside the app: filters and taps go through a `postMessage` bridge, gestures feel like a web page rather than a native app, the WebView cannot be tested with React Native Testing Library, accessibility is worse, and the image and Leaflet files must be bundled for offline use. Leaflet's last stable release is 1.9.4 (2023), and 2.0 has been in alpha since 2025. **Second fallback** if we need clustering later. |
| **Image-zoom components** (`react-native-image-zoom-viewer`, `react-native-image-pan-zoom`) | ✅ / ✅ | Partly | Unmaintained since 2020, and built to zoom one image, not an image with interactive markers. |
| **`@likashefqet/react-native-image-zoom`** | ✅ / ✅ | ✅ / ✅ | Uses Reanimated, but zooms a single image and has no support for overlays; last release December 2024. |
| **`@openspacelabs/react-native-zoomable-view`** | ✅ / ✅ | ✅ / ✅ | Zooms any content, but handles gestures on the JavaScript thread, which is less smooth on mid-range Android phones, and version 3 is still in beta. |
| **`react-native-maps` with an image overlay** | ✅ / ✅ | Development build / ✅ | Needs geographic coordinates (fake ones, here) and a Google Maps API key on Android. Contradicts D1 and the normalised `MapPosition`. |
| **`@shopify/react-native-skia` canvas** | ✅ / ✅ | ✅ / ✅ | Best performance for thousands of markers, but a heavy dependency and lower-level drawing. Not needed for a few hundred points. |
| **PDF converted on the device** | — | — | Uploads happen in the browser, so each client would need its own PDF renderer; on mobile that means a native module (`react-native-pdf`) that does not run in Expo Go. Every client would convert again and could produce a different raster, and offline caching gets harder. |

## Open points

- Compatibility spike on real iOS and Android devices (*Decision* 5): done with F4's setup, before US-21 and US-22; owner to be set when the stories are planned.
- Where the original and rendered files are stored: guide G1, Tomás.
- Upload file-size limit: set when US-19 is specified.

## Sources (checked 22 October 2026)

- npm registry: `react-native-svg` 15.16.0, `react-native-zoom-toolkit` 5.1.1, `react-native-gesture-handler` 3.4.0, `react-native-reanimated` 4.7.1, `react-native-webview` 14.0.1, `leaflet` 1.9.4 (2.0.0-alpha.1), `react-native-image-zoom-viewer` 3.0.1 (2020), `@likashefqet/react-native-image-zoom` 4.3.0 (2024), `@openspacelabs/react-native-zoomable-view` 3.0.0-beta.6.
- Expo SDK 58 bundled native modules (`expo/packages/expo/bundledNativeModules.json`): React Native 0.88.0, `react-native-svg` 15.16.0, `react-native-gesture-handler` ~3.3.0, `react-native-reanimated` 4.7.1.
- [`react-native-zoom-toolkit`](https://github.com/Glazzes/react-native-zoom-toolkit) documentation and issues #134 and #136.
- [Upgrading to Gesture Handler 3](https://docs.swmansion.com/react-native-gesture-handler/docs/guides/upgrading-to-3/).
- Maven Central: Apache PDFBox 3.0.7.

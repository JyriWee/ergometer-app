# Android 17 Adaptive UI Checklist (App-Wide, API 37)

Last verified: 2026-02-14

## Purpose
This checklist tracks Android 17 large-screen requirements across the entire ErgometerApp, not only the session screen.

## Confirmed platform changes
- Android 17 Beta 1 was released on 2026-02-13.
- Android 17 introduces mandatory large-screen adaptivity for apps targeting API 37.
- On displays with `smallest width >= 600dp`, Android 17 ignores app-level restrictions for:
  - `screenOrientation`
  - `resizeableActivity`
  - `minAspectRatio`
  - `maxAspectRatio`
  - app APIs that attempt to force fixed orientation
- Result: the app must adapt to current window bounds and orientation on tablets, foldables, and desktop windowing.

## ErgometerApp baseline audit (2026-02-14)
- `app/build.gradle.kts` uses `targetSdk = 36`.
- `app/src/main/AndroidManifest.xml` does not define orientation/aspect locks.
- Kotlin source audit shows no `setRequestedOrientation` usage.
- Top-level app destinations are rendered from:
  - `app/src/main/java/com/example/ergometerapp/ui/MainActivityContent.kt`

## App-wide surface inventory
- `MENU`: `MenuScreen` in `app/src/main/java/com/example/ergometerapp/ui/Screens.kt`
- `CONNECTING`: `ConnectingScreen` in `app/src/main/java/com/example/ergometerapp/ui/Screens.kt`
- `SESSION`: `SessionScreen` in `app/src/main/java/com/example/ergometerapp/ui/Screens.kt`
- `SUMMARY`: `SummaryScreen` in `app/src/main/java/com/example/ergometerapp/ui/Screens.kt`
- Debug overlay: `FtmsDebugTimelineScreen` mounted from `MainActivityContent`
- Shared chart component: `WorkoutProfileChart` in `app/src/main/java/com/example/ergometerapp/ui/components/WorkoutProfileChart.kt`

## App-wide requirements before targetSdk 37
- [ ] All top-level destinations remain usable at `sw600dp+` in both portrait and landscape.
- [ ] All top-level destinations remain usable in split-screen and dynamically resized windows.
- [ ] No primary action is clipped, hidden, or pushed off-screen without scroll recovery.
- [ ] Main session controls remain reachable with debug overlay shown and hidden.
- [ ] Session state remains stable across orientation changes and window size changes.
- [ ] Workout import and session start flow remain actionable on large screens.
- [ ] Summary remains fully readable and navigable on tablet and multi-window layouts.

## Per-surface compliance matrix

### MENU screen
- [ ] Workout import status text wraps cleanly at narrow and medium widths.
- [ ] Workout chart preview does not overlap actions.
- [ ] Start button remains visible/reachable at small window heights.

### CONNECTING screen
- [ ] Connecting status remains centered and readable across size classes.
- [ ] No persistent blank regions caused by orientation assumptions.

### SESSION screen
- [ ] Wide layout split (`>= 900dp`) remains balanced and scannable.
- [ ] Narrow layout stack remains scrollable with no hidden controls.
- [ ] Telemetry, machine status, workout chart, and controls remain available in all window sizes.
- [ ] End session button remains reachable even with keyboard/system insets changes.

### SUMMARY screen
- [ ] Summary metrics grid renders correctly in 1 and 2 column modes.
- [ ] Back-to-menu action remains visible/reachable in reduced-height windows.

### Debug overlay
- [ ] Floating debug button does not block critical session controls.
- [ ] Bottom timeline overlay (`200dp`) does not hide critical controls in short windows.
- [ ] Overlay can be toggled off from all supported form factors.

### Shared chart component
- [ ] Workout chart remains readable at minimum supported width.
- [ ] Live cursor remains visible and correctly clamped during resize events.

## Validation matrix (manual)
Run the matrix for every destination (`MENU`, `CONNECTING`, `SESSION`, `SUMMARY`) with and without debug overlay (debug builds):

- [ ] Tablet portrait full-screen (`sw600dp+`)
- [ ] Tablet landscape full-screen (`sw600dp+`)
- [ ] Split-screen 50/50
- [ ] Split-screen approximately 70/30
- [ ] Freeform/desktop windowing mode (if device supports it)
- [ ] Rotate while session is running
- [ ] Resize window while session is running

## Implementation guidelines for this repository
- Keep orientation handling policy-free in app code; do not rely on fixed orientation behavior.
- Prefer responsive composition paths (current `BoxWithConstraints` + width breakpoints).
- Keep vertical scrolling available for long content sections on reduced heights.
- Avoid adding fixed-height overlays in production-critical paths without fallback behavior.

## Sources
- Android 17 release notes:
  - https://developer.android.com/about/versions/17/release-notes
- Android 17 Beta 1 announcement:
  - https://android-developers.googleblog.com/2026/02/the-first-beta-of-android-17.html
- Orientation/aspect/resizability guidance:
  - https://developer.android.com/develop/ui/compose/layouts/adaptive/app-orientation-aspect-ratio-resizability
- Device compatibility mode (`sw600dp+` behavior under API 37):
  - https://developer.android.com/guide/practices/device-compatibility-mode
- Adaptive app quality guidelines:
  - https://developer.android.com/docs/quality-guidelines/adaptive-app-quality

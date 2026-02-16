# Next Session

## Branch
- current: `feature/planned-tss-from-workout`

## Recently Completed
- Added CI release verification (`:app:assembleRelease` with minify + `:app:lintRelease`).
- Added optional secrets-based release signing wiring (no signing data in repository).
- Added release/signing documentation in `docs/ci-release-signing.md`.
- Fixed GitHub Actions expression error by removing direct `secrets` usage from step-level `if` condition.
- Updated UI layout:
  - Summary metrics now use two columns.
  - Menu: moved workout selection under device search buttons, added inline filename display.
  - Menu: added two scrollable metadata boxes for workout name/description tags.
- Updated Menu behavior/style:
  - Name/description metadata boxes are shown only when a workout is selected.
  - Filename box + metadata boxes use white card backgrounds.
- Removed user-visible MAC address exposure from Menu UI:
  - Manual MAC input fields removed from Menu.
  - Trainer/HR are shown as compact side-by-side device cards.
  - Device picker list now shows name + RSSI only (no MAC text).
- Adjusted workout row proportions to 30/70 (`Select workout` / filename display).
- Removed unused manual-MAC plumbing from app state/UI wiring:
  - Deleted Menu callback/model fields related to manual FTMS/HR MAC text input.
  - Refactored ViewModel to keep only selected device MACs from scanner/persistence.
  - Removed obsolete MAC-related string resources.
- Adjusted Menu top row:
  - Added one-line row with `FTMS based training session` (left), narrow FTP input (~1/6), and `Current FTP` hint (right).
  - FTP validation errors are shown below that row in red.
- Enabled project-wide Gradle configuration cache in `gradle.properties` and verified cache store/reuse in CLI builds.
- Enabled additional Gradle performance settings in `gradle.properties`:
  - `org.gradle.caching=true`
  - `org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8`
- Improved portrait Menu readability:
  - Fixed subtitle typo (`training`).
  - Increased device/metadata card label contrast to black.
- Updated device picker dismiss control:
  - Replaced filled cancel button with state-aware outlined button.
  - While scanning: shows `Stop scan` with amber warning styling.
  - When idle: shows `Close picker` with high-contrast neutral styling on dark background.
- Emphasized Menu primary action:
  - `Start session` now uses dedicated CTA styling (darker teal, white text, 56dp height).
  - Added leading play icon and semibold label weight.
  - Disabled CTA state remains clearly visible while preserving contrast.
- Tuned Menu visual hierarchy and selection clarity:
  - Secondary Menu actions (`Search trainer`, `Search HR`, `Select workout`) now use a lighter secondary button style to keep focus on `Start session`.
  - Added trainer/HR selection status dots (green when selected, gray when not selected) in device cards.
  - Moved workout step count into the workout card header row next to `Workout`.
  - Added tap-to-open full workout filename dialog from the filename card to handle long names.
- Extended Menu tap-to-view details:
  - Added the same dialog presentation for `Workout name` and `Workout description` cards.
  - Workout chart header row text (`Workout` + step count subtitle) is now white for consistent contrast on the dark chart card.
- Added planned workout TSS calculation (FTP-aware) from execution segments:
  - New `WorkoutPlannedTssCalculator` with one-decimal output.
  - Uses strict execution mapping to avoid showing misleading values for unsupported/invalid workouts.
  - Supports steady, ramp (integrated IF²), and expanded interval segments.
- Wired planned TSS into state orchestration and Menu UI:
  - Recomputed on workout import and on FTP changes.
  - Shown in the Menu workout header row next to step count.
- Added unit tests for planned TSS calculation:
  - steady 1h @ FTP, ramp integration, interval expansion, and mapping-failure null case.
- Fixed Compose lint error (`UnusedBoxWithConstraintsScope`) in `SummaryScreen`:
  - Replaced unnecessary `BoxWithConstraints` with `Box` in summary root.
  - Kept `BoxWithConstraints` in `SessionScreen` where `maxWidth` is required.
  - Verified with `:app:lintDebug`.

## Next Task
- Validate planned TSS values against a few known workouts and confirm desired fallback behavior when strict mapping fails (`TSS unavailable` vs hidden).

## Definition of Done
- Menu layout matches requested component order and proportions.
- Summary page remains readable with two columns.
- No regressions in compile/test.
- No user-visible MAC mentions remain in Menu flow.
- Picker dismiss action is clearly differentiated for scanning vs idle states.
- Long workout filenames are accessible in full via explicit tap action.
- Planned TSS updates correctly when FTP changes and when a new workout is imported.

## Risks / Open Questions
- Confirm whether summary should stay two columns even on narrow portrait screens.
- Confirm desired truncation/scroll behavior for long filename and long description values.
- Confirm product expectation for unsupported steps (show no TSS vs approximate legacy TSS).

## Validation
1. `./gradlew :app:compileDebugKotlin --no-daemon`
2. `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.workout.WorkoutPlannedTssCalculatorTest" --no-daemon`
3. `./gradlew :app:lintDebug --no-daemon`
4. `./gradlew :app:assembleRelease --no-daemon -Pergometer.release.minify=true`
5. `./gradlew :app:lintRelease --no-daemon -Pergometer.release.minify=true`
6. Manual check: generate one exported file and import it to at least one target platform.

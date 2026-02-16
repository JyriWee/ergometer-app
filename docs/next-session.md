# Next Session

## Branch
- current: `docs/contributor-onboarding`

## Recently Completed
- Added contributor-facing architecture overview in `docs/architecture.md`.
- Added setup and development onboarding guide in `docs/onboarding.md`.
- Added BLE status and recovery behavior guide in `docs/ble-status-and-recovery.md`.
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
  - Added trainer/HR live connection status dots in device cards:
    - connected: pulsing green
    - disconnected: amber
  - Moved workout step count into the workout card header row next to `Workout`.
  - Added tap-to-open full workout filename dialog from the filename card to handle long names.
- Extended Menu tap-to-view details:
  - Added the same dialog presentation for `Workout name` and `Workout description` cards.
  - Workout chart header row text (`Workout` + step count subtitle) is now white for consistent contrast on the dark chart card.
- Refined MENU device status indicators with tri-state semantics:
  - `Connected`: pulsing green.
  - `Idle` (normal not-connected state before/after session): neutral gray.
  - `Issue` (trainer auto-connect failure prompt active): amber.
  - HR indicator uses connected/idle (green/gray) to avoid false warning color in normal idle state.
- Added automatic trainer availability probing on MENU:
  - Passive FTMS scan probe runs every 10 seconds for the selected trainer MAC.
  - Probe duration is 1.5 seconds for faster status refresh with controlled scan load.
  - Probe result feeds trainer indicator without opening a GATT/control-point session.
  - Picker scans and session start temporarily suspend probe scanning to avoid scan contention.
  - Trainer indicator stays gray until first probe result is known, then shows green/amber based on availability.
- Added automatic HR availability probing on MENU (longer interval than trainer):
  - Passive HR scan probe runs every 30 seconds for the selected HR MAC.
  - HR indicator now mirrors trainer semantics (green when reachable/connected, amber when known unreachable, gray before first known state).
- Stabilized HR status indicator against transient BLE advertisement misses:
  - Added HR hysteresis (`hrStatusMissThreshold = 2`, `hrStatusStaleTimeoutMs = 75s`).
  - Single missed probe no longer flips HR to amber.
  - Reachable state is refreshed immediately on confirmed HR connection callbacks.
- Improved picker scan feedback on MENU:
  - While scanning, picker status text now shows animated trailing dots (`.`, `..`, `...`) in a calm loop.
  - Keeps scan feedback visible without introducing spinner/noisy motion.
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
- Hardened request-control failure handling for session start:
  - Added deterministic rollback path from `CONNECTING/SESSION` to `MENU` when request-control is rejected.
  - Added deterministic rollback path when request-control times out.
  - Shows actionable connection issue prompt with `Search again` CTA for both reject and timeout cases.
- Added command-timeout callback in `FtmsController`:
  - Timeout now reports the in-flight request opcode to orchestrator.
  - Tracks in-flight opcode for diagnostics and timeout routing.
- Added unit test coverage for controller timeout callback behavior:
  - `FtmsControllerTimeoutTest` verifies request-control timeout opcode reporting and no timeout signal when write-start fails.
- Added local unit-test `android.util.Log` stub to avoid JVM test crashes from Android Log mocking gaps.
- Implemented P0-2 FTMS response-correlation hardening:
  - `FtmsController` now validates Control Point responses against the expected in-flight opcode.
  - Mismatched responses are ignored (BUSY remains active) until matching response or timeout.
  - Responses received with no in-flight command are classified separately.
  - Added `onUnexpectedControlPointResponse` diagnostics callback for orchestration/debug flow.
- Added/extended FTMS controller tests:
  - Timeout callback still reports request opcode.
  - Mismatched response does not release BUSY and eventually times out the expected command.
  - No-in-flight response is reported as unexpected with explicit reason.
- Implemented P0-3 long-session memory hardening in `SessionManager`:
  - Replaced unbounded per-sample arrays with memory-stable streaming aggregates (`count/sum/max`).
  - Preserved summary output semantics (`avg` as integer truncation, `max` nullable when no samples).
  - Kept HR source preference invariant (external strap preferred; bike HR only when strap data is absent).
  - Validated with `:app:compileDebugKotlin` and `:app:lintDebug`.
- Added streaming "actual TSS" calculation for completed sessions:
  - Introduced `ActualTssAccumulator` (rolling-window NP-style smoothing + one-decimal TSS output).
  - Wired session-start FTP into `SessionManager.startSession(ftpWatts)`.
  - Added `actualTss` to `SessionSummary`, Summary UI, and text export (`SessionStorage`).
  - Added unit tests in `ActualTssAccumulatorTest`.
- Hardened actual TSS gap handling with conservative hold limits:
  - Added `maxHoldSeconds` to `ActualTssAccumulator` (default: 3 s).
  - Gaps beyond hold limit are counted as zero power to avoid optimistic TSS inflation.
  - Added regression test that verifies conservative behavior under sparse telemetry.
- Completed P0-4 FTMS flow regression coverage with deterministic JVM tests:
  - Added `SessionOrchestratorFlowTest` for request-control reject rollback, timeout rollback, request-control success start transition, and stop-flow to summary transition.
  - Added test hooks in `SessionOrchestrator` for deterministic FTMS-event simulation without BLE hardware.
  - Injected `mainThreadHandler` into `SessionOrchestrator` constructor to avoid implicit timing races in tests.
  - Hardened test Android Looper stub with `myLooper()` and `getThread()` for Compose runtime compatibility.
- Implemented P1-1 HR lifecycle hardening:
  - Added `HrReconnectCoordinator` with bounded exponential backoff reconnect policy.
  - Extended `HrBleClient` with deterministic disconnect callback support and reconnect orchestration.
  - Wired `MainViewModel` to clear HR state when HR link disconnects.
- Added targeted HR reconnect/disconnect unit coverage:
  - New `HrReconnectCoordinatorTest` covers bounded retries, explicit-close suppression, and backoff reset after successful reconnect.
- Added targeted `SessionManager` edge-case tests:
  - New `SessionManagerEdgeCaseTest` covers short/no-data sessions and conservative TSS behavior under sparse telemetry gaps.
- Added first critical-flow instrumentation coverage:
  - New `MainActivityContentFlowTest` verifies `MENU -> CONNECTING -> SESSION -> STOPPING -> SUMMARY` rendering anchors.

## Next Task
- Review latest GitHub-Codex audit output and select one implementation item for a small, testable first increment (recommended: P1-2 strict-by-default release execution policy).

## Definition of Done
- Selected next implementation scope is explicit and bounded.
- First increment is implemented on a feature branch (not `main`).
- Compile/test/lint checks pass for the changed scope.
- Session handoff notes are updated with concrete next step and validation commands.

## Risks / Open Questions
- Keep commit size controlled; propose commit earlier once each tested increment is complete.
- Confirm product expectation for unsupported workout steps in release (`strict block` vs `degraded fallback`).
- Confirm desired user-visible wording if strict mode blocks a workout.
- Confirm whether additional UI instrumentation coverage is needed before next release-targeted changes.

## Validation
1. `./gradlew :app:compileDebugKotlin --no-daemon`
2. `./gradlew :app:testDebugUnitTest --no-daemon`
3. `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
4. `./gradlew :app:lintDebug --no-daemon`

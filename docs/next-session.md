# Next Session

## Branch
- current: `feature/ci-workflow-concurrency`

## Session Handoff
- next task: Continue landscape/tablet tuning for `SESSION` screen; `MENU` and `WORKOUT_EDITOR` landscape are accepted baseline.
- DoD:
  - Shared `AdaptiveLayoutMode` resolver is used by all primary screens.
  - `MENU` two-pane behavior is validated on-device and accepted (tablet landscape).
  - `SESSION` uses two-pane structure on medium/expanded windows and keeps compact flow unchanged.
  - `SUMMARY` uses one-column metrics on compact and two-column metrics on medium/expanded.
  - `WORKOUT_EDITOR` opens with selected workout data by default (when editor draft is pristine), while preserving existing draft edits.
  - `WORKOUT_EDITOR` two-pane layout is finalized for tablet landscape:
    - left pane: preview + editor meta/actions/status (sticky pane)
    - right pane: step cards (scroll pane)
    - two-row step action buttons and single-row numeric inputs for Steady/Ramp/Intervals
  - CI smoke command fix remains active and `Android Build` checks pass for PR `#30`.
  - Run deferred manual picker verification when multi-HR hardware is available.
- risks:
  - `WORKOUT_EDITOR` compact labels are optimized for tablet landscape; phone-landscape may still need separate tuning later.
  - Single-row Intervals inputs are dense; very narrow windows may require fallback or horizontal scroll in future.
  - Two-pane content density may require spacing tweaks after real-device landscape checks.
  - Editor right pane can look sparse when no validation or preview content is present.
  - CI emulator startup remains environment dependent.
  - Multi-HR picker verification is deferred due current hardware constraints.
- validation commands:
  - `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.example.ergometerapp.ui.AdaptiveLayoutTest" --no-daemon`
  - `./gradlew :app:lintDebug --no-daemon`
  - `gh pr checks 30`
  - `gh run view <run-id> --json jobs,status,conclusion`
  - `gh run view <run-id> --job <job-id> --log | tail -n 80`
  - manual: validate portrait + landscape on tablet for `MENU/SESSION/SUMMARY/WORKOUT_EDITOR`

## Deferred Manual Validation
- id: `MANUAL-HR-PICKER-MULTI-DEVICE-001`
- scope: Verify HR picker discovery behavior with multiple HR straps after scan-list throttling changes.
- reason deferred:
  - Only one chest strap can be worn in a valid position at a time.
  - Spare batteries for additional straps are currently unavailable.
- execute when:
  - At least two HR straps are powered and advertising simultaneously.
- expected:
  - HR picker shows all advertising straps.
  - Device rows remain stable and ordering converges by strongest RSSI.
  - Selecting any listed HR strap still applies correctly and session HR data works.

## Recently Completed
- Workout editor unsaved-state visual cue:
  - `Unsaved changes` indicator now uses an explicit amber accent (theme-aware dark/light variants).
  - Kept indicator static (no blink) to avoid distraction and preserve readability/accessibility.
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
- Workout editor polish follow-up:
  - Fixed step position label color (`Step X / Y`) to use themed `onSurface` for reliable dark-mode contrast.
  - Corrected unsaved-changes semantics:
    - editor no longer starts in unsaved state by default,
    - loading selected workout into editor now marks draft as saved baseline,
    - `Unsaved changes` now appears only after actual draft edits.
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
- Workout editor density/layout cleanup:
  - Removed always-visible editor heading/subtitle to free vertical space.
  - Success status text is now suppressed in-line; only error status remains visible.
  - In two-pane mode:
    - `Name` + `Description` are shown in the right pane before step editing.
    - `Author` is moved to the left pane lower section.
  - Tightened spacing/padding for better no-scroll fit in constrained landscape heights.
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
- Workout editor back-navigation unsaved guard:
  - Added explicit unsaved-changes dialog when pressing editor `Back`.
  - New options:
    - `Apply and go back` (applies draft to MENU selection and exits editor)
    - `Discard changes` (returns to MENU without applying draft)
    - `Keep editing` (stays in editor)
  - Behavior aligns back-navigation with user expectation that unsaved edits must be acknowledged explicitly.
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
- Workout editor file-load flow parity with MENU selector:
  - `Load` action in workout editor now opens the same document picker flow as MENU `Select workout`.
  - When a file is selected while `WORKOUT_EDITOR` is open, import result is immediately reflected in editor draft state.
  - `Open workout editor` now always initializes editor draft from current MENU selection when a workout is selected, so menu/editor cannot drift apart.
  - Import failure while in editor now surfaces as an editor status error instead of silently doing nothing.
  - Updated button label from `Load selected` to `Load file` to match behavior.
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
- Workout editor landscape finalization:
  - Moved editor pre-step content (title/actions/status/unsaved/meta fields) to left pane under preview.
  - Kept right pane dedicated to steps for continuous editing flow.
  - Converted step action row to two rows in two-pane mode:
    - `Move earlier` / `Move later`
    - `Copy step` / `Delete step`
  - Converted numeric step fields to single-row layout in two-pane mode:
    - Steady: duration + FTP %
    - Ramp: duration + start FTP % + end FTP %
    - Intervals: repeat + on/off duration + on/off %
  - Tuned Intervals field widths so `Repeat` label is fully visible.
  - Explicitly themed `Steps` header color (`onSurface`) to avoid dark-mode black text.
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
- Workout editor landscape and open-flow fixes:
  - Improved `WORKOUT_EDITOR` two-pane readability in landscape:
    - custom pane weights for editor (`left` wider than generic two-pane)
    - compact action labels in two-pane mode (`Back/Load/Save/Apply`)
    - compact step-row labels in two-pane mode (`Earlier/Later/Copy/Delete`)
  - Fixed editor open behavior:
    - `Open workout editor` now auto-loads the selected workout when editor draft is pristine.
    - Existing non-pristine draft is preserved (no silent overwrite).
  - Hardened instrumentation smoke anchor:
    - `MainActivityContentFlowTest` summary assertion now anchors on `summary_duration` instead of a scroll-dependent button label.
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
- Menu two-pane landscape tuning pass (tablet):
  - Increased left pane weight to improve readability of setup controls.
  - Refined two-pane subtitle/FTP row into a clearer stacked layout.
  - Added compact label mode for trainer/HR cards to reduce aggressive truncation.
  - Updated pane-weight expectations in `app/src/test/java/com/example/ergometerapp/ui/AdaptiveLayoutTest.kt`.
  - On-device result: accepted by user (`MENU` landscape now balanced).
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.ui.AdaptiveLayoutTest" --no-daemon`
- Adaptive layout foundation and screen integration:
  - Added `app/src/main/java/com/example/ergometerapp/ui/AdaptiveLayout.kt`:
    - width classes: `<600dp`, `600..839dp`, `>=840dp`
    - height classes: `<480dp`, `480..899dp`, `>=900dp`
    - layout modes: `SINGLE_PANE`, `SINGLE_PANE_DENSE`, `TWO_PANE_MEDIUM`, `TWO_PANE_EXPANDED`
    - pane ratios: `45/55` (medium), `35/65` (expanded)
  - Updated `MenuScreen` in `app/src/main/java/com/example/ergometerapp/ui/Screens.kt`:
    - compact keeps existing vertical flow
    - medium/expanded use two-pane split (device/setup on left, workout/metadata on right)
  - Updated `SessionScreen` in `app/src/main/java/com/example/ergometerapp/ui/Screens.kt`:
    - compact keeps stacked telemetry + workout
    - medium/expanded split telemetry/issues and workout progress into two panes
  - Updated `SummaryScreen` in `app/src/main/java/com/example/ergometerapp/ui/Screens.kt`:
    - compact metrics grid is 1 column
    - medium/expanded metrics grid is 2 columns with wider content width
  - Updated `WorkoutEditorScreen` in `app/src/main/java/com/example/ergometerapp/ui/WorkoutEditorScreen.kt`:
    - compact keeps single-column editor
    - medium/expanded split into left editor pane and right validation/preview pane
  - Added unit coverage:
    - `app/src/test/java/com/example/ergometerapp/ui/AdaptiveLayoutTest.kt`
  - Validation:
    - `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.example.ergometerapp.ui.AdaptiveLayoutTest" --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
- CI smoke command parsing fix for PR `#30`:
  - Root cause identified from GitHub Actions logs:
    - Gradle command in smoke script was parsed with a literal `\` task (`Task '\' not found`).
  - Updated `.github/workflows/android-build.yml` smoke script to a single-line Gradle command:
    - `./gradlew :app:connectedDebugAndroidTest --no-daemon --stacktrace -Pandroid.testInstrumentationRunnerArguments.class=com.example.ergometerapp.ui.MainActivityContentFlowTest`
  - Why:
    - Removes shell line-continuation ambiguity in workflow script execution.
- CI emulator boot stability tuning for PR `#30`:
  - Updated `.github/workflows/android-build.yml` `android-instrumentation-smoke` runner inputs:
    - `target: default` (lighter system image than `google_apis` for this smoke test).
    - `emulator-boot-timeout: 900` to tolerate slower GitHub runner startup.
    - Added `-no-snapshot` to emulator options for cleaner cold boot behavior.
  - Kept smoke scope unchanged:
    - `connectedDebugAndroidTest` with `MainActivityContentFlowTest`.
  - Trigger reason:
    - Previous CI run failed with `Timeout waiting for emulator to boot.`
- CI workflow deduplication guard:
  - Added top-level workflow `concurrency` to `.github/workflows/android-build.yml`:
    - `group: ${{ github.workflow }}-${{ github.head_ref || github.ref_name }}`
    - `cancel-in-progress: true`
  - Purpose: prevent duplicate heavy runs for the same feature branch when both `push` and `pull_request` events trigger.
  - Scope: does not change test steps or release checks; only run scheduling behavior.
- CI Android instrumentation smoke baseline:
  - Added new workflow job `android-instrumentation-smoke` in `.github/workflows/android-build.yml`.
  - Job runs after `build-test-lint` and executes emulator-based instrumentation smoke using `reactivecircus/android-emulator-runner@v2`.
  - Smoke scope is intentionally narrow: `MainActivityContentFlowTest` only.
  - Validation:
    - `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
- Scanner ordering responsiveness follow-up (P1 refinement):
  - Updated `ScannedDeviceListPolicy.upsert(...)` to keep per-device RSSI current in both directions (stronger and weaker), while preserving known device name if incoming advertisement name is blank.
  - This prevents picker ordering from sticking to old peak RSSI values in long scans.
  - Expanded policy unit tests in `ScannedDeviceListPolicyTest`:
    - weaker RSSI updates existing row
    - blank incoming name keeps existing name
  - Validation:
    - `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.ScannedDeviceListPolicyTest" --no-daemon`
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
- FTMS parse-main-thread load reduction:
  - `SessionOrchestrator` now parses FTMS Indoor Bike payloads on a single-thread background executor.
  - Added stale-result guards (generation + latest request id) before applying parsed data to UI/session state.
  - Executor is closed in orchestrator `stopAndClose()` to avoid lifecycle leaks.
  - Practical validation result: PASS on device session start/run/stop path.
  - Validation:
    - `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.session.SessionOrchestratorFlowTest" --no-daemon`
    - `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.ble.FtmsControllerTimeoutTest" --no-daemon`
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
- HR stale-callback hardening (audit P0):
  - `HrBleClient` now closes stale GATT instances on stale `onConnectionStateChange` callbacks.
  - `HrBleClient` now ignores stale `onCharacteristicChanged` callbacks.
  - Added regression tests in `app/src/test/java/com/example/ergometerapp/ble/HrBleClientStaleCallbackTest.kt`.
  - Added test dependency via version catalog (`mockito-core`) and moved direct dependencies to TOML aliases:
    - `androidx.compose.material:material-icons-core`
    - `net.sf.kxml:kxml2`
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.ble.HrBleClientStaleCallbackTest" --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon` (0 errors, 4 warnings)
- Inspection cleanup pass (Android Studio export + lint hygiene):
  - Deleted exported inspection XML reports from project root (temporary IDE artifacts).
  - Fixed Compose modifier warnings in:
    - `app/src/main/java/com/example/ergometerapp/ui/Screens.kt`
    - `app/src/main/java/com/example/ergometerapp/ui/WorkoutEditorScreen.kt`
  - Removed one unused ViewModel helper (`workoutEditorSuggestedFileName`).
  - Applied `SharedPreferences.edit { ... }` KTX style in:
    - `app/src/main/java/com/example/ergometerapp/DeviceSettingsStorage.kt`
    - `app/src/main/java/com/example/ergometerapp/FtpSettingsStorage.kt`
  - Removed unused resource set:
    - deleted `app/src/main/res/values/colors.xml` (template-only colors)
    - cleaned unused entries from `app/src/main/res/values/strings.xml`
  - Validation:
    - `./gradlew :app:lintDebug --no-daemon` -> `0 errors, 5 warnings`
- Backup policy hardened with explicit exclusions (offline-first/privacy):
  - Updated `app/src/main/res/xml/data_extraction_rules.xml` to exclude:
    - `sharedpref/ergometer_app_settings.xml`
    - all `file/.`
    - all `database/.`
  - Updated `app/src/main/res/xml/backup_rules.xml` with matching exclusions.
  - Documented backup behavior in `README.md` (`Backup policy` section).
  - Validated with:
    - `./gradlew :app:lintDebug --no-daemon`
- Released `v0.1.0` to GitHub:
  - Merged PR `#23` (`feature/v0-1-0-release-prep` -> `main`).
  - Tagged `main` as `v0.1.0`.
  - Published GitHub Release with signed APK asset:
    - `https://github.com/JyriWee/ergometer-app/releases/tag/v0.1.0`
  - Restored strict review policy to `required_approving_review_count=1` after merge.
- `v0.1.0` version decision and baseline update:
  - Updated `app/build.gradle.kts`:
    - `versionName = "0.1.0"`
    - `versionCode = 2`
  - Verified local validation after version bump:
    - `./gradlew clean :app:compileDebugKotlin :app:lintDebug --no-daemon`
- Signed release pipeline setup and verification:
  - Generated local release keystore at `.local/release-signing/ergometer-release.jks` (gitignored).
  - Generated local signing env file at `.local/release-signing/release-signing.env` (gitignored).
  - Configured GitHub repository secrets:
    - `ANDROID_RELEASE_KEYSTORE_B64`
    - `ANDROID_RELEASE_STORE_PASSWORD`
    - `ANDROID_RELEASE_KEY_ALIAS`
    - `ANDROID_RELEASE_KEY_PASSWORD`
  - Verified signed release build end-to-end:
    - `:app:assembleRelease` (PASS)
    - `:app:lintRelease` (PASS)
  - Verified produced signed APK at:
    - `app/build/outputs/apk/release/app-release.apk`
  - Updated branch-protection posture on GitHub:
    - Repo visibility set to public.
    - Required status check enforced on `main`: `build-test-lint` (`strict=true`).
    - Admin bypass disabled (`enforce_admins=true`).
- Release preparation docs baseline:
  - Added `docs/release-checklist.md` with DoD-driven release flow and artifact mode split (unsigned/signed).
  - Added `CHANGELOG.md` with initial `v0.1.0` entry.
  - Added `docs/release-notes-v0.1.0.md` as GitHub Release draft content.
  - Updated `README.md` documentation index with release docs links.
- Open-source readiness baseline (MIT):
  - Added `LICENSE` (MIT).
  - Added community docs: `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`.
  - Added GitHub templates: bug report, feature request, issue template config, and PR template.
  - Updated `README.md` with an explicit Open Source section linking governance documents.
- Workout editor theme parity pass (dark/light):
  - Added explicit safe-area handling (`WindowInsets.safeDrawing`) so editor content no longer clips under status/navigation bars.
  - Applied explicit theme background to editor root for consistent dark/light rendering.
  - Added subtle theme-aware borders to editor cards (step/validation/preview) to improve light-theme separation while keeping dark-theme calm.
  - Kept editor layout and behavior unchanged.
  - Verified locally:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
  - Verified on-device with dark/light screenshots; accepted as "good enough" for this iteration.
- Summary screen theme parity pass (dark/light):
  - Applied explicit theme background to summary root container for deterministic dark/light rendering.
  - Added subtle theme-aware borders to summary section card and summary metric cards for better light-theme separation while keeping dark-theme calm.
  - Kept summary layout and content unchanged.
  - Verified locally:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
  - Verified on-device with dark/light screenshots; accepted as "good enough" for this iteration.
- Session screen theme parity pass (dark/light):
  - Added subtle theme-aware card borders for session summary surfaces (`TopTelemetrySection`, `WorkoutProgressSection`, `SessionIssuesSection`) to improve separation on light theme without dark-theme overemphasis.
  - Kept layout/content unchanged; only visual contrast tuning.
  - Verified locally:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
  - Verified on-device with dark/light screenshots; accepted as "good enough" for this iteration.
- Finalized MENU theme contrast after on-device dark/light verification:
  - Applied theme background to the MENU root container so dark theme no longer renders dark text on a light surface.
  - Increased secondary button contrast by using `primaryContainer/onPrimaryContainer`.
  - Improved disabled `Start session` legibility by using explicit themed disabled colors instead of low-alpha primary tint.
- Completed UI list item 1 (theme color refactor pass):
  - Replaced remaining hardcoded Menu card/text/device-indicator colors in `Screens.kt` with theme-driven values.
  - Updated Menu card helpers to use centralized themed card colors.
  - Updated Workout Editor step-card and text-field colors in `WorkoutEditorScreen.kt` to follow `MaterialTheme.colorScheme`.
  - Verified locally:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:lintDebug --no-daemon`
- Improved workout editor step-card readability:
  - Step cards now use a white container with dark content text and a subtle border for higher contrast on bright backgrounds.
  - Step input fields now use white filled containers with darker borders so values remain readable inside step cards.
- Clarified workout editor step-order controls:
  - Renamed step reordering button labels from `Up` / `Down` to `Move step earlier` / `Move step later`.
  - Rebalanced step action row button widths and tightened button content spacing so all four actions remain on one row with longer labels.
- Added a full offline ZWO format reference to avoid future web dependency:
  - New document: `docs/zwo-format-reference.md`
  - Includes observed root elements, workout child elements, per-step attribute inventory, parser normalization rules, export scope, and maintenance commands.
- Updated documentation index and compatibility doc links:
  - `README.md` now links both ZWO docs.
  - `docs/zwo-format-compatibility.md` points to the full reference.
- Fixed workout editor export filename behavior:
  - Switched `CreateDocument` MIME from `application/xml` to `application/octet-stream` so document providers keep the suggested `.zwo` suffix (avoids auto-appended `.xml`).
- Extended ZWO parser compatibility based on external ZWO field reference:
  - Added support for step aliases: `Freeride`, `SolidState`, `MaxEffort`.
  - Added power fallbacks for common variants:
    - Warmup/Cooldown/Ramp: fallback from `Power` to low/high when needed.
    - SteadyState/SolidState: fallback from `PowerLow`/`PowerHigh` average when `Power` is missing.
    - IntervalsT: fallback from `PowerOnLow/High` and `PowerOffLow/High` when `OnPower`/`OffPower` are missing.
  - Added parser tests for alias handling and fallback power mapping in `ZwoParserTest`.
- Added compatibility documentation with web sources:
  - `docs/zwo-format-compatibility.md`
- Switched workout editor power input semantics from fraction to percentage:
  - Editor input fields now use percentage values (e.g., `80`) to match UI labels.
  - Import conversion maps stored workout fractions to editor percentages (e.g., `0.80 -> 80`).
  - Build/export conversion maps editor percentages back to workout fractions (`80 -> 0.80`) for unchanged ZWO output format.
  - Validation range now enforces percentage limits (`30-200`).
  - Updated default step values in editor actions to percentage-based inputs.
  - Extended `WorkoutEditorMapperTest` assertions to verify both import and export conversion behavior.
- Updated workout editor initialization behavior (Option A):
  - `WorkoutEditorDraft.empty()` now starts with zero steps (no prefilled step).
  - `MainViewModel` `nextWorkoutEditorStepId` now starts at `1L` and remains monotonic after add/duplicate/import.
  - `WorkoutEditorMapper.fromWorkout(...)` no longer injects a fallback steady step when imported workout has no editor-supported steps.
  - Added unit test `fromWorkoutLeavesDraftEmptyWhenNoSupportedStepsExist`.
  - Practical effect: users explicitly choose the first step type (including warmup-style ramp via `Add ramp step`).
- Refined workout editor usability pass:
  - Updated editor top text fields to explicit black text/border styling for stronger readability.
  - Renamed power labels from fraction terminology to user-facing `FTP percentage (%)`.
  - Updated add-step button labels to explicit action wording (`Add steady step`, `Add ramp step`, `Add intervals step`).
  - Added mandatory save prompt when applying unsaved editor draft to menu selection:
    - `Save and apply` opens document export and applies on successful save.
    - `Apply without save` allows immediate apply after explicit confirmation.
- Implemented in-app workout editor MVP foundation:
  - Added new destination `WORKOUT_EDITOR` and menu entry point (`Open workout editor`).
  - Added editor draft models/actions for supported step types (`Steady`, `Ramp`, `Intervals`).
  - Added editor mapper with validation and strict conversion to `WorkoutFile`.
  - Added `.zwo` serializer for supported step tags and wired export through `CreateDocument`.
  - Added `SessionOrchestrator.onWorkoutEdited(...)` so editor output can be applied directly to menu session selection.
  - Added preview metrics on editor screen via existing chart + planned TSS/step counter pipeline.
  - Added unit tests for editor mapper/serializer (`WorkoutEditorMapperTest`).
  - Updated `MainActivityContentFlowTest` fixture for extended UI model/callback contract.
- Added focused low-latency guard unit coverage in BLE layer:
  - Extracted guard logic into `LowLatencyScanStartGate` to keep scanner pacing behavior directly testable.
  - Added `LowLatencyScanStartGateTest` for cooldown, throttle-backoff, rolling-window cap, and non-low-latency bypass behavior.
  - Kept `BleDeviceScanner` behavior unchanged by routing start-delay decisions through the extracted guard.
- Added testable device-scan policy extraction:
  - New `DeviceScanPolicy` centralizes picker/probe scan mode mapping and picker completion decisions (retry/error/no-results/done).
  - `MainViewModel` now uses this policy for retry gating and scan completion status classification.
- Added focused JVM regression tests:
  - `DeviceScanPolicyTest.pickerUsesLowLatencyAndProbeUsesBalancedMode`
  - `DeviceScanPolicyTest.retryTooFrequentRequiresAllowFlagActivePickerAndCodeSix`
  - `DeviceScanPolicyTest.completionClassificationMapsToExpectedUiStatusPath`
- Added proactive low-latency scan start window guard in `BleDeviceScanner`:
  - Tracks successful low-latency starts in a global 30-second rolling window.
  - Caps starts to 3 per window to avoid Android scanner registration throttle (`status=6`) during rapid picker restart patterns.
  - Integrates with existing restart cooldown and post-failure backoff.
- Added BLE scanner diagnostics for throttle root-cause analysis:
  - `BleDeviceScanner` now records a global ring-buffer scan journal with source label (`picker`, `probe_ftms`, `probe_hr`), service, mode, and lifecycle events.
  - On Android scan throttle failure (`status=6`), journal is dumped to LogCat automatically for timeline analysis.
- Hardened picker UX against rapid start/stop tap patterns:
  - Added a 3-second safety lock for the picker `Stop scanning` button at scan start.
  - Unlocks early as soon as at least one matching device is discovered.
  - Always unlocks when scan stops/fails so the picker can be closed deterministically.
- Implemented menu scan-cost hardening for passive availability probes:
  - Added configurable `scanMode` to `BleDeviceScanner.start(...)` (default remains low-latency for interactive picker scans).
  - Switched trainer/HR background MENU probes to `SCAN_MODE_BALANCED`.
  - Kept interactive picker scanning behavior unchanged.
- Validated passive probe tuning locally:
  - `:app:compileDebugKotlin`
  - `:app:testDebugUnitTest --tests "com.example.ergometerapp.session.SessionOrchestratorFlowTest" --tests "com.example.ergometerapp.session.SessionManagerEdgeCaseTest"`
  - `:app:lintDebug`
- Implemented audit P0-3 stop-flow persistence hardening:
  - `SessionManager` now queues session summary persistence to a background single-thread executor.
  - Summary publication (`lastSummary`, phase transition, emitted UI state) remains synchronous on main thread.
  - Added guarded persistence error logging to avoid crash on storage failures.
- Added P0-3 regression test coverage:
  - `SessionManagerEdgeCaseTest.stopSessionQueuesPersistenceWithoutBlockingSummaryPublication`.
- Validated P0-3 locally:
  - `:app:compileDebugKotlin`
  - `:app:testDebugUnitTest --tests "com.example.ergometerapp.session.SessionManagerEdgeCaseTest" --tests "com.example.ergometerapp.session.SessionOrchestratorFlowTest"`
  - `:app:lintDebug`
- Implemented audit P0-2 FTMS telemetry/log pressure reduction:
  - Added Indoor Bike telemetry coalescing in `FtmsBleClient` with a 200 ms main-thread update cadence (latest sample wins).
  - Replaced per-notification debug spam with debug-only sampled rate logging.
  - Added debug-gated FTMS verbose logging helpers in `FtmsBleClient` and `FtmsController` to avoid release log noise and string-build overhead.
  - Kept protocol-critical callbacks (`onReady`, control-point response, ownership, disconnect) unthrottled.
- Validated P0-2 locally:
  - `:app:compileDebugKotlin`
  - `:app:testDebugUnitTest --tests "com.example.ergometerapp.ble.FtmsControllerTimeoutTest" --tests "com.example.ergometerapp.session.SessionOrchestratorFlowTest"`
  - `:app:lintDebug`
- Implemented audit P0-1 HR readiness hardening:
  - `HrBleClient` now emits `onConnected` only after HR CCCD write succeeds.
  - Added deterministic setup-abort path for missing service/characteristic/descriptor and notification setup failures.
  - Setup failures are no longer silent; failures now route through reconnect/disconnect handling with explicit reasons.
- Validated P0-1 locally:
  - `:app:compileDebugKotlin`
  - `:app:testDebugUnitTest --tests "com.example.ergometerapp.ble.HrReconnectCoordinatorTest"`
  - `:app:lintDebug`
- Added bilingual project-origin note in `docs/ai-assisted-development-note.md` (Finnish + English).
- Linked AI-assisted development note from `README.md`.
- Received latest external GitHub-Codex audit with prioritized findings (HR ready-state sequencing, FTMS main-thread/log pressure, SessionManager stop-time persistence threading, probe scan cost).
- Refreshed root `README.md` to reflect current project scope, setup, validation commands, configuration flags, and documentation index.
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
- Continue workout editor MVP hardening:
  - Run practical device verification for editor save/apply flow (new draft, load selected, save `.zwo`, re-import, start session).
  - Improve editor step UX (optional step-type conversion and tighter field layout on tablet portrait).
  - Add focused tests for validation boundary values and editor-to-session apply flow.

## Definition of Done
- Implementation is done on a dedicated feature branch (not `main`).
- Editor supports create/edit for steady/ramp/interval steps with deterministic validation and apply/save actions.
- Exported `.zwo` files are accepted by the existing importer and preserve planned TSS/step count semantics.
- No functional changes to workout/session flow.
- No regressions in compile/test/lint for touched scope.
- Session handoff notes are updated for the next increment.

## Risks / Open Questions
- Keep commit size controlled; propose commit as soon as each tested increment is complete.
- Android BLE scan APIs are difficult to unit test without abstraction; may require a narrow indirection layer.
- Decide whether to keep current probe intervals or tune them after practical battery tests.

## Validation
1. `./gradlew :app:compileDebugKotlin --no-daemon`
2. `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.ZwoParserTest" --tests "com.example.ergometerapp.workout.editor.WorkoutEditorMapperTest" --tests "com.example.ergometerapp.DeviceScanPolicyTest" --tests "com.example.ergometerapp.ble.LowLatencyScanStartGateTest" --no-daemon`
3. `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
4. `./gradlew :app:lintDebug --no-daemon`

## Session Update (2026-02-19 - FIT Export Planning)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Added `docs/fit-export-plan.md`.
- Collected official FIT Activity export requirements and message sequencing references.
- Mapped current ErgometerApp session fields to FIT `FileId/Record/Lap/Session/Activity` model.
- Documented staged implementation path (`v1`, `v1.1`, `v2`) and licensing risk checkpoints.

### Next Task
- Implement `v1` minimal valid FIT export behind a feature flag (single-session activity file with required messages).

### Definition of Done
- A completed session can be exported as a syntactically valid `.fit` activity file.
- Exported file includes required message types (`FileId`, `Activity`, `Session`, `Lap`, `Record`).
- Required summary fields are populated (`start_time`, `total_elapsed_time`, `total_timer_time`, `timestamp`).
- Export path reports typed success/failure back to UI.
- Documentation is updated with user-visible export behavior and known limitations.

### Risks / Open Questions
- FIT SDK licensing model is not standard OSS; verify compatibility with public-template distribution policy before shipping SDK-based writer.
- Summary-only export is valid but may appear sparse in analysis platforms until per-sample record timeline is added.
- Platform-specific import tolerance differs; practical smoke import is required.

### Validation Commands
1. `./gradlew :app:compileDebugKotlin --no-daemon`
2. `./gradlew :app:testDebugUnitTest --no-daemon`
3. `./gradlew :app:lintDebug --no-daemon`
4. Manual: export one session and verify import on at least one target platform.

## Session Update (2026-02-19 - FTMS Protocol Documentation)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Added and finalized `docs/ftms-protocol-reference.md`.
- Consolidated public FTMS references (profile/service page, assigned numbers, public ICS/TS docs).
- Documented current app FTMS contract (UUID requirements, setup order, control-point invariants, opcode set).
- Added interoperability checklist for validating new trainer models.

### Next Task
- Wait for current smoke-test result and then continue FIT export `v1` implementation (`FileId` + `Activity` + `Session` + `Lap` + `Record`).

### Definition of Done
- FTMS protocol reference is accurate against current code and public Bluetooth sources.
- Document distinguishes normative public references vs app-specific behavior.
- Future maintainer can verify trainer compatibility using the provided checklist.
- Session handoff data is updated for continuation.

### Risks / Open Questions
- Public Bluetooth documents do not include full normative payload semantics; deeper details still require licensed spec text.
- Trainer firmware differences can still require device-level validation despite protocol-level checklist pass.

### Validation Commands
1. `./gradlew :app:compileDebugKotlin --no-daemon`
2. `./gradlew :app:testDebugUnitTest --no-daemon`
3. `./gradlew :app:lintDebug --no-daemon`
4. Manual: verify one trainer end-to-end (`MENU -> CONNECTING -> SESSION -> SUMMARY`) with control-point command acknowledgments.

## Session Update (2026-02-19 - Custom FIT Export v1, no SDK)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Implemented a custom in-repo FIT binary export path (Option B, no Garmin SDK dependency).
- Added `session/export/FitExportService` + minimal FIT writer with CRC/header/definition/data record support.
- Export now writes required activity message set: `file_id`, `record`, `lap`, `session`, `activity`.
- Added Summary UI export action (`Export .fit`) with typed success/error status messaging.
- Added session timestamps to `SessionSummary` (`startTimestampMillis`, `stopTimestampMillis`) for valid FIT timing fields.
- Added unit tests for FIT structure/CRC/message coverage and sparse-summary handling.

### Next Task
- Manual validation of generated `.fit` imports on target platforms (Intervals.icu, Strava, GoldenCheetah).
- If needed, add richer record timeline export (1 Hz samples) after import compatibility baseline is confirmed.

### Definition of Done
- Summary screen can export a completed session as `.fit` through create-document flow.
- Exported file has valid FIT header, header CRC, and file CRC.
- File contains required activity messages (`file_id`, `record`, `lap`, `session`, `activity`).
- Compile, unit tests, androidTest compile, and lint pass for touched scope.

### Risks / Open Questions
- Current export uses a single record sample from summary-level data; downstream chart richness is limited.
- Some platforms may accept minimal FIT strictly but display reduced analytics until per-sample record timeline is added.
- Event semantics are intentionally minimal (`activity` + `stop`) and may need tuning for platform-specific expectations.

### Validation Commands
1. `./gradlew :app:compileDebugKotlin --no-daemon`
2. `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.session.export.FitExportServiceTest" --no-daemon`
3. `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
4. `./gradlew :app:lintDebug --no-daemon`

## Session Update (2026-02-19 - FIT Timeline Export v1.1)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Added per-session timeline model with 1 Hz capture gating:
  - `SessionSample`
  - `SessionExportSnapshot`
- Extended `SessionManager` to collect timestamped telemetry samples during active sessions and expose immutable export snapshots.
- Updated summary export flow to use snapshot payloads (`summary + timeline`) instead of summary-only payloads.
- Updated FIT writer to emit multiple `record` messages from session timeline samples.
- Added fallback record generation when timeline is empty to preserve export compatibility.
- Added and updated unit tests for:
  - FIT record timeline emission
  - SessionManager one-sample-per-second capture behavior

### Next Task
- Run practical import validation of timeline-rich FIT files on target platforms (Intervals.icu, Strava, GoldenCheetah) and verify charts/timestamps look correct.

### Definition of Done
- Summary export produces FIT files that include multiple time-ordered `record` messages when telemetry exists.
- FIT export remains valid when no timeline samples are available (fallback record path).
- Export UI flow remains unchanged for users (same button and save flow).
- Compile, unit tests, androidTest compile, and lint pass for touched scope.

### Risks / Open Questions
- Current timeline is sampled at most once per second using app wall-clock and latest known values; platforms may differ in smoothing expectations.
- Cadence is exported as integer RPM from truncated FTMS cadence; rounding strategy can be revisited if needed.
- No pause/resume FIT event stream yet; platforms may still infer timer time differently for complex stop flows.

### Validation Commands
1. `./gradlew :app:compileDebugKotlin --no-daemon`
2. `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.session.export.FitExportServiceTest" --tests "com.example.ergometerapp.session.SessionManagerEdgeCaseTest.timelineCapture_isLimitedToOneSamplePerSecond" --no-daemon`
3. `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
4. `./gradlew :app:lintDebug --no-daemon`

## Session Update (2026-02-19 - Session Chart Cursor FTP Label)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Added a live FTP percentage label next to the session chart cursor target label.
- Cursor labels now show:
  - right side: current target watts (`W`)
  - left side: current target relative FTP (`%`)
- Kept label rendering resilient for narrow chart widths by clamping both labels within chart bounds.

### Next Task
- Practical UI check on device during an active session to confirm readability and non-overlap at low and high target powers.

### Definition of Done
- Session chart cursor shows watts and FTP% simultaneously at the same vertical level.
- Existing chart behavior and interactions remain unchanged.
- Compile and lint pass after the change.

### Risks / Open Questions
- On extremely narrow layouts, labels may still feel visually dense even though they stay in bounds.
- If needed, future refinement can hide one label under tight width constraints.

### Validation Commands
1. `./gradlew :app:compileDebugKotlin --no-daemon`
2. `./gradlew :app:lintDebug --no-daemon`

## Session Update (2026-02-19 - Cursor Label Placement Adjustment)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Adjusted session chart live cursor labels to reduce overlap risk:
  - watts label remains on the right side of cursor
  - FTP percent label is now stacked directly below the watts label on the right side
- Kept bounds clamping so stacked labels remain inside chart drawing area.

### Next Task
- Practical readability check during workout execution on tablet and phone portrait.

### Definition of Done
- Cursor labels show `W` and `%` as a right-side vertical stack.
- No clipping or overlap regressions in normal session chart sizes.
- Compile and lint pass.

### Risks / Open Questions
- In very short chart heights the stacked labels may still feel dense; optional future fallback is hiding `%` label under tight height constraints.

### Validation Commands
1. `./gradlew :app:compileDebugKotlin --no-daemon`
2. `./gradlew :app:lintDebug --no-daemon`

## Session Update (2026-02-19 - FIT Export Manual Validation Template)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Added a dedicated manual validation template for FIT export compatibility:
  - `docs/fit-export-manual-validation-template.md`
- Template covers:
  - source-of-truth summary values from app
  - platform-specific comparison tables (GoldenCheetah, Intervals.icu, Strava)
  - tolerance-based PASS/FAIL rules
  - timeline quality checks for power/cadence/HR charts

### Next Task
- Execute one full cross-platform FIT validation run using the new template with the latest 27-minute workout export.

### Definition of Done
- One exported FIT file is validated in all three target platforms using the template.
- Any platform mismatch is captured with delta and defect notes.
- Follow-up fixes are prioritized from real import results.

### Risks / Open Questions
- Platform-side metric rounding/normalization may differ despite a valid FIT payload.
- Some platforms may compute derived fields (for example TSS) differently from app summary values.

### Validation Commands
1. `git status --short --branch`

## Session Update (2026-02-19 - FIT Baseline Values Captured)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Captured baseline expected FIT values from a real exported workout:
  - `.local/fit-exports/session_2026-02-19_06-48-25.fit`
- Added pre-filled validation report:
  - `docs/fit-export-validation-2026-02-19-session_06-48-25.md`
- Report includes:
  - structural FIT validity checks (header/CRC/messages)
  - expected metrics (duration, distance, power, cadence, HR, calories, TSS)
  - ready-to-fill comparison tables for GoldenCheetah, Intervals.icu, and Strava

### Next Task
- Keep FIT baseline compatibility by re-running the same cross-platform check after future FIT export changes.

### Definition of Done
- The same FIT artifact is imported into all target platforms.
- Platform values are compared against the pre-filled expected baseline.
- Any out-of-tolerance mismatches are logged as concrete follow-up items.
- Current baseline status: PASS on GoldenCheetah, Intervals.icu, and Strava.

### Risks / Open Questions
- Platform-specific rounding and derived metric recalculation may create small deltas.
- Event/timer semantics may still need refinement if pause/resume handling differs by platform.

### Validation Commands
1. `git status --short --branch`

## Session Update (2026-02-19 - ADB Daily Ops Cheatsheet)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Added practical device-debug command reference:
  - `docs/adb-cheatsheet.md`
- Covers:
  - install/start/stop/clear app
  - logcat filtering
  - file pull/push patterns
  - screenshots and screenrecord
  - instrumentation test commands
  - key diagnostics (`dumpsys` battery/activity/bluetooth)
  - multi-device serial targeting

### Next Task
- Use cheatsheet commands in real debugging sessions and refine only if gaps are found.

### Definition of Done
- Core adb workflows for this project are documented in one place and ready for daily use.

### Risks / Open Questions
- Some `run-as` file commands depend on build type/debuggability and may not work in release contexts.

### Validation Commands
1. `adb devices -l`
2. `adb shell am start -n com.example.ergometerapp/.MainActivity`

## Session Update (2026-02-19 - ADB Log Harness Improvements)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Added helper script:
  - `scripts/adb/logcat-ergometer.sh`
- Script capabilities:
  - filtered ErgometerApp-focused log capture
  - optional log buffer clear
  - dump or live follow modes
  - serial-targeted capture
  - output file capture to `.local/logs/...`
  - `--pid-only` mode for low-noise app-process-only logs
- Updated `docs/adb-cheatsheet.md` with helper script usage examples.

### Next Task
- Use `--pid-only` mode during practical workout and BLE scenario tests to collect reproducible low-noise logs.

### Definition of Done
- One command is enough to capture useful app logs during device testing.
- Developers can switch between broad filtered capture and strict app-PID capture as needed.

### Risks / Open Questions
- `--pid-only` requires the app process to be running before capture starts.
- If app restarts during a long run, a new process PID requires restarting capture.

### Validation Commands
1. `bash -n scripts/adb/logcat-ergometer.sh`
2. `adb shell am start -n com.example.ergometerapp/.MainActivity`
3. `./scripts/adb/logcat-ergometer.sh --dump --pid-only --serial R92Y40YAZPB`

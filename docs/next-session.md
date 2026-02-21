# Next Session

## Branch
- current: `feature/pr32-connect-timeout-watchdog`

## Session Handoff
- next task: Start replacing the quarantined rotation coverage with a dedicated `MainActivity` recreation-safe instrumentation test.
- DoD:
  - `build-test-lint` runs only when Android-impacting files changed on `pull_request`/`push`.
  - Docs-only/non-Android PR updates skip `build-test-lint` fast gate and save CI wait time.
  - New manual smoke dispatch cancels older in-progress smoke on the same branch automatically.
  - Manual dispatch with `run_instrumentation_smoke=true` starts only `android-instrumentation-smoke` and skips `build-test-lint`.
  - Manual dispatch with `include_flaky_tests=false` completes successfully on PR branch.
  - `menuAndSessionAnchorsRemainVisibleAcrossRotation` remains quarantined until replacement test exists.
  - Replacement rotation test validates menu/session anchors across portrait<->landscape recreation on at least one physical device.
  - Workflow summary explicitly records non-blocking flaky-inclusive failure context.
- risks:
  - If the path list is too narrow, a real Android-impacting change could skip fast gate unexpectedly.
  - If the path list is too broad, we still lose part of the expected CI time savings.
  - Aggressive smoke cancellation can interrupt a run if another dispatch is triggered unintentionally.
  - Quarantine reduces false alarms but temporarily lowers direct rotation-regression signal.
  - Flaky-inclusive lane may hide new regressions if warnings are not actively monitored.
  - Emulator instrumentation runtime is long (~20 minutes) and increases feedback delay.
  - Nightly/manual smoke still requires active monitoring to produce actionable signal.
- validation commands:
  - `gh workflow run \"Android Build\" --ref feature/pr32-connect-timeout-watchdog -f run_instrumentation_smoke=true -f include_flaky_tests=true`
  - `gh workflow run \"Android Build\" --ref feature/pr32-connect-timeout-watchdog -f run_instrumentation_smoke=true -f include_flaky_tests=false`
  - `gh run list --workflow \"Android Build\" --branch feature/pr32-connect-timeout-watchdog --limit 5`
  - `git diff --name-only <base-sha> <head-sha> -- app/ build.gradle build.gradle.kts settings.gradle settings.gradle.kts gradle.properties gradle/ gradlew gradlew.bat scripts/adb/ .github/workflows/android-build.yml`
  - `gh run view <run-id> --job <detect-job-id> --log`
  - `ANDROID_SERIAL=R9WT702055P ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ergometerapp.ui.MainActivityContentFlowTest --no-daemon`
  - `ANDROID_SERIAL=R92Y40YAZPB ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ergometerapp.ui.MainActivityContentFlowTest --no-daemon`
  - `gh workflow run \"Android Build\" --ref feature/pr32-connect-timeout-watchdog -f run_instrumentation_smoke=true -f include_flaky_tests=false`
  - `gh workflow run \"Android Build\" --ref feature/pr32-connect-timeout-watchdog -f run_instrumentation_smoke=true -f include_flaky_tests=true`
  - `gh run list --workflow \"Android Build\" --limit 5`
  - `gh run view <run-id> --log-failed`

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
- Smoke-lane auto-cancel for manual reruns:
  - Added job-level concurrency to `android-instrumentation-smoke`:
    - `group: ${{ github.workflow }}-smoke-${{ github.ref_name || github.ref }}`
    - `cancel-in-progress: true`
  - Result: new smoke dispatch on same branch cancels previous in-progress smoke run automatically.
- Docs-only PR gate verification for CI wait-time reduction:
  - Opened temporary docs-only PR against `feature/pr32-connect-timeout-watchdog` to validate new `detect-android-changes` behavior.
  - Verification run `22248334054`:
    - `detect-android-changes`: `success`
    - `build-test-lint`: `skipped`
    - `android-instrumentation-smoke`: `skipped`
  - Result: docs-only changes now bypass long Android fast gate as intended.
- CI wait-time reduction via Android change detection gate:
  - Added `detect-android-changes` job in `.github/workflows/android-build.yml`.
  - `build-test-lint` now depends on detected Android-impacting file changes for `pull_request`/`push`, while manual dispatch keeps explicit control.
  - Detection writes a step summary with decision and changed files, making skip/run behavior visible in each CI run.
  - Validation:
    - Docs-only commit simulation (`dcff1d5`) produced no relevant file matches for the gate.
    - Android-impacting commit simulation (`af0bd63`) matched `.github/workflows/android-build.yml` as expected.
- Rotation-test quarantine for stable smoke signal:
  - `menuAndSessionAnchorsRemainVisibleAcrossRotation` in `MainActivityContentFlowTest` is now explicitly quarantined with `@Ignore`.
  - Retained a bounded retry helper inside the quarantined test to document the investigated race-mitigation attempt.
  - Validation:
    - `ANDROID_SERIAL=R9WT702055P ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ergometerapp.ui.MainActivityContentFlowTest --no-daemon` (`SKIPPED`: rotation test, class run passes)
    - `ANDROID_SERIAL=R92Y40YAZPB ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ergometerapp.ui.MainActivityContentFlowTest --no-daemon` (`SKIPPED`: rotation test, class run passes)
    - `22247846824` (`include_flaky_tests=true`) completed `success`; include-flaky lane reports `menuAndSessionAnchorsRemainVisibleAcrossRotation` as `SKIPPED` with no test failures.
- Non-blocking policy for flaky-inclusive GitHub smoke:
  - `Run instrumentation smoke on emulator (include flaky)` now uses `continue-on-error: true`.
  - Added workflow summary marker when flaky-inclusive lane fails:
    - `Record non-blocking flaky smoke failure`
  - Result: `include_flaky_tests=true` is informational; default `exclude flaky` lane remains the pass/fail smoke gate.
  - Validation:
    - `22247080041` (`include_flaky_tests=true`) completed `success` while still recording flaky failure (`menuAndSessionAnchorsRemainVisibleAcrossRotation`) and adding non-blocking summary note.
    - Uploaded `smoke-policy.txt` confirms `include_flaky=true`.
- GitHub smoke dispatch stabilization round (PR `#33` branch):
  - Replaced multiline Gradle invocations in emulator-runner script blocks with single-line commands to avoid `/usr/bin/sh` line-splitting (`Task '\\' not found`).
  - Added `disk-size: 4096M` to emulator-runner include/exclude smoke steps.
  - Manual dispatch results:
    - `22246195078` (`include_flaky_tests=true`): workflow path worked; instrumentation executed and failed in known flaky test `menuAndSessionAnchorsRemainVisibleAcrossRotation`.
    - `22246629540` (`include_flaky_tests=false`): `android-instrumentation-smoke` completed successfully.
- GitHub emulator-runner shell-compat fix for smoke policy:
  - Replaced bash-array based runner-arg logic with two explicit emulator-runner steps (`include flaky` / `exclude flaky`) keyed by resolved smoke-policy output.
  - Fix addresses workflow-dispatch run failure where `/usr/bin/sh` could not parse bash array syntax.
- Workflow dispatch smoke-only routing fix:
  - `build-test-lint` is now skipped when manual dispatch is explicitly used for smoke (`run_instrumentation_smoke=true`), so manual smoke does not trigger redundant full build/test/lint.
- `MainActivityContentFlowTest` anchor fix for animated waiting labels:
  - Updated connecting/stopping assertions to match normalized waiting status text used by animated dot rendering.
  - Fixed failing cases:
    - `criticalFlowScreensRenderExpectedAnchors`
    - `startSessionAnchorsStayConsistentAcrossConnectPermissionDenyThenGrant`
  - Validation:
    - `ANDROID_SERIAL=R92Y40YAZPB ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ergometerapp.ui.MainActivityContentFlowTest --no-daemon`
- Device smoke end-to-end pass after cleanup hardening:
  - `scripts/adb/device-smoke.sh --serial R92Y40YAZPB` now completes with `test_exit_code=0` and no lingering `adb logcat` process.
  - Verified artifact run:
    - `.local/device-test-runs/run-20260221-013713/run-summary.txt`
- Device smoke cleanup hardening for failure-path exit:
  - Reworked `scripts/adb/device-smoke.sh` logcat capture to avoid background pipeline orphaning (`adb logcat` is now tracked as a single process).
  - Added forced shutdown fallback for lingering logcat PID and post-capture filtering step, preserving default filtered `logcat.log`.
  - Confirmed failure-path exit no longer leaves `device-smoke.sh` or `adb logcat` processes running.
  - Validation:
    - `bash -n scripts/adb/device-smoke.sh`
    - `scripts/adb/device-smoke.sh --serial R92Y40YAZPB` (expected test failure due existing UI anchor assertions, script exits cleanly)
- Opened PR `#33` (`feature/pr32-connect-timeout-watchdog` -> `main`) with session connect-timeout + smoke/CI policy changes.
- Flaky-test visibility policy finalized across local + GitHub smoke lanes:
  - `android-build.yml` now supports manual flaky inclusion input (`include_flaky_tests`) and resolves explicit smoke policy per trigger.
  - Nightly scheduled smoke includes flaky tests by default; manual dispatch can include them explicitly.
  - Smoke artifacts now include `smoke-policy.txt` and connected AndroidTest reports for triage visibility.
  - `scripts/adb/emulator-smoke.sh` now logs flaky policy at runtime and records both `exclude_flaky` and `include_flaky` in `run-summary.txt`.
  - `docs/adb-cheatsheet.md` now documents lane-specific flaky policy and artifact expectations.
  - Validation:
    - `bash -n scripts/adb/emulator-smoke.sh`
    - `scripts/adb/emulator-smoke.sh --help`
    - `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.session.SessionOrchestratorFlowTest" --no-daemon`
- Device smoke determinism via explicit Gradle serial pinning:
  - `scripts/adb/device-smoke.sh` now runs install and connected AndroidTest Gradle tasks with `ANDROID_SERIAL` pinned to the selected/resolved device serial.
  - Added explicit run log line for Gradle target serial and included `gradle_android_serial` in `run-summary.txt`.
  - Validation:
    - `bash -n scripts/adb/device-smoke.sh`
    - `scripts/adb/device-smoke.sh --help`
- GitHub CI smoke decoupling and safer trigger model:
  - Workflow now runs `push` checks only on `main` to avoid duplicate branch `push` + `pull_request` CI noise.
  - Added nightly schedule (`04:30 UTC`, currently `06:30` Finland local time) and manual toggle input for emulator smoke execution.
  - `android-instrumentation-smoke` no longer blocks normal PR runs by default; it runs on nightly schedule or manual dispatch input.
  - Added explicit workflow token hardening with read-only `contents` permissions.
  - Validation:
    - `git diff -- .github/workflows/android-build.yml`
- Session start connect-phase watchdog (`CONNECTING` timeout):
  - Added a bounded connect-phase timeout in `SessionOrchestrator` (`15s`) to fail fast from stuck `CONNECTING` and return to `MENU` with recovery prompt.
  - Added cancellation wiring so timeout is cleared on successful control grant, disconnect cleanup, and orchestrator teardown.
  - Added deterministic unit coverage in `SessionOrchestratorFlowTest`:
    - `connectFlowTimeoutRollsBackToMenuWithRecoveryPrompt`
    - `connectFlowTimeoutIsCancelledAfterRequestControlGranted`
  - Validation:
    - `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.session.SessionOrchestratorFlowTest" --no-daemon`
- UI process hardening for future sessions:
  - Added `docs/ui-workflow-playbook.md` with a step-by-step beginner-friendly UI workflow.
  - Added an explicit AGENTS rule to follow the playbook on UI tasks and remind when drift is detected.
  - Merged all pending feature work into `main` through PR `#31`, then cleaned merged feature branches from `origin`.
- Session dark-mode consistency + stopping feedback polish:
  - Added themed background to `SessionScreen` root container to remove bright side gutters in dark mode.
  - Added themed background to `Connecting` and `Stopping` transitional screens for consistent dark-mode rendering.
  - Added animated trailing dots to `Stopping` headline to match ongoing-operation feedback style.
  - Validation:
    - `./gradlew :app:compileDebugKotlin :app:installDebug --no-daemon`
    - `./scripts/adb/capture.sh --serial R92Y40YAZPB --no-record --out-dir .local/captures/session-layout-check`
    - screenshot: `.local/captures/session-layout-check/screenshot-20260220-050647.png`
- Waiting-state feedback polish for Session and Connecting:
  - Added shared animated trailing-dot label helper for active waiting feedback.
  - `Connecting` screen now shows animated dots to indicate ongoing connection attempts.
  - Session state message now shows animated dots when waiting for user action:
    - `waiting start`
    - `paused`
  - Validation:
    - `./gradlew :app:compileDebugKotlin :app:installDebug --no-daemon`
- Session preset visibility bug fix (`waiting start` vs active ride):
  - Fixed regression where preset selector disappeared for the entire Session.
  - Updated visibility condition to keep preset visible during waiting-start and hide it only after ride is actively running.
  - Validation:
    - `./gradlew :app:compileDebugKotlin :app:installDebug --no-daemon`
    - manual (USB tablet): waiting-start shows preset, active ride hides preset (`PASS`).
    - `./scripts/adb/capture.sh --serial R92Y40YAZPB --no-record --out-dir .local/captures/session-layout-check`
    - screenshot: `.local/captures/session-layout-check/screenshot-20260220-044546.png`
- Session portrait preset selector and telemetry hierarchy refinements:
  - Added portrait-only layout presets in Session: `Balanced`, `Power first`, `Workout first`.
  - Added compact-after-selection preset UI (`Preset: ...` + `Change`) to reclaim vertical space immediately after choosing a preset.
  - Swapped metric placement as requested:
    - emphasized row: `HR | Power / target | Cadence / target`
    - upper telemetry row: `Speed | Kcal | Distance`
  - Added preset labels in `strings.xml`.
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:compileDebugKotlin :app:installDebug --no-daemon`
    - manual (USB tablet): preset selection + compact mode behavior verified.
    - `./scripts/adb/capture.sh --serial R92Y40YAZPB --no-record --out-dir .local/captures/session-layout-check`
    - screenshot: `.local/captures/session-layout-check/screenshot-20260220-043252.png`
- Session screen portrait column behavior update:
  - Forced `SessionScreen` to single-column content flow in portrait orientation, including tablet portrait widths.
  - Kept two-pane behavior available in landscape by gating two-pane rendering with orientation check.
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
- Emulator smoke hardening after real run feedback:
  - Fixed license acceptance handling in `scripts/adb/emulator-smoke.sh` under `set -o pipefail` so `yes | sdkmanager --licenses` no longer fails on benign broken pipe.
  - Added known Compose startup flake auto-retry (`--retries`, default `1`) for emulator instrumentation runs.
  - Added default exclusion of `@FlakyTest` in emulator smoke (`notAnnotation=androidx.test.filters.FlakyTest`) with opt-in override `--include-flaky`.
  - Marked rotation continuity test as `@FlakyTest` in `MainActivityContentFlowTest` so default emulator smoke stays stable while full coverage remains opt-in.
  - Updated `docs/adb-cheatsheet.md` with `--include-flaky` usage and behavior.
  - Validation:
    - `bash -n scripts/adb/emulator-smoke.sh`
    - `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
    - `scripts/adb/emulator-smoke.sh` (`ErgometerApi34`, 8 tests, 0 failures; flaky rotation test excluded by default)
- Local emulator smoke pipeline for fast regression:
  - Added `scripts/adb/emulator-smoke.sh` to create/boot AVD and run connected instrumentation tests pinned to emulator serial.
  - Added run artifact collection under `.local/emulator-test-runs/` (`emulator.log`, `logcat.log`, test XML/report copies, screenshot, summary).
  - Updated `docs/adb-cheatsheet.md` with emulator usage and a clear split:
    - emulator for fast UI/instrumentation regression,
    - USB tablet + Tunturi for BLE realism.
  - Validation:
    - `bash -n scripts/adb/emulator-smoke.sh`
    - `scripts/adb/emulator-smoke.sh --help`
    - `scripts/adb/emulator-smoke.sh --create-only` (expected local prerequisite error when SDK Command-line Tools are missing)
- P0 regression coverage closure for start/stop transitions:
  - Added `startAndStopFlowTransitionCompletesToSummaryOnAcknowledgement` to `SessionOrchestratorFlowTest`.
  - Added `stopFlowTimeoutCompletesToSummaryWithoutAcknowledgement` to cover timeout finalization from `STOPPING_AWAIT_ACK`.
  - Extended test `ManualHandler` with deterministic `advanceBy(...)` to drive delayed stop-timeout callbacks without wall-clock waits.
  - Validation:
    - `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.session.SessionOrchestratorFlowTest" --tests "com.example.ergometerapp.ble.FtmsControllerTimeoutTest" --no-daemon`
- Start-blocked reason attention pulse:
  - Added subtle pulsing animation for the disabled `Start session` reason text so setup blockers are easier to notice on tablet UI.
  - Kept copy and gating logic unchanged; only visual emphasis was added.
  - Validation:
    - `./gradlew --stop`
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
    - `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ergometerapp.ui.MainActivityContentFlowTest --no-daemon` (`SM-X210`: 9 tests, 0 failures)
- Start-button disabled reason messaging:
  - MENU now shows explicit reason text under `Start session` when start preconditions are not met.
  - Reason text is dynamic and reports missing setup directly (`workout`, `trainer`, import issue, execution issue) instead of leaving disabled state unexplained.
  - `MainViewModel.onStartSession()` is guarded by `canStartSession()` so runtime behavior matches UI gating.
  - Added instrumentation assertion to keep disabled+reason UX stable:
    - `startSessionButtonRemainsDisabledWhenStartEnabledIsFalse` now also verifies reason text rendering.
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
    - `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ergometerapp.ui.MainActivityContentFlowTest --no-daemon` (`SM-X210`: 9 tests, 0 failures)
- Start gating and disabled-visibility hardening:
  - `MainViewModel.onStartSession()` now requires full `canStartSession()` preconditions (valid workout + trainer), not just trainer selection.
  - MENU `Start session` button disabled colors were adjusted to `surfaceVariant/onSurfaceVariant` to increase visual separation in dark mode.
  - Added instrumentation regression `startSessionButtonRemainsDisabledWhenStartEnabledIsFalse` in `MainActivityContentFlowTest`.
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.session.SessionOrchestratorFlowTest" --no-daemon`
    - `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
    - `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ergometerapp.ui.MainActivityContentFlowTest --no-daemon` (`SM-X210`: 9 tests, 0 failures)
- BT connect-permission denial recovery hardening:
  - Added explicit in-app recovery mode for denied `BLUETOOTH_CONNECT` during session start:
    - show connection issue prompt with `Open settings` CTA,
    - clear stale prompt on explicit retry,
    - keep legacy trainer failure mode on `Search again`.
  - Added app settings deep-link callback from menu connection issue dialog to `ACTION_APPLICATION_DETAILS_SETTINGS`.
  - Extended tests:
    - `MainActivityContentFlowTest`: added `menuConnectionIssueDialogSupportsOpenSettingsRecoveryAction`.
    - `SessionOrchestratorFlowTest`: asserts denied-permission path sets settings-recovery prompt and clears on explicit retry.
  - Validation:
    - `./gradlew :app:compileDebugKotlin --no-daemon`
    - `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.session.SessionOrchestratorFlowTest" --no-daemon`
    - `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
    - `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.ergometerapp.ui.MainActivityContentFlowTest --no-daemon` (`SM-X210`: 8 tests, 0 failures)
- ADB one-command local device smoke pipeline:
  - Added `scripts/adb/device-smoke.sh` for repeatable local validation:
    - installs debug + androidTest APKs
    - clears app data (optional)
    - runs connected instrumentation tests (class/all-tests mode)
    - captures logcat and optional screen recording
    - stores timestamped artifacts under `.local/device-test-runs/`
  - Updated `docs/adb-cheatsheet.md` with usage examples and artifact layout.
  - Validation:
    - `scripts/adb/device-smoke.sh` on `SM-X210` (2 tests, 0 failures)
    - `bash -n scripts/adb/device-smoke.sh`
- `MENU` picker instrumentation coverage:
  - Added `menuPickerScanAndCloseStatesRenderExpectedActions` in `app/src/androidTest/java/com/example/ergometerapp/ui/MainActivityContentFlowTest.kt`.
  - Covers active picker title, scanning status anchor, `Stop scan` action, failed scan status, and `Close picker` action.
  - Validation:
    - `./gradlew connectedDebugAndroidTest --no-daemon` on `SM-X210` (3 tests, 0 failures).
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

## Session Update (2026-02-19 - ADB Capture Helper)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Added capture helper script:
  - `scripts/adb/capture.sh`
- Script captures:
  - screenshot (`.png`)
  - screen recording (`.mp4`)
- Supports:
  - device serial targeting
  - configurable output directory
  - configurable recording duration
  - optional screenshot-only or record-only modes
- Updated `docs/adb-cheatsheet.md` with helper usage.

### Next Task
- Use `capture.sh` together with `logcat-ergometer.sh` during scenario tests to keep reproducible evidence bundles (logs + media).

### Definition of Done
- One command can produce screenshot and recording artifacts in `.local/captures`.
- Command works on current test tablet (`SM-X210`).

### Risks / Open Questions
- Device may fallback to lower recording resolution when codec resources are constrained (`screenrecord` behavior).

### Validation Commands
1. `bash -n scripts/adb/capture.sh`
2. `./scripts/adb/capture.sh --serial R92Y40YAZPB --seconds 3`

## Session Update (2026-02-20 - Menu Connection-Issue Instrumentation Coverage)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Expanded `MainActivityContentFlowTest` instrumentation coverage for interactive MENU/BLE recovery states:
  - Added `menuConnectionIssueDialogShowsRecoveryActionsAndRoutesCallbacks`.
  - Verifies connection-issue dialog title/message rendering.
  - Verifies both CTA labels (`Search again`, `Dismiss`) and callback routing.
- Tightened picker scan-state assertions in existing test:
  - While scanning with stop-lock active, `Stop scan` is now asserted as disabled.
  - After scan completes, `Close picker` is now asserted as enabled.
- Practical on-device validation:
  - First smoke run failed with `No compose hierarchies found` while device state was likely in sleep/keyguard mode.
  - Re-ran after explicit wake/unlock; smoke passed (`3/3` tests) on `SM-X210`.

### Next Task
- Continue instrumentation expansion for lifecycle-sensitive UI paths:
  - Add rotation continuity coverage for `MENU` and `SESSION`.
  - Add permission deny/grant continuity coverage for BLE scan/connect paths.

### Definition of Done
- `MainActivityContentFlowTest` keeps existing flow anchors green and adds connection-issue recovery coverage.
- New test coverage is stable on connected tablet (`SM-X210`) using one-command smoke.
- Test execution checklist includes explicit pre-run device wake step to avoid false negatives.

### Risks / Open Questions
- Tablet sleep/keyguard can cause false instrumentation failures (`No compose hierarchies found`) if not woken before run.
- Current smoke scope still focuses on a single test class; broader regressions still depend on manual checks.

### Validation Commands
1. `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
2. `scripts/adb/device-smoke.sh`
3. `adb -s R92Y40YAZPB shell input keyevent KEYCODE_WAKEUP`
4. `adb -s R92Y40YAZPB shell wm dismiss-keyguard`
5. `scripts/adb/device-smoke.sh`

## Session Update (2026-02-20 - Rotation Continuity Instrumentation Coverage)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Expanded `MainActivityContentFlowTest` with rotation continuity coverage:
  - Added `menuAndSessionAnchorsRemainVisibleAcrossRotation`.
  - Verifies `MENU` title anchor remains visible after landscape rotation.
  - Verifies `SESSION` quit-action anchor remains visible after portrait rotation.
  - Resets requested orientation to `UNSPECIFIED` in a `finally` block to avoid cross-test orientation leakage.
- Existing instrumentation tests remain green with the new rotation case (`4` total tests in class).

### Next Task
- Add BLE permission deny/grant continuity instrumentation coverage:
  - ensure UI state continuity for scan/connect flows when permissions are denied then granted
  - keep assertions robust to existing animated scan-status text behavior

### Definition of Done
- Rotation continuity is regression-protected for core `MENU` and `SESSION` anchors.
- `MainActivityContentFlowTest` passes on-device with the added rotation test.
- Session handoff is updated with concrete commands and risks.

### Risks / Open Questions
- Tablet keyguard/sleep state can still produce false negatives if not dismissed before instrumentation launch.
- BLE-dependent tests require manual wake of the Tunturi ergometer before attempting trainer connection.

### Validation Commands
1. `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
2. `adb devices -l`
3. `adb -s R92Y40YAZPB shell input keyevent KEYCODE_WAKEUP`
4. `adb -s R92Y40YAZPB shell wm dismiss-keyguard`
5. `scripts/adb/device-smoke.sh`

## Session Update (2026-02-20 - BLE Scan Permission Continuity Instrumentation Coverage)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Added deny/grant continuity instrumentation coverage for BLE scan permission flow in:
  - `app/src/androidTest/java/com/example/ergometerapp/ui/MainActivityContentFlowTest.kt`
- New test:
  - `menuPickerFlowStaysConsistentAcrossScanPermissionDenyThenGrant`
  - Covers sequence:
    - picker open + `menu_device_scan_permission_required`
    - scanning state after grant (`menu_device_scan_status_scanning`)
    - completed state with discovered device row and done status
  - Asserts picker action semantics remain usable across the sequence (`Stop scan` lock while scanning, `Close picker` enabled after scan).
- Stabilized final picker action assertion with `performScrollTo()` to avoid viewport-only false negatives on tablet layouts.
- Existing instrumentation suite in this class now runs 5 tests green on device smoke.

### Next Task
- Add connect-flow continuity coverage around permission-gated start behavior:
  - verify start-session UI continuity when connect permission is denied and later granted
  - keep assertions anchored to stable visible strings/state transitions

### Definition of Done
- BLE scan permission deny/grant continuity is regression-protected by instrumentation.
- `MainActivityContentFlowTest` passes on device with the new coverage (`5/5`).
- Session handoff includes practical device pre-check notes.

### Risks / Open Questions
- Test environment stability still depends on dismissing tablet keyguard before instrumentation launch.
- BLE connection scenarios require manual wake of Tunturi ergometer before connection attempts.

### Validation Commands
1. `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
2. `adb -s R92Y40YAZPB shell input keyevent KEYCODE_WAKEUP`
3. `adb -s R92Y40YAZPB shell wm dismiss-keyguard`
4. `scripts/adb/device-smoke.sh`

## Session Update (2026-02-20 - Connect Permission Continuity Coverage)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Added connect-permission continuity instrumentation coverage in:
  - `app/src/androidTest/java/com/example/ergometerapp/ui/MainActivityContentFlowTest.kt`
- New instrumentation test:
  - `startSessionAnchorsStayConsistentAcrossConnectPermissionDenyThenGrant`
  - Verifies MENU start CTA remains actionable after a denied-path return to MENU.
  - Verifies transition anchors to CONNECTING remain stable once granted-path flow continues.
- Added orchestrator unit coverage for denied-then-granted callback behavior:
  - `app/src/test/java/com/example/ergometerapp/session/SessionOrchestratorFlowTest.kt`
  - New test: `connectPermissionDeniedThenGrantedKeepsFlowStableUntilExplicitRetry`
  - Verifies pending-start is cleared on deny and later grant does not auto-resume without explicit retry.
- Expanded instrumentation class total to 6 tests and verified on device smoke.

### Next Task
- Expand permission continuity coverage to include BLE scan/connect permission matrix edges:
  - connect permission denied while pending start is active and user retries explicitly
  - scan permission denied while picker is active and user restarts search

### Definition of Done
- Connect-permission continuity is covered in both UI instrumentation anchors and orchestrator state logic.
- On-device instrumentation smoke passes with all `MainActivityContentFlowTest` cases green.
- Session handoff includes required pre-run device checks.

### Risks / Open Questions
- Tablet sleep/keyguard still causes false negatives if not dismissed before instrumentation run.
- Tunturi ergometer must be woken manually before BLE connection attempts.

### Validation Commands
1. `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.session.SessionOrchestratorFlowTest.connectPermissionDeniedThenGrantedKeepsFlowStableUntilExplicitRetry" --no-daemon`
2. `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
3. `adb -s R92Y40YAZPB shell input keyevent KEYCODE_WAKEUP`
4. `adb -s R92Y40YAZPB shell wm dismiss-keyguard`
5. `scripts/adb/device-smoke.sh`

## Session Update (2026-02-20 - Permission Matrix Edge Retries)

### Branch
- `feature/ci-workflow-concurrency`

### Recently Completed
- Added connect-permission retry edge coverage in orchestrator unit tests:
  - `connectPermissionDeniedAllowsPendingStartToBeReArmedOnExplicitRetry`
  - Location: `app/src/test/java/com/example/ergometerapp/session/SessionOrchestratorFlowTest.kt`
  - Verifies denied permission clears pending start, and explicit user retry re-arms pending start deterministically.
- Added active-picker scan-permission retry edge coverage in instrumentation:
  - `activePickerPermissionDeniedStateSupportsExplicitSearchRetry`
  - Location: `app/src/androidTest/java/com/example/ergometerapp/ui/MainActivityContentFlowTest.kt`
  - Verifies active picker can recover from permission-required state via explicit `Search trainer` retry and return to scanning state.
- Existing connect continuity and scan continuity cases remain green.
- Instrumentation class now contains `7` passing tests on on-device smoke.

### Next Task
- Extend permission continuity beyond model-driven anchors into practical runtime-flow confidence:
  - run targeted manual permission toggling pass (deny -> grant) on device for both scan and connect
  - capture logs/screenshots for reproducible evidence bundle

### Definition of Done
- Permission matrix retry edges are covered by deterministic tests in both orchestrator and instrumentation layers.
- On-device smoke run passes all `MainActivityContentFlowTest` cases (`7/7`).
- Session handoff reflects latest edge coverage and validation commands.

### Risks / Open Questions
- Compose instrumentation remains model-driven and does not operate Android system permission dialogs directly.
- Device keyguard/sleep still needs explicit wake/unlock pre-check to avoid false negatives.
- BLE connect behavior depends on manual wake state of Tunturi ergometer before connection attempts.

### Validation Commands
1. `./gradlew :app:testDebugUnitTest --tests "com.example.ergometerapp.session.SessionOrchestratorFlowTest.connectPermissionDeniedThenGrantedKeepsFlowStableUntilExplicitRetry" --tests "com.example.ergometerapp.session.SessionOrchestratorFlowTest.connectPermissionDeniedAllowsPendingStartToBeReArmedOnExplicitRetry" --no-daemon`
2. `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`
3. `adb -s R92Y40YAZPB shell input keyevent KEYCODE_WAKEUP`
4. `adb -s R92Y40YAZPB shell wm dismiss-keyguard`
5. `scripts/adb/device-smoke.sh`

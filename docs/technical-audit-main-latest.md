# Technical Audit (main-latest snapshot)

## 1) TL;DR
- The architecture is cleanly split (BLE transport, FTMS command controller, session orchestration, UI), but there are still a few high-risk lifecycle gaps around FTMS command acknowledgements and reconnect UX.
- Most critical risk: request-control failures/timeouts do not have a deterministic user-facing failure transition, which can leave the app in `CONNECTING` without actionable recovery.
- FTMS command pipeline enforces single in-flight writes, but does not correlate responses to expected command state; out-of-order/late responses can mutate state incorrectly.
- Workout import/mapping diagnostics are better than average, yet degraded fallback is still enabled by default, which can hide unsupported workout semantics in production.
- Session data collection keeps full in-memory sample arrays for the whole workout, creating avoidable memory pressure in long sessions.
- CI covers compile/unit/lint/release assembly, but there are no instrumentation/UI tests for reconnect/state transitions/rotation.
- UI has good adaptive sizing primitives, but key screens still rely on full scroll columns instead of virtualization; this is acceptable now but weakens scalability.
- Logging is extensive in debug style but lacks structured production diagnostics pipeline (export/session correlation IDs/crash breadcrumbs).
- Release and signing path is generally healthy; partial signing config fails fast.
- Next sprint should focus on deterministic FTMS failure handling, command/response invariants, and reconnect test harness coverage before adding new features.

## 2) Top findings by severity

### Critical-1 — Request-control failure path can strand UI in CONNECTING
- **Problem**: Session transitions to `SESSION` only on `Request Control` success, but there is no equivalent terminal fallback when control is denied or times out.
- **Why this is a risk**: Users can be stuck in non-progressing `CONNECTING` (or protocol limbo) with no guided recovery path; this is a direct session-start regression risk.
- **Reference**: `app/src/main/java/com/example/ergometerapp/session/SessionOrchestrator.kt:371-373,389-397`; `app/src/main/java/com/example/ergometerapp/ble/FtmsController.kt:347-349`.
- **Concrete fix**: Add explicit handling for `(opcode=0x00, result!=0x01)` and controller timeout events to trigger `MENU` rollback + actionable error prompt (`search trainer` CTA), and optionally close/reopen FTMS link once.

### Critical-2 — FTMS response processing is not correlated to expected in-flight command
- **Problem**: `onControlPointResponse` accepts any response opcode/result and releases `BUSY`, without validating against the command that was sent.
- **Why this is a risk**: Late/out-of-order responses can falsely unblock queue flow and execute wrong pending operations (e.g., reset/stop/target), creating hard-to-reproduce trainer state bugs.
- **Reference**: `app/src/main/java/com/example/ergometerapp/ble/FtmsController.kt:275-315`.
- **Concrete fix**: Track expected opcode + sequence state for the in-flight command; ignore or quarantine mismatched responses and emit diagnostic event.

### High-1 — Long sessions accumulate unbounded telemetry sample lists
- **Problem**: `powerSamples`, `cadenceSamples`, and `heartRateSamples` append for entire session duration.
- **Why this is a risk**: Multi-hour workouts can cause avoidable memory growth and GC churn; this can degrade UI smoothness or trigger OOM on low-memory devices.
- **Reference**: `app/src/main/java/com/example/ergometerapp/session/SessionManager.kt:24-26,60,74,76,92`.
- **Concrete fix**: Replace raw sample storage with streaming aggregates (count/sum/max) and optional bounded ring buffers for diagnostics only.

### High-2 — Stop-flow summary is finalized before trainer STOP acknowledgement
- **Problem**: Session summary is captured before STOP ack/disconnect completion.
- **Why this is a risk**: If device teardown fails or telemetry keeps flowing briefly, summary may not match actual trainer stop point, complicating analytics and user trust.
- **Reference**: `app/src/main/java/com/example/ergometerapp/session/SessionOrchestrator.kt:209-217`; `app/src/main/java/com/example/ergometerapp/session/SessionManager.kt:160-203`.
- **Concrete fix**: Store provisional summary at stop press, then finalize/overwrite at STOP ack or disconnect boundary with consistent timestamp policy.

### High-3 — HR BLE client lacks reconnect/state-ownership guardrails
- **Problem**: HR path has no reconnect policy, no stale-callback generation checks, and no disconnect callback surfaced to orchestration.
- **Why this is a risk**: HR telemetry can silently disappear after transient radio issues, while UI/session logic assumes strap behavior remains stable.
- **Reference**: `app/src/main/java/com/example/ergometerapp/ble/HrBleClient.kt:23-132`.
- **Concrete fix**: Mirror FTMS-style lifecycle handling minimally: active-generation guard, optional bounded reconnect attempts, and explicit `onDisconnected` callback to orchestration/UI.

### High-4 — Workout fallback mode enabled by default masks mapping incompatibilities
- **Problem**: Legacy fallback is enabled by default (`ergometer.workout.allowLegacyFallback=true`).
- **Why this is a risk**: Unsupported steps may execute in degraded mode unnoticed, producing workouts that differ from source intent and increasing hidden regressions.
- **Reference**: `gradle.properties:27`; `app/src/main/java/com/example/ergometerapp/session/SessionOrchestrator.kt:618-643,660-686`.
- **Concrete fix**: Flip default to strict mode for release builds; allow fallback only behind explicit debug/dev flag and clear user-visible warning.

### Medium-1 — FTMS parser swallows parse exceptions without diagnostics
- **Problem**: Parser catches all exceptions and returns `valid=false` without recording reason.
- **Why this is a risk**: Production troubleshooting cannot distinguish malformed payloads, device quirks, and parser defects.
- **Reference**: `app/src/main/java/com/example/ergometerapp/ftms/IndoorBikeParser.kt:150-169`.
- **Concrete fix**: Emit structured parse-failure event (reason + payload length/flags) to debug buffer/log channel with privacy-safe truncation.

### Medium-2 — Device scan list sorting on each callback is O(n log n) churn
- **Problem**: Every scan result triggers list sort+replace.
- **Why this is a risk**: Burst scan traffic can cause unnecessary recompositions and UI jank on older devices.
- **Reference**: `app/src/main/java/com/example/ergometerapp/MainViewModel.kt:318-333`.
- **Concrete fix**: Throttle UI updates (e.g., 200–300 ms batch), maintain keyed map, and sort only at batch flush.

### Medium-3 — UI uses non-virtualized scrolling for potentially large content sections
- **Problem**: Menu/session/summary rely on `Column + verticalScroll` and `forEach` rendering.
- **Why this is a risk**: As content grows (devices, metadata, future analytics cards), compose work scales poorly versus lazy containers.
- **Reference**: `app/src/main/java/com/example/ergometerapp/ui/Screens.kt:327-347,859-863,1318`.
- **Concrete fix**: Migrate large/repeatable sections to `LazyColumn`/`LazyVerticalGrid`, keep only small fixed blocks in regular columns.

### Medium-4 — Reconnect intent flag exists but is effectively dead state
- **Problem**: `reconnectBleOnNextSessionStart` is reset/logged but has no real decision path.
- **Why this is a risk**: Dead flags increase cognitive load and can hide partially implemented reconnect design.
- **Reference**: `app/src/main/java/com/example/ergometerapp/AppUiState.kt:45`; `app/src/main/java/com/example/ergometerapp/session/SessionOrchestrator.kt:159,742`.
- **Concrete fix**: Either remove the flag entirely or implement explicit semantics with tests (set/consume/reset lifecycle).

### Medium-5 — Instrumentation coverage is placeholder-only
- **Problem**: Android instrumentation test suite currently contains only a package-name smoke test.
- **Why this is a risk**: Critical UI lifecycle scenarios (rotation, background/foreground, permission denial flows) are not regression-protected.
- **Reference**: `app/src/androidTest/java/com/example/ergometerapp/ExampleInstrumentedTest.kt:14-21`; `.github/workflows/android-build.yml:56-77`.
- **Concrete fix**: Add compose instrumentation tests for menu->connecting->session->stopping->summary transitions and permission/reconnect branches; run them in CI (managed emulator).

### Low-1 — Manifest still includes legacy Bluetooth permissions on minSdk 33
- **Problem**: `BLUETOOTH` and `BLUETOOTH_ADMIN` are declared despite modern permission model.
- **Why this is a risk**: Low direct runtime risk, but adds confusion and policy-review noise.
- **Reference**: `app/src/main/AndroidManifest.xml:5-10`; `app/build.gradle.kts:54`.
- **Concrete fix**: Remove legacy permissions unless a documented compatibility reason exists.

### Low-2 — `allowBackup=true` may unintentionally include user/session data
- **Problem**: Application backup is enabled.
- **Why this is a risk**: Potential privacy/compliance concern depending on product policy for workout/session metadata.
- **Reference**: `app/src/main/AndroidManifest.xml:13-15`.
- **Concrete fix**: Confirm product policy; disable backup or explicitly scope backed-up data via rules.

## 3) Prioritized implementation plan

### P0 (now)
1. Deterministic request-control failure/timeout UX transition with recovery CTA. **Estimate: M**
2. Command/response correlation invariant in `FtmsController` (+ mismatch diagnostics). **Estimate: M**
3. Long-session memory fix: streaming aggregates in `SessionManager`. **Estimate: M**
4. Add regression tests for FTMS timeout/denied-control/start-stop transitions. **Estimate: L**

### P1 (next sprint)
1. HR client lifecycle hardening (disconnect callback + reconnect policy + stale callback guard). **Estimate: M**
2. Strict-by-default workout execution policy for release; fallback opt-in. **Estimate: S**
3. Add instrumentation tests for portrait/landscape and permission flow state continuity. **Estimate: L**
4. Parse failure structured diagnostics for FTMS payload failures. **Estimate: S**

### P2 (later)
1. UI virtualization (`Lazy*`) and scan batching for scalability. **Estimate: M**
2. Remove dead reconnect flag or complete implementation. **Estimate: S**
3. Manifest/backup cleanup after product-policy decision. **Estimate: S**

## 4) Quick wins (< 1 day)
1. Add explicit error banner + return-to-menu flow when request-control response is non-success.
2. Log and surface FTMS command timeout reason in UI connection issue text.
3. Replace sample arrays with aggregate counters in `SessionManager` (no behavior change to summary fields).
4. Remove unused `reconnectBleOnNextSessionStart` field if no planned usage.
5. Add one unit test for mismatched FTMS response opcode handling (current behavior baseline + fixed expectation).
6. Add one instrumentation smoke for rotate in `MENU` and `SESSION` without crash.

## 5) Testing and validation plan

### Missing automated tests
- FTMS command ownership edge cases:
  - Request control denied.
  - Request control timeout.
  - Out-of-order response opcode.
  - STOP ack lost then disconnect.
- Reconnect race matrix:
  - Reconnect attempt succeeds on Nth retry.
  - Explicit close while reconnect runnable is pending.
  - Stale callback from old GATT generation.
- Workout execution policy:
  - Strict mode blocks unsupported steps in release config.
  - Fallback mode explicit warning text and behavior.
- UI state machine instrumentation:
  - `MENU -> CONNECTING -> SESSION -> STOPPING -> SUMMARY` and abort paths.
  - Permission deny/grant loops for `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`.

### Required manual tests
- **BLE reconnect focus**:
  1. Start session, power trainer off for 5–10s, power on, confirm reconnect behavior and UI status.
  2. Trigger reconnect while pressing end session; verify no stuck STOPPING state.
  3. Trainer in/out of range repeatedly; ensure no duplicate disconnect prompts.
- **Portrait/Landscape UI focus**:
  1. Rotate during active scan list updates in menu.
  2. Rotate in `CONNECTING`, `SESSION`, and `STOPPING` states.
  3. Large-screen tablet landscape: verify chart, telemetry cards, and bottom CTA placement.
- **Workout pipeline focus**:
  1. Valid ZWO with all supported steps.
  2. Unsupported step file (ensure strict/fallback behavior is explicit).
  3. Corrupt XML file and empty file diagnostics.

## 6) What should NOT be changed now
- Keep the current separation of concerns (`FtmsBleClient` vs `FtmsController` vs `SessionOrchestrator`), because it is a strong base for targeted hardening.
- Keep generation-based stale callback protection in FTMS orchestrator; this already prevents a class of reconnect race regressions.
- Keep release-signing fail-fast guardrails and CI release assembly/lint checks.
- Keep the current stop-flow state machine concept (`STOPPING_AWAIT_ACK`) while tightening edge paths.

## 7) Open product decisions needed
1. Should the app always block start when FTMS control is denied, or allow telemetry-only mode with explicit limitation?
2. For unsupported workout steps, is degraded execution acceptable in production, or should release builds be strict-only?
3. What is the acceptable reconnect window (attempt count/backoff) before user-facing failure?
4. Is persistent backup of session/workout metadata allowed by product/privacy policy?
5. Should HR strap disconnect during session show a visible warning or remain silent fallback to bike HR?

## 8) Environment and validation status for this audit
- Attempted local automated tests, but they are blocked in this environment due to missing Android SDK path (`ANDROID_HOME` / `local.properties sdk.dir`).
- Command run:
  - `./gradlew :app:testDebugUnitTest --no-daemon`
- Failure reason observed:
  - `SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in local.properties`.


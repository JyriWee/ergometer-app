# ErgometerApp Architecture

## Purpose
This document explains the project structure for contributors who want to reuse this repository as a starting point.

## Runtime Layers

### 1) UI and Navigation Layer
- `app/src/main/java/com/example/ergometerapp/MainActivity.kt`
- `app/src/main/java/com/example/ergometerapp/ui/MainActivityContent.kt`
- `app/src/main/java/com/example/ergometerapp/ui/Screens.kt`

Responsibilities:
- Render top-level destinations (`MENU`, `CONNECTING`, `SESSION`, `STOPPING`, `SUMMARY`).
- Forward user intents to `MainViewModel`.
- Keep UI mostly stateless by consuming immutable UI models.

### 2) ViewModel and App State Layer
- `app/src/main/java/com/example/ergometerapp/MainViewModel.kt`
- `app/src/main/java/com/example/ergometerapp/AppUiState.kt`

Responsibilities:
- Own long-lived app/session services across configuration changes.
- Hold user-selected device/workout/FTP state.
- Coordinate menu-only device availability probes (scan-only, no GATT ownership).

### 3) Session Orchestration Layer
- `app/src/main/java/com/example/ergometerapp/session/SessionOrchestrator.kt`

Responsibilities:
- Coordinate FTMS lifecycle, workout lifecycle, and UI navigation.
- Enforce start/stop state transitions and recovery behavior.
- Connect callback-driven BLE events to deterministic UI/session transitions.

### 4) BLE Transport and FTMS Control Layer
- `app/src/main/java/com/example/ergometerapp/ble/FtmsBleClient.kt`
- `app/src/main/java/com/example/ergometerapp/ble/FtmsController.kt`
- `app/src/main/java/com/example/ergometerapp/ble/HrBleClient.kt`
- `app/src/main/java/com/example/ergometerapp/ble/HrReconnectCoordinator.kt`
- `app/src/main/java/com/example/ergometerapp/ble/BleDeviceScanner.kt`

Responsibilities:
- Manage BLE link setup/teardown and service discovery.
- Serialize FTMS Control Point commands (single in-flight command model).
- Apply timeout handling and stale-callback protection.
- Provide bounded HR reconnect behavior.

### 5) Workout Import and Execution Layer
- `app/src/main/java/com/example/ergometerapp/workout/WorkoutImportService.kt`
- `app/src/main/java/com/example/ergometerapp/workout/ZwoParser.kt`
- `app/src/main/java/com/example/ergometerapp/workout/ExecutionWorkoutMapper.kt`
- `app/src/main/java/com/example/ergometerapp/workout/runner/WorkoutStepper.kt`
- `app/src/main/java/com/example/ergometerapp/workout/runner/WorkoutRunner.kt`

Responsibilities:
- Parse imported workout files.
- Convert parsed workouts into executable segments.
- Drive timed target updates for FTMS power control.

### 6) Session Metrics and Persistence Layer
- `app/src/main/java/com/example/ergometerapp/session/SessionManager.kt`
- `app/src/main/java/com/example/ergometerapp/session/ActualTssAccumulator.kt`
- `app/src/main/java/com/example/ergometerapp/session/SessionStorage.kt`

Responsibilities:
- Aggregate power/cadence/HR/session duration data.
- Compute summary metrics including Actual TSS.
- Persist session summaries.

## Core Invariants

1. FTMS control writes are serialized.
- `FtmsController` allows only one active control command at a time.

2. Session start does not become active before control is granted.
- `CONNECTING -> SESSION` transition is gated by request-control success flow.

3. Stop flow is explicit.
- `STOPPING` state exists to prevent ambiguous end-session transitions.

4. UI state is callback-driven, not timer-driven business logic.
- BLE callbacks and orchestrator events decide state transitions.

5. Menu availability probes are passive.
- Menu probe scans only advertisements and does not claim GATT/control ownership.

## Session Flow (High Level)

### Start Flow
1. User selects workout and trainer on `MENU`.
2. User taps `Start session`.
3. Orchestrator opens FTMS link and enters `CONNECTING`.
4. After FTMS ready + control granted, app enters `SESSION`.
5. Workout runner starts when cadence gating conditions are met.

### Stop Flow
1. User taps end session.
2. App enters `STOPPING` and sends FTMS stop/reset sequence.
3. Summary finalizes on acknowledgement, disconnect, or stop timeout fallback.
4. App navigates to `SUMMARY`.

## Why This Shape Works
- BLE protocol handling stays out of composables.
- UI can be reworked without changing FTMS behavior.
- Workout parsing/mapping/execution can evolve independently from BLE transport.
- Tests can target protocol and flow logic without real hardware.

## Suggested Read Order for New Contributors
1. `MainActivityContent.kt`
2. `MainViewModel.kt`
3. `SessionOrchestrator.kt`
4. `FtmsBleClient.kt` and `FtmsController.kt`
5. `WorkoutImportService.kt` and `ExecutionWorkoutMapper.kt`
6. `SessionManager.kt`

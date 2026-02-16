# ErgometerApp

ErgometerApp is an Android app for structured indoor cycling sessions with FTMS trainers and optional BLE heart-rate sensors.

The project is intentionally pragmatic: protocol correctness and session stability first, UI polish second.

## What It Does
- Imports workout files in ZWO/XML format (unsupported formats return typed import errors).
- Controls FTMS trainer target power through a serialized Control Point pipeline.
- Supports optional BLE HR sensor connection and reconnect handling.
- Runs cadence-gated workout execution (start/pause/resume behavior in session flow).
- Shows live session metrics and workout profile chart.
- Produces post-session summary including planned and actual TSS.

## Requirements
- Android 13+ (`minSdk = 33`)
- FTMS-compatible trainer
- BLE heart-rate sensor (optional)

## Quick Start

### 1) Open the project
1. Clone this repository.
2. Open it in Android Studio.
3. Let Gradle sync complete.

### 2) Validate setup from CLI
Run from project root:

```bash
./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :app:lintDebug --no-daemon
```

### 3) Run on device
1. Connect an Android device with Bluetooth.
2. Start debug build from Android Studio.
3. Grant Bluetooth permissions when requested.
4. In `MENU`: select trainer, optional HR, workout file, then start session.

## Configuration

### Gradle properties
- `ergometer.ftp.watts`
  - Default FTP value shown on first app launch.
- `ergometer.workout.allowLegacyFallback`
  - Controls whether unsupported workouts can run in degraded fallback mode.
- `ergometer.release.minify`
  - Controls release minification (`true` by default).

### Release signing (CI/local)
Set all of these (environment variables preferred):
- `ERGOMETER_RELEASE_STORE_FILE`
- `ERGOMETER_RELEASE_STORE_PASSWORD`
- `ERGOMETER_RELEASE_KEY_ALIAS`
- `ERGOMETER_RELEASE_KEY_PASSWORD`

See `docs/ci-release-signing.md` for details.

## Validation Commands

```bash
./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
./gradlew :app:lintDebug --no-daemon
```

## Documentation Index
- Architecture overview: `docs/architecture.md`
- Onboarding guide: `docs/onboarding.md`
- BLE status and recovery behavior: `docs/ble-status-and-recovery.md`
- AI-assisted development note (FI/EN): `docs/ai-assisted-development-note.md`
- Next-session handoff notes: `docs/next-session.md`
- CI signing/release notes: `docs/ci-release-signing.md`
- Adaptive UI checklist (Android 17): `docs/android-17-adaptive-checklist.md`

## Development Notes
- Prefer feature branches for all changes; avoid direct work on `main`.
- Keep changes small and testable.
- Update `docs/next-session.md` at session end.

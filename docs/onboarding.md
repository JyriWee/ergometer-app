# Onboarding Guide

## Audience
This guide is for contributors who are new to Android and want to run and extend this project safely.

## Prerequisites
- Android Studio (latest stable recommended).
- Android SDK installed through Android Studio.
- A physical Android device with Bluetooth support (recommended for FTMS/HR testing).
- Git configured locally.

## Clone and Open
1. Clone repository.
2. Open project root in Android Studio.
3. Let Gradle sync complete.

## First Build (CLI)
Run from project root:

```bash
./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :app:lintDebug --no-daemon
```

If both pass, project setup is healthy.

## Run on Device
1. Connect Android device and enable developer options + USB debugging.
2. Start app from Android Studio (`app` debug variant).
3. Grant Bluetooth permissions when requested.

## First Functional Smoke Test
1. On `MENU`, set FTP value.
2. Select trainer using `Search trainer`.
3. (Optional) Select HR device using `Search HR`.
4. Select workout file.
5. Start session and verify transition `MENU -> CONNECTING -> SESSION`.
6. End session and verify `STOPPING -> SUMMARY`.

## Daily Development Workflow
1. Create a feature branch (do not work directly on `main`).
2. Implement one focused change set.
3. Validate locally.
4. Commit only tested changes.
5. Push feature branch and merge to `main` after review/testing.

## Recommended Validation Commands
```bash
./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:lintDebug --no-daemon
```

Optional Android test compile check:

```bash
./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
```

## Common Issues

### Android SDK location missing
Symptom: Gradle reports missing `ANDROID_HOME`/`sdk.dir`.

Fix:
- Open project in Android Studio and ensure SDK is installed.
- Confirm `local.properties` points to valid SDK path.

### BLE device not discovered
Check:
- Device Bluetooth is enabled.
- Runtime scan permission is granted.
- Trainer/strap is powered on and advertising.

### Session stuck in connecting/stopping
Collect:
- Logcat around FTMS events (`FTMS` tag).
- Exact screen state transitions and timestamps.

## Key Documents
- Architecture overview: `docs/architecture.md`
- BLE status behavior and recovery: `docs/ble-status-and-recovery.md`
- CI release/signing notes: `docs/ci-release-signing.md`
- Next session handoff: `docs/next-session.md`

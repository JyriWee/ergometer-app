# Changelog

All notable changes to this project are documented in this file.

## [0.1.0] - 2026-02-18

### Added
- FTMS trainer control pipeline with serialized Control Point handling and timeout recovery.
- BLE reconnect and ownership handling improvements for session stability.
- Workout import flow with typed parse/import errors.
- Workout runner execution mapping and planned/actual TSS reporting.
- In-app workout editor MVP with ZWO export and menu apply flow.
- Post-session summary including distance, calories, power, cadence, and heart-rate metrics.
- Public contribution baseline:
  - MIT `LICENSE`
  - `CONTRIBUTING.md`
  - `CODE_OF_CONDUCT.md`
  - `SECURITY.md`
  - GitHub issue and pull request templates

### Changed
- Refined MENU, SESSION, SUMMARY, and WORKOUT EDITOR UI theme parity (dark/light).
- Improved BLE device picker stability and scan-throttle handling.
- Added release pipeline checks in CI (`assembleRelease`, `lintRelease`).

### Notes
- Current Gradle app version fields are managed in `app/build.gradle.kts`.
- This release is intended as the first public, usable baseline and extension platform.


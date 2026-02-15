# Next Session

## Branch
- current: `main`

## Recently Completed
- Added CI release verification (`:app:assembleRelease` with minify + `:app:lintRelease`).
- Added optional secrets-based release signing wiring (no signing data in repository).
- Added release/signing documentation in `docs/ci-release-signing.md`.
- Fixed GitHub Actions expression error by removing direct `secrets` usage from step-level `if` condition.

## Next Task
- DISCUSS: Define V1 scope for `session -> activity export` (prefer `.fit`, optional `.tcx` fallback).

## Definition of Done
- Export target and file format(s) are decided.
- Minimum exported fields are agreed (time, power, cadence, HR, speed, distance, calories, summary).
- Implementation order is agreed (UI trigger, exporter, validation on target platforms).

## Risks / Open Questions
- `.fit` interoperability details vary by platform if required messages/fields are missing.
- Need to decide whether to export at 1 Hz or preserve native sample intervals.
- Need to decide where export action lives in UI (Summary screen vs separate history/export flow).

## Validation
1. `./gradlew :app:compileDebugKotlin --no-daemon`
2. `./gradlew :app:testDebugUnitTest --no-daemon`
3. `./gradlew :app:assembleRelease --no-daemon -Pergometer.release.minify=true`
4. `./gradlew :app:lintRelease --no-daemon -Pergometer.release.minify=true`
5. Manual check: generate one exported file and import it to at least one target platform.

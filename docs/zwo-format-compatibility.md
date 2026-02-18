# ZWO Format Compatibility

This document records the current ZWO/XML field compatibility in ErgometerApp.

For the full offline format catalog, use:
- `docs/zwo-format-reference.md`

## Sources
- Zwift workout tag reference generated from built-in Zwift workouts:
  - <https://github.com/h4l/zwift-workout-file-reference>
  - Raw reference used for field inventory:
    <https://raw.githubusercontent.com/h4l/zwift-workout-file-reference/master/zwift_workout_file_tag_reference.md>

## Important Clarification
- `.zwo` files are XML documents.
- `.xml` is only a generic filename extension for XML files.
- In this app, import accepts both `.zwo` and `.xml` when content matches workout XML.

## Current Import Support (Parser)

### Root-level metadata
- Parsed and used:
  - `<name>`
  - `<description>`
  - `<author>`
  - `<tags><tag .../></tags>` (attribute/text variants)
- Ignored safely:
  - Other optional metadata fields from Zwift built-in files (for example `category`, `sportType`, `durationType`, `workoutLength`, and similar).

### Workout step tags
- Parsed as first-class step types:
  - `<Warmup>`
  - `<Cooldown>`
  - `<SteadyState>`
  - `<Ramp>`
  - `<IntervalsT>`
  - `<FreeRide>`
- Parsed aliases/fallbacks:
  - `<Freeride>` -> `FreeRide`
  - `<SolidState>` -> `SteadyState`
  - `<MaxEffort>` -> `FreeRide`

### Attribute compatibility
- Warmup/Cooldown/Ramp:
  - Uses `PowerLow`/`PowerHigh` when present.
  - Falls back from `Power` when low/high are missing.
- SteadyState/SolidState:
  - Uses `Power` directly when present.
  - Falls back to average of `PowerLow` + `PowerHigh` when `Power` is missing.
- IntervalsT:
  - Uses `OnPower`/`OffPower` when present.
  - Falls back to averaged range values from `PowerOnLow`/`PowerOnHigh` and `PowerOffLow`/`PowerOffHigh`.

### Unknown ZWO step tags
- Unknown step tags are preserved as `Unknown` steps when they look executable
  (for example they include `Duration` or `OnDuration` and have an uppercase step-like name).

## Current Export Support (Workout Editor)
- Editor export always writes XML content with `.zwo`-compatible structure:
  - Root: `<workout_file>`
  - Metadata: `<author>`, `<name>`, `<description>`, `<sportType>bike</sportType>`, `<durationType>time</durationType>`
  - Steps: `<SteadyState>`, `<Ramp>`, `<IntervalsT>`
- Editor power input is percent-based in UI (for example `80`) and converted to ZWO fraction values on export (`0.80`).

## Known Scope Limits
- The app intentionally does not execute every optional Zwift-specific element/sub-element
  (for example `TextEvent`, `TextNotification`, `gameplayevent`, plan metadata fields).
- Unsupported/unknown executable-looking steps are preserved during parse for forward compatibility,
  but strict execution mapping may reject them for ERG execution.

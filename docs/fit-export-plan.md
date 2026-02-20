# FIT Export Plan

## Goal
Enable exporting completed training sessions from ErgometerApp to `.fit` activity files for external platforms (e.g. Strava, Intervals.icu, GoldenCheetah), while keeping the app offline-first and stable.

## Scope
This document defines a practical implementation model for FIT export based on:
- Current ErgometerApp session data model.
- Official Garmin FIT documentation and SDK message definitions.

## Current Data Inventory (ErgometerApp)
From `SessionSummary` and session runtime state we currently have:
- Duration seconds
- Actual TSS (double)
- Average/max power
- Average/max cadence
- Average/max heart rate
- Total distance (meters)
- Total energy (kcal)

Important limitation:
- The app currently stores summary aggregates, not a durable per-second timeline of record samples (power/cadence/hr/distance over time).

## FIT Requirements Relevant to Activity Export
For FIT Activity files, the required message types are:
- `FileId`
- `Activity`
- `Session`
- `Lap`
- `Record`

For summary messages (`Session`, `Lap`, `Length`, etc.), the required fields include:
- `start_time`
- `total_elapsed_time`
- `total_timer_time`
- `timestamp`

For `Record` messages:
- `timestamp` is required
- At least one additional value is required (e.g., distance/power/hr/cadence)

Typical activity ordering uses summary-last sequencing:
- `FileId` -> optional `DeviceInfo` -> `Event(timer_start)` -> `Record...` -> `Lap` -> `Session` -> `Activity`

## Proposed Export Model

### v1 (Minimal, Valid Activity Export)
Produce one single-sport cycling FIT file per completed session.

Message set:
- Required: `FileId`, `Record` (at least one), `Lap`, `Session`, `Activity`
- Recommended optional: `Event` (timer start/stop), `DeviceInfo`

Semantics:
- Sport: cycling (and indoor sub-sport where supported by chosen SDK profile)
- No GPS required for indoor rides
- If no full sample stream exists yet, write at least one valid `Record` with timestamp + one metric (e.g., distance or power) so file stays compliant.

### v1.1 (Useful for Analysis Platforms)
Add export sample stream at fixed interval (recommended 1 Hz):
- `Record.timestamp`
- `Record.power`
- `Record.cadence`
- `Record.heart_rate`
- `Record.distance`
- optional `Record.speed`

This is the first version that gives strong chart parity in third-party tools.

### v2 (Higher Fidelity)
Add richer semantics:
- Pause/resume `Event` timeline
- Workout step boundaries as lap markers (or additional events)
- Optional developer fields for app-specific metadata

## Field Mapping (Current Data -> FIT)

### FileId
- `type` = Activity (required)
- `manufacturer`, `product`, `serial_number`, `time_created`

### Session (summary)
- `start_time` <- session start timestamp
- `timestamp` <- session end timestamp
- `total_elapsed_time` <- `durationSeconds`
- `total_timer_time` <- `durationSeconds` (until pause timeline is modeled separately)
- `total_distance` <- `distanceMeters`
- `total_calories` <- `totalEnergyKcal`
- `avg_power` / `max_power` <- summary
- `avg_cadence` / `max_cadence` <- summary
- `avg_heart_rate` / `max_heart_rate` <- summary
- `training_stress_score` <- `actualTss` (scaled to field precision; SDK defines scale 10)

### Lap
Use one lap for v1:
- Mirror session duration and totals in lap fields
- Add split/lap support in later phase

### Record
- v1: one minimal record (timestamp + one value)
- v1.1+: periodic records from sampled timeline

### Activity
- `timestamp` <- session end
- `total_timer_time` <- `durationSeconds`
- `num_sessions` = 1
- `type` = manual/auto by implementation choice
- `event` + `event_type` for stop semantics when supported
- `local_timestamp` for timezone reconstruction

## Architecture Proposal
Introduce a dedicated export pipeline to keep domain logic isolated:
- `session/export/FitExportService`
- `session/export/FitModelMapper`
- `session/export/FitWriter` (SDK-backed or custom binary writer)
- `session/export/FitExportResult` (typed success/failure)

Recommended runtime flow:
1. Build immutable export snapshot from final session state.
2. Map snapshot to FIT model (message-level DTOs).
3. Write file in app-private storage, then share via Android share sheet.
4. Surface precise user-visible error on export failure.

## Implementation Decision: Writer Strategy

### Option A: Official Garmin FIT Java SDK (fastest)
Pros:
- Lowest protocol risk
- Direct access to message classes and encode utilities

Cons:
- License is Garmin FIT Protocol License, not a standard OSS license.
- Must validate compatibility with this project’s MIT/open-template distribution goals.

### Option B: Custom Minimal FIT Writer (more effort)
Pros:
- Full control over licensing and distribution
- Smaller binary footprint if kept minimal

Cons:
- Higher protocol correctness burden (headers, definitions, CRC, scaling, message ordering)

Practical recommendation:
- Start with Option A for technical validation in a branch.
- Before public release policy is finalized, decide whether to keep SDK dependency or replace with custom writer.

## Risks
- **Data fidelity risk:** Summary-only export (without full Record timeline) may import but look sparse.
- **Compatibility risk:** Different platforms have different tolerance for minimal files.
- **Licensing risk:** FIT SDK license terms require explicit review for public template distribution.
- **Semantics risk:** TSS scaling and field optionality must match profile definitions exactly.

## Validation Plan

### Automated
- Unit tests:
  - Timestamp conversion and FIT epoch handling
  - Field scaling (seconds, meters, m/s, TSS scale)
  - Required message presence and ordering
- Golden-file tests:
  - Encode known session -> decode and assert critical values

### Manual
- Import smoke tests on target platforms:
  - Strava
  - Intervals.icu
  - GoldenCheetah
- Verify:
  - Duration
  - Distance
  - Avg/max power
  - Avg/max cadence
  - Avg/max HR
  - Total calories
  - TSS (if consumed)

## Next Concrete Step
Implement `v1` minimal valid export behind a feature flag, then verify decoding and platform import before adding 1 Hz record sampling.

## References
- Garmin FIT SDK docs: https://developer.garmin.com/fit/
- FIT Protocol: https://developer.garmin.com/fit/protocol/
- Activity File type: https://developer.garmin.com/fit/file-types/activity/
- Encoding Activity Files cookbook: https://developer.garmin.com/fit/cookbook/encoding-activity-files/
- Garmin FIT Java SDK (Maven + examples): https://github.com/garmin/fit-java-sdk
- FIT Java SDK `RecordMesg` field definitions: https://raw.githubusercontent.com/garmin/fit-java-sdk/master/src/main/java/com/garmin/fit/RecordMesg.java
- FIT Java SDK `SessionMesg` field definitions (includes `training_stress_score`): https://raw.githubusercontent.com/garmin/fit-java-sdk/master/src/main/java/com/garmin/fit/SessionMesg.java
- FIT Java SDK `DateTime` (FIT/Unix offset): https://raw.githubusercontent.com/garmin/fit-java-sdk/master/src/main/java/com/garmin/fit/DateTime.java
- FIT Protocol License text: https://raw.githubusercontent.com/garmin/fit-java-sdk/master/LICENSE.txt

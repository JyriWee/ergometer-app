# FIT Export Validation Run (2026-02-19)

## Artifact
- File: `.local/fit-exports/session_2026-02-19_06-48-25.fit`
- Source app branch: `feature/ci-workflow-concurrency`
- Purpose: baseline expected values for cross-platform import checks

## FIT Structural Validation
- Header signature `.FIT`: PASS
- Header CRC: PASS
- File CRC: PASS
- Required messages present: PASS (`file_id`, `record`, `lap`, `session`, `activity`)
- Record timeline present: PASS (`1647` records)

## Expected Values (Source of Truth for Import Comparison)
These values were extracted directly from the FIT file payload.

| Metric | Expected Value |
|---|---|
| Start time (UTC) | `2026-02-19T04:20:58Z` |
| End time (UTC) | `2026-02-19T04:48:25Z` |
| Duration | `27:27` (`1647 s`) |
| Distance | `6070 m` |
| Total calories | `121 kcal` |
| Average power | `71 W` |
| Max power | `124 W` |
| Average heart rate | `110 bpm` |
| Max heart rate | `121 bpm` |
| Average cadence | `69 rpm` |
| Max cadence | `94 rpm` |
| Actual TSS | `28.3` |
| Record count | `1647` |

## Tolerance Rules
- Duration: exact or `+/- 1 s`
- Distance: exact or `+/- 1 m`
- Calories: exact or `+/- 1 kcal`
- Avg/max power, HR, cadence: exact or `+/- 1`
- TSS: exact or `+/- 0.1`

## Platform Import Results

### GoldenCheetah
- Import status: `PASS`
- Notes: accepted and data visible.

| Metric | Expected | GoldenCheetah | Delta | PASS/FAIL |
|---|---|---|---|---|
| Duration | `27:27` |  |  |  |
| Distance | `6070 m` |  |  |  |
| Total calories | `121 kcal` |  |  |  |
| Average power | `71 W` |  |  |  |
| Max power | `124 W` |  |  |  |
| Average HR | `110 bpm` |  |  |  |
| Max HR | `121 bpm` |  |  |  |
| Average cadence | `69 rpm` |  |  |  |
| Max cadence | `94 rpm` |  |  |  |
| Actual TSS | `28.3` |  |  |  |

### Intervals.icu
- Import status: `PASS`

| Metric | Expected | Intervals.icu | Delta | PASS/FAIL |
|---|---|---|---|---|
| Duration | `27:27` |  |  |  |
| Distance | `6070 m` |  |  |  |
| Total calories | `121 kcal` |  |  |  |
| Average power | `71 W` |  |  |  |
| Max power | `124 W` |  |  |  |
| Average HR | `110 bpm` |  |  |  |
| Max HR | `121 bpm` |  |  |  |
| Average cadence | `69 rpm` |  |  |  |
| Max cadence | `94 rpm` |  |  |  |
| Actual TSS | `28.3` |  |  |  |

### Strava
- Import status: `PASS`

| Metric | Expected | Strava | Delta | PASS/FAIL |
|---|---|---|---|---|
| Duration | `27:27` |  |  |  |
| Distance | `6070 m` |  |  |  |
| Total calories | `121 kcal` |  |  |  |
| Average power | `71 W` |  |  |  |
| Max power | `124 W` |  |  |  |
| Average HR | `110 bpm` |  |  |  |
| Max HR | `121 bpm` |  |  |  |
| Average cadence | `69 rpm` |  |  |  |
| Max cadence | `94 rpm` |  |  |  |
| Actual TSS | `28.3` |  |  |  |

## Follow-up
- Cross-platform baseline import is now PASS in all three targets (GoldenCheetah, Intervals.icu, Strava).
- If later exports show out-of-tolerance mismatches, create a defect entry and prioritize FIT event/timer semantics refinement.

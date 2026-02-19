# Manual Validation Template: FIT Export Compatibility

## Purpose
Use this checklist to validate one exported `.fit` file across target platforms:
- GoldenCheetah
- Intervals.icu
- Strava

The goal is to verify that exported data is both accepted and interpreted consistently.

## Test Run Metadata
- Date:
- Tester:
- App branch / commit:
- Device model:
- Android version:
- Workout duration in app summary:
- Exported file name:

## Source of Truth (ErgometerApp Summary)
Fill these values from the app `SUMMARY` screen before importing the `.fit`:

| Metric | Value in App Summary |
|---|---|
| Duration | |
| Distance (m) | |
| Total calories (kcal) | |
| Average power (W) | |
| Max power (W) | |
| Average cadence (rpm) | |
| Max cadence (rpm) | |
| Average HR (bpm) | |
| Max HR (bpm) | |
| Actual TSS | |

## Acceptance Tolerances
Use these tolerances for PASS/FAIL decisions:
- Duration: exact match or +/- 1 second
- Distance: exact match or +/- 1 meter
- Calories: exact match or +/- 1 kcal
- Power/Cadence/HR averages and maxima: exact match or +/- 1 unit
- TSS: exact match or +/- 0.1

If a platform rounds differently but remains within tolerance, mark as PASS with note.

## Platform Results

### 1) GoldenCheetah
- Import result: `PASS / FAIL`
- File accepted without errors: `YES / NO`
- Activity type recognized as indoor cycling: `YES / NO`

| Metric | App Summary | GoldenCheetah | Delta | PASS/FAIL |
|---|---|---|---|---|
| Duration | | | | |
| Distance | | | | |
| Calories | | | | |
| Avg Power | | | | |
| Max Power | | | | |
| Avg Cadence | | | | |
| Max Cadence | | | | |
| Avg HR | | | | |
| Max HR | | | | |
| TSS | | | | |

Notes:

### 2) Intervals.icu
- Import result: `PASS / FAIL`
- File accepted without errors: `YES / NO`
- Activity appears on timeline/history: `YES / NO`

| Metric | App Summary | Intervals.icu | Delta | PASS/FAIL |
|---|---|---|---|---|
| Duration | | | | |
| Distance | | | | |
| Calories | | | | |
| Avg Power | | | | |
| Max Power | | | | |
| Avg Cadence | | | | |
| Max Cadence | | | | |
| Avg HR | | | | |
| Max HR | | | | |
| TSS | | | | |

Notes:

### 3) Strava
- Import result: `PASS / FAIL`
- File accepted without errors: `YES / NO`
- Activity appears on timeline/history: `YES / NO`

| Metric | App Summary | Strava | Delta | PASS/FAIL |
|---|---|---|---|---|
| Duration | | | | |
| Distance | | | | |
| Calories | | | | |
| Avg Power | | | | |
| Max Power | | | | |
| Avg Cadence | | | | |
| Max Cadence | | | | |
| Avg HR | | | | |
| Max HR | | | | |
| TSS | | | | |

Notes:

## Timeline/Chart Quality Check
Use this section to verify that the file contains multiple `record` samples (not a single-point export).

For each platform, check:
- Power chart has visible timeline shape (not flat/empty due to single sample)
- Cadence chart has timeline data (if cadence was present during session)
- HR chart has timeline data (if HR was connected)

| Platform | Power timeline | Cadence timeline | HR timeline | PASS/FAIL | Notes |
|---|---|---|---|---|---|
| GoldenCheetah | YES/NO | YES/NO | YES/NO | | |
| Intervals.icu | YES/NO | YES/NO | YES/NO | | |
| Strava | YES/NO | YES/NO | YES/NO | | |

## Overall Verdict
- GoldenCheetah: `PASS / FAIL`
- Intervals.icu: `PASS / FAIL`
- Strava: `PASS / FAIL`
- Final overall: `PASS / FAIL`

## Defects / Follow-up
| ID | Severity | Platform | Description | Suggested Fix |
|---|---|---|---|---|
|  |  |  |  |  |


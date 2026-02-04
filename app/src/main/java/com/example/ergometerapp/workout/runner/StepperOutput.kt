package com.example.ergometerapp.workout.runner

/**
 * Output of a tick: targets and completion status.
 *
 * Edge cases:
 * - `targetPowerWatts` can be null for free ride or when power attributes are missing.
 * - `done` is true once all steps are completed; targets may be null in that state.
 */
data class StepperOutput(
    val targetPowerWatts: Int?,
    val targetCadence: Int?,
    val done: Boolean,
    val label: String,
)

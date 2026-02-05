package com.example.ergometerapp.workout

/**
 * Parsed representation of a Zwift `.zwo` workout file.
 */
data class WorkoutFile(
    val name: String?,
    val description: String?,
    val author: String?,
    val tags: List<String>,
    val steps: List<Step>,
)

/**
 * Steps preserve the file's units; numeric fields are nullable when attributes are missing.
 */
sealed class Step {
    data class Warmup(
        val durationSec: Int?,
        val powerLow: Double?,
        val powerHigh: Double?,
        val cadence: Int?,
    ) : Step()

    data class Cooldown(
        val durationSec: Int?,
        val powerLow: Double?,
        val powerHigh: Double?,
        val cadence: Int?,
    ) : Step()

    data class SteadyState(
        val durationSec: Int?,
        val power: Double?,
        val cadence: Int?,
    ) : Step()

    data class Ramp(
        val durationSec: Int?,
        val powerLow: Double?,
        val powerHigh: Double?,
        val cadence: Int?,
    ) : Step()

    data class IntervalsT(
        val onDurationSec: Int?,
        val offDurationSec: Int?,
        val onPower: Double?,
        val offPower: Double?,
        val repeat: Int?,
        val cadence: Int?,
    ) : Step()

    data class FreeRide(
        val durationSec: Int?,
        val cadence: Int?,
    ) : Step()

    data class Unknown(
        val tagName: String,
        val attributes: Map<String, String>,
    ) : Step()
}


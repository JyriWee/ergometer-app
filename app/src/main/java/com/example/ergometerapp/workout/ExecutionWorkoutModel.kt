package com.example.ergometerapp.workout

/**
 * Non-nullable workout representation used by execution.
 * The mapper normalizes nullable metadata from parser output to empty strings.
 */
data class ExecutionWorkout(
    val name: String,
    val description: String,
    val author: String,
    val tags: List<String>,
    val segments: List<ExecutionSegment>,
    val totalDurationSec: Int,
)

/**
 * Deterministic execution segment mapped from a single source step.
 */
sealed class ExecutionSegment {
    abstract val sourceStepIndex: Int
    abstract val durationSec: Int
    abstract val cadence: CadenceTarget

    data class Steady(
        override val sourceStepIndex: Int,
        override val durationSec: Int,
        val targetWatts: Int,
        override val cadence: CadenceTarget,
    ) : ExecutionSegment()

    data class Ramp(
        override val sourceStepIndex: Int,
        override val durationSec: Int,
        val startWatts: Int,
        val endWatts: Int,
        override val cadence: CadenceTarget,
    ) : ExecutionSegment()
}

/**
 * Cadence behavior for an execution segment.
 */
sealed class CadenceTarget {
    object AnyCadence : CadenceTarget()

    data class FixedCadence(
        val rpm: Int,
    ) : CadenceTarget()
}

/**
 * Mapping outcome for conversion from parser model to execution model.
 */
sealed class MappingResult {
    data class Success(
        val workout: ExecutionWorkout,
    ) : MappingResult()

    data class Failure(
        val errors: List<MappingError>,
    ) : MappingResult()
}

/**
 * Structured mapping error that can reference a specific source step.
 */
data class MappingError(
    val code: MappingErrorCode,
    val message: String,
    val stepIndex: Int? = null,
    val stepType: String? = null,
)

/**
 * Stable error codes for mapper failure handling.
 */
enum class MappingErrorCode {
    INVALID_FTP,
    UNSUPPORTED_STEP,
    MISSING_REPEAT,
    INVALID_REPEAT,
    MISSING_DURATION,
    INVALID_DURATION,
    MISSING_POWER,
    MISSING_POWER_LOW,
    MISSING_POWER_HIGH,
    INVALID_POWER,
    WATTS_OVERFLOW,
    TOTAL_DURATION_OVERFLOW,
    NO_SUPPORTED_STEPS,
}

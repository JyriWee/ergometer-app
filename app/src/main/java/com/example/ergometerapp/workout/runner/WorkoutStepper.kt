package com.example.ergometerapp.workout.runner

import com.example.ergometerapp.workout.ExecutionWorkout
import com.example.ergometerapp.workout.ExecutionSegment
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import kotlin.math.roundToInt

/**
 * Minimal, deterministic stepper for ZWO workouts.
 *
 * Invariants and edge cases:
 * - Advancement only uses deltas from successive `tick()` calls and stops while paused.
 * - Steps with missing required durations are skipped immediately for this minimal implementation.
 * - Unknown steps are treated as zero-duration to avoid blocking on unsupported formats.
 */
class WorkoutStepper private constructor(
    private val workout: WorkoutFile?,
    private val ftpWatts: Int?,
    private val executionWorkout: ExecutionWorkout?,
) {
    init {
        require((workout != null && ftpWatts != null) xor (executionWorkout != null))
    }

    constructor(
        workout: WorkoutFile,
        ftpWatts: Int,
    ) : this(
        workout = workout,
        ftpWatts = ftpWatts,
        executionWorkout = null,
    )

    constructor(executionWorkout: ExecutionWorkout) : this(
        workout = null,
        ftpWatts = null,
        executionWorkout = executionWorkout,
    )

    companion object {
        /**
         * Explicit entry point for the native execution model path.
         */
        fun fromExecutionWorkout(workout: ExecutionWorkout): WorkoutStepper {
            return WorkoutStepper(executionWorkout = workout)
        }
    }

    private var state = StepperState(
        stepIndex = 0,
        stepElapsedMs = 0L,
        intervalRep = 0,
        inOn = true,
        paused = true,
    )
    private var lastUptimeMs: Long? = null
    private var stopped = true
    private var executionCurrentSegmentIndex = 0
    private var executionSecondsIntoSegment = 0
    private var executionCurrentTargetWatts: Int? = null
    private var executionDone = true
    private var executionRemainderMs = 0L

    fun start() {
        state = StepperState(
            stepIndex = 0,
            stepElapsedMs = 0L,
            intervalRep = 0,
            inOn = true,
            paused = false,
        )
        lastUptimeMs = null
        stopped = false
        if (executionWorkout != null) {
            executionCurrentSegmentIndex = 0
            executionSecondsIntoSegment = 0
            executionRemainderMs = 0L
            executionDone = executionWorkout.segments.isEmpty()
            normalizeExecutionPosition()
            updateExecutionTargetWatts()
            syncStateFromExecution()
        }
    }

    fun pause() {
        if (!stopped) {
            state = state.copy(paused = true)
        }
    }

    fun resume() {
        if (!stopped) {
            state = state.copy(paused = false)
            lastUptimeMs = null
        }
    }

    fun stop() {
        stopped = true
        if (executionWorkout != null) {
            executionCurrentSegmentIndex = executionWorkout.segments.size
            executionSecondsIntoSegment = 0
            executionCurrentTargetWatts = null
            executionDone = true
            executionRemainderMs = 0L
        }
        state = state.copy(
            stepIndex = totalStepCount(),
            stepElapsedMs = 0L,
            intervalRep = 0,
            inOn = true,
            paused = true,
        )
        lastUptimeMs = null
    }

    fun getState(): StepperState = state

    fun restore(state: StepperState) {
        this.state = state
        stopped = state.stepIndex >= totalStepCount()
        lastUptimeMs = null
        if (executionWorkout != null) {
            executionCurrentSegmentIndex = state.stepIndex.coerceAtLeast(0)
            executionSecondsIntoSegment = (state.stepElapsedMs / 1000L).toInt().coerceAtLeast(0)
            executionRemainderMs = state.stepElapsedMs.rem(1000L).coerceAtLeast(0L)
            executionDone = executionCurrentSegmentIndex >= executionWorkout.segments.size
            normalizeExecutionPosition()
            updateExecutionTargetWatts()
            syncStateFromExecution()
        }
    }

    fun tick(nowUptimeMs: Long): StepperOutput {
        if (executionWorkout != null) {
            return tickExecution(nowUptimeMs)
        }
        val workout = requireLegacyWorkout()

        if (stopped) {
            return StepperOutput(
                targetPowerWatts = null,
                targetCadence = null,
                done = true,
                label = "Done",
            )
        }

        if (state.paused) {
            return currentOutput(workout = workout, done = false)
        }

        val last = lastUptimeMs
        lastUptimeMs = nowUptimeMs
        if (last == null) {
            return currentOutput(workout = workout, done = false)
        }

        var remainingDeltaMs = (nowUptimeMs - last).coerceAtLeast(0L)
        while (remainingDeltaMs > 0L) {
            if (state.stepIndex >= workout.steps.size) {
                stopped = true
                return StepperOutput(
                    targetPowerWatts = null,
                    targetCadence = null,
                    done = true,
                    label = "Done",
                )
            }

            val step = workout.steps[state.stepIndex]
            val durationMs = stepDurationMs(step, state)
            if (durationMs == null || durationMs == 0L) {
                advanceStepOrInterval(workout = workout)
                continue
            }

            val stepRemaining = durationMs - state.stepElapsedMs
            if (remainingDeltaMs < stepRemaining) {
                state = state.copy(stepElapsedMs = state.stepElapsedMs + remainingDeltaMs)
                remainingDeltaMs = 0L
            } else {
                state = state.copy(stepElapsedMs = durationMs)
                remainingDeltaMs -= stepRemaining
                advanceStepOrInterval(workout = workout)
            }
        }

        return currentOutput(workout = workout, done = state.stepIndex >= workout.steps.size)
    }

    private fun currentOutput(workout: WorkoutFile, done: Boolean): StepperOutput {
        if (done || state.stepIndex >= workout.steps.size) {
            return StepperOutput(
                targetPowerWatts = null,
                targetCadence = null,
                done = true,
                label = "Done",
            )
        }
        val step = workout.steps[state.stepIndex]
        val label = when (step) {
            is Step.Warmup -> "Warmup"
            is Step.Cooldown -> "Cooldown"
            is Step.SteadyState -> "Steady"
            is Step.Ramp -> "Ramp"
            is Step.IntervalsT -> "Intervals"
            is Step.FreeRide -> "Free Ride"
            is Step.Unknown -> step.tagName
        }
        return StepperOutput(
            targetPowerWatts = targetPowerWatts(step, state),
            targetCadence = targetCadence(step),
            done = false,
            label = label,
        )
    }

    private fun targetCadence(step: Step): Int? = when (step) {
        is Step.Warmup -> step.cadence
        is Step.Cooldown -> step.cadence
        is Step.SteadyState -> step.cadence
        is Step.Ramp -> step.cadence
        is Step.IntervalsT -> step.cadence
        is Step.FreeRide -> step.cadence
        is Step.Unknown -> null
    }

    private fun targetPowerWatts(step: Step, state: StepperState): Int? {
        return when (step) {
            is Step.Warmup -> interpolatedWatts(
                step.powerLow,
                step.powerHigh,
                step.durationSec,
                state.stepElapsedMs
            )

            is Step.Cooldown -> {
                val descendingRange = cooldownDescendingRange(step.powerLow, step.powerHigh)
                if (descendingRange == null) {
                    null
                } else {
                    interpolatedWatts(
                        descendingRange.first,
                        descendingRange.second,
                        step.durationSec,
                        state.stepElapsedMs
                    )
                }
            }

            is Step.Ramp -> interpolatedWatts(
                step.powerLow,
                step.powerHigh,
                step.durationSec,
                state.stepElapsedMs
            )

            is Step.SteadyState -> ratioToWatts(step.power)
            is Step.IntervalsT -> {
                if (state.inOn) ratioToWatts(step.onPower) else ratioToWatts(step.offPower)
            }

            is Step.FreeRide -> null
            is Step.Unknown -> null
        }
    }

    private fun interpolatedWatts(
        low: Double?,
        high: Double?,
        durationSec: Int?,
        elapsedMs: Long,
    ): Int? {
        if (low == null || high == null || durationSec == null) return null
        val durationMs = durationSec.toLong() * 1000L
        if (durationMs <= 0L) return null
        val t = (elapsedMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
        val ratio = low + (high - low) * t
        return ratioToWatts(ratio)
    }

    /**
     * Cooldown is always descending, regardless of source field ordering.
     */
    private fun cooldownDescendingRange(
        powerLow: Double?,
        powerHigh: Double?,
    ): Pair<Double, Double>? {
        if (powerLow == null || powerHigh == null) return null
        return maxOf(powerLow, powerHigh) to minOf(powerLow, powerHigh)
    }

    private fun ratioToWatts(ratio: Double?): Int? {
        if (ratio == null) return null
        val ftpWatts = requireLegacyFtpWatts()
        return (ratio * ftpWatts).roundToInt()
    }

    private fun stepDurationMs(step: Step, state: StepperState): Long? {
        return when (step) {
            is Step.Warmup -> step.durationSec?.toLong()?.times(1000L)
            is Step.Cooldown -> step.durationSec?.toLong()?.times(1000L)
            is Step.SteadyState -> step.durationSec?.toLong()?.times(1000L)
            is Step.Ramp -> step.durationSec?.toLong()?.times(1000L)
            is Step.FreeRide -> step.durationSec?.toLong()?.times(1000L)
            is Step.IntervalsT -> {
                val onMs = step.onDurationSec?.toLong()?.times(1000L)
                val offMs = step.offDurationSec?.toLong()?.times(1000L)
                if (onMs == null || offMs == null) return null
                if (state.inOn) onMs else offMs
            }

            is Step.Unknown -> null
        }
    }

    private fun advanceStepOrInterval(workout: WorkoutFile) {
        val step = workout.steps.getOrNull(state.stepIndex)
        if (step is Step.IntervalsT) {
            val repeat = step.repeat ?: 0
            if (repeat <= 0) {
                advanceStep()
                return
            }
            if (state.inOn) {
                state = state.copy(stepElapsedMs = 0L, inOn = false)
                return
            }
            val nextRep = state.intervalRep + 1
            if (nextRep >= repeat) {
                advanceStep()
            } else {
                state = state.copy(stepElapsedMs = 0L, inOn = true, intervalRep = nextRep)
            }
        } else {
            advanceStep()
        }
    }

    private fun advanceStep() {
        state = state.copy(
            stepIndex = state.stepIndex + 1,
            stepElapsedMs = 0L,
            intervalRep = 0,
            inOn = true,
        )
    }

    private fun totalStepCount(): Int {
        val workout = workout
        if (workout != null) {
            return workout.steps.size
        }
        val executionWorkout = executionWorkout
        if (executionWorkout != null) {
            return executionWorkout.segments.size
        }
        return 0
    }

    private fun requireLegacyWorkout(): WorkoutFile {
        return workout
            ?: throw UnsupportedOperationException(
                "Legacy WorkoutFile path is not available in execution mode.",
            )
    }

    private fun requireLegacyFtpWatts(): Int {
        return ftpWatts
            ?: throw UnsupportedOperationException(
                "Legacy FTP path is not available in execution mode.",
            )
    }

    private fun tickExecution(nowUptimeMs: Long): StepperOutput {
        if (stopped) {
            return StepperOutput(
                targetPowerWatts = null,
                targetCadence = null,
                done = true,
                label = "Done",
            )
        }

        if (state.paused) {
            return currentExecutionOutput()
        }

        val last = lastUptimeMs
        lastUptimeMs = nowUptimeMs
        if (last == null) {
            return currentExecutionOutput()
        }

        executionRemainderMs += (nowUptimeMs - last).coerceAtLeast(0L)
        while (executionRemainderMs >= 1000L && !executionDone) {
            executionRemainderMs -= 1000L
            executionSecondsIntoSegment += 1
            normalizeExecutionPosition()
            updateExecutionTargetWatts()
        }

        syncStateFromExecution()
        if (executionDone) {
            stopped = true
            return StepperOutput(
                targetPowerWatts = null,
                targetCadence = null,
                done = true,
                label = "Done",
            )
        }

        return currentExecutionOutput()
    }

    private fun currentExecutionOutput(): StepperOutput {
        if (executionDone) {
            return StepperOutput(
                targetPowerWatts = null,
                targetCadence = null,
                done = true,
                label = "Done",
            )
        }

        val segment = requireExecutionWorkout().segments[executionCurrentSegmentIndex]
        return StepperOutput(
            targetPowerWatts = executionCurrentTargetWatts,
            targetCadence = null,
            done = false,
            label = executionLabel(segment),
        )
    }

    private fun executionLabel(segment: ExecutionSegment): String {
        return when (segment) {
            is ExecutionSegment.Steady -> "Steady"
            is ExecutionSegment.Ramp -> "Ramp"
        }
    }

    private fun syncStateFromExecution() {
        state = state.copy(
            stepIndex = executionCurrentSegmentIndex,
            stepElapsedMs = executionSecondsIntoSegment.toLong() * 1000L + executionRemainderMs,
            intervalRep = 0,
            inOn = true,
        )
    }

    private fun normalizeExecutionPosition() {
        val segments = requireExecutionWorkout().segments
        while (executionCurrentSegmentIndex < segments.size) {
            val segment = segments[executionCurrentSegmentIndex]
            if (segment.durationSec <= 0 || executionSecondsIntoSegment >= segment.durationSec) {
                executionCurrentSegmentIndex += 1
                executionSecondsIntoSegment = 0
                continue
            }
            break
        }
        executionDone = executionCurrentSegmentIndex >= segments.size
        if (executionDone) {
            executionCurrentTargetWatts = null
        }
    }

    private fun updateExecutionTargetWatts() {
        if (executionDone) {
            executionCurrentTargetWatts = null
            return
        }

        val segment = requireExecutionWorkout().segments[executionCurrentSegmentIndex]
        executionCurrentTargetWatts = when (segment) {
            is ExecutionSegment.Steady -> segment.targetWatts
            is ExecutionSegment.Ramp -> {
                val t = executionSecondsIntoSegment.toDouble() / segment.durationSec.toDouble()
                (segment.startWatts + (segment.endWatts - segment.startWatts) * t).roundToInt()
            }
        }
    }

    private fun requireExecutionWorkout(): ExecutionWorkout {
        return executionWorkout
            ?: throw UnsupportedOperationException(
                "ExecutionWorkout path is not available in legacy mode.",
            )
    }
}

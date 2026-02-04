package com.example.ergometerapp.workout.runner

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
class WorkoutStepper(
    private val workout: WorkoutFile,
    private val ftpWatts: Int,
) {
    private var state = StepperState(
        stepIndex = 0,
        stepElapsedMs = 0L,
        intervalRep = 0,
        inOn = true,
        paused = true,
    )
    private var lastUptimeMs: Long? = null
    private var stopped = true

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
        state = state.copy(
            stepIndex = workout.steps.size,
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
        stopped = state.stepIndex >= workout.steps.size
        lastUptimeMs = null
    }

    fun tick(nowUptimeMs: Long): StepperOutput {
        if (stopped) {
            return StepperOutput(
                targetPowerWatts = null,
                targetCadence = null,
                done = true,
                label = "Done",
            )
        }

        if (state.paused) {
            return currentOutput(done = false)
        }

        val last = lastUptimeMs
        lastUptimeMs = nowUptimeMs
        if (last == null) {
            return currentOutput(done = false)
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
                advanceStepOrInterval()
                continue
            }

            val stepRemaining = durationMs - state.stepElapsedMs
            if (remainingDeltaMs < stepRemaining) {
                state = state.copy(stepElapsedMs = state.stepElapsedMs + remainingDeltaMs)
                remainingDeltaMs = 0L
            } else {
                state = state.copy(stepElapsedMs = durationMs)
                remainingDeltaMs -= stepRemaining
                advanceStepOrInterval()
            }
        }

        return currentOutput(done = state.stepIndex >= workout.steps.size)
    }

    private fun currentOutput(done: Boolean): StepperOutput {
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
            is Step.Warmup -> interpolatedWatts(step.powerLow, step.powerHigh, step.durationSec, state.stepElapsedMs)
            is Step.Cooldown -> interpolatedWatts(step.powerLow, step.powerHigh, step.durationSec, state.stepElapsedMs)
            is Step.Ramp -> interpolatedWatts(step.powerLow, step.powerHigh, step.durationSec, state.stepElapsedMs)
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

    private fun ratioToWatts(ratio: Double?): Int? {
        if (ratio == null) return null
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

    private fun advanceStepOrInterval() {
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
}

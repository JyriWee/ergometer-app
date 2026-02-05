package com.example.ergometerapp.workout.runner

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Drives a [WorkoutStepper] on a fixed tick and forwards target power updates.
 *
 * Invariants and edge cases:
 * - Uses [SystemClock.uptimeMillis] deltas for deterministic progression across pauses.
 * - Applies target power only when non-null to avoid overriding free-ride steps.
 * - Stops ticking once the workout is done and clears targets via [applyTarget].
 */
class WorkoutRunner(
    private val stepper: WorkoutStepper,
    private val applyTarget: (Int?) -> Unit,
    private val tickIntervalMs: Long = 250L,
    private val nowUptimeMs: () -> Long = { SystemClock.uptimeMillis() },
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private var running = false
    private var paused = true

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val output = stepper.tick(nowUptimeMs())
            output.targetPowerWatts?.let { applyTarget(it) }
            if (output.done) {
                stopInternal(clearTarget = true, stopStepper = false)
                return
            }
            handler.postDelayed(this, tickIntervalMs)
        }
    }

    fun start() {
        stopInternal(clearTarget = false, stopStepper = false)
        stepper.start()
        running = true
        paused = false
        handler.post(tickRunnable)
    }

    fun pause() {
        if (!running || paused) return
        stepper.pause()
        paused = true
        handler.removeCallbacks(tickRunnable)
    }

    fun resume() {
        if (!running) {
            running = true
        }
        if (!paused) return
        stepper.resume()
        paused = false
        handler.post(tickRunnable)
    }

    fun stop() {
        stopInternal(clearTarget = true, stopStepper = true)
    }

    fun restore(state: StepperState) {
        stepper.restore(state)
        running = false
        paused = state.paused
        handler.removeCallbacks(tickRunnable)
    }

    fun getState(): StepperState = stepper.getState()

    private fun stopInternal(clearTarget: Boolean, stopStepper: Boolean) {
        handler.removeCallbacks(tickRunnable)
        running = false
        paused = true
        if (stopStepper) {
            stepper.stop()
        }
        if (clearTarget) {
            applyTarget(null)
        }
    }
}

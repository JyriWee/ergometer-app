package com.example.ergometerapp.session

import com.example.ergometerapp.SessionState
import com.example.ergometerapp.ftms.IndoorBikeData

enum class SessionPhase {
    IDLE,
    RUNNING,
    STOPPED
}

/**
 * Coordinates a single workout session lifecycle.
 *
 * This class is intentionally UI-agnostic and BLE-agnostic; it only consumes
 * parsed FTMS/HR signals and produces a [SessionSummary].
 */
class SessionManager(
    private val context: android.content.Context,
    private val onStateUpdated: (SessionState) -> Unit
) {
    var lastSummary: SessionSummary? = null
        private set
    private val powerSamples = mutableListOf<Int>()
    private val cadenceSamples = mutableListOf<Int>()
    private val heartRateSamples = mutableListOf<Int>()
    private var latestBikeData: IndoorBikeData? = null
    private var latestHeartRate: Int? = null
    private var sessionStartMillis: Long? = null

    private var durationAtStopSec: Int? = null

    private var lastDistanceMeters: Int? = null

    private var sessionPhase: SessionPhase = SessionPhase.IDLE

    /**
     * Current session phase used by UI for state rendering and controls.
     */
    fun getPhase(): SessionPhase = sessionPhase

    fun updateBikeData(bikeData: IndoorBikeData) {
        latestBikeData = bikeData

        if (sessionPhase == SessionPhase.RUNNING) {

            // Power values at or below zero are treated as invalid samples.
            latestBikeData?.instantaneousPowerW
                ?.takeIf { it > 0 }
                ?.let { powerSamples.add(it) }

            // Distance is cumulative; store the latest for the summary.
            latestBikeData?.totalDistanceMeters
                ?.let { lastDistanceMeters = it }

            // Prefer external HR strap if present; fall back to bike HR otherwise.
            // This avoids mixing two sensors with different latencies.
            if (latestHeartRate == null) {
                latestBikeData?.heartRateBpm
                    ?.takeIf { it in 30..220 }
                    ?.let { heartRateSamples.add(it) }
            }
            latestBikeData?.instantaneousCadenceRpm?.let { cadenceSamples.add(it.toInt()) }
        }

        emitState()
    }

    /**
     * Updates heart rate from an external sensor.
     *
     * When present, it supersedes any HR embedded in FTMS bike data to keep
     * the data source consistent for statistics.
     */
    fun updateHeartRate(hr: Int?) {
        latestHeartRate = hr

        if (sessionPhase == SessionPhase.RUNNING) {
            hr?.let { heartRateSamples.add(it) }
        }

        emitState()
    }


    private fun emitState() {

        val durationSec =
            when (sessionPhase) {
                SessionPhase.RUNNING -> {
                    val start = sessionStartMillis
                    if (start != null)
                        ((System.currentTimeMillis() - start) / 1000).toInt()
                    else 0
                }
                SessionPhase.STOPPED -> durationAtStopSec ?: 0
                else -> 0
            }

        // Effective HR is exposed for UI even when only bike HR is available.
        val effectiveHr =
            latestHeartRate
                ?: latestBikeData?.heartRateBpm
                    ?.takeIf { it in 30..220 }

        val state = SessionState(
            bike = latestBikeData,
            heartRateBpm = latestHeartRate,
            effectiveHeartRateBpm = effectiveHr,
            timestampMillis = System.currentTimeMillis(),
            durationSeconds = durationSec
        )

        onStateUpdated(state)
    }

    /**
     * Starts a new session and clears any prior aggregates.
     */
    fun startSession() {
        sessionPhase = SessionPhase.RUNNING
        sessionStartMillis = System.currentTimeMillis()

        powerSamples.clear()
        cadenceSamples.clear()
        heartRateSamples.clear()
        lastDistanceMeters = null
        lastSummary = null
        latestBikeData = null
        latestHeartRate = null
        lastDistanceMeters = null
        durationAtStopSec = null
        emitState()
    }

    /**
     * Stops the session and finalizes summary statistics.
     *
     * Uses simple averages over collected samples; the caller is responsible for
     * choosing sampling frequency elsewhere.
     */
    fun stopSession() {
        if (sessionPhase != SessionPhase.RUNNING) return

        val start = sessionStartMillis ?: return

        val durationSec = ((System.currentTimeMillis() - start) / 1000).toInt()

        durationAtStopSec = durationSec

        val avgPower =
            powerSamples.takeIf { it.isNotEmpty() }?.average()?.toInt()

        val maxPower =
            powerSamples.maxOrNull()

        val avgCadence =
            cadenceSamples.takeIf { it.isNotEmpty() }?.average()?.toInt()

        val maxCadence =
            cadenceSamples.maxOrNull()

        val avgHeartRate =
            heartRateSamples.takeIf { it.isNotEmpty() }?.average()?.toInt()

        val maxHeartRate =
            heartRateSamples.maxOrNull()

        val summary = SessionSummary(
            durationSeconds = durationSec,
            avgPower = avgPower,
            maxPower = maxPower,
            avgCadence = avgCadence,
            maxCadence = maxCadence,
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            distanceMeters = lastDistanceMeters
        )

        lastSummary = summary
        sessionPhase = SessionPhase.STOPPED

        lastSummary?.let {
            SessionStorage.save(context, it)
        }
    }
}

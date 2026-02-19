package com.example.ergometerapp.session

import android.util.Log
import com.example.ergometerapp.SessionState
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.export.SessionExportSnapshot
import java.util.concurrent.Executor
import java.util.concurrent.Executors

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
    private val onStateUpdated: (SessionState) -> Unit,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val persistSummary: (android.content.Context, SessionSummary) -> Unit = { ctx, summary ->
        SessionStorage.save(ctx, summary)
    },
    private val summaryPersistenceExecutor: Executor = SUMMARY_PERSISTENCE_EXECUTOR,
) {
    companion object {
        private val SUMMARY_PERSISTENCE_EXECUTOR: Executor by lazy {
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "SessionSummaryPersist").apply { isDaemon = true }
            }
        }
    }

    var lastSummary: SessionSummary? = null
        private set
    private val powerStats = IntSampleAccumulator()
    private val cadenceStats = IntSampleAccumulator()
    private val heartRateStats = IntSampleAccumulator()
    private var actualTssAccumulator: ActualTssAccumulator? = null
    private var latestBikeData: IndoorBikeData? = null
    private var latestHeartRate: Int? = null
    private var sessionStartMillis: Long? = null
    private val timelineSamples = mutableListOf<SessionSample>()
    private var lastSampleSecond: Long? = null

    private var durationAtStopSec: Int? = null

    private var lastDistanceMeters: Int? = null
    private var lastTotalEnergyKcal: Int? = null

    private var sessionPhase: SessionPhase = SessionPhase.IDLE
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Memory-stable running aggregate for integer telemetry samples.
     *
     * Averages use integer division to preserve existing summary behavior.
     */
    private class IntSampleAccumulator {
        private var count: Long = 0
        private var sum: Long = 0
        private var max: Int? = null

        fun add(sample: Int) {
            count += 1
            sum += sample.toLong()
            val currentMax = max
            max = if (currentMax == null) sample else maxOf(currentMax, sample)
        }

        fun reset() {
            count = 0
            sum = 0
            max = null
        }

        fun averageOrNull(): Int? {
            if (count == 0L) return null
            return (sum / count).toInt()
        }

        fun maxOrNull(): Int? = max
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * Current session phase used by UI for state rendering and controls.
     */
    fun getPhase(): SessionPhase = sessionPhase

    fun updateBikeData(bikeData: IndoorBikeData) {
        runOnMainThread {
            latestBikeData = bikeData
            if (sessionPhase == SessionPhase.RUNNING) {
                // Only valid FTMS frames with power values contribute to summary stats.
                latestBikeData
                    ?.takeIf { it.valid && it.instantaneousPowerW != null }
                    ?.instantaneousPowerW
                    ?.let { powerWatts ->
                        powerStats.add(powerWatts)
                        actualTssAccumulator?.recordPower(
                            powerWatts = powerWatts,
                            timestampMillis = nowMillis(),
                        )
                    }

                // Distance is cumulative; store the latest for the summary.
                latestBikeData?.totalDistanceMeters
                    ?.let { lastDistanceMeters = it }
                latestBikeData?.totalEnergyKcal
                    ?.let { lastTotalEnergyKcal = it }

                // Prefer external HR strap if present; fall back to bike HR otherwise.
                // This avoids mixing two sensors with different latencies.
                if (latestHeartRate == null) {
                    latestBikeData?.heartRateBpm
                        ?.takeIf { it in 30..220 }
                        ?.let { heartRateStats.add(it) }
                }
                latestBikeData?.instantaneousCadenceRpm?.let { cadenceStats.add(it.toInt()) }
                recordSampleIfDue(nowMillis())
            }
            emitState()
        }
    }

    /**
     * Updates heart rate from an external sensor.
     *
     * When present, it supersedes any HR embedded in FTMS bike data to keep
     * the data source consistent for statistics.
     */
    fun updateHeartRate(hr: Int?) {
        runOnMainThread {
            latestHeartRate = hr
            if (sessionPhase == SessionPhase.RUNNING) {
                hr?.takeIf { it in 30..220 }?.let { heartRateStats.add(it) }
                recordSampleIfDue(nowMillis())
            }

            emitState()
        }
    }


    private fun emitState() {

        val durationSec =
            when (sessionPhase) {
                SessionPhase.RUNNING -> {
                    val start = sessionStartMillis
                    if (start != null)
                        ((nowMillis() - start) / 1000).toInt()
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
            timestampMillis = nowMillis(),
            durationSeconds = durationSec
        )

        onStateUpdated(state)
    }

    /**
     * Starts a new session and clears any prior aggregates.
     */
    fun startSession(ftpWatts: Int) {
        runOnMainThread {
            sessionPhase = SessionPhase.RUNNING
            sessionStartMillis = nowMillis()

            powerStats.reset()
            cadenceStats.reset()
            heartRateStats.reset()
            actualTssAccumulator = ActualTssAccumulator(ftpWatts = ftpWatts)
            lastDistanceMeters = null
            lastTotalEnergyKcal = null
            lastSummary = null
            latestBikeData = null
            latestHeartRate = null
            durationAtStopSec = null
            timelineSamples.clear()
            lastSampleSecond = null
            emitState()
        }
    }

    /**
     * Stops the session and finalizes summary statistics.
     *
     * Uses simple averages over collected samples; the caller is responsible for
     * choosing sampling frequency elsewhere.
     */
    fun stopSession() {
        runOnMainThread {
            if (sessionPhase != SessionPhase.RUNNING) return@runOnMainThread

            val start = sessionStartMillis ?: return@runOnMainThread
            val stopTimestampMillis = nowMillis()
            val durationSec = ((stopTimestampMillis - start) / 1000).toInt()
            recordSampleIfDue(stopTimestampMillis)

            durationAtStopSec = durationSec

            val avgPower = powerStats.averageOrNull()
            val maxPower = powerStats.maxOrNull()
            val avgCadence = cadenceStats.averageOrNull()
            val maxCadence = cadenceStats.maxOrNull()
            val avgHeartRate = heartRateStats.averageOrNull()
            val maxHeartRate = heartRateStats.maxOrNull()
            val actualTss = actualTssAccumulator?.calculateTss(
                durationSeconds = durationSec,
                stopTimestampMillis = stopTimestampMillis,
            )

            val summary = SessionSummary(
                startTimestampMillis = start,
                stopTimestampMillis = stopTimestampMillis,
                durationSeconds = durationSec,
                actualTss = actualTss,
                avgPower = avgPower,
                maxPower = maxPower,
                avgCadence = avgCadence,
                maxCadence = maxCadence,
                avgHeartRate = avgHeartRate,
                maxHeartRate = maxHeartRate,
                distanceMeters = lastDistanceMeters,
                totalEnergyKcal = lastTotalEnergyKcal,
            )

            lastSummary = summary
            sessionPhase = SessionPhase.STOPPED
            emitState()

            queueSummaryPersistence(summary)
        }
    }

    /**
     * Returns a stable snapshot of collected timeline samples for export use.
     */
    fun exportTimelineSnapshot(): List<SessionSample> {
        return timelineSamples.toList()
    }

    /**
     * Builds a single immutable export snapshot after session completion.
     */
    fun buildExportSnapshot(): SessionExportSnapshot? {
        val summary = lastSummary ?: return null
        return SessionExportSnapshot(
            summary = summary,
            timeline = exportTimelineSnapshot(),
        )
    }

    /**
     * Persists summary off the main thread so stop-flow UI transitions remain smooth.
     *
     * Invariant: summary publication to UI ([lastSummary], phase, emitted state) is
     * completed before persistence is queued.
     */
    private fun queueSummaryPersistence(summary: SessionSummary) {
        summaryPersistenceExecutor.execute {
            try {
                persistSummary(context, summary)
            } catch (t: Throwable) {
                Log.w("SESSION", "Summary persistence failed: ${t.message}")
            }
        }
    }

    private fun recordSampleIfDue(timestampMillis: Long) {
        if (sessionPhase != SessionPhase.RUNNING) return
        val sampleSecond = timestampMillis / 1000L
        val previousSecond = lastSampleSecond
        if (previousSecond != null && sampleSecond <= previousSecond) return

        val bikeData = latestBikeData
        val resolvedHeartRate = latestHeartRate ?: bikeData?.heartRateBpm?.takeIf { it in 30..220 }
        timelineSamples += SessionSample(
            timestampMillis = timestampMillis,
            powerWatts = bikeData?.instantaneousPowerW,
            cadenceRpm = bikeData?.instantaneousCadenceRpm?.toInt(),
            heartRateBpm = resolvedHeartRate,
            distanceMeters = lastDistanceMeters,
            totalEnergyKcal = lastTotalEnergyKcal,
        )
        lastSampleSecond = sampleSecond
    }
}

package com.example.ergometerapp.session

import android.content.ContextWrapper
import com.example.ergometerapp.SessionState
import com.example.ergometerapp.ftms.IndoorBikeData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class SessionManagerEdgeCaseTest {

    @Test
    fun shortSessionWithNoDataProducesNullTelemetryMetrics() {
        val clock = MutableClock(startMillis = 1_000L)
        val manager = createManager(clock)

        manager.startSession(ftpWatts = 250)
        clock.advanceBy(500L)
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(0, summary!!.durationSeconds)
        assertNull(summary.actualTss)
        assertNull(summary.avgPower)
        assertNull(summary.maxPower)
        assertNull(summary.avgCadence)
        assertNull(summary.maxCadence)
        assertNull(summary.avgHeartRate)
        assertNull(summary.maxHeartRate)
    }

    @Test
    fun noTelemetryForLongerSessionKeepsActualTssNull() {
        val clock = MutableClock(startMillis = 10_000L)
        val manager = createManager(clock)

        manager.startSession(ftpWatts = 250)
        clock.advanceBy(10_000L)
        manager.stopSession()

        val summary = manager.lastSummary
        assertNotNull(summary)
        assertEquals(10, summary!!.durationSeconds)
        assertNull(summary.actualTss)
        assertNull(summary.avgPower)
    }

    @Test
    fun sparseTelemetryGapsReduceActualTssComparedToDenseSampling() {
        val denseSummary = runConstantPowerSession(sampleIntervalSec = 1, durationSec = 120)
        val sparseSummary = runConstantPowerSession(sampleIntervalSec = 5, durationSec = 120)
        val denseTss = requireNotNull(denseSummary.actualTss)
        val sparseTss = requireNotNull(sparseSummary.actualTss)

        assertTrue(sparseTss < denseTss)
        assertTrue(sparseTss > 0.0)
        assertEquals(250, denseSummary.avgPower)
        assertEquals(250, sparseSummary.avgPower)
    }

    @Test
    fun stopSessionQueuesPersistenceWithoutBlockingSummaryPublication() {
        val clock = MutableClock(startMillis = 1_000L)
        val queuedTasks = mutableListOf<Runnable>()
        val persistedSummaries = mutableListOf<SessionSummary>()
        val manager = SessionManager(
            context = ContextWrapper(null),
            onStateUpdated = { _: SessionState -> },
            nowMillis = { clock.currentMillis },
            persistSummary = { _, summary -> persistedSummaries += summary },
            summaryPersistenceExecutor = Executor { task -> queuedTasks += task },
        )

        manager.startSession(ftpWatts = 250)
        clock.advanceBy(5_000L)
        manager.stopSession()

        val publishedSummary = requireNotNull(manager.lastSummary)
        assertEquals(5, publishedSummary.durationSeconds)
        assertTrue(persistedSummaries.isEmpty())
        assertEquals(1, queuedTasks.size)

        queuedTasks.single().run()
        assertEquals(listOf(publishedSummary), persistedSummaries)
    }

    private fun runConstantPowerSession(sampleIntervalSec: Int, durationSec: Int): SessionSummary {
        val clock = MutableClock(startMillis = 100_000L)
        val manager = createManager(clock)
        manager.startSession(ftpWatts = 250)

        var second = 0
        while (second <= durationSec) {
            clock.currentMillis = 100_000L + second * 1000L
            manager.updateBikeData(
                bikeData(
                    instantaneousPowerWatts = 250,
                )
            )
            second += sampleIntervalSec
        }

        clock.currentMillis = 100_000L + durationSec * 1000L
        manager.stopSession()
        return requireNotNull(manager.lastSummary)
    }

    private fun createManager(clock: MutableClock): SessionManager {
        return SessionManager(
            context = ContextWrapper(null),
            onStateUpdated = { _: SessionState -> },
            nowMillis = { clock.currentMillis },
            persistSummary = { _, _ -> },
        )
    }

    private fun bikeData(
        instantaneousPowerWatts: Int,
    ): IndoorBikeData {
        return IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = null,
            averageCadenceRpm = null,
            totalDistanceMeters = null,
            resistanceLevel = null,
            instantaneousPowerW = instantaneousPowerWatts,
            averagePowerW = null,
            totalEnergyKcal = null,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = null,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null,
        )
    }

    private class MutableClock(
        startMillis: Long,
    ) {
        var currentMillis: Long = startMillis

        fun advanceBy(deltaMillis: Long) {
            currentMillis += deltaMillis
        }
    }
}

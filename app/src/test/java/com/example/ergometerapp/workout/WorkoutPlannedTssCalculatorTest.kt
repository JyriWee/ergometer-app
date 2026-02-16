package com.example.ergometerapp.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutPlannedTssCalculatorTest {
    @Test
    fun calculateReturnsExpectedTssForOneHourSteadyAtFtp() {
        val workout = workoutWithSteps(
            Step.SteadyState(
                durationSec = 3600,
                power = 1.0,
                cadence = null,
            ),
        )

        val plannedTss = WorkoutPlannedTssCalculator.calculate(workout, ftpWatts = 250)

        assertNotNull(plannedTss)
        assertEquals(100.0, plannedTss!!, 0.0)
    }

    @Test
    fun calculateIntegratesRampIntensitySquared() {
        val workout = workoutWithSteps(
            Step.Ramp(
                durationSec = 3600,
                powerLow = 0.5,
                powerHigh = 1.0,
                cadence = null,
            ),
        )

        val plannedTss = WorkoutPlannedTssCalculator.calculate(workout, ftpWatts = 300)

        assertNotNull(plannedTss)
        assertEquals(58.3, plannedTss!!, 0.0)
    }

    @Test
    fun calculateHandlesExpandedIntervalSegments() {
        val workout = workoutWithSteps(
            Step.IntervalsT(
                onDurationSec = 900,
                offDurationSec = 900,
                onPower = 1.0,
                offPower = 0.5,
                repeat = 2,
                cadence = null,
            ),
        )

        val plannedTss = WorkoutPlannedTssCalculator.calculate(workout, ftpWatts = 200)

        assertNotNull(plannedTss)
        assertEquals(62.5, plannedTss!!, 0.0)
    }

    @Test
    fun calculateReturnsNullWhenStrictMappingFails() {
        val workout = workoutWithSteps(
            Step.FreeRide(
                durationSec = 600,
                cadence = null,
            ),
        )

        val plannedTss = WorkoutPlannedTssCalculator.calculate(workout, ftpWatts = 220)

        assertNull(plannedTss)
    }

    private fun workoutWithSteps(vararg steps: Step): WorkoutFile {
        return WorkoutFile(
            name = "Planned TSS test",
            description = null,
            author = null,
            tags = emptyList(),
            steps = steps.toList(),
        )
    }
}

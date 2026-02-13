package com.example.ergometerapp.ui.components

import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutProfileChartMappingTest {

    @Test
    fun cooldownRampDirectionIsPreserved() {
        val workout = workoutWithSteps(
            Step.Cooldown(
                durationSec = 120,
                powerLow = 0.60,
                powerHigh = 0.40,
                cadence = 85,
            ),
        )

        val segments = buildWorkoutProfileSegments(workout)

        assertEquals(1, segments.size)
        val segment = segments.single()
        assertEquals(SegmentKind.RAMP, segment.kind)
        assertEquals(0, segment.startSec)
        assertEquals(120, segment.durationSec)
        assertEquals(0.60, segment.startPowerRelFtp ?: 0.0, 0.0001)
        assertEquals(0.40, segment.endPowerRelFtp ?: 0.0, 0.0001)
    }

    @Test
    fun intervalsExpandToAlternatingOnOffSegmentsWithCorrectTimeline() {
        val workout = workoutWithSteps(
            Step.IntervalsT(
                onDurationSec = 30,
                offDurationSec = 15,
                onPower = 1.20,
                offPower = 0.50,
                repeat = 3,
                cadence = 95,
            ),
        )

        val segments = buildWorkoutProfileSegments(workout)

        assertEquals(6, segments.size)
        assertEquals(135, segments.sumOf { it.durationSec })
        assertEquals(listOf(0, 30, 45, 75, 90, 120), segments.map { it.startSec })
        assertEquals(
            listOf(1.20, 0.50, 1.20, 0.50, 1.20, 0.50),
            segments.map { it.startPowerRelFtp ?: 0.0 },
        )
        assertTrue(segments.all { it.kind == SegmentKind.STEADY })
    }

    @Test
    fun invalidStepsAreSkippedWithoutAdvancingTimeline() {
        val workout = workoutWithSteps(
            Step.SteadyState(
                durationSec = null,
                power = 0.8,
                cadence = null,
            ),
            Step.Ramp(
                durationSec = 60,
                powerLow = null,
                powerHigh = 1.1,
                cadence = null,
            ),
            Step.FreeRide(
                durationSec = -10,
                cadence = null,
            ),
            Step.Unknown(
                tagName = "CustomStep",
                attributes = mapOf("Duration" to "30"),
            ),
            Step.SteadyState(
                durationSec = 45,
                power = 0.7,
                cadence = 90,
            ),
        )

        val segments = buildWorkoutProfileSegments(workout)

        assertEquals(1, segments.size)
        val only = segments.single()
        assertEquals(0, only.startSec)
        assertEquals(45, only.durationSec)
        assertEquals(SegmentKind.STEADY, only.kind)
        assertEquals(0.7, only.startPowerRelFtp ?: 0.0, 0.0001)
    }

    private fun workoutWithSteps(vararg steps: Step): WorkoutFile {
        return WorkoutFile(
            name = "Test workout",
            description = null,
            author = null,
            tags = emptyList(),
            steps = steps.toList(),
        )
    }
}

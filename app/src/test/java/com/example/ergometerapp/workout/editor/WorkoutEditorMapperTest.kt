package com.example.ergometerapp.workout.editor

import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEditorMapperTest {
    @Test
    fun validateReportsErrorsForInvalidInputs() {
        val draft = WorkoutEditorDraft(
            name = "Invalid",
            description = "",
            author = "",
            steps = listOf(
                WorkoutEditorStepDraft.Steady(
                    id = 1L,
                    durationSecText = "0",
                    powerText = "2.5",
                ),
            ),
        )

        val errors = WorkoutEditorMapper.validate(draft)

        assertTrue(errors.any { it.contains("duration", ignoreCase = true) })
        assertTrue(errors.any { it.contains("power", ignoreCase = true) })
    }

    @Test
    fun buildWorkoutMapsSupportedSteps() {
        val draft = WorkoutEditorDraft(
            name = "Build test",
            description = "desc",
            author = "me",
            steps = listOf(
                WorkoutEditorStepDraft.Steady(
                    id = 1L,
                    durationSecText = "300",
                    powerText = "75",
                ),
                WorkoutEditorStepDraft.Ramp(
                    id = 2L,
                    durationSecText = "120",
                    startPowerText = "60",
                    endPowerText = "90",
                ),
                WorkoutEditorStepDraft.Intervals(
                    id = 3L,
                    repeatText = "4",
                    onDurationSecText = "30",
                    offDurationSecText = "30",
                    onPowerText = "100",
                    offPowerText = "60",
                ),
            ),
        )

        val result = WorkoutEditorMapper.buildWorkout(draft)

        check(result is WorkoutEditorBuildResult.Success)
        assertEquals(3, result.workout.steps.size)
        assertTrue(result.workout.steps[0] is Step.SteadyState)
        assertTrue(result.workout.steps[1] is Step.Ramp)
        assertTrue(result.workout.steps[2] is Step.IntervalsT)
        assertEquals(0.75, (result.workout.steps[0] as Step.SteadyState).power ?: 0.0, 0.0001)
        assertEquals(0.60, (result.workout.steps[1] as Step.Ramp).powerLow ?: 0.0, 0.0001)
        assertEquals(0.90, (result.workout.steps[1] as Step.Ramp).powerHigh ?: 0.0, 0.0001)
        assertEquals(1.00, (result.workout.steps[2] as Step.IntervalsT).onPower ?: 0.0, 0.0001)
        assertEquals(0.60, (result.workout.steps[2] as Step.IntervalsT).offPower ?: 0.0, 0.0001)
    }

    @Test
    fun fromWorkoutConvertsSupportedAndCountsSkippedSteps() {
        val workout = WorkoutFile(
            name = "Imported",
            description = "desc",
            author = "author",
            tags = emptyList(),
            steps = listOf(
                Step.Warmup(durationSec = 120, powerLow = 0.5, powerHigh = 0.7, cadence = null),
                Step.SteadyState(durationSec = 300, power = 0.8, cadence = null),
                Step.FreeRide(durationSec = 60, cadence = null),
                Step.Unknown(tagName = "Custom", attributes = mapOf("a" to "b")),
            ),
        )

        val result = WorkoutEditorMapper.fromWorkout(workout)

        assertEquals(2, result.draft.steps.size)
        assertEquals(2, result.skippedStepCount)
        val warmup = result.draft.steps[0] as WorkoutEditorStepDraft.Ramp
        val steady = result.draft.steps[1] as WorkoutEditorStepDraft.Steady
        assertEquals("50", warmup.startPowerText)
        assertEquals("70", warmup.endPowerText)
        assertEquals("80", steady.powerText)
    }

    @Test
    fun fromWorkoutLeavesDraftEmptyWhenNoSupportedStepsExist() {
        val workout = WorkoutFile(
            name = "Unsupported only",
            description = "",
            author = "",
            tags = emptyList(),
            steps = listOf(
                Step.FreeRide(durationSec = 120, cadence = null),
                Step.Unknown(tagName = "Custom", attributes = emptyMap()),
            ),
        )

        val result = WorkoutEditorMapper.fromWorkout(workout)

        assertEquals(0, result.draft.steps.size)
        assertEquals(2, result.skippedStepCount)
    }

    @Test
    fun zwoSerializerWritesSupportedStepTags() {
        val workout = WorkoutFile(
            name = "Export",
            description = "desc",
            author = "author",
            tags = emptyList(),
            steps = listOf(
                Step.SteadyState(durationSec = 300, power = 0.75, cadence = null),
                Step.Ramp(durationSec = 120, powerLow = 0.6, powerHigh = 0.9, cadence = null),
                Step.IntervalsT(
                    onDurationSec = 30,
                    offDurationSec = 30,
                    onPower = 1.0,
                    offPower = 0.6,
                    repeat = 4,
                    cadence = null,
                ),
            ),
        )

        val xml = WorkoutZwoSerializer.serialize(workout)

        assertTrue(xml.contains("<SteadyState"))
        assertTrue(xml.contains("<Ramp"))
        assertTrue(xml.contains("<IntervalsT"))
    }
}

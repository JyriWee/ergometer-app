package com.example.ergometerapp.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionWorkoutMapperTest {
    @Test
    fun steadyStateMapsSuccessfully() {
        val workout = workoutWithSteps(
            Step.SteadyState(
                durationSec = 60,
                power = 0.75,
                cadence = 90,
            ),
        )

        val success = requireSuccess(ExecutionWorkoutMapper.map(workout, ftp = 200))
        val segment = success.workout.segments.single()

        assertEquals(60, success.workout.totalDurationSec)
        assertTrue(segment is ExecutionSegment.Steady)

        val steady = segment as ExecutionSegment.Steady
        assertEquals(0, steady.sourceStepIndex)
        assertEquals(60, steady.durationSec)
        assertEquals(150, steady.targetWatts)
        assertEquals(CadenceTarget.FixedCadence(90), steady.cadence)
    }

    @Test
    fun rampMapsSuccessfully() {
        val workout = workoutWithSteps(
            Step.Ramp(
                durationSec = 120,
                powerLow = 0.5,
                powerHigh = 1.0,
                cadence = null,
            ),
        )

        val success = requireSuccess(ExecutionWorkoutMapper.map(workout, ftp = 250))
        val segment = success.workout.segments.single()

        assertEquals(120, success.workout.totalDurationSec)
        assertTrue(segment is ExecutionSegment.Ramp)

        val ramp = segment as ExecutionSegment.Ramp
        assertEquals(0, ramp.sourceStepIndex)
        assertEquals(120, ramp.durationSec)
        assertEquals(125, ramp.startWatts)
        assertEquals(250, ramp.endWatts)
        assertEquals(CadenceTarget.AnyCadence, ramp.cadence)
    }

    @Test
    fun ftpLessThanOrEqualToZeroFailsWithInvalidFtp() {
        val workout = workoutWithSteps(
            Step.SteadyState(
                durationSec = 60,
                power = 0.8,
                cadence = 85,
            ),
        )

        listOf(0, -1).forEach { ftp ->
            val failure = requireFailure(ExecutionWorkoutMapper.map(workout, ftp = ftp))
            assertContainsErrorCode(failure, MappingErrorCode.INVALID_FTP)
        }
    }

    @Test
    fun unsupportedStepFailsWithUnsupportedStep() {
        val workout = workoutWithSteps(
            Step.Warmup(
                durationSec = 30,
                powerLow = 0.5,
                powerHigh = 0.7,
                cadence = null,
            ),
        )

        val failure = requireFailure(ExecutionWorkoutMapper.map(workout, ftp = 200))
        assertContainsErrorCode(failure, MappingErrorCode.UNSUPPORTED_STEP)
    }

    @Test
    fun missingDurationFailsWithMissingDuration() {
        val workout = workoutWithSteps(
            Step.SteadyState(
                durationSec = null,
                power = 0.6,
                cadence = null,
            ),
        )

        val failure = requireFailure(ExecutionWorkoutMapper.map(workout, ftp = 200))
        assertContainsErrorCode(failure, MappingErrorCode.MISSING_DURATION)
    }

    @Test
    fun invalidPowerFailsWithInvalidPower() {
        listOf(Double.NaN, -0.1).forEach { power ->
            val workout = workoutWithSteps(
                Step.SteadyState(
                    durationSec = 45,
                    power = power,
                    cadence = null,
                ),
            )

            val failure = requireFailure(ExecutionWorkoutMapper.map(workout, ftp = 200))
            assertContainsErrorCode(failure, MappingErrorCode.INVALID_POWER)
        }
    }

    @Test
    fun emptyStepsFailsWithNoSupportedSteps() {
        val failure = requireFailure(ExecutionWorkoutMapper.map(workoutWithSteps(), ftp = 200))
        assertContainsErrorCode(failure, MappingErrorCode.NO_SUPPORTED_STEPS)
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

    private fun requireSuccess(result: MappingResult): MappingResult.Success {
        return result as? MappingResult.Success
            ?: throw AssertionError("Expected MappingResult.Success, got $result")
    }

    private fun requireFailure(result: MappingResult): MappingResult.Failure {
        return result as? MappingResult.Failure
            ?: throw AssertionError("Expected MappingResult.Failure, got $result")
    }

    private fun assertContainsErrorCode(failure: MappingResult.Failure, code: MappingErrorCode) {
        assertTrue("Expected error code $code, got ${failure.errors.map { it.code }}", failure.errors.any { it.code == code })
    }
}

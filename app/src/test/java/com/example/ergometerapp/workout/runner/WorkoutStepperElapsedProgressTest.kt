package com.example.ergometerapp.workout.runner

import com.example.ergometerapp.workout.CadenceTarget
import com.example.ergometerapp.workout.ExecutionSegment
import com.example.ergometerapp.workout.ExecutionWorkout
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutStepperElapsedProgressTest {
    @Test
    fun intervalsElapsedProgressMatchesExpandedTimeline() {
        val workout = WorkoutFile(
            name = "Intervals",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(
                Step.IntervalsT(
                    onDurationSec = 2,
                    offDurationSec = 3,
                    onPower = 0.8,
                    offPower = 0.5,
                    repeat = 2,
                    cadence = null,
                ),
            ),
        )
        val stepper = WorkoutStepper(workout = workout, ftpWatts = 200)

        val observed = runSecondBySecondElapsed(stepper)

        assertEquals((0..9).toList(), observed.activeElapsedSec)
        assertEquals(10, observed.doneElapsedSec)
    }

    @Test
    fun executionElapsedProgressMatchesSegmentDurations() {
        val workout = ExecutionWorkout(
            name = "Execution",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 2,
                    targetWatts = 180,
                    cadence = CadenceTarget.AnyCadence,
                ),
                ExecutionSegment.Ramp(
                    sourceStepIndex = 1,
                    durationSec = 2,
                    startWatts = 180,
                    endWatts = 220,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 4,
        )
        val stepper = WorkoutStepper.fromExecutionWorkout(workout)

        val observed = runSecondBySecondElapsed(stepper)

        assertEquals(listOf(0, 1, 2, 3), observed.activeElapsedSec)
        assertEquals(4, observed.doneElapsedSec)
    }

    private fun runSecondBySecondElapsed(stepper: WorkoutStepper): ObservedElapsed {
        val activeElapsed = mutableListOf<Int>()
        var doneElapsed = -1
        var nowMs = 0L
        val guardMaxSeconds = 120

        stepper.start()
        repeat(guardMaxSeconds) {
            val output = stepper.tick(nowMs)
            if (output.done) {
                doneElapsed = output.elapsedSec
                return ObservedElapsed(
                    activeElapsedSec = activeElapsed.toList(),
                    doneElapsedSec = doneElapsed,
                )
            }
            activeElapsed += output.elapsedSec
            nowMs += 1000L
        }

        throw AssertionError("Elapsed test exceeded guard limit without completion.")
    }

    private data class ObservedElapsed(
        val activeElapsedSec: List<Int>,
        val doneElapsedSec: Int,
    )
}

package com.example.ergometerapp.workout

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutImportServiceTest {
    private val service = WorkoutImportService()

    @Test
    fun importsZwoByFileExtension() {
        val xml = """
            <workout_file>
              <name>Test</name>
              <workout>
                <SteadyState Duration="60" Power="0.75" Cadence="90"/>
              </workout>
            </workout_file>
        """.trimIndent()

        val result = service.importFromText("test.zwo", xml)

        val success = result as? WorkoutImportResult.Success
            ?: throw AssertionError("Expected success, got $result")
        assertEquals(WorkoutImportFormat.ZWO_XML, success.format)
        assertEquals(1, success.workoutFile.steps.size)
        assertTrue(success.workoutFile.steps.first() is Step.SteadyState)
    }

    @Test
    fun importsXmlByContentSniffingWithoutExtension() {
        val xml = """
            <workout_file>
              <workout>
                <Ramp Duration="120" PowerLow="0.5" PowerHigh="0.8" />
              </workout>
            </workout_file>
        """.trimIndent()

        val result = service.importFromText("workout_data", xml)

        val success = result as? WorkoutImportResult.Success
            ?: throw AssertionError("Expected success, got $result")
        assertEquals(WorkoutImportFormat.ZWO_XML, success.format)
        assertEquals(1, success.workoutFile.steps.size)
        assertTrue(success.workoutFile.steps.first() is Step.Ramp)
    }

    @Test
    fun returnsParseFailedForMalformedXml() {
        val malformed = "<workout_file><workout><SteadyState Duration=\"60\" Power=\"0.8\"></workout_file>"

        val result = service.importFromText("broken.zwo", malformed)

        val failure = result as? WorkoutImportResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        assertEquals(WorkoutImportErrorCode.PARSE_FAILED, failure.error.code)
        assertEquals(WorkoutImportFormat.ZWO_XML, failure.error.detectedFormat)
        assertTrue(
            "Expected parser diagnostics for malformed XML",
            !failure.error.technicalDetails.isNullOrBlank(),
        )
        assertTrue(
            "Expected malformed XML reason in technical details",
            failure.error.technicalDetails!!.contains("Malformed ZWO XML"),
        )
    }

    @Test
    fun returnsEmptyWorkoutForXmlWithoutSteps() {
        val emptyWorkoutXml = """
            <workout_file>
              <name>Empty</name>
              <workout />
            </workout_file>
        """.trimIndent()

        val result = service.importFromText("empty.xml", emptyWorkoutXml)

        val failure = result as? WorkoutImportResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        assertEquals(WorkoutImportErrorCode.EMPTY_WORKOUT, failure.error.code)
        assertEquals(WorkoutImportFormat.ZWO_XML, failure.error.detectedFormat)
    }

    @Test
    fun returnsUnsupportedForMyWhooshJsonUntilAdapterExists() {
        val myWhooshJson = """{"name":"MyWhoosh Workout","steps":[{"type":"steady"}]}"""

        val result = service.importFromText("mywhoosh.json", myWhooshJson)

        val failure = result as? WorkoutImportResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        assertEquals(WorkoutImportErrorCode.UNSUPPORTED_FORMAT, failure.error.code)
        assertEquals(WorkoutImportFormat.MYWHOOSH_JSON, failure.error.detectedFormat)
    }

    @Test
    fun returnsUnsupportedForUnknownFormat() {
        val result = service.importFromText("notes.txt", "plain text")

        val failure = result as? WorkoutImportResult.Failure
            ?: throw AssertionError("Expected failure, got $result")
        assertEquals(WorkoutImportErrorCode.UNSUPPORTED_FORMAT, failure.error.code)
        assertEquals(WorkoutImportFormat.UNKNOWN, failure.error.detectedFormat)
    }

    @Test
    fun importsProjectRootWorkoutTestXml() {
        val xml = readWorkoutTestXml()

        val result = service.importFromText("Workout_Test.xml", xml)

        val success = result as? WorkoutImportResult.Success
            ?: throw AssertionError("Expected success, got $result")
        assertEquals(WorkoutImportFormat.ZWO_XML, success.format)
        assertEquals(4, success.workoutFile.steps.size)
        assertTrue(success.workoutFile.steps[0] is Step.Warmup)
        assertTrue(success.workoutFile.steps[1] is Step.IntervalsT)
        assertTrue(success.workoutFile.steps[2] is Step.SteadyState)
        assertTrue(success.workoutFile.steps[3] is Step.Cooldown)
    }

    private fun readWorkoutTestXml(): String {
        val candidates = listOf(
            File("Workout_Test.xml"),
            File("../Workout_Test.xml"),
            File("../../Workout_Test.xml"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: throw AssertionError(
                "Fixture Workout_Test.xml not found. Looked in: " +
                    candidates.joinToString { it.path },
            )
        return file.readText()
    }
}

package com.example.ergometerapp.workout

/**
 * Canonical workout import formats recognized by the app.
 */
enum class WorkoutImportFormat {
    ZWO_XML,
    MYWHOOSH_JSON,
    UNKNOWN,
}

/**
 * Stable error codes for workout import failures.
 */
enum class WorkoutImportErrorCode {
    EMPTY_CONTENT,
    UNSUPPORTED_FORMAT,
    PARSE_FAILED,
    EMPTY_WORKOUT,
}

/**
 * Structured import error payload for UI and logging.
 */
data class WorkoutImportError(
    val code: WorkoutImportErrorCode,
    val message: String,
    val detectedFormat: WorkoutImportFormat,
    val technicalDetails: String? = null,
)

/**
 * Import operation result with either parsed workout data or a typed error.
 */
sealed class WorkoutImportResult {
    data class Success(
        val workoutFile: WorkoutFile,
        val format: WorkoutImportFormat,
    ) : WorkoutImportResult()

    data class Failure(
        val error: WorkoutImportError,
    ) : WorkoutImportResult()
}

/**
 * Imports workout files from text content with lightweight format detection.
 *
 * The service intentionally keeps detection simple and deterministic:
 * - `.zwo`/`.xml` are parsed through the existing ZWO XML parser.
 * - `.json` is recognized as MyWhoosh JSON and reported as not yet supported.
 * - Unknown content types return a typed unsupported-format failure.
 */
class WorkoutImportService {

    /**
     * Parses a workout definition from raw text.
     *
     * [sourceName] is optional but recommended, because filename extension helps
     * select the intended parser when content sniffing is ambiguous.
     */
    fun importFromText(sourceName: String?, content: String): WorkoutImportResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return WorkoutImportResult.Failure(
                WorkoutImportError(
                    code = WorkoutImportErrorCode.EMPTY_CONTENT,
                    message = "Workout file content is empty.",
                    detectedFormat = WorkoutImportFormat.UNKNOWN,
                ),
            )
        }

        val format = detectFormat(sourceName = sourceName, trimmedContent = trimmed)
        return when (format) {
            WorkoutImportFormat.ZWO_XML -> parseZwoXml(trimmed)
            WorkoutImportFormat.MYWHOOSH_JSON -> WorkoutImportResult.Failure(
                WorkoutImportError(
                    code = WorkoutImportErrorCode.UNSUPPORTED_FORMAT,
                    message = "MyWhoosh JSON import is not implemented yet.",
                    detectedFormat = WorkoutImportFormat.MYWHOOSH_JSON,
                ),
            )
            WorkoutImportFormat.UNKNOWN -> WorkoutImportResult.Failure(
                WorkoutImportError(
                    code = WorkoutImportErrorCode.UNSUPPORTED_FORMAT,
                    message = "Unsupported workout file format.",
                    detectedFormat = WorkoutImportFormat.UNKNOWN,
                ),
            )
        }
    }

    private fun parseZwoXml(content: String): WorkoutImportResult {
        return try {
            val workout = parseZwo(content)
            if (workout.steps.isEmpty()) {
                WorkoutImportResult.Failure(
                    WorkoutImportError(
                        code = WorkoutImportErrorCode.EMPTY_WORKOUT,
                        message = "Workout does not contain executable steps.",
                        detectedFormat = WorkoutImportFormat.ZWO_XML,
                    ),
                )
            } else {
                WorkoutImportResult.Success(
                    workoutFile = workout,
                    format = WorkoutImportFormat.ZWO_XML,
                )
            }
        } catch (e: Exception) {
            WorkoutImportResult.Failure(
                WorkoutImportError(
                    code = WorkoutImportErrorCode.PARSE_FAILED,
                    message = "Workout XML parsing failed.",
                    detectedFormat = WorkoutImportFormat.ZWO_XML,
                    technicalDetails = e.message?.takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    private fun detectFormat(sourceName: String?, trimmedContent: String): WorkoutImportFormat {
        val extension = sourceName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()

        if (extension == "zwo" || extension == "xml") {
            return WorkoutImportFormat.ZWO_XML
        }
        if (extension == "json") {
            return WorkoutImportFormat.MYWHOOSH_JSON
        }

        if (trimmedContent.startsWith("<")) {
            return WorkoutImportFormat.ZWO_XML
        }
        if (trimmedContent.startsWith("{") || trimmedContent.startsWith("[")) {
            return WorkoutImportFormat.MYWHOOSH_JSON
        }
        return WorkoutImportFormat.UNKNOWN
    }
}

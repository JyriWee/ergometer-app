package com.example.ergometerapp.workout.editor

import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import java.util.Locale

internal data class WorkoutEditorImportResult(
    val draft: WorkoutEditorDraft,
    val skippedStepCount: Int,
)

internal sealed class WorkoutEditorBuildResult {
    data class Success(val workout: WorkoutFile) : WorkoutEditorBuildResult()
    data class Failure(val errors: List<String>) : WorkoutEditorBuildResult()
}

/**
 * Converts between editable draft and strict workout model used by session flow.
 */
internal object WorkoutEditorMapper {
    private const val minPowerPercent = 30.0
    private const val maxPowerPercent = 200.0

    fun fromWorkout(workout: WorkoutFile): WorkoutEditorImportResult {
        var nextId = 1L
        var skipped = 0
        val steps = mutableListOf<WorkoutEditorStepDraft>()

        workout.steps.forEach { step ->
            val mapped = when (step) {
                is Step.SteadyState -> WorkoutEditorStepDraft.Steady(
                    id = nextId++,
                    durationSecText = step.durationSec?.toString().orEmpty(),
                    powerText = step.power?.toEditorPercent().orEmpty(),
                )
                is Step.Ramp -> WorkoutEditorStepDraft.Ramp(
                    id = nextId++,
                    durationSecText = step.durationSec?.toString().orEmpty(),
                    startPowerText = step.powerLow?.toEditorPercent().orEmpty(),
                    endPowerText = step.powerHigh?.toEditorPercent().orEmpty(),
                )
                is Step.Warmup -> WorkoutEditorStepDraft.Ramp(
                    id = nextId++,
                    durationSecText = step.durationSec?.toString().orEmpty(),
                    startPowerText = step.powerLow?.toEditorPercent().orEmpty(),
                    endPowerText = step.powerHigh?.toEditorPercent().orEmpty(),
                )
                is Step.Cooldown -> WorkoutEditorStepDraft.Ramp(
                    id = nextId++,
                    durationSecText = step.durationSec?.toString().orEmpty(),
                    startPowerText = step.powerLow?.toEditorPercent().orEmpty(),
                    endPowerText = step.powerHigh?.toEditorPercent().orEmpty(),
                )
                is Step.IntervalsT -> WorkoutEditorStepDraft.Intervals(
                    id = nextId++,
                    repeatText = step.repeat?.toString().orEmpty(),
                    onDurationSecText = step.onDurationSec?.toString().orEmpty(),
                    offDurationSecText = step.offDurationSec?.toString().orEmpty(),
                    onPowerText = step.onPower?.toEditorPercent().orEmpty(),
                    offPowerText = step.offPower?.toEditorPercent().orEmpty(),
                )
                is Step.FreeRide,
                is Step.Unknown,
                -> null
            }
            if (mapped == null) {
                skipped += 1
            } else {
                steps += mapped
            }
        }

        return WorkoutEditorImportResult(
            draft = WorkoutEditorDraft(
                name = workout.name.orEmpty(),
                description = workout.description.orEmpty(),
                author = workout.author.orEmpty(),
                steps = steps,
            ),
            skippedStepCount = skipped,
        )
    }

    fun validate(draft: WorkoutEditorDraft): List<String> {
        val errors = mutableListOf<String>()
        if (draft.steps.isEmpty()) {
            errors += "Add at least one step."
        }

        draft.steps.forEachIndexed { index, step ->
            val stepPrefix = "Step ${index + 1}"
            when (step) {
                is WorkoutEditorStepDraft.Steady -> {
                    validatePositiveInt(step.durationSecText, "$stepPrefix duration", errors)
                    validatePower(step.powerText, "$stepPrefix power", errors)
                }
                is WorkoutEditorStepDraft.Ramp -> {
                    validatePositiveInt(step.durationSecText, "$stepPrefix duration", errors)
                    validatePower(step.startPowerText, "$stepPrefix start power", errors)
                    validatePower(step.endPowerText, "$stepPrefix end power", errors)
                }
                is WorkoutEditorStepDraft.Intervals -> {
                    validatePositiveInt(step.repeatText, "$stepPrefix repeat", errors)
                    validatePositiveInt(step.onDurationSecText, "$stepPrefix ON duration", errors)
                    validatePositiveInt(step.offDurationSecText, "$stepPrefix OFF duration", errors)
                    validatePower(step.onPowerText, "$stepPrefix ON power", errors)
                    validatePower(step.offPowerText, "$stepPrefix OFF power", errors)
                }
            }
        }

        return errors
    }

    fun buildWorkout(draft: WorkoutEditorDraft): WorkoutEditorBuildResult {
        val errors = validate(draft)
        if (errors.isNotEmpty()) {
            return WorkoutEditorBuildResult.Failure(errors)
        }

        val mappedSteps = draft.steps.map { step ->
            when (step) {
                is WorkoutEditorStepDraft.Steady -> {
                    Step.SteadyState(
                        durationSec = step.durationSecText.toPositiveInt()!!,
                        power = step.powerText.toPowerFraction()!!,
                        cadence = null,
                    )
                }
                is WorkoutEditorStepDraft.Ramp -> {
                    Step.Ramp(
                        durationSec = step.durationSecText.toPositiveInt()!!,
                        powerLow = step.startPowerText.toPowerFraction()!!,
                        powerHigh = step.endPowerText.toPowerFraction()!!,
                        cadence = null,
                    )
                }
                is WorkoutEditorStepDraft.Intervals -> {
                    Step.IntervalsT(
                        onDurationSec = step.onDurationSecText.toPositiveInt()!!,
                        offDurationSec = step.offDurationSecText.toPositiveInt()!!,
                        onPower = step.onPowerText.toPowerFraction()!!,
                        offPower = step.offPowerText.toPowerFraction()!!,
                        repeat = step.repeatText.toPositiveInt()!!,
                        cadence = null,
                    )
                }
            }
        }

        return WorkoutEditorBuildResult.Success(
            WorkoutFile(
                name = draft.name.trim().takeIf { it.isNotEmpty() },
                description = draft.description.trim().takeIf { it.isNotEmpty() },
                author = draft.author.trim().takeIf { it.isNotEmpty() },
                tags = emptyList(),
                steps = mappedSteps,
            ),
        )
    }

    fun suggestedFileName(draft: WorkoutEditorDraft): String {
        val rawName = draft.name.trim().ifEmpty { "workout" }
        val normalized = rawName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .ifEmpty { "workout" }
        return "$normalized.zwo"
    }

    private fun validatePositiveInt(raw: String, field: String, errors: MutableList<String>) {
        if (raw.toPositiveInt() == null) {
            errors += "$field must be a positive integer."
        }
    }

    private fun validatePower(raw: String, field: String, errors: MutableList<String>) {
        val parsed = raw.toPowerPercent()
        if (parsed == null) {
            errors += "$field must be a number in ${minPowerPercent.toEditorNumber()}-${maxPowerPercent.toEditorNumber()}."
        }
    }

    private fun String.toPositiveInt(): Int? {
        val parsed = trim().toIntOrNull() ?: return null
        return parsed.takeIf { it > 0 }
    }

    private fun String.toPowerPercent(): Double? {
        val normalized = trim().replace(',', '.')
        val parsed = normalized.toDoubleOrNull() ?: return null
        if (parsed < minPowerPercent || parsed > maxPowerPercent) return null
        return parsed
    }

    private fun String.toPowerFraction(): Double? {
        return toPowerPercent()?.div(100.0)
    }

    private fun Double.toEditorPercent(): String {
        return (this * 100.0).toEditorNumber()
    }

    private fun Double.toEditorNumber(): String {
        return String.format(Locale.US, "%.3f", this)
            .trimEnd('0')
            .trimEnd('.')
    }
}

/**
 * Serializes editor-supported workout steps to a ZWO document.
 */
internal object WorkoutZwoSerializer {
    fun serialize(workout: WorkoutFile): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append("<workout_file>\n")
        workout.author?.let { builder.append("  <author>${escapeXml(it)}</author>\n") }
        workout.name?.let { builder.append("  <name>${escapeXml(it)}</name>\n") }
        workout.description?.let { builder.append("  <description>${escapeXml(it)}</description>\n") }
        builder.append("  <sportType>bike</sportType>\n")
        builder.append("  <durationType>time</durationType>\n")
        builder.append("  <workout>\n")
        workout.steps.forEach { step ->
            when (step) {
                is Step.SteadyState -> {
                    builder.append(
                        "    <SteadyState Duration=\"${step.durationSec ?: 0}\" Power=\"${formatPower(step.power)}\"/>\n"
                    )
                }
                is Step.Ramp -> {
                    builder.append(
                        "    <Ramp Duration=\"${step.durationSec ?: 0}\" PowerLow=\"${formatPower(step.powerLow)}\" PowerHigh=\"${formatPower(step.powerHigh)}\"/>\n"
                    )
                }
                is Step.IntervalsT -> {
                    builder.append(
                        "    <IntervalsT Repeat=\"${step.repeat ?: 0}\" OnDuration=\"${step.onDurationSec ?: 0}\" OffDuration=\"${step.offDurationSec ?: 0}\" OnPower=\"${formatPower(step.onPower)}\" OffPower=\"${formatPower(step.offPower)}\"/>\n"
                    )
                }
                is Step.Warmup,
                is Step.Cooldown,
                is Step.FreeRide,
                is Step.Unknown,
                -> {
                    // Editor only emits supported step types.
                }
            }
        }
        builder.append("  </workout>\n")
        builder.append("</workout_file>\n")
        return builder.toString()
    }

    private fun formatPower(value: Double?): String {
        val safe = value ?: 0.0
        return String.format(Locale.US, "%.3f", safe)
            .trimEnd('0')
            .trimEnd('.')
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

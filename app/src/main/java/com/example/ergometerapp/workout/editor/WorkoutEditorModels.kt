package com.example.ergometerapp.workout.editor

/**
 * Editable workout draft used by the in-app workout editor.
 *
 * Values are stored as raw text so users can type intermediate states while the
 * validator reports actionable issues.
 */
data class WorkoutEditorDraft(
    val name: String,
    val description: String,
    val author: String,
    val steps: List<WorkoutEditorStepDraft>,
) {
    companion object {
        /**
         * Returns a blank draft so users explicitly choose the first step type.
         */
        fun empty(): WorkoutEditorDraft {
            return WorkoutEditorDraft(
                name = "",
                description = "",
                author = "",
                steps = emptyList(),
            )
        }
    }
}

/**
 * Supported editable step variants for the MVP editor.
 */
sealed class WorkoutEditorStepDraft(
    open val id: Long,
) {
    data class Steady(
        override val id: Long,
        val durationSecText: String,
        val powerText: String,
    ) : WorkoutEditorStepDraft(id)

    data class Ramp(
        override val id: Long,
        val durationSecText: String,
        val startPowerText: String,
        val endPowerText: String,
    ) : WorkoutEditorStepDraft(id)

    data class Intervals(
        override val id: Long,
        val repeatText: String,
        val onDurationSecText: String,
        val offDurationSecText: String,
        val onPowerText: String,
        val offPowerText: String,
    ) : WorkoutEditorStepDraft(id)
}

enum class WorkoutEditorStepType {
    STEADY,
    RAMP,
    INTERVALS,
}

enum class WorkoutEditorStepField {
    DURATION_SEC,
    POWER,
    START_POWER,
    END_POWER,
    REPEAT,
    ON_DURATION_SEC,
    OFF_DURATION_SEC,
    ON_POWER,
    OFF_POWER,
}

/**
 * Editor actions routed through ViewModel so UI remains declarative.
 */
sealed class WorkoutEditorAction {
    data object OpenEditor : WorkoutEditorAction()
    data object BackToMenu : WorkoutEditorAction()
    data object NewDraft : WorkoutEditorAction()
    data object LoadSelectedWorkout : WorkoutEditorAction()
    data object ApplyToMenuSelection : WorkoutEditorAction()
    data object DismissSaveBeforeApplyPrompt : WorkoutEditorAction()
    data object ConfirmApplyWithoutSaving : WorkoutEditorAction()
    data object PrepareSaveAndApply : WorkoutEditorAction()

    data class SetName(val value: String) : WorkoutEditorAction()
    data class SetDescription(val value: String) : WorkoutEditorAction()
    data class SetAuthor(val value: String) : WorkoutEditorAction()

    data class AddStep(val type: WorkoutEditorStepType) : WorkoutEditorAction()
    data class DeleteStep(val stepId: Long) : WorkoutEditorAction()
    data class DuplicateStep(val stepId: Long) : WorkoutEditorAction()
    data class MoveStepUp(val stepId: Long) : WorkoutEditorAction()
    data class MoveStepDown(val stepId: Long) : WorkoutEditorAction()
    data class ChangeStepField(
        val stepId: Long,
        val field: WorkoutEditorStepField,
        val value: String,
    ) : WorkoutEditorAction()
}

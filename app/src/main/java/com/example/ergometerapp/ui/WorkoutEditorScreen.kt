package com.example.ergometerapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.R
import com.example.ergometerapp.ui.components.WorkoutProfileChart
import com.example.ergometerapp.workout.WorkoutExecutionStepCounter
import com.example.ergometerapp.workout.WorkoutPlannedTssCalculator
import com.example.ergometerapp.workout.editor.WorkoutEditorAction
import com.example.ergometerapp.workout.editor.WorkoutEditorBuildResult
import com.example.ergometerapp.workout.editor.WorkoutEditorDraft
import com.example.ergometerapp.workout.editor.WorkoutEditorMapper
import com.example.ergometerapp.workout.editor.WorkoutEditorStepDraft
import com.example.ergometerapp.workout.editor.WorkoutEditorStepField
import com.example.ergometerapp.workout.editor.WorkoutEditorStepType

/**
 * In-app editor for creating and adjusting structured workouts.
 */
@Composable
internal fun WorkoutEditorScreen(
    draft: WorkoutEditorDraft,
    validationErrors: List<String>,
    statusMessage: String?,
    statusIsError: Boolean,
    hasUnsavedChanges: Boolean,
    showSaveBeforeApplyPrompt: Boolean,
    ftpWatts: Int,
    onAction: (WorkoutEditorAction) -> Unit,
    onRequestSave: (String) -> Unit,
) {
    val previewWorkout = when (val buildResult = WorkoutEditorMapper.buildWorkout(draft)) {
        is WorkoutEditorBuildResult.Success -> buildResult.workout
        is WorkoutEditorBuildResult.Failure -> null
    }
    val stepCount = previewWorkout?.let {
        WorkoutExecutionStepCounter.count(workout = it, ftpWatts = ftpWatts)
    }
    val plannedTss = previewWorkout?.let {
        WorkoutPlannedTssCalculator.calculate(workout = it, ftpWatts = ftpWatts)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.workout_editor_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.workout_editor_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onAction(WorkoutEditorAction.BackToMenu) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.workout_editor_back_to_menu))
            }
            Button(
                onClick = { onAction(WorkoutEditorAction.NewDraft) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.workout_editor_new))
            }
            Button(
                onClick = { onAction(WorkoutEditorAction.LoadSelectedWorkout) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.workout_editor_load_selected))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onRequestSave(WorkoutEditorMapper.suggestedFileName(draft)) },
                enabled = validationErrors.isEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.workout_editor_save_zwo))
            }
            Button(
                onClick = { onAction(WorkoutEditorAction.ApplyToMenuSelection) },
                enabled = validationErrors.isEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.workout_editor_apply_to_menu))
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (statusIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        if (hasUnsavedChanges) {
            Text(
                text = stringResource(R.string.workout_editor_unsaved_changes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        EditorTextField(
            label = stringResource(R.string.workout_editor_name),
            value = draft.name,
            onValueChange = { onAction(WorkoutEditorAction.SetName(it)) },
            singleLine = true,
        )
        EditorTextField(
            label = stringResource(R.string.workout_editor_author),
            value = draft.author,
            onValueChange = { onAction(WorkoutEditorAction.SetAuthor(it)) },
            singleLine = true,
        )
        EditorTextField(
            label = stringResource(R.string.workout_editor_description),
            value = draft.description,
            onValueChange = { onAction(WorkoutEditorAction.SetDescription(it)) },
            singleLine = false,
        )

        Text(
            text = stringResource(R.string.workout_editor_steps),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        draft.steps.forEachIndexed { index, step ->
            WorkoutEditorStepCard(
                step = step,
                index = index,
                onAction = onAction,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onAction(WorkoutEditorAction.AddStep(WorkoutEditorStepType.STEADY)) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.workout_editor_add_steady))
            }
            Button(
                onClick = { onAction(WorkoutEditorAction.AddStep(WorkoutEditorStepType.RAMP)) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.workout_editor_add_ramp))
            }
            Button(
                onClick = { onAction(WorkoutEditorAction.AddStep(WorkoutEditorStepType.INTERVALS)) },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.workout_editor_add_intervals))
            }
        }

        if (validationErrors.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.workout_editor_validation_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    validationErrors.forEach { error ->
                        Text(
                            text = "• $error",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (previewWorkout != null) {
            Card {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.workout_editor_preview_steps,
                                stepCount ?: 0,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (plannedTss != null) {
                            Text(
                                text = stringResource(R.string.workout_editor_preview_tss, plannedTss),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    WorkoutProfileChart(
                        workout = previewWorkout,
                        ftpWatts = ftpWatts,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showSaveBeforeApplyPrompt) {
        AlertDialog(
            onDismissRequest = { onAction(WorkoutEditorAction.DismissSaveBeforeApplyPrompt) },
            title = { Text(stringResource(R.string.workout_editor_save_prompt_title)) },
            text = { Text(stringResource(R.string.workout_editor_save_prompt_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAction(WorkoutEditorAction.PrepareSaveAndApply)
                        onRequestSave(WorkoutEditorMapper.suggestedFileName(draft))
                    },
                ) {
                    Text(stringResource(R.string.workout_editor_save_prompt_save_and_apply))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(WorkoutEditorAction.ConfirmApplyWithoutSaving) },
                ) {
                    Text(stringResource(R.string.workout_editor_save_prompt_apply_without_save))
                }
            },
        )
    }
}

@Composable
private fun WorkoutEditorStepCard(
    step: WorkoutEditorStepDraft,
    index: Int,
    onAction: (WorkoutEditorAction) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = Color(0xFF111111),
        ),
        border = BorderStroke(width = 1.dp, color = Color(0xFFB8B8B8)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val stepTypeLabel = when (step) {
                is WorkoutEditorStepDraft.Steady -> stringResource(R.string.workout_editor_step_type_steady)
                is WorkoutEditorStepDraft.Ramp -> stringResource(R.string.workout_editor_step_type_ramp)
                is WorkoutEditorStepDraft.Intervals -> stringResource(R.string.workout_editor_step_type_intervals)
            }
            Text(
                text = stringResource(R.string.workout_editor_step_title, index + 1, stepTypeLabel),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onAction(WorkoutEditorAction.MoveStepUp(step.id)) },
                    modifier = Modifier.weight(1.45f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.workout_editor_step_up),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = { onAction(WorkoutEditorAction.MoveStepDown(step.id)) },
                    modifier = Modifier.weight(1.45f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.workout_editor_step_down),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = { onAction(WorkoutEditorAction.DuplicateStep(step.id)) },
                    modifier = Modifier.weight(0.9f),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Text(
                        text = stringResource(R.string.workout_editor_step_duplicate),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = { onAction(WorkoutEditorAction.DeleteStep(step.id)) },
                    modifier = Modifier.weight(0.9f),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Text(
                        text = stringResource(R.string.workout_editor_step_delete),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when (step) {
                is WorkoutEditorStepDraft.Steady -> {
                    StepNumberField(
                        label = stringResource(R.string.workout_editor_field_duration_sec),
                        value = step.durationSecText,
                        onValueChange = {
                            onAction(
                                WorkoutEditorAction.ChangeStepField(
                                    stepId = step.id,
                                    field = WorkoutEditorStepField.DURATION_SEC,
                                    value = it,
                                ),
                            )
                        },
                    )
                    StepDecimalField(
                        label = stringResource(R.string.workout_editor_field_power),
                        value = step.powerText,
                        onValueChange = {
                            onAction(
                                WorkoutEditorAction.ChangeStepField(
                                    stepId = step.id,
                                    field = WorkoutEditorStepField.POWER,
                                    value = it,
                                ),
                            )
                        },
                    )
                }
                is WorkoutEditorStepDraft.Ramp -> {
                    StepNumberField(
                        label = stringResource(R.string.workout_editor_field_duration_sec),
                        value = step.durationSecText,
                        onValueChange = {
                            onAction(
                                WorkoutEditorAction.ChangeStepField(
                                    stepId = step.id,
                                    field = WorkoutEditorStepField.DURATION_SEC,
                                    value = it,
                                ),
                            )
                        },
                    )
                    StepDecimalField(
                        label = stringResource(R.string.workout_editor_field_start_power),
                        value = step.startPowerText,
                        onValueChange = {
                            onAction(
                                WorkoutEditorAction.ChangeStepField(
                                    stepId = step.id,
                                    field = WorkoutEditorStepField.START_POWER,
                                    value = it,
                                ),
                            )
                        },
                    )
                    StepDecimalField(
                        label = stringResource(R.string.workout_editor_field_end_power),
                        value = step.endPowerText,
                        onValueChange = {
                            onAction(
                                WorkoutEditorAction.ChangeStepField(
                                    stepId = step.id,
                                    field = WorkoutEditorStepField.END_POWER,
                                    value = it,
                                ),
                            )
                        },
                    )
                }
                is WorkoutEditorStepDraft.Intervals -> {
                    StepNumberField(
                        label = stringResource(R.string.workout_editor_field_repeat),
                        value = step.repeatText,
                        onValueChange = {
                            onAction(
                                WorkoutEditorAction.ChangeStepField(
                                    stepId = step.id,
                                    field = WorkoutEditorStepField.REPEAT,
                                    value = it,
                                ),
                            )
                        },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StepNumberField(
                            label = stringResource(R.string.workout_editor_field_on_duration_sec),
                            value = step.onDurationSecText,
                            onValueChange = {
                                onAction(
                                    WorkoutEditorAction.ChangeStepField(
                                        stepId = step.id,
                                        field = WorkoutEditorStepField.ON_DURATION_SEC,
                                        value = it,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        StepNumberField(
                            label = stringResource(R.string.workout_editor_field_off_duration_sec),
                            value = step.offDurationSecText,
                            onValueChange = {
                                onAction(
                                    WorkoutEditorAction.ChangeStepField(
                                        stepId = step.id,
                                        field = WorkoutEditorStepField.OFF_DURATION_SEC,
                                        value = it,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StepDecimalField(
                            label = stringResource(R.string.workout_editor_field_on_power),
                            value = step.onPowerText,
                            onValueChange = {
                                onAction(
                                    WorkoutEditorAction.ChangeStepField(
                                        stepId = step.id,
                                        field = WorkoutEditorStepField.ON_POWER,
                                        value = it,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        StepDecimalField(
                            label = stringResource(R.string.workout_editor_field_off_power),
                            value = step.offPowerText,
                            onValueChange = {
                                onAction(
                                    WorkoutEditorAction.ChangeStepField(
                                        stepId = step.id,
                                        field = WorkoutEditorStepField.OFF_POWER,
                                        value = it,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        colors = workoutEditorTextFieldColors(),
    )
}

@Composable
private fun StepNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        colors = workoutEditorTextFieldColors(),
    )
}

@Composable
private fun StepDecimalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
        colors = workoutEditorTextFieldColors(),
    )
}

@Composable
private fun workoutEditorTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.Black,
    unfocusedTextColor = Color.Black,
    focusedLabelColor = Color.Black,
    unfocusedLabelColor = Color.Black,
    focusedBorderColor = Color(0xFF4A4A4A),
    unfocusedBorderColor = Color(0xFF6A6A6A),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    cursorColor = Color.Black,
)

package com.example.ergometerapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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

private val WorkoutEditorSinglePaneMaxWidth = 920.dp
private val WorkoutEditorTwoPaneMaxWidth = 1400.dp

@Composable
private fun workoutEditorCardBorder(): BorderStroke {
    val alpha = if (isSystemInDarkTheme()) 0.24f else 0.42f
    return BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
    )
}

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
    var selectedStepId by remember { mutableStateOf<Long?>(null) }
    var pendingAutoSelectStepCount by remember { mutableStateOf<Int?>(null) }
    var pendingSelectLastStep by remember { mutableStateOf(false) }
    var showBackWithUnsavedPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(draft.steps) {
        val pendingStepCount = pendingAutoSelectStepCount
        if (pendingStepCount != null && draft.steps.size >= pendingStepCount && draft.steps.isNotEmpty()) {
            selectedStepId = draft.steps.last().id
            pendingAutoSelectStepCount = null
            return@LaunchedEffect
        }
        if (pendingSelectLastStep) {
            selectedStepId = draft.steps.lastOrNull()?.id
            pendingSelectLastStep = false
            return@LaunchedEffect
        }
        selectedStepId = when {
            draft.steps.isEmpty() -> null
            selectedStepId == null -> draft.steps.first().id
            draft.steps.any { it.id == selectedStepId } -> selectedStepId
            else -> draft.steps.first().id
        }
    }
    val selectedStepIndex = remember(draft.steps, selectedStepId) {
        draft.steps.indexOfFirst { it.id == selectedStepId }
    }
    val selectedStep = selectedStepIndex
        .takeIf { it >= 0 }
        ?.let { draft.steps[it] }
    val segmentStepIds = remember(draft.steps, previewWorkout) {
        if (previewWorkout == null) {
            emptyList()
        } else {
            buildEditorSegmentStepIds(draft)
        }
    }
    val highlightedSegmentIndices = remember(segmentStepIds, selectedStepId) {
        if (selectedStepId == null) {
            emptySet()
        } else {
            segmentStepIds.mapIndexedNotNull { index, stepId ->
                index.takeIf { stepId == selectedStepId }
            }.toSet()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val layoutMode = rememberImeStableAdaptiveLayoutMode(width = maxWidth, height = maxHeight)
        val showTwoPane = layoutMode.isTwoPane()
        val useCompactActionLabels = showTwoPane
        val paneWeights = if (showTwoPane) {
            workoutEditorPaneWeights(layoutMode)
        } else {
            layoutMode.paneWeights()
        }
        val contentMaxWidth = if (showTwoPane) WorkoutEditorTwoPaneMaxWidth else WorkoutEditorSinglePaneMaxWidth

        val headerAndActionsContent: @Composable ColumnScope.() -> Unit = {
            Text(
                text = stringResource(R.string.workout_editor_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
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
                    onClick = {
                        if (hasUnsavedChanges) {
                            showBackWithUnsavedPrompt = true
                        } else {
                            onAction(WorkoutEditorAction.BackToMenu)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    ActionButtonLabel(
                        if (useCompactActionLabels) {
                            stringResource(R.string.workout_editor_back_to_menu_compact)
                        } else {
                            stringResource(R.string.workout_editor_back_to_menu)
                        }
                    )
                }
                Button(
                    onClick = { onAction(WorkoutEditorAction.NewDraft) },
                    modifier = Modifier.weight(1f),
                ) {
                    ActionButtonLabel(stringResource(R.string.workout_editor_new))
                }
                Button(
                    onClick = { onAction(WorkoutEditorAction.LoadSelectedWorkout) },
                    modifier = Modifier.weight(1f),
                ) {
                    ActionButtonLabel(
                        if (useCompactActionLabels) {
                            stringResource(R.string.workout_editor_load_selected_compact)
                        } else {
                            stringResource(R.string.workout_editor_load_selected)
                        }
                    )
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
                    ActionButtonLabel(
                        if (useCompactActionLabels) {
                            stringResource(R.string.workout_editor_save_zwo_compact)
                        } else {
                            stringResource(R.string.workout_editor_save_zwo)
                        }
                    )
                }
                Button(
                    onClick = { onAction(WorkoutEditorAction.ApplyToMenuSelection) },
                    enabled = validationErrors.isEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    ActionButtonLabel(
                        if (useCompactActionLabels) {
                            stringResource(R.string.workout_editor_apply_to_menu_compact)
                        } else {
                            stringResource(R.string.workout_editor_apply_to_menu)
                        }
                    )
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
        }

        val editorMetaContent: @Composable ColumnScope.() -> Unit = {
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
        }

        val editorStepsContent: @Composable ColumnScope.() -> Unit = {
            Text(
                text = stringResource(R.string.workout_editor_steps),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (selectedStep != null && selectedStepIndex >= 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            if (selectedStepIndex > 0) {
                                selectedStepId = draft.steps[selectedStepIndex - 1].id
                            }
                        },
                        enabled = selectedStepIndex > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.workout_editor_prev_step))
                    }
                    Text(
                        text = stringResource(
                            R.string.workout_editor_selected_step_position,
                            selectedStepIndex + 1,
                            draft.steps.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (selectedStepIndex < draft.steps.lastIndex) {
                                selectedStepId = draft.steps[selectedStepIndex + 1].id
                            }
                        },
                        enabled = selectedStepIndex < draft.steps.lastIndex,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.workout_editor_next_step))
                    }
                }

                WorkoutEditorStepCard(
                    step = selectedStep,
                    index = selectedStepIndex,
                    onAction = { action ->
                        if (action is WorkoutEditorAction.DeleteStep) {
                            pendingSelectLastStep = true
                        }
                        onAction(action)
                    },
                    useCompactLabels = useCompactActionLabels,
                    useTwoColumnActionLayout = useCompactActionLabels,
                )
            } else {
                Text(
                    text = stringResource(R.string.workout_editor_no_steps_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        pendingAutoSelectStepCount = draft.steps.size + 1
                        onAction(WorkoutEditorAction.AddStep(WorkoutEditorStepType.STEADY))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.workout_editor_add_steady))
                }
                Button(
                    onClick = {
                        pendingAutoSelectStepCount = draft.steps.size + 1
                        onAction(WorkoutEditorAction.AddStep(WorkoutEditorStepType.RAMP))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.workout_editor_add_ramp))
                }
                Button(
                    onClick = {
                        pendingAutoSelectStepCount = draft.steps.size + 1
                        onAction(WorkoutEditorAction.AddStep(WorkoutEditorStepType.INTERVALS))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.workout_editor_add_intervals))
                }
            }
        }

        val previewContent: @Composable ColumnScope.() -> Unit = {
            if (previewWorkout != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = workoutEditorCardBorder(),
                ) {
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
                            highlightedSegmentIndices = highlightedSegmentIndices,
                            onSegmentTap = { segmentIndex ->
                                segmentStepIds.getOrNull(segmentIndex)?.let { tappedStepId ->
                                    selectedStepId = tappedStepId
                                }
                            },
                        )
                    }
                }
            }
        }

        val validationContent: @Composable ColumnScope.() -> Unit = {
            if (validationErrors.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    border = workoutEditorCardBorder(),
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
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (showTwoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = contentMaxWidth),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(paneWeights.left),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        previewContent()
                        headerAndActionsContent()
                        editorMetaContent()
                        validationContent()
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Column(
                        modifier = Modifier
                            .weight(paneWeights.right)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        editorStepsContent()
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = contentMaxWidth)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    headerAndActionsContent()
                    editorMetaContent()
                    editorStepsContent()
                    validationContent()
                    previewContent()
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
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

    if (showBackWithUnsavedPrompt) {
        AlertDialog(
            onDismissRequest = { showBackWithUnsavedPrompt = false },
            title = { Text(stringResource(R.string.workout_editor_back_prompt_title)) },
            text = { Text(stringResource(R.string.workout_editor_back_prompt_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackWithUnsavedPrompt = false
                        onAction(WorkoutEditorAction.ConfirmApplyWithoutSaving)
                    },
                ) {
                    Text(stringResource(R.string.workout_editor_back_prompt_apply_and_back))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showBackWithUnsavedPrompt = false
                            onAction(WorkoutEditorAction.BackToMenu)
                        },
                    ) {
                        Text(stringResource(R.string.workout_editor_back_prompt_discard))
                    }
                    TextButton(
                        onClick = {
                            showBackWithUnsavedPrompt = false
                        },
                    ) {
                        Text(stringResource(R.string.workout_editor_back_prompt_keep_editing))
                    }
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
    useCompactLabels: Boolean,
    useTwoColumnActionLayout: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface,
        ),
        border = workoutEditorCardBorder(),
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

            if (useTwoColumnActionLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onAction(WorkoutEditorAction.MoveStepUp(step.id)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.workout_editor_step_up_compact),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = { onAction(WorkoutEditorAction.MoveStepDown(step.id)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.workout_editor_step_down_compact),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onAction(WorkoutEditorAction.DuplicateStep(step.id)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.workout_editor_step_duplicate_compact),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = { onAction(WorkoutEditorAction.DeleteStep(step.id)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.workout_editor_step_delete_compact),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
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
                            text = if (useCompactLabels) {
                                stringResource(R.string.workout_editor_step_up_compact)
                            } else {
                                stringResource(R.string.workout_editor_step_up)
                            },
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
                            text = if (useCompactLabels) {
                                stringResource(R.string.workout_editor_step_down_compact)
                            } else {
                                stringResource(R.string.workout_editor_step_down)
                            },
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
                            text = if (useCompactLabels) {
                                stringResource(R.string.workout_editor_step_duplicate_compact)
                            } else {
                                stringResource(R.string.workout_editor_step_duplicate)
                            },
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
                            text = if (useCompactLabels) {
                                stringResource(R.string.workout_editor_step_delete_compact)
                            } else {
                                stringResource(R.string.workout_editor_step_delete)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            when (step) {
                is WorkoutEditorStepDraft.Steady -> {
                    if (useTwoColumnActionLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StepNumberField(
                                label = stringResource(R.string.workout_editor_field_duration_sec_compact),
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
                                modifier = Modifier.weight(1f),
                            )
                            StepDecimalField(
                                label = stringResource(R.string.workout_editor_field_power_compact),
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
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
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
                }
                is WorkoutEditorStepDraft.Ramp -> {
                    if (useTwoColumnActionLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
                                modifier = Modifier.weight(1f),
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
                                modifier = Modifier.weight(1f),
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
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
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
                }
                is WorkoutEditorStepDraft.Intervals -> {
                    if (useTwoColumnActionLayout) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StepNumberField(
                                label = stringResource(R.string.workout_editor_field_repeat_compact),
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
                                modifier = Modifier.weight(1.4f),
                            )
                            StepNumberField(
                                label = stringResource(R.string.workout_editor_field_on_duration_sec_compact),
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
                                modifier = Modifier.weight(0.9f),
                            )
                            StepNumberField(
                                label = stringResource(R.string.workout_editor_field_off_duration_sec_compact),
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
                                modifier = Modifier.weight(0.9f),
                            )
                            StepDecimalField(
                                label = stringResource(R.string.workout_editor_field_on_power_compact),
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
                                modifier = Modifier.weight(0.9f),
                            )
                            StepDecimalField(
                                label = stringResource(R.string.workout_editor_field_off_power_compact),
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
                                modifier = Modifier.weight(0.9f),
                            )
                        }
                    } else {
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
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
        colors = workoutEditorTextFieldColors(),
    )
}

@Composable
private fun StepDecimalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
        colors = workoutEditorTextFieldColors(),
    )
}

@Composable
private fun workoutEditorTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    cursorColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun ActionButtonLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun workoutEditorPaneWeights(layoutMode: AdaptiveLayoutMode): AdaptivePaneWeights {
    return when (layoutMode) {
        AdaptiveLayoutMode.TWO_PANE_MEDIUM -> AdaptivePaneWeights(left = 0.44f, right = 0.56f)
        AdaptiveLayoutMode.TWO_PANE_EXPANDED -> AdaptivePaneWeights(left = 0.42f, right = 0.58f)
        AdaptiveLayoutMode.SINGLE_PANE,
        AdaptiveLayoutMode.SINGLE_PANE_DENSE,
        -> layoutMode.paneWeights()
    }
}

/**
 * Builds step-id mapping for rendered chart segments so chart taps can select the source step.
 *
 * Interval steps are expanded to ON/OFF segment pairs to match chart rendering semantics.
 */
private fun buildEditorSegmentStepIds(draft: WorkoutEditorDraft): List<Long> {
    val segmentStepIds = mutableListOf<Long>()
    draft.steps.forEach { step ->
        when (step) {
            is WorkoutEditorStepDraft.Steady -> {
                if (step.durationSecText.toIntOrNull()?.let { it > 0 } == true) {
                    segmentStepIds += step.id
                }
            }

            is WorkoutEditorStepDraft.Ramp -> {
                if (step.durationSecText.toIntOrNull()?.let { it > 0 } == true) {
                    segmentStepIds += step.id
                }
            }

            is WorkoutEditorStepDraft.Intervals -> {
                val repeatCount = step.repeatText.toIntOrNull()?.takeIf { it > 0 } ?: return@forEach
                val onDurationValid = step.onDurationSecText.toIntOrNull()?.let { it > 0 } == true
                val offDurationValid = step.offDurationSecText.toIntOrNull()?.let { it > 0 } == true
                if (!onDurationValid || !offDurationValid) return@forEach
                repeat(repeatCount) {
                    segmentStepIds += step.id
                    segmentStepIds += step.id
                }
            }
        }
    }
    return segmentStepIds
}

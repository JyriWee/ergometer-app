package com.example.ergometerapp.ui

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.R
import com.example.ergometerapp.DeviceSelectionKind
import com.example.ergometerapp.ScannedBleDevice
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.ui.components.SegmentKind
import com.example.ergometerapp.ui.components.WorkoutProfileChart
import com.example.ergometerapp.ui.components.WorkoutProfileSegment
import com.example.ergometerapp.ui.components.buildWorkoutProfileSegments
import com.example.ergometerapp.ui.components.disabledVisibleButtonColors
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.runner.IntervalPartPhase
import com.example.ergometerapp.workout.runner.RunnerState
import kotlin.math.roundToInt
import java.util.Locale

private val SessionTopMetricsCompactWidth = 700.dp
private val MenuMaxContentWidth = 560.dp
private val MenuTwoPaneMaxContentWidth = 1200.dp
private val SessionMaxContentWidth = 1200.dp
private val SummaryMaxContentWidth = 920.dp
private val SummaryTwoPaneMaxContentWidth = 1200.dp
private val SessionStickyActionBottomPadding = 96.dp
private val SessionWorkoutChartHeight = 220.dp

private data class MetricItem(
    val label: String,
    val value: String
)

private enum class DeviceConnectionIndicatorState {
    CONNECTED,
    IDLE,
    ISSUE,
}

@Composable
private fun sessionCardBorder(): BorderStroke {
    val alpha = if (isSystemInDarkTheme()) 0.22f else 0.45f
    return BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
    )
}

@Composable
private fun menuNormalTextColor() = MaterialTheme.colorScheme.onSurface

@Composable
private fun menuErrorTextColor() = MaterialTheme.colorScheme.error

@Composable
private fun menuPickerStatusColor() = MaterialTheme.colorScheme.tertiary

@Composable
private fun menuPickerWarningColor() = MaterialTheme.colorScheme.error

@Composable
private fun menuPickerNeutralColor() = MaterialTheme.colorScheme.onSurface

@Composable
private fun menuStartCtaColor() = MaterialTheme.colorScheme.primary

@Composable
private fun menuStartCtaContentColor() = MaterialTheme.colorScheme.onPrimary

@Composable
private fun menuDeviceConnectedColor() = MaterialTheme.colorScheme.primary

@Composable
private fun menuDeviceIdleColor() = MaterialTheme.colorScheme.outline

@Composable
private fun menuDeviceIssueColor() = MaterialTheme.colorScheme.error

@Composable
private fun menuTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = menuNormalTextColor(),
    unfocusedTextColor = menuNormalTextColor(),
    cursorColor = menuNormalTextColor(),
    focusedBorderColor = menuNormalTextColor(),
    unfocusedBorderColor = menuNormalTextColor(),
    focusedLabelColor = menuNormalTextColor(),
    unfocusedLabelColor = menuNormalTextColor(),
    focusedPlaceholderColor = menuNormalTextColor().copy(alpha = 0.7f),
    unfocusedPlaceholderColor = menuNormalTextColor().copy(alpha = 0.7f),
    errorBorderColor = menuErrorTextColor(),
    errorLabelColor = menuErrorTextColor(),
    errorCursorColor = menuErrorTextColor(),
)

@Composable
private fun menuSecondaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
)

@Composable
private fun menuInfoCardColors() = CardDefaults.elevatedCardColors(
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * Entry screen for starting a session.
 *
 * The start action is gated on successful workout import and selected trainer
 * so runner execution always starts from a validated plan and explicit device.
 */
@Composable
internal fun MenuScreen(
    selectedWorkoutFileName: String?,
    selectedWorkoutStepCount: Int?,
    selectedWorkoutPlannedTss: Double?,
    selectedWorkoutImportError: String?,
    selectedWorkout: WorkoutFile?,
    ftpWatts: Int,
    ftpInputText: String,
    ftpInputError: String?,
    ftmsDeviceName: String,
    ftmsSelected: Boolean,
    ftmsConnected: Boolean,
    ftmsConnectionKnown: Boolean,
    hrDeviceName: String,
    hrSelected: Boolean,
    hrConnected: Boolean,
    hrConnectionKnown: Boolean,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
    connectionIssueMessage: String?,
    suggestTrainerSearchAfterConnectionIssue: Boolean,
    activeDeviceSelectionKind: DeviceSelectionKind?,
    scannedDevices: List<ScannedBleDevice>,
    deviceScanInProgress: Boolean,
    deviceScanStatus: String?,
    deviceScanStopEnabled: Boolean,
    startEnabled: Boolean,
    onSelectWorkoutFile: () -> Unit,
    onOpenWorkoutEditor: () -> Unit,
    onFtpInputChanged: (String) -> Unit,
    onSearchFtmsDevices: () -> Unit,
    onSearchHrDevices: () -> Unit,
    onScannedDeviceSelected: (ScannedBleDevice) -> Unit,
    onDismissDeviceSelection: () -> Unit,
    onDismissConnectionIssue: () -> Unit,
    onSearchFtmsDevicesFromConnectionIssue: () -> Unit,
    onStartSession: () -> Unit
) {
    val normalTextColor = menuNormalTextColor()
    val errorTextColor = menuErrorTextColor()
    val pickerStatusColor = menuPickerStatusColor()
    val pickerWarningColor = menuPickerWarningColor()
    val pickerNeutralColor = menuPickerNeutralColor()
    val startCtaColor = menuStartCtaColor()
    val startCtaContentColor = menuStartCtaContentColor()
    val showWorkoutFileDialog = remember { mutableStateOf(false) }
    val showWorkoutNameDialog = remember { mutableStateOf(false) }
    val showWorkoutDescriptionDialog = remember { mutableStateOf(false) }
    val unknown = stringResource(R.string.value_unknown)
    val stepCountText =
        if (selectedWorkout != null && selectedWorkoutStepCount != null && selectedWorkoutImportError == null) {
            stringResource(R.string.menu_workout_step_count, selectedWorkoutStepCount)
        } else {
            null
        }
    val plannedTssText =
        if (selectedWorkout != null && selectedWorkoutPlannedTss != null && selectedWorkoutImportError == null) {
            stringResource(R.string.menu_workout_planned_tss, selectedWorkoutPlannedTss)
        } else {
            null
        }
    val statusText =
        when {
            selectedWorkoutImportError != null -> {
                stringResource(R.string.menu_workout_import_failed, selectedWorkoutImportError)
            }
            selectedWorkout == null -> stringResource(R.string.menu_workout_not_selected)
            else -> null
        }
    val statusTextColor = if (selectedWorkoutImportError != null) errorTextColor else normalTextColor
    val workoutFileDisplayName = selectedWorkoutFileName
        ?.substringAfterLast('/')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: stringResource(R.string.menu_workout_file_none)
    val workoutNameTagValue = selectedWorkout?.name
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: unknown
    val workoutDescriptionTagValue = selectedWorkout?.description
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: unknown
    val trainerDisplayName = ftmsDeviceName.ifBlank { stringResource(R.string.menu_device_not_selected) }
    val hrDisplayName = hrDeviceName.ifBlank { stringResource(R.string.menu_device_not_selected) }
    val trainerIndicatorState = when {
        ftmsConnected -> DeviceConnectionIndicatorState.CONNECTED
        ftmsSelected && ftmsConnectionKnown -> DeviceConnectionIndicatorState.ISSUE
        suggestTrainerSearchAfterConnectionIssue && !connectionIssueMessage.isNullOrBlank() -> {
            DeviceConnectionIndicatorState.ISSUE
        }
        else -> DeviceConnectionIndicatorState.IDLE
    }
    val hrIndicatorState = if (hrConnected) {
        DeviceConnectionIndicatorState.CONNECTED
    } else if (hrSelected && hrConnectionKnown) {
        DeviceConnectionIndicatorState.ISSUE
    } else {
        DeviceConnectionIndicatorState.IDLE
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val layoutMode = resolveAdaptiveLayoutMode(width = maxWidth, height = maxHeight)
        val showTwoPane = layoutMode.isTwoPane()
        val paneWeights = layoutMode.paneWeights()
        val contentMaxWidth = if (showTwoPane) MenuTwoPaneMaxContentWidth else MenuMaxContentWidth

        val leftPaneContent: @Composable ColumnScope.() -> Unit = {
            Text(
                text = stringResource(R.string.menu_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = normalTextColor
            )

            if (showTwoPane) {
                Text(
                    text = stringResource(R.string.menu_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = normalTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = ftpInputText,
                        onValueChange = onFtpInputChanged,
                        modifier = Modifier.widthIn(min = 72.dp, max = 96.dp),
                        placeholder = { Text("FTP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = ftpInputError != null,
                        colors = menuTextFieldColors(),
                    )
                    Text(
                        text = stringResource(R.string.menu_ftp_hint, ftpWatts),
                        style = MaterialTheme.typography.bodySmall,
                        color = normalTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.menu_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = normalTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.5f),
                    )

                    OutlinedTextField(
                        value = ftpInputText,
                        onValueChange = onFtpInputChanged,
                        modifier = Modifier.weight(0.16666667f),
                        placeholder = { Text("FTP") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = ftpInputError != null,
                        colors = menuTextFieldColors(),
                    )

                    Text(
                        text = stringResource(R.string.menu_ftp_hint, ftpWatts),
                        style = MaterialTheme.typography.bodySmall,
                        color = normalTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.33333334f),
                    )
                }
            }

            if (ftpInputError != null) {
                Text(
                    text = ftpInputError,
                    style = MaterialTheme.typography.bodySmall,
                    color = errorTextColor,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                DeviceSelectionInfoCard(
                    label = stringResource(R.string.menu_trainer_device_label),
                    value = trainerDisplayName,
                    indicatorState = trainerIndicatorState,
                    compactLabel = showTwoPane,
                    modifier = Modifier.weight(1f),
                )
                DeviceSelectionInfoCard(
                    label = stringResource(R.string.menu_hr_device_label),
                    value = hrDisplayName,
                    indicatorState = hrIndicatorState,
                    compactLabel = showTwoPane,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSearchFtmsDevices,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = menuSecondaryButtonColors()
                ) {
                    Text(stringResource(R.string.menu_search_trainer_devices_short))
                }

                Button(
                    onClick = onSearchHrDevices,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = menuSecondaryButtonColors()
                ) {
                    Text(stringResource(R.string.menu_search_hr_devices_short))
                }
            }

            if (activeDeviceSelectionKind != null) {
                val unknownDeviceName = stringResource(R.string.menu_device_unknown_name)
                val title = when (activeDeviceSelectionKind) {
                    DeviceSelectionKind.FTMS -> stringResource(R.string.menu_trainer_picker_title)
                    DeviceSelectionKind.HEART_RATE -> stringResource(R.string.menu_hr_picker_title)
                }
                SectionCard(title = title) {
                    if (deviceScanStatus != null) {
                        val scanStatusText = if (deviceScanInProgress) {
                            val dotsTransition = rememberInfiniteTransition(label = "deviceScanDots")
                            val dotsProgress = dotsTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 3f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(
                                        durationMillis = 1400,
                                        easing = LinearEasing,
                                    ),
                                    repeatMode = RepeatMode.Restart,
                                ),
                                label = "deviceScanDotsProgress",
                            ).value
                            val dotsCount = dotsProgress.toInt().coerceIn(0, 2) + 1
                            val baseText = deviceScanStatus.trimEnd().trimEnd('.', '…')
                            "$baseText${".".repeat(dotsCount)}"
                        } else {
                            deviceScanStatus
                        }
                        Text(
                            text = scanStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = pickerStatusColor
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    scannedDevices.forEach { device ->
                        val label = buildString {
                            val baseName = device.displayName?.takeIf { it.isNotBlank() }
                                ?: unknownDeviceName
                            append(baseName)
                            append(" • RSSI ")
                            append(device.rssi)
                        }
                        Button(
                            onClick = { onScannedDeviceSelected(device) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = disabledVisibleButtonColors()
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    val dismissContentColor =
                        if (deviceScanInProgress) pickerWarningColor else pickerNeutralColor
                    val dismissBorderColor =
                        if (deviceScanInProgress) pickerWarningColor else pickerNeutralColor.copy(alpha = 0.75f)

                    OutlinedButton(
                        onClick = onDismissDeviceSelection,
                        enabled = if (deviceScanInProgress) deviceScanStopEnabled else true,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, dismissBorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = dismissContentColor)
                    ) {
                        Text(
                            stringResource(
                                if (deviceScanInProgress) {
                                    R.string.menu_cancel_device_scan
                                } else {
                                    R.string.menu_close_device_picker
                                }
                            )
                        )
                    }
                }
            }
        }

        val rightPaneContent: @Composable ColumnScope.() -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSelectWorkoutFile,
                    modifier = Modifier
                        .weight(0.3f)
                        .height(40.dp),
                    colors = menuSecondaryButtonColors(),
                ) {
                    Text(
                        text = stringResource(R.string.menu_select_workout_file_short),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MenuInlineValueCard(
                    value = workoutFileDisplayName,
                    modifier = Modifier.weight(0.7f),
                    onClick = if (selectedWorkoutFileName != null) {
                        { showWorkoutFileDialog.value = true }
                    } else {
                        null
                    },
                )
            }

            Button(
                onClick = onOpenWorkoutEditor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                colors = menuSecondaryButtonColors(),
            ) {
                Text(stringResource(R.string.menu_open_workout_editor))
            }

            if (selectedWorkout != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    WorkoutMetaListBox(
                        label = stringResource(R.string.menu_workout_name_tag),
                        value = workoutNameTagValue,
                        onClick = { showWorkoutNameDialog.value = true },
                        modifier = Modifier.weight(1f),
                    )
                    WorkoutMetaListBox(
                        label = stringResource(R.string.menu_workout_description_tag),
                        value = workoutDescriptionTagValue,
                        onClick = { showWorkoutDescriptionDialog.value = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (statusText != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusTextColor
                )
            }

            if (workoutExecutionModeMessage != null) {
                Text(
                    text = workoutExecutionModeMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (workoutExecutionModeIsError) {
                        errorTextColor
                    } else {
                        normalTextColor
                    }
                )
            }

            if (selectedWorkout != null) {
                SectionCard(title = null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.session_workout_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = normalTextColor,
                        )
                        if (stepCountText != null || plannedTssText != null) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                if (stepCountText != null) {
                                    Text(
                                        text = stepCountText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = normalTextColor,
                                    )
                                }
                                if (plannedTssText != null) {
                                    Text(
                                        text = plannedTssText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = normalTextColor,
                                    )
                                }
                            }
                        }
                    }
                    WorkoutProfileChart(
                        workout = selectedWorkout,
                        ftpWatts = ftpWatts,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (showTwoPane) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(paneWeights.left),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            content = leftPaneContent,
                        )
                        Column(
                            modifier = Modifier.weight(paneWeights.right),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            content = rightPaneContent,
                        )
                    }
                } else {
                    leftPaneContent()
                    rightPaneContent()
                }

                Button(
                    onClick = onStartSession,
                    enabled = startEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = startCtaColor,
                        contentColor = startCtaContentColor,
                        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.menu_start_session),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showWorkoutFileDialog.value && selectedWorkoutFileName != null) {
        AlertDialog(
            onDismissRequest = { showWorkoutFileDialog.value = false },
            title = { Text(stringResource(R.string.menu_workout_file_dialog_title)) },
            text = { Text(workoutFileDisplayName) },
            confirmButton = {
                TextButton(onClick = { showWorkoutFileDialog.value = false }) {
                    Text(stringResource(R.string.menu_dialog_ok))
                }
            },
        )
    }

    if (showWorkoutNameDialog.value && selectedWorkout != null) {
        AlertDialog(
            onDismissRequest = { showWorkoutNameDialog.value = false },
            title = { Text(stringResource(R.string.menu_workout_name_tag)) },
            text = { Text(workoutNameTagValue) },
            confirmButton = {
                TextButton(onClick = { showWorkoutNameDialog.value = false }) {
                    Text(stringResource(R.string.menu_dialog_ok))
                }
            },
        )
    }

    if (showWorkoutDescriptionDialog.value && selectedWorkout != null) {
        AlertDialog(
            onDismissRequest = { showWorkoutDescriptionDialog.value = false },
            title = { Text(stringResource(R.string.menu_workout_description_tag)) },
            text = { Text(workoutDescriptionTagValue) },
            confirmButton = {
                TextButton(onClick = { showWorkoutDescriptionDialog.value = false }) {
                    Text(stringResource(R.string.menu_dialog_ok))
                }
            },
        )
    }

    if (suggestTrainerSearchAfterConnectionIssue && connectionIssueMessage != null) {
        AlertDialog(
            onDismissRequest = onDismissConnectionIssue,
            title = { Text(stringResource(R.string.menu_connection_issue_title)) },
            text = { Text(connectionIssueMessage) },
            confirmButton = {
                TextButton(onClick = onSearchFtmsDevicesFromConnectionIssue) {
                    Text(stringResource(R.string.menu_connection_issue_search_again))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissConnectionIssue) {
                    Text(stringResource(R.string.menu_connection_issue_dismiss))
                }
            }
        )
    }
}

/**
 * Transitional screen shown while FTMS setup is completing.
 */
@Composable
internal fun ConnectingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.status_connecting),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.menu_connection_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceSelectionInfoCard(
    label: String,
    value: String,
    indicatorState: DeviceConnectionIndicatorState,
    compactLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val normalTextColor = menuNormalTextColor()
    val connectionPulseTransition = rememberInfiniteTransition(label = "deviceConnectionPulse")
    val pulseAlpha = connectionPulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "deviceConnectionPulseAlpha",
    ).value
    val indicatorColor = when (indicatorState) {
        DeviceConnectionIndicatorState.CONNECTED -> menuDeviceConnectedColor()
        DeviceConnectionIndicatorState.IDLE -> menuDeviceIdleColor()
        DeviceConnectionIndicatorState.ISSUE -> menuDeviceIssueColor()
    }
    val indicatorAlpha = if (indicatorState == DeviceConnectionIndicatorState.CONNECTED) {
        pulseAlpha
    } else {
        1.0f
    }

    ElevatedCard(
        modifier = modifier.height(if (compactLabel) 88.dp else 78.dp),
        colors = menuInfoCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = if (compactLabel) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    color = normalTextColor,
                    maxLines = if (compactLabel) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = indicatorColor.copy(alpha = indicatorAlpha),
                            shape = CircleShape,
                        )
                )
            }
            Text(
                text = value,
                style = if (compactLabel) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = normalTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MenuInlineValueCard(
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val normalTextColor = menuNormalTextColor()
    val cardModifier = if (onClick != null) {
        modifier
            .height(40.dp)
            .clickable(onClick = onClick)
    } else {
        modifier.height(40.dp)
    }
    ElevatedCard(
        modifier = cardModifier,
        colors = menuInfoCardColors(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = normalTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WorkoutMetaListBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val normalTextColor = menuNormalTextColor()
    val cardModifier = if (onClick != null) {
        modifier
            .height(92.dp)
            .clickable(onClick = onClick)
    } else {
        modifier.height(92.dp)
    }
    ElevatedCard(
        modifier = cardModifier,
        colors = menuInfoCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = normalTextColor,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = normalTextColor,
                )
            }
        }
    }
}

/**
 * Transitional screen shown while waiting for STOP acknowledgment before summary.
 */
@Composable
internal fun StoppingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.status_stopping),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.status_stopping_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Live session UI.
 *
 * This screen surfaces FTMS/HR telemetry and exposes control actions. Buttons
 * are intentionally visible but disabled when control has not been granted, to
 * make the protocol state explicit to the user.
 */
@Composable
internal fun SessionScreen(
    phase: SessionPhase,
    bikeData: IndoorBikeData?,
    heartRate: Int?,
    ftmsReady: Boolean,
    ftmsControlGranted: Boolean,
    selectedWorkout: WorkoutFile?,
    selectedWorkoutFileName: String?,
    ftpWatts: Int,
    runnerState: RunnerState,
    lastTargetPower: Int?,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
    onEndSession: () -> Unit
) {
    LaunchedEffect(bikeData) {
        Log.d("UI", "SessionScreen bikeData updated: $bikeData")
    }

    val unknown = stringResource(R.string.value_unknown)
    val effectiveHr = heartRate ?: bikeData?.heartRateBpm
    val powerValue = stringResource(
        R.string.session_power_value,
        format0(bikeData?.instantaneousPowerW, unknown)
    )
    val heartRateValue = stringResource(
        R.string.session_hr_value,
        format0(effectiveHr, unknown)
    )
    val speedValue = stringResource(
        R.string.session_speed_value,
        format1(bikeData?.instantaneousSpeedKmh, unknown)
    )
    val distanceValue = stringResource(
        R.string.summary_distance_value,
        format0(bikeData?.totalDistanceMeters, unknown)
    )
    val kcalValue = stringResource(
        R.string.session_kcal_value,
        format0(bikeData?.totalEnergyKcal, unknown)
    )
    val cadenceRpm = bikeData?.instantaneousCadenceRpm
    val cadenceTargetValue = stringResource(
        R.string.session_cadence_target_value,
        format1(cadenceRpm, unknown),
        format0(runnerState.targetCadence, unknown),
    )
    val workoutSegments = remember(selectedWorkout) {
        selectedWorkout?.let { buildWorkoutProfileSegments(it) }.orEmpty()
    }
    val totalWorkoutSec = workoutSegments.sumOf { it.durationSec }.takeIf { it > 0 }
    val elapsedWorkoutSec = runnerState.workoutElapsedSec?.coerceAtLeast(0) ?: 0
    val remainingWorkoutSec = totalWorkoutSec?.let { total ->
        (total - elapsedWorkoutSec).coerceAtLeast(0)
    }
    val elapsedText = formatTime(elapsedWorkoutSec, unknown)
    val remainingText = formatTime(remainingWorkoutSec, unknown)
    val totalText = formatTime(totalWorkoutSec, unknown)
    val elapsedOfTotalText = stringResource(
        R.string.session_elapsed_of_total_value,
        elapsedText,
        totalText,
    )
    val workoutComplete = totalWorkoutSec != null &&
        totalWorkoutSec > 0 &&
        remainingWorkoutSec == 0
    val workoutName = resolveWorkoutDisplayName(
        selectedWorkout = selectedWorkout,
        selectedWorkoutFileName = selectedWorkoutFileName,
        fallback = unknown,
    )
    val sessionIssues = buildList {
        if (!ftmsReady) {
            add(stringResource(R.string.session_issue_ftms_not_ready))
        }
        if (ftmsReady && !ftmsControlGranted) {
            add(stringResource(R.string.session_issue_control_missing))
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val layoutMode = resolveAdaptiveLayoutMode(width = maxWidth, height = maxHeight)
        val showTwoPane = layoutMode.isTwoPane()
        val paneWeights = layoutMode.paneWeights()
        val compactTopMetrics = !showTwoPane && maxWidth < SessionTopMetricsCompactWidth

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .fillMaxWidth()
                    .widthIn(max = SessionMaxContentWidth),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .padding(bottom = SessionStickyActionBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (showTwoPane) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.weight(paneWeights.left),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                TopTelemetrySection(
                                    compactLayout = compactTopMetrics,
                                    heartRateValue = heartRateValue,
                                    speedValue = speedValue,
                                    powerValue = powerValue,
                                    cadenceTargetValue = cadenceTargetValue,
                                    distanceValue = distanceValue,
                                    kcalValue = kcalValue,
                                )
                                if (sessionIssues.isNotEmpty()) {
                                    SessionIssuesSection(messages = sessionIssues)
                                }
                            }
                            Column(
                                modifier = Modifier.weight(paneWeights.right),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                WorkoutProgressSection(
                                    selectedWorkout = selectedWorkout,
                                    workoutName = workoutName,
                                    workoutSegments = workoutSegments,
                                    ftpWatts = ftpWatts,
                                    runnerState = runnerState,
                                    phase = phase,
                                    cadenceRpm = cadenceRpm,
                                    remainingText = remainingText,
                                    elapsedOfTotalText = elapsedOfTotalText,
                                    unknown = unknown,
                                    lastTargetPower = lastTargetPower,
                                    workoutComplete = workoutComplete,
                                    workoutExecutionModeMessage = workoutExecutionModeMessage,
                                    workoutExecutionModeIsError = workoutExecutionModeIsError,
                                )
                            }
                        }
                    } else {
                        TopTelemetrySection(
                            compactLayout = compactTopMetrics,
                            heartRateValue = heartRateValue,
                            speedValue = speedValue,
                            powerValue = powerValue,
                            cadenceTargetValue = cadenceTargetValue,
                            distanceValue = distanceValue,
                            kcalValue = kcalValue,
                        )

                        WorkoutProgressSection(
                            selectedWorkout = selectedWorkout,
                            workoutName = workoutName,
                            workoutSegments = workoutSegments,
                            ftpWatts = ftpWatts,
                            runnerState = runnerState,
                            phase = phase,
                            cadenceRpm = cadenceRpm,
                            remainingText = remainingText,
                            elapsedOfTotalText = elapsedOfTotalText,
                            unknown = unknown,
                            lastTargetPower = lastTargetPower,
                            workoutComplete = workoutComplete,
                            workoutExecutionModeMessage = workoutExecutionModeMessage,
                            workoutExecutionModeIsError = workoutExecutionModeIsError,
                        )

                        if (sessionIssues.isNotEmpty()) {
                            SessionIssuesSection(messages = sessionIssues)
                        }
                    }
                }
            }

            Button(
                onClick = onEndSession,
                enabled = phase == SessionPhase.RUNNING,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.5f)
                    .padding(vertical = 16.dp),
                colors = disabledVisibleButtonColors()
            ) {
                Text(stringResource(R.string.btn_quit_session_now))
            }
        }
    }
}

@Composable
private fun TopTelemetrySection(
    compactLayout: Boolean,
    heartRateValue: String,
    speedValue: String,
    powerValue: String,
    cadenceTargetValue: String,
    distanceValue: String,
    kcalValue: String,
) {
    val cardBorder = sessionCardBorder()
    SectionCard(title = null, border = cardBorder) {
        if (compactLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_hr_short_label),
                    value = heartRateValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_instant_power_label),
                    value = powerValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.summary_distance),
                    value = distanceValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_speed_label),
                    value = speedValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_cadence_target_label),
                    value = cadenceTargetValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_kcal_label),
                    value = kcalValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
            }
            return@SectionCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_hr_short_label),
                    value = heartRateValue,
                    modifier = Modifier.fillMaxWidth(),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_speed_label),
                    value = speedValue,
                    modifier = Modifier.fillMaxWidth(),
                    border = cardBorder,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_instant_power_label),
                    value = powerValue,
                    modifier = Modifier.fillMaxWidth(),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_cadence_target_label),
                    value = cadenceTargetValue,
                    modifier = Modifier.fillMaxWidth(),
                    border = cardBorder,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.summary_distance),
                    value = distanceValue,
                    modifier = Modifier.fillMaxWidth(),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_kcal_label),
                    value = kcalValue,
                    modifier = Modifier.fillMaxWidth(),
                    border = cardBorder,
                )
            }
        }
    }
}

@Composable
private fun TopMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
) {
    Card(
        modifier = modifier,
        border = border,
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WorkoutProgressSection(
    selectedWorkout: WorkoutFile?,
    workoutName: String,
    workoutSegments: List<WorkoutProfileSegment>,
    ftpWatts: Int,
    runnerState: RunnerState,
    phase: SessionPhase,
    cadenceRpm: Double?,
    remainingText: String,
    elapsedOfTotalText: String,
    unknown: String,
    lastTargetPower: Int?,
    workoutComplete: Boolean,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
) {
    val cardBorder = sessionCardBorder()
    val activeSegment = remember(workoutSegments, runnerState.workoutElapsedSec, workoutComplete) {
        currentWorkoutProfileSegment(
            workoutSegments = workoutSegments,
            elapsedSec = runnerState.workoutElapsedSec,
            completed = workoutComplete,
        )
    }
    SectionCard(title = null, border = cardBorder) {
        Text(
            text = stringResource(R.string.session_workout_name_value, workoutName),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                label = stringResource(R.string.session_workout_step_remaining),
                value = formatTime(runnerState.stepRemainingSec, unknown),
                modifier = Modifier.weight(1f),
                border = cardBorder,
            )
            MetricCard(
                label = stringResource(R.string.session_workout_remaining),
                value = remainingText,
                modifier = Modifier.weight(1f),
                border = cardBorder,
            )
            MetricCard(
                label = stringResource(R.string.session_elapsed_of_total),
                value = elapsedOfTotalText,
                modifier = Modifier.weight(1f),
                valueStyle = MaterialTheme.typography.titleMedium,
                valueMaxLines = 1,
                border = cardBorder,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetricCard(
                label = stringResource(R.string.session_workout_step_type),
                value = sessionStepTypeLabel(
                    phase = phase,
                    runnerState = runnerState,
                    cadenceRpm = cadenceRpm,
                    activeSegment = activeSegment,
                    unknown = unknown,
                ),
                modifier = Modifier.weight(1f),
                border = cardBorder,
            )
            MetricCard(
                label = stringResource(R.string.session_target_label),
                value = sessionTargetPowerLabel(
                    runnerState = runnerState,
                    activeSegment = activeSegment,
                    ftpWatts = ftpWatts,
                    fallbackTargetPower = lastTargetPower,
                    unknown = unknown,
                ),
                modifier = Modifier.weight(1f),
                border = cardBorder,
            )
        }

        val stateMessage = sessionStateLabel(
            phase = phase,
            runnerState = runnerState,
            cadenceRpm = cadenceRpm,
        )
        val intervalMessage = runnerState.intervalPart?.let { intervalPart ->
            intervalCountdownLabel(
                phase = intervalPart.phase,
                remainingSec = intervalPart.remainingSec,
            )
        }
        val extraMessages = buildList<Pair<String, Boolean>> {
            if (!workoutExecutionModeMessage.isNullOrBlank()) {
                add(workoutExecutionModeMessage to workoutExecutionModeIsError)
            }
            if (workoutComplete) {
                add(stringResource(R.string.session_workout_complete) to false)
            }
        }

        Text(
            text = stringResource(R.string.session_workout_messages),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stateMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            if (intervalMessage != null) {
                Text(
                    text = " \u2022 ",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = intervalMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        extraMessages.forEach { (message, isError) ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (selectedWorkout != null) {
            WorkoutProfileChart(
                workout = selectedWorkout,
                ftpWatts = ftpWatts,
                elapsedSec = runnerState.workoutElapsedSec,
                currentTargetWatts = runnerState.targetPowerWatts ?: lastTargetPower,
                chartHeight = SessionWorkoutChartHeight
            )
        }
    }
}

private fun resolveWorkoutDisplayName(
    selectedWorkout: WorkoutFile?,
    selectedWorkoutFileName: String?,
    fallback: String,
): String {
    val parsedName = selectedWorkout?.name?.trim().orEmpty()
    if (parsedName.isNotEmpty()) {
        return parsedName
    }
    val fileName = selectedWorkoutFileName
        ?.substringAfterLast('/')
        ?.trim()
        .orEmpty()
    if (fileName.isNotEmpty()) {
        return fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
    }
    return fallback
}

private fun currentWorkoutProfileSegment(
    workoutSegments: List<WorkoutProfileSegment>,
    elapsedSec: Int?,
    completed: Boolean,
): WorkoutProfileSegment? {
    if (workoutSegments.isEmpty()) return null
    if (completed) return workoutSegments.last()
    val elapsed = (elapsedSec ?: 0).coerceAtLeast(0)
    return workoutSegments.firstOrNull { segment ->
        elapsed < (segment.startSec + segment.durationSec)
    } ?: workoutSegments.last()
}

@Composable
private fun sessionStepTypeLabel(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    activeSegment: WorkoutProfileSegment?,
    unknown: String,
): String {
    if (isWaitingStartState(phase = phase, runnerState = runnerState, cadenceRpm = cadenceRpm)) {
        return stringResource(R.string.session_workout_step_start)
    }
    if (runnerState.done) {
        return stringResource(R.string.session_workout_state_done)
    }
    if (runnerState.intervalPart != null) {
        return stringResource(R.string.session_workout_step_type_interval)
    }
    return when (activeSegment?.kind) {
        SegmentKind.RAMP -> stringResource(R.string.session_workout_step_type_ramp)
        SegmentKind.STEADY -> stringResource(R.string.session_workout_step_type_steady)
        SegmentKind.FREERIDE -> stringResource(R.string.session_workout_step_type_free_ride)
        null -> runnerState.label ?: unknown
    }
}

private fun sessionTargetPowerLabel(
    runnerState: RunnerState,
    activeSegment: WorkoutProfileSegment?,
    ftpWatts: Int,
    fallbackTargetPower: Int?,
    unknown: String,
): String {
    if (runnerState.intervalPart != null) {
        return formatWatts(runnerState.targetPowerWatts ?: fallbackTargetPower, unknown)
    }
    if (activeSegment?.kind == SegmentKind.RAMP) {
        val startWatts = activeSegment.startPowerRelFtp?.let { relativePowerToWatts(it, ftpWatts) }
        val endWatts = activeSegment.endPowerRelFtp?.let { relativePowerToWatts(it, ftpWatts) }
        if (startWatts != null && endWatts != null) {
            return if (startWatts == endWatts) {
                formatWatts(startWatts, unknown)
            } else {
                "$startWatts -> $endWatts W"
            }
        }
    }
    val resolvedTarget = runnerState.targetPowerWatts
        ?: activeSegment?.startPowerRelFtp?.let { relativePowerToWatts(it, ftpWatts) }
        ?: fallbackTargetPower
    return formatWatts(resolvedTarget, unknown)
}

private fun relativePowerToWatts(relativePower: Double, ftpWatts: Int): Int {
    return (relativePower * ftpWatts.coerceAtLeast(1)).roundToInt()
}

private fun formatWatts(value: Int?, fallback: String): String {
    return value?.let { "$it W" } ?: fallback
}

/**
 * End-of-session summary UI.
 *
 * Summary values may be null when signals were not available during the session.
 */
@Composable
internal fun SummaryScreen(
    summary: SessionSummary?,
    onBackToMenu: () -> Unit
) {
    val unknown = stringResource(R.string.value_unknown)
    val summaryCardBorder = sessionCardBorder()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val layoutMode = resolveAdaptiveLayoutMode(width = maxWidth, height = maxHeight)
        val contentMaxWidth = if (layoutMode.isTwoPane()) {
            SummaryTwoPaneMaxContentWidth
        } else {
            SummaryMaxContentWidth
        }
        val summaryColumns = if (layoutMode.isTwoPane()) 2 else 1

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.summary_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                if (summary == null) {
                    SectionCard(
                        title = stringResource(R.string.summary_title),
                        border = summaryCardBorder,
                    ) {
                        Text(
                            text = stringResource(R.string.no_summary),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val summaryItems = listOf(
                        MetricItem(
                            stringResource(R.string.summary_duration),
                            formatTime(summary.durationSeconds, unknown)
                        ),
                        MetricItem(
                            stringResource(R.string.summary_actual_tss),
                            format1(summary.actualTss, unknown)
                        ),
                        MetricItem(
                            stringResource(R.string.summary_distance),
                            stringResource(
                                R.string.summary_distance_value,
                                format0(summary.distanceMeters, unknown)
                            )
                        ),
                        MetricItem(
                            stringResource(R.string.summary_total_calories),
                            stringResource(
                                R.string.summary_kcal_value,
                                format0(summary.totalEnergyKcal, unknown)
                            )
                        ),
                        MetricItem(
                            stringResource(R.string.summary_avg_power),
                            stringResource(
                                R.string.summary_power_value,
                                format0(summary.avgPower, unknown)
                            )
                        ),
                        MetricItem(
                            stringResource(R.string.summary_max_power),
                            stringResource(
                                R.string.summary_power_value,
                                format0(summary.maxPower, unknown)
                            )
                        ),
                        MetricItem(
                            stringResource(R.string.summary_avg_cadence),
                            stringResource(
                                R.string.summary_cadence_value,
                                format0(summary.avgCadence, unknown)
                            )
                        ),
                        MetricItem(
                            stringResource(R.string.summary_max_cadence),
                            stringResource(
                                R.string.summary_cadence_value,
                                format0(summary.maxCadence, unknown)
                            )
                        ),
                        MetricItem(
                            stringResource(R.string.summary_avg_hr),
                            stringResource(
                                R.string.summary_hr_value,
                                format0(summary.avgHeartRate, unknown)
                            )
                        ),
                        MetricItem(
                            stringResource(R.string.summary_max_hr),
                            stringResource(
                                R.string.summary_hr_value,
                                format0(summary.maxHeartRate, unknown)
                            )
                        )
                    )

                    SectionCard(
                        title = stringResource(R.string.summary_title),
                        border = summaryCardBorder,
                    ) {
                        MetricsGrid(
                            items = summaryItems,
                            columns = summaryColumns,
                            cardBorder = summaryCardBorder,
                        )
                    }
                }

                Button(
                    onClick = onBackToMenu,
                    modifier = if (layoutMode.isTwoPane()) {
                        Modifier.fillMaxWidth(0.5f).align(Alignment.CenterHorizontally)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    colors = disabledVisibleButtonColors()
                ) {
                    Text(stringResource(R.string.back_to_menu))
                }
            }
        }
    }
}

@Composable
private fun SessionIssuesSection(
    messages: List<String>
) {
    SectionCard(
        title = stringResource(R.string.session_issue_title),
        border = sessionCardBorder(),
    ) {
        messages.forEach { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun sessionStateLabel(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
): String {
    if (phase != SessionPhase.RUNNING) {
        return phaseLabel(phase)
    }
    if (isWaitingStartState(phase = phase, runnerState = runnerState, cadenceRpm = cadenceRpm)) {
        return stringResource(R.string.session_state_waiting)
    }
    return workoutStateLabel(runnerState)
}

private fun isWaitingStartState(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
): Boolean {
    if (phase != SessionPhase.RUNNING) return false
    val elapsedSec = runnerState.workoutElapsedSec ?: 0
    return !runnerState.running &&
        (cadenceRpm ?: 0.0) <= 0.0 &&
        elapsedSec == 0
}

@Composable
private fun intervalCountdownLabel(
    phase: IntervalPartPhase,
    remainingSec: Int,
): String {
    val phaseLabel = when (phase) {
        IntervalPartPhase.ON -> stringResource(R.string.session_workout_interval_phase_on)
        IntervalPartPhase.OFF -> stringResource(R.string.session_workout_interval_phase_off)
    }
    return stringResource(
        R.string.session_workout_interval_countdown,
        phaseLabel,
        remainingSec.coerceAtLeast(0),
    )
}

@Composable
private fun phaseLabel(phase: SessionPhase): String {
    return when (phase) {
        SessionPhase.IDLE -> stringResource(R.string.session_phase_idle)
        SessionPhase.RUNNING -> stringResource(R.string.session_phase_running)
        SessionPhase.STOPPED -> stringResource(R.string.session_phase_stopped)
    }
}

@Composable
private fun workoutStateLabel(runnerState: RunnerState): String {
    return when {
        runnerState.running && runnerState.paused ->
            stringResource(R.string.session_workout_state_paused)

        runnerState.running ->
            stringResource(R.string.session_workout_state_running)

        runnerState.done ->
            stringResource(R.string.session_workout_state_done)

        else ->
            stringResource(R.string.session_workout_state_stopped)
    }
}

@Composable
private fun MetricsGrid(
    items: List<MetricItem>,
    columns: Int,
    cardBorder: BorderStroke? = null,
) {
    if (columns <= 1) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                MetricCard(
                    label = item.label,
                    value = item.value,
                    modifier = Modifier.fillMaxWidth(),
                    border = cardBorder,
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { item ->
                    MetricCard(
                        label = item.label,
                        value = item.value,
                        modifier = Modifier.weight(1f),
                        border = cardBorder,
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    valueMaxLines: Int = Int.MAX_VALUE,
    border: BorderStroke? = null,
) {
    Card(
        modifier = modifier,
        border = border,
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = valueStyle,
                fontWeight = FontWeight.SemiBold,
                maxLines = valueMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = border,
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}

private fun format1(value: Double?, fallback: String): String {
    return value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: fallback
}

private fun format0(value: Int?, fallback: String): String {
    return value?.toString() ?: fallback
}

/**
 * Formats seconds as `mm:ss` for human readability.
 */
private fun formatTime(seconds: Int?, fallback: String): String {
    val safeSeconds = seconds ?: return fallback
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
}

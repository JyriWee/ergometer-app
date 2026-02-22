package com.example.ergometerapp.ui

import android.util.Log
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.R
import com.example.ergometerapp.DeviceSelectionKind
import com.example.ergometerapp.HrProfileSex
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
import kotlinx.coroutines.delay
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
private val SessionWorkoutChartHeightPhonePortrait = 260.dp

private data class MetricItem(
    val label: String,
    val value: String
)

private enum class DeviceConnectionIndicatorState {
    CONNECTED,
    IDLE,
    ISSUE,
}

private enum class SessionPortraitPreset {
    BALANCED,
    POWER_FIRST,
    WORKOUT_FIRST,
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
private fun menuStartButtonColors() = ButtonDefaults.buttonColors(
    containerColor = menuStartCtaColor(),
    contentColor = menuStartCtaContentColor(),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
)

@Composable
private fun sessionQuitButtonColors(emphasized: Boolean) = if (emphasized) {
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
} else {
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

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

@Composable
private fun WaitingStatusText(
    baseText: String,
    animateDots: Boolean,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
) {
    val normalizedBase = baseText.trimEnd().trimEnd('.', '…')
    if (!animateDots) {
        Text(
            text = normalizedBase,
            style = style,
            color = color,
            fontWeight = fontWeight,
            modifier = modifier,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap,
        )
        return
    }

    var dotsCount by remember(animateDots) { mutableIntStateOf(1) }
    LaunchedEffect(animateDots) {
        while (true) {
            delay(350)
            dotsCount = if (dotsCount >= 3) 1 else dotsCount + 1
        }
    }
    val displayText = buildAnnotatedString {
        append(normalizedBase)
        val dotsStart = length
        append("...")
        val hiddenDots = 3 - dotsCount.coerceIn(1, 3)
        if (hiddenDots > 0) {
            addStyle(
                style = SpanStyle(color = Color.Transparent),
                start = dotsStart + dotsCount,
                end = dotsStart + 3,
            )
        }
    }

    Text(
        text = displayText,
        style = style,
        color = color,
        fontWeight = fontWeight,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
    )
}

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
    hrProfileAge: Int?,
    hrProfileAgeInput: String,
    hrProfileAgeError: String?,
    hrProfileSex: HrProfileSex?,
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
    suggestOpenSettingsAfterConnectionIssue: Boolean,
    activeDeviceSelectionKind: DeviceSelectionKind?,
    scannedDevices: List<ScannedBleDevice>,
    deviceScanInProgress: Boolean,
    deviceScanStatus: String?,
    deviceScanStopEnabled: Boolean,
    startEnabled: Boolean,
    onSelectWorkoutFile: () -> Unit,
    onOpenWorkoutEditor: () -> Unit,
    onFtpInputChanged: (String) -> Unit,
    onHrProfileAgeInputChanged: (String) -> Unit,
    onHrProfileSexSelected: (HrProfileSex) -> Unit,
    onSearchFtmsDevices: () -> Unit,
    onSearchHrDevices: () -> Unit,
    onScannedDeviceSelected: (ScannedBleDevice) -> Unit,
    onDismissDeviceSelection: () -> Unit,
    onDismissConnectionIssue: () -> Unit,
    onSearchFtmsDevicesFromConnectionIssue: () -> Unit,
    onOpenAppSettingsFromConnectionIssue: () -> Unit,
    onStartSession: () -> Unit
) {
    val normalTextColor = menuNormalTextColor()
    val errorTextColor = menuErrorTextColor()
    val pickerStatusColor = menuPickerStatusColor()
    val pickerWarningColor = menuPickerWarningColor()
    val pickerNeutralColor = menuPickerNeutralColor()
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
    val startBlockedReasonText = if (!startEnabled) {
        val reasons = mutableListOf<String>()
        if (selectedWorkoutImportError != null) {
            reasons += stringResource(R.string.menu_start_blocked_fix_workout)
        } else if (selectedWorkout == null) {
            reasons += stringResource(R.string.menu_start_blocked_select_workout)
        }
        if (!ftmsSelected) {
            reasons += stringResource(R.string.menu_start_blocked_select_trainer)
        }
        if (workoutExecutionModeIsError) {
            reasons += stringResource(R.string.menu_start_blocked_execution)
        }
        if (reasons.isEmpty()) {
            stringResource(R.string.menu_start_blocked_generic)
        } else {
            reasons.joinToString(separator = " ")
        }
    } else {
        null
    }
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
    val hrProfileSexLabel = when (hrProfileSex) {
        HrProfileSex.MALE -> stringResource(R.string.menu_hr_profile_sex_male)
        HrProfileSex.FEMALE -> stringResource(R.string.menu_hr_profile_sex_female)
        null -> unknown
    }
    val hrProfileSummary = if (hrProfileAge != null && hrProfileSex != null) {
        stringResource(R.string.menu_hr_profile_summary_value, hrProfileAge, hrProfileSexLabel)
    } else {
        stringResource(R.string.menu_hr_profile_summary_missing)
    }
    val trainerDisplayName = ftmsDeviceName.ifBlank { stringResource(R.string.menu_device_not_selected) }
    val hrDisplayName = hrDeviceName.ifBlank { stringResource(R.string.menu_device_not_selected) }
    val trainerIndicatorState = when {
        ftmsConnected -> DeviceConnectionIndicatorState.CONNECTED
        ftmsSelected && ftmsConnectionKnown -> DeviceConnectionIndicatorState.ISSUE
        (suggestTrainerSearchAfterConnectionIssue || suggestOpenSettingsAfterConnectionIssue) &&
            !connectionIssueMessage.isNullOrBlank() -> {
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
        val layoutMode = rememberImeStableAdaptiveLayoutMode(width = maxWidth, height = maxHeight)
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

            SectionCard(title = stringResource(R.string.menu_hr_profile_title)) {
                Text(
                    text = hrProfileSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = normalTextColor,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = hrProfileAgeInput,
                        onValueChange = onHrProfileAgeInputChanged,
                        modifier = Modifier.weight(0.38f),
                        label = { Text(stringResource(R.string.menu_hr_profile_age_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = hrProfileAgeError != null,
                        colors = menuTextFieldColors(),
                    )
                    Row(
                        modifier = Modifier.weight(0.62f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HrProfileSexButton(
                            label = stringResource(R.string.menu_hr_profile_sex_male),
                            selected = hrProfileSex == HrProfileSex.MALE,
                            onClick = { onHrProfileSexSelected(HrProfileSex.MALE) },
                            modifier = Modifier.weight(1f),
                        )
                        HrProfileSexButton(
                            label = stringResource(R.string.menu_hr_profile_sex_female),
                            selected = hrProfileSex == HrProfileSex.FEMALE,
                            onClick = { onHrProfileSexSelected(HrProfileSex.FEMALE) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (hrProfileAgeError != null) {
                    Text(
                        text = hrProfileAgeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = errorTextColor,
                    )
                }
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
                    colors = menuStartButtonColors(),
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

                if (startBlockedReasonText != null) {
                    val blockedReasonPulseTransition =
                        rememberInfiniteTransition(label = "menuStartBlockedReasonPulse")
                    val blockedReasonAlpha = blockedReasonPulseTransition.animateFloat(
                        initialValue = 0.45f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 900,
                                easing = LinearEasing,
                            ),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "menuStartBlockedReasonAlpha",
                    ).value
                    Text(
                        text = startBlockedReasonText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = blockedReasonAlpha),
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

    val showConnectionIssueDialog =
        connectionIssueMessage != null &&
            (suggestTrainerSearchAfterConnectionIssue || suggestOpenSettingsAfterConnectionIssue)
    if (showConnectionIssueDialog) {
        AlertDialog(
            onDismissRequest = onDismissConnectionIssue,
            title = { Text(stringResource(R.string.menu_connection_issue_title)) },
            text = { Text(connectionIssueMessage) },
            confirmButton = {
                val confirmLabel = if (suggestOpenSettingsAfterConnectionIssue) {
                    stringResource(R.string.menu_connection_issue_open_settings)
                } else {
                    stringResource(R.string.menu_connection_issue_search_again)
                }
                val confirmAction = if (suggestOpenSettingsAfterConnectionIssue) {
                    onOpenAppSettingsFromConnectionIssue
                } else {
                    onSearchFtmsDevicesFromConnectionIssue
                }
                TextButton(onClick = confirmAction) {
                    Text(confirmLabel)
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
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WaitingStatusText(
                baseText = stringResource(R.string.status_connecting),
                animateDots = true,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
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
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WaitingStatusText(
                baseText = stringResource(R.string.status_stopping),
                animateDots = true,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
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
    hrProfileAge: Int?,
    hrProfileSex: HrProfileSex?,
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
    val powerTargetValue = stringResource(
        R.string.session_power_target_value,
        format0(bikeData?.instantaneousPowerW, unknown),
        format0(runnerState.targetPowerWatts ?: lastTargetPower, unknown),
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
    val workoutDescription = selectedWorkout?.description
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: unknown
    val showWorkoutInfoDialog = rememberSaveable { mutableStateOf(false) }
    val hrZoneValue = sessionHeartRateZoneLabel(
        currentHeartRate = effectiveHr,
        profileAge = hrProfileAge,
        profileSex = hrProfileSex,
        unknown = unknown,
        missingProfile = stringResource(R.string.session_hr_zone_set_profile),
    )
    val sessionIssues = buildList {
        if (!ftmsReady) {
            add(stringResource(R.string.session_issue_ftms_not_ready))
        }
        if (ftmsReady && !ftmsControlGranted) {
            add(stringResource(R.string.session_issue_control_missing))
        }
    }
    val portraitPresetState = rememberSaveable {
        mutableStateOf(SessionPortraitPreset.BALANCED)
    }
    // Reserved for future parser-driven workout text events shown in the shared status field.
    val sessionTextEventMessage: String? = null

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val layoutMode = resolveAdaptiveLayoutMode(width = maxWidth, height = maxHeight)
        // Keep session content in one column in portrait for better scan order.
        val isPortrait = maxHeight >= maxWidth
        val widthClass = resolveAdaptiveWidthClass(maxWidth)
        val isPhonePortrait = isPortrait && widthClass == AdaptiveWidthClass.COMPACT
        val isPhoneLandscape = !isPortrait && maxHeight < 500.dp
        val showTwoPane = layoutMode.isTwoPane() && !isPortrait
        val paneWeights = layoutMode.paneWeights()
        val compactTopMetrics = !showTwoPane && maxWidth < SessionTopMetricsCompactWidth
        val sectionHorizontalPadding = if (isPhonePortrait) 12.dp else 20.dp
        val sessionTopPadding = if (isPhonePortrait) 16.dp else if (isPortrait) 28.dp else 16.dp
        val endSessionButtonWidth = if (isPhonePortrait) 0.9f else 0.5f
        val waitingForStart = isWaitingStartState(
            phase = phase,
            runnerState = runnerState,
            cadenceRpm = cadenceRpm,
        )
        val endSessionCtaEmphasized = phase == SessionPhase.RUNNING && !waitingForStart

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
                        .padding(horizontal = sectionHorizontalPadding)
                        .padding(top = sessionTopPadding, bottom = 16.dp)
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
                                    speedValue = speedValue,
                                    kcalValue = kcalValue,
                                    distanceValue = distanceValue,
                                    workoutRemainingValue = remainingText,
                                    elapsedOfTotalValue = elapsedOfTotalText,
                                )
                                PrimaryTelemetrySection(
                                    heartRateValue = heartRateValue,
                                    powerTargetValue = powerTargetValue,
                                    cadenceTargetValue = cadenceTargetValue,
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
                                    onOpenWorkoutInfo = { showWorkoutInfoDialog.value = true },
                                    statusOverrideMessage = sessionTextEventMessage,
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
                                    showTimingMetrics = false,
                                )
                            }
                        }
                    } else {
                        if (isPhonePortrait) {
                            PhonePortraitSessionWorkoutCard(
                                onOpenWorkoutInfo = { showWorkoutInfoDialog.value = true },
                                onEndSession = onEndSession,
                                endSessionEnabled = phase == SessionPhase.RUNNING,
                                endSessionCtaEmphasized = endSessionCtaEmphasized,
                                statusOverrideMessage = sessionTextEventMessage,
                                sessionIssues = sessionIssues,
                                phase = phase,
                                runnerState = runnerState,
                                cadenceRpm = cadenceRpm,
                                heartRateValue = heartRateValue,
                                powerTargetValue = powerTargetValue,
                                cadenceTargetValue = cadenceTargetValue,
                                speedValue = speedValue,
                                kcalValue = kcalValue,
                                distanceValue = distanceValue,
                                hrZoneValue = hrZoneValue,
                                elapsedOfTotalText = elapsedOfTotalText,
                                selectedWorkout = selectedWorkout,
                                ftpWatts = ftpWatts,
                                workoutElapsedSec = runnerState.workoutElapsedSec,
                                currentTargetWatts = runnerState.targetPowerWatts ?: lastTargetPower,
                                workoutExecutionModeMessage = workoutExecutionModeMessage,
                                workoutExecutionModeIsError = workoutExecutionModeIsError,
                            )
                        } else if (isPhoneLandscape) {
                            PhoneLandscapeSessionWorkoutCard(
                                onOpenWorkoutInfo = { showWorkoutInfoDialog.value = true },
                                onEndSession = onEndSession,
                                statusOverrideMessage = sessionTextEventMessage,
                                endSessionEnabled = phase == SessionPhase.RUNNING,
                                endSessionCtaEmphasized = endSessionCtaEmphasized,
                                heartRateValue = heartRateValue,
                                powerTargetValue = powerTargetValue,
                                cadenceTargetValue = cadenceTargetValue,
                                elapsedOfTotalText = elapsedOfTotalText,
                                speedValue = speedValue,
                                distanceValue = distanceValue,
                                kcalValue = kcalValue,
                                hrZoneValue = hrZoneValue,
                                sessionIssues = sessionIssues,
                                phase = phase,
                                runnerState = runnerState,
                                cadenceRpm = cadenceRpm,
                                workoutExecutionModeMessage = workoutExecutionModeMessage,
                                workoutExecutionModeIsError = workoutExecutionModeIsError,
                                selectedWorkout = selectedWorkout,
                                ftpWatts = ftpWatts,
                                workoutElapsedSec = runnerState.workoutElapsedSec,
                                currentTargetWatts = runnerState.targetPowerWatts ?: lastTargetPower,
                            )
                        } else {
                            val showPortraitPresetSelector =
                                phase != SessionPhase.RUNNING ||
                                    isWaitingStartState(
                                        phase = phase,
                                        runnerState = runnerState,
                                        cadenceRpm = cadenceRpm,
                                    )
                            if (showPortraitPresetSelector) {
                                SessionPresetSelector(
                                    selectedPreset = portraitPresetState.value,
                                    onPresetSelected = { selected -> portraitPresetState.value = selected },
                                    phonePortraitLayout = isPhonePortrait,
                                )
                            }

                            when (portraitPresetState.value) {
                                SessionPortraitPreset.BALANCED -> {
                                    TopTelemetrySection(
                                        compactLayout = compactTopMetrics,
                                        phonePortraitLayout = isPhonePortrait,
                                        speedValue = speedValue,
                                        kcalValue = kcalValue,
                                        distanceValue = distanceValue,
                                        workoutRemainingValue = remainingText,
                                        elapsedOfTotalValue = elapsedOfTotalText,
                                    )

                                    PrimaryTelemetrySection(
                                        heartRateValue = heartRateValue,
                                        powerTargetValue = powerTargetValue,
                                        cadenceTargetValue = cadenceTargetValue,
                                        phonePortraitLayout = isPhonePortrait,
                                    )

                                    WorkoutProgressSection(
                                        selectedWorkout = selectedWorkout,
                                        onOpenWorkoutInfo = { showWorkoutInfoDialog.value = true },
                                        statusOverrideMessage = sessionTextEventMessage,
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
                                        showTimingMetrics = false,
                                        phonePortraitLayout = isPhonePortrait,
                                    )
                                }

                                SessionPortraitPreset.POWER_FIRST -> {
                                    PrimaryTelemetrySection(
                                        heartRateValue = heartRateValue,
                                        powerTargetValue = powerTargetValue,
                                        cadenceTargetValue = cadenceTargetValue,
                                        phonePortraitLayout = isPhonePortrait,
                                    )

                                    TopTelemetrySection(
                                        compactLayout = compactTopMetrics,
                                        phonePortraitLayout = isPhonePortrait,
                                        speedValue = speedValue,
                                        kcalValue = kcalValue,
                                        distanceValue = distanceValue,
                                        workoutRemainingValue = remainingText,
                                        elapsedOfTotalValue = elapsedOfTotalText,
                                    )

                                    WorkoutProgressSection(
                                        selectedWorkout = selectedWorkout,
                                        onOpenWorkoutInfo = { showWorkoutInfoDialog.value = true },
                                        statusOverrideMessage = sessionTextEventMessage,
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
                                        showTimingMetrics = false,
                                        phonePortraitLayout = isPhonePortrait,
                                    )
                                }

                                SessionPortraitPreset.WORKOUT_FIRST -> {
                                    WorkoutProgressSection(
                                        selectedWorkout = selectedWorkout,
                                        onOpenWorkoutInfo = { showWorkoutInfoDialog.value = true },
                                        statusOverrideMessage = sessionTextEventMessage,
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
                                        showTimingMetrics = false,
                                        phonePortraitLayout = isPhonePortrait,
                                    )

                                    TopTelemetrySection(
                                        compactLayout = compactTopMetrics,
                                        phonePortraitLayout = isPhonePortrait,
                                        speedValue = speedValue,
                                        kcalValue = kcalValue,
                                        distanceValue = distanceValue,
                                        workoutRemainingValue = remainingText,
                                        elapsedOfTotalValue = elapsedOfTotalText,
                                    )

                                    PrimaryTelemetrySection(
                                        heartRateValue = heartRateValue,
                                        powerTargetValue = powerTargetValue,
                                        cadenceTargetValue = cadenceTargetValue,
                                        phonePortraitLayout = isPhonePortrait,
                                    )
                                }
                            }
                            if (sessionIssues.isNotEmpty()) {
                                SessionIssuesSection(messages = sessionIssues)
                            }
                        }
                    }
                }
            }

            if (!isPhoneLandscape && !isPhonePortrait) {
                Button(
                    onClick = onEndSession,
                    enabled = phase == SessionPhase.RUNNING,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(endSessionButtonWidth)
                        .padding(vertical = 16.dp),
                    colors = sessionQuitButtonColors(emphasized = endSessionCtaEmphasized)
                ) {
                    Text(stringResource(R.string.btn_quit_session_now))
                }
            }
        }
    }

    if (showWorkoutInfoDialog.value) {
        AlertDialog(
            onDismissRequest = { showWorkoutInfoDialog.value = false },
            title = { Text(stringResource(R.string.session_workout_info_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.session_workout_info_name, workoutName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.session_workout_info_description, workoutDescription),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showWorkoutInfoDialog.value = false }) {
                    Text(stringResource(R.string.menu_dialog_ok))
                }
            },
        )
    }
}

@Composable
private fun PhonePortraitSessionWorkoutCard(
    onOpenWorkoutInfo: () -> Unit,
    onEndSession: () -> Unit,
    endSessionEnabled: Boolean,
    endSessionCtaEmphasized: Boolean,
    statusOverrideMessage: String?,
    sessionIssues: List<String>,
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    heartRateValue: String,
    powerTargetValue: String,
    cadenceTargetValue: String,
    speedValue: String,
    kcalValue: String,
    distanceValue: String,
    hrZoneValue: String,
    elapsedOfTotalText: String,
    selectedWorkout: WorkoutFile?,
    ftpWatts: Int,
    workoutElapsedSec: Int?,
    currentTargetWatts: Int?,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
) {
    val cardBorder = sessionCardBorder()

    SectionCard(title = null, border = cardBorder) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionInfoButton(
                onClick = onOpenWorkoutInfo,
                modifier = Modifier,
            )
            Button(
                onClick = onEndSession,
                enabled = endSessionEnabled,
                colors = sessionQuitButtonColors(emphasized = endSessionCtaEmphasized),
                modifier = Modifier.height(36.dp),
            ) {
                Text(
                    text = stringResource(R.string.btn_quit_session_now),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        SessionInlineMetricsRow(
            leftLabel = stringResource(R.string.session_hr_short_label),
            leftValue = heartRateValue,
            rightLabel = stringResource(R.string.session_power_target_label),
            rightValue = powerTargetValue,
            leftValueScale = 1.3f,
            rightValueScale = 1.3f,
        )
        SessionInlineMetricsRow(
            leftLabel = stringResource(R.string.session_hr_zone_label),
            leftValue = hrZoneValue,
            rightLabel = stringResource(R.string.session_cadence_target_label),
            rightValue = cadenceTargetValue,
        )

        sessionIssues.forEach { issue ->
            Text(
                text = issue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SessionInlineMetricsRow(
            leftLabel = stringResource(R.string.session_elapsed_of_total),
            leftValue = elapsedOfTotalText,
            rightLabel = stringResource(R.string.summary_distance),
            rightValue = distanceValue,
        )
        SessionInlineMetricsRow(
            leftLabel = stringResource(R.string.session_speed_label),
            leftValue = speedValue,
            rightLabel = stringResource(R.string.session_kcal_label),
            rightValue = kcalValue,
        )

        val stateMessage = sessionStateLabel(
            phase = phase,
            runnerState = runnerState,
            cadenceRpm = cadenceRpm,
        )
        val waitingForUserAction = isSessionWaitingForUserActionState(
            phase = phase,
            runnerState = runnerState,
            cadenceRpm = cadenceRpm,
        )
        val statusMessage = resolveSessionStatusMessage(
            overrideMessage = statusOverrideMessage,
            fallbackMessage = stateMessage,
        )
        val animateStatusDots = shouldAnimateSessionStatusDots(
            overrideMessage = statusOverrideMessage,
            waitingForUserAction = waitingForUserAction,
        )
        Spacer(modifier = Modifier.height(10.dp))
        WaitingStatusText(
            baseText = statusMessage,
            animateDots = animateStatusDots,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp, max = 96.dp)
                .animateContentSize(),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (!workoutExecutionModeMessage.isNullOrBlank()) {
            Text(
                text = workoutExecutionModeMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (workoutExecutionModeIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        if (selectedWorkout != null) {
            WorkoutProfileChart(
                workout = selectedWorkout,
                ftpWatts = ftpWatts,
                elapsedSec = workoutElapsedSec,
                currentTargetWatts = currentTargetWatts,
                chartHeight = SessionWorkoutChartHeightPhonePortrait
            )
        }
    }
}

@Composable
private fun PhoneLandscapeSessionWorkoutCard(
    onOpenWorkoutInfo: () -> Unit,
    onEndSession: () -> Unit,
    statusOverrideMessage: String?,
    endSessionEnabled: Boolean,
    endSessionCtaEmphasized: Boolean,
    heartRateValue: String,
    powerTargetValue: String,
    cadenceTargetValue: String,
    elapsedOfTotalText: String,
    speedValue: String,
    distanceValue: String,
    kcalValue: String,
    hrZoneValue: String,
    sessionIssues: List<String>,
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
    selectedWorkout: WorkoutFile?,
    ftpWatts: Int,
    workoutElapsedSec: Int?,
    currentTargetWatts: Int?,
) {
    val cardBorder = sessionCardBorder()
    val stateMessage = sessionStateLabel(
        phase = phase,
        runnerState = runnerState,
        cadenceRpm = cadenceRpm,
    )
    val waitingForUserAction = isSessionWaitingForUserActionState(
        phase = phase,
        runnerState = runnerState,
        cadenceRpm = cadenceRpm,
    )
    val statusMessage = resolveSessionStatusMessage(
        overrideMessage = statusOverrideMessage,
        fallbackMessage = stateMessage,
    )
    val animateStatusDots = shouldAnimateSessionStatusDots(
        overrideMessage = statusOverrideMessage,
        waitingForUserAction = waitingForUserAction,
    )
    SectionCard(title = null, border = cardBorder) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionInfoButton(
                onClick = onOpenWorkoutInfo,
                modifier = Modifier,
            )
            WaitingStatusText(
                baseText = statusMessage,
                animateDots = animateStatusDots,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 24.dp, max = 64.dp)
                    .animateContentSize(),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                onClick = onEndSession,
                enabled = endSessionEnabled,
                colors = sessionQuitButtonColors(emphasized = endSessionCtaEmphasized),
                modifier = Modifier.height(36.dp),
            ) {
                Text(
                    text = stringResource(R.string.btn_quit_session_now),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (!workoutExecutionModeMessage.isNullOrBlank()) {
            Text(
                text = workoutExecutionModeMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (workoutExecutionModeIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionInlineMetric(
                    label = stringResource(R.string.session_hr_short_label),
                    value = heartRateValue,
                )
                SessionInlineMetric(
                    label = stringResource(R.string.session_hr_zone_label),
                    value = hrZoneValue,
                )
                SessionInlineMetric(
                    label = stringResource(R.string.session_power_target_label),
                    value = powerTargetValue,
                )
                SessionInlineMetric(
                    label = stringResource(R.string.session_cadence_target_label),
                    value = cadenceTargetValue,
                )
            }

            Box(
                modifier = Modifier.weight(2.7f),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (selectedWorkout != null) {
                    WorkoutProfileChart(
                        workout = selectedWorkout,
                        ftpWatts = ftpWatts,
                        elapsedSec = workoutElapsedSec,
                        currentTargetWatts = currentTargetWatts,
                        chartHeight = SessionWorkoutChartHeight,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionInlineMetric(
                    label = stringResource(R.string.session_elapsed_of_total),
                    value = elapsedOfTotalText,
                )
                SessionInlineMetric(
                    label = stringResource(R.string.session_speed_label),
                    value = speedValue,
                )
                SessionInlineMetric(
                    label = stringResource(R.string.summary_distance),
                    value = distanceValue,
                )
                SessionInlineMetric(
                    label = stringResource(R.string.session_kcal_label),
                    value = kcalValue,
                )
            }
        }

        sessionIssues.forEach { issue ->
            Text(
                text = issue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SessionInlineMetricsRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    leftValueScale: Float = 1f,
    rightValueScale: Float = 1f,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SessionInlineMetric(
            label = leftLabel,
            value = leftValue,
            modifier = Modifier.weight(1f),
            valueScale = leftValueScale,
        )
        SessionInlineMetric(
            label = rightLabel,
            value = rightValue,
            modifier = Modifier.weight(1f),
            valueScale = rightValueScale,
        )
    }
}

@Composable
private fun SessionInlineMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueScale: Float = 1f,
) {
    val safeScale = valueScale.coerceAtLeast(0.8f)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = MaterialTheme.typography.labelMedium.fontSize * safeScale,
        lineHeight = MaterialTheme.typography.labelMedium.lineHeight * safeScale,
    )
    val valueStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = MaterialTheme.typography.titleMedium.fontSize * safeScale,
        lineHeight = MaterialTheme.typography.titleMedium.lineHeight * safeScale,
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = valueStyle,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SessionPresetSelector(
    selectedPreset: SessionPortraitPreset,
    onPresetSelected: (SessionPortraitPreset) -> Unit,
    phonePortraitLayout: Boolean = false,
) {
    val hasUserSelectedPreset = rememberSaveable { mutableStateOf(false) }
    val expanded = rememberSaveable { mutableStateOf(true) }
    val cardBorder = sessionCardBorder()
    val balancedLabel = stringResource(R.string.session_layout_preset_balanced)
    val powerFirstLabel = stringResource(R.string.session_layout_preset_power_first)
    val workoutFirstLabel = stringResource(R.string.session_layout_preset_workout_first)
    val selectedPresetLabel = when (selectedPreset) {
        SessionPortraitPreset.BALANCED -> balancedLabel
        SessionPortraitPreset.POWER_FIRST -> powerFirstLabel
        SessionPortraitPreset.WORKOUT_FIRST -> workoutFirstLabel
    }
    val onPresetClick: (SessionPortraitPreset) -> Unit = { preset ->
        onPresetSelected(preset)
        hasUserSelectedPreset.value = true
        expanded.value = false
    }
    val showCompactSelector = hasUserSelectedPreset.value && !expanded.value

    if (showCompactSelector) {
        SectionCard(title = null, border = cardBorder) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.session_layout_preset_selected_value,
                        selectedPresetLabel,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { expanded.value = true }) {
                    Text(text = stringResource(R.string.session_layout_preset_change))
                }
            }
        }
        return
    }

    SectionCard(
        title = stringResource(R.string.session_layout_preset_title),
        border = cardBorder,
    ) {
        if (phonePortraitLayout) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionPresetButton(
                    label = balancedLabel,
                    selected = selectedPreset == SessionPortraitPreset.BALANCED,
                    onClick = { onPresetClick(SessionPortraitPreset.BALANCED) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SessionPresetButton(
                    label = powerFirstLabel,
                    selected = selectedPreset == SessionPortraitPreset.POWER_FIRST,
                    onClick = { onPresetClick(SessionPortraitPreset.POWER_FIRST) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SessionPresetButton(
                    label = workoutFirstLabel,
                    selected = selectedPreset == SessionPortraitPreset.WORKOUT_FIRST,
                    onClick = { onPresetClick(SessionPortraitPreset.WORKOUT_FIRST) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionPresetButton(
                    label = balancedLabel,
                    selected = selectedPreset == SessionPortraitPreset.BALANCED,
                    onClick = { onPresetClick(SessionPortraitPreset.BALANCED) },
                    modifier = Modifier.weight(1f),
                )
                SessionPresetButton(
                    label = powerFirstLabel,
                    selected = selectedPreset == SessionPortraitPreset.POWER_FIRST,
                    onClick = { onPresetClick(SessionPortraitPreset.POWER_FIRST) },
                    modifier = Modifier.weight(1f),
                )
                SessionPresetButton(
                    label = workoutFirstLabel,
                    selected = selectedPreset == SessionPortraitPreset.WORKOUT_FIRST,
                    onClick = { onPresetClick(SessionPortraitPreset.WORKOUT_FIRST) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SessionPresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(text = label, maxLines = 1)
        }
        return
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(text = label, maxLines = 1)
    }
}

@Composable
private fun HrProfileSexButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(40.dp),
            colors = menuSecondaryButtonColors(),
        ) {
            Text(text = label, maxLines = 1)
        }
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
    ) {
        Text(text = label, maxLines = 1)
    }
}

@Composable
private fun SessionInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = stringResource(R.string.session_workout_info_content_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopTelemetrySection(
    compactLayout: Boolean,
    phonePortraitLayout: Boolean = false,
    speedValue: String,
    kcalValue: String,
    distanceValue: String,
    workoutRemainingValue: String,
    elapsedOfTotalValue: String,
) {
    val cardBorder = sessionCardBorder()
    val metricSpacing = if (compactLayout) 8.dp else 10.dp
    SectionCard(title = null, border = cardBorder) {
        if (phonePortraitLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metricSpacing),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_speed_label),
                    value = speedValue,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metricSpacing),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.summary_distance),
                    value = distanceValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_workout_remaining),
                    value = workoutRemainingValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
            }
            TopMetricCard(
                label = stringResource(R.string.session_elapsed_of_total),
                value = elapsedOfTotalValue,
                modifier = Modifier.fillMaxWidth(),
                border = cardBorder,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metricSpacing),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_speed_label),
                    value = speedValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_kcal_label),
                    value = kcalValue,
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
                horizontalArrangement = Arrangement.spacedBy(metricSpacing),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_workout_remaining),
                    value = workoutRemainingValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_elapsed_of_total),
                    value = elapsedOfTotalValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
            }
        }
    }
}

@Composable
private fun PrimaryTelemetrySection(
    heartRateValue: String,
    powerTargetValue: String,
    cadenceTargetValue: String,
    phonePortraitLayout: Boolean = false,
) {
    val cardBorder = sessionCardBorder()
    SectionCard(title = null, border = cardBorder) {
        if (phonePortraitLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_hr_short_label),
                    value = heartRateValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                    emphasized = true,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_power_target_label),
                    value = powerTargetValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                    emphasized = true,
                )
            }
            TopMetricCard(
                label = stringResource(R.string.session_cadence_target_label),
                value = cadenceTargetValue,
                modifier = Modifier.fillMaxWidth(),
                border = cardBorder,
                emphasized = true,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_hr_short_label),
                    value = heartRateValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                    emphasized = true,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_power_target_label),
                    value = powerTargetValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                    emphasized = true,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_cadence_target_label),
                    value = cadenceTargetValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                    emphasized = true,
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
    emphasized: Boolean = false,
) {
    val emphasizedScale = 1.2f
    val labelStyle = if (emphasized) {
        MaterialTheme.typography.titleSmall.copy(
            fontSize = MaterialTheme.typography.titleSmall.fontSize * emphasizedScale,
            lineHeight = MaterialTheme.typography.titleSmall.lineHeight * emphasizedScale,
        )
    } else {
        MaterialTheme.typography.labelMedium
    }
    val valueStyle = if (emphasized) {
        MaterialTheme.typography.headlineSmall.copy(
            fontSize = MaterialTheme.typography.headlineSmall.fontSize * emphasizedScale,
            lineHeight = MaterialTheme.typography.headlineSmall.lineHeight * emphasizedScale,
        )
    } else {
        MaterialTheme.typography.titleMedium
    }
    val labelColor = if (emphasized) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val valueWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold
    val verticalPadding = if (emphasized) 14.dp else 9.dp

    Card(
        modifier = modifier,
        border = border,
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                style = labelStyle,
                color = labelColor
            )
            Text(
                text = value,
                style = valueStyle,
                fontWeight = valueWeight
            )
        }
    }
}

@Composable
private fun WorkoutProgressSection(
    selectedWorkout: WorkoutFile?,
    onOpenWorkoutInfo: () -> Unit,
    statusOverrideMessage: String?,
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
    showTimingMetrics: Boolean = true,
    phonePortraitLayout: Boolean = false,
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
        Box(modifier = Modifier.fillMaxWidth()) {
            SessionInfoButton(
                onClick = onOpenWorkoutInfo,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        if (showTimingMetrics) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
        }

        if (phonePortraitLayout) {
            MetricCard(
                label = stringResource(R.string.session_workout_step_type),
                value = sessionStepTypeLabel(
                    phase = phase,
                    runnerState = runnerState,
                    cadenceRpm = cadenceRpm,
                    activeSegment = activeSegment,
                    unknown = unknown,
                ),
                modifier = Modifier.fillMaxWidth(),
                border = cardBorder,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    label = stringResource(R.string.session_workout_step_remaining),
                    value = formatTime(runnerState.stepRemainingSec, unknown),
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
        } else {
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
                    label = stringResource(R.string.session_workout_step_remaining),
                    value = formatTime(runnerState.stepRemainingSec, unknown),
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
        }

        val stateMessage = sessionStateLabel(
            phase = phase,
            runnerState = runnerState,
            cadenceRpm = cadenceRpm,
        )
        val waitingForUserAction = isSessionWaitingForUserActionState(
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
        val statusMessage = resolveSessionStatusMessage(
            overrideMessage = statusOverrideMessage,
            fallbackMessage = stateMessage,
        )
        val statusMessageWithInterval = if (
            statusOverrideMessage.isNullOrBlank() &&
            !intervalMessage.isNullOrBlank()
        ) {
            "$statusMessage • $intervalMessage"
        } else {
            statusMessage
        }
        val animateStatusDots = shouldAnimateSessionStatusDots(
            overrideMessage = statusOverrideMessage,
            waitingForUserAction = waitingForUserAction,
        )
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
        WaitingStatusText(
            baseText = statusMessageWithInterval,
            animateDots = animateStatusDots,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp, max = 64.dp)
                .animateContentSize(),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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

private data class HrZoneRange(
    val zone: Int,
    val minBpm: Int,
    val maxBpm: Int,
)

@Composable
private fun sessionHeartRateZoneLabel(
    currentHeartRate: Int?,
    profileAge: Int?,
    profileSex: HrProfileSex?,
    unknown: String,
    missingProfile: String,
): String {
    if (profileAge == null || profileSex == null) {
        return missingProfile
    }
    val hr = currentHeartRate?.takeIf { it > 0 } ?: return unknown
    val estimatedMaxHr = estimatedMaxHeartRate(age = profileAge, sex = profileSex)
    val ranges = heartRateZoneRanges(maxHeartRate = estimatedMaxHr)
    val matched = ranges.firstOrNull { hr in it.minBpm..it.maxBpm } ?: ranges.last()
    return stringResource(
        R.string.session_hr_zone_value,
        matched.zone,
        matched.minBpm,
        matched.maxBpm,
    )
}

private fun estimatedMaxHeartRate(age: Int, sex: HrProfileSex): Int {
    val clampedAge = age.coerceIn(13, 100)
    val max = when (sex) {
        HrProfileSex.MALE -> 208.0 - (0.7 * clampedAge)
        HrProfileSex.FEMALE -> 206.0 - (0.88 * clampedAge)
    }
    return max.roundToInt().coerceIn(120, 220)
}

private fun heartRateZoneRanges(maxHeartRate: Int): List<HrZoneRange> {
    val maxHr = maxHeartRate.coerceIn(120, 220)
    val percentages = listOf(
        0.50 to 0.60,
        0.60 to 0.70,
        0.70 to 0.80,
        0.80 to 0.90,
        0.90 to 1.00,
    )
    return percentages.mapIndexed { index, (minPercent, maxPercent) ->
        val minBpm = (maxHr * minPercent).roundToInt()
        val maxBpm = (maxHr * maxPercent).roundToInt()
        HrZoneRange(
            zone = index + 1,
            minBpm = minBpm,
            maxBpm = maxBpm,
        )
    }
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
    fitExportStatusMessage: String?,
    fitExportStatusIsError: Boolean,
    onRequestFitExport: () -> Unit,
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

                if (!fitExportStatusMessage.isNullOrBlank()) {
                    Text(
                        text = fitExportStatusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (fitExportStatusIsError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                val buttonModifier = if (layoutMode.isTwoPane()) {
                    Modifier.fillMaxWidth(0.7f).align(Alignment.CenterHorizontally)
                } else {
                    Modifier.fillMaxWidth()
                }

                Row(
                    modifier = buttonModifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onRequestFitExport,
                        enabled = summary != null,
                        modifier = Modifier.weight(1f),
                        colors = disabledVisibleButtonColors(),
                    ) {
                        Text(stringResource(R.string.summary_export_fit))
                    }
                    Button(
                        onClick = onBackToMenu,
                        modifier = Modifier.weight(1f),
                        colors = disabledVisibleButtonColors()
                    ) {
                        Text(stringResource(R.string.back_to_menu))
                    }
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
    return elapsedSec == 0 &&
        (cadenceRpm ?: 0.0) <= 0.0
}

private fun isSessionWaitingForUserActionState(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
): Boolean {
    if (isWaitingStartState(phase = phase, runnerState = runnerState, cadenceRpm = cadenceRpm)) {
        return true
    }
    return phase == SessionPhase.RUNNING &&
        runnerState.running &&
        runnerState.paused
}

private fun resolveSessionStatusMessage(
    overrideMessage: String?,
    fallbackMessage: String,
): String {
    return overrideMessage
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: fallbackMessage
}

private fun shouldAnimateSessionStatusDots(
    overrideMessage: String?,
    waitingForUserAction: Boolean,
): Boolean {
    return overrideMessage.isNullOrBlank() && waitingForUserAction
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

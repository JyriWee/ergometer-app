package com.example.ergometerapp.ui

import android.util.Log
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.R
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.ui.components.WorkoutProfileChart
import com.example.ergometerapp.ui.components.buildWorkoutProfileSegments
import com.example.ergometerapp.ui.components.disabledVisibleButtonColors
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.runner.IntervalPartPhase
import com.example.ergometerapp.workout.runner.RunnerState
import java.util.Locale

private val WideLayoutMinWidth = 900.dp
private val PrimaryMetricsStackWidth = 560.dp
private val MenuMaxContentWidth = 560.dp
private val SessionMaxContentWidth = 1200.dp
private val SummaryMaxContentWidth = 920.dp
private val SessionStickyActionBottomPadding = 96.dp
private val SessionWorkoutChartHeight = 220.dp

private data class MetricItem(
    val label: String,
    val value: String
)

/**
 * Entry screen for starting a session.
 *
 * The start action is gated on successful workout import so runner execution
 * always starts from a validated file-backed workout definition.
 */
@Composable
internal fun MenuScreen(
    selectedWorkoutFileName: String?,
    selectedWorkoutStepCount: Int?,
    selectedWorkoutImportError: String?,
    selectedWorkout: WorkoutFile?,
    ftpWatts: Int,
    ftpInputText: String,
    ftpInputError: String?,
    startEnabled: Boolean,
    onSelectWorkoutFile: () -> Unit,
    onFtpInputChanged: (String) -> Unit,
    onStartSession: () -> Unit
) {
    val statusText =
        when {
            selectedWorkoutImportError != null -> {
                stringResource(R.string.menu_workout_import_failed, selectedWorkoutImportError)
            }
            selectedWorkoutFileName != null && selectedWorkoutStepCount != null -> {
                stringResource(
                    R.string.menu_workout_selected,
                    selectedWorkoutFileName,
                    selectedWorkoutStepCount
                )
            }
            else -> stringResource(R.string.menu_workout_not_selected)
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = MenuMaxContentWidth),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.menu_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = stringResource(R.string.menu_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onSelectWorkoutFile,
                    modifier = Modifier.fillMaxWidth(),
                    colors = disabledVisibleButtonColors()
                ) {
                    Text(stringResource(R.string.menu_select_workout_file))
                }

                OutlinedTextField(
                    value = ftpInputText,
                    onValueChange = onFtpInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.menu_ftp_label)) },
                    placeholder = { Text(stringResource(R.string.menu_ftp_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = ftpInputError != null,
                )

                Text(
                    text = ftpInputError ?: stringResource(R.string.menu_ftp_hint, ftpWatts),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ftpInputError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selectedWorkout != null) {
                    SectionCard(title = stringResource(R.string.session_workout_title)) {
                        WorkoutProfileChart(
                            workout = selectedWorkout,
                            ftpWatts = ftpWatts,
                        )
                    }
                }

                Button(
                    onClick = onStartSession,
                    enabled = startEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = disabledVisibleButtonColors()
                ) {
                    Text(stringResource(R.string.menu_start_session))
                }
            }
        }
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
    ftpWatts: Int,
    runnerState: RunnerState,
    lastTargetPower: Int?,
    onEndSession: () -> Unit
) {
    LaunchedEffect(bikeData) {
        Log.d("UI", "SessionScreen bikeData updated: $bikeData")
    }

    val unknown = stringResource(R.string.value_unknown)
    val effectiveHr = heartRate ?: bikeData?.heartRateBpm
    val totalWorkoutSec = remember(selectedWorkout) {
        selectedWorkout?.let { workout ->
            buildWorkoutProfileSegments(workout).sumOf { it.durationSec }
        }
    }
    val elapsedWorkoutSec = runnerState.workoutElapsedSec?.coerceAtLeast(0) ?: 0
    val remainingWorkoutSec = totalWorkoutSec?.let { total ->
        (total - elapsedWorkoutSec).coerceAtLeast(0)
    }
    val workoutComplete = totalWorkoutSec != null &&
        totalWorkoutSec > 0 &&
        remainingWorkoutSec == 0

    val cadenceRpm = bikeData?.instantaneousCadenceRpm

    val secondaryMetrics = listOf(
        MetricItem(
            label = stringResource(R.string.session_cadence_label),
            value = stringResource(
                R.string.session_cadence_value,
                format1(cadenceRpm, unknown)
            )
        ),
        MetricItem(
            label = stringResource(R.string.session_speed_label),
            value = stringResource(
                R.string.session_speed_value,
                format1(bikeData?.instantaneousSpeedKmh, unknown)
            )
        ),
        MetricItem(
            label = stringResource(R.string.summary_distance),
            value = stringResource(
                R.string.summary_distance_value,
                format0(bikeData?.totalDistanceMeters, unknown)
            )
        ),
        MetricItem(
            label = stringResource(R.string.session_target_label),
            value = stringResource(
                R.string.session_target_value,
                format0(lastTargetPower, unknown)
            )
        )
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
        val isWide = maxWidth >= WideLayoutMinWidth
        val stackPrimaryMetrics = maxWidth < PrimaryMetricsStackWidth

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
                    PrimaryMetricsSection(
                        powerValue = stringResource(
                            R.string.session_power_value,
                            format0(bikeData?.instantaneousPowerW, unknown)
                        ),
                        heartRateValue = stringResource(
                            R.string.session_hr_value,
                            format0(effectiveHr, unknown)
                        ),
                        stackCards = stackPrimaryMetrics,
                    )

                    WorkoutProgressSection(
                        selectedWorkout = selectedWorkout,
                        ftpWatts = ftpWatts,
                        runnerState = runnerState,
                        phase = phase,
                        cadenceRpm = cadenceRpm,
                        elapsedText = formatTime(elapsedWorkoutSec, unknown),
                        remainingText = formatTime(remainingWorkoutSec, unknown),
                        unknown = unknown,
                        workoutComplete = workoutComplete,
                    )

                    if (sessionIssues.isNotEmpty()) {
                        SessionIssuesSection(messages = sessionIssues)
                    }

                    SecondaryMetricsSection(
                        metrics = secondaryMetrics,
                        columns = if (isWide) 2 else 1
                    )
                }
            }

            Button(
                onClick = onEndSession,
                enabled = phase == SessionPhase.RUNNING,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = SessionMaxContentWidth)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                colors = disabledVisibleButtonColors()
            ) {
                Text(stringResource(R.string.btn_quit_session_now))
            }
        }
    }
}

@Composable
private fun PrimaryMetricsSection(
    powerValue: String,
    heartRateValue: String,
    stackCards: Boolean,
) {
    if (stackCards) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PrimaryMetricCard(
                label = stringResource(R.string.session_power_label),
                value = powerValue,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth()
            )
            PrimaryMetricCard(
                label = stringResource(R.string.session_hr_label),
                value = heartRateValue,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PrimaryMetricCard(
            label = stringResource(R.string.session_power_label),
            value = powerValue,
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
        PrimaryMetricCard(
            label = stringResource(R.string.session_hr_label),
            value = heartRateValue,
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PrimaryMetricCard(
    label: String,
    value: String,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun WorkoutProgressSection(
    selectedWorkout: WorkoutFile?,
    ftpWatts: Int,
    runnerState: RunnerState,
    phase: SessionPhase,
    cadenceRpm: Double?,
    elapsedText: String,
    remainingText: String,
    unknown: String,
    workoutComplete: Boolean,
) {
    SectionCard(title = stringResource(R.string.session_workout_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                label = stringResource(R.string.session_elapsed),
                value = elapsedText,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = stringResource(R.string.session_remaining),
                value = remainingText,
                modifier = Modifier.weight(1f)
            )
        }

        LabeledValueRow(
            label = stringResource(R.string.session_state_label),
            value = sessionStateLabel(
                phase = phase,
                runnerState = runnerState,
                cadenceRpm = cadenceRpm,
            ),
        )

        LabeledValueRow(
            label = stringResource(R.string.session_workout_step),
            value = workoutStepLabel(
                phase = phase,
                runnerState = runnerState,
                cadenceRpm = cadenceRpm,
                unknown = unknown,
            )
        )

        LabeledValueRow(
            label = stringResource(R.string.session_workout_step_remaining),
            value = formatTime(runnerState.stepRemainingSec, unknown),
        )

        val intervalPart = runnerState.intervalPart
        if (intervalPart != null) {
            LabeledValueRow(
                label = stringResource(R.string.session_workout_interval),
                value = intervalProgressLabel(
                    phase = intervalPart.phase,
                    repIndex = intervalPart.repIndex,
                    repTotal = intervalPart.repTotal,
                    remainingText = formatTime(intervalPart.remainingSec, unknown),
                ),
            )
        }

        LabeledValueRow(
            label = stringResource(R.string.session_workout_target_cadence),
            value = stringResource(
                R.string.session_workout_target_cadence_value,
                format0(runnerState.targetCadence, unknown)
            )
        )

        if (workoutComplete) {
            Text(
                text = stringResource(R.string.session_workout_complete),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (selectedWorkout != null) {
            WorkoutProfileChart(
                workout = selectedWorkout,
                ftpWatts = ftpWatts,
                elapsedSec = runnerState.workoutElapsedSec,
                chartHeight = SessionWorkoutChartHeight
            )
        }
    }
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isWide = maxWidth >= WideLayoutMinWidth

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = SummaryMaxContentWidth)
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
                    SectionCard(title = stringResource(R.string.summary_title)) {
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
                            stringResource(R.string.summary_distance),
                            stringResource(
                                R.string.summary_distance_value,
                                format0(summary.distanceMeters, unknown)
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

                    SectionCard(title = stringResource(R.string.summary_title)) {
                        MetricsGrid(
                            items = summaryItems,
                            columns = if (isWide) 2 else 1
                        )
                    }
                }

                Button(
                    onClick = onBackToMenu,
                    modifier = Modifier.fillMaxWidth(),
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
    SectionCard(title = stringResource(R.string.session_issue_title)) {
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
private fun SecondaryMetricsSection(
    metrics: List<MetricItem>,
    columns: Int
) {
    SectionCard(title = stringResource(R.string.session_title)) {
        MetricsGrid(
            items = metrics,
            columns = columns
        )
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

@Composable
private fun workoutStepLabel(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    unknown: String,
): String {
    if (isWaitingStartState(phase = phase, runnerState = runnerState, cadenceRpm = cadenceRpm)) {
        return stringResource(R.string.session_workout_step_start)
    }
    return runnerState.label ?: unknown
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
private fun intervalProgressLabel(
    phase: IntervalPartPhase,
    repIndex: Int,
    repTotal: Int,
    remainingText: String,
): String {
    val phaseLabel = when (phase) {
        IntervalPartPhase.ON -> stringResource(R.string.session_workout_interval_phase_on)
        IntervalPartPhase.OFF -> stringResource(R.string.session_workout_interval_phase_off)
    }
    return stringResource(
        R.string.session_workout_interval_progress,
        phaseLabel,
        repIndex,
        repTotal,
        remainingText,
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
    columns: Int
) {
    if (columns <= 1) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                MetricCard(
                    label = item.label,
                    value = item.value,
                    modifier = Modifier.fillMaxWidth()
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
                        modifier = Modifier.weight(1f)
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
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier) {
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
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun LabeledValueRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Medium
        )
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

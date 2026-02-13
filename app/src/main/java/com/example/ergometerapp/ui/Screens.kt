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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.R
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.ui.components.WorkoutProfileChart
import com.example.ergometerapp.ui.components.disabledVisibleButtonColors
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.runner.RunnerState
import java.util.Locale

private val WideLayoutMinWidth = 900.dp
private val MenuMaxContentWidth = 560.dp
private val SessionMaxContentWidth = 1200.dp
private val SummaryMaxContentWidth = 920.dp

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
    startEnabled: Boolean,
    onSelectWorkoutFile: () -> Unit,
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
    durationSeconds: Int?,
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
    val elapsedTime = formatTime(durationSeconds, unknown)

    val ftmsStatusText =
        if (ftmsReady) stringResource(R.string.status_ready)
        else stringResource(R.string.status_connecting)

    val controlStatusText =
        if (ftmsControlGranted) stringResource(R.string.status_control)
        else stringResource(R.string.status_unavailable)

    val metrics = listOf(
        MetricItem(
            label = stringResource(R.string.session_power_label),
            value = stringResource(
                R.string.session_power_value,
                format0(bikeData?.instantaneousPowerW, unknown)
            )
        ),
        MetricItem(
            label = stringResource(R.string.session_cadence_label),
            value = stringResource(
                R.string.session_cadence_value,
                format1(bikeData?.instantaneousCadenceRpm, unknown)
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
            label = stringResource(R.string.session_hr_label),
            value = stringResource(
                R.string.session_hr_value,
                format0(effectiveHr, unknown)
            )
        )
    )

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
                    .widthIn(max = SessionMaxContentWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ElevatedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.session_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.session_phase, phaseLabel(phase)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.session_elapsed_time),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = elapsedTime,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            TelemetrySection(
                                bikeData = bikeData,
                                metrics = metrics,
                                isWide = true
                            )
                            MachineStatusSection(
                                ftmsStatusText = ftmsStatusText,
                                controlStatusText = controlStatusText,
                                lastTargetPower = lastTargetPower,
                                unknown = unknown
                            )
                            WorkoutProfileSection(
                                selectedWorkout = selectedWorkout,
                                ftpWatts = ftpWatts,
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            WorkoutControlsSection(
                                runnerState = runnerState,
                                onEndSession = onEndSession,
                                endSessionEnabled = phase == SessionPhase.RUNNING,
                                unknown = unknown
                            )
                        }
                    }
                } else {
                    TelemetrySection(
                        bikeData = bikeData,
                        metrics = metrics,
                        isWide = false
                    )

                    MachineStatusSection(
                        ftmsStatusText = ftmsStatusText,
                        controlStatusText = controlStatusText,
                        lastTargetPower = lastTargetPower,
                        unknown = unknown
                    )

                    WorkoutProfileSection(
                        selectedWorkout = selectedWorkout,
                        ftpWatts = ftpWatts,
                    )

                    WorkoutControlsSection(
                        runnerState = runnerState,
                        onEndSession = onEndSession,
                        endSessionEnabled = phase == SessionPhase.RUNNING,
                        unknown = unknown
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutProfileSection(selectedWorkout: WorkoutFile?, ftpWatts: Int) {
    if (selectedWorkout == null) return
    SectionCard(title = stringResource(R.string.session_workout_title)) {
        WorkoutProfileChart(
            workout = selectedWorkout,
            ftpWatts = ftpWatts,
        )
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
private fun TelemetrySection(
    bikeData: IndoorBikeData?,
    metrics: List<MetricItem>,
    isWide: Boolean
) {
    SectionCard(title = stringResource(R.string.session_title)) {
        if (bikeData == null) {
            Text(
                text = stringResource(R.string.session_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MetricsGrid(
            items = metrics,
            columns = if (isWide) 2 else 1
        )
    }
}

@Composable
private fun MachineStatusSection(
    ftmsStatusText: String,
    controlStatusText: String,
    lastTargetPower: Int?,
    unknown: String
) {
    SectionCard(title = stringResource(R.string.session_status_title)) {
        LabeledValueRow(
            label = stringResource(R.string.session_status_ftms),
            value = ftmsStatusText
        )

        LabeledValueRow(
            label = stringResource(R.string.session_status_control),
            value = controlStatusText
        )

        LabeledValueRow(
            label = stringResource(R.string.session_target_label),
            value = stringResource(
                R.string.session_target_value,
                format0(lastTargetPower, unknown)
            )
        )
    }
}

@Composable
private fun WorkoutControlsSection(
    runnerState: RunnerState,
    onEndSession: () -> Unit,
    endSessionEnabled: Boolean,
    unknown: String
) {
    SectionCard(title = stringResource(R.string.session_workout_title)) {
        LabeledValueRow(
            label = stringResource(R.string.session_workout_state),
            value = workoutStateLabel(runnerState)
        )

        LabeledValueRow(
            label = stringResource(R.string.session_workout_step),
            value = runnerState.label ?: unknown
        )

        LabeledValueRow(
            label = stringResource(R.string.session_workout_target_cadence),
            value = stringResource(
                R.string.session_workout_target_cadence_value,
                format0(runnerState.targetCadence, unknown)
            )
        )

        HorizontalDivider()

        Button(
            onClick = onEndSession,
            enabled = endSessionEnabled,
            modifier = Modifier.fillMaxWidth(),
            colors = disabledVisibleButtonColors()
        ) {
            Text(stringResource(R.string.btn_end_session))
        }
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

private fun format1(value: Double?, fallback: String): String {
    return value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: fallback
}

private fun format0(value: Int?, fallback: String): String {
    return value?.toString() ?: fallback
}

/**
 * Formats seconds as `M:SS` for human readability.
 */
private fun formatTime(seconds: Int?, fallback: String): String {
    val safeSeconds = seconds ?: return fallback
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
}

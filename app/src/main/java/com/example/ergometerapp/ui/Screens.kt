package com.example.ergometerapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ergometerapp.R
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.ui.components.disabledVisibleButtonColors
import com.example.ergometerapp.workout.runner.RunnerState


/**
 * Entry screen for starting a session.
 *
 * The start action is gated on FTMS readiness because Control Point writes are
 * undefined until the device is connected and notifications/indications are set.
 */
@Composable
internal fun MenuScreen(
    ftmsReady: Boolean,
    onStartSession: () -> Unit
) {
    Column(Modifier.padding(24.dp)) {
        Text(stringResource(R.string.menu_title), fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))

        Text(
            text = if (ftmsReady)
                stringResource(R.string.menu_ftms_ready)
            else
                stringResource(R.string.menu_ftms_connecting),
            fontSize = 18.sp
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onStartSession,
            enabled = ftmsReady,
            colors = disabledVisibleButtonColors()
        ) {
            Text(stringResource(R.string.menu_start_session))
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
    runnerState: RunnerState,
    lastTargetPower: Int?,
    onPauseWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    onTakeControl: () -> Unit,
    onSetTargetPower: (Int) -> Unit,
    onRelease: () -> Unit,
    onStopWorkout: () -> Unit,
    onEndSession: () -> Unit
) {
    val sessionActive = runnerState.running
    val workoutPaused = runnerState.paused
    val workoutRunning = runnerState.running
    val workoutDone = runnerState.done
    val canSendPower = ftmsReady && ftmsControlGranted
    val canSendManualPower = sessionActive && canSendPower && workoutPaused
    // External HR is preferred by the session layer; fall back to bike HR for display.
    val effectiveHr = heartRate ?: bikeData?.heartRateBpm
    val dur = durationSeconds ?: 0

    val canTakeControl =
        phase == SessionPhase.RUNNING &&
                runnerState.running &&
                ftmsReady &&
                !ftmsControlGranted

    val canManualPower =
        ftmsControlGranted &&
                runnerState.paused &&
                !runnerState.done


    val canPause = runnerState.running
    val canResume = runnerState.paused

    val canRelease =
        ftmsControlGranted &&
                runnerState.running


    val canStopWorkout =
        runnerState.running || runnerState.paused

    Column(Modifier.padding(24.dp)) {
        Text(stringResource(R.string.session_title), fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.session_phase, phase.toString()),
            fontSize = 18.sp
        )

        Spacer(Modifier.height(12.dp))

        // LIVE DATA
        if (bikeData == null) {
            Text(stringResource(R.string.session_waiting), fontSize = 18.sp)
        } else {
            Text(
                text = stringResource(R.string.session_speed, format1(bikeData.instantaneousSpeedKmh)),
                fontSize = 24.sp
            )

            Text(stringResource(R.string.session_cadence, format1(bikeData.instantaneousCadenceRpm)), fontSize = 24.sp)
            Text(stringResource(R.string.session_power, format0(bikeData.instantaneousPowerW)), fontSize = 24.sp)
            Text(stringResource(R.string.session_time, formatTime(dur)), fontSize = 18.sp)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.session_hr, (effectiveHr?.toString() ?: "--")),
            fontSize = 22.sp
        )


        Spacer(Modifier.height(16.dp))

        // FTMS status
        val ftmsStatusText =
            if (!ftmsReady) stringResource(R.string.session_ftms_connecting)
            else if (ftmsControlGranted) stringResource(R.string.session_ftms_control)
            else stringResource(R.string.session_ftms_ready)

        Text(
            text = stringResource(R.string.session_ftms_status, ftmsStatusText),
            fontSize = 18.sp
        )

        Text(
            text = stringResource(R.string.session_target, lastTargetPower?.toString() ?: "--"),
            fontSize = 18.sp
        )


        Spacer(Modifier.height(12.dp))

        // Control buttons
        Button(
            onClick = onTakeControl,
            enabled = canTakeControl,
            colors = disabledVisibleButtonColors()
        )
        {
            Text(stringResource(R.string.btn_take_control))
        }


        Spacer(Modifier.height(8.dp))

        // Power buttons (always visible; greyed out when disabled)
        Row {
            Button(
                onClick = { onSetTargetPower(120) },
                enabled = canManualPower,
                colors = disabledVisibleButtonColors()
            )
            { Text(stringResource(R.string.power_button, 120)) }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = { onSetTargetPower(160) },
                enabled = canManualPower,
                colors = disabledVisibleButtonColors()
            )
            { Text(stringResource(R.string.power_button, 160)) }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = { onSetTargetPower(200) },
                enabled = canManualPower,
                colors = disabledVisibleButtonColors()
            )
            { Text(stringResource(R.string.power_button, 200)) }
        }

        Spacer(Modifier.height(8.dp))

        if (!workoutPaused) {
            Text(
                text = stringResource(R.string.session_workout_running_pause_manual),
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = if (workoutPaused) onResumeWorkout else onPauseWorkout,
            enabled = workoutRunning,
            colors = disabledVisibleButtonColors()
        ) {
            Text(
                text = stringResource(
                    if (workoutPaused) R.string.btn_resume_workout else R.string.btn_pause_workout
                )
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onRelease,
            enabled = canRelease,
            colors = disabledVisibleButtonColors()
        )
        {
            Text(stringResource(R.string.btn_release))
        }

        Spacer(Modifier.height(16.dp))

        // Session control
        Button(
            onClick = onStopWorkout,
            enabled = canStopWorkout,
            colors = disabledVisibleButtonColors()
        )
        {
            Text(stringResource(R.string.btn_stop_workout))
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onEndSession,
            enabled = phase == SessionPhase.RUNNING,
            colors = disabledVisibleButtonColors()
        ) {
            Text(stringResource(R.string.btn_end_session))
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
    Column(Modifier.padding(24.dp)) {
        Text(stringResource(R.string.summary), fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))

        if (summary == null) {
            Text(stringResource(R.string.no_summary))
        } else {
            Text(stringResource(R.string.duration, summary.durationSeconds.toString()))
            Text(stringResource(R.string.avg_power, summary.avgPower?.toString() ?: "--"))
            Text(stringResource(R.string.max_power, summary.maxPower?.toString() ?: "--"))
            Text(stringResource(R.string.avg_cadence, summary.avgCadence?.toString() ?: "--"))
            Text(stringResource(R.string.max_cadence, summary.maxCadence?.toString() ?: "--"))
            Text(stringResource(R.string.avg_hr, summary.avgHeartRate?.toString() ?: "--"))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onBackToMenu,
            colors = disabledVisibleButtonColors()
        ) {
            Text(stringResource(R.string.back_to_menu))
        }
    }
}

private fun format1(value: Double?): String =
    value?.let { String.format("%.1f", it) } ?: "--"

private fun format0(value: Int?): String =
    value?.toString() ?: "--"

/**
 * Formats seconds as `M:SS` for human readability.
 */
private fun formatTime(seconds: Int?): String {
    if (seconds == null) return "--"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

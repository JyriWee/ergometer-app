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

@Composable
internal fun SessionScreen(
    phase: SessionPhase,
    bikeData: IndoorBikeData?,
    heartRate: Int?,
    durationSeconds: Int?,
    ftmsReady: Boolean,
    ftmsControlGranted: Boolean,
    lastTargetPower: Int?,
    onTakeControl: () -> Unit,
    onSetTargetPower: (Int) -> Unit,
    onRelease: () -> Unit,
    onStopSession: () -> Unit
) {
    val canSendPower = ftmsReady && ftmsControlGranted
    val effectiveHr = heartRate ?: bikeData?.heartRateBpm
    val dur = durationSeconds ?: 0
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
            onClick = {
                onTakeControl()
                // Log.d("FTMS", "UI: requestControl()")
            },
            enabled = ftmsReady && !ftmsControlGranted,
            colors = disabledVisibleButtonColors()
        ) {
            Text(stringResource(R.string.btn_take_control))
        }


        Spacer(Modifier.height(8.dp))

        // Power buttons (always visible; greyed out when disabled)
        Row {
            Button(
                onClick = { onSetTargetPower(120) },
                enabled = canSendPower,
                colors = disabledVisibleButtonColors()
            ) { Text(stringResource(R.string.power_button, 120)) }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = { onSetTargetPower(160) },
                enabled = canSendPower,
                colors = disabledVisibleButtonColors()
            ) { Text(stringResource(R.string.power_button, 160)) }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = { onSetTargetPower(200) },
                enabled = canSendPower,
                colors = disabledVisibleButtonColors()
            ) { Text(stringResource(R.string.power_button, 200)) }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onRelease,
            enabled = ftmsReady && ftmsControlGranted,
            colors = disabledVisibleButtonColors()
        ) {
            Text(stringResource(R.string.btn_release))
        }

        Spacer(Modifier.height(16.dp))

        // Session control
        Button(
            onClick = onStopSession,
            enabled = phase == SessionPhase.RUNNING,
            colors = disabledVisibleButtonColors()
        ) {
            Text(stringResource(R.string.btn_stop_session))
        }
    }
}

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

private fun formatTime(seconds: Int?): String {
    if (seconds == null) return "--"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

package com.example.ergometerapp

import android.Manifest
import android.view.WindowManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ergometerapp.ble.FtmsBleClient
import com.example.ergometerapp.ble.FtmsController
import com.example.ergometerapp.ble.HrBleClient
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.ftms.parseIndoorBikeData
import com.example.ergometerapp.session.SessionManager
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.ui.theme.ErgometerAppTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource


/**
 * MainActivity
 *
 * Vastaa:
 * - BLE-clientien käynnistämisestä
 * - käyttöoikeuksista
 * - tilan syötöstä Compose-UI:lle
 *
 * Ei sisällä session-logiikkaa.
 */


class MainActivity : ComponentActivity() {
    private val REQUEST_BLUETOOTH_CONNECT = 1001

    private enum class AppScreen { MENU, SESSION, SUMMARY }

    private val screenState = mutableStateOf(AppScreen.MENU)

    private val heartRateState = mutableStateOf<Int?>(null)

    private lateinit var hrClient: HrBleClient

    private val bikeDataState = mutableStateOf<IndoorBikeData?>(null)

    private val summaryState = mutableStateOf<SessionSummary?>(null)

    private val sessionState = mutableStateOf<SessionState?>(null)

    private val ftmsReadyState = mutableStateOf(false)

    private val ftmsControlGrantedState = mutableStateOf(false)

    private val lastTargetPowerState = mutableStateOf<Int?>(null)


    private lateinit var bleClient: FtmsBleClient

    private lateinit var sessionManager: SessionManager

    private lateinit var ftmsController: FtmsController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sessionManager = SessionManager(this) { state ->
            sessionState.value = state
        }
        setContent {
            val screen = screenState.value

            ErgometerAppTheme {
                val session = sessionState.value
                val bikeData = bikeDataState.value
                val heartRate = heartRateState.value
                val phase = sessionManager.getPhase()
                val ftmsReady = ftmsReadyState.value
                val ftmsControlGranted = ftmsControlGrantedState.value
                val lastTargetPower = lastTargetPowerState.value

                when (screen) {
                    AppScreen.MENU -> MenuScreen(
                        ftmsReady = ftmsReadyState.value,
                        onStartSession = {
                            sessionManager.startSession()
                            keepScreenOn()
                            screenState.value = AppScreen.SESSION
                        }
                    )

                    AppScreen.SESSION -> SessionScreen(
                        phase = phase,
                        bikeData = bikeData,
                        heartRate = heartRate,
                        durationSeconds = session?.durationSeconds,
                        ftmsReady = ftmsReady,
                        ftmsControlGranted = ftmsControlGranted,
                        lastTargetPower = lastTargetPower,
                        onTakeControl = {
                            ftmsController.requestControl()
                        },
                        onSetTargetPower = { watts ->
                            ftmsController.setTargetPower(watts)
                            lastTargetPowerState.value = watts
                        },
                        onRelease = {
                            ftmsController.stop()
                            ftmsController.reset()
                        },
                        onStopSession = {
                            sessionManager.stopSession()
                            ftmsController.stop()
                            ftmsController.reset()

                            summaryState.value = sessionManager.lastSummary
                            allowScreenOff()
                            screenState.value = AppScreen.SUMMARY
                        }
                    )


                    AppScreen.SUMMARY -> SummaryScreen(
                        summary = summaryState.value,
                        onBackToMenu = {
                            screenState.value = AppScreen.MENU
                        }
                    )
                }
            }
        }
        ensureBluetoothPermission()

        bleClient = FtmsBleClient(
            context = this,
            onIndoorBikeData = { bytes ->
                val parsedData = parseIndoorBikeData(bytes)
                bikeDataState.value = parsedData
                sessionManager.updateBikeData(parsedData)
            },
            onReady = {
                ftmsReadyState.value = true
            },
            onControlPointResponse = { requestOpcode, resultCode ->

                ftmsController.onControlPointResponse(requestOpcode, resultCode)

                Log.d("FTMS", "UI state: cp response opcode=$requestOpcode result=$resultCode")

                // 0x00 = Request Control, 0x01 = Success
                if (requestOpcode == 0x00 && resultCode == 0x01) {
                    ftmsControlGrantedState.value = true
                }

                // 0x01 = Reset, 0x01 = Success (käytetään tätä “release done” -merkkinä)
                if (requestOpcode == 0x01 && resultCode == 0x01) {
                    ftmsControlGrantedState.value = false
                    lastTargetPowerState.value = null
                }
            },
            onDisconnected = {
                ftmsReadyState.value = false
                ftmsControlGrantedState.value = false
                lastTargetPowerState.value = null
                Log.w("FTMS", "UI state: disconnected -> READY=false CONTROL=false")
            }
        )


        ftmsController = FtmsController { payload ->
            bleClient.writeControlPoint(payload)
        }

        bleClient.connect("E0:DF:01:46:14:2F")

        hrClient = HrBleClient(this)
        { bpm ->
            heartRateState.value = bpm
            sessionManager.updateHeartRate(bpm)

        }

        hrClient.connect("24:AC:AC:04:12:79")

    }

    private fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun allowScreenOff() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun ensureBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQUEST_BLUETOOTH_CONNECT
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("BLE", "BLUETOOTH_CONNECT granted")
            } else {
                Log.d("BLE", "BLUETOOTH_CONNECT denied")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        allowScreenOff()
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

@Composable
private fun MenuScreen(
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

        Button(onClick = onStartSession, enabled = ftmsReady) {
            Text(stringResource(R.string.menu_start_session))
        }
    }
}

@Composable
private fun SessionScreen(
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
    val powerButtonColors = ButtonDefaults.buttonColors(
        disabledContainerColor = Color(0xFFE0E0E0),
        disabledContentColor = Color(0xFF666666)
    )
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
            enabled = ftmsReady && !ftmsControlGranted
        ) {
            Text(stringResource(R.string.btn_take_control))
        }


        Spacer(Modifier.height(8.dp))

        // Power buttons (always visible; greyed out when disabled)
        Row {
            Button(
                onClick = { onSetTargetPower(120) },
                enabled = canSendPower,
                colors = powerButtonColors
            ) { Text(stringResource(R.string.power_button, 120)) }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = { onSetTargetPower(160) },
                enabled = canSendPower,
                colors = powerButtonColors
            ) { Text(stringResource(R.string.power_button, 160)) }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = { onSetTargetPower(200) },
                enabled = canSendPower,
                colors = powerButtonColors
            ) { Text(stringResource(R.string.power_button, 200)) }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onRelease,
            enabled = ftmsReady && ftmsControlGranted
        ) {
            Text(stringResource(R.string.btn_release))
        }

        Spacer(Modifier.height(16.dp))

        // Session control
        Button(
            onClick = onStopSession,
            enabled = phase == SessionPhase.RUNNING
        ) {
            Text(stringResource(R.string.btn_stop_session))
        }
    }
}

@Composable
private fun SummaryScreen(
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
        Button(onClick = onBackToMenu) {
            Text(stringResource(R.string.back_to_menu))
        }
    }
}
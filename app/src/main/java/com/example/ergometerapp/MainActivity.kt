package com.example.ergometerapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.example.ergometerapp.ble.FtmsBleClient
import com.example.ergometerapp.ble.FtmsController
import com.example.ergometerapp.ble.HrBleClient
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.ftms.parseIndoorBikeData
import com.example.ergometerapp.session.SessionManager
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.ui.MenuScreen
import com.example.ergometerapp.ui.SessionScreen
import com.example.ergometerapp.ui.SummaryScreen
import com.example.ergometerapp.ui.theme.ErgometerAppTheme



/**
 * MainActivity
 *
 * Responsibilities:
 * - starting BLE clients
 * - permissions
 * - providing state to the Compose UI
 *
 * Does not contain session logic.
 */


class MainActivity : ComponentActivity() {
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
    private val requestBluetoothConnectPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d("BLE", "BLUETOOTH_CONNECT granted")
            } else {
                Log.d("BLE", "BLUETOOTH_CONNECT denied")
            }
        }

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
                            releaseControl()
                        },
                        onStopSession = {
                            stopSessionAndGoToSummary()
                        }
                    )


                    AppScreen.SUMMARY -> SummaryScreen(
                        summary = summaryState.value,
                        onBackToMenu = {
                            summaryState.value = null
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

                // 0x01 = Reset, 0x01 = Success (used as the “release done” signal)
                if (requestOpcode == 0x01 && resultCode == 0x01) {
                    resetFtmsUiState(clearReady = false)
                }
            },
            onDisconnected = {
                resetFtmsUiState(clearReady = true)
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

    private fun releaseControl() {
        // FTMS: stop + reset (FtmsController handles queueing/timeouts)
        ftmsController.stop()
        ftmsController.reset()

        // Make the UI state immediately “optimistically” sensible:
        resetFtmsUiState(clearReady = false)
        // CP reset-success confirms the release (idempotent)
    }

    private fun stopSessionAndGoToSummary() {
        sessionManager.stopSession()
        releaseControl()

        summaryState.value = sessionManager.lastSummary
        allowScreenOff()
        screenState.value = AppScreen.SUMMARY
    }

    private fun resetFtmsUiState(clearReady: Boolean) {
        if (clearReady) {
            ftmsReadyState.value = false
        }
        ftmsControlGrantedState.value = false
        lastTargetPowerState.value = null
    }

    private fun ensureBluetoothPermission() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothConnectPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        allowScreenOff()
    }

}

package com.example.ergometerapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                            lastTargetPowerState.value = null
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




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
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.ergometerapp.ui.debug.FtmsDebugTimelineScreen
import com.example.ergometerapp.ui.theme.ErgometerAppTheme
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.runner.RunnerState
import com.example.ergometerapp.workout.runner.WorkoutRunner
import com.example.ergometerapp.workout.runner.WorkoutStepper


/**
 * App entry point that wires BLE clients, session logic, and UI state.
 *
 * This activity intentionally keeps protocol handling in the BLE/controller
 * layers and only reacts to their callbacks to update UI state.
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

    private val runnerState = mutableStateOf(RunnerState.stopped())

    private val showDebugTimelineState = mutableStateOf(false)

    private lateinit var bleClient: FtmsBleClient

    private lateinit var sessionManager: SessionManager

    private lateinit var ftmsController: FtmsController

    private var workoutRunner: WorkoutRunner? = null
    private val requestBluetoothConnectPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d("BLE", "BLUETOOTH_CONNECT granted")
                bleClient.connect("E0:DF:01:46:14:2F")
                hrClient.connect("24:AC:AC:04:12:79")
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
                val currentRunnerState = runnerState.value
                val showDebugTimeline = showDebugTimelineState.value

                if (BuildConfig.DEBUG) {
                    Box {
                        when (screen) {
                            AppScreen.MENU -> MenuScreen(
                                ftmsReady = ftmsReady,
                                onStartSession = {
                                    sessionManager.startSession()
                                    ensureWorkoutRunner().start()
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
                                runnerState = currentRunnerState,
                                lastTargetPower = lastTargetPower,
                                onPauseWorkout = { workoutRunner?.pause() },
                                onResumeWorkout = { workoutRunner?.resume() },
                                onTakeControl = { ftmsController.requestControl() },
                                onSetTargetPower = { watts ->
                                    ftmsController.setTargetPower(watts)
                                    lastTargetPowerState.value = watts
                                },
                                onRelease = { releaseControl(false) },
                                onStopWorkout = { stopWorkout() },
                                onEndSession = { endSessionAndGoToSummary() }
                            )

                            AppScreen.SUMMARY -> SummaryScreen(
                                summary = summaryState.value,
                                onBackToMenu = {
                                    summaryState.value = null
                                    screenState.value = AppScreen.MENU
                                }
                            )
                        }

                        Button(onClick = {
                            showDebugTimelineState.value = !showDebugTimelineState.value
                        }) {
                            Text("Debug")
                        }

                        if (showDebugTimeline) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(200.dp)
                            ) {
                                FtmsDebugTimelineScreen()
                            }
                        }


                    }
                } else {
                    when (screen) {
                        AppScreen.MENU -> MenuScreen(
                            ftmsReady = ftmsReady,
                            onStartSession = {
                                sessionManager.startSession()
                                ensureWorkoutRunner().start()
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
                            runnerState = currentRunnerState,
                            lastTargetPower = lastTargetPower,
                            onPauseWorkout = { workoutRunner?.pause() },
                            onResumeWorkout = { workoutRunner?.resume() },
                            onTakeControl = { ftmsController.requestControl() },
                            onSetTargetPower = { watts ->
                                ftmsController.setTargetPower(watts)
                                lastTargetPowerState.value = watts
                            },
                            onRelease = { releaseControl(false) },
                            onStopWorkout = { stopWorkout() },
                            onEndSession = { endSessionAndGoToSummary() }
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
        }

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

                // FTMS: Request Control success unlocks power control in the UI.
                if (requestOpcode == 0x00 && resultCode == 0x01) {
                    ftmsControlGrantedState.value = true
                }

                // Reset success is treated as a definitive "release done" signal.
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

        // TODO: Move hardcoded MACs to configuration or device discovery flow.
        hrClient = HrBleClient(this)
        { bpm ->
            heartRateState.value = bpm
            sessionManager.updateHeartRate(bpm)

        }

        ensureBluetoothPermission()
    }
        /**
     * Keeps the screen awake during an active session to avoid lost telemetry
     * visibility when the user is mid-workout.
     */
    private fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Clears the keep-awake flag once a session is finished.
     */
    private fun allowScreenOff() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Releases FTMS control while preserving session semantics.
     *
     * Hard release uses stop+reset for full session termination. Soft release
     * only relinquishes FTMS control so it can be reacquired later.
     */
    private fun releaseControl(resetDevice: Boolean) {
        if (resetDevice) {
            ftmsController.stop()
            ftmsController.reset()
        } else {
            ftmsController.releaseControl()
        }
        resetFtmsUiState(clearReady = false)
    }

    /**
     * Ends only the structured workout.
     *
     * This intentionally keeps the BLE session alive so Indoor Bike Data and HR
     * telemetry can continue while the user free-rides.
     */
    private fun stopWorkout() {
        workoutRunner?.stop()
        lastTargetPowerState.value = null
    }

    /**
     * Finalizes the session and moves to the summary screen.
     */
    private fun endSessionAndGoToSummary() {
        stopWorkout()
        workoutRunner = null
        sessionManager.stopSession()
        releaseControl(true)

        summaryState.value = sessionManager.lastSummary
        allowScreenOff()
        screenState.value = AppScreen.SUMMARY
    }

    /**
     * Clears FTMS UI state; callers decide whether disconnection should also clear readiness.
     */
    private fun resetFtmsUiState(clearReady: Boolean) {
        if (clearReady) {
            ftmsReadyState.value = false
        }
        ftmsControlGrantedState.value = false
        lastTargetPowerState.value = null
    }

    /**
     * Requests BLUETOOTH_CONNECT when needed; required for GATT operations on Android 12+.
     */
    private fun ensureBluetoothPermission() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothConnectPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            bleClient.connect("E0:DF:01:46:14:2F")
            hrClient.connect("24:AC:AC:04:12:79")
        }
    }

    /**
     * Provides a replaceable workout source for early runner integration.
     */
    private fun ensureWorkoutRunner(): WorkoutRunner {
        val existing = workoutRunner
        if (existing != null) return existing
        val runner = WorkoutRunner(
            stepper = WorkoutStepper(createTestWorkout(), ftpWatts = 200),
            targetWriter = com.example.ergometerapp.ftms.FtmsTargetWriter { targetWatts ->
                if (ftmsReadyState.value &&
                    ftmsControlGrantedState.value &&
                    targetWatts != null &&
                    !runnerState.value.done) {
                    ftmsController.setTargetPower(targetWatts)
                    lastTargetPowerState.value = targetWatts
                } else {
                    lastTargetPowerState.value = null
                }
            },
            onStateChanged = { state ->
                runnerState.value = state
            }
        )
        workoutRunner = runner
        return runner
    }

    /**
     * Minimal workout used for verifying runner integration.
     */
    private fun createTestWorkout(): WorkoutFile {
        return WorkoutFile(
            name = "Test Workout",
            description = "Minimal hardcoded workout for runner testing.",
            author = "ErgometerApp",
            tags = emptyList(),
            steps = listOf(
                Step.Warmup(durationSec = 120, powerLow = 0.5, powerHigh = 0.7, cadence = 90),
                Step.SteadyState(durationSec = 180, power = 0.75, cadence = 90),
                Step.Cooldown(durationSec = 120, powerLow = 0.6, powerHigh = 0.4, cadence = 85)
            )
        )
    }

    override fun onDestroy() {
        workoutRunner?.stop()
        bleClient.close()
        hrClient.close()
        allowScreenOff()
        super.onDestroy()
    }

}

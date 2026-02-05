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
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
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

    private val workoutPausedState = mutableStateOf(false)
    private val workoutRunningState = mutableStateOf(false)

    private lateinit var bleClient: FtmsBleClient

    private lateinit var sessionManager: SessionManager

    private lateinit var ftmsController: FtmsController

    private var workoutRunner: WorkoutRunner? = null
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
                val workoutPaused = workoutPausedState.value
                val workoutRunning = workoutRunningState.value

                when (screen) {
                    AppScreen.MENU -> MenuScreen(
                        ftmsReady = ftmsReadyState.value,
                        onStartSession = {
                            sessionManager.startSession()
                            ensureWorkoutRunner().start()
                            workoutPausedState.value = false
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
                        workoutPaused = workoutPaused,
                        workoutRunning = workoutRunning,
                        lastTargetPower = lastTargetPower,
                        onPauseWorkout = {
                            workoutRunner?.pause()
                            workoutPausedState.value = true
                        },
                        onResumeWorkout = {
                            workoutRunner?.resume()
                            workoutPausedState.value = false
                        },
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
        bleClient.connect("E0:DF:01:46:14:2F")

        hrClient = HrBleClient(this)
        { bpm ->
            heartRateState.value = bpm
            sessionManager.updateHeartRate(bpm)

        }

        hrClient.connect("24:AC:AC:04:12:79")

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
     * Releases FTMS control using the stop+reset pattern.
     *
     * Some devices only acknowledge control release after a reset, so the UI
     * resets state optimistically and waits for the Control Point response.
     */
    private fun releaseControl() {
        // FTMS: stop + reset (FtmsController handles queueing/timeouts)
        ftmsController.stop()
        ftmsController.reset()

        // Make the UI state immediately “optimistically” sensible:
        resetFtmsUiState(clearReady = false)
        // CP reset-success confirms the release (idempotent)
    }

    /**
     * Finalizes the session and moves to the summary screen.
     */
    private fun stopSessionAndGoToSummary() {
        workoutRunner?.stop()
        workoutRunner = null
        workoutPausedState.value = false
        sessionManager.stopSession()
        releaseControl()

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
            applyTarget = { targetWatts ->
                if (ftmsReadyState.value && ftmsControlGrantedState.value && targetWatts != null) {
                    ftmsController.setTargetPower(targetWatts)
                    lastTargetPowerState.value = targetWatts
                } else {
                    lastTargetPowerState.value = null
                }
            },
            onRunningChanged = { running ->
                workoutRunningState.value = running
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
        super.onDestroy()
        allowScreenOff()
    }

}

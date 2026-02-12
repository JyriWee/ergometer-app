package com.example.ergometerapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.ble.FtmsBleClient
import com.example.ergometerapp.ble.FtmsController
import com.example.ergometerapp.ble.HrBleClient
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.ftms.parseIndoorBikeData
import com.example.ergometerapp.session.SessionManager
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.ui.ConnectingScreen
import com.example.ergometerapp.ui.MenuScreen
import com.example.ergometerapp.ui.SessionScreen
import com.example.ergometerapp.ui.SummaryScreen
import com.example.ergometerapp.ui.debug.FtmsDebugTimelineScreen
import com.example.ergometerapp.ui.theme.ErgometerAppTheme
import com.example.ergometerapp.workout.WorkoutImportResult
import com.example.ergometerapp.workout.WorkoutImportService
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
    private enum class AppScreen { MENU, CONNECTING, SESSION, SUMMARY }

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

    private var bleClient: FtmsBleClient? = null

    private lateinit var sessionManager: SessionManager

    private lateinit var ftmsController: FtmsController

    private var workoutRunner: WorkoutRunner? = null
    private val workoutImportService = WorkoutImportService()
    private var importedWorkoutForRunner: WorkoutFile? = null
    private var reconnectBleOnNextSessionStart = false
    private var awaitingStopResponseBeforeBleClose = false
    private var pendingSessionStartAfterPermission = false
    private val requestBluetoothConnectPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d("BLE", "BLUETOOTH_CONNECT granted")
                bleClient?.connect("E0:DF:01:46:14:2F")
                reconnectBleOnNextSessionStart = false
                hrClient.connect("24:AC:AC:04:12:79")
                if (pendingSessionStartAfterPermission) {
                    pendingSessionStartAfterPermission = false
                    screenState.value = AppScreen.CONNECTING
                }
                dumpUiState("permissionResult(granted=true)")
            } else {
                pendingSessionStartAfterPermission = false
                Log.d("BLE", "BLUETOOTH_CONNECT denied")
                dumpUiState("permissionResult(granted=false)")
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
                    val debugToggleContentDescription =
                        stringResource(R.string.debug_toggle_content_description)
                    Box {
                        when (screen) {
                            AppScreen.MENU -> {
                                MenuScreen(
                                    onStartSession = {
                                        startSessionConnection()
                                    }
                                )
                            }

                            AppScreen.CONNECTING -> {
                                ConnectingScreen()
                            }


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
                                onSetTargetPower = { watts ->
                                    ftmsController.setTargetPower(watts)
                                    lastTargetPowerState.value = watts
                                },
                                onRelease = { releaseControl(false) },
                                onStopWorkout = { endSessionAndGoToSummary() },
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

                        FloatingActionButton(
                            onClick = {
                                showDebugTimelineState.value = !showDebugTimelineState.value
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .semantics {
                                    contentDescription = debugToggleContentDescription
                                }
                        ) {
                            Text(stringResource(R.string.debug_toggle))
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
                        AppScreen.MENU -> {
                            MenuScreen(
                                onStartSession = {
                                    startSessionConnection()
                                }
                            )
                        }

                        AppScreen.CONNECTING -> {
                            ConnectingScreen()
                        }

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
                            onSetTargetPower = { watts ->
                                ftmsController.setTargetPower(watts)
                                lastTargetPowerState.value = watts
                            },
                            onRelease = { releaseControl(false) },
                            onStopWorkout = { endSessionAndGoToSummary() },
                            onEndSession = { endSessionAndGoToSummary() }
                        )

                        AppScreen.SUMMARY -> SummaryScreen(
                            summary = summaryState.value,
                            onBackToMenu = {
                                summaryState.value = null
                                screenState.value = AppScreen.MENU                            }
                        )
                    }
                }
            }
        }

        bleClient = createFtmsBleClient()


        ftmsController = createFtmsController()

        // TODO: Move hardcoded MACs to configuration or device discovery flow.
        hrClient = HrBleClient(this)
        { bpm ->
            heartRateState.value = bpm
            sessionManager.updateHeartRate(bpm)

        }

        ensureBluetoothPermission()
    }

    private fun dumpUiState(event: String) {
        Log.d(
            "FTMS",
            "UI_DUMP event=$event screen=${screenState.value} " +
                "ready=${ftmsReadyState.value} controlGranted=${ftmsControlGrantedState.value} " +
                "lastTarget=${lastTargetPowerState.value} runnerState=${runnerState.value} " +
                "reconnectPending=$reconnectBleOnNextSessionStart " +
                "awaitingStopClose=$awaitingStopResponseBeforeBleClose " +
                "pendingSessionStart=$pendingSessionStartAfterPermission"
        )
    }

    private fun startSessionConnection() {
        pendingSessionStartAfterPermission = true
        bleClient?.close()
        bleClient = createFtmsBleClient()
        ftmsController = createFtmsController()
        val connectInitiated = ensureBluetoothPermission()
        if (connectInitiated) {
            pendingSessionStartAfterPermission = false
            screenState.value = AppScreen.CONNECTING
        }
        dumpUiState("startSessionConnection(connectInitiated=$connectInitiated)")
    }

    private fun createFtmsBleClient(): FtmsBleClient {
        return FtmsBleClient(
            context = this@MainActivity,
            onIndoorBikeData = { bytes ->
                val parsedData = parseIndoorBikeData(bytes)
                bikeDataState.value = parsedData
                sessionManager.updateBikeData(parsedData)
            },
            onReady = {
                ftmsReadyState.value = true
                if (screenState.value == AppScreen.CONNECTING) {
                    // Session starts only after Request Control is acknowledged.
                    ftmsController.requestControl()
                }
                dumpUiState("bleOnReady")
            },
            onControlPointResponse = { requestOpcode, resultCode ->

                ftmsController.onControlPointResponse(requestOpcode, resultCode)

                Log.d("FTMS", "UI state: cp response opcode=$requestOpcode result=$resultCode")

                // FTMS: Request Control success unlocks power control in the UI.
                if (requestOpcode == 0x00 && resultCode == 0x01) {
                    ftmsControlGrantedState.value = true
                    if (screenState.value == AppScreen.CONNECTING) {
                        sessionManager.startSession()
                        ensureWorkoutRunner().start()
                        keepScreenOn()
                        screenState.value = AppScreen.SESSION
                    }
                }

                // Reset success is treated as a definitive "release done" signal.
                if (requestOpcode == 0x01 && resultCode == 0x01) {
                    resetFtmsUiState(clearReady = false)
                }
                dumpUiState("bleOnControlPointResponse(op=$requestOpcode,result=$resultCode)")
            },
            onDisconnected = {
                ftmsController.onDisconnected()
                awaitingStopResponseBeforeBleClose = false
                resetFtmsUiState(clearReady = true)
                Log.w("FTMS", "UI state: disconnected -> READY=false CONTROL=false")
                dumpUiState("bleOnDisconnected")
            }
        )
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
     * Hard release uses STOP for full session termination. Soft release
     * clears ERG target without sending STOP so telemetry can continue.
     */
    private fun releaseControl(resetDevice: Boolean) {
        if (resetDevice) {
            ftmsController.stopWorkout()
        } else {
            ftmsController.clearTargetPower()
        }
        resetFtmsUiState(clearReady = false)
        dumpUiState("releaseControl(resetDevice=$resetDevice)")
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
        awaitingStopResponseBeforeBleClose = true
        releaseControl(true)

        summaryState.value = sessionManager.lastSummary
        allowScreenOff()
        screenState.value = AppScreen.SUMMARY
        dumpUiState("endSessionAndGoToSummary")
    }

    /**
     * Enforces fresh BLE state after FTMS STOP by tearing down active GATT links.
     */
    private fun forceBleReconnectOnNextSession() {
        bleClient!!.close()
        hrClient.close()
        ftmsController = createFtmsController()
        resetFtmsUiState(clearReady = true)
        reconnectBleOnNextSessionStart = true
        dumpUiState("forceBleReconnectOnNextSession")
    }

    /**
     * Reconnects BLE links only when a previous STOP forced a session reset.
     */
    private fun reconnectBleIfNeeded() {
        if (!reconnectBleOnNextSessionStart) return
        ensureBluetoothPermission()
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
        dumpUiState("resetFtmsUiState(clearReady=$clearReady)")
    }

    /**
     * Requests BLUETOOTH_CONNECT when needed; required for GATT operations on Android 12+.
     */
    private fun ensureBluetoothPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothConnectPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            dumpUiState("ensureBluetoothPermission(requested=true)")
            return false
        } else {
            bleClient!!.connect("E0:DF:01:46:14:2F")
            reconnectBleOnNextSessionStart = false
            hrClient.connect("24:AC:AC:04:12:79")
            dumpUiState("ensureBluetoothPermission(requested=false)")
            return true
        }
    }

    /**
     * Provides a replaceable workout source for early runner integration.
     */
    private fun ensureWorkoutRunner(): WorkoutRunner {
        val existing = workoutRunner
        if (existing != null) return existing
        val runner = WorkoutRunner(
            stepper = WorkoutStepper(getWorkoutForRunner(), ftpWatts = 100),
            targetWriter = { targetWatts ->
                if (ftmsReadyState.value && ftmsControlGrantedState.value) {
                    if (targetWatts == null) {
                        ftmsController.clearTargetPower() // ERG release
                    } else {
                        ftmsController.setTargetPower(targetWatts)
                    }
                }
                lastTargetPowerState.value = targetWatts
            },
            onStateChanged = { state ->
                runnerState.value = state
            }
        )

        workoutRunner = runner
        return runner
    }

    private fun getWorkoutForRunner(): WorkoutFile {
        val cached = importedWorkoutForRunner
        if (cached != null) return cached

        val xmlContent = try {
            assets.open("Workout_Test.xml").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.w("WORKOUT", "Failed to load Workout_Test.xml: ${e.message}")
            return createTestWorkout()
        }

        return when (val result = workoutImportService.importFromText("Workout_Test.xml", xmlContent)) {
            is WorkoutImportResult.Success -> {
                importedWorkoutForRunner = result.workoutFile
                Log.d("WORKOUT", "Loaded Workout_Test.xml with ${result.workoutFile.steps.size} steps")
                result.workoutFile
            }
            is WorkoutImportResult.Failure -> {
                Log.w(
                    "WORKOUT",
                    "Workout import failed code=${result.error.code} " +
                        "format=${result.error.detectedFormat}; using fallback workout"
                )
                createTestWorkout()
            }
        }
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
        dumpUiState("onDestroy")
        workoutRunner?.stop()
        bleClient?.close()
        hrClient.close()
        allowScreenOff()
        super.onDestroy()
    }

    /**
     * Creates a fresh FTMS command controller bound to the active BLE client.
     */
    private fun createFtmsController(): FtmsController {
        return FtmsController(
            writeControlPoint = { payload ->
                bleClient?.writeControlPoint(payload)
            },
            onStopAcknowledged = {
                Handler(Looper.getMainLooper()).post {
                    dumpUiState("onStopAcknowledged")
                    bleClient?.close()
                }
            }
        )
    }
}

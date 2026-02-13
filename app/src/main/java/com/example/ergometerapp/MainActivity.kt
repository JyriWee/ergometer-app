package com.example.ergometerapp

import android.Manifest
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
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
    private val defaultFtmsDeviceMac = BuildConfig.DEFAULT_FTMS_DEVICE_MAC
    private val defaultHrDeviceMac = BuildConfig.DEFAULT_HR_DEVICE_MAC

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
    private val selectedWorkoutFileNameState = mutableStateOf<String?>(null)
    private val selectedWorkoutStepCountState = mutableStateOf<Int?>(null)
    private val selectedWorkoutImportErrorState = mutableStateOf<String?>(null)
    private val workoutReadyState = mutableStateOf(false)
    private var reconnectBleOnNextSessionStart = false
    private var awaitingStopResponseBeforeBleClose = false
    private var pendingSessionStartAfterPermission = false
    private var pendingCadenceStartAfterControlGranted = false
    private var autoPausedByZeroCadence = false
    private val requestBluetoothConnectPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.d("BLE", "BLUETOOTH_CONNECT granted")
                bleClient?.connect(defaultFtmsDeviceMac)
                reconnectBleOnNextSessionStart = false
                hrClient.connect(defaultHrDeviceMac)
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
    private val selectWorkoutFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) {
                dumpUiState("workoutSelection(cancelled)")
                return@registerForActivityResult
            }
            importWorkoutFromUri(uri)
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
                val selectedWorkoutFileName = selectedWorkoutFileNameState.value
                val selectedWorkoutStepCount = selectedWorkoutStepCountState.value
                val selectedWorkoutImportError = selectedWorkoutImportErrorState.value
                val workoutReady = workoutReadyState.value

                if (BuildConfig.DEBUG) {
                    val debugToggleContentDescription =
                        stringResource(R.string.debug_toggle_content_description)
                    Box {
                        when (screen) {
                            AppScreen.MENU -> {
                                MenuScreen(
                                    selectedWorkoutFileName = selectedWorkoutFileName,
                                    selectedWorkoutStepCount = selectedWorkoutStepCount,
                                    selectedWorkoutImportError = selectedWorkoutImportError,
                                    startEnabled = workoutReady,
                                    onSelectWorkoutFile = { selectWorkoutFile.launch(arrayOf("*/*")) },
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
                                onPauseWorkout = { pauseWorkoutManually() },
                                onResumeWorkout = { resumeWorkoutManually() },
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
                                selectedWorkoutFileName = selectedWorkoutFileName,
                                selectedWorkoutStepCount = selectedWorkoutStepCount,
                                selectedWorkoutImportError = selectedWorkoutImportError,
                                startEnabled = workoutReady,
                                onSelectWorkoutFile = { selectWorkoutFile.launch(arrayOf("*/*")) },
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
                            onPauseWorkout = { pauseWorkoutManually() },
                            onResumeWorkout = { resumeWorkoutManually() },
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
                "pendingSessionStart=$pendingSessionStartAfterPermission " +
                "pendingCadenceStart=$pendingCadenceStartAfterControlGranted " +
                "autoPausedByZeroCadence=$autoPausedByZeroCadence"
        )
    }

    private fun startSessionConnection() {
        if (!workoutReadyState.value) {
            Log.w("WORKOUT", "Start ignored: no valid workout selected")
            dumpUiState("startSessionConnectionIgnored(noWorkout)")
            return
        }
        pendingCadenceStartAfterControlGranted = false
        autoPausedByZeroCadence = false
        pendingSessionStartAfterPermission = true
        bikeDataState.value = null
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

    private fun importWorkoutFromUri(uri: Uri) {
        val sourceName = resolveDisplayName(uri) ?: "selected_workout"
        val content = try {
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8).use { reader ->
                reader?.readText()
            }
        } catch (e: Exception) {
            Log.w("WORKOUT", "Failed reading selected workout file: ${e.message}")
            null
        }

        if (content.isNullOrBlank()) {
            importedWorkoutForRunner = null
            workoutRunner = null
            selectedWorkoutFileNameState.value = sourceName
            selectedWorkoutStepCountState.value = null
            selectedWorkoutImportErrorState.value = "Unable to read file content."
            workoutReadyState.value = false
            dumpUiState("workoutImportReadFailed(name=$sourceName)")
            return
        }

        when (val result = workoutImportService.importFromText(sourceName = sourceName, content = content)) {
            is WorkoutImportResult.Success -> {
                importedWorkoutForRunner = result.workoutFile
                workoutRunner = null
                selectedWorkoutFileNameState.value = sourceName
                selectedWorkoutStepCountState.value = result.workoutFile.steps.size
                selectedWorkoutImportErrorState.value = null
                workoutReadyState.value = true
                Log.d(
                    "WORKOUT",
                    "Selected workout imported name=$sourceName steps=${result.workoutFile.steps.size}"
                )
                dumpUiState("workoutImportSuccess(name=$sourceName)")
            }
            is WorkoutImportResult.Failure -> {
                importedWorkoutForRunner = null
                workoutRunner = null
                selectedWorkoutFileNameState.value = sourceName
                selectedWorkoutStepCountState.value = null
                selectedWorkoutImportErrorState.value = result.error.message
                workoutReadyState.value = false
                Log.w(
                    "WORKOUT",
                    "Selected workout import failed code=${result.error.code} format=${result.error.detectedFormat}"
                )
                dumpUiState("workoutImportFailure(name=$sourceName,code=${result.error.code})")
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
    }

    private fun createFtmsBleClient(): FtmsBleClient {
        return FtmsBleClient(
            context = this@MainActivity,
            onIndoorBikeData = { bytes ->
                val parsedData = parseIndoorBikeData(bytes)
                bikeDataState.value = parsedData
                sessionManager.updateBikeData(parsedData)
                applyCadenceDrivenRunnerControl(parsedData.instantaneousCadenceRpm)
            },
            onReady = { controlPointReady ->
                ftmsReadyState.value = controlPointReady
                ftmsController.setTransportReady(controlPointReady)
                if (screenState.value == AppScreen.CONNECTING && controlPointReady) {
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
                        pendingCadenceStartAfterControlGranted = true
                        autoPausedByZeroCadence = false
                        keepScreenOn()
                        screenState.value = AppScreen.SESSION
                        applyCadenceDrivenRunnerControl(bikeDataState.value?.instantaneousCadenceRpm)
                    }
                }

                // Reset success is treated as a definitive "release done" signal.
                if (requestOpcode == 0x01 && resultCode == 0x01) {
                    resetFtmsUiState(clearReady = false)
                }
                dumpUiState("bleOnControlPointResponse(op=$requestOpcode,result=$resultCode)")
            },
            onDisconnected = {
                ftmsController.setTransportReady(false)
                ftmsController.onDisconnected()
                awaitingStopResponseBeforeBleClose = false
                pendingCadenceStartAfterControlGranted = false
                autoPausedByZeroCadence = false
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
        pendingCadenceStartAfterControlGranted = false
        autoPausedByZeroCadence = false
        workoutRunner?.stop()
        lastTargetPowerState.value = null
    }

    private fun pauseWorkoutManually() {
        autoPausedByZeroCadence = false
        workoutRunner?.pause()
    }

    private fun resumeWorkoutManually() {
        autoPausedByZeroCadence = false
        workoutRunner?.resume()
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
        pendingCadenceStartAfterControlGranted = false
        autoPausedByZeroCadence = false
        dumpUiState("resetFtmsUiState(clearReady=$clearReady)")
    }

    /**
     * Applies cadence gating for runner progression.
     *
     * The runner starts only after control is granted and cadence is above zero.
     * During an active workout, cadence zero auto-pauses and cadence above zero
     * auto-resumes only if the pause was cadence-triggered.
     */
    private fun applyCadenceDrivenRunnerControl(cadenceRpm: Double?) {
        if (screenState.value != AppScreen.SESSION || !ftmsControlGrantedState.value) return
        val cadencePositive = (cadenceRpm ?: 0.0) > 0.0

        if (pendingCadenceStartAfterControlGranted) {
            if (!cadencePositive) return
            pendingCadenceStartAfterControlGranted = false
            autoPausedByZeroCadence = false
            ensureWorkoutRunner().start()
            dumpUiState("runnerStartByCadence")
            return
        }

        val runner = workoutRunner ?: return
        val currentState = runnerState.value
        if (!currentState.running || currentState.done) return

        if (!currentState.paused && !cadencePositive) {
            autoPausedByZeroCadence = true
            runner.pause()
            dumpUiState("runnerAutoPauseByCadenceZero")
            return
        }

        if (currentState.paused && cadencePositive && autoPausedByZeroCadence) {
            autoPausedByZeroCadence = false
            runner.resume()
            dumpUiState("runnerAutoResumeByCadencePositive")
        }
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
            bleClient!!.connect(defaultFtmsDeviceMac)
            reconnectBleOnNextSessionStart = false
            hrClient.connect(defaultHrDeviceMac)
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

        Log.w("WORKOUT", "No selected workout imported; using fallback workout")
        return createTestWorkout()
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

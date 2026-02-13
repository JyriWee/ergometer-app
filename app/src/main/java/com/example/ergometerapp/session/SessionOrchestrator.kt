package com.example.ergometerapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.ergometerapp.ble.FtmsBleClient
import com.example.ergometerapp.ble.FtmsController
import com.example.ergometerapp.ftms.parseIndoorBikeData
import com.example.ergometerapp.session.SessionManager
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.WorkoutImportResult
import com.example.ergometerapp.workout.WorkoutImportService
import com.example.ergometerapp.workout.runner.WorkoutRunner
import com.example.ergometerapp.workout.runner.WorkoutStepper

/**
 * Coordinates FTMS session lifecycle without owning Android lifecycle wiring.
 *
 * Activity keeps permission and composition responsibilities while this class
 * owns the start/stop/control transitions and runner bridge.
 */
class SessionOrchestrator(
    private val context: Context,
    private val uiState: AppUiState,
    private val sessionManager: SessionManager,
    private val workoutImportService: WorkoutImportService,
    private val ensureBluetoothPermission: () -> Boolean,
    private val connectHeartRate: () -> Unit,
    private val closeHeartRate: () -> Unit,
    private val keepScreenOn: () -> Unit,
    private val allowScreenOff: () -> Unit
) {
    private var bleClient: FtmsBleClient? = null
    private lateinit var ftmsController: FtmsController
    private var workoutRunner: WorkoutRunner? = null
    private var importedWorkoutForRunner: WorkoutFile? = null

    fun initialize() {
        bleClient = createFtmsBleClient()
        ftmsController = createFtmsController()
    }

    fun startSessionConnection() {
        uiState.pendingSessionStartAfterPermission = true
        bleClient?.close()
        bleClient = createFtmsBleClient()
        ftmsController = createFtmsController()
        val connectInitiated = ensureBluetoothPermission()
        if (connectInitiated) {
            uiState.pendingSessionStartAfterPermission = false
            uiState.screen.value = AppScreen.CONNECTING
        }
        dumpUiState("startSessionConnection(connectInitiated=$connectInitiated)")
    }

    fun onBluetoothPermissionResult(granted: Boolean) {
        if (granted) {
            Log.d("BLE", "BLUETOOTH_CONNECT granted")
            connectBleClients()
            if (uiState.pendingSessionStartAfterPermission) {
                uiState.pendingSessionStartAfterPermission = false
                uiState.screen.value = AppScreen.CONNECTING
            }
            dumpUiState("permissionResult(granted=true)")
            return
        }

        uiState.pendingSessionStartAfterPermission = false
        Log.d("BLE", "BLUETOOTH_CONNECT denied")
        dumpUiState("permissionResult(granted=false)")
    }

    fun connectBleClients() {
        bleClient?.connect("E0:DF:01:46:14:2F")
        uiState.reconnectBleOnNextSessionStart = false
        connectHeartRate()
    }

    fun ensureReconnectionFromPermissionCheck() {
        if (!uiState.reconnectBleOnNextSessionStart) return
        ensureBluetoothPermission()
    }

    fun releaseControl(resetDevice: Boolean) {
        if (resetDevice) {
            ftmsController.stopWorkout()
        } else {
            ftmsController.clearTargetPower()
        }
        resetFtmsUiState(clearReady = false)
        dumpUiState("releaseControl(resetDevice=$resetDevice)")
    }

    fun setTargetPower(watts: Int) {
        ftmsController.setTargetPower(watts)
        uiState.lastTargetPower.value = watts
    }

    fun endSessionAndGoToSummary() {
        stopWorkout()
        workoutRunner = null
        sessionManager.stopSession()
        uiState.awaitingStopResponseBeforeBleClose = true
        releaseControl(resetDevice = true)

        uiState.summary.value = sessionManager.lastSummary
        allowScreenOff()
        uiState.screen.value = AppScreen.SUMMARY
        dumpUiState("endSessionAndGoToSummary")
    }

    fun pauseWorkout() {
        workoutRunner?.pause()
    }

    fun resumeWorkout() {
        workoutRunner?.resume()
    }

    fun reconnectBleOnNextSession() {
        bleClient?.close()
        closeHeartRate()
        ftmsController = createFtmsController()
        resetFtmsUiState(clearReady = true)
        uiState.reconnectBleOnNextSessionStart = true
        dumpUiState("forceBleReconnectOnNextSession")
    }

    fun stopAndClose() {
        dumpUiState("onDestroy")
        workoutRunner?.stop()
        bleClient?.close()
        closeHeartRate()
        allowScreenOff()
    }

    private fun createFtmsBleClient(): FtmsBleClient {
        return FtmsBleClient(
            context = context,
            onIndoorBikeData = { bytes ->
                val parsedData = parseIndoorBikeData(bytes)
                uiState.bikeData.value = parsedData
                sessionManager.updateBikeData(parsedData)
            },
            onReady = {
                uiState.ftmsReady.value = true
                if (uiState.screen.value == AppScreen.CONNECTING) {
                    ftmsController.requestControl()
                }
                dumpUiState("bleOnReady")
            },
            onControlPointResponse = { requestOpcode, resultCode ->
                ftmsController.onControlPointResponse(requestOpcode, resultCode)
                Log.d("FTMS", "UI state: cp response opcode=$requestOpcode result=$resultCode")

                if (requestOpcode == 0x00 && resultCode == 0x01) {
                    uiState.ftmsControlGranted.value = true
                    if (uiState.screen.value == AppScreen.CONNECTING) {
                        sessionManager.startSession()
                        ensureWorkoutRunner().start()
                        keepScreenOn()
                        uiState.screen.value = AppScreen.SESSION
                    }
                }

                if (requestOpcode == 0x01 && resultCode == 0x01) {
                    resetFtmsUiState(clearReady = false)
                }
                dumpUiState("bleOnControlPointResponse(op=$requestOpcode,result=$resultCode)")
            },
            onDisconnected = {
                ftmsController.onDisconnected()
                uiState.awaitingStopResponseBeforeBleClose = false
                resetFtmsUiState(clearReady = true)
                Log.w("FTMS", "UI state: disconnected -> READY=false CONTROL=false")
                dumpUiState("bleOnDisconnected")
            }
        )
    }

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

    private fun stopWorkout() {
        workoutRunner?.stop()
        uiState.lastTargetPower.value = null
    }

    private fun resetFtmsUiState(clearReady: Boolean) {
        if (clearReady) {
            uiState.ftmsReady.value = false
        }
        uiState.ftmsControlGranted.value = false
        uiState.lastTargetPower.value = null
        dumpUiState("resetFtmsUiState(clearReady=$clearReady)")
    }

    private fun ensureWorkoutRunner(): WorkoutRunner {
        val existing = workoutRunner
        if (existing != null) return existing
        val runner = WorkoutRunner(
            stepper = WorkoutStepper(getWorkoutForRunner(), ftpWatts = 100),
            targetWriter = { targetWatts ->
                if (uiState.ftmsReady.value && uiState.ftmsControlGranted.value) {
                    if (targetWatts == null) {
                        ftmsController.clearTargetPower()
                    } else {
                        ftmsController.setTargetPower(targetWatts)
                    }
                }
                uiState.lastTargetPower.value = targetWatts
            },
            onStateChanged = { state ->
                uiState.runner.value = state
            }
        )

        workoutRunner = runner
        return runner
    }

    private fun getWorkoutForRunner(): WorkoutFile {
        val cached = importedWorkoutForRunner
        if (cached != null) return cached

        val xmlContent = try {
            context.assets.open("Workout_Test.xml").bufferedReader(Charsets.UTF_8).use { it.readText() }
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

    private fun dumpUiState(event: String) {
        Log.d(
            "FTMS",
            "UI_DUMP event=$event screen=${uiState.screen.value} " +
                "ready=${uiState.ftmsReady.value} controlGranted=${uiState.ftmsControlGranted.value} " +
                "lastTarget=${uiState.lastTargetPower.value} runnerState=${uiState.runner.value} " +
                "reconnectPending=${uiState.reconnectBleOnNextSessionStart} " +
                "awaitingStopClose=${uiState.awaitingStopResponseBeforeBleClose} " +
                "pendingSessionStart=${uiState.pendingSessionStartAfterPermission}"
        )
    }
}


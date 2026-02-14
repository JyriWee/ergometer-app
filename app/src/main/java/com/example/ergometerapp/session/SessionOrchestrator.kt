package com.example.ergometerapp.session

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.AppUiState
import com.example.ergometerapp.BuildConfig
import com.example.ergometerapp.ble.FtmsBleClient
import com.example.ergometerapp.ble.FtmsController
import com.example.ergometerapp.ftms.parseIndoorBikeData
import com.example.ergometerapp.workout.ExecutionWorkoutMapper
import com.example.ergometerapp.workout.MappingResult
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.WorkoutImportError
import com.example.ergometerapp.workout.WorkoutImportFormat
import com.example.ergometerapp.workout.WorkoutImportResult
import com.example.ergometerapp.workout.WorkoutImportService
import com.example.ergometerapp.workout.WorkoutExecutionStepCounter
import com.example.ergometerapp.workout.runner.WorkoutRunner
import com.example.ergometerapp.workout.runner.WorkoutStepper

/**
 * Owns FTMS/session/workout orchestration while Activity keeps lifecycle and permissions.
 *
 * The key invariant is that stale BLE callbacks from an old GATT client instance are ignored
 * by generation checks, so reconnect races cannot mutate current UI/session state.
 */
class SessionOrchestrator(
    private val context: Context,
    private val uiState: AppUiState,
    private val sessionManager: SessionManager,
    private val ensureBluetoothPermission: () -> Boolean,
    private val connectHeartRate: () -> Unit,
    private val closeHeartRate: () -> Unit,
    private val keepScreenOn: () -> Unit,
    private val allowScreenOff: () -> Unit,
    private val currentFtpWatts: () -> Int,
    private val workoutImportService: WorkoutImportService = WorkoutImportService()
) {
    private val defaultFtmsDeviceMac = BuildConfig.DEFAULT_FTMS_DEVICE_MAC

    private var bleClient: FtmsBleClient? = null
    private lateinit var ftmsController: FtmsController
    private var workoutRunner: WorkoutRunner? = null
    private var ftmsClientGeneration: Int = 0
    private var activeFtmsClientGeneration: Int = 0

    /**
     * Creates fresh BLE/controller instances for the current activity lifetime.
     */
    fun initialize() {
        bleClient = createFtmsBleClient()
        ftmsController = createFtmsController()
    }

    /**
     * Starts FTMS connection flow for a new session.
     *
     * Session start is blocked when no validated workout exists to avoid entering
     * a half-started state where control is acquired without a runner plan.
     */
    fun startSessionConnection() {
        if (!uiState.workoutReady.value) {
            Log.w("WORKOUT", "Start ignored: no valid workout selected")
            dumpUiState("startSessionConnectionIgnored(noWorkout)")
            return
        }

        resetFtmsUiState(clearReady = true)
        uiState.pendingSessionStartAfterPermission = true
        uiState.bikeData.value = null

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

    /**
     * Handles Android permission callback and resumes pending session start when possible.
     */
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

    /**
     * Called by Activity when permission check already passed.
     */
    fun connectBleClients() {
        bleClient?.connect(defaultFtmsDeviceMac)
        uiState.reconnectBleOnNextSessionStart = false
        connectHeartRate()
    }

    /**
     * Imports a workout file selected by the user and updates UI-ready metadata.
     */
    fun onWorkoutFileSelected(uri: Uri?) {
        if (uri == null) {
            dumpUiState("workoutSelection(cancelled)")
            return
        }
        importWorkoutFromUri(uri)
    }

    /**
     * Stops the active session, releases trainer control and opens summary screen.
     */
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

    /**
     * Releases owned resources during activity teardown.
     */
    fun stopAndClose() {
        dumpUiState("onDestroy")
        workoutRunner?.stop()
        bleClient?.close()
        closeHeartRate()
        allowScreenOff()
    }

    private fun importWorkoutFromUri(uri: Uri) {
        val sourceName = resolveDisplayName(uri) ?: "selected_workout"
        var readFailureDetails: String? = null
        val content = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8).use { reader ->
                reader?.readText()
            }
        } catch (e: Exception) {
            Log.w("WORKOUT", "Failed reading selected workout file: ${e.message}")
            readFailureDetails = e.message?.takeIf { it.isNotBlank() }
            null
        }

        if (content.isNullOrBlank()) {
            uiState.selectedWorkout.value = null
            workoutRunner = null
            uiState.selectedWorkoutFileName.value = sourceName
            uiState.selectedWorkoutStepCount.value = null
            uiState.selectedWorkoutImportError.value =
                formatReadFailureMessage(readFailureDetails = readFailureDetails)
            uiState.workoutReady.value = false
            dumpUiState("workoutImportReadFailed(name=$sourceName)")
            return
        }

        when (val result = workoutImportService.importFromText(sourceName = sourceName, content = content)) {
            is WorkoutImportResult.Success -> {
                val executionStepCount = WorkoutExecutionStepCounter.count(
                    workout = result.workoutFile,
                    ftpWatts = currentFtpWatts(),
                )
                uiState.selectedWorkout.value = result.workoutFile
                workoutRunner = null
                uiState.selectedWorkoutFileName.value = sourceName
                uiState.selectedWorkoutStepCount.value = executionStepCount
                uiState.selectedWorkoutImportError.value = null
                uiState.workoutReady.value = true
                Log.d(
                    "WORKOUT",
                    "Selected workout imported name=$sourceName executionSteps=$executionStepCount rawSteps=${result.workoutFile.steps.size}"
                )
                dumpUiState("workoutImportSuccess(name=$sourceName)")
            }

            is WorkoutImportResult.Failure -> {
                uiState.selectedWorkout.value = null
                workoutRunner = null
                uiState.selectedWorkoutFileName.value = sourceName
                uiState.selectedWorkoutStepCount.value = null
                uiState.selectedWorkoutImportError.value = formatImportFailureMessage(result.error)
                uiState.workoutReady.value = false
                Log.w(
                    "WORKOUT",
                    "Selected workout import failed code=${result.error.code} format=${result.error.detectedFormat}"
                )
                dumpUiState("workoutImportFailure(name=$sourceName,code=${result.error.code})")
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
    }

    private fun formatReadFailureMessage(readFailureDetails: String?): String {
        if (readFailureDetails.isNullOrBlank()) {
            return "Unable to read file content."
        }
        return "Unable to read file content.\nDetails: $readFailureDetails"
    }

    /**
     * Keeps parser diagnostics visible in the same message shown to the user.
     */
    private fun formatImportFailureMessage(error: WorkoutImportError): String {
        val detectedFormat = when (error.detectedFormat) {
            WorkoutImportFormat.ZWO_XML -> "ZWO XML"
            WorkoutImportFormat.MYWHOOSH_JSON -> "MyWhoosh JSON"
            WorkoutImportFormat.UNKNOWN -> "Unknown"
        }

        return buildString {
            append(error.message)
            if (!error.technicalDetails.isNullOrBlank()) {
                append("\nDetails: ")
                append(error.technicalDetails)
            }
            append("\nDetected format: ")
            append(detectedFormat)
            append("\nError code: ")
            append(error.code.name)
        }
    }

    private fun createFtmsBleClient(): FtmsBleClient {
        val generation = ++ftmsClientGeneration
        activeFtmsClientGeneration = generation

        return FtmsBleClient(
            context = context,
            onIndoorBikeData = { bytes ->
                if (generation != activeFtmsClientGeneration) {
                    Log.d("FTMS", "Ignoring stale onIndoorBikeData callback (generation=$generation)")
                    return@FtmsBleClient
                }
                val parsedData = parseIndoorBikeData(bytes)
                uiState.bikeData.value = parsedData
                sessionManager.updateBikeData(parsedData)
                applyCadenceDrivenRunnerControl(parsedData.instantaneousCadenceRpm)
            },
            onReady = { controlPointReady ->
                if (generation != activeFtmsClientGeneration) {
                    Log.d("FTMS", "Ignoring stale onReady callback (generation=$generation)")
                    return@FtmsBleClient
                }
                uiState.ftmsReady.value = controlPointReady
                ftmsController.setTransportReady(controlPointReady)
                if (!controlPointReady) {
                    ftmsController.onDisconnected()
                }
                if (controlPointReady &&
                    (uiState.screen.value == AppScreen.CONNECTING || !uiState.ftmsControlGranted.value)
                ) {
                    // New session start must always request control; reconnect paths request only when needed.
                    ftmsController.requestControl()
                }
                dumpUiState("bleOnReady")
            },
            onControlPointResponse = { requestOpcode, resultCode ->
                if (generation != activeFtmsClientGeneration) {
                    Log.d(
                        "FTMS",
                        "Ignoring stale onControlPointResponse callback (generation=$generation)"
                    )
                    return@FtmsBleClient
                }

                ftmsController.onControlPointResponse(requestOpcode, resultCode)
                Log.d("FTMS", "UI state: cp response opcode=$requestOpcode result=$resultCode")

                // Session enters active mode only after Request Control is acknowledged.
                if (requestOpcode == 0x00 && resultCode == 0x01) {
                    if (uiState.screen.value == AppScreen.CONNECTING) {
                        sessionManager.startSession()
                        uiState.pendingCadenceStartAfterControlGranted = true
                        uiState.autoPausedByZeroCadence = false
                        keepScreenOn()
                        uiState.screen.value = AppScreen.SESSION
                        applyCadenceDrivenRunnerControl(uiState.bikeData.value?.instantaneousCadenceRpm)
                    }
                }

                // Reset success is treated as a definitive "release done" signal.
                if (requestOpcode == 0x01 && resultCode == 0x01) {
                    resetFtmsUiState(clearReady = false)
                }
                dumpUiState("bleOnControlPointResponse(op=$requestOpcode,result=$resultCode)")
            },
            onDisconnected = {
                if (generation != activeFtmsClientGeneration) {
                    Log.d("FTMS", "Ignoring stale onDisconnected callback (generation=$generation)")
                    return@FtmsBleClient
                }
                val wasSessionFlowActive =
                    uiState.screen.value == AppScreen.CONNECTING || uiState.screen.value == AppScreen.SESSION
                ftmsController.setTransportReady(false)
                ftmsController.onDisconnected()
                uiState.awaitingStopResponseBeforeBleClose = false
                resetFtmsUiState(clearReady = true)
                stopWorkout()
                workoutRunner = null
                sessionManager.stopSession()
                if (wasSessionFlowActive) {
                    allowScreenOff()
                    uiState.screen.value = AppScreen.MENU
                }
                Log.w("FTMS", "UI state: disconnected -> READY=false CONTROL=false sessionStopped=true")
                dumpUiState("bleOnDisconnected")
            },
            onControlOwnershipChanged = { controlGranted ->
                if (generation != activeFtmsClientGeneration) {
                    Log.d(
                        "FTMS",
                        "Ignoring stale onControlOwnershipChanged callback (generation=$generation)"
                    )
                    return@FtmsBleClient
                }
                uiState.ftmsControlGranted.value = controlGranted
                dumpUiState("bleOnControlOwnershipChanged(granted=$controlGranted)")
            }
        )
    }

    /**
     * Releases FTMS control while preserving session semantics.
     *
     * Hard release uses STOP for full session termination. Soft release clears
     * ERG target without sending STOP so telemetry can continue.
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
     * Ends only the structured workout and keeps telemetry channels active.
     */
    private fun stopWorkout() {
        uiState.pendingCadenceStartAfterControlGranted = false
        uiState.autoPausedByZeroCadence = false
        workoutRunner?.stop()
        uiState.lastTargetPower.value = null
    }

    /**
     * Clears FTMS UI state; callers decide whether readiness should be reset.
     */
    private fun resetFtmsUiState(clearReady: Boolean) {
        if (clearReady) {
            uiState.ftmsReady.value = false
        }
        uiState.ftmsControlGranted.value = false
        uiState.lastTargetPower.value = null
        uiState.pendingCadenceStartAfterControlGranted = false
        uiState.autoPausedByZeroCadence = false
        dumpUiState("resetFtmsUiState(clearReady=$clearReady)")
    }

    /**
     * Applies cadence gating for runner progression.
     *
     * Runner starts only after control is granted and cadence is above zero.
     * During an active workout, cadence zero auto-pauses and cadence above zero
     * auto-resumes only when the pause was cadence-triggered.
     */
    private fun applyCadenceDrivenRunnerControl(cadenceRpm: Double?) {
        if (uiState.screen.value != AppScreen.SESSION || !uiState.ftmsControlGranted.value) return
        val cadencePositive = (cadenceRpm ?: 0.0) > 0.0

        if (uiState.pendingCadenceStartAfterControlGranted) {
            if (!cadencePositive) return
            uiState.pendingCadenceStartAfterControlGranted = false
            uiState.autoPausedByZeroCadence = false
            ensureWorkoutRunner().start()
            dumpUiState("runnerStartByCadence")
            return
        }

        val runner = workoutRunner ?: return
        val currentState = uiState.runner.value
        if (!currentState.running || currentState.done) return

        if (!currentState.paused && !cadencePositive) {
            uiState.autoPausedByZeroCadence = true
            runner.pause()
            dumpUiState("runnerAutoPauseByCadenceZero")
            return
        }

        if (currentState.paused && cadencePositive && uiState.autoPausedByZeroCadence) {
            uiState.autoPausedByZeroCadence = false
            runner.resume()
            dumpUiState("runnerAutoResumeByCadencePositive")
        }
    }

    /**
     * Lazily builds a runner bound to the currently selected workout.
     */
    private fun ensureWorkoutRunner(): WorkoutRunner {
        val existing = workoutRunner
        if (existing != null) return existing

        val stepper = createRunnerStepper(getWorkoutForRunner())
        val runner = WorkoutRunner(
            stepper = stepper,
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

    /**
     * Prefers strict execution mapping while preserving legacy fallback for unsupported steps.
     */
    private fun createRunnerStepper(workout: WorkoutFile): WorkoutStepper {
        val ftpWatts = currentFtpWatts().coerceAtLeast(1)
        return when (val mapped = ExecutionWorkoutMapper.map(workout, ftp = ftpWatts)) {
            is MappingResult.Success -> {
                WorkoutStepper.fromExecutionWorkout(mapped.workout)
            }

            is MappingResult.Failure -> {
                val summary = mapped.errors.joinToString(separator = ", ") { it.code.name }
                Log.w("WORKOUT", "Execution mapping failed; falling back to legacy stepper: $summary")
                WorkoutStepper(workout, ftpWatts = ftpWatts)
            }
        }
    }

    private fun getWorkoutForRunner(): WorkoutFile {
        val cached = uiState.selectedWorkout.value
        if (cached != null) return cached

        Log.w("WORKOUT", "No selected workout imported; using fallback workout")
        return createTestWorkout()
    }

    /**
     * Minimal local fallback workout to keep runner testable without import.
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

    private fun dumpUiState(event: String) {
        Log.d(
            "FTMS",
            "UI_DUMP event=$event screen=${uiState.screen.value} " +
                "ready=${uiState.ftmsReady.value} controlGranted=${uiState.ftmsControlGranted.value} " +
                "lastTarget=${uiState.lastTargetPower.value} runnerState=${uiState.runner.value} " +
                "reconnectPending=${uiState.reconnectBleOnNextSessionStart} " +
                "awaitingStopClose=${uiState.awaitingStopResponseBeforeBleClose} " +
                "pendingSessionStart=${uiState.pendingSessionStartAfterPermission} " +
                "pendingCadenceStart=${uiState.pendingCadenceStartAfterControlGranted} " +
                "autoPausedByZeroCadence=${uiState.autoPausedByZeroCadence}"
        )
    }
}

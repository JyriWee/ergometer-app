package com.example.ergometerapp

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.example.ergometerapp.ble.BleDeviceScanner
import com.example.ergometerapp.ble.HrBleClient
import com.example.ergometerapp.session.SessionManager
import com.example.ergometerapp.session.SessionOrchestrator
import com.example.ergometerapp.workout.editor.WorkoutEditorAction
import com.example.ergometerapp.workout.editor.WorkoutEditorBuildResult
import com.example.ergometerapp.workout.editor.WorkoutEditorDraft
import com.example.ergometerapp.workout.editor.WorkoutEditorMapper
import com.example.ergometerapp.workout.editor.WorkoutEditorStepDraft
import com.example.ergometerapp.workout.editor.WorkoutEditorStepField
import com.example.ergometerapp.workout.editor.WorkoutEditorStepType
import com.example.ergometerapp.workout.editor.WorkoutZwoSerializer
import java.io.OutputStreamWriter
import java.util.UUID

/**
 * Holds app/session state across Activity recreation.
 *
 * The ViewModel owns long-lived session services so orientation changes do not
 * tear down active BLE connections or workout execution.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val defaultFtpWatts = BuildConfig.DEFAULT_FTP_WATTS.coerceAtLeast(1)
    private val ftpInputMaxLength = 4
    private val ftmsServiceUuid: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val hrServiceUuid: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val errorToneDurationMs = 120
    private val trainerStatusProbeIntervalMs = 10_000L
    private val trainerStatusProbeDurationMs = 1_500L
    private val hrStatusProbeIntervalMs = 30_000L
    private val hrStatusProbeDurationMs = 1_500L
    private val statusProbeScanMode = DeviceScanPolicy.scanModeFor(DeviceScanPolicy.Purpose.MENU_PROBE)
    private val pickerScanMode = DeviceScanPolicy.scanModeFor(DeviceScanPolicy.Purpose.PICKER)
    private val pickerScanRetryDelayMs = 1_500L
    private val pickerStopButtonLockDurationMs = 3_000L
    private val scannedDeviceSortThrottleMs = 300L
    private val statusProbeResumeDelayAfterPickerMs = 2_000L
    private val hrStatusMissThreshold = 2
    private val hrStatusStaleTimeoutMs = 75_000L
    private val workoutEditorMaxTextLength = 180
    private val workoutEditorMaxDescriptionLength = 400

    val uiState = AppUiState()
    val ftpWattsState = mutableIntStateOf(defaultFtpWatts)
    val ftpInputTextState = mutableStateOf(defaultFtpWatts.toString())
    val ftpInputErrorState = mutableStateOf<String?>(null)
    val ftmsDeviceNameState = mutableStateOf("")
    val ftmsReachableState = mutableStateOf<Boolean?>(null)
    val hrDeviceNameState = mutableStateOf("")
    val hrReachableState = mutableStateOf<Boolean?>(null)
    val hrConnectedState = mutableStateOf(false)
    val activeDeviceSelectionKindState = mutableStateOf<DeviceSelectionKind?>(null)
    val scannedDevicesState = mutableStateListOf<ScannedBleDevice>()
    val deviceScanInProgressState = mutableStateOf(false)
    val deviceScanStatusState = mutableStateOf<String?>(null)
    val deviceScanStopEnabledState = mutableStateOf(true)
    val workoutEditorDraftState = mutableStateOf(WorkoutEditorDraft.empty())
    val workoutEditorValidationErrorsState = mutableStateOf(WorkoutEditorMapper.validate(WorkoutEditorDraft.empty()))
    val workoutEditorStatusMessageState = mutableStateOf<String?>(null)
    val workoutEditorStatusIsErrorState = mutableStateOf(false)
    val workoutEditorHasUnsavedChangesState = mutableStateOf(true)
    val workoutEditorShowSaveBeforeApplyPromptState = mutableStateOf(false)
    private val selectedFtmsDeviceMacState = mutableStateOf<String?>(null)
    private val selectedHrDeviceMacState = mutableStateOf<String?>(null)

    private var ensureBluetoothConnectPermissionCallback: (() -> Boolean)? = null
    private var ensureBluetoothScanPermissionCallback: (() -> Boolean)? = null
    private var keepScreenOnCallback: (() -> Unit)? = null
    private var allowScreenOffCallback: (() -> Unit)? = null
    private var pendingDeviceScanKind: DeviceSelectionKind? = null
    private var pendingPickerScanRetry: Runnable? = null
    private var pendingPickerStopUnlock: Runnable? = null
    private var pendingScannedDeviceSort: Runnable? = null
    private var pendingStatusProbeResume: Runnable? = null
    private var statusProbeSuppressedUntilElapsedMs: Long = 0L
    private var nextWorkoutEditorStepId: Long = 1L
    private var pendingWorkoutEditorApplyAfterSave = false
    private var closed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bleDeviceScanner = BleDeviceScanner(appContext, scannerLabel = "picker")
    private val trainerStatusScanner = BleDeviceScanner(appContext, scannerLabel = "probe_ftms")
    private val hrStatusScanner = BleDeviceScanner(appContext, scannerLabel = "probe_hr")
    private var trainerStatusProbeInProgress = false
    private var hrStatusProbeInProgress = false
    private var trainerStatusProbeLoopRunning = false
    private var hrStatusProbeLoopRunning = false
    private var hrConsecutiveMisses = 0
    private var hrLastSeenElapsedMs: Long? = null
    private val trainerStatusProbeRunnable = object : Runnable {
        override fun run() {
            if (closed || !trainerStatusProbeLoopRunning) return
            probeTrainerAvailabilityNow()
            mainHandler.postDelayed(this, trainerStatusProbeIntervalMs)
        }
    }
    private val hrStatusProbeRunnable = object : Runnable {
        override fun run() {
            if (closed || !hrStatusProbeLoopRunning) return
            probeHrAvailabilityNow()
            mainHandler.postDelayed(this, hrStatusProbeIntervalMs)
        }
    }
    private var errorToneGenerator: ToneGenerator? = null

    private val sessionManager = SessionManager(
        context = appContext,
        onStateUpdated = { state ->
            uiState.session.value = state
        },
    )

    private val hrClient = HrBleClient(
        context = appContext,
        onHeartRate = { bpm ->
            uiState.heartRate.value = bpm
            sessionManager.updateHeartRate(bpm)
        },
        onConnected = {
            hrConnectedState.value = true
            hrReachableState.value = true
            hrConsecutiveMisses = 0
            hrLastSeenElapsedMs = SystemClock.elapsedRealtime()
        },
        onDisconnected = {
            hrConnectedState.value = false
            uiState.heartRate.value = null
            sessionManager.updateHeartRate(null)
        },
    )

    private val sessionOrchestrator = SessionOrchestrator(
        context = appContext,
        uiState = uiState,
        sessionManager = sessionManager,
        ensureBluetoothPermission = { ensureBluetoothConnectPermissionCallback?.invoke() == true },
        connectHeartRate = {
            val hrMac = currentHrDeviceMac()
            if (hrMac != null) {
                hrClient.connect(hrMac)
            }
        },
        closeHeartRate = { hrClient.close() },
        keepScreenOn = { keepScreenOnCallback?.invoke() },
        allowScreenOff = { allowScreenOffCallback?.invoke() },
        onExecutionMappingFailure = { playExecutionFailureTone() },
        currentFtmsDeviceMac = { currentFtmsDeviceMac() },
        currentFtpWatts = { ftpWattsState.intValue },
    )

    init {
        val storedFtpWatts = FtpSettingsStorage.loadFtpWatts(appContext, defaultFtpWatts)
        val storedFtmsMac = DeviceSettingsStorage.loadFtmsDeviceMac(appContext)
        val storedHrMac = DeviceSettingsStorage.loadHrDeviceMac(appContext)
        val storedFtmsName = DeviceSettingsStorage.loadFtmsDeviceName(appContext).orEmpty()
        val storedHrName = DeviceSettingsStorage.loadHrDeviceName(appContext).orEmpty()

        ftpWattsState.intValue = storedFtpWatts
        ftpInputTextState.value = storedFtpWatts.toString()
        ftpInputErrorState.value = null
        selectedFtmsDeviceMacState.value = storedFtmsMac
        ftmsDeviceNameState.value = storedFtmsName
        selectedHrDeviceMacState.value = storedHrMac
        hrDeviceNameState.value = storedHrName
        sessionOrchestrator.initialize()
    }

    /**
     * Binds Activity-specific callbacks after recreation.
     */
    fun bindActivityCallbacks(
        ensureBluetoothConnectPermission: () -> Boolean,
        ensureBluetoothScanPermission: () -> Boolean,
        keepScreenOn: () -> Unit,
        allowScreenOff: () -> Unit,
    ) {
        ensureBluetoothConnectPermissionCallback = ensureBluetoothConnectPermission
        ensureBluetoothScanPermissionCallback = ensureBluetoothScanPermission
        keepScreenOnCallback = keepScreenOn
        allowScreenOffCallback = allowScreenOff
        if (uiState.screen.value == AppScreen.SESSION || uiState.screen.value == AppScreen.STOPPING) {
            keepScreenOn()
        }
        startTrainerStatusPolling()
        startHrStatusPolling()
        probeTrainerAvailabilityNow()
        probeHrAvailabilityNow()
    }

    /**
     * Unbinds Activity callbacks before a configuration-driven teardown.
     */
    fun unbindActivityCallbacks() {
        ensureBluetoothConnectPermissionCallback = null
        ensureBluetoothScanPermissionCallback = null
        keepScreenOnCallback = null
        allowScreenOffCallback = null
        stopTrainerStatusPolling()
        stopHrStatusPolling()
    }

    /**
     * Stops runtime services exactly once when app flow is finishing.
     */
    fun stopAndClose() {
        if (closed) return
        closed = true
        cancelPendingPickerScanRetry()
        cancelPendingPickerStopUnlock()
        cancelPendingScannedDeviceSort()
        cancelPendingStatusProbeResume()
        stopTrainerStatusPolling()
        stopHrStatusPolling()
        bleDeviceScanner.stop()
        trainerStatusScanner.stop()
        hrStatusScanner.stop()
        trainerStatusProbeInProgress = false
        hrStatusProbeInProgress = false
        releaseErrorTone()
        ftmsReachableState.value = null
        hrReachableState.value = null
        hrConsecutiveMisses = 0
        hrLastSeenElapsedMs = null
        hrConnectedState.value = false
        sessionOrchestrator.stopAndClose()
    }

    /**
     * Returns the current session phase for rendering.
     */
    fun phase() = sessionManager.getPhase()

    fun onBluetoothPermissionResult(granted: Boolean) {
        sessionOrchestrator.onBluetoothPermissionResult(granted)
    }

    /**
     * Continues pending device scan request after runtime BLUETOOTH_SCAN result.
     */
    fun onBluetoothScanPermissionResult(granted: Boolean) {
        val pendingKind = pendingDeviceScanKind
        pendingDeviceScanKind = null
        if (!granted || pendingKind == null) {
            if (!granted) {
                deviceScanStatusState.value = appContext.getString(R.string.menu_device_scan_permission_required)
                deviceScanInProgressState.value = false
            }
            if (granted) {
                probeTrainerAvailabilityNow()
                probeHrAvailabilityNow()
            }
            return
        }
        startDeviceScan(pendingKind)
    }

    fun onWorkoutFileSelected(uri: Uri?) {
        sessionOrchestrator.onWorkoutFileSelected(uri)
    }

    fun onStartSession() {
        if (!hasValidTrainerSelection()) {
            return
        }
        cancelTrainerStatusProbeScan()
        cancelHrStatusProbeScan()
        sessionOrchestrator.startSessionConnection()
    }

    fun onEndSessionAndGoToSummary() {
        sessionOrchestrator.endSessionAndGoToSummary()
    }

    fun onBackToMenu() {
        uiState.summary.value = null
        uiState.screen.value = AppScreen.MENU
        allowScreenOffCallback?.invoke()
        probeTrainerAvailabilityNow()
        probeHrAvailabilityNow()
    }

    /**
     * Handles workout editor events from UI.
     */
    fun onWorkoutEditorAction(action: WorkoutEditorAction) {
        when (action) {
            WorkoutEditorAction.OpenEditor -> {
                clearWorkoutEditorStatus()
                workoutEditorShowSaveBeforeApplyPromptState.value = false
                pendingWorkoutEditorApplyAfterSave = false
                uiState.screen.value = AppScreen.WORKOUT_EDITOR
            }
            WorkoutEditorAction.BackToMenu -> {
                uiState.screen.value = AppScreen.MENU
            }
            WorkoutEditorAction.NewDraft -> {
                resetWorkoutEditorDraft()
                setWorkoutEditorStatus(
                    message = appContext.getString(R.string.workout_editor_status_new_draft),
                    isError = false,
                )
            }
            WorkoutEditorAction.LoadSelectedWorkout -> {
                loadSelectedWorkoutIntoEditor()
            }
            WorkoutEditorAction.ApplyToMenuSelection -> {
                applyEditorWorkoutToMenuSelection()
            }
            WorkoutEditorAction.DismissSaveBeforeApplyPrompt -> {
                workoutEditorShowSaveBeforeApplyPromptState.value = false
                pendingWorkoutEditorApplyAfterSave = false
            }
            WorkoutEditorAction.ConfirmApplyWithoutSaving -> {
                workoutEditorShowSaveBeforeApplyPromptState.value = false
                pendingWorkoutEditorApplyAfterSave = false
                applyEditorWorkoutToMenuSelection(skipUnsavedPrompt = true)
            }
            WorkoutEditorAction.PrepareSaveAndApply -> {
                workoutEditorShowSaveBeforeApplyPromptState.value = false
                pendingWorkoutEditorApplyAfterSave = true
            }
            is WorkoutEditorAction.SetName -> {
                updateWorkoutEditorDraft(
                    workoutEditorDraftState.value.copy(
                        name = action.value.trim().take(workoutEditorMaxTextLength),
                    ),
                )
            }
            is WorkoutEditorAction.SetDescription -> {
                updateWorkoutEditorDraft(
                    workoutEditorDraftState.value.copy(
                        description = action.value.take(workoutEditorMaxDescriptionLength),
                    ),
                )
            }
            is WorkoutEditorAction.SetAuthor -> {
                updateWorkoutEditorDraft(
                    workoutEditorDraftState.value.copy(
                        author = action.value.trim().take(workoutEditorMaxTextLength),
                    ),
                )
            }
            is WorkoutEditorAction.AddStep -> {
                val draft = workoutEditorDraftState.value
                val added = draft.steps + createStepDraft(action.type)
                updateWorkoutEditorDraft(draft.copy(steps = added))
            }
            is WorkoutEditorAction.DeleteStep -> {
                val draft = workoutEditorDraftState.value
                val filtered = draft.steps.filterNot { it.id == action.stepId }
                updateWorkoutEditorDraft(draft.copy(steps = filtered))
            }
            is WorkoutEditorAction.DuplicateStep -> {
                val draft = workoutEditorDraftState.value
                val sourceIndex = draft.steps.indexOfFirst { it.id == action.stepId }
                if (sourceIndex < 0) return
                val source = draft.steps[sourceIndex]
                val duplicated = source.withId(nextWorkoutEditorStepId++)
                val updated = draft.steps.toMutableList().apply {
                    add(sourceIndex + 1, duplicated)
                }
                updateWorkoutEditorDraft(draft.copy(steps = updated))
            }
            is WorkoutEditorAction.MoveStepUp -> {
                moveWorkoutEditorStep(stepId = action.stepId, direction = -1)
            }
            is WorkoutEditorAction.MoveStepDown -> {
                moveWorkoutEditorStep(stepId = action.stepId, direction = 1)
            }
            is WorkoutEditorAction.ChangeStepField -> {
                updateWorkoutEditorStepField(action.stepId, action.field, action.value)
            }
        }
    }

    /**
     * Persists editor draft as `.zwo` to the document selected by user.
     */
    fun onWorkoutEditorExportTargetSelected(uri: Uri?) {
        if (uri == null) {
            pendingWorkoutEditorApplyAfterSave = false
            return
        }
        when (val buildResult = WorkoutEditorMapper.buildWorkout(workoutEditorDraftState.value)) {
            is WorkoutEditorBuildResult.Failure -> {
                pendingWorkoutEditorApplyAfterSave = false
                workoutEditorValidationErrorsState.value = buildResult.errors
                setWorkoutEditorStatus(
                    message = appContext.getString(R.string.workout_editor_status_fix_errors_before_save),
                    isError = true,
                )
            }
            is WorkoutEditorBuildResult.Success -> {
                val xml = WorkoutZwoSerializer.serialize(buildResult.workout)
                val writeOk = runCatching {
                    appContext.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                            writer.write(xml)
                        }
                    } ?: throw IllegalStateException("Output stream unavailable")
                }.isSuccess
                if (writeOk) {
                    workoutEditorHasUnsavedChangesState.value = false
                    setWorkoutEditorStatus(
                        message = appContext.getString(R.string.workout_editor_status_saved),
                        isError = false,
                    )
                    if (pendingWorkoutEditorApplyAfterSave) {
                        pendingWorkoutEditorApplyAfterSave = false
                        applyEditorWorkoutToMenuSelection(skipUnsavedPrompt = true)
                    }
                } else {
                    pendingWorkoutEditorApplyAfterSave = false
                    setWorkoutEditorStatus(
                        message = appContext.getString(R.string.workout_editor_status_save_failed),
                        isError = true,
                    )
                }
            }
        }
    }

    /**
     * Starts FTMS device discovery flow for trainer selection.
     */
    fun onSearchFtmsDevicesRequested() {
        requestDeviceScan(DeviceSelectionKind.FTMS)
    }

    /**
     * Starts heart-rate device discovery flow for optional strap selection.
     */
    fun onSearchHrDevicesRequested() {
        requestDeviceScan(DeviceSelectionKind.HEART_RATE)
    }

    /**
     * Closes device picker and stops active scan.
     */
    fun onDismissDeviceSelection() {
        cancelPendingPickerScanRetry()
        cancelPendingPickerStopUnlock()
        cancelPendingScannedDeviceSort()
        bleDeviceScanner.stop()
        activeDeviceSelectionKindState.value = null
        deviceScanInProgressState.value = false
        deviceScanStatusState.value = null
        deviceScanStopEnabledState.value = true
        scannedDevicesState.clear()
        pendingDeviceScanKind = null
        suppressStatusProbesTemporarily()
    }

    /**
     * Persists selected scanned device to FTMS/HR settings based on active picker.
     */
    fun onScannedDeviceSelected(device: ScannedBleDevice) {
        when (activeDeviceSelectionKindState.value) {
            DeviceSelectionKind.FTMS -> applyFtmsDeviceSelection(
                normalizedMac = BluetoothMacAddress.normalizeOrNull(device.macAddress),
                deviceName = device.displayName,
            )
            DeviceSelectionKind.HEART_RATE -> applyHrDeviceSelection(
                normalizedMac = BluetoothMacAddress.normalizeOrNull(device.macAddress),
                deviceName = device.displayName,
            )
            null -> return
        }
        clearConnectionIssuePrompt()
        onDismissDeviceSelection()
    }

    /**
     * Dismisses connection-failure prompt shown after failed trainer auto-connect.
     */
    fun clearConnectionIssuePrompt() {
        uiState.connectionIssueMessage.value = null
        uiState.suggestTrainerSearchAfterConnectionIssue.value = false
    }

    /**
     * Dismisses failure prompt and opens trainer device discovery immediately.
     */
    fun onSearchFtmsDevicesFromConnectionIssue() {
        clearConnectionIssuePrompt()
        onSearchFtmsDevicesRequested()
    }

    /**
     * Accepts only positive numeric FTP input and persists the latest valid value.
     */
    fun onFtpInputChanged(rawInput: String) {
        val sanitized = rawInput.filter { it.isDigit() }.take(ftpInputMaxLength)
        ftpInputTextState.value = sanitized
        val parsed = sanitized.toIntOrNull()
        if (parsed == null || parsed <= 0) {
            ftpInputErrorState.value = appContext.getString(R.string.menu_ftp_error_invalid)
            return
        }
        ftpInputErrorState.value = null
        ftpWattsState.intValue = parsed
        FtpSettingsStorage.saveFtpWatts(appContext, parsed)
        sessionOrchestrator.onFtpWattsChanged()
    }

    /**
     * Start is allowed only when workout import is valid and trainer device is selected.
     */
    fun canStartSession(): Boolean {
        return uiState.workoutReady.value && hasValidTrainerSelection()
    }

    /**
     * Returns whether trainer selection contains a valid persisted FTMS MAC.
     */
    fun hasSelectedFtmsDevice(): Boolean = hasValidTrainerSelection()

    /**
     * Returns whether optional HR selection contains a valid persisted MAC.
     */
    fun hasSelectedHrDevice(): Boolean = currentHrDeviceMac() != null

    private fun resetWorkoutEditorDraft() {
        val empty = WorkoutEditorDraft.empty()
        workoutEditorDraftState.value = empty
        workoutEditorValidationErrorsState.value = WorkoutEditorMapper.validate(empty)
        workoutEditorHasUnsavedChangesState.value = true
        workoutEditorShowSaveBeforeApplyPromptState.value = false
        pendingWorkoutEditorApplyAfterSave = false
        nextWorkoutEditorStepId = (empty.steps.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    private fun loadSelectedWorkoutIntoEditor() {
        val selectedWorkout = uiState.selectedWorkout.value
        if (selectedWorkout == null) {
            setWorkoutEditorStatus(
                message = appContext.getString(R.string.workout_editor_status_no_selected_workout),
                isError = true,
            )
            return
        }
        val importResult = WorkoutEditorMapper.fromWorkout(selectedWorkout)
        updateWorkoutEditorDraft(importResult.draft)
        nextWorkoutEditorStepId = (importResult.draft.steps.maxOfOrNull { it.id } ?: 0L) + 1L
        if (importResult.skippedStepCount > 0) {
            setWorkoutEditorStatus(
                message = appContext.getString(
                    R.string.workout_editor_status_loaded_with_skipped_steps,
                    importResult.skippedStepCount,
                ),
                isError = false,
            )
        } else {
            setWorkoutEditorStatus(
                message = appContext.getString(R.string.workout_editor_status_loaded_selected_workout),
                isError = false,
            )
        }
    }

    private fun applyEditorWorkoutToMenuSelection(skipUnsavedPrompt: Boolean = false) {
        if (!skipUnsavedPrompt && workoutEditorHasUnsavedChangesState.value) {
            workoutEditorShowSaveBeforeApplyPromptState.value = true
            return
        }
        workoutEditorShowSaveBeforeApplyPromptState.value = false
        pendingWorkoutEditorApplyAfterSave = false
        when (val buildResult = WorkoutEditorMapper.buildWorkout(workoutEditorDraftState.value)) {
            is WorkoutEditorBuildResult.Failure -> {
                workoutEditorValidationErrorsState.value = buildResult.errors
                setWorkoutEditorStatus(
                    message = appContext.getString(R.string.workout_editor_status_fix_errors_before_apply),
                    isError = true,
                )
            }
            is WorkoutEditorBuildResult.Success -> {
                val fileName = WorkoutEditorMapper.suggestedFileName(workoutEditorDraftState.value)
                sessionOrchestrator.onWorkoutEdited(
                    workout = buildResult.workout,
                    sourceName = fileName,
                )
                workoutEditorHasUnsavedChangesState.value = false
                clearWorkoutEditorStatus()
                uiState.screen.value = AppScreen.MENU
            }
        }
    }

    private fun updateWorkoutEditorDraft(updated: WorkoutEditorDraft) {
        workoutEditorDraftState.value = updated
        workoutEditorValidationErrorsState.value = WorkoutEditorMapper.validate(updated)
        workoutEditorHasUnsavedChangesState.value = true
    }

    private fun setWorkoutEditorStatus(message: String, isError: Boolean) {
        workoutEditorStatusMessageState.value = message
        workoutEditorStatusIsErrorState.value = isError
    }

    private fun clearWorkoutEditorStatus() {
        workoutEditorStatusMessageState.value = null
        workoutEditorStatusIsErrorState.value = false
    }

    private fun createStepDraft(type: WorkoutEditorStepType): WorkoutEditorStepDraft {
        val id = nextWorkoutEditorStepId++
        return when (type) {
            WorkoutEditorStepType.STEADY -> WorkoutEditorStepDraft.Steady(
                id = id,
                durationSecText = "300",
                powerText = "75",
            )
            WorkoutEditorStepType.RAMP -> WorkoutEditorStepDraft.Ramp(
                id = id,
                durationSecText = "300",
                startPowerText = "60",
                endPowerText = "80",
            )
            WorkoutEditorStepType.INTERVALS -> WorkoutEditorStepDraft.Intervals(
                id = id,
                repeatText = "4",
                onDurationSecText = "60",
                offDurationSecText = "60",
                onPowerText = "100",
                offPowerText = "60",
            )
        }
    }

    private fun moveWorkoutEditorStep(stepId: Long, direction: Int) {
        val draft = workoutEditorDraftState.value
        val index = draft.steps.indexOfFirst { it.id == stepId }
        if (index < 0) return
        val targetIndex = index + direction
        if (targetIndex !in draft.steps.indices) return
        val reordered = draft.steps.toMutableList()
        val current = reordered.removeAt(index)
        reordered.add(targetIndex, current)
        updateWorkoutEditorDraft(draft.copy(steps = reordered))
    }

    private fun updateWorkoutEditorStepField(stepId: Long, field: WorkoutEditorStepField, rawValue: String) {
        val draft = workoutEditorDraftState.value
        val updated = draft.steps.map { step ->
            if (step.id != stepId) return@map step
            when (step) {
                is WorkoutEditorStepDraft.Steady -> when (field) {
                    WorkoutEditorStepField.DURATION_SEC -> step.copy(durationSecText = rawValue)
                    WorkoutEditorStepField.POWER -> step.copy(powerText = rawValue)
                    else -> step
                }
                is WorkoutEditorStepDraft.Ramp -> when (field) {
                    WorkoutEditorStepField.DURATION_SEC -> step.copy(durationSecText = rawValue)
                    WorkoutEditorStepField.START_POWER -> step.copy(startPowerText = rawValue)
                    WorkoutEditorStepField.END_POWER -> step.copy(endPowerText = rawValue)
                    else -> step
                }
                is WorkoutEditorStepDraft.Intervals -> when (field) {
                    WorkoutEditorStepField.REPEAT -> step.copy(repeatText = rawValue)
                    WorkoutEditorStepField.ON_DURATION_SEC -> step.copy(onDurationSecText = rawValue)
                    WorkoutEditorStepField.OFF_DURATION_SEC -> step.copy(offDurationSecText = rawValue)
                    WorkoutEditorStepField.ON_POWER -> step.copy(onPowerText = rawValue)
                    WorkoutEditorStepField.OFF_POWER -> step.copy(offPowerText = rawValue)
                    else -> step
                }
            }
        }
        updateWorkoutEditorDraft(draft.copy(steps = updated))
    }

    private fun requestDeviceScan(kind: DeviceSelectionKind) {
        cancelPendingPickerScanRetry()
        cancelPendingPickerStopUnlock()
        cancelPendingScannedDeviceSort()
        cancelPendingStatusProbeResume()
        cancelTrainerStatusProbeScan()
        cancelHrStatusProbeScan()
        if (kind == DeviceSelectionKind.HEART_RATE) {
            // Many HR sensors stop advertising while connected, so close active
            // HR GATT before opening picker to keep device discovery reliable.
            hrClient.close()
            hrConnectedState.value = false
            uiState.heartRate.value = null
            sessionManager.updateHeartRate(null)
        }
        pendingDeviceScanKind = kind
        val permissionAvailable = ensureBluetoothScanPermissionCallback?.invoke() == true
        if (!permissionAvailable) {
            return
        }
        pendingDeviceScanKind = null
        startDeviceScan(kind)
    }

    private fun startDeviceScan(kind: DeviceSelectionKind, allowRetryOnTooFrequent: Boolean = true) {
        cancelPendingPickerScanRetry()
        cancelPendingPickerStopUnlock()
        cancelPendingScannedDeviceSort()
        cancelTrainerStatusProbeScan()
        cancelHrStatusProbeScan()
        bleDeviceScanner.stop()
        activeDeviceSelectionKindState.value = kind
        scannedDevicesState.clear()
        deviceScanInProgressState.value = true
        deviceScanStatusState.value = appContext.getString(R.string.menu_device_scan_status_scanning)
        lockPickerStopButton()

        val targetServiceUuid = when (kind) {
            DeviceSelectionKind.FTMS -> ftmsServiceUuid
            DeviceSelectionKind.HEART_RATE -> hrServiceUuid
        }

        val started = bleDeviceScanner.start(
            serviceUuid = targetServiceUuid,
            scanMode = pickerScanMode,
            onDeviceFound = { device ->
                addOrUpdateScannedDevice(device)
            },
            onFinished = onFinished@{ errorMessage ->
                val shouldRetry = DeviceScanPolicy.shouldRetryTooFrequent(
                    allowRetryOnTooFrequent = allowRetryOnTooFrequent,
                    errorMessage = errorMessage,
                    isSelectionStillActive = activeDeviceSelectionKindState.value == kind,
                )
                if (shouldRetry) {
                    deviceScanInProgressState.value = true
                    deviceScanStatusState.value = appContext.getString(R.string.menu_device_scan_status_retrying)
                    lockPickerStopButton()
                    val retryRunnable = Runnable {
                        pendingPickerScanRetry = null
                        if (closed) return@Runnable
                        if (activeDeviceSelectionKindState.value != kind) return@Runnable
                        startDeviceScan(kind, allowRetryOnTooFrequent = false)
                    }
                    pendingPickerScanRetry = retryRunnable
                    mainHandler.postDelayed(retryRunnable, pickerScanRetryDelayMs)
                    return@onFinished
                }

                deviceScanInProgressState.value = false
                deviceScanStopEnabledState.value = true
                cancelPendingPickerStopUnlock()
                flushPendingScannedDeviceSort()
                val completion = DeviceScanPolicy.classifyCompletion(
                    errorMessage = errorMessage,
                    resultCount = scannedDevicesState.size,
                )
                deviceScanStatusState.value =
                    when (completion) {
                        DeviceScanPolicy.Completion.ERROR -> errorMessage
                        DeviceScanPolicy.Completion.NO_RESULTS -> {
                            appContext.getString(R.string.menu_device_scan_status_no_results)
                        }
                        DeviceScanPolicy.Completion.DONE -> {
                            appContext.getString(R.string.menu_device_scan_status_done, scannedDevicesState.size)
                        }
                    }
            },
        )

        if (!started) {
            deviceScanInProgressState.value = false
            deviceScanStopEnabledState.value = true
            cancelPendingPickerStopUnlock()
            if (deviceScanStatusState.value == null) {
                deviceScanStatusState.value = appContext.getString(R.string.menu_device_scan_status_failed)
            }
        }
    }

    private fun cancelPendingPickerScanRetry() {
        pendingPickerScanRetry?.let { mainHandler.removeCallbacks(it) }
        pendingPickerScanRetry = null
    }

    private fun lockPickerStopButton() {
        deviceScanStopEnabledState.value = false
        cancelPendingPickerStopUnlock()
        val unlockRunnable = Runnable {
            pendingPickerStopUnlock = null
            if (!deviceScanInProgressState.value) return@Runnable
            deviceScanStopEnabledState.value = true
        }
        pendingPickerStopUnlock = unlockRunnable
        mainHandler.postDelayed(unlockRunnable, pickerStopButtonLockDurationMs)
    }

    private fun cancelPendingPickerStopUnlock() {
        pendingPickerStopUnlock?.let { mainHandler.removeCallbacks(it) }
        pendingPickerStopUnlock = null
    }

    private fun scheduleScannedDeviceSort() {
        if (pendingScannedDeviceSort != null) return
        val sortRunnable = Runnable {
            pendingScannedDeviceSort = null
            applyScannedDeviceSort()
        }
        pendingScannedDeviceSort = sortRunnable
        mainHandler.postDelayed(sortRunnable, scannedDeviceSortThrottleMs)
    }

    private fun flushPendingScannedDeviceSort() {
        val pending = pendingScannedDeviceSort ?: return
        mainHandler.removeCallbacks(pending)
        pendingScannedDeviceSort = null
        applyScannedDeviceSort()
    }

    private fun cancelPendingScannedDeviceSort() {
        pendingScannedDeviceSort?.let { mainHandler.removeCallbacks(it) }
        pendingScannedDeviceSort = null
    }

    private fun applyScannedDeviceSort() {
        if (scannedDevicesState.size <= 1) return
        val sorted = ScannedDeviceListPolicy.sortedBySignal(scannedDevicesState)
        val orderChanged = sorted.indices.any { index ->
            sorted[index].macAddress != scannedDevicesState[index].macAddress
        }
        if (!orderChanged) return
        scannedDevicesState.clear()
        scannedDevicesState.addAll(sorted)
    }

    /**
     * Avoids immediate passive probe scans right after closing picker, so rapid
     * picker reopen does not hit platform scan-frequency throttling.
     */
    private fun suppressStatusProbesTemporarily() {
        cancelPendingStatusProbeResume()
        statusProbeSuppressedUntilElapsedMs =
            SystemClock.elapsedRealtime() + statusProbeResumeDelayAfterPickerMs
        val resumeRunnable = Runnable {
            pendingStatusProbeResume = null
            if (closed) return@Runnable
            if (uiState.screen.value != AppScreen.MENU) return@Runnable
            if (activeDeviceSelectionKindState.value != null || deviceScanInProgressState.value) return@Runnable
            probeTrainerAvailabilityNow()
            probeHrAvailabilityNow()
        }
        pendingStatusProbeResume = resumeRunnable
        mainHandler.postDelayed(resumeRunnable, statusProbeResumeDelayAfterPickerMs)
    }

    private fun cancelPendingStatusProbeResume() {
        pendingStatusProbeResume?.let { mainHandler.removeCallbacks(it) }
        pendingStatusProbeResume = null
    }

    private fun addOrUpdateScannedDevice(device: ScannedBleDevice) {
        if (deviceScanInProgressState.value) {
            deviceScanStopEnabledState.value = true
            cancelPendingPickerStopUnlock()
        }
        val listChanged = ScannedDeviceListPolicy.upsert(
            devices = scannedDevicesState,
            incoming = device,
        )
        if (listChanged) {
            scheduleScannedDeviceSort()
        }
    }

    private fun applyFtmsDeviceSelection(normalizedMac: String?, deviceName: String?) {
        cancelTrainerStatusProbeScan()
        if (normalizedMac == null) {
            selectedFtmsDeviceMacState.value = null
            ftmsDeviceNameState.value = ""
            ftmsReachableState.value = null
            DeviceSettingsStorage.saveFtmsDeviceMac(appContext, null)
            DeviceSettingsStorage.saveFtmsDeviceName(appContext, null)
            return
        }
        selectedFtmsDeviceMacState.value = normalizedMac
        val normalizedName = deviceName?.trim()?.takeIf { it.isNotEmpty() }
        ftmsDeviceNameState.value = normalizedName.orEmpty()
        ftmsReachableState.value = null
        DeviceSettingsStorage.saveFtmsDeviceMac(appContext, normalizedMac)
        DeviceSettingsStorage.saveFtmsDeviceName(appContext, normalizedName)
        probeTrainerAvailabilityNow()
    }

    private fun applyHrDeviceSelection(normalizedMac: String?, deviceName: String?) {
        cancelHrStatusProbeScan()
        if (normalizedMac == null) {
            selectedHrDeviceMacState.value = null
            hrDeviceNameState.value = ""
            hrReachableState.value = null
            hrConsecutiveMisses = 0
            hrLastSeenElapsedMs = null
            hrConnectedState.value = false
            DeviceSettingsStorage.saveHrDeviceMac(appContext, null)
            DeviceSettingsStorage.saveHrDeviceName(appContext, null)
            return
        }
        selectedHrDeviceMacState.value = normalizedMac
        val normalizedName = deviceName?.trim()?.takeIf { it.isNotEmpty() }
        hrDeviceNameState.value = normalizedName.orEmpty()
        hrReachableState.value = null
        hrConsecutiveMisses = 0
        hrLastSeenElapsedMs = null
        DeviceSettingsStorage.saveHrDeviceMac(appContext, normalizedMac)
        DeviceSettingsStorage.saveHrDeviceName(appContext, normalizedName)
        probeHrAvailabilityNow()
    }

    private fun hasValidTrainerSelection(): Boolean = currentFtmsDeviceMac() != null

    private fun currentFtmsDeviceMac(): String? {
        return selectedFtmsDeviceMacState.value?.let(BluetoothMacAddress::normalizeOrNull)
    }

    private fun currentHrDeviceMac(): String? {
        return selectedHrDeviceMacState.value?.let(BluetoothMacAddress::normalizeOrNull)
    }

    /**
     * Starts periodic trainer availability probing while this ViewModel is bound to UI.
     */
    private fun startTrainerStatusPolling() {
        if (trainerStatusProbeLoopRunning) return
        trainerStatusProbeLoopRunning = true
        mainHandler.post(trainerStatusProbeRunnable)
    }

    /**
     * Starts periodic HR availability probing while this ViewModel is bound to UI.
     */
    private fun startHrStatusPolling() {
        if (hrStatusProbeLoopRunning) return
        hrStatusProbeLoopRunning = true
        mainHandler.post(hrStatusProbeRunnable)
    }

    /**
     * Stops periodic trainer availability probing.
     */
    private fun stopTrainerStatusPolling() {
        trainerStatusProbeLoopRunning = false
        mainHandler.removeCallbacks(trainerStatusProbeRunnable)
        cancelTrainerStatusProbeScan()
    }

    /**
     * Stops periodic HR availability probing.
     */
    private fun stopHrStatusPolling() {
        hrStatusProbeLoopRunning = false
        mainHandler.removeCallbacks(hrStatusProbeRunnable)
        cancelHrStatusProbeScan()
    }

    /**
     * Stops any in-flight trainer status probe scan.
     */
    private fun cancelTrainerStatusProbeScan() {
        trainerStatusScanner.stop()
        trainerStatusProbeInProgress = false
    }

    /**
     * Stops any in-flight HR status probe scan.
     */
    private fun cancelHrStatusProbeScan() {
        hrStatusScanner.stop()
        hrStatusProbeInProgress = false
    }

    /**
     * Probes whether the selected trainer is currently discoverable via FTMS advertisements.
     *
     * Probe is intentionally passive and does not open a GATT session, so it
     * cannot interfere with control-point ownership or active workouts.
     */
    private fun probeTrainerAvailabilityNow() {
        if (closed) return
        if (SystemClock.elapsedRealtime() < statusProbeSuppressedUntilElapsedMs) return
        if (uiState.screen.value != AppScreen.MENU) return
        if (activeDeviceSelectionKindState.value != null || deviceScanInProgressState.value) return
        if (trainerStatusProbeInProgress || hrStatusProbeInProgress) return

        val targetMac = currentFtmsDeviceMac()
        if (targetMac == null) {
            ftmsReachableState.value = null
            return
        }

        if (!hasBluetoothScanPermission()) {
            return
        }

        var targetFound = false
        trainerStatusProbeInProgress = true
        val started = trainerStatusScanner.start(
            serviceUuid = ftmsServiceUuid,
            durationMs = trainerStatusProbeDurationMs,
            scanMode = statusProbeScanMode,
            onDeviceFound = { device ->
                if (BluetoothMacAddress.normalizeOrNull(device.macAddress) == targetMac) {
                    targetFound = true
                }
            },
            onFinished = onFinished@{ errorMessage ->
                trainerStatusProbeInProgress = false
                if (errorMessage != null) {
                    // Keep last known state for transient scan failures.
                    probeHrAvailabilityNow()
                    return@onFinished
                }
                if (currentFtmsDeviceMac() == targetMac) {
                    ftmsReachableState.value = targetFound
                }
                probeHrAvailabilityNow()
            },
        )
        if (!started) {
            trainerStatusProbeInProgress = false
            probeHrAvailabilityNow()
        }
    }

    /**
     * Probes whether the selected HR sensor is currently discoverable.
     *
     * Probe remains passive (scan-only) so HR status can update in MENU without
     * creating/holding an actual GATT connection between sessions.
     */
    private fun probeHrAvailabilityNow() {
        if (closed) return
        if (SystemClock.elapsedRealtime() < statusProbeSuppressedUntilElapsedMs) return
        if (uiState.screen.value != AppScreen.MENU) return
        if (activeDeviceSelectionKindState.value != null || deviceScanInProgressState.value) return
        if (trainerStatusProbeInProgress || hrStatusProbeInProgress) return

        val targetMac = currentHrDeviceMac()
        if (targetMac == null) {
            hrReachableState.value = null
            hrConsecutiveMisses = 0
            hrLastSeenElapsedMs = null
            return
        }

        if (!hasBluetoothScanPermission()) {
            return
        }

        var targetFound = false
        hrStatusProbeInProgress = true
        val started = hrStatusScanner.start(
            serviceUuid = hrServiceUuid,
            durationMs = hrStatusProbeDurationMs,
            scanMode = statusProbeScanMode,
            onDeviceFound = { device ->
                if (BluetoothMacAddress.normalizeOrNull(device.macAddress) == targetMac) {
                    targetFound = true
                }
            },
            onFinished = onFinished@{ errorMessage ->
                hrStatusProbeInProgress = false
                if (errorMessage != null) {
                    return@onFinished
                }
                if (currentHrDeviceMac() == targetMac) {
                    if (targetFound) {
                        hrReachableState.value = true
                        hrConsecutiveMisses = 0
                        hrLastSeenElapsedMs = SystemClock.elapsedRealtime()
                    } else {
                        hrConsecutiveMisses += 1
                        val nowElapsedMs = SystemClock.elapsedRealtime()
                        val staleByMisses = hrConsecutiveMisses >= hrStatusMissThreshold
                        val staleByTime = hrLastSeenElapsedMs?.let { lastSeenElapsedMs ->
                            (nowElapsedMs - lastSeenElapsedMs) >= hrStatusStaleTimeoutMs
                        } == true
                        if (staleByMisses || staleByTime) {
                            hrReachableState.value = false
                        }
                    }
                }
            },
        )
        if (!started) {
            hrStatusProbeInProgress = false
        }
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun WorkoutEditorStepDraft.withId(newId: Long): WorkoutEditorStepDraft {
        return when (this) {
            is WorkoutEditorStepDraft.Steady -> copy(id = newId)
            is WorkoutEditorStepDraft.Ramp -> copy(id = newId)
            is WorkoutEditorStepDraft.Intervals -> copy(id = newId)
        }
    }

    override fun onCleared() {
        stopAndClose()
        super.onCleared()
    }

    private fun playExecutionFailureTone() {
        try {
            val generator = errorToneGenerator ?: ToneGenerator(
                AudioManager.STREAM_NOTIFICATION,
                80
            ).also { created ->
                errorToneGenerator = created
            }
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, errorToneDurationMs)
        } catch (_: Throwable) {
            // Tone playback is non-critical; failures should not affect session flow.
        }
    }

    private fun releaseErrorTone() {
        try {
            errorToneGenerator?.release()
        } catch (_: Throwable) {
            // Best-effort release only.
        } finally {
            errorToneGenerator = null
        }
    }
}

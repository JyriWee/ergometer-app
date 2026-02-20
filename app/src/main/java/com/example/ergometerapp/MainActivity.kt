package com.example.ergometerapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.ergometerapp.ui.MainActivityContent
import com.example.ergometerapp.ui.MainActivityUiModel
import com.example.ergometerapp.workout.editor.WorkoutEditorAction

/**
 * App entry point that binds lifecycle/permissions to UI and orchestration services.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val requestBluetoothConnectPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onBluetoothPermissionResult(granted)
        }
    private val requestBluetoothScanPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onBluetoothScanPermissionResult(granted)
        }

    private val selectWorkoutFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            viewModel.onWorkoutFileSelected(uri)
        }
    // Use a generic MIME type so document providers keep the suggested `.zwo` suffix as-is.
    private val exportWorkoutFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            viewModel.onWorkoutEditorExportTargetSelected(uri)
        }
    private val exportSessionFitFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            viewModel.onSessionFitExportTargetSelected(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.bindActivityCallbacks(
            ensureBluetoothConnectPermission = { ensureBluetoothConnectPermission() },
            ensureBluetoothScanPermission = { ensureBluetoothScanPermission() },
            keepScreenOn = { keepScreenOn() },
            allowScreenOff = { allowScreenOff() },
        )

        setContent {
            MainActivityContent(
                model = MainActivityUiModel(
                    screen = viewModel.uiState.screen.value,
                    bikeData = viewModel.uiState.bikeData.value,
                    heartRate = viewModel.uiState.heartRate.value,
                    phase = viewModel.phase(),
                    ftmsReady = viewModel.uiState.ftmsReady.value,
                    ftmsControlGranted = viewModel.uiState.ftmsControlGranted.value,
                    lastTargetPower = viewModel.uiState.lastTargetPower.value,
                    runnerState = viewModel.uiState.runner.value,
                    summary = viewModel.uiState.summary.value,
                    selectedWorkout = viewModel.uiState.selectedWorkout.value,
                    selectedWorkoutFileName = viewModel.uiState.selectedWorkoutFileName.value,
                    selectedWorkoutStepCount = viewModel.uiState.selectedWorkoutStepCount.value,
                    selectedWorkoutPlannedTss = viewModel.uiState.selectedWorkoutPlannedTss.value,
                    selectedWorkoutImportError = viewModel.uiState.selectedWorkoutImportError.value,
                    startEnabled = viewModel.canStartSession(),
                    ftpWatts = viewModel.ftpWattsState.intValue,
                    ftpInputText = viewModel.ftpInputTextState.value,
                    ftpInputError = viewModel.ftpInputErrorState.value,
                    ftmsDeviceName = viewModel.ftmsDeviceNameState.value,
                    ftmsSelected = viewModel.hasSelectedFtmsDevice(),
                    ftmsConnected = viewModel.uiState.ftmsReady.value ||
                        viewModel.ftmsReachableState.value == true,
                    ftmsConnectionKnown = viewModel.uiState.ftmsReady.value ||
                        viewModel.ftmsReachableState.value != null,
                    hrDeviceName = viewModel.hrDeviceNameState.value,
                    hrSelected = viewModel.hasSelectedHrDevice(),
                    hrConnected = viewModel.hrConnectedState.value ||
                        viewModel.hrReachableState.value == true,
                    hrConnectionKnown = viewModel.hrConnectedState.value ||
                        viewModel.hrReachableState.value != null,
                    workoutExecutionModeMessage = viewModel.uiState.workoutExecutionModeMessage.value,
                    workoutExecutionModeIsError = viewModel.uiState.workoutExecutionModeIsError.value,
                    connectionIssueMessage = viewModel.uiState.connectionIssueMessage.value,
                    suggestTrainerSearchAfterConnectionIssue = viewModel.uiState.suggestTrainerSearchAfterConnectionIssue.value,
                    suggestOpenSettingsAfterConnectionIssue = viewModel.uiState.suggestOpenSettingsAfterConnectionIssue.value,
                    activeDeviceSelectionKind = viewModel.activeDeviceSelectionKindState.value,
                    scannedDevices = viewModel.scannedDevicesState.toList(),
                    deviceScanInProgress = viewModel.deviceScanInProgressState.value,
                    deviceScanStatus = viewModel.deviceScanStatusState.value,
                    deviceScanStopEnabled = viewModel.deviceScanStopEnabledState.value,
                    workoutEditorDraft = viewModel.workoutEditorDraftState.value,
                    workoutEditorValidationErrors = viewModel.workoutEditorValidationErrorsState.value,
                    workoutEditorStatusMessage = viewModel.workoutEditorStatusMessageState.value,
                    workoutEditorStatusIsError = viewModel.workoutEditorStatusIsErrorState.value,
                    workoutEditorHasUnsavedChanges = viewModel.workoutEditorHasUnsavedChangesState.value,
                    workoutEditorShowSaveBeforeApplyPrompt = viewModel.workoutEditorShowSaveBeforeApplyPromptState.value,
                    summaryFitExportStatusMessage = viewModel.summaryFitExportStatusMessageState.value,
                    summaryFitExportStatusIsError = viewModel.summaryFitExportStatusIsErrorState.value,
                ),
                onSelectWorkoutFile = { selectWorkoutFile.launch(arrayOf("*/*")) },
                onFtpInputChanged = { input -> viewModel.onFtpInputChanged(input) },
                onSearchFtmsDevices = { viewModel.onSearchFtmsDevicesRequested() },
                onSearchHrDevices = { viewModel.onSearchHrDevicesRequested() },
                onScannedDeviceSelected = { device -> viewModel.onScannedDeviceSelected(device) },
                onDismissDeviceSelection = { viewModel.onDismissDeviceSelection() },
                onDismissConnectionIssue = { viewModel.clearConnectionIssuePrompt() },
                onSearchFtmsDevicesFromConnectionIssue = { viewModel.onSearchFtmsDevicesFromConnectionIssue() },
                onOpenAppSettingsFromConnectionIssue = {
                    viewModel.clearConnectionIssuePrompt()
                    openAppSettings()
                },
                onStartSession = { viewModel.onStartSession() },
                onEndSession = { viewModel.onEndSessionAndGoToSummary() },
                onBackToMenu = { viewModel.onBackToMenu() },
                onWorkoutEditorAction = { action: WorkoutEditorAction ->
                    when (action) {
                        WorkoutEditorAction.LoadSelectedWorkout -> selectWorkoutFile.launch(arrayOf("*/*"))
                        else -> viewModel.onWorkoutEditorAction(action)
                    }
                },
                onRequestWorkoutEditorSave = { suggestedFileName ->
                    exportWorkoutFile.launch(suggestedFileName)
                },
                onRequestSummaryFitExport = {
                    viewModel.prepareSessionFitExport()
                        ?.let { suggestedFileName -> exportSessionFitFile.launch(suggestedFileName) }
                },
            )
        }
    }

    /**
     * Requests BLUETOOTH_CONNECT when needed; required for session GATT operations on Android 12+.
     */
    private fun ensureBluetoothConnectPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothConnectPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return false
        }

        return true
    }

    /**
     * Requests BLUETOOTH_SCAN when needed; required for in-app BLE discovery on Android 12+.
     */
    private fun ensureBluetoothScanPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothScanPermission.launch(Manifest.permission.BLUETOOTH_SCAN)
            return false
        }
        return true
    }

    /**
     * Opens this app's Android settings page for runtime permission recovery.
     */
    private fun openAppSettings() {
        val settingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        startActivity(settingsIntent)
    }

    /**
     * Keeps the screen awake during an active session to avoid lost telemetry visibility.
     */
    private fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Clears the keep-awake flag once session flow is no longer active.
     */
    private fun allowScreenOff() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        if (isChangingConfigurations) {
            viewModel.unbindActivityCallbacks()
        } else if (isFinishing) {
            viewModel.stopAndClose()
        } else {
            viewModel.unbindActivityCallbacks()
        }
        super.onDestroy()
    }
}

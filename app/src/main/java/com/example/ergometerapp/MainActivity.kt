package com.example.ergometerapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.ergometerapp.ui.MainActivityContent
import com.example.ergometerapp.ui.MainActivityUiModel

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
                    selectedWorkoutImportError = viewModel.uiState.selectedWorkoutImportError.value,
                    startEnabled = viewModel.canStartSession(),
                    ftpWatts = viewModel.ftpWattsState.intValue,
                    ftpInputText = viewModel.ftpInputTextState.value,
                    ftpInputError = viewModel.ftpInputErrorState.value,
                    ftmsMacInputText = viewModel.ftmsMacInputTextState.value,
                    ftmsMacInputError = viewModel.ftmsMacInputErrorState.value,
                    ftmsDeviceName = viewModel.ftmsDeviceNameState.value,
                    hrMacInputText = viewModel.hrMacInputTextState.value,
                    hrMacInputError = viewModel.hrMacInputErrorState.value,
                    hrDeviceName = viewModel.hrDeviceNameState.value,
                    connectionIssueMessage = viewModel.uiState.connectionIssueMessage.value,
                    suggestTrainerSearchAfterConnectionIssue = viewModel.uiState.suggestTrainerSearchAfterConnectionIssue.value,
                    activeDeviceSelectionKind = viewModel.activeDeviceSelectionKindState.value,
                    scannedDevices = viewModel.scannedDevicesState.toList(),
                    deviceScanInProgress = viewModel.deviceScanInProgressState.value,
                    deviceScanStatus = viewModel.deviceScanStatusState.value,
                ),
                onSelectWorkoutFile = { selectWorkoutFile.launch(arrayOf("*/*")) },
                onFtpInputChanged = { input -> viewModel.onFtpInputChanged(input) },
                onFtmsMacInputChanged = { input -> viewModel.onFtmsMacInputChanged(input) },
                onHrMacInputChanged = { input -> viewModel.onHrMacInputChanged(input) },
                onSearchFtmsDevices = { viewModel.onSearchFtmsDevicesRequested() },
                onSearchHrDevices = { viewModel.onSearchHrDevicesRequested() },
                onScannedDeviceSelected = { device -> viewModel.onScannedDeviceSelected(device) },
                onDismissDeviceSelection = { viewModel.onDismissDeviceSelection() },
                onDismissConnectionIssue = { viewModel.clearConnectionIssuePrompt() },
                onSearchFtmsDevicesFromConnectionIssue = { viewModel.onSearchFtmsDevicesFromConnectionIssue() },
                onStartSession = { viewModel.onStartSession() },
                onEndSession = { viewModel.onEndSessionAndGoToSummary() },
                onBackToMenu = { viewModel.onBackToMenu() },
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

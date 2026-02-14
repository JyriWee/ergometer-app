package com.example.ergometerapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ergometerapp.ble.HrBleClient
import com.example.ergometerapp.session.SessionManager
import com.example.ergometerapp.session.SessionOrchestrator
import com.example.ergometerapp.ui.MainActivityContent
import com.example.ergometerapp.ui.MainActivityUiModel

/**
 * App entry point that binds lifecycle/permissions to UI and orchestration services.
 */
class MainActivity : ComponentActivity() {
    private val defaultHrDeviceMac = BuildConfig.DEFAULT_HR_DEVICE_MAC
    private val defaultFtpWatts = BuildConfig.DEFAULT_FTP_WATTS.coerceAtLeast(1)

    private val uiState = AppUiState()

    private lateinit var hrClient: HrBleClient
    private lateinit var sessionManager: SessionManager
    private lateinit var sessionOrchestrator: SessionOrchestrator

    private val requestBluetoothConnectPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            sessionOrchestrator.onBluetoothPermissionResult(granted)
        }

    private val selectWorkoutFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            sessionOrchestrator.onWorkoutFileSelected(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this) { state ->
            uiState.session.value = state
        }

        hrClient = HrBleClient(this) { bpm ->
            uiState.heartRate.value = bpm
            sessionManager.updateHeartRate(bpm)
        }

        sessionOrchestrator = SessionOrchestrator(
            context = this,
            uiState = uiState,
            sessionManager = sessionManager,
            ensureBluetoothPermission = { ensureBluetoothPermission() },
            connectHeartRate = { hrClient.connect(defaultHrDeviceMac) },
            closeHeartRate = { hrClient.close() },
            keepScreenOn = { keepScreenOn() },
            allowScreenOff = { allowScreenOff() }
        )
        sessionOrchestrator.initialize()

        setContent {
            MainActivityContent(
                model = MainActivityUiModel(
                    screen = uiState.screen.value,
                    bikeData = uiState.bikeData.value,
                    heartRate = uiState.heartRate.value,
                    phase = sessionManager.getPhase(),
                    ftmsReady = uiState.ftmsReady.value,
                    ftmsControlGranted = uiState.ftmsControlGranted.value,
                    lastTargetPower = uiState.lastTargetPower.value,
                    runnerState = uiState.runner.value,
                    summary = uiState.summary.value,
                    selectedWorkout = uiState.selectedWorkout.value,
                    selectedWorkoutFileName = uiState.selectedWorkoutFileName.value,
                    selectedWorkoutStepCount = uiState.selectedWorkoutStepCount.value,
                    selectedWorkoutImportError = uiState.selectedWorkoutImportError.value,
                    workoutReady = uiState.workoutReady.value,
                    ftpWatts = defaultFtpWatts,
                    showDebugTimeline = uiState.showDebugTimeline.value
                ),
                showDebugTools = BuildConfig.DEBUG,
                onSelectWorkoutFile = { selectWorkoutFile.launch(arrayOf("*/*")) },
                onStartSession = { sessionOrchestrator.startSessionConnection() },
                onEndSession = { sessionOrchestrator.endSessionAndGoToSummary() },
                onBackToMenu = {
                    uiState.summary.value = null
                    uiState.screen.value = AppScreen.MENU
                },
                onToggleDebugTimeline = {
                    uiState.showDebugTimeline.value = !uiState.showDebugTimeline.value
                }
            )
        }

        ensureBluetoothPermission()
    }

    /**
     * Requests BLUETOOTH_CONNECT when needed; required for GATT operations on Android 12+.
     */
    private fun ensureBluetoothPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothConnectPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return false
        }

        sessionOrchestrator.connectBleClients()
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
        sessionOrchestrator.stopAndClose()
        super.onDestroy()
    }
}

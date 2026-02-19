package com.example.ergometerapp.ui

import androidx.compose.runtime.Composable
import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.DeviceSelectionKind
import com.example.ergometerapp.ScannedBleDevice
import com.example.ergometerapp.workout.editor.WorkoutEditorAction
import com.example.ergometerapp.workout.editor.WorkoutEditorDraft
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.ui.theme.ErgometerAppTheme
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.runner.RunnerState

/**
 * Immutable model used by [MainActivityContent] to render top-level destinations.
 */
internal data class MainActivityUiModel(
    val screen: AppScreen,
    val bikeData: IndoorBikeData?,
    val heartRate: Int?,
    val phase: SessionPhase,
    val ftmsReady: Boolean,
    val ftmsControlGranted: Boolean,
    val lastTargetPower: Int?,
    val runnerState: RunnerState,
    val summary: SessionSummary?,
    val selectedWorkout: WorkoutFile?,
    val selectedWorkoutFileName: String?,
    val selectedWorkoutStepCount: Int?,
    val selectedWorkoutPlannedTss: Double?,
    val selectedWorkoutImportError: String?,
    val startEnabled: Boolean,
    val ftpWatts: Int,
    val ftpInputText: String,
    val ftpInputError: String?,
    val ftmsDeviceName: String,
    val ftmsSelected: Boolean,
    val ftmsConnected: Boolean,
    val ftmsConnectionKnown: Boolean,
    val hrDeviceName: String,
    val hrSelected: Boolean,
    val hrConnected: Boolean,
    val hrConnectionKnown: Boolean,
    val workoutExecutionModeMessage: String?,
    val workoutExecutionModeIsError: Boolean,
    val connectionIssueMessage: String?,
    val suggestTrainerSearchAfterConnectionIssue: Boolean,
    val activeDeviceSelectionKind: DeviceSelectionKind?,
    val scannedDevices: List<ScannedBleDevice>,
    val deviceScanInProgress: Boolean,
    val deviceScanStatus: String?,
    val deviceScanStopEnabled: Boolean,
    val workoutEditorDraft: WorkoutEditorDraft,
    val workoutEditorValidationErrors: List<String>,
    val workoutEditorStatusMessage: String?,
    val workoutEditorStatusIsError: Boolean,
    val workoutEditorHasUnsavedChanges: Boolean,
    val workoutEditorShowSaveBeforeApplyPrompt: Boolean,
    val summaryFitExportStatusMessage: String?,
    val summaryFitExportStatusIsError: Boolean,
)

/**
 * Renders the application root destinations.
 */
@Composable
internal fun MainActivityContent(
    model: MainActivityUiModel,
    onSelectWorkoutFile: () -> Unit,
    onFtpInputChanged: (String) -> Unit,
    onSearchFtmsDevices: () -> Unit,
    onSearchHrDevices: () -> Unit,
    onScannedDeviceSelected: (ScannedBleDevice) -> Unit,
    onDismissDeviceSelection: () -> Unit,
    onDismissConnectionIssue: () -> Unit,
    onSearchFtmsDevicesFromConnectionIssue: () -> Unit,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onBackToMenu: () -> Unit,
    onWorkoutEditorAction: (WorkoutEditorAction) -> Unit,
    onRequestWorkoutEditorSave: (String) -> Unit,
    onRequestSummaryFitExport: () -> Unit,
) {
    ErgometerAppTheme {
        MainDestinationContent(
            model = model,
            onSelectWorkoutFile = onSelectWorkoutFile,
            onFtpInputChanged = onFtpInputChanged,
            onSearchFtmsDevices = onSearchFtmsDevices,
            onSearchHrDevices = onSearchHrDevices,
            onScannedDeviceSelected = onScannedDeviceSelected,
            onDismissDeviceSelection = onDismissDeviceSelection,
            onDismissConnectionIssue = onDismissConnectionIssue,
            onSearchFtmsDevicesFromConnectionIssue = onSearchFtmsDevicesFromConnectionIssue,
            onStartSession = onStartSession,
            onEndSession = onEndSession,
            onBackToMenu = onBackToMenu,
            onWorkoutEditorAction = onWorkoutEditorAction,
            onRequestWorkoutEditorSave = onRequestWorkoutEditorSave,
            onRequestSummaryFitExport = onRequestSummaryFitExport,
        )
    }
}

@Composable
private fun MainDestinationContent(
    model: MainActivityUiModel,
    onSelectWorkoutFile: () -> Unit,
    onFtpInputChanged: (String) -> Unit,
    onSearchFtmsDevices: () -> Unit,
    onSearchHrDevices: () -> Unit,
    onScannedDeviceSelected: (ScannedBleDevice) -> Unit,
    onDismissDeviceSelection: () -> Unit,
    onDismissConnectionIssue: () -> Unit,
    onSearchFtmsDevicesFromConnectionIssue: () -> Unit,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onBackToMenu: () -> Unit,
    onWorkoutEditorAction: (WorkoutEditorAction) -> Unit,
    onRequestWorkoutEditorSave: (String) -> Unit,
    onRequestSummaryFitExport: () -> Unit,
) {
    when (model.screen) {
        AppScreen.MENU -> {
            MenuScreen(
                selectedWorkoutFileName = model.selectedWorkoutFileName,
                selectedWorkoutStepCount = model.selectedWorkoutStepCount,
                selectedWorkoutPlannedTss = model.selectedWorkoutPlannedTss,
                selectedWorkoutImportError = model.selectedWorkoutImportError,
                selectedWorkout = model.selectedWorkout,
                ftpWatts = model.ftpWatts,
                ftpInputText = model.ftpInputText,
                ftpInputError = model.ftpInputError,
                ftmsDeviceName = model.ftmsDeviceName,
                ftmsSelected = model.ftmsSelected,
                ftmsConnected = model.ftmsConnected,
                ftmsConnectionKnown = model.ftmsConnectionKnown,
                hrDeviceName = model.hrDeviceName,
                hrSelected = model.hrSelected,
                hrConnected = model.hrConnected,
                hrConnectionKnown = model.hrConnectionKnown,
                workoutExecutionModeMessage = model.workoutExecutionModeMessage,
                workoutExecutionModeIsError = model.workoutExecutionModeIsError,
                connectionIssueMessage = model.connectionIssueMessage,
                suggestTrainerSearchAfterConnectionIssue = model.suggestTrainerSearchAfterConnectionIssue,
                activeDeviceSelectionKind = model.activeDeviceSelectionKind,
                scannedDevices = model.scannedDevices,
                deviceScanInProgress = model.deviceScanInProgress,
                deviceScanStatus = model.deviceScanStatus,
                deviceScanStopEnabled = model.deviceScanStopEnabled,
                startEnabled = model.startEnabled,
                onSelectWorkoutFile = onSelectWorkoutFile,
                onFtpInputChanged = onFtpInputChanged,
                onSearchFtmsDevices = onSearchFtmsDevices,
                onSearchHrDevices = onSearchHrDevices,
                onScannedDeviceSelected = onScannedDeviceSelected,
                onDismissDeviceSelection = onDismissDeviceSelection,
                onDismissConnectionIssue = onDismissConnectionIssue,
                onSearchFtmsDevicesFromConnectionIssue = onSearchFtmsDevicesFromConnectionIssue,
                onStartSession = onStartSession,
                onOpenWorkoutEditor = { onWorkoutEditorAction(WorkoutEditorAction.OpenEditor) },
            )
        }

        AppScreen.WORKOUT_EDITOR -> {
            WorkoutEditorScreen(
                draft = model.workoutEditorDraft,
                validationErrors = model.workoutEditorValidationErrors,
                statusMessage = model.workoutEditorStatusMessage,
                statusIsError = model.workoutEditorStatusIsError,
                hasUnsavedChanges = model.workoutEditorHasUnsavedChanges,
                showSaveBeforeApplyPrompt = model.workoutEditorShowSaveBeforeApplyPrompt,
                ftpWatts = model.ftpWatts,
                onAction = onWorkoutEditorAction,
                onRequestSave = onRequestWorkoutEditorSave,
            )
        }

        AppScreen.CONNECTING -> {
            ConnectingScreen()
        }

        AppScreen.STOPPING -> {
            StoppingScreen()
        }

        AppScreen.SESSION -> {
            SessionScreen(
                phase = model.phase,
                bikeData = model.bikeData,
                heartRate = model.heartRate,
                ftmsReady = model.ftmsReady,
                ftmsControlGranted = model.ftmsControlGranted,
                selectedWorkout = model.selectedWorkout,
                selectedWorkoutFileName = model.selectedWorkoutFileName,
                ftpWatts = model.ftpWatts,
                runnerState = model.runnerState,
                lastTargetPower = model.lastTargetPower,
                workoutExecutionModeMessage = model.workoutExecutionModeMessage,
                workoutExecutionModeIsError = model.workoutExecutionModeIsError,
                onEndSession = onEndSession
            )
        }

        AppScreen.SUMMARY -> {
            SummaryScreen(
                summary = model.summary,
                fitExportStatusMessage = model.summaryFitExportStatusMessage,
                fitExportStatusIsError = model.summaryFitExportStatusIsError,
                onRequestFitExport = onRequestSummaryFitExport,
                onBackToMenu = onBackToMenu,
            )
        }
    }
}

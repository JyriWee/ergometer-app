package com.example.ergometerapp.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.R
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.workout.editor.WorkoutEditorDraft
import com.example.ergometerapp.workout.runner.RunnerState
import org.junit.Rule
import org.junit.Test

class MainActivityContentFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun criticalFlowScreensRenderExpectedAnchors() {
        val modelState = mutableStateOf(baseModel(screen = AppScreen.MENU))

        composeRule.setContent {
            MainActivityContent(
                model = modelState.value,
                onSelectWorkoutFile = {},
                onFtpInputChanged = {},
                onSearchFtmsDevices = {},
                onSearchHrDevices = {},
                onScannedDeviceSelected = {},
                onDismissDeviceSelection = {},
                onDismissConnectionIssue = {},
                onSearchFtmsDevicesFromConnectionIssue = {},
                onStartSession = {},
                onEndSession = {},
                onBackToMenu = {},
                onWorkoutEditorAction = {},
                onRequestWorkoutEditorSave = {},
            )
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.menu_title)).assertIsDisplayed()

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.CONNECTING)
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.status_connecting)).assertIsDisplayed()

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.SESSION)
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.btn_quit_session_now)).assertIsDisplayed()

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.STOPPING)
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.status_stopping)).assertIsDisplayed()

        composeRule.runOnIdle {
            modelState.value = baseModel(
                screen = AppScreen.SUMMARY,
                summary = SessionSummary(
                    durationSeconds = 120,
                    actualTss = 3.3,
                    avgPower = 200,
                    maxPower = 260,
                    avgCadence = 85,
                    maxCadence = 95,
                    avgHeartRate = 140,
                    maxHeartRate = 158,
                    distanceMeters = 1000,
                    totalEnergyKcal = 35,
                )
            )
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.summary_duration)).assertIsDisplayed()
    }

    private fun baseModel(
        screen: AppScreen,
        summary: SessionSummary? = null,
    ): MainActivityUiModel {
        return MainActivityUiModel(
            screen = screen,
            bikeData = null,
            heartRate = null,
            phase = SessionPhase.RUNNING,
            ftmsReady = true,
            ftmsControlGranted = true,
            lastTargetPower = null,
            runnerState = RunnerState.stopped(workoutElapsedSec = 0),
            summary = summary,
            selectedWorkout = null,
            selectedWorkoutFileName = null,
            selectedWorkoutStepCount = null,
            selectedWorkoutPlannedTss = null,
            selectedWorkoutImportError = null,
            startEnabled = false,
            ftpWatts = 250,
            ftpInputText = "250",
            ftpInputError = null,
            ftmsDeviceName = "Trainer",
            ftmsSelected = true,
            ftmsConnected = true,
            ftmsConnectionKnown = true,
            hrDeviceName = "HR",
            hrSelected = true,
            hrConnected = true,
            hrConnectionKnown = true,
            workoutExecutionModeMessage = null,
            workoutExecutionModeIsError = false,
            connectionIssueMessage = null,
            suggestTrainerSearchAfterConnectionIssue = false,
            activeDeviceSelectionKind = null,
            scannedDevices = emptyList(),
            deviceScanInProgress = false,
            deviceScanStatus = null,
            deviceScanStopEnabled = true,
            workoutEditorDraft = WorkoutEditorDraft.empty(),
            workoutEditorValidationErrors = emptyList(),
            workoutEditorStatusMessage = null,
            workoutEditorStatusIsError = false,
            workoutEditorHasUnsavedChanges = false,
            workoutEditorShowSaveBeforeApplyPrompt = false,
        )
    }
}

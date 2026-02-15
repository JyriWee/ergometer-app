package com.example.ergometerapp.ui

import androidx.compose.runtime.Composable
import com.example.ergometerapp.AppScreen
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
    val selectedWorkoutImportError: String?,
    val workoutReady: Boolean,
    val ftpWatts: Int,
    val ftpInputText: String,
    val ftpInputError: String?,
)

/**
 * Renders the application root destinations.
 */
@Composable
internal fun MainActivityContent(
    model: MainActivityUiModel,
    onSelectWorkoutFile: () -> Unit,
    onFtpInputChanged: (String) -> Unit,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onBackToMenu: () -> Unit,
) {
    ErgometerAppTheme {
        MainDestinationContent(
            model = model,
            onSelectWorkoutFile = onSelectWorkoutFile,
            onFtpInputChanged = onFtpInputChanged,
            onStartSession = onStartSession,
            onEndSession = onEndSession,
            onBackToMenu = onBackToMenu
        )
    }
}

@Composable
private fun MainDestinationContent(
    model: MainActivityUiModel,
    onSelectWorkoutFile: () -> Unit,
    onFtpInputChanged: (String) -> Unit,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onBackToMenu: () -> Unit
) {
    when (model.screen) {
        AppScreen.MENU -> {
            MenuScreen(
                selectedWorkoutFileName = model.selectedWorkoutFileName,
                selectedWorkoutStepCount = model.selectedWorkoutStepCount,
                selectedWorkoutImportError = model.selectedWorkoutImportError,
                selectedWorkout = model.selectedWorkout,
                ftpWatts = model.ftpWatts,
                ftpInputText = model.ftpInputText,
                ftpInputError = model.ftpInputError,
                startEnabled = model.workoutReady,
                onSelectWorkoutFile = onSelectWorkoutFile,
                onFtpInputChanged = onFtpInputChanged,
                onStartSession = onStartSession
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
                onEndSession = onEndSession
            )
        }

        AppScreen.SUMMARY -> {
            SummaryScreen(
                summary = model.summary,
                onBackToMenu = onBackToMenu
            )
        }
    }
}

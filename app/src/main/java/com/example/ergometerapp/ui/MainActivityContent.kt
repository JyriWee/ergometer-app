package com.example.ergometerapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.R
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.ui.debug.FtmsDebugTimelineScreen
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
    val showDebugTimeline: Boolean
)

/**
 * Renders the application root destinations and optional debug overlays.
 */
@Composable
internal fun MainActivityContent(
    model: MainActivityUiModel,
    showDebugTools: Boolean,
    onSelectWorkoutFile: () -> Unit,
    onFtpInputChanged: (String) -> Unit,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onBackToMenu: () -> Unit,
    onToggleDebugTimeline: () -> Unit
) {
    ErgometerAppTheme {
        if (!showDebugTools) {
            MainDestinationContent(
                model = model,
                onSelectWorkoutFile = onSelectWorkoutFile,
                onFtpInputChanged = onFtpInputChanged,
                onStartSession = onStartSession,
                onEndSession = onEndSession,
                onBackToMenu = onBackToMenu
            )
            return@ErgometerAppTheme
        }

        val debugToggleContentDescription =
            stringResource(R.string.debug_toggle_content_description)

        Box {
            MainDestinationContent(
                model = model,
                onSelectWorkoutFile = onSelectWorkoutFile,
                onFtpInputChanged = onFtpInputChanged,
                onStartSession = onStartSession,
                onEndSession = onEndSession,
                onBackToMenu = onBackToMenu
            )

            FloatingActionButton(
                onClick = onToggleDebugTimeline,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .semantics {
                        contentDescription = debugToggleContentDescription
                    }
            ) {
                Text(stringResource(R.string.debug_toggle))
            }

            if (model.showDebugTimeline) {
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

        AppScreen.SESSION -> {
            SessionScreen(
                phase = model.phase,
                bikeData = model.bikeData,
                heartRate = model.heartRate,
                ftmsReady = model.ftmsReady,
                ftmsControlGranted = model.ftmsControlGranted,
                selectedWorkout = model.selectedWorkout,
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

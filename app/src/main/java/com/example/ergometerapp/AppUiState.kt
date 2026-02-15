package com.example.ergometerapp

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.runner.RunnerState

/**
 * Explicit stop-flow state to avoid ambiguous combinations of stop-related flags.
 */
enum class StopFlowState {
    IDLE,
    STOPPING_AWAIT_ACK,
}

/**
 * Centralized holder for UI-observable state and related session flags.
 *
 * Keeping these fields grouped allows orchestration code to update a single
 * object instead of scattering mutable references across activity scope.
 */
class AppUiState {
    val screen: MutableState<AppScreen> = mutableStateOf(AppScreen.MENU)
    val heartRate: MutableState<Int?> = mutableStateOf(null)
    val bikeData: MutableState<IndoorBikeData?> = mutableStateOf(null)
    val summary: MutableState<SessionSummary?> = mutableStateOf(null)
    val session: MutableState<SessionState?> = mutableStateOf(null)
    val ftmsReady: MutableState<Boolean> = mutableStateOf(false)
    val ftmsControlGranted: MutableState<Boolean> = mutableStateOf(false)
    val lastTargetPower: MutableState<Int?> = mutableStateOf(null)
    val runner: MutableState<RunnerState> = mutableStateOf(RunnerState.stopped())
    val selectedWorkout: MutableState<WorkoutFile?> = mutableStateOf(null)
    val selectedWorkoutFileName: MutableState<String?> = mutableStateOf(null)
    val selectedWorkoutStepCount: MutableState<Int?> = mutableStateOf(null)
    val selectedWorkoutImportError: MutableState<String?> = mutableStateOf(null)
    val workoutReady: MutableState<Boolean> = mutableStateOf(false)
    val stopFlowState: MutableState<StopFlowState> = mutableStateOf(StopFlowState.IDLE)

    var reconnectBleOnNextSessionStart: Boolean = false
    var pendingSessionStartAfterPermission: Boolean = false
    var pendingCadenceStartAfterControlGranted: Boolean = false
    var autoPausedByZeroCadence: Boolean = false
}

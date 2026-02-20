package com.example.ergometerapp.session

import android.content.ContextWrapper
import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.AppUiState
import com.example.ergometerapp.StopFlowState
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOrchestratorFlowTest {

    @Test
    fun requestControlRejectedRollsBackToMenuWithRecoveryPrompt() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true

        harness.orchestrator.simulateRequestControlRejectedForTest(resultCode = 0x05)

        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertTrue(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertNotNull(harness.uiState.connectionIssueMessage.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun requestControlTimeoutRollsBackToMenuWithRecoveryPrompt() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true

        harness.orchestrator.simulateRequestControlTimeoutForTest()

        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertTrue(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertNotNull(harness.uiState.connectionIssueMessage.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun requestControlSuccessFromConnectingTransitionsToSession() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING

        harness.orchestrator.simulateRequestControlGrantedForTest()

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertEquals(SessionPhase.RUNNING, harness.sessionManager.getPhase())
        assertTrue(harness.uiState.pendingCadenceStartAfterControlGranted)
        assertEquals(1, harness.currentKeepScreenOnCalls)
    }

    @Test
    fun stopFlowCompletesToSummaryOnAcknowledgement() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION

        harness.orchestrator.endSessionAndGoToSummary()

        assertEquals(AppScreen.STOPPING, harness.uiState.screen.value)
        assertEquals(StopFlowState.STOPPING_AWAIT_ACK, harness.uiState.stopFlowState.value)
        assertNull(harness.uiState.summary.value)

        harness.orchestrator.simulateStopAcknowledgedForTest()

        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun startAndStopFlowTransitionCompletesToSummaryOnAcknowledgement() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING

        harness.orchestrator.simulateRequestControlGrantedForTest()

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertEquals(SessionPhase.RUNNING, harness.sessionManager.getPhase())
        assertEquals(1, harness.currentKeepScreenOnCalls)

        harness.orchestrator.endSessionAndGoToSummary()
        assertEquals(AppScreen.STOPPING, harness.uiState.screen.value)
        assertEquals(StopFlowState.STOPPING_AWAIT_ACK, harness.uiState.stopFlowState.value)

        harness.orchestrator.simulateStopAcknowledgedForTest()

        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
        assertNotNull(harness.uiState.summary.value)
    }

    @Test
    fun stopFlowTimeoutCompletesToSummaryWithoutAcknowledgement() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION

        harness.orchestrator.endSessionAndGoToSummary()

        assertEquals(AppScreen.STOPPING, harness.uiState.screen.value)
        assertEquals(StopFlowState.STOPPING_AWAIT_ACK, harness.uiState.stopFlowState.value)

        manualHandler.advanceBy(3999L)
        assertEquals(AppScreen.STOPPING, harness.uiState.screen.value)
        assertEquals(StopFlowState.STOPPING_AWAIT_ACK, harness.uiState.stopFlowState.value)

        manualHandler.advanceBy(1L)
        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun connectFlowTimeoutRollsBackToMenuWithRecoveryPrompt() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()

        harness.orchestrator.beginConnectFlowForTest()
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        manualHandler.advanceBy(14_999L)
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        manualHandler.advanceBy(1L)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertTrue(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertNotNull(harness.uiState.connectionIssueMessage.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun connectFlowTimeoutIsCancelledAfterRequestControlGranted() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()

        harness.orchestrator.beginConnectFlowForTest()
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        harness.orchestrator.simulateRequestControlGrantedForTest()
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertEquals(SessionPhase.RUNNING, harness.sessionManager.getPhase())

        manualHandler.advanceBy(15_000L)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertEquals(0, harness.currentAllowScreenOffCalls)
        assertNull(harness.uiState.connectionIssueMessage.value)
    }

    @Test
    fun connectPermissionDeniedThenGrantedKeepsFlowStableUntilExplicitRetry() {
        var connectPermissionGranted = false
        val harness = createHarness(
            ensureBluetoothPermission = { connectPermissionGranted },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()

        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)

        harness.orchestrator.onBluetoothPermissionResult(granted = false)
        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertTrue(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertNotNull(harness.uiState.connectionIssueMessage.value)

        connectPermissionGranted = true
        harness.orchestrator.onBluetoothPermissionResult(granted = true)
        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertTrue(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
    }

    @Test
    fun connectPermissionDeniedAllowsPendingStartToBeReArmedOnExplicitRetry() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)

        harness.orchestrator.onBluetoothPermissionResult(granted = false)
        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertTrue(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)

        harness.orchestrator.startSessionConnection()
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertNull(harness.uiState.connectionIssueMessage.value)
    }

    private fun createHarness(
        mainHandler: android.os.Handler = ManualHandler(),
        ensureBluetoothPermission: () -> Boolean = { true },
    ): Harness {
        val uiState = AppUiState()
        val context = ContextWrapper(null)

        var keepScreenOnCalls = 0
        var allowScreenOffCalls = 0

        val sessionManager = SessionManager(
            context = context,
            onStateUpdated = { state -> uiState.session.value = state },
        )

        val orchestrator = SessionOrchestrator(
            context = context,
            uiState = uiState,
            sessionManager = sessionManager,
            ensureBluetoothPermission = ensureBluetoothPermission,
            connectHeartRate = {},
            closeHeartRate = {},
            keepScreenOn = { keepScreenOnCalls += 1 },
            allowScreenOff = { allowScreenOffCalls += 1 },
            currentFtmsDeviceMac = { "AA:BB:CC:DD:EE:FF" },
            currentFtpWatts = { 250 },
            mainThreadHandler = mainHandler,
        )

        return Harness(
            orchestrator = orchestrator,
            uiState = uiState,
            sessionManager = sessionManager,
            keepScreenOnCounter = { keepScreenOnCalls },
            allowScreenOffCounter = { allowScreenOffCalls },
        )
    }

    private data class Harness(
        val orchestrator: SessionOrchestrator,
        val uiState: AppUiState,
        val sessionManager: SessionManager,
        val keepScreenOnCounter: () -> Int,
        val allowScreenOffCounter: () -> Int,
    ) {
        val currentKeepScreenOnCalls: Int
            get() = keepScreenOnCounter()

        val currentAllowScreenOffCalls: Int
            get() = allowScreenOffCounter()
    }

    private fun readyWorkout(): WorkoutFile {
        return WorkoutFile(
            name = "Permission Flow Workout",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(
                Step.SteadyState(
                    durationSec = 180,
                    power = 0.75,
                    cadence = 90,
                )
            ),
        )
    }

    private class ManualHandler : android.os.Handler(android.os.Looper.getMainLooper()) {
        private data class ScheduledRunnable(
            val runnable: Runnable,
            val runAtMs: Long,
            val order: Long,
        )

        private val queue = mutableListOf<ScheduledRunnable>()
        private var nextOrder = 0L
        private var nowMs = 0L

        override fun post(runnable: Runnable): Boolean {
            queue += ScheduledRunnable(
                runnable = runnable,
                runAtMs = nowMs,
                order = nextOrder++,
            )
            return true
        }

        override fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
            queue += ScheduledRunnable(
                runnable = runnable,
                runAtMs = nowMs + delayMillis.coerceAtLeast(0L),
                order = nextOrder++,
            )
            return true
        }

        override fun removeCallbacks(runnable: Runnable) {
            queue.removeAll { it.runnable === runnable }
        }

        fun advanceBy(deltaMs: Long) {
            require(deltaMs >= 0L) { "Delta must be non-negative." }
            val targetMs = nowMs + deltaMs
            while (true) {
                val next = queue
                    .filter { it.runAtMs <= targetMs }
                    .minWithOrNull(compareBy<ScheduledRunnable> { it.runAtMs }.thenBy { it.order })
                    ?: break
                queue.remove(next)
                nowMs = next.runAtMs
                next.runnable.run()
            }
            nowMs = targetMs
        }
    }

}

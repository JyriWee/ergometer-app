package com.example.ergometerapp.session

import android.content.ContextWrapper
import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.AppUiState
import com.example.ergometerapp.StopFlowState
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

    private fun createHarness(mainHandler: android.os.Handler = ManualHandler()): Harness {
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
            ensureBluetoothPermission = { true },
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
    }
}

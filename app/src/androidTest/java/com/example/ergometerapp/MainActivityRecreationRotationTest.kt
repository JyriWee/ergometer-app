package com.example.ergometerapp

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test

/**
 * Verifies that critical top-level anchors survive orientation-driven activity recreation.
 */
class MainActivityRecreationRotationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun menuAndSessionAnchorsRemainVisibleAcrossRotationRecreation() {
        val menuTitle = composeRule.activity.getString(R.string.menu_title)
        val sessionQuitLabel = composeRule.activity.getString(R.string.btn_quit_session_now)

        try {
            assertNodeWithTextEventually(menuTitle)

            rotateAndWait(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            assertNodeWithTextEventually(menuTitle)

            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.uiState.screen.value = AppScreen.SESSION
                viewModel.uiState.ftmsReady.value = true
                viewModel.uiState.ftmsControlGranted.value = true
            }
            composeRule.waitForIdle()
            assertNodeWithTextEventually(sessionQuitLabel)

            rotateAndWait(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            assertNodeWithTextEventually(sessionQuitLabel)
        } finally {
            composeRule.runOnUiThread {
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun rotateAndWait(requestedOrientation: Int) {
        composeRule.runOnUiThread {
            composeRule.activity.requestedOrientation = requestedOrientation
        }
        composeRule.waitForIdle()
    }

    private fun currentViewModel(): MainViewModel {
        return ViewModelProvider(composeRule.activity)[MainViewModel::class.java]
    }

    private fun assertNodeWithTextEventually(
        text: String,
        timeoutMillis: Long = 10_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            try {
                composeRule.onNodeWithText(text).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: IllegalStateException) {
                false
            }
        }
    }
}

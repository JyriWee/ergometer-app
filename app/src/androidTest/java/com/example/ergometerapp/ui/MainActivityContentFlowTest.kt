package com.example.ergometerapp.ui

import androidx.activity.ComponentActivity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.mutableStateOf
import androidx.test.filters.FlakyTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.DeviceSelectionKind
import com.example.ergometerapp.R
import com.example.ergometerapp.ScannedBleDevice
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.workout.editor.WorkoutEditorDraft
import com.example.ergometerapp.workout.runner.RunnerState
import org.junit.Assert.assertEquals
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
                onOpenAppSettingsFromConnectionIssue = {},
                onStartSession = {},
                onEndSession = {},
                onBackToMenu = {},
                onWorkoutEditorAction = {},
                onRequestWorkoutEditorSave = {},
                onRequestSummaryFitExport = {},
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
                    startTimestampMillis = 1_700_000_000_000L,
                    stopTimestampMillis = 1_700_000_120_000L,
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

    @Test
    fun menuPickerScanAndCloseStatesRenderExpectedActions() {
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
                onOpenAppSettingsFromConnectionIssue = {},
                onStartSession = {},
                onEndSession = {},
                onBackToMenu = {},
                onWorkoutEditorAction = {},
                onRequestWorkoutEditorSave = {},
                onRequestSummaryFitExport = {},
            )
        }

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.MENU).copy(
                activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                deviceScanInProgress = true,
                deviceScanStopEnabled = false,
                deviceScanStatus = composeRule.activity.getString(R.string.menu_device_scan_status_scanning),
            )
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_trainer_picker_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Scanning nearby BLE devices", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_cancel_device_scan)
        ).assertIsDisplayed().assertIsNotEnabled()

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.MENU).copy(
                activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                deviceScanInProgress = false,
                deviceScanStopEnabled = true,
                deviceScanStatus = composeRule.activity.getString(R.string.menu_device_scan_status_failed),
            )
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_device_scan_status_failed)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_close_device_picker)
        ).assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun menuConnectionIssueDialogShowsRecoveryActionsAndRoutesCallbacks() {
        val modelState = mutableStateOf(baseModel(screen = AppScreen.MENU))
        var searchAgainClicks = 0
        var dismissClicks = 0
        val issueMessage = "Request control timed out"

        composeRule.setContent {
            MainActivityContent(
                model = modelState.value,
                onSelectWorkoutFile = {},
                onFtpInputChanged = {},
                onSearchFtmsDevices = {},
                onSearchHrDevices = {},
                onScannedDeviceSelected = {},
                onDismissDeviceSelection = {},
                onDismissConnectionIssue = { dismissClicks += 1 },
                onSearchFtmsDevicesFromConnectionIssue = { searchAgainClicks += 1 },
                onOpenAppSettingsFromConnectionIssue = {},
                onStartSession = {},
                onEndSession = {},
                onBackToMenu = {},
                onWorkoutEditorAction = {},
                onRequestWorkoutEditorSave = {},
                onRequestSummaryFitExport = {},
            )
        }

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.MENU).copy(
                connectionIssueMessage = issueMessage,
                suggestTrainerSearchAfterConnectionIssue = true,
            )
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_connection_issue_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(issueMessage).assertIsDisplayed()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_connection_issue_search_again)
        ).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, searchAgainClicks)
            assertEquals(0, dismissClicks)
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_connection_issue_dismiss)
        ).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, dismissClicks)
        }
    }

    @Test
    fun menuConnectionIssueDialogSupportsOpenSettingsRecoveryAction() {
        val modelState = mutableStateOf(baseModel(screen = AppScreen.MENU))
        var openSettingsClicks = 0
        var dismissClicks = 0
        val issueMessage = "Bluetooth permission is required."

        composeRule.setContent {
            MainActivityContent(
                model = modelState.value,
                onSelectWorkoutFile = {},
                onFtpInputChanged = {},
                onSearchFtmsDevices = {},
                onSearchHrDevices = {},
                onScannedDeviceSelected = {},
                onDismissDeviceSelection = {},
                onDismissConnectionIssue = { dismissClicks += 1 },
                onSearchFtmsDevicesFromConnectionIssue = {},
                onOpenAppSettingsFromConnectionIssue = { openSettingsClicks += 1 },
                onStartSession = {},
                onEndSession = {},
                onBackToMenu = {},
                onWorkoutEditorAction = {},
                onRequestWorkoutEditorSave = {},
                onRequestSummaryFitExport = {},
            )
        }

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.MENU).copy(
                connectionIssueMessage = issueMessage,
                suggestTrainerSearchAfterConnectionIssue = false,
                suggestOpenSettingsAfterConnectionIssue = true,
            )
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_connection_issue_open_settings)
        ).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, openSettingsClicks)
            assertEquals(0, dismissClicks)
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_connection_issue_dismiss)
        ).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, dismissClicks)
        }
    }

    @Test
    @FlakyTest
    fun menuAndSessionAnchorsRemainVisibleAcrossRotation() {
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
                onOpenAppSettingsFromConnectionIssue = {},
                onStartSession = {},
                onEndSession = {},
                onBackToMenu = {},
                onWorkoutEditorAction = {},
                onRequestWorkoutEditorSave = {},
                onRequestSummaryFitExport = {},
            )
        }

        val menuTitle = composeRule.activity.getString(R.string.menu_title)
        val quitLabel = composeRule.activity.getString(R.string.btn_quit_session_now)

        try {
            composeRule.onNodeWithText(menuTitle).assertIsDisplayed()

            composeRule.runOnUiThread {
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(menuTitle).assertIsDisplayed()

            composeRule.runOnIdle {
                modelState.value = baseModel(screen = AppScreen.SESSION)
            }
            composeRule.onNodeWithText(quitLabel).assertIsDisplayed()

            composeRule.runOnUiThread {
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(quitLabel).assertIsDisplayed()
        } finally {
            composeRule.runOnUiThread {
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Test
    fun menuPickerFlowStaysConsistentAcrossScanPermissionDenyThenGrant() {
        val modelState = mutableStateOf(baseModel(screen = AppScreen.MENU))
        val permissionRequired = composeRule.activity.getString(R.string.menu_device_scan_permission_required)
        val scanningStatus = composeRule.activity.getString(R.string.menu_device_scan_status_scanning)
        val doneStatus = composeRule.activity.getString(R.string.menu_device_scan_status_done, 1)

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
                onOpenAppSettingsFromConnectionIssue = {},
                onStartSession = {},
                onEndSession = {},
                onBackToMenu = {},
                onWorkoutEditorAction = {},
                onRequestWorkoutEditorSave = {},
                onRequestSummaryFitExport = {},
            )
        }

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.MENU).copy(
                activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                deviceScanInProgress = false,
                deviceScanStopEnabled = true,
                deviceScanStatus = permissionRequired,
            )
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.menu_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_trainer_picker_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(permissionRequired).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_close_device_picker)
        ).assertIsDisplayed().assertIsEnabled()

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.MENU).copy(
                activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                deviceScanInProgress = true,
                deviceScanStopEnabled = false,
                deviceScanStatus = scanningStatus,
            )
        }

        composeRule.onNodeWithText("Scanning nearby BLE devices", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_cancel_device_scan)
        ).assertIsDisplayed().assertIsNotEnabled()

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.MENU).copy(
                activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                scannedDevices = listOf(
                    ScannedBleDevice(
                        macAddress = "AA:BB:CC:DD:EE:FF",
                        displayName = "Tunturi Trainer",
                        rssi = -48,
                    )
                ),
                deviceScanInProgress = false,
                deviceScanStopEnabled = true,
                deviceScanStatus = doneStatus,
            )
        }

        composeRule.onNodeWithText(doneStatus).assertIsDisplayed()
        composeRule.onNodeWithText("Tunturi Trainer", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_close_device_picker)
        ).performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun startSessionButtonRemainsDisabledWhenStartEnabledIsFalse() {
        val modelState = mutableStateOf(baseModel(screen = AppScreen.MENU).copy(startEnabled = false))
        var startClicks = 0
        val blockedReason = composeRule.activity.getString(R.string.menu_start_blocked_select_workout)

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
                onOpenAppSettingsFromConnectionIssue = {},
                onStartSession = { startClicks += 1 },
                onEndSession = {},
                onBackToMenu = {},
                onWorkoutEditorAction = {},
                onRequestWorkoutEditorSave = {},
                onRequestSummaryFitExport = {},
            )
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_start_session)
        ).assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText(blockedReason).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, startClicks)
        }
    }

    @Test
    fun startSessionAnchorsStayConsistentAcrossConnectPermissionDenyThenGrant() {
        val modelState = mutableStateOf(baseModel(screen = AppScreen.MENU).copy(startEnabled = true))
        var startClicks = 0

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
                onOpenAppSettingsFromConnectionIssue = {},
                onStartSession = { startClicks += 1 },
                onEndSession = {},
                onBackToMenu = {},
                onWorkoutEditorAction = {},
                onRequestWorkoutEditorSave = {},
                onRequestSummaryFitExport = {},
            )
        }

        val menuTitle = composeRule.activity.getString(R.string.menu_title)
        val startSession = composeRule.activity.getString(R.string.menu_start_session)
        val connecting = composeRule.activity.getString(R.string.status_connecting)
        val connectingHint = composeRule.activity.getString(R.string.menu_connection_hint)

        composeRule.onNodeWithText(menuTitle).assertIsDisplayed()
        composeRule.onNodeWithText(startSession).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, startClicks)
        }

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.MENU).copy(startEnabled = true)
        }
        composeRule.onNodeWithText(menuTitle).assertIsDisplayed()
        composeRule.onNodeWithText(startSession).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(2, startClicks)
        }

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.CONNECTING)
        }
        composeRule.onNodeWithText(connecting).assertIsDisplayed()
        composeRule.onNodeWithText(connectingHint).assertIsDisplayed()
    }

    @Test
    fun activePickerPermissionDeniedStateSupportsExplicitSearchRetry() {
        val modelState = mutableStateOf(baseModel(screen = AppScreen.MENU))
        var searchTrainerClicks = 0
        val permissionRequired = composeRule.activity.getString(R.string.menu_device_scan_permission_required)
        val scanningStatus = composeRule.activity.getString(R.string.menu_device_scan_status_scanning)
        val searchTrainerLabel = composeRule.activity.getString(R.string.menu_search_trainer_devices_short)

        composeRule.setContent {
            MainActivityContent(
                model = modelState.value,
                onSelectWorkoutFile = {},
                onFtpInputChanged = {},
                onSearchFtmsDevices = { searchTrainerClicks += 1 },
                onSearchHrDevices = {},
                onScannedDeviceSelected = {},
                onDismissDeviceSelection = {},
                onDismissConnectionIssue = {},
                onSearchFtmsDevicesFromConnectionIssue = {},
                onOpenAppSettingsFromConnectionIssue = {},
                onStartSession = {},
                onEndSession = {},
                onBackToMenu = {},
                onWorkoutEditorAction = {},
                onRequestWorkoutEditorSave = {},
                onRequestSummaryFitExport = {},
            )
        }

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.MENU).copy(
                activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                deviceScanInProgress = false,
                deviceScanStopEnabled = true,
                deviceScanStatus = permissionRequired,
            )
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_trainer_picker_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(permissionRequired).assertIsDisplayed()
        composeRule.onNodeWithText(searchTrainerLabel).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, searchTrainerClicks)
        }

        composeRule.runOnIdle {
            modelState.value = baseModel(screen = AppScreen.MENU).copy(
                activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                deviceScanInProgress = true,
                deviceScanStopEnabled = false,
                deviceScanStatus = scanningStatus,
            )
        }

        composeRule.onNodeWithText("Scanning nearby BLE devices", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_cancel_device_scan)
        ).assertIsDisplayed().assertIsNotEnabled()
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
            suggestOpenSettingsAfterConnectionIssue = false,
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
            summaryFitExportStatusMessage = null,
            summaryFitExportStatusIsError = false,
        )
    }
}

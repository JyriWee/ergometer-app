package com.example.ergometerapp

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.example.ergometerapp.ble.HrBleClient
import com.example.ergometerapp.session.SessionManager
import com.example.ergometerapp.session.SessionOrchestrator

/**
 * Holds app/session state across Activity recreation.
 *
 * The ViewModel owns long-lived session services so orientation changes do not
 * tear down active BLE connections or workout execution.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val defaultFtpWatts = BuildConfig.DEFAULT_FTP_WATTS.coerceAtLeast(1)
    private val ftpInputMaxLength = 4

    val uiState = AppUiState()
    val ftpWattsState = mutableIntStateOf(defaultFtpWatts)
    val ftpInputTextState = mutableStateOf(defaultFtpWatts.toString())
    val ftpInputErrorState = mutableStateOf<String?>(null)
    val ftmsMacInputTextState = mutableStateOf("")
    val ftmsMacInputErrorState = mutableStateOf<String?>(null)
    val hrMacInputTextState = mutableStateOf("")
    val hrMacInputErrorState = mutableStateOf<String?>(null)

    private var ensureBluetoothPermissionCallback: (() -> Boolean)? = null
    private var keepScreenOnCallback: (() -> Unit)? = null
    private var allowScreenOffCallback: (() -> Unit)? = null
    private var closed = false

    private val sessionManager = SessionManager(appContext) { state ->
        uiState.session.value = state
    }

    private val hrClient = HrBleClient(appContext) { bpm ->
        uiState.heartRate.value = bpm
        sessionManager.updateHeartRate(bpm)
    }

    private val sessionOrchestrator = SessionOrchestrator(
        context = appContext,
        uiState = uiState,
        sessionManager = sessionManager,
        ensureBluetoothPermission = { ensureBluetoothPermissionCallback?.invoke() == true },
        connectHeartRate = {
            val hrMac = currentHrDeviceMac()
            if (hrMac != null) {
                hrClient.connect(hrMac)
            }
        },
        closeHeartRate = { hrClient.close() },
        keepScreenOn = { keepScreenOnCallback?.invoke() },
        allowScreenOff = { allowScreenOffCallback?.invoke() },
        currentFtmsDeviceMac = { currentFtmsDeviceMac() },
        currentFtpWatts = { ftpWattsState.intValue },
    )

    init {
        val storedFtpWatts = FtpSettingsStorage.loadFtpWatts(appContext, defaultFtpWatts)
        val storedFtmsMac = DeviceSettingsStorage.loadFtmsDeviceMac(appContext).orEmpty()
        val storedHrMac = DeviceSettingsStorage.loadHrDeviceMac(appContext).orEmpty()

        ftpWattsState.intValue = storedFtpWatts
        ftpInputTextState.value = storedFtpWatts.toString()
        ftpInputErrorState.value = null
        ftmsMacInputTextState.value = storedFtmsMac
        hrMacInputTextState.value = storedHrMac
        ftmsMacInputErrorState.value = validateFtmsMacError(storedFtmsMac)
        hrMacInputErrorState.value = validateOptionalMacError(storedHrMac)
        sessionOrchestrator.initialize()
    }

    /**
     * Binds Activity-specific callbacks after recreation.
     */
    fun bindActivityCallbacks(
        ensureBluetoothPermission: () -> Boolean,
        keepScreenOn: () -> Unit,
        allowScreenOff: () -> Unit,
    ) {
        ensureBluetoothPermissionCallback = ensureBluetoothPermission
        keepScreenOnCallback = keepScreenOn
        allowScreenOffCallback = allowScreenOff
        if (uiState.screen.value == AppScreen.SESSION || uiState.screen.value == AppScreen.STOPPING) {
            keepScreenOn()
        }
    }

    /**
     * Unbinds Activity callbacks before a configuration-driven teardown.
     */
    fun unbindActivityCallbacks() {
        ensureBluetoothPermissionCallback = null
        keepScreenOnCallback = null
        allowScreenOffCallback = null
    }

    /**
     * Stops runtime services exactly once when app flow is finishing.
     */
    fun stopAndClose() {
        if (closed) return
        closed = true
        sessionOrchestrator.stopAndClose()
    }

    /**
     * Returns the current session phase for rendering.
     */
    fun phase() = sessionManager.getPhase()

    fun onBluetoothPermissionResult(granted: Boolean) {
        sessionOrchestrator.onBluetoothPermissionResult(granted)
    }

    fun onWorkoutFileSelected(uri: Uri?) {
        sessionOrchestrator.onWorkoutFileSelected(uri)
    }

    fun onStartSession() {
        val ftmsError = validateFtmsMacError(ftmsMacInputTextState.value)
        ftmsMacInputErrorState.value = ftmsError
        if (ftmsError != null) {
            return
        }
        sessionOrchestrator.startSessionConnection()
    }

    fun onEndSessionAndGoToSummary() {
        sessionOrchestrator.endSessionAndGoToSummary()
    }

    fun onBackToMenu() {
        uiState.summary.value = null
        uiState.screen.value = AppScreen.MENU
        allowScreenOffCallback?.invoke()
    }

    /**
     * Accepts only positive numeric FTP input and persists the latest valid value.
     */
    fun onFtpInputChanged(rawInput: String) {
        val sanitized = rawInput.filter { it.isDigit() }.take(ftpInputMaxLength)
        ftpInputTextState.value = sanitized
        val parsed = sanitized.toIntOrNull()
        if (parsed == null || parsed <= 0) {
            ftpInputErrorState.value = appContext.getString(R.string.menu_ftp_error_invalid)
            return
        }
        ftpInputErrorState.value = null
        ftpWattsState.intValue = parsed
        FtpSettingsStorage.saveFtpWatts(appContext, parsed)
    }

    /**
     * Accepts and persists FTMS trainer MAC address in canonical format.
     */
    fun onFtmsMacInputChanged(rawInput: String) {
        val sanitized = BluetoothMacAddress.sanitizeUserInput(rawInput)
        ftmsMacInputTextState.value = sanitized

        val normalized = BluetoothMacAddress.normalizeOrNull(sanitized)
        val error = validateFtmsMacError(sanitized)
        ftmsMacInputErrorState.value = error

        if (normalized != null) {
            ftmsMacInputTextState.value = normalized
            DeviceSettingsStorage.saveFtmsDeviceMac(appContext, normalized)
        } else {
            DeviceSettingsStorage.saveFtmsDeviceMac(appContext, null)
        }
    }

    /**
     * Accepts and persists optional HR strap MAC address in canonical format.
     */
    fun onHrMacInputChanged(rawInput: String) {
        val sanitized = BluetoothMacAddress.sanitizeUserInput(rawInput)
        hrMacInputTextState.value = sanitized

        val normalized = BluetoothMacAddress.normalizeOrNull(sanitized)
        val error = validateOptionalMacError(sanitized)
        hrMacInputErrorState.value = error

        if (normalized != null) {
            hrMacInputTextState.value = normalized
            DeviceSettingsStorage.saveHrDeviceMac(appContext, normalized)
        } else {
            DeviceSettingsStorage.saveHrDeviceMac(appContext, null)
        }
    }

    /**
     * Start is allowed only when workout import is valid and FTMS device address is valid.
     */
    fun canStartSession(): Boolean {
        return uiState.workoutReady.value && validateFtmsMacError(ftmsMacInputTextState.value) == null
    }

    private fun validateFtmsMacError(input: String): String? {
        if (input.isBlank()) {
            return appContext.getString(R.string.menu_ftms_mac_error_required)
        }
        if (BluetoothMacAddress.normalizeOrNull(input) == null) {
            return appContext.getString(R.string.menu_mac_error_invalid)
        }
        return null
    }

    private fun validateOptionalMacError(input: String): String? {
        if (input.isBlank()) return null
        if (BluetoothMacAddress.normalizeOrNull(input) == null) {
            return appContext.getString(R.string.menu_mac_error_invalid)
        }
        return null
    }

    private fun currentFtmsDeviceMac(): String? {
        return BluetoothMacAddress.normalizeOrNull(ftmsMacInputTextState.value)
    }

    private fun currentHrDeviceMac(): String? {
        return BluetoothMacAddress.normalizeOrNull(hrMacInputTextState.value)
    }

    override fun onCleared() {
        stopAndClose()
        super.onCleared()
    }
}

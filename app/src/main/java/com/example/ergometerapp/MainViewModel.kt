package com.example.ergometerapp

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.example.ergometerapp.ble.BleDeviceScanner
import com.example.ergometerapp.ble.HrBleClient
import com.example.ergometerapp.session.SessionManager
import com.example.ergometerapp.session.SessionOrchestrator
import java.util.UUID

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
    private val ftmsServiceUuid: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val hrServiceUuid: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val errorToneDurationMs = 120

    val uiState = AppUiState()
    val ftpWattsState = mutableIntStateOf(defaultFtpWatts)
    val ftpInputTextState = mutableStateOf(defaultFtpWatts.toString())
    val ftpInputErrorState = mutableStateOf<String?>(null)
    val ftmsDeviceNameState = mutableStateOf("")
    val hrDeviceNameState = mutableStateOf("")
    val activeDeviceSelectionKindState = mutableStateOf<DeviceSelectionKind?>(null)
    val scannedDevicesState = mutableStateListOf<ScannedBleDevice>()
    val deviceScanInProgressState = mutableStateOf(false)
    val deviceScanStatusState = mutableStateOf<String?>(null)
    private val selectedFtmsDeviceMacState = mutableStateOf<String?>(null)
    private val selectedHrDeviceMacState = mutableStateOf<String?>(null)

    private var ensureBluetoothConnectPermissionCallback: (() -> Boolean)? = null
    private var ensureBluetoothScanPermissionCallback: (() -> Boolean)? = null
    private var keepScreenOnCallback: (() -> Unit)? = null
    private var allowScreenOffCallback: (() -> Unit)? = null
    private var pendingDeviceScanKind: DeviceSelectionKind? = null
    private var closed = false
    private val bleDeviceScanner = BleDeviceScanner(appContext)
    private var errorToneGenerator: ToneGenerator? = null

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
        ensureBluetoothPermission = { ensureBluetoothConnectPermissionCallback?.invoke() == true },
        connectHeartRate = {
            val hrMac = currentHrDeviceMac()
            if (hrMac != null) {
                hrClient.connect(hrMac)
            }
        },
        closeHeartRate = { hrClient.close() },
        keepScreenOn = { keepScreenOnCallback?.invoke() },
        allowScreenOff = { allowScreenOffCallback?.invoke() },
        onExecutionMappingFailure = { playExecutionFailureTone() },
        currentFtmsDeviceMac = { currentFtmsDeviceMac() },
        currentFtpWatts = { ftpWattsState.intValue },
    )

    init {
        val storedFtpWatts = FtpSettingsStorage.loadFtpWatts(appContext, defaultFtpWatts)
        val storedFtmsMac = DeviceSettingsStorage.loadFtmsDeviceMac(appContext)
        val storedHrMac = DeviceSettingsStorage.loadHrDeviceMac(appContext)
        val storedFtmsName = DeviceSettingsStorage.loadFtmsDeviceName(appContext).orEmpty()
        val storedHrName = DeviceSettingsStorage.loadHrDeviceName(appContext).orEmpty()

        ftpWattsState.intValue = storedFtpWatts
        ftpInputTextState.value = storedFtpWatts.toString()
        ftpInputErrorState.value = null
        selectedFtmsDeviceMacState.value = storedFtmsMac
        ftmsDeviceNameState.value = storedFtmsName
        selectedHrDeviceMacState.value = storedHrMac
        hrDeviceNameState.value = storedHrName
        sessionOrchestrator.initialize()
    }

    /**
     * Binds Activity-specific callbacks after recreation.
     */
    fun bindActivityCallbacks(
        ensureBluetoothConnectPermission: () -> Boolean,
        ensureBluetoothScanPermission: () -> Boolean,
        keepScreenOn: () -> Unit,
        allowScreenOff: () -> Unit,
    ) {
        ensureBluetoothConnectPermissionCallback = ensureBluetoothConnectPermission
        ensureBluetoothScanPermissionCallback = ensureBluetoothScanPermission
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
        ensureBluetoothConnectPermissionCallback = null
        ensureBluetoothScanPermissionCallback = null
        keepScreenOnCallback = null
        allowScreenOffCallback = null
    }

    /**
     * Stops runtime services exactly once when app flow is finishing.
     */
    fun stopAndClose() {
        if (closed) return
        closed = true
        bleDeviceScanner.stop()
        releaseErrorTone()
        sessionOrchestrator.stopAndClose()
    }

    /**
     * Returns the current session phase for rendering.
     */
    fun phase() = sessionManager.getPhase()

    fun onBluetoothPermissionResult(granted: Boolean) {
        sessionOrchestrator.onBluetoothPermissionResult(granted)
    }

    /**
     * Continues pending device scan request after runtime BLUETOOTH_SCAN result.
     */
    fun onBluetoothScanPermissionResult(granted: Boolean) {
        val pendingKind = pendingDeviceScanKind
        pendingDeviceScanKind = null
        if (!granted || pendingKind == null) {
            if (!granted) {
                deviceScanStatusState.value = appContext.getString(R.string.menu_device_scan_permission_required)
                deviceScanInProgressState.value = false
            }
            return
        }
        startDeviceScan(pendingKind)
    }

    fun onWorkoutFileSelected(uri: Uri?) {
        sessionOrchestrator.onWorkoutFileSelected(uri)
    }

    fun onStartSession() {
        if (!hasValidTrainerSelection()) {
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
     * Starts FTMS device discovery flow for trainer selection.
     */
    fun onSearchFtmsDevicesRequested() {
        requestDeviceScan(DeviceSelectionKind.FTMS)
    }

    /**
     * Starts heart-rate device discovery flow for optional strap selection.
     */
    fun onSearchHrDevicesRequested() {
        requestDeviceScan(DeviceSelectionKind.HEART_RATE)
    }

    /**
     * Closes device picker and stops active scan.
     */
    fun onDismissDeviceSelection() {
        bleDeviceScanner.stop()
        activeDeviceSelectionKindState.value = null
        deviceScanInProgressState.value = false
        deviceScanStatusState.value = null
        scannedDevicesState.clear()
        pendingDeviceScanKind = null
    }

    /**
     * Persists selected scanned device to FTMS/HR settings based on active picker.
     */
    fun onScannedDeviceSelected(device: ScannedBleDevice) {
        when (activeDeviceSelectionKindState.value) {
            DeviceSelectionKind.FTMS -> applyFtmsDeviceSelection(
                normalizedMac = BluetoothMacAddress.normalizeOrNull(device.macAddress),
                deviceName = device.displayName,
            )
            DeviceSelectionKind.HEART_RATE -> applyHrDeviceSelection(
                normalizedMac = BluetoothMacAddress.normalizeOrNull(device.macAddress),
                deviceName = device.displayName,
            )
            null -> return
        }
        clearConnectionIssuePrompt()
        onDismissDeviceSelection()
    }

    /**
     * Dismisses connection-failure prompt shown after failed trainer auto-connect.
     */
    fun clearConnectionIssuePrompt() {
        uiState.connectionIssueMessage.value = null
        uiState.suggestTrainerSearchAfterConnectionIssue.value = false
    }

    /**
     * Dismisses failure prompt and opens trainer device discovery immediately.
     */
    fun onSearchFtmsDevicesFromConnectionIssue() {
        clearConnectionIssuePrompt()
        onSearchFtmsDevicesRequested()
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
        sessionOrchestrator.onFtpWattsChanged()
    }

    /**
     * Start is allowed only when workout import is valid and trainer device is selected.
     */
    fun canStartSession(): Boolean {
        return uiState.workoutReady.value && hasValidTrainerSelection()
    }

    private fun requestDeviceScan(kind: DeviceSelectionKind) {
        pendingDeviceScanKind = kind
        val permissionAvailable = ensureBluetoothScanPermissionCallback?.invoke() == true
        if (!permissionAvailable) {
            return
        }
        pendingDeviceScanKind = null
        startDeviceScan(kind)
    }

    private fun startDeviceScan(kind: DeviceSelectionKind) {
        bleDeviceScanner.stop()
        activeDeviceSelectionKindState.value = kind
        scannedDevicesState.clear()
        deviceScanInProgressState.value = true
        deviceScanStatusState.value = appContext.getString(R.string.menu_device_scan_status_scanning)

        val targetServiceUuid = when (kind) {
            DeviceSelectionKind.FTMS -> ftmsServiceUuid
            DeviceSelectionKind.HEART_RATE -> hrServiceUuid
        }

        val started = bleDeviceScanner.start(
            serviceUuid = targetServiceUuid,
            onDeviceFound = { device ->
                addOrUpdateScannedDevice(device)
            },
            onFinished = { errorMessage ->
                deviceScanInProgressState.value = false
                deviceScanStatusState.value =
                    when {
                        errorMessage != null -> errorMessage
                        scannedDevicesState.isEmpty() -> {
                            appContext.getString(R.string.menu_device_scan_status_no_results)
                        }
                        else -> {
                            appContext.getString(R.string.menu_device_scan_status_done, scannedDevicesState.size)
                        }
                    }
            },
        )

        if (!started) {
            deviceScanInProgressState.value = false
            if (deviceScanStatusState.value == null) {
                deviceScanStatusState.value = appContext.getString(R.string.menu_device_scan_status_failed)
            }
        }
    }

    private fun addOrUpdateScannedDevice(device: ScannedBleDevice) {
        val existingIndex = scannedDevicesState.indexOfFirst { it.macAddress == device.macAddress }
        if (existingIndex < 0) {
            scannedDevicesState.add(device)
        } else {
            val existing = scannedDevicesState[existingIndex]
            val preferNew = device.rssi > existing.rssi ||
                (existing.displayName.isNullOrBlank() && !device.displayName.isNullOrBlank())
            if (preferNew) {
                scannedDevicesState[existingIndex] = device
            }
        }
        val sorted = scannedDevicesState.sortedByDescending { it.rssi }
        scannedDevicesState.clear()
        scannedDevicesState.addAll(sorted)
    }

    private fun applyFtmsDeviceSelection(normalizedMac: String?, deviceName: String?) {
        if (normalizedMac == null) {
            selectedFtmsDeviceMacState.value = null
            ftmsDeviceNameState.value = ""
            DeviceSettingsStorage.saveFtmsDeviceMac(appContext, null)
            DeviceSettingsStorage.saveFtmsDeviceName(appContext, null)
            return
        }
        selectedFtmsDeviceMacState.value = normalizedMac
        val normalizedName = deviceName?.trim()?.takeIf { it.isNotEmpty() }
        ftmsDeviceNameState.value = normalizedName.orEmpty()
        DeviceSettingsStorage.saveFtmsDeviceMac(appContext, normalizedMac)
        DeviceSettingsStorage.saveFtmsDeviceName(appContext, normalizedName)
    }

    private fun applyHrDeviceSelection(normalizedMac: String?, deviceName: String?) {
        if (normalizedMac == null) {
            selectedHrDeviceMacState.value = null
            hrDeviceNameState.value = ""
            DeviceSettingsStorage.saveHrDeviceMac(appContext, null)
            DeviceSettingsStorage.saveHrDeviceName(appContext, null)
            return
        }
        selectedHrDeviceMacState.value = normalizedMac
        val normalizedName = deviceName?.trim()?.takeIf { it.isNotEmpty() }
        hrDeviceNameState.value = normalizedName.orEmpty()
        DeviceSettingsStorage.saveHrDeviceMac(appContext, normalizedMac)
        DeviceSettingsStorage.saveHrDeviceName(appContext, normalizedName)
    }

    private fun hasValidTrainerSelection(): Boolean = currentFtmsDeviceMac() != null

    private fun currentFtmsDeviceMac(): String? {
        return selectedFtmsDeviceMacState.value?.let(BluetoothMacAddress::normalizeOrNull)
    }

    private fun currentHrDeviceMac(): String? {
        return selectedHrDeviceMacState.value?.let(BluetoothMacAddress::normalizeOrNull)
    }

    override fun onCleared() {
        stopAndClose()
        super.onCleared()
    }

    private fun playExecutionFailureTone() {
        try {
            val generator = errorToneGenerator ?: ToneGenerator(
                AudioManager.STREAM_NOTIFICATION,
                80
            ).also { created ->
                errorToneGenerator = created
            }
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, errorToneDurationMs)
        } catch (_: Throwable) {
            // Tone playback is non-critical; failures should not affect session flow.
        }
    }

    private fun releaseErrorTone() {
        try {
            errorToneGenerator?.release()
        } catch (_: Throwable) {
            // Best-effort release only.
        } finally {
            errorToneGenerator = null
        }
    }
}

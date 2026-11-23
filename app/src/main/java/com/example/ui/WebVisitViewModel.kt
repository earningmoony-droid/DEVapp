package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AutoScrollDirection
import com.example.model.ScrollIndicatorState
import com.example.model.ScrollSpeed
import com.example.model.TestSessionStatus
import com.example.model.UrlValidationResult
import com.example.model.UrlValidator
import com.example.model.VpnConnectionStatus
import com.example.model.VpnProvider
import com.example.model.VpnVerificationInfo
import com.example.model.WebCommandEvent
import com.example.model.WebCommandType
import com.example.network.VpnMonitorManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebVisitUiState(
    val urlInput: String = "",
    val urlError: String? = null,
    val viewDurationSec: Int = 15,
    val pauseDurationSec: Int = 3,
    val totalRuns: Int = 3,
    val scrollDirection: AutoScrollDirection = AutoScrollDirection.NONE,
    val scrollSpeed: ScrollSpeed = ScrollSpeed.MEDIUM,
    val scrollStatus: ScrollIndicatorState = ScrollIndicatorState.IDLE,
    val autoNextEnabled: Boolean = true,

    // VPN Testing Module (temporary in-memory state only)
    val selectedVpnProvider: VpnProvider = VpnProvider.ANDROID_SYSTEM,
    val installedProviders: Set<VpnProvider> = setOf(VpnProvider.ANDROID_SYSTEM),
    val vpnStatus: VpnConnectionStatus = VpnConnectionStatus.DISCONNECTED,
    val vpnInterfaceName: String? = null,
    val requireVpnForTest: Boolean = false,
    val vpnVerification: VpnVerificationInfo? = null,
    val vpnActionMessage: String? = null,
    val isVerifyingVpn: Boolean = false,

    // Temporary active session state only
    val isRunning: Boolean = false,
    val status: TestSessionStatus = TestSessionStatus.IDLE,
    val activeUrl: String? = null,
    val previewLoadToken: Int = 0,
    val currentRunNumber: Int = 0,
    val activeTotalRuns: Int = 1,
    val phaseRemainingSec: Int = 0,
    val overallProgress: Float = 0f,
    val isPreviewLoading: Boolean = false,
    val previewBlockedReason: String? = null,
    val canGoBack: Boolean = false,
    val webCommand: WebCommandEvent? = null,
    val isHtmlSheetVisible: Boolean = false
)

class WebVisitViewModel(application: Application) : AndroidViewModel(application) {

    private val vpnMonitor = VpnMonitorManager(application.applicationContext)

    private val _uiState = MutableStateFlow(WebVisitUiState())
    val uiState: StateFlow<WebVisitUiState> = _uiState.asStateFlow()

    private var queueJob: Job? = null
    private var commandSeq: Long = 0L
    private var wasVpnActive: Boolean = false

    init {
        refreshInstalledProviders()

        viewModelScope.launch {
            vpnMonitor.transportState.collect { transport ->
                val prevActive = wasVpnActive
                wasVpnActive = transport.isVpnActive

                val currentState = _uiState.value
                val droppedDuringTest = prevActive && !transport.isVpnActive &&
                    currentState.isRunning && currentState.requireVpnForTest

                if (droppedDuringTest) {
                    stopTestForVpnLoss()
                } else {
                    _uiState.update {
                        it.copy(
                            vpnStatus = if (transport.isVpnActive) {
                                VpnConnectionStatus.CONNECTED
                            } else if (it.vpnStatus == VpnConnectionStatus.LOST) {
                                VpnConnectionStatus.LOST
                            } else {
                                VpnConnectionStatus.DISCONNECTED
                            },
                            vpnInterfaceName = transport.interfaceName
                        )
                    }
                }
            }
        }
    }

    fun refreshInstalledProviders() {
        val installed = VpnProvider.entries.filter { vpnMonitor.isProviderInstalled(it) }.toSet()
        _uiState.update { it.copy(installedProviders = installed) }
    }

    fun selectVpnProvider(provider: VpnProvider) {
        _uiState.update {
            it.copy(selectedVpnProvider = provider, vpnActionMessage = null)
        }
    }

    fun setRequireVpnForTest(required: Boolean) {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(requireVpnForTest = required, vpnActionMessage = null) }
    }

    fun openVpnConnectOrDisconnect() {
        val provider = _uiState.value.selectedVpnProvider
        vpnMonitor.openOfficialVpnControl(provider)
            .onSuccess { msg ->
                _uiState.update { it.copy(vpnActionMessage = msg) }
            }
            .onFailure { err ->
                _uiState.update {
                    it.copy(vpnActionMessage = err.localizedMessage ?: "Unable to open VPN settings.")
                }
            }
    }

    fun verifyVpnConnection() {
        if (_uiState.value.isVerifyingVpn) return
        _uiState.update {
            it.copy(
                isVerifyingVpn = true,
                vpnStatus = VpnConnectionStatus.VERIFYING,
                vpnActionMessage = null
            )
        }
        viewModelScope.launch {
            val info = vpnMonitor.verifyConnection()
            val active = vpnMonitor.transportState.value.isVpnActive
            _uiState.update {
                it.copy(
                    isVerifyingVpn = false,
                    vpnStatus = if (active) VpnConnectionStatus.CONNECTED else VpnConnectionStatus.DISCONNECTED,
                    vpnInterfaceName = vpnMonitor.transportState.value.interfaceName,
                    vpnVerification = info,
                    vpnActionMessage = info.errorMessage
                )
            }
        }
    }

    fun setHtmlSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(isHtmlSheetVisible = visible) }
    }

    fun onUrlChanged(newValue: String) {
        val currentError = if (newValue.isBlank()) null else {
            when (val res = UrlValidator.validate(newValue)) {
                is UrlValidationResult.Valid -> null
                is UrlValidationResult.Invalid -> res.errorMessage
            }
        }
        _uiState.update { it.copy(urlInput = newValue, urlError = currentError) }
    }

    fun applyQuickProtocol(protocol: String) {
        if (_uiState.value.isRunning) return
        val current = _uiState.value.urlInput.trim()
        val stripped = current.removePrefix("https://").removePrefix("http://")
        onUrlChanged("$protocol$stripped")
    }

    fun updateViewDuration(seconds: Int) {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(viewDurationSec = seconds.coerceIn(3, 300)) }
    }

    fun updatePauseDuration(seconds: Int) {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(pauseDurationSec = seconds.coerceIn(0, 120)) }
    }

    fun updateTotalRuns(runs: Int) {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(totalRuns = runs.coerceIn(1, 50)) }
    }

    fun startAutoScrollDown() {
        _uiState.update {
            it.copy(
                scrollDirection = AutoScrollDirection.DOWN,
                scrollStatus = if (it.activeUrl != null) ScrollIndicatorState.SCROLLING_DOWN else ScrollIndicatorState.IDLE
            )
        }
    }

    fun startAutoScrollUp() {
        _uiState.update {
            it.copy(
                scrollDirection = AutoScrollDirection.UP,
                scrollStatus = if (it.activeUrl != null) ScrollIndicatorState.SCROLLING_UP else ScrollIndicatorState.IDLE
            )
        }
    }

    fun startRandomScroll() {
        _uiState.update {
            it.copy(
                scrollDirection = AutoScrollDirection.RANDOM,
                scrollStatus = if (it.activeUrl != null) ScrollIndicatorState.RANDOM_DOWN else ScrollIndicatorState.IDLE
            )
        }
    }

    fun stopAutoScroll() {
        _uiState.update {
            it.copy(
                scrollDirection = AutoScrollDirection.NONE,
                scrollStatus = ScrollIndicatorState.STOPPED
            )
        }
    }

    fun updateScrollStatus(status: ScrollIndicatorState) {
        _uiState.update { it.copy(scrollStatus = status) }
    }

    fun setScrollSpeed(speed: ScrollSpeed) {
        _uiState.update { it.copy(scrollSpeed = speed) }
    }

    fun setAutoNextEnabled(enabled: Boolean) {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(autoNextEnabled = enabled) }
    }

    fun startTest() {
        val state = _uiState.value
        if (state.isRunning) return

        val validation = UrlValidator.validate(state.urlInput)
        if (validation is UrlValidationResult.Invalid) {
            _uiState.update { it.copy(urlError = validation.errorMessage) }
            return
        }

        vpnMonitor.refreshState()
        val vpnActive = vpnMonitor.transportState.value.isVpnActive
        if (state.requireVpnForTest && !vpnActive) {
            _uiState.update {
                it.copy(
                    vpnStatus = VpnConnectionStatus.DISCONNECTED,
                    vpnActionMessage = "VPN required: Connect a VPN provider before starting the test."
                )
            }
            return
        }

        val normalizedUrl = (validation as UrlValidationResult.Valid).normalizedUrl
        val runs = if (state.autoNextEnabled) state.totalRuns else 1
        val viewSec = state.viewDurationSec
        val pauseSec = state.pauseDurationSec

        _uiState.update {
            it.copy(
                urlInput = normalizedUrl,
                urlError = null,
                vpnActionMessage = null,
                isRunning = true,
                status = TestSessionStatus.LOADING,
                activeUrl = normalizedUrl,
                currentRunNumber = 1,
                activeTotalRuns = runs,
                phaseRemainingSec = viewSec,
                overallProgress = 0f,
                isPreviewLoading = true,
                previewBlockedReason = null,
                canGoBack = false,
                scrollStatus = when (it.scrollDirection) {
                    AutoScrollDirection.DOWN -> ScrollIndicatorState.SCROLLING_DOWN
                    AutoScrollDirection.UP -> ScrollIndicatorState.SCROLLING_UP
                    AutoScrollDirection.RANDOM -> ScrollIndicatorState.RANDOM_DOWN
                    AutoScrollDirection.NONE -> ScrollIndicatorState.IDLE
                }
            )
        }

        queueJob?.cancel()
        queueJob = viewModelScope.launch {
            runQueue(normalizedUrl, runs, viewSec, pauseSec)
        }
    }

    private suspend fun runQueue(
        targetUrl: String,
        runs: Int,
        viewDurationSec: Int,
        pauseDurationSec: Int
    ) {
        for (runIdx in 1..runs) {
            if (_uiState.value.requireVpnForTest && !vpnMonitor.transportState.value.isVpnActive) {
                stopTestForVpnLoss()
                return
            }

            _uiState.update {
                it.copy(
                    status = TestSessionStatus.LOADING,
                    activeUrl = targetUrl,
                    previewLoadToken = it.previewLoadToken + 1,
                    currentRunNumber = runIdx,
                    activeTotalRuns = runs,
                    phaseRemainingSec = viewDurationSec,
                    isPreviewLoading = true,
                    previewBlockedReason = null
                )
            }

            var loadGraceMs = 0
            while (_uiState.value.isPreviewLoading &&
                _uiState.value.previewBlockedReason == null &&
                loadGraceMs < 4000
            ) {
                if (_uiState.value.requireVpnForTest && !vpnMonitor.transportState.value.isVpnActive) {
                    stopTestForVpnLoss()
                    return
                }
                delay(150)
                loadGraceMs += 150
            }

            if (_uiState.value.previewBlockedReason != null) {
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        status = TestSessionStatus.BLOCKED,
                        isPreviewLoading = false,
                        scrollDirection = AutoScrollDirection.NONE,
                        scrollStatus = ScrollIndicatorState.RESTRICTED
                    )
                }
                return
            }

            _uiState.update {
                it.copy(
                    status = TestSessionStatus.VIEWING,
                    isPreviewLoading = false
                )
            }

            for (secLeft in viewDurationSec downTo 1) {
                if (_uiState.value.requireVpnForTest && !vpnMonitor.transportState.value.isVpnActive) {
                    stopTestForVpnLoss()
                    return
                }
                _uiState.update {
                    val fractionDoneInRun =
                        (viewDurationSec - secLeft).toFloat() / viewDurationSec.toFloat()
                    val overall = ((runIdx - 1) + fractionDoneInRun) / runs.toFloat()
                    it.copy(
                        phaseRemainingSec = secLeft,
                        overallProgress = overall.coerceIn(0f, 1f)
                    )
                }
                delay(1000)

                if (_uiState.value.previewBlockedReason != null) {
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            status = TestSessionStatus.BLOCKED,
                            scrollDirection = AutoScrollDirection.NONE,
                            scrollStatus = ScrollIndicatorState.RESTRICTED
                        )
                    }
                    return
                }
            }

            _uiState.update {
                it.copy(overallProgress = runIdx.toFloat() / runs.toFloat())
            }

            val hasNextRun = runIdx < runs && _uiState.value.autoNextEnabled
            if (hasNextRun && pauseDurationSec > 0) {
                _uiState.update {
                    it.copy(
                        status = TestSessionStatus.PAUSING_BETWEEN,
                        activeUrl = null,
                        phaseRemainingSec = pauseDurationSec,
                        canGoBack = false
                    )
                }
                for (pauseLeft in pauseDurationSec downTo 1) {
                    if (_uiState.value.requireVpnForTest && !vpnMonitor.transportState.value.isVpnActive) {
                        stopTestForVpnLoss()
                        return
                    }
                    _uiState.update { it.copy(phaseRemainingSec = pauseLeft) }
                    delay(1000)
                }
            }
        }

        _uiState.update {
            it.copy(
                isRunning = false,
                status = TestSessionStatus.COMPLETED,
                activeUrl = null,
                phaseRemainingSec = 0,
                overallProgress = 1f,
                canGoBack = false,
                scrollDirection = AutoScrollDirection.NONE,
                scrollStatus = ScrollIndicatorState.IDLE,
                webCommand = WebCommandEvent(++commandSeq, WebCommandType.CLEAR_SESSION)
            )
        }
    }

    private fun stopTestForVpnLoss() {
        queueJob?.cancel()
        queueJob = null

        _uiState.update {
            it.copy(
                isRunning = false,
                status = TestSessionStatus.STOPPED,
                activeUrl = null,
                currentRunNumber = 0,
                phaseRemainingSec = 0,
                overallProgress = 0f,
                isPreviewLoading = false,
                previewBlockedReason = null,
                canGoBack = false,
                scrollDirection = AutoScrollDirection.NONE,
                scrollStatus = ScrollIndicatorState.STOPPED,
                vpnStatus = VpnConnectionStatus.LOST,
                vpnInterfaceName = null,
                vpnActionMessage = "Test stopped: VPN connectivity was lost.",
                webCommand = WebCommandEvent(++commandSeq, WebCommandType.CLEAR_SESSION)
            )
        }
    }

    fun stopTest() {
        queueJob?.cancel()
        queueJob = null

        _uiState.update {
            it.copy(
                isRunning = false,
                status = TestSessionStatus.STOPPED,
                activeUrl = null,
                currentRunNumber = 0,
                phaseRemainingSec = 0,
                overallProgress = 0f,
                isPreviewLoading = false,
                previewBlockedReason = null,
                canGoBack = false,
                scrollDirection = AutoScrollDirection.NONE,
                scrollStatus = ScrollIndicatorState.STOPPED,
                webCommand = WebCommandEvent(++commandSeq, WebCommandType.CLEAR_SESSION)
            )
        }
    }

    fun triggerBack() {
        _uiState.update {
            it.copy(webCommand = WebCommandEvent(++commandSeq, WebCommandType.BACK))
        }
    }

    fun triggerHome() {
        val state = _uiState.value
        val target = state.activeUrl ?: when (val res = UrlValidator.validate(state.urlInput)) {
            is UrlValidationResult.Valid -> res.normalizedUrl
            is UrlValidationResult.Invalid -> null
        } ?: return

        _uiState.update {
            it.copy(
                activeUrl = target,
                previewBlockedReason = null,
                webCommand = WebCommandEvent(++commandSeq, WebCommandType.HOME, target)
            )
        }
    }

    fun triggerReload() {
        if (_uiState.value.activeUrl == null) return
        _uiState.update {
            it.copy(
                previewBlockedReason = null,
                webCommand = WebCommandEvent(++commandSeq, WebCommandType.RELOAD)
            )
        }
    }

    fun consumeWebCommand(commandId: Long) {
        _uiState.update {
            if (it.webCommand?.id == commandId) it.copy(webCommand = null) else it
        }
    }

    fun updateCanGoBack(canGoBack: Boolean) {
        _uiState.update { it.copy(canGoBack = canGoBack) }
    }

    fun onPreviewPageStarted() {
        _uiState.update { it.copy(isPreviewLoading = true) }
    }

    fun onPreviewPageFinished(canGoBack: Boolean) {
        _uiState.update {
            it.copy(
                isPreviewLoading = false,
                canGoBack = canGoBack,
                status = if (it.isRunning && it.status == TestSessionStatus.LOADING) {
                    TestSessionStatus.VIEWING
                } else {
                    it.status
                }
            )
        }
    }

    fun onPreviewBlockedOrError(reason: String) {
        _uiState.update {
            it.copy(
                isPreviewLoading = false,
                previewBlockedReason = reason,
                scrollDirection = AutoScrollDirection.NONE,
                scrollStatus = ScrollIndicatorState.RESTRICTED
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        queueJob?.cancel()
        vpnMonitor.release()
    }
}

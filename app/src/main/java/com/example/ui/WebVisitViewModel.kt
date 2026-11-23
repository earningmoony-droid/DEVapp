package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AutoScrollDirection
import com.example.model.ClickInteractionMode
import com.example.model.ClickTargetItem
import com.example.model.ClickTestStatus
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
    val isScrollPaused: Boolean = false,
    val scrollPositionText: String = "0% • 0/0px",
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

    // Clicks & Tap Automation (temporary in-memory state only, no history/logs)
    val clickMode: ClickInteractionMode = ClickInteractionMode.CLICK,
    val clickIntervalSec: Int = 2,
    val clickSelector: String = "",
    val discoveredClickTargets: List<ClickTargetItem> = emptyList(),
    val isScanningTargets: Boolean = false,
    val isAutoClickRunning: Boolean = false,
    val clickCount: Int = 0,
    val clickStatus: ClickTestStatus = ClickTestStatus.IDLE,
    val clickStatusMessage: String? = null,
    val isAuthorizedOwnSite: Boolean = false,

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
    private var autoClickJob: Job? = null
    private var commandSeq: Long = 0L
    private var wasVpnActive: Boolean = false
    private var lastSingleTriggerAtMs: Long = 0L

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
            if (it.activeUrl == null) {
                return@update it.copy(
                    scrollDirection = AutoScrollDirection.NONE,
                    isScrollPaused = false,
                    scrollStatus = ScrollIndicatorState.IDLE
                )
            }
            it.copy(
                scrollDirection = AutoScrollDirection.DOWN,
                isScrollPaused = false,
                scrollStatus = ScrollIndicatorState.SCROLLING_DOWN
            )
        }
    }

    fun startAutoScrollUp() {
        _uiState.update {
            if (it.activeUrl == null) {
                return@update it.copy(
                    scrollDirection = AutoScrollDirection.NONE,
                    isScrollPaused = false,
                    scrollStatus = ScrollIndicatorState.IDLE
                )
            }
            it.copy(
                scrollDirection = AutoScrollDirection.UP,
                isScrollPaused = false,
                scrollStatus = ScrollIndicatorState.SCROLLING_UP
            )
        }
    }

    fun startRandomScroll() {
        _uiState.update {
            if (it.activeUrl == null) {
                return@update it.copy(
                    scrollDirection = AutoScrollDirection.NONE,
                    isScrollPaused = false,
                    scrollStatus = ScrollIndicatorState.IDLE
                )
            }
            it.copy(
                scrollDirection = AutoScrollDirection.RANDOM,
                isScrollPaused = false,
                scrollStatus = ScrollIndicatorState.RANDOM_DOWN
            )
        }
    }

    fun togglePauseAutoScroll() {
        _uiState.update { state ->
            if (state.scrollDirection == AutoScrollDirection.NONE) return@update state
            val nextPaused = !state.isScrollPaused
            val nextStatus = if (nextPaused) {
                ScrollIndicatorState.PAUSED
            } else {
                when (state.scrollDirection) {
                    AutoScrollDirection.DOWN -> ScrollIndicatorState.SCROLLING_DOWN
                    AutoScrollDirection.UP -> ScrollIndicatorState.SCROLLING_UP
                    AutoScrollDirection.RANDOM -> ScrollIndicatorState.RANDOM_DOWN
                    AutoScrollDirection.NONE -> ScrollIndicatorState.IDLE
                }
            }
            state.copy(isScrollPaused = nextPaused, scrollStatus = nextStatus)
        }
    }

    fun stopAutoScroll() {
        _uiState.update {
            it.copy(
                scrollDirection = AutoScrollDirection.NONE,
                isScrollPaused = false,
                scrollStatus = ScrollIndicatorState.STOPPED
            )
        }
    }

    fun onScrollFeedback(status: ScrollIndicatorState, positionText: String?) {
        val isTerminal = status == ScrollIndicatorState.AT_TOP ||
            status == ScrollIndicatorState.AT_BOTTOM ||
            status == ScrollIndicatorState.NOT_SCROLLABLE ||
            status == ScrollIndicatorState.STUCK ||
            status == ScrollIndicatorState.RESTRICTED

        _uiState.update { state ->
            val updatedPos = positionText ?: state.scrollPositionText
            if (state.isScrollPaused && !isTerminal) {
                return@update state.copy(scrollPositionText = updatedPos)
            }
            if (state.scrollDirection == AutoScrollDirection.NONE && !isTerminal) {
                return@update state.copy(scrollPositionText = updatedPos)
            }
            state.copy(
                scrollStatus = status,
                scrollPositionText = updatedPos,
                scrollDirection = if (isTerminal) AutoScrollDirection.NONE else state.scrollDirection,
                isScrollPaused = if (isTerminal) false else state.isScrollPaused
            )
        }
    }

    fun setScrollSpeed(speed: ScrollSpeed) {
        _uiState.update { it.copy(scrollSpeed = speed) }
    }

    fun setClickInteractionMode(mode: ClickInteractionMode) {
        if (_uiState.value.isAutoClickRunning) return
        _uiState.update { it.copy(clickMode = mode, clickStatusMessage = null) }
    }

    fun setAutoNextEnabled(enabled: Boolean) {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(autoNextEnabled = enabled) }
    }

    // --- Clicks Automation Controls (Strictly for authorized own sites / test pages, zero ads) ---

    fun setAuthorizedOwnSite(authorized: Boolean) {
        if (!authorized && _uiState.value.isAutoClickRunning) {
            stopAutoClick("Stopped: Authorization unchecked.")
        }
        _uiState.update {
            it.copy(
                isAuthorizedOwnSite = authorized,
                clickStatusMessage = if (authorized) null else it.clickStatusMessage
            )
        }
    }

    fun onClickSelectorChanged(selector: String) {
        _uiState.update {
            it.copy(
                clickSelector = selector,
                clickStatusMessage = null
            )
        }
    }

    fun selectDiscoveredTarget(target: ClickTargetItem) {
        _uiState.update {
            it.copy(
                clickSelector = target.selector,
                clickStatusMessage = "Selected: ${target.label}"
            )
        }
    }

    fun updateClickInterval(seconds: Int) {
        if (_uiState.value.isAutoClickRunning) return
        _uiState.update { it.copy(clickIntervalSec = seconds.coerceIn(1, 60)) }
    }

    fun scanClickTargets() {
        val state = _uiState.value
        if (state.activeUrl == null || state.previewBlockedReason != null || state.status == TestSessionStatus.PAUSING_BETWEEN) {
            _uiState.update {
                it.copy(
                    clickStatus = ClickTestStatus.RESTRICTED,
                    clickStatusMessage = "Load your website preview first before scanning buttons or links."
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isScanningTargets = true,
                clickStatusMessage = null,
                webCommand = WebCommandEvent(++commandSeq, WebCommandType.SCAN_CLICK_TARGETS)
            )
        }
    }

    fun onClickTargetsScanned(targets: List<ClickTargetItem>, errorMsg: String?) {
        _uiState.update { state ->
            val currentStillValid = targets.any { it.selector == state.clickSelector }
            state.copy(
                isScanningTargets = false,
                discoveredClickTargets = targets,
                clickSelector = when {
                    state.clickSelector.isNotBlank() && currentStillValid -> state.clickSelector
                    state.clickSelector.isBlank() && targets.isNotEmpty() -> targets.first().selector
                    else -> state.clickSelector
                },
                clickStatus = when {
                    errorMsg != null -> ClickTestStatus.RESTRICTED
                    targets.isEmpty() -> ClickTestStatus.NO_TARGET
                    else -> if (state.isAutoClickRunning) ClickTestStatus.RUNNING else ClickTestStatus.IDLE
                },
                clickStatusMessage = errorMsg ?: if (targets.isEmpty()) {
                    "No safe buttons or links found on this view."
                } else {
                    "Found ${targets.size} safe button/link target(s)."
                }
            )
        }
    }

    private fun isProhibitedAdSelector(selector: String): Boolean {
        val lower = selector.lowercase()
        val blockedTokens = listOf(
            "adsbygoogle", "doubleclick", "googlesyndication", "adservice",
            "adsystem", "adserver", "adnxs", "taboola", "outbrain", "mgid",
            "sponsored", "advertisement", "ad-slot", "ad_slot", "ad-banner",
            "ad_banner", "google_ads", "data-ad"
        )
        return blockedTokens.any { lower.contains(it) }
    }

    fun triggerSingleClickTest() {
        val now = System.currentTimeMillis()
        if (now - lastSingleTriggerAtMs < 320L) return

        val state = _uiState.value
        if (!state.isAuthorizedOwnSite) {
            _uiState.update {
                it.copy(
                    clickStatus = ClickTestStatus.RESTRICTED,
                    clickStatusMessage = "Confirm this is your own website or authorized test page first."
                )
            }
            return
        }
        if (state.activeUrl == null || state.previewBlockedReason != null || state.status == TestSessionStatus.PAUSING_BETWEEN) {
            _uiState.update {
                it.copy(
                    clickStatus = ClickTestStatus.RESTRICTED,
                    clickStatusMessage = "Start a test session so the website preview is loaded."
                )
            }
            return
        }
        val selector = state.clickSelector.trim()
        if (selector.isEmpty()) {
            _uiState.update {
                it.copy(
                    clickStatus = ClickTestStatus.NO_TARGET,
                    clickStatusMessage = "Scan targets or enter a button/link selector."
                )
            }
            return
        }
        if (isProhibitedAdSelector(selector)) {
            _uiState.update {
                it.copy(
                    clickStatus = ClickTestStatus.BLOCKED_AD,
                    clickStatusMessage = "Blocked: Ad elements and CTR manipulation are prohibited."
                )
            }
            return
        }

        lastSingleTriggerAtMs = now
        _uiState.update {
            it.copy(
                clickStatusMessage = null,
                webCommand = WebCommandEvent(
                    id = ++commandSeq,
                    type = WebCommandType.EXECUTE_SINGLE_CLICK,
                    selector = selector,
                    interactionMode = state.clickMode
                )
            )
        }
    }

    fun startAutoClick() {
        val state = _uiState.value
        if (state.isAutoClickRunning) return
        if (!state.isAuthorizedOwnSite) {
            _uiState.update {
                it.copy(
                    clickStatus = ClickTestStatus.RESTRICTED,
                    clickStatusMessage = "Confirm this is your own website or authorized test page first."
                )
            }
            return
        }
        if (state.activeUrl == null || state.previewBlockedReason != null || state.status == TestSessionStatus.PAUSING_BETWEEN) {
            _uiState.update {
                it.copy(
                    clickStatus = ClickTestStatus.RESTRICTED,
                    clickStatusMessage = "Start a test session so the website preview is loaded."
                )
            }
            return
        }
        val selector = state.clickSelector.trim()
        if (selector.isEmpty()) {
            _uiState.update {
                it.copy(
                    clickStatus = ClickTestStatus.NO_TARGET,
                    clickStatusMessage = "Select or enter a target button/link before starting Auto Click."
                )
            }
            return
        }
        if (isProhibitedAdSelector(selector)) {
            _uiState.update {
                it.copy(
                    clickStatus = ClickTestStatus.BLOCKED_AD,
                    clickStatusMessage = "Blocked: Ad elements and artificial clicks are strictly prohibited."
                )
            }
            return
        }

        val intervalMs = state.clickIntervalSec.coerceIn(1, 60) * 1000L
        val modeLabel = if (state.clickMode == ClickInteractionMode.TAP) "Auto Tap" else "Auto Click"
        _uiState.update {
            it.copy(
                isAutoClickRunning = true,
                clickStatus = ClickTestStatus.RUNNING,
                clickStatusMessage = "$modeLabel active every ${state.clickIntervalSec}s"
            )
        }

        autoClickJob?.cancel()
        autoClickJob = viewModelScope.launch {
            while (_uiState.value.isAutoClickRunning) {
                val cur = _uiState.value
                val activeLabel = if (cur.clickMode == ClickInteractionMode.TAP) "Auto Tap" else "Auto Click"
                if (!cur.isAuthorizedOwnSite ||
                    cur.activeUrl == null ||
                    cur.previewBlockedReason != null
                ) {
                    stopAutoClick("$activeLabel stopped: Preview not active.")
                    break
                }
                if (cur.status != TestSessionStatus.PAUSING_BETWEEN && !cur.isPreviewLoading) {
                    _uiState.update {
                        it.copy(
                            webCommand = WebCommandEvent(
                                id = ++commandSeq,
                                type = WebCommandType.EXECUTE_SINGLE_CLICK,
                                selector = cur.clickSelector.trim(),
                                interactionMode = cur.clickMode
                            )
                        )
                    }
                }
                delay(intervalMs)
            }
        }
    }

    fun stopAutoClick(reason: String? = null) {
        autoClickJob?.cancel()
        autoClickJob = null
        _uiState.update {
            val defaultMsg = if (it.clickMode == ClickInteractionMode.TAP) "Auto Tap stopped." else "Auto Click stopped."
            it.copy(
                isAutoClickRunning = false,
                clickStatus = ClickTestStatus.STOPPED,
                clickStatusMessage = reason ?: defaultMsg
            )
        }
    }

    fun onSingleClickExecuted(resultCode: String, detail: String?) {
        when (resultCode) {
            "SKIPPED" -> Unit
            "CLICKED" -> {
                _uiState.update {
                    it.copy(
                        clickCount = it.clickCount + 1,
                        clickStatus = if (it.isAutoClickRunning) ClickTestStatus.RUNNING else ClickTestStatus.IDLE,
                        clickStatusMessage = detail ?: "Tested interaction (${it.clickCount + 1})."
                    )
                }
            }
            "BLOCKED_AD" -> {
                autoClickJob?.cancel()
                autoClickJob = null
                _uiState.update {
                    it.copy(
                        isAutoClickRunning = false,
                        clickStatus = ClickTestStatus.BLOCKED_AD,
                        clickStatusMessage = "Blocked: Clicking ads or sponsored elements is strictly prohibited."
                    )
                }
            }
            "NO_TARGET" -> {
                autoClickJob?.cancel()
                autoClickJob = null
                _uiState.update {
                    it.copy(
                        isAutoClickRunning = false,
                        clickStatus = ClickTestStatus.NO_TARGET,
                        clickStatusMessage = "Selected button/link was not found on the current view."
                    )
                }
            }
            else -> {
                autoClickJob?.cancel()
                autoClickJob = null
                _uiState.update {
                    it.copy(
                        isAutoClickRunning = false,
                        clickStatus = ClickTestStatus.RESTRICTED,
                        clickStatusMessage = detail ?: "Click blocked by page security restrictions."
                    )
                }
            }
        }
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
                        isScrollPaused = false,
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
                            isScrollPaused = false,
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

        autoClickJob?.cancel()
        autoClickJob = null

        _uiState.update {
            it.copy(
                isRunning = false,
                status = TestSessionStatus.COMPLETED,
                activeUrl = null,
                phaseRemainingSec = 0,
                overallProgress = 1f,
                canGoBack = false,
                scrollDirection = AutoScrollDirection.NONE,
                isScrollPaused = false,
                scrollPositionText = "0% • 0/0px",
                scrollStatus = ScrollIndicatorState.IDLE,
                isAutoClickRunning = false,
                clickCount = 0,
                discoveredClickTargets = emptyList(),
                clickStatus = ClickTestStatus.IDLE,
                clickStatusMessage = null,
                webCommand = WebCommandEvent(++commandSeq, WebCommandType.CLEAR_SESSION)
            )
        }
    }

    private fun stopTestForVpnLoss() {
        queueJob?.cancel()
        queueJob = null
        autoClickJob?.cancel()
        autoClickJob = null

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
                isScrollPaused = false,
                scrollPositionText = "0% • 0/0px",
                scrollStatus = ScrollIndicatorState.STOPPED,
                isAutoClickRunning = false,
                clickCount = 0,
                discoveredClickTargets = emptyList(),
                clickStatus = ClickTestStatus.IDLE,
                clickStatusMessage = null,
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
        autoClickJob?.cancel()
        autoClickJob = null

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
                isScrollPaused = false,
                scrollPositionText = "0% • 0/0px",
                scrollStatus = ScrollIndicatorState.STOPPED,
                isAutoClickRunning = false,
                clickCount = 0,
                discoveredClickTargets = emptyList(),
                clickStatus = ClickTestStatus.IDLE,
                clickStatusMessage = null,
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
                isScrollPaused = false,
                scrollStatus = ScrollIndicatorState.RESTRICTED
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        queueJob?.cancel()
        autoClickJob?.cancel()
        vpnMonitor.release()
    }
}

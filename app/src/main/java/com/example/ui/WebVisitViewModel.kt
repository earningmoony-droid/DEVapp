package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AutoScrollDirection
import com.example.model.ClickInteractionMode
import com.example.model.ClickTargetItem
import com.example.model.ClickTestStatus
import com.example.model.DEMO_ENV_URL
import com.example.model.RandomInteractionStatus
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    val clickTargetInput: String = "100",
    val clickTarget: Int = 100,
    val discoveredClickTargets: List<ClickTargetItem> = emptyList(),
    val isScanningTargets: Boolean = false,
    val isAutoClickRunning: Boolean = false,
    val isManualClickListening: Boolean = false,
    val clickCount: Int = 0,
    val clickStatus: ClickTestStatus = ClickTestStatus.IDLE,
    val clickStatusMessage: String? = null,
    val isAuthorizedOwnSite: Boolean = false,

    // Random Interaction Testing Mode (Own Test Page & Demo Environment only, zero ads)
    val randomTapTargetInput: String = "20",
    val randomTapTarget: Int = 20,
    val randomTapDelayInput: String = "2",
    val randomTapDelaySec: Int = 2,
    val randomTapCount: Int = 0,
    val isRandomInteractionRunning: Boolean = false,
    val isRandomArmedOnLoad: Boolean = false,
    val isDemoEnvironment: Boolean = false,
    val randomStatus: RandomInteractionStatus = RandomInteractionStatus.IDLE,
    val randomStatusMessage: String? = "Starts after verified page load. Performs safe random UI taps with scroll down/up cycles. Own Test Page & Demo Environment only; ads are blocked.",

    // Temporary active session state only
    val isRunning: Boolean = false,
    val status: TestSessionStatus = TestSessionStatus.IDLE,
    val activeUrl: String? = null,
    val previewLoadToken: Int = 0,
    val visitCount: Int = 0,
    val failedCount: Int = 0,
    val currentRunNumber: Int = 0,
    val activeTotalRuns: Int = 1,
    val phaseRemainingSec: Int = 0,
    val overallProgress: Float = 0f,
    val isPreviewLoading: Boolean = false,
    val previewBlockedReason: String? = null,
    val lastLoadWasNetworkError: Boolean = false,
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
    private var randomJob: Job? = null
    private var commandSeq: Long = 0L
    private var wasVpnActive: Boolean = false
    private var lastSingleTriggerAtMs: Long = 0L
    private var countedVisitToken: Int = -1
    private var countedFailToken: Int = -1
    private var randomLastSelector: String = ""
    private var pendingRandomTapDeferred: CompletableDeferred<Triple<String, String?, String?>>? = null
    private var pendingRandomScrollDeferred: CompletableDeferred<Pair<Boolean, String?>>? = null

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
        if (!authorized) {
            if (_uiState.value.isAutoClickRunning) {
                stopAutoClick("Stopped: Authorization unchecked.")
            }
            if ((_uiState.value.isRandomInteractionRunning || _uiState.value.isRandomArmedOnLoad) &&
                !_uiState.value.isDemoEnvironment
            ) {
                stopRandomInteraction("Stopped: Authorization unchecked.", RandomInteractionStatus.RESTRICTED)
            }
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

    fun updateClickTargetInput(rawInput: String) {
        val digitsOnly = rawInput.filter { it.isDigit() }.take(5)
        val parsed = digitsOnly.toIntOrNull()?.coerceIn(1, 10000)
        _uiState.update { state ->
            val nextTarget = parsed ?: state.clickTarget
            val reachedNow = state.isAutoClickRunning && parsed != null && state.clickCount >= nextTarget
            if (reachedNow) {
                autoClickJob?.cancel()
                autoClickJob = null
            }
            state.copy(
                clickTargetInput = digitsOnly,
                clickTarget = nextTarget,
                isAutoClickRunning = if (reachedNow) false else state.isAutoClickRunning,
                isManualClickListening = if (reachedNow) false else state.isManualClickListening,
                clickStatus = if (reachedNow) ClickTestStatus.COMPLETED else state.clickStatus,
                clickStatusMessage = if (reachedNow) {
                    "Target reached (${state.clickCount}/$nextTarget). Click Test stopped automatically."
                } else {
                    state.clickStatusMessage
                }
            )
        }
    }

    fun selectClickTargetPreset(target: Int) {
        val safeTarget = target.coerceIn(1, 10000)
        _uiState.update { state ->
            val reachedNow = state.isAutoClickRunning && state.clickCount >= safeTarget
            if (reachedNow) {
                autoClickJob?.cancel()
                autoClickJob = null
            }
            state.copy(
                clickTargetInput = safeTarget.toString(),
                clickTarget = safeTarget,
                isAutoClickRunning = if (reachedNow) false else state.isAutoClickRunning,
                isManualClickListening = if (reachedNow) false else state.isManualClickListening,
                clickStatus = if (reachedNow) ClickTestStatus.COMPLETED else state.clickStatus,
                clickStatusMessage = if (reachedNow) {
                    "Target reached (${state.clickCount}/$safeTarget). Click Test stopped automatically."
                } else {
                    "Click Target updated to $safeTarget (${state.clickCount}/$safeTarget)."
                }
            )
        }
    }

    fun resetClickCounter() {
        autoClickJob?.cancel()
        autoClickJob = null
        lastSingleTriggerAtMs = 0L
        _uiState.update { state ->
            val safeTarget = state.clickTargetInput.toIntOrNull()?.coerceIn(1, 10000) ?: state.clickTarget
            state.copy(
                clickTarget = safeTarget,
                clickTargetInput = safeTarget.toString(),
                isAutoClickRunning = false,
                isManualClickListening = false,
                clickCount = 0,
                clickStatus = ClickTestStatus.IDLE,
                clickStatusMessage = "Click counter reset (0/$safeTarget)."
            )
        }
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

        val parsedTarget = state.clickTargetInput.trim().toIntOrNull()
        if (parsedTarget == null || parsedTarget < 1) {
            _uiState.update {
                it.copy(
                    clickStatus = ClickTestStatus.NO_TARGET,
                    clickStatusMessage = "Enter a valid Click Target (1 to 10000)."
                )
            }
            return
        }
        val safeTarget = parsedTarget.coerceIn(1, 10000)

        if (!state.isAuthorizedOwnSite) {
            _uiState.update {
                it.copy(
                    clickTarget = safeTarget,
                    clickTargetInput = safeTarget.toString(),
                    clickStatus = ClickTestStatus.RESTRICTED,
                    clickStatusMessage = "Confirm this is your own website or authorized test page first."
                )
            }
            return
        }
        if (state.activeUrl == null || state.previewBlockedReason != null || state.status == TestSessionStatus.PAUSING_BETWEEN) {
            _uiState.update {
                it.copy(
                    clickTarget = safeTarget,
                    clickTargetInput = safeTarget.toString(),
                    clickStatus = ClickTestStatus.RESTRICTED,
                    clickStatusMessage = "Start a test session so the website preview is loaded."
                )
            }
            return
        }
        if (state.clickCount >= safeTarget) {
            _uiState.update {
                it.copy(
                    clickTarget = safeTarget,
                    clickTargetInput = safeTarget.toString(),
                    clickStatus = ClickTestStatus.COMPLETED,
                    clickStatusMessage = "Target ${state.clickCount}/$safeTarget already reached. Reset Counter or raise Click Target."
                )
            }
            return
        }

        val selector = state.clickSelector.trim()
        if (selector.isNotEmpty() && isProhibitedAdSelector(selector)) {
            _uiState.update {
                it.copy(
                    clickStatus = ClickTestStatus.BLOCKED_AD,
                    clickStatusMessage = "Blocked: Ad elements and artificial clicks are strictly prohibited."
                )
            }
            return
        }

        if (selector.isEmpty()) {
            autoClickJob?.cancel()
            autoClickJob = null
            _uiState.update {
                it.copy(
                    clickTarget = safeTarget,
                    clickTargetInput = safeTarget.toString(),
                    isAutoClickRunning = true,
                    isManualClickListening = true,
                    clickStatus = ClickTestStatus.RUNNING,
                    clickStatusMessage = "Click Test active (${state.clickCount}/$safeTarget): Tap elements in preview or enter a selector for auto clicking."
                )
            }
            return
        }

        val intervalMs = state.clickIntervalSec.coerceIn(1, 60) * 1000L
        val modeLabel = if (state.clickMode == ClickInteractionMode.TAP) "Tap Test" else "Click Test"
        _uiState.update {
            it.copy(
                clickTarget = safeTarget,
                clickTargetInput = safeTarget.toString(),
                isAutoClickRunning = true,
                isManualClickListening = false,
                clickStatus = ClickTestStatus.RUNNING,
                clickStatusMessage = "$modeLabel active (${state.clickCount}/$safeTarget) every ${state.clickIntervalSec}s"
            )
        }

        autoClickJob?.cancel()
        autoClickJob = viewModelScope.launch {
            while (_uiState.value.isAutoClickRunning && !_uiState.value.isManualClickListening) {
                val cur = _uiState.value
                val activeLabel = if (cur.clickMode == ClickInteractionMode.TAP) "Tap Test" else "Click Test"
                if (cur.clickCount >= cur.clickTarget) {
                    _uiState.update {
                        it.copy(
                            isAutoClickRunning = false,
                            isManualClickListening = false,
                            clickStatus = ClickTestStatus.COMPLETED,
                            clickStatusMessage = "Target reached (${cur.clickCount}/${cur.clickTarget}). $activeLabel stopped automatically."
                        )
                    }
                    break
                }
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
            val defaultMsg = if (it.clickMode == ClickInteractionMode.TAP) {
                "Tap Test stopped (${it.clickCount}/${it.clickTarget})."
            } else {
                "Click Test stopped (${it.clickCount}/${it.clickTarget})."
            }
            it.copy(
                isAutoClickRunning = false,
                isManualClickListening = false,
                clickStatus = ClickTestStatus.STOPPED,
                clickStatusMessage = reason ?: defaultMsg
            )
        }
    }

    fun onDirectPreviewClick(targetLabel: String) {
        val now = System.currentTimeMillis()
        if (now - lastSingleTriggerAtMs < 280L) return

        val state = _uiState.value
        if (!state.isAuthorizedOwnSite && !state.isDemoEnvironment) {
            _uiState.update {
                it.copy(
                    clickStatus = ClickTestStatus.RESTRICTED,
                    clickStatusMessage = "Enable 'Authorized Own Site / Test Page' to count preview clicks."
                )
            }
            return
        }
        if (state.activeUrl == null || state.previewBlockedReason != null) return

        if (state.isAutoClickRunning && state.clickCount >= state.clickTarget) {
            autoClickJob?.cancel()
            autoClickJob = null
            _uiState.update {
                it.copy(
                    isAutoClickRunning = false,
                    isManualClickListening = false,
                    clickStatus = ClickTestStatus.COMPLETED,
                    clickStatusMessage = "Target reached (${state.clickCount}/${state.clickTarget}). Click Test stopped automatically."
                )
            }
            return
        }

        lastSingleTriggerAtMs = now
        val safeLabel = targetLabel.ifBlank { "element" }
        _uiState.update {
            val nextCount = it.clickCount + 1
            val target = it.clickTarget
            val reachedTarget = it.isAutoClickRunning && nextCount >= target
            if (reachedTarget) {
                autoClickJob?.cancel()
                autoClickJob = null
            }
            val actionWord = if (it.clickMode == ClickInteractionMode.TAP) "tap" else "click"
            it.copy(
                clickCount = nextCount,
                isAutoClickRunning = if (reachedTarget) false else it.isAutoClickRunning,
                isManualClickListening = if (reachedTarget) false else it.isManualClickListening,
                clickStatus = when {
                    reachedTarget -> ClickTestStatus.COMPLETED
                    it.isAutoClickRunning -> ClickTestStatus.RUNNING
                    else -> ClickTestStatus.IDLE
                },
                clickStatusMessage = if (reachedTarget) {
                    "Target reached on \"$safeLabel\" ($nextCount/$target). Click Test stopped automatically."
                } else {
                    "Counted direct $actionWord on \"$safeLabel\" ($nextCount/$target)."
                }
            )
        }
    }

    fun onSingleClickExecuted(resultCode: String, detail: String?) {
        when (resultCode) {
            "SKIPPED" -> Unit
            "CLICKED" -> {
                _uiState.update { state ->
                    if (state.isAutoClickRunning && state.clickCount >= state.clickTarget) {
                        autoClickJob?.cancel()
                        autoClickJob = null
                        return@update state.copy(
                            isAutoClickRunning = false,
                            isManualClickListening = false,
                            clickStatus = ClickTestStatus.COMPLETED,
                            clickStatusMessage = "Target reached (${state.clickCount}/${state.clickTarget}). Click Test stopped automatically."
                        )
                    }
                    val nextCount = state.clickCount + 1
                    val target = state.clickTarget
                    val reachedTarget = state.isAutoClickRunning && nextCount >= target
                    if (reachedTarget) {
                        autoClickJob?.cancel()
                        autoClickJob = null
                    }
                    state.copy(
                        clickCount = nextCount,
                        isAutoClickRunning = if (reachedTarget) false else state.isAutoClickRunning,
                        isManualClickListening = if (reachedTarget) false else state.isManualClickListening,
                        clickStatus = when {
                            reachedTarget -> ClickTestStatus.COMPLETED
                            state.isAutoClickRunning -> ClickTestStatus.RUNNING
                            else -> ClickTestStatus.IDLE
                        },
                        clickStatusMessage = if (reachedTarget) {
                            "Target reached ($nextCount/$target). Click Test stopped automatically."
                        } else {
                            "${detail ?: "Tested interaction"} ($nextCount/$target)."
                        }
                    )
                }
            }
            "BLOCKED_AD" -> {
                autoClickJob?.cancel()
                autoClickJob = null
                _uiState.update {
                    it.copy(
                        isAutoClickRunning = false,
                        isManualClickListening = false,
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
                        isManualClickListening = false,
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
                        isManualClickListening = false,
                        clickStatus = ClickTestStatus.RESTRICTED,
                        clickStatusMessage = detail ?: "Click blocked by page security restrictions."
                    )
                }
            }
        }
    }

    // --- Random Interaction Testing Mode (Own Test Page & Demo Environment Only, Zero Ads) ---

    fun loadDemoTestEnvironment() {
        val cur = _uiState.value
        if (cur.isRunning || cur.isRandomInteractionRunning) return

        queueJob?.cancel()
        queueJob = null
        autoClickJob?.cancel()
        autoClickJob = null
        cancelRandomJobsAndDeferreds()
        countedVisitToken = -1
        countedFailToken = -1
        randomLastSelector = ""

        _uiState.update {
            it.copy(
                isRunning = false,
                status = TestSessionStatus.VIEWING,
                activeUrl = DEMO_ENV_URL,
                previewLoadToken = it.previewLoadToken + 1,
                isPreviewLoading = false,
                previewBlockedReason = null,
                lastLoadWasNetworkError = false,
                canGoBack = false,
                scrollDirection = AutoScrollDirection.NONE,
                isScrollPaused = false,
                scrollPositionText = "0% • 0/0px",
                scrollStatus = ScrollIndicatorState.IDLE,
                isAutoClickRunning = false,
                isManualClickListening = false,
                discoveredClickTargets = emptyList(),
                isDemoEnvironment = true,
                isRandomInteractionRunning = false,
                isRandomArmedOnLoad = false,
                randomStatus = RandomInteractionStatus.IDLE,
                randomStatusMessage = "Demo Test Environment loaded. Tap 'Start Random Test' to run Random Tap & Scroll Down/Up testing."
            )
        }
    }

    fun updateRandomTapTargetInput(rawInput: String) {
        val digitsOnly = rawInput.filter { it.isDigit() }.take(4)
        val parsed = digitsOnly.toIntOrNull()?.coerceIn(1, 5000)
        _uiState.update { state ->
            val nextTarget = parsed ?: state.randomTapTarget
            val reachedNow = state.isRandomInteractionRunning && parsed != null && state.randomTapCount >= nextTarget
            if (reachedNow) {
                cancelRandomJobsAndDeferreds()
            }
            state.copy(
                randomTapTargetInput = digitsOnly,
                randomTapTarget = nextTarget,
                isRandomInteractionRunning = if (reachedNow) false else state.isRandomInteractionRunning,
                isRandomArmedOnLoad = if (reachedNow) false else state.isRandomArmedOnLoad,
                randomStatus = if (reachedNow) RandomInteractionStatus.COMPLETED else state.randomStatus,
                randomStatusMessage = if (reachedNow) {
                    "Random Interaction completed (${state.randomTapCount}/$nextTarget taps)."
                } else {
                    state.randomStatusMessage
                }
            )
        }
    }

    fun updateRandomTapDelayInput(rawInput: String) {
        if (_uiState.value.isRandomInteractionRunning || _uiState.value.isRandomArmedOnLoad) return
        val digitsOnly = rawInput.filter { it.isDigit() }.take(2)
        val parsed = digitsOnly.toIntOrNull()?.coerceIn(1, 30)
        _uiState.update {
            it.copy(
                randomTapDelayInput = digitsOnly,
                randomTapDelaySec = parsed ?: it.randomTapDelaySec
            )
        }
    }

    fun startRandomInteraction() {
        val state = _uiState.value
        if (state.isRandomInteractionRunning || state.isRandomArmedOnLoad) return

        val safeTarget = state.randomTapTargetInput.trim().toIntOrNull()?.coerceIn(1, 5000) ?: 20
        val safeDelay = state.randomTapDelayInput.trim().toIntOrNull()?.coerceIn(1, 30) ?: 2

        if (state.randomTapCount >= safeTarget) {
            _uiState.update {
                it.copy(
                    randomTapTarget = safeTarget,
                    randomTapTargetInput = safeTarget.toString(),
                    randomTapDelaySec = safeDelay,
                    randomTapDelayInput = safeDelay.toString(),
                    randomStatus = RandomInteractionStatus.COMPLETED,
                    randomStatusMessage = "Tap Target (${state.randomTapCount}/$safeTarget) already reached. Press Reset or raise Tap Target."
                )
            }
            return
        }

        if (!state.isDemoEnvironment && !state.isAuthorizedOwnSite) {
            _uiState.update {
                it.copy(
                    randomTapTarget = safeTarget,
                    randomTapTargetInput = safeTarget.toString(),
                    randomTapDelaySec = safeDelay,
                    randomTapDelayInput = safeDelay.toString(),
                    randomStatus = RandomInteractionStatus.RESTRICTED,
                    randomStatusMessage = "Random Interaction only works on your own Test Page ('Authorized Own Site') or Demo Environment ('Load Demo Test Environment')."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                randomTapTarget = safeTarget,
                randomTapTargetInput = safeTarget.toString(),
                randomTapDelaySec = safeDelay,
                randomTapDelayInput = safeDelay.toString()
            )
        }

        val previewReady = (state.isDemoEnvironment && state.activeUrl == DEMO_ENV_URL) ||
            (state.activeUrl != null && !state.isPreviewLoading && state.previewBlockedReason == null && state.status == TestSessionStatus.VIEWING)

        if (previewReady) {
            launchRandomInteractionLoop()
            return
        }

        if (state.isRunning && state.isPreviewLoading && state.previewBlockedReason == null) {
            _uiState.update {
                it.copy(
                    isRandomArmedOnLoad = true,
                    randomStatus = RandomInteractionStatus.WAITING_LOAD,
                    randomStatusMessage = "Waiting for page load to finish before starting Random Interaction..."
                )
            }
            return
        }

        val check = UrlValidator.validate(state.urlInput)
        if (check is UrlValidationResult.Invalid) {
            _uiState.update {
                it.copy(
                    randomStatus = RandomInteractionStatus.FAILED,
                    randomStatusMessage = "Load Demo Test Environment or enter a valid Test Page URL first."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isRandomArmedOnLoad = true,
                randomStatus = RandomInteractionStatus.WAITING_LOAD,
                randomStatusMessage = "Loading Test Page; Random Interaction will begin once page load completes..."
            )
        }
        startTestInternal(preserveRandomArmed = true)
    }

    fun stopRandomInteraction(
        reason: String? = null,
        finalStatus: RandomInteractionStatus = RandomInteractionStatus.STOPPED
    ) {
        cancelRandomJobsAndDeferreds()
        _uiState.update {
            it.copy(
                isRandomInteractionRunning = false,
                isRandomArmedOnLoad = false,
                randomStatus = finalStatus,
                randomStatusMessage = reason ?: "Random Interaction stopped (${it.randomTapCount}/${it.randomTapTarget})."
            )
        }
    }

    fun resetRandomInteractionCounter() {
        cancelRandomJobsAndDeferreds()
        randomLastSelector = ""
        _uiState.update {
            val safeTarget = it.randomTapTargetInput.toIntOrNull()?.coerceIn(1, 5000) ?: it.randomTapTarget
            it.copy(
                randomTapTarget = safeTarget,
                randomTapTargetInput = safeTarget.toString(),
                randomTapCount = 0,
                isRandomInteractionRunning = false,
                isRandomArmedOnLoad = false,
                randomStatus = RandomInteractionStatus.IDLE,
                randomStatusMessage = "Counter reset (0/$safeTarget). Ready to start."
            )
        }
    }

    fun onRandomTapExecuted(status: String, label: String?, selector: String?) {
        pendingRandomTapDeferred?.complete(Triple(status, label, selector))
    }

    fun onRandomScrollFinished(moved: Boolean, posText: String?) {
        pendingRandomScrollDeferred?.complete(Pair(moved, posText))
    }

    private fun cancelRandomJobsAndDeferreds() {
        randomJob?.cancel()
        randomJob = null
        pendingRandomTapDeferred?.cancel()
        pendingRandomTapDeferred = null
        pendingRandomScrollDeferred?.cancel()
        pendingRandomScrollDeferred = null
    }

    private fun launchRandomInteractionLoop() {
        cancelRandomJobsAndDeferreds()
        randomLastSelector = ""
        _uiState.update {
            it.copy(
                isRandomInteractionRunning = true,
                isRandomArmedOnLoad = false,
                scrollDirection = AutoScrollDirection.NONE,
                isScrollPaused = false,
                randomStatus = RandomInteractionStatus.TAPPING,
                randomStatusMessage = "Starting Random Interaction (${it.randomTapCount}/${it.randomTapTarget})..."
            )
        }

        randomJob = viewModelScope.launch {
            var nextScrollDir = "down"
            var tapsSinceScroll = 0

            while (_uiState.value.isRandomInteractionRunning) {
                val cur = _uiState.value
                if (cur.previewBlockedReason != null ||
                    cur.activeUrl == null ||
                    cur.status == TestSessionStatus.FAILED ||
                    cur.status == TestSessionStatus.ERROR ||
                    cur.status == TestSessionStatus.BLOCKED
                ) {
                    stopRandomInteraction("Stopped: Page load failed.", RandomInteractionStatus.FAILED)
                    break
                }
                if (!cur.isDemoEnvironment && !cur.isAuthorizedOwnSite) {
                    stopRandomInteraction(
                        "Stopped: Authorized Test Page or Demo Environment required.",
                        RandomInteractionStatus.RESTRICTED
                    )
                    break
                }
                if (cur.randomTapCount >= cur.randomTapTarget) {
                    stopRandomInteraction(
                        "Random Interaction completed (${cur.randomTapCount}/${cur.randomTapTarget} taps).",
                        RandomInteractionStatus.COMPLETED
                    )
                    break
                }

                if (tapsSinceScroll >= 2) {
                    val scrollStatus = if (nextScrollDir == "down") {
                        RandomInteractionStatus.SCROLLING_DOWN
                    } else {
                        RandomInteractionStatus.SCROLLING_UP
                    }
                    _uiState.update {
                        it.copy(
                            randomStatus = scrollStatus,
                            scrollStatus = if (nextScrollDir == "down") ScrollIndicatorState.RANDOM_DOWN else ScrollIndicatorState.RANDOM_UP,
                            randomStatusMessage = "Scrolling $nextScrollDir before re-scanning UI elements (${cur.randomTapCount}/${cur.randomTapTarget})..."
                        )
                    }
                    val scrollDeferred = CompletableDeferred<Pair<Boolean, String?>>()
                    pendingRandomScrollDeferred = scrollDeferred
                    _uiState.update {
                        it.copy(
                            webCommand = WebCommandEvent(
                                id = ++commandSeq,
                                type = WebCommandType.RANDOM_SCROLL_STEP,
                                selector = nextScrollDir
                            )
                        )
                    }
                    val scrollRes = withTimeoutOrNull(3500L) { scrollDeferred.await() }
                    pendingRandomScrollDeferred = null
                    if (!_uiState.value.isRandomInteractionRunning) break
                    if (scrollRes?.second != null) {
                        _uiState.update { it.copy(scrollPositionText = scrollRes.second!!) }
                    }
                    nextScrollDir = if (nextScrollDir == "down") "up" else "down"
                    tapsSinceScroll = 0
                    delay(350L)
                    continue
                }

                val tapDeferred = CompletableDeferred<Triple<String, String?, String?>>()
                pendingRandomTapDeferred = tapDeferred
                _uiState.update {
                    it.copy(
                        webCommand = WebCommandEvent(
                            id = ++commandSeq,
                            type = WebCommandType.EXECUTE_RANDOM_TAP,
                            selector = randomLastSelector
                        )
                    )
                }
                val tapRes = withTimeoutOrNull(3000L) { tapDeferred.await() }
                pendingRandomTapDeferred = null
                if (!_uiState.value.isRandomInteractionRunning) break

                if (tapRes == null) {
                    stopRandomInteraction("Stopped: Page preview unavailable.", RandomInteractionStatus.FAILED)
                    break
                }

                val (resCode, resLabel, resSelector) = tapRes
                when (resCode) {
                    "TAPPED" -> {
                        if (!resSelector.isNullOrBlank()) {
                            randomLastSelector = resSelector
                        }
                        tapsSinceScroll += 1
                        val elLabel = resLabel?.takeIf { it.isNotBlank() } ?: "UI element"
                        var reachedGoal = false
                        var delaySecNow = 2
                        _uiState.update { s ->
                            val nextCount = s.randomTapCount + 1
                            val target = s.randomTapTarget.coerceAtLeast(1)
                            delaySecNow = s.randomTapDelaySec.coerceIn(1, 30)
                            if (nextCount >= target) {
                                reachedGoal = true
                                s.copy(
                                    randomTapCount = nextCount,
                                    isRandomInteractionRunning = false,
                                    isRandomArmedOnLoad = false,
                                    randomStatus = RandomInteractionStatus.COMPLETED,
                                    randomStatusMessage = "Target reached ($nextCount/$target) after tapping \"$elLabel\"."
                                )
                            } else {
                                s.copy(
                                    randomTapCount = nextCount,
                                    randomStatus = RandomInteractionStatus.TAPPING,
                                    randomStatusMessage = "Tapped \"$elLabel\" ($nextCount/$target) • Next in ${delaySecNow}s"
                                )
                            }
                        }
                        if (reachedGoal) break
                        delay(delaySecNow * 1000L)
                    }
                    "BLOCKED_AD" -> {
                        stopRandomInteraction(
                            "Blocked: Ad elements and ad clicks are strictly prohibited.",
                            RandomInteractionStatus.RESTRICTED
                        )
                        break
                    }
                    "NO_TARGET" -> {
                        stopRandomInteraction(
                            "No safe non-ad UI elements found to tap on this test page.",
                            RandomInteractionStatus.RESTRICTED
                        )
                        break
                    }
                    else -> {
                        stopRandomInteraction(
                            resLabel ?: "Random Interaction blocked on this page.",
                            RandomInteractionStatus.RESTRICTED
                        )
                        break
                    }
                }
            }
        }
    }

    fun startTest() {
        startTestInternal(preserveRandomArmed = false)
    }

    private fun startTestInternal(preserveRandomArmed: Boolean) {
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
                    vpnActionMessage = "VPN required: Connect a VPN provider before starting the test.",
                    isRandomArmedOnLoad = false
                )
            }
            return
        }

        val normalizedUrl = (validation as UrlValidationResult.Valid).normalizedUrl
        val runs = if (state.autoNextEnabled) state.totalRuns else 1
        val viewSec = state.viewDurationSec
        val pauseSec = state.pauseDurationSec

        cancelRandomJobsAndDeferreds()
        countedVisitToken = -1
        countedFailToken = -1
        randomLastSelector = ""

        _uiState.update {
            it.copy(
                urlInput = normalizedUrl,
                urlError = null,
                vpnActionMessage = null,
                isDemoEnvironment = false,
                isRandomInteractionRunning = false,
                isRandomArmedOnLoad = preserveRandomArmed,
                isRunning = true,
                status = TestSessionStatus.LOADING,
                activeUrl = normalizedUrl,
                visitCount = 0,
                failedCount = 0,
                clickCount = 0,
                currentRunNumber = 1,
                activeTotalRuns = runs,
                phaseRemainingSec = viewSec,
                overallProgress = 0f,
                isPreviewLoading = true,
                previewBlockedReason = null,
                lastLoadWasNetworkError = false,
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
                    previewBlockedReason = null,
                    lastLoadWasNetworkError = false
                )
            }

            var loadGraceMs = 0
            while (_uiState.value.isPreviewLoading &&
                _uiState.value.previewBlockedReason == null &&
                loadGraceMs < 8000
            ) {
                if (_uiState.value.requireVpnForTest && !vpnMonitor.transportState.value.isVpnActive) {
                    stopTestForVpnLoss()
                    return
                }
                delay(150)
                loadGraceMs += 150
            }

            val currentAfterWait = _uiState.value
            val timedOut = currentAfterWait.isPreviewLoading && currentAfterWait.previewBlockedReason == null
            if (timedOut) {
                onPreviewBlockedOrError("Page load timed out before completing.", isNetworkError = false)
            }

            val stateAfterLoad = _uiState.value
            if (stateAfterLoad.previewBlockedReason != null) {
                val hardError = stateAfterLoad.lastLoadWasNetworkError
                val canAdvance = !hardError && runIdx < runs && stateAfterLoad.autoNextEnabled
                if (canAdvance) {
                    _uiState.update {
                        it.copy(
                            status = TestSessionStatus.FAILED,
                            isPreviewLoading = false,
                            scrollDirection = AutoScrollDirection.NONE,
                            isScrollPaused = false,
                            scrollStatus = ScrollIndicatorState.RESTRICTED
                        )
                    }
                    delay(1200)
                    if (pauseDurationSec > 0) {
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
                    continue
                } else {
                    autoClickJob?.cancel()
                    autoClickJob = null
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            status = if (hardError) TestSessionStatus.ERROR else TestSessionStatus.FAILED,
                            isPreviewLoading = false,
                            isAutoClickRunning = false,
                            isManualClickListening = false,
                            scrollDirection = AutoScrollDirection.NONE,
                            isScrollPaused = false,
                            scrollStatus = ScrollIndicatorState.RESTRICTED
                        )
                    }
                    return
                }
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
                    val hardError = _uiState.value.lastLoadWasNetworkError
                    autoClickJob?.cancel()
                    autoClickJob = null
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            status = if (hardError) TestSessionStatus.ERROR else TestSessionStatus.FAILED,
                            isAutoClickRunning = false,
                            isManualClickListening = false,
                            scrollDirection = AutoScrollDirection.NONE,
                            isScrollPaused = false,
                            scrollStatus = ScrollIndicatorState.RESTRICTED
                        )
                    }
                    return
                }
            }

            while (_uiState.value.isRandomInteractionRunning || _uiState.value.isRandomArmedOnLoad) {
                if (_uiState.value.requireVpnForTest && !vpnMonitor.transportState.value.isVpnActive) {
                    stopTestForVpnLoss()
                    return
                }
                if (_uiState.value.previewBlockedReason != null) {
                    val hardError = _uiState.value.lastLoadWasNetworkError
                    autoClickJob?.cancel()
                    autoClickJob = null
                    cancelRandomJobsAndDeferreds()
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            status = if (hardError) TestSessionStatus.ERROR else TestSessionStatus.FAILED,
                            isAutoClickRunning = false,
                            isManualClickListening = false,
                            isRandomInteractionRunning = false,
                            isRandomArmedOnLoad = false,
                            randomStatus = RandomInteractionStatus.FAILED,
                            randomStatusMessage = "Stopped: Page load failure occurred.",
                            scrollDirection = AutoScrollDirection.NONE,
                            isScrollPaused = false,
                            scrollStatus = ScrollIndicatorState.RESTRICTED
                        )
                    }
                    return
                }
                delay(300)
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
        cancelRandomJobsAndDeferreds()

        _uiState.update {
            val allFailed = it.visitCount == 0 && it.failedCount > 0
            it.copy(
                isRunning = false,
                status = if (allFailed) TestSessionStatus.FAILED else TestSessionStatus.COMPLETED,
                activeUrl = null,
                phaseRemainingSec = 0,
                overallProgress = if (allFailed) it.overallProgress else 1f,
                canGoBack = false,
                scrollDirection = AutoScrollDirection.NONE,
                isScrollPaused = false,
                scrollPositionText = "0% • 0/0px",
                scrollStatus = ScrollIndicatorState.IDLE,
                isAutoClickRunning = false,
                isManualClickListening = false,
                isRandomInteractionRunning = false,
                isRandomArmedOnLoad = false,
                isDemoEnvironment = false,
                discoveredClickTargets = emptyList(),
                clickStatus = if (it.clickCount >= it.clickTarget && it.clickCount > 0) ClickTestStatus.COMPLETED else ClickTestStatus.IDLE,
                webCommand = WebCommandEvent(++commandSeq, WebCommandType.CLEAR_SESSION)
            )
        }
    }

    private fun stopTestForVpnLoss() {
        queueJob?.cancel()
        queueJob = null
        autoClickJob?.cancel()
        autoClickJob = null
        cancelRandomJobsAndDeferreds()
        countedVisitToken = -1
        countedFailToken = -1

        _uiState.update {
            it.copy(
                isRunning = false,
                status = TestSessionStatus.STOPPED,
                activeUrl = null,
                visitCount = 0,
                failedCount = 0,
                currentRunNumber = 0,
                phaseRemainingSec = 0,
                overallProgress = 0f,
                isPreviewLoading = false,
                previewBlockedReason = null,
                lastLoadWasNetworkError = false,
                canGoBack = false,
                scrollDirection = AutoScrollDirection.NONE,
                isScrollPaused = false,
                scrollPositionText = "0% • 0/0px",
                scrollStatus = ScrollIndicatorState.STOPPED,
                isAutoClickRunning = false,
                isManualClickListening = false,
                isRandomInteractionRunning = false,
                isRandomArmedOnLoad = false,
                isDemoEnvironment = false,
                randomStatus = RandomInteractionStatus.STOPPED,
                randomStatusMessage = "Stopped: VPN connectivity was lost.",
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
        cancelRandomJobsAndDeferreds()
        countedVisitToken = -1
        countedFailToken = -1

        _uiState.update {
            it.copy(
                isRunning = false,
                status = TestSessionStatus.STOPPED,
                activeUrl = null,
                visitCount = 0,
                failedCount = 0,
                currentRunNumber = 0,
                phaseRemainingSec = 0,
                overallProgress = 0f,
                isPreviewLoading = false,
                previewBlockedReason = null,
                lastLoadWasNetworkError = false,
                canGoBack = false,
                scrollDirection = AutoScrollDirection.NONE,
                isScrollPaused = false,
                scrollPositionText = "0% • 0/0px",
                scrollStatus = ScrollIndicatorState.STOPPED,
                isAutoClickRunning = false,
                isManualClickListening = false,
                isRandomInteractionRunning = false,
                isRandomArmedOnLoad = false,
                isDemoEnvironment = false,
                randomTapCount = 0,
                randomStatus = RandomInteractionStatus.STOPPED,
                randomStatusMessage = "Session stopped & cleared.",
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
        val target = if (state.isDemoEnvironment) {
            DEMO_ENV_URL
        } else {
            state.activeUrl ?: when (val res = UrlValidator.validate(state.urlInput)) {
                is UrlValidationResult.Valid -> res.normalizedUrl
                is UrlValidationResult.Invalid -> null
            } ?: return
        }

        _uiState.update {
            it.copy(
                activeUrl = target,
                previewBlockedReason = null,
                lastLoadWasNetworkError = false,
                webCommand = WebCommandEvent(++commandSeq, WebCommandType.HOME, target)
            )
        }
    }

    fun triggerReload() {
        val state = _uiState.value
        if (state.activeUrl == null) return
        _uiState.update {
            it.copy(
                previewBlockedReason = null,
                lastLoadWasNetworkError = false,
                webCommand = WebCommandEvent(++commandSeq, WebCommandType.RELOAD, it.activeUrl)
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
        var shouldLaunchRandom = false
        _uiState.update { state ->
            val token = state.previewLoadToken
            val shouldCountVisit = state.isRunning &&
                state.status == TestSessionStatus.LOADING &&
                state.previewBlockedReason == null &&
                state.activeUrl != null &&
                countedVisitToken != token &&
                countedFailToken != token

            if (shouldCountVisit) {
                countedVisitToken = token
            }

            if (state.isRandomArmedOnLoad && !state.isRandomInteractionRunning && state.previewBlockedReason == null) {
                shouldLaunchRandom = true
            }

            state.copy(
                isPreviewLoading = false,
                canGoBack = canGoBack,
                visitCount = if (shouldCountVisit) state.visitCount + 1 else state.visitCount,
                status = if (state.isRunning && state.status == TestSessionStatus.LOADING && state.previewBlockedReason == null) {
                    TestSessionStatus.VIEWING
                } else {
                    state.status
                }
            )
        }
        if (shouldLaunchRandom) {
            launchRandomInteractionLoop()
        }
    }

    fun onPreviewBlockedOrError(reason: String, isNetworkError: Boolean = false) {
        val hadRandom = _uiState.value.isRandomInteractionRunning || _uiState.value.isRandomArmedOnLoad
        if (hadRandom) {
            cancelRandomJobsAndDeferreds()
        }
        _uiState.update { state ->
            val token = state.previewLoadToken
            val shouldCountFail = state.isRunning &&
                countedFailToken != token &&
                countedVisitToken != token

            if (shouldCountFail) {
                countedFailToken = token
            }

            state.copy(
                isPreviewLoading = false,
                previewBlockedReason = reason,
                lastLoadWasNetworkError = isNetworkError,
                failedCount = if (shouldCountFail) state.failedCount + 1 else state.failedCount,
                scrollDirection = AutoScrollDirection.NONE,
                isScrollPaused = false,
                scrollStatus = ScrollIndicatorState.RESTRICTED,
                isRandomInteractionRunning = if (hadRandom) false else state.isRandomInteractionRunning,
                isRandomArmedOnLoad = if (hadRandom) false else state.isRandomArmedOnLoad,
                randomStatus = if (hadRandom) RandomInteractionStatus.FAILED else state.randomStatus,
                randomStatusMessage = if (hadRandom) "Stopped: Page load failed ($reason)" else state.randomStatusMessage
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        queueJob?.cancel()
        autoClickJob?.cancel()
        cancelRandomJobsAndDeferreds()
        vpnMonitor.release()
    }
}

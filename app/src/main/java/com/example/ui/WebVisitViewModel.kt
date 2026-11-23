package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ErrorDetectionSeverity
import com.example.model.TestSessionStatus
import com.example.model.UrlValidationResult
import com.example.model.UrlValidator
import com.example.model.WebCommandEvent
import com.example.model.WebCommandType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class WebVisitUiState(
    val urlInput: String = "",
    val urlError: String? = null,

    // In-memory test session only (zero history, zero logs, zero fake counters)
    val status: TestSessionStatus = TestSessionStatus.IDLE,
    val activeUrl: String? = null,
    val previewLoadToken: Int = 0,
    val isPreviewLoading: Boolean = false,
    val previewBlockedReason: String? = null,
    val overallProgress: Float = 0f,

    // Separate Impression Status & Click Tracking Status
    val verifiedLoadCount: Int = 0,
    val verifiedUiClicks: Int = 0,
    val impressionStatusText: String = "Not Tested",
    val clickTrackingStatusText: String = "Idle",
    val pageLoadStatusText: String = "Idle",
    val clickListenerStatusText: String = "Not Active",
    val errorDetectionText: String = "No Click Tracking Error Detected",
    val errorSeverity: ErrorDetectionSeverity = ErrorDetectionSeverity.NONE,
    val statusMessage: String = "Enter a Website URL and tap Start Test to check Page Load, Impression Status, and Click Tracking.",
    val isCheckingListener: Boolean = false,

    val webCommand: WebCommandEvent? = null,
    val isHtmlSheetVisible: Boolean = false
)

class WebVisitViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WebVisitUiState())
    val uiState: StateFlow<WebVisitUiState> = _uiState.asStateFlow()

    private var loadWatchdogJob: Job? = null
    private var listenerCheckJob: Job? = null
    private var commandSeq: Long = 0L
    private var countedLoadToken: Int = -1
    private var countedFailToken: Int = -1
    private var lastTrustedClickAtMs: Long = 0L
    private var pendingListenerDeferred: CompletableDeferred<String>? = null

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
        if (_uiState.value.status == TestSessionStatus.LOADING) return
        val stripped = _uiState.value.urlInput.trim().removePrefix("https://").removePrefix("http://")
        onUrlChanged("$protocol$stripped")
    }

    fun startTest() {
        val state = _uiState.value
        if (state.status == TestSessionStatus.LOADING) return

        val validation = UrlValidator.validate(state.urlInput)
        if (validation is UrlValidationResult.Invalid) {
            _uiState.update { it.copy(urlError = validation.errorMessage) }
            return
        }

        val normalizedUrl = (validation as UrlValidationResult.Valid).normalizedUrl
        cancelJobsAndDeferreds()
        countedLoadToken = -1
        countedFailToken = -1
        lastTrustedClickAtMs = 0L

        val nextToken = state.previewLoadToken + 1
        _uiState.update {
            it.copy(
                urlInput = normalizedUrl,
                urlError = null,
                status = TestSessionStatus.LOADING,
                activeUrl = normalizedUrl,
                previewLoadToken = nextToken,
                isPreviewLoading = true,
                previewBlockedReason = null,
                overallProgress = 0.35f,
                verifiedLoadCount = 0,
                verifiedUiClicks = 0,
                impressionStatusText = "Checking Load...",
                clickTrackingStatusText = "Waiting for Load",
                pageLoadStatusText = "Loading...",
                clickListenerStatusText = "Pending",
                errorDetectionText = "No Click Tracking Error Detected",
                errorSeverity = ErrorDetectionSeverity.NONE,
                statusMessage = "Loading Website URL to verify Impression and Click Tracking...",
                isCheckingListener = false
            )
        }

        loadWatchdogJob = viewModelScope.launch {
            delay(8500L)
            val cur = _uiState.value
            if (cur.previewLoadToken == nextToken &&
                cur.status == TestSessionStatus.LOADING &&
                cur.isPreviewLoading &&
                cur.previewBlockedReason == null
            ) {
                onPreviewBlockedOrError(
                    reason = "Network Error: Connection timed out before website finished loading.",
                    isNetworkError = true
                )
            }
        }
    }

    fun stopTest() {
        cancelJobsAndDeferreds()
        countedLoadToken = -1
        countedFailToken = -1
        lastTrustedClickAtMs = 0L

        _uiState.update {
            it.copy(
                status = TestSessionStatus.STOPPED,
                activeUrl = null,
                isPreviewLoading = false,
                previewBlockedReason = null,
                overallProgress = 0f,
                verifiedLoadCount = 0,
                verifiedUiClicks = 0,
                impressionStatusText = "Stopped",
                clickTrackingStatusText = "Stopped",
                pageLoadStatusText = "Stopped",
                clickListenerStatusText = "Stopped",
                errorDetectionText = "No Click Tracking Error Detected",
                errorSeverity = ErrorDetectionSeverity.NONE,
                statusMessage = "Test stopped and temporary state cleared (no history or logs saved).",
                isCheckingListener = false,
                webCommand = WebCommandEvent(++commandSeq, WebCommandType.CLEAR_SESSION)
            )
        }
    }

    fun triggerReload() {
        val state = _uiState.value
        val currentUrl = state.activeUrl ?: return
        if (state.status == TestSessionStatus.LOADING) return

        cancelJobsAndDeferreds()
        countedLoadToken = -1
        countedFailToken = -1
        lastTrustedClickAtMs = 0L

        val nextToken = state.previewLoadToken + 1
        _uiState.update {
            it.copy(
                status = TestSessionStatus.LOADING,
                previewLoadToken = nextToken,
                previewBlockedReason = null,
                isPreviewLoading = true,
                overallProgress = 0.35f,
                impressionStatusText = "Checking Load...",
                clickTrackingStatusText = "Waiting for Load",
                pageLoadStatusText = "Loading...",
                clickListenerStatusText = "Pending",
                errorDetectionText = "No Click Tracking Error Detected",
                errorSeverity = ErrorDetectionSeverity.NONE,
                statusMessage = "Reloading $currentUrl...",
                webCommand = WebCommandEvent(++commandSeq, WebCommandType.RELOAD, currentUrl)
            )
        }

        loadWatchdogJob = viewModelScope.launch {
            delay(8500L)
            val cur = _uiState.value
            if (cur.previewLoadToken == nextToken &&
                cur.status == TestSessionStatus.LOADING &&
                cur.isPreviewLoading &&
                cur.previewBlockedReason == null
            ) {
                onPreviewBlockedOrError(
                    reason = "Network Error: Website reload timed out.",
                    isNetworkError = true
                )
            }
        }
    }

    fun consumeWebCommand(commandId: Long) {
        _uiState.update {
            if (it.webCommand?.id == commandId) it.copy(webCommand = null) else it
        }
    }

    fun onPreviewPageStarted() {
        _uiState.update {
            it.copy(
                isPreviewLoading = true,
                pageLoadStatusText = "Loading...",
                clickTrackingStatusText = "Waiting for Load"
            )
        }
    }

    fun onPreviewPageFinished(listenerBound: Boolean = true) {
        loadWatchdogJob?.cancel()
        loadWatchdogJob = null
        _uiState.update { state ->
            if (state.previewBlockedReason != null || state.activeUrl == null) {
                return@update state.copy(isPreviewLoading = false)
            }
            val token = state.previewLoadToken
            val isFirstLoadForToken = countedLoadToken != token && countedFailToken != token
            if (isFirstLoadForToken) {
                countedLoadToken = token
            }

            state.copy(
                isPreviewLoading = false,
                overallProgress = 1f,
                status = if (state.status == TestSessionStatus.LOADING) TestSessionStatus.LOADED else state.status,
                verifiedLoadCount = if (isFirstLoadForToken) state.verifiedLoadCount + 1 else state.verifiedLoadCount,
                impressionStatusText = "Impression Verified",
                pageLoadStatusText = "Loaded (WebView DOM)",
                clickListenerStatusText = if (listenerBound) "Active (0 Duplicates)" else "Error: Listener Failed",
                clickTrackingStatusText = if (listenerBound) "Listener Active" else "Click Tracking Error",
                errorDetectionText = if (listenerBound) {
                    "No Click Tracking Error Detected"
                } else {
                    "Click Tracking Error: Unable to bind DOM click listener."
                },
                errorSeverity = if (listenerBound) ErrorDetectionSeverity.NONE else ErrorDetectionSeverity.ERROR,
                statusMessage = if (listenerBound) {
                    "Page loaded and Impression verified. Tap inside your website preview to verify real UI click tracking."
                } else {
                    "Page loaded, but DOM click listener could not attach."
                }
            )
        }
    }

    fun onPreviewBlockedOrError(reason: String, isNetworkError: Boolean = false) {
        cancelJobsAndDeferreds()
        _uiState.update { state ->
            val token = state.previewLoadToken
            if (countedFailToken != token && countedLoadToken != token) {
                countedFailToken = token
            }
            val nextStatus = if (isNetworkError) TestSessionStatus.NETWORK_ERROR else TestSessionStatus.FAILED
            state.copy(
                status = nextStatus,
                isPreviewLoading = false,
                overallProgress = 0f,
                previewBlockedReason = reason,
                impressionStatusText = if (isNetworkError) "Not Verified (Network Error)" else "Not Verified (Load Failed)",
                pageLoadStatusText = if (isNetworkError) "Network Error" else "Load Failed",
                clickTrackingStatusText = "Unavailable",
                clickListenerStatusText = "Unavailable",
                errorDetectionText = reason,
                errorSeverity = ErrorDetectionSeverity.ERROR,
                statusMessage = reason
            )
        }
    }

    fun onDirectPreviewClick(targetLabel: String) {
        val now = System.currentTimeMillis()
        if (now - lastTrustedClickAtMs < 250L) {
            onInvalidTrafficOrNotice("Invalid Traffic Filtered: Rapid duplicate click burst (<250ms) ignored.")
            return
        }
        val state = _uiState.value
        if (state.activeUrl == null || state.previewBlockedReason != null) return

        lastTrustedClickAtMs = now
        val safeLabel = targetLabel.ifBlank { "element" }
        _uiState.update {
            it.copy(
                verifiedUiClicks = it.verifiedUiClicks + 1,
                clickTrackingStatusText = "Click Event Received",
                clickListenerStatusText = "Active (0 Duplicates)",
                errorDetectionText = "No Click Tracking Error Detected",
                errorSeverity = ErrorDetectionSeverity.NONE,
                statusMessage = "Real user click received on \"$safeLabel\". Impression and Click Tracking verified separately."
            )
        }
    }

    fun onAdElementObserved() {
        _uiState.update {
            it.copy(
                clickTrackingStatusText = "Ad Network Protected",
                errorDetectionText = "Ad Network Protection Active: Ad click was not altered or intercepted.",
                errorSeverity = ErrorDetectionSeverity.INFO,
                statusMessage = "Ad interaction handled naturally by the site/ad network. Only non-ad UI interactions are counted here."
            )
        }
    }

    fun onInvalidTrafficOrNotice(reason: String) {
        val msg = reason.ifBlank { "Invalid Traffic Filtered: Synthetic or duplicate click ignored." }
        _uiState.update {
            it.copy(
                clickTrackingStatusText = "Invalid Traffic Filtered",
                errorDetectionText = msg,
                errorSeverity = ErrorDetectionSeverity.WARNING,
                statusMessage = msg
            )
        }
    }

    fun checkClickTrackingHealth() {
        val s = _uiState.value
        if (s.isCheckingListener) return
        if (s.activeUrl == null || s.status == TestSessionStatus.IDLE || s.status == TestSessionStatus.STOPPED) {
            _uiState.update {
                it.copy(
                    errorDetectionText = "Enter a Website URL and tap Start Test before checking Click Tracking.",
                    errorSeverity = ErrorDetectionSeverity.ERROR,
                    statusMessage = "No active website loaded."
                )
            }
            return
        }
        if (s.previewBlockedReason != null ||
            s.status == TestSessionStatus.FAILED ||
            s.status == TestSessionStatus.NETWORK_ERROR
        ) {
            _uiState.update {
                it.copy(
                    errorDetectionText = s.previewBlockedReason ?: "Fix Page Load or Network Error first.",
                    errorSeverity = ErrorDetectionSeverity.ERROR,
                    statusMessage = s.previewBlockedReason ?: "Website failed to load."
                )
            }
            return
        }

        listenerCheckJob?.cancel()
        pendingListenerDeferred?.cancel()
        val deferred = CompletableDeferred<String>()
        pendingListenerDeferred = deferred

        _uiState.update {
            it.copy(
                isCheckingListener = true,
                statusMessage = "Verifying DOM Click Event Listener and checking for blocking overlays...",
                webCommand = WebCommandEvent(id = ++commandSeq, type = WebCommandType.CHECK_CLICK_LISTENER)
            )
        }

        listenerCheckJob = viewModelScope.launch {
            val raw = withTimeoutOrNull(3500L) { deferred.await() }
            pendingListenerDeferred = null
            val obj = raw?.let { runCatching { JSONObject(it) }.getOrNull() }
            val ok = obj?.optBoolean("ok", false) == true
            val blocked = obj?.optString("status") == "BLOCKED"
            val listenerText = obj?.optString("listenerText")?.takeIf { it.isNotBlank() }
                ?: if (ok) "Active (0 Duplicates)" else "Error: Not Responding"
            val reason = obj?.optString("reason")?.takeIf { it.isNotBlank() }
                ?: "Click Tracking Error: DOM click event pipeline did not respond."

            _uiState.update { cur ->
                cur.copy(
                    isCheckingListener = false,
                    status = if (blocked) TestSessionStatus.BLOCKED else cur.status,
                    clickListenerStatusText = listenerText,
                    clickTrackingStatusText = when {
                        blocked -> "Blocked (Cross-Origin)"
                        ok -> if (cur.verifiedUiClicks > 0) "Click Event Received" else "Listener Active"
                        else -> "Click Tracking Error"
                    },
                    errorDetectionText = if (ok) "No Click Tracking Error Detected" else reason,
                    errorSeverity = when {
                        blocked -> ErrorDetectionSeverity.WARNING
                        ok -> ErrorDetectionSeverity.NONE
                        else -> ErrorDetectionSeverity.ERROR
                    },
                    statusMessage = if (ok) {
                        "Click Event Listener verified active (0 duplicates, CSS pointer-events clear)."
                    } else {
                        reason
                    }
                )
            }
        }
    }

    fun onListenerHealthResult(jsonResult: String) {
        pendingListenerDeferred?.complete(jsonResult)
    }

    private fun cancelJobsAndDeferreds() {
        loadWatchdogJob?.cancel()
        loadWatchdogJob = null
        listenerCheckJob?.cancel()
        listenerCheckJob = null
        pendingListenerDeferred?.cancel()
        pendingListenerDeferred = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelJobsAndDeferreds()
    }
}

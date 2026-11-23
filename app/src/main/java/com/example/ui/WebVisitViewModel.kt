package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TesterPreferencesRepository
import com.example.model.HistoryStatusType
import com.example.model.TestHistoryEntry
import com.example.model.TestSessionStatus
import com.example.model.TesterSettings
import com.example.model.UrlValidationResult
import com.example.model.UrlValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppDestination {
    DASHBOARD,
    HISTORY
}

data class WebVisitUiState(
    val selectedDestination: AppDestination = AppDestination.DASHBOARD,
    val urlInput: String = "https://example.com",
    val urlError: String? = null,
    val viewDurationSec: Int = 15,
    val pauseDurationSec: Int = 3,
    val totalRuns: Int = 3,

    // Active test queue state
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val status: TestSessionStatus = TestSessionStatus.IDLE,
    val activeUrl: String? = null,
    val previewLoadToken: Int = 0,
    val currentRunNumber: Int = 0,
    val completedRunsInBatch: Int = 0,
    val remainingRunsInBatch: Int = 0,
    val phaseRemainingSec: Int = 0,
    val phaseTotalSec: Int = 0,
    val overallProgress: Float = 0f,
    val isPreviewLoading: Boolean = false,
    val previewBlockedReason: String? = null,

    // Persisted stats & history
    val lifetimeCompletedSessions: Int = 0,
    val history: List<TestHistoryEntry> = emptyList(),
    val isHtmlSheetVisible: Boolean = false
)

class WebVisitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TesterPreferencesRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(WebVisitUiState())
    val uiState: StateFlow<WebVisitUiState> = _uiState.asStateFlow()

    private var queueJob: Job? = null
    private var prePauseStatus: TestSessionStatus = TestSessionStatus.VIEWING

    init {
        viewModelScope.launch {
            val savedSettings = repository.settingsFlow.first()
            _uiState.update {
                it.copy(
                    urlInput = savedSettings.lastUrl,
                    viewDurationSec = savedSettings.viewDurationSec,
                    pauseDurationSec = savedSettings.pauseDurationSec,
                    totalRuns = savedSettings.totalRuns
                )
            }
        }

        viewModelScope.launch {
            repository.historyFlow.collect { historyList ->
                _uiState.update { it.copy(history = historyList) }
            }
        }

        viewModelScope.launch {
            repository.lifetimeCompletedRunsFlow.collect { total ->
                _uiState.update { it.copy(lifetimeCompletedSessions = total) }
            }
        }
    }

    fun selectDestination(destination: AppDestination) {
        _uiState.update { it.copy(selectedDestination = destination) }
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
        val current = _uiState.value.urlInput.trim()
        val stripped = current.removePrefix("https://").removePrefix("http://")
        onUrlChanged("$protocol$stripped")
    }

    fun updateViewDuration(seconds: Int) {
        if (_uiState.value.isRunning) return
        val clamped = seconds.coerceIn(3, 300)
        _uiState.update { it.copy(viewDurationSec = clamped) }
        persistCurrentSettings()
    }

    fun updatePauseDuration(seconds: Int) {
        if (_uiState.value.isRunning) return
        val clamped = seconds.coerceIn(0, 120)
        _uiState.update { it.copy(pauseDurationSec = clamped) }
        persistCurrentSettings()
    }

    fun updateTotalRuns(runs: Int) {
        if (_uiState.value.isRunning) return
        val clamped = runs.coerceIn(1, 50)
        _uiState.update { it.copy(totalRuns = clamped) }
        persistCurrentSettings()
    }

    private fun persistCurrentSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveSettings(
                TesterSettings(
                    lastUrl = state.urlInput.trim(),
                    viewDurationSec = state.viewDurationSec,
                    pauseDurationSec = state.pauseDurationSec,
                    totalRuns = state.totalRuns
                )
            )
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

        val normalizedUrl = (validation as UrlValidationResult.Valid).normalizedUrl
        val runs = state.totalRuns
        val viewSec = state.viewDurationSec
        val pauseSec = state.pauseDurationSec

        _uiState.update {
            it.copy(
                urlInput = normalizedUrl,
                urlError = null,
                isRunning = true,
                isPaused = false,
                status = TestSessionStatus.LOADING,
                activeUrl = normalizedUrl,
                currentRunNumber = 1,
                completedRunsInBatch = 0,
                remainingRunsInBatch = runs,
                phaseRemainingSec = viewSec,
                phaseTotalSec = viewSec,
                overallProgress = 0f,
                isPreviewLoading = true,
                previewBlockedReason = null
            )
        }
        persistCurrentSettings()

        queueJob?.cancel()
        queueJob = viewModelScope.launch {
            runTestQueue(normalizedUrl, runs, viewSec, pauseSec)
        }
    }

    private suspend fun runTestQueue(
        targetUrl: String,
        totalRuns: Int,
        viewDurationSec: Int,
        pauseDurationSec: Int
    ) {
        var completedInBatch = 0

        for (runIdx in 1..totalRuns) {
            _uiState.update {
                it.copy(
                    status = TestSessionStatus.LOADING,
                    activeUrl = targetUrl,
                    previewLoadToken = it.previewLoadToken + 1,
                    currentRunNumber = runIdx,
                    completedRunsInBatch = completedInBatch,
                    remainingRunsInBatch = totalRuns - completedInBatch,
                    phaseRemainingSec = viewDurationSec,
                    phaseTotalSec = viewDurationSec,
                    isPreviewLoading = true,
                    previewBlockedReason = null
                )
            }

            // Wait up to 4 seconds for initial load callback or proceed into viewing timer
            var loadGraceMs = 0
            while (_uiState.value.isPreviewLoading &&
                _uiState.value.previewBlockedReason == null &&
                loadGraceMs < 4000
            ) {
                while (_uiState.value.isPaused) {
                    delay(150)
                }
                delay(150)
                loadGraceMs += 150
            }

            if (_uiState.value.previewBlockedReason != null) {
                // Keep showing the blocked state so user can tap "Open Website" or "Stop"
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        status = TestSessionStatus.BLOCKED,
                        isPreviewLoading = false
                    )
                }
                recordHistory(
                    url = targetUrl,
                    statusType = HistoryStatusType.BLOCKED,
                    completedRuns = completedInBatch,
                    totalRuns = totalRuns
                )
                return
            }

            _uiState.update {
                it.copy(
                    status = TestSessionStatus.VIEWING,
                    isPreviewLoading = false
                )
            }

            // Viewing phase countdown
            for (secLeft in viewDurationSec downTo 1) {
                _uiState.update {
                    val fractionDoneInRun =
                        (viewDurationSec - secLeft).toFloat() / viewDurationSec.toFloat()
                    val overall = ((runIdx - 1) + fractionDoneInRun) / totalRuns.toFloat()
                    it.copy(
                        phaseRemainingSec = secLeft,
                        phaseTotalSec = viewDurationSec,
                        overallProgress = overall.coerceIn(0f, 1f)
                    )
                }
                waitOneSecondRespectingPause()
                if (_uiState.value.previewBlockedReason != null) {
                    _uiState.update {
                        it.copy(
                            isRunning = false,
                            isPaused = false,
                            status = TestSessionStatus.BLOCKED
                        )
                    }
                    recordHistory(
                        url = targetUrl,
                        statusType = HistoryStatusType.BLOCKED,
                        completedRuns = completedInBatch,
                        totalRuns = totalRuns
                    )
                    return
                }
            }

            completedInBatch = runIdx
            val remainingAfterRun = totalRuns - completedInBatch
            _uiState.update {
                it.copy(
                    completedRunsInBatch = completedInBatch,
                    remainingRunsInBatch = remainingAfterRun,
                    overallProgress = completedInBatch.toFloat() / totalRuns.toFloat()
                )
            }

            // Pause between runs if more runs remain and pauseDurationSec > 0
            if (remainingAfterRun > 0 && pauseDurationSec > 0) {
                _uiState.update {
                    it.copy(
                        status = TestSessionStatus.PAUSING_BETWEEN,
                        activeUrl = null,
                        phaseRemainingSec = pauseDurationSec,
                        phaseTotalSec = pauseDurationSec
                    )
                }
                for (pauseLeft in pauseDurationSec downTo 1) {
                    _uiState.update {
                        it.copy(
                            phaseRemainingSec = pauseLeft,
                            phaseTotalSec = pauseDurationSec
                        )
                    }
                    waitOneSecondRespectingPause()
                }
            }
        }

        // Batch completed cleanly
        recordHistory(
            url = targetUrl,
            statusType = HistoryStatusType.COMPLETED,
            completedRuns = completedInBatch,
            totalRuns = totalRuns
        )

        _uiState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                status = TestSessionStatus.COMPLETED,
                phaseRemainingSec = 0,
                overallProgress = 1f,
                remainingRunsInBatch = 0
            )
        }
    }

    private suspend fun waitOneSecondRespectingPause() {
        var elapsedMs = 0
        while (elapsedMs < 1000) {
            while (_uiState.value.isPaused) {
                delay(120)
            }
            delay(100)
            elapsedMs += 100
        }
    }

    fun togglePauseResume() {
        val state = _uiState.value
        if (!state.isRunning) return

        if (state.isPaused) {
            _uiState.update {
                it.copy(
                    isPaused = false,
                    status = prePauseStatus
                )
            }
        } else {
            prePauseStatus = state.status
            _uiState.update {
                it.copy(
                    isPaused = true,
                    status = TestSessionStatus.PAUSED
                )
            }
        }
    }

    fun stopTest() {
        val state = _uiState.value
        val wasRunning = state.isRunning
        queueJob?.cancel()
        queueJob = null

        if (wasRunning && state.activeUrl != null) {
            val urlToLog = state.activeUrl
            val completed = state.completedRunsInBatch
            val total = state.totalRuns
            viewModelScope.launch {
                recordHistory(
                    url = urlToLog,
                    statusType = HistoryStatusType.STOPPED,
                    completedRuns = completed,
                    totalRuns = total
                )
            }
        }

        _uiState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                status = if (wasRunning) TestSessionStatus.STOPPED else TestSessionStatus.IDLE,
                activeUrl = null,
                phaseRemainingSec = 0,
                phaseTotalSec = 0,
                isPreviewLoading = false,
                previewBlockedReason = null
            )
        }
    }

    fun onPreviewPageStarted() {
        _uiState.update { it.copy(isPreviewLoading = true) }
    }

    fun onPreviewPageFinished() {
        _uiState.update {
            it.copy(
                isPreviewLoading = false,
                status = if (it.isRunning && !it.isPaused && it.status == TestSessionStatus.LOADING) {
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
                previewBlockedReason = reason
            )
        }
    }

    fun recordExternalOpen(url: String) {
        val state = _uiState.value
        viewModelScope.launch {
            recordHistory(
                url = url,
                statusType = HistoryStatusType.OPENED_EXTERNALLY,
                completedRuns = 1,
                totalRuns = state.totalRuns
            )
        }
    }

    fun retestFromHistory(url: String) {
        if (_uiState.value.isRunning) return
        _uiState.update {
            it.copy(
                urlInput = url,
                urlError = null,
                selectedDestination = AppDestination.DASHBOARD
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    private suspend fun recordHistory(
        url: String,
        statusType: HistoryStatusType,
        completedRuns: Int,
        totalRuns: Int
    ) {
        val entry = TestHistoryEntry(
            id = UUID.randomUUID().toString(),
            timestampMillis = System.currentTimeMillis(),
            url = url,
            statusType = statusType,
            completedRuns = completedRuns,
            totalRuns = totalRuns
        )
        repository.addHistoryEntry(entry, incrementCompletedRuns = completedRuns)
    }

    override fun onCleared() {
        super.onCleared()
        queueJob?.cancel()
    }
}

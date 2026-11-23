package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.HistoryStatusType
import com.example.model.TestHistoryEntry
import com.example.model.TestSessionStatus
import com.example.ui.AppDestination
import com.example.ui.WebVisitUiState
import com.example.ui.WebVisitViewModel
import com.example.ui.components.DurationStepperRow
import com.example.ui.components.GlassActionButton
import com.example.ui.components.GradientPrimaryButton
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.MetricGlassTile
import com.example.ui.components.WebPreviewCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassStrokeBright
import com.example.ui.theme.GlassStrokeSubtle
import com.example.ui.theme.GlassStrokeViolet
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LuminousPurple
import com.example.ui.theme.MidnightDeep
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WebVisitTesterApp(
    viewModel: WebVisitViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (uiState.selectedDestination != AppDestination.DASHBOARD) {
        BackHandler {
            viewModel.selectDestination(AppDestination.DASHBOARD)
        }
    }

    val openWebsiteInBrowser: (String) -> Unit = { targetUrl ->
        val trimmed = targetUrl.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                viewModel.recordExternalOpen(trimmed)
            }.onFailure {
                Toast.makeText(context, "No browser app found to open this URL.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LiquidGlassBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                TopHeaderBar(
                    status = uiState.status,
                    onShowHtmlClick = { viewModel.setHtmlSheetVisible(true) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val isWideScreen = maxWidth >= 820.dp

                    when (uiState.selectedDestination) {
                        AppDestination.DASHBOARD -> {
                            DashboardContent(
                                uiState = uiState,
                                isWideScreen = isWideScreen,
                                onUrlChanged = viewModel::onUrlChanged,
                                onApplyProtocol = viewModel::applyQuickProtocol,
                                onViewDurationChanged = viewModel::updateViewDuration,
                                onPauseDurationChanged = viewModel::updatePauseDuration,
                                onTotalRunsChanged = viewModel::updateTotalRuns,
                                onStartTest = viewModel::startTest,
                                onTogglePause = viewModel::togglePauseResume,
                                onStopTest = viewModel::stopTest,
                                onPageStarted = viewModel::onPreviewPageStarted,
                                onPageFinished = viewModel::onPreviewPageFinished,
                                onBlockedOrError = viewModel::onPreviewBlockedOrError,
                                onOpenExternal = openWebsiteInBrowser,
                                onViewAllHistory = { viewModel.selectDestination(AppDestination.HISTORY) },
                                onRetestUrl = viewModel::retestFromHistory
                            )
                        }

                        AppDestination.HISTORY -> {
                            HistoryScreen(
                                history = uiState.history,
                                lifetimeCompleted = uiState.lifetimeCompletedSessions,
                                isRunning = uiState.isRunning,
                                onRetest = viewModel::retestFromHistory,
                                onOpenExternal = openWebsiteInBrowser,
                                onClearHistory = viewModel::clearHistory
                            )
                        }
                    }
                }
            }

            // Floating Liquid Glass Bottom Navigation Pill
            FloatingGlassNavBar(
                selectedDestination = uiState.selectedDestination,
                historyCount = uiState.history.size,
                onSelect = viewModel::selectDestination,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }

        if (uiState.isHtmlSheetVisible) {
            SingleFileHtmlDialog(
                onDismiss = { viewModel.setHtmlSheetVisible(false) }
            )
        }
    }
}

@Composable
private fun TopHeaderBar(
    status: TestSessionStatus,
    onShowHtmlClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            TestSessionStatus.VIEWING, TestSessionStatus.COMPLETED -> EmeraldSuccess
            TestSessionStatus.PAUSING_BETWEEN, TestSessionStatus.PAUSED, TestSessionStatus.STOPPED -> AmberWarning
            TestSessionStatus.BLOCKED, TestSessionStatus.ERROR -> CoralDanger
            else -> ElectricCyan
        },
        label = "status_color"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_webvisit_foreground_1795404951294),
                contentDescription = "WebVisit Tester emblem",
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .border(1.dp, GlassStrokeBright, RoundedCornerShape(13.dp))
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Sequential Website Tester",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onShowHtmlClick,
                shape = RoundedCornerShape(999.dp),
                color = GlassSurfaceElevated.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, GlassStrokeSubtle),
                modifier = Modifier.testTag("export_html_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = "Standalone index.html",
                        tint = LuminousPurple,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.btn_export_html),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = statusColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = status.displayLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: WebVisitUiState,
    isWideScreen: Boolean,
    onUrlChanged: (String) -> Unit,
    onApplyProtocol: (String) -> Unit,
    onViewDurationChanged: (Int) -> Unit,
    onPauseDurationChanged: (Int) -> Unit,
    onTotalRunsChanged: (Int) -> Unit,
    onStartTest: () -> Unit,
    onTogglePause: () -> Unit,
    onStopTest: () -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onBlockedOrError: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onViewAllHistory: () -> Unit,
    onRetestUrl: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Dashboard Metrics Strip
        DashboardMetricsStrip(uiState = uiState)

        if (isWideScreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(0.95f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ControlAndQueuePanel(
                        uiState = uiState,
                        onUrlChanged = onUrlChanged,
                        onApplyProtocol = onApplyProtocol,
                        onViewDurationChanged = onViewDurationChanged,
                        onPauseDurationChanged = onPauseDurationChanged,
                        onTotalRunsChanged = onTotalRunsChanged,
                        onStartTest = onStartTest,
                        onTogglePause = onTogglePause,
                        onStopTest = onStopTest
                    )
                }

                Column(
                    modifier = Modifier.weight(1.05f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    WebPreviewCard(
                        activeUrl = uiState.activeUrl,
                        fallbackUrl = uiState.urlInput,
                        loadToken = uiState.previewLoadToken,
                        status = uiState.status,
                        isPreviewLoading = uiState.isPreviewLoading,
                        blockedReason = uiState.previewBlockedReason,
                        phaseRemainingSec = uiState.phaseRemainingSec,
                        onPageStarted = onPageStarted,
                        onPageFinished = onPageFinished,
                        onBlockedOrError = onBlockedOrError,
                        onOpenExternal = onOpenExternal
                    )
                    RecentHistoryPreviewPanel(
                        history = uiState.history,
                        isRunning = uiState.isRunning,
                        onViewAll = onViewAllHistory,
                        onRetest = onRetestUrl
                    )
                }
            }
        } else {
            ControlAndQueuePanel(
                uiState = uiState,
                onUrlChanged = onUrlChanged,
                onApplyProtocol = onApplyProtocol,
                onViewDurationChanged = onViewDurationChanged,
                onPauseDurationChanged = onPauseDurationChanged,
                onTotalRunsChanged = onTotalRunsChanged,
                onStartTest = onStartTest,
                onTogglePause = onTogglePause,
                onStopTest = onStopTest
            )

            WebPreviewCard(
                activeUrl = uiState.activeUrl,
                fallbackUrl = uiState.urlInput,
                loadToken = uiState.previewLoadToken,
                status = uiState.status,
                isPreviewLoading = uiState.isPreviewLoading,
                blockedReason = uiState.previewBlockedReason,
                phaseRemainingSec = uiState.phaseRemainingSec,
                onPageStarted = onPageStarted,
                onPageFinished = onPageFinished,
                onBlockedOrError = onBlockedOrError,
                onOpenExternal = onOpenExternal
            )

            RecentHistoryPreviewPanel(
                history = uiState.history,
                isRunning = uiState.isRunning,
                onViewAll = onViewAllHistory,
                onRetest = onRetestUrl
            )
        }
    }
}

@Composable
private fun DashboardMetricsStrip(uiState: WebVisitUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricGlassTile(
            label = stringResource(R.string.label_completed_sessions),
            value = uiState.lifetimeCompletedSessions.toString(),
            accentColor = EmeraldSuccess,
            icon = Icons.Rounded.TaskAlt,
            modifier = Modifier.weight(1f)
        )
        MetricGlassTile(
            label = stringResource(R.string.label_remaining_tests),
            value = if (uiState.isRunning) uiState.remainingRunsInBatch.toString() else "0",
            accentColor = ElectricCyan,
            icon = Icons.Rounded.PendingActions,
            modifier = Modifier.weight(1f)
        )
        MetricGlassTile(
            label = if (uiState.isRunning) "Timer" else stringResource(R.string.label_current_status),
            value = if (uiState.isRunning) formatTimer(uiState.phaseRemainingSec) else uiState.status.displayLabel,
            accentColor = LuminousPurple,
            icon = Icons.Rounded.Timer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ControlAndQueuePanel(
    uiState: WebVisitUiState,
    onUrlChanged: (String) -> Unit,
    onApplyProtocol: (String) -> Unit,
    onViewDurationChanged: (Int) -> Unit,
    onPauseDurationChanged: (Int) -> Unit,
    onTotalRunsChanged: (Int) -> Unit,
    onStartTest: () -> Unit,
    onTogglePause: () -> Unit,
    onStopTest: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val animatedProgress by animateFloatAsState(
        targetValue = uiState.overallProgress,
        animationSpec = spring(),
        label = "queue_progress"
    )

    LiquidGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.url_label),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.urlInput,
            onValueChange = onUrlChanged,
            enabled = !uiState.isRunning,
            singleLine = true,
            isError = uiState.urlError != null,
            placeholder = {
                Text(
                    text = stringResource(R.string.url_placeholder),
                    color = TextMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = ElectricCyan
                )
            },
            trailingIcon = {
                if (uiState.urlInput.isNotEmpty() && !uiState.isRunning) {
                    IconButton(
                        onClick = { onUrlChanged("") },
                        modifier = Modifier.testTag("clear_url_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Clear URL",
                            tint = TextMuted
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = GlassStrokeViolet,
                errorBorderColor = CoralDanger,
                focusedContainerColor = MidnightDeep.copy(alpha = 0.65f),
                unfocusedContainerColor = MidnightDeep.copy(alpha = 0.50f),
                disabledContainerColor = MidnightDeep.copy(alpha = 0.35f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                disabledTextColor = TextSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("url_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick HTTP / HTTPS prefix buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isHttps = !uiState.urlInput.trim().lowercase().startsWith("http://")
            ProtocolChip(
                label = "https://",
                selected = isHttps,
                enabled = !uiState.isRunning,
                onClick = { onApplyProtocol("https://") },
                modifier = Modifier.weight(1f)
            )
            ProtocolChip(
                label = "http://",
                selected = !isHttps,
                enabled = !uiState.isRunning,
                onClick = { onApplyProtocol("http://") },
                modifier = Modifier.weight(1f)
            )
        }

        AnimatedVisibility(
            visible = uiState.urlError != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = CoralDanger,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = uiState.urlError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoralDanger
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timer & Run Configuration
        DurationStepperRow(
            label = stringResource(R.string.label_view_duration),
            valueText = "${uiState.viewDurationSec}s",
            presets = listOf("10s" to 10, "15s" to 15, "30s" to 30, "60s" to 60),
            currentValue = uiState.viewDurationSec,
            enabled = !uiState.isRunning,
            onDecrement = { onViewDurationChanged(uiState.viewDurationSec - 5) },
            onIncrement = { onViewDurationChanged(uiState.viewDurationSec + 5) },
            onPresetSelected = onViewDurationChanged,
            testTagPrefix = "view_duration"
        )

        Spacer(modifier = Modifier.height(14.dp))

        DurationStepperRow(
            label = stringResource(R.string.label_pause_duration),
            valueText = "${uiState.pauseDurationSec}s",
            presets = listOf("0s" to 0, "3s" to 3, "5s" to 5, "10s" to 10),
            currentValue = uiState.pauseDurationSec,
            enabled = !uiState.isRunning,
            onDecrement = { onPauseDurationChanged(uiState.pauseDurationSec - 1) },
            onIncrement = { onPauseDurationChanged(uiState.pauseDurationSec + 1) },
            onPresetSelected = onPauseDurationChanged,
            testTagPrefix = "pause_duration"
        )

        Spacer(modifier = Modifier.height(14.dp))

        DurationStepperRow(
            label = stringResource(R.string.label_total_runs),
            valueText = "${uiState.totalRuns}x",
            presets = listOf("1x" to 1, "3x" to 3, "5x" to 5, "10x" to 10),
            currentValue = uiState.totalRuns,
            enabled = !uiState.isRunning,
            onDecrement = { onTotalRunsChanged(uiState.totalRuns - 1) },
            onIncrement = { onTotalRunsChanged(uiState.totalRuns + 1) },
            onPresetSelected = onTotalRunsChanged,
            testTagPrefix = "total_runs"
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Action Controls: Start, Pause/Resume, Stop
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GradientPrimaryButton(
                text = stringResource(R.string.btn_start),
                icon = Icons.Rounded.PlayArrow,
                onClick = {
                    focusManager.clearFocus()
                    onStartTest()
                },
                enabled = !uiState.isRunning,
                testTag = "start_test_button",
                modifier = Modifier.weight(1.25f)
            )

            GlassActionButton(
                text = if (uiState.isPaused) stringResource(R.string.btn_resume) else stringResource(R.string.btn_pause),
                icon = if (uiState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                onClick = onTogglePause,
                enabled = uiState.isRunning,
                accentColor = AmberWarning,
                testTag = "pause_resume_button",
                modifier = Modifier.weight(1f)
            )

            GlassActionButton(
                text = stringResource(R.string.btn_stop),
                icon = Icons.Rounded.Stop,
                onClick = onStopTest,
                enabled = uiState.isRunning,
                accentColor = CoralDanger,
                testTag = "stop_test_button",
                modifier = Modifier.weight(0.95f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Queue Status & Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(GlassStrokeSubtle)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val runLabel = when {
                uiState.isRunning -> "Test ${uiState.currentRunNumber} of ${uiState.totalRuns} • ${uiState.remainingRunsInBatch} left"
                uiState.status == TestSessionStatus.COMPLETED -> "Completed ${uiState.totalRuns} of ${uiState.totalRuns}"
                else -> "Queue Ready (${uiState.totalRuns} run${if (uiState.totalRuns == 1) "" else "s"})"
            }
            Text(
                text = runLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MidnightDeep.copy(alpha = 0.78f))
                .border(1.dp, GlassStrokeSubtle, RoundedCornerShape(999.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(ElectricCyan, IndigoAccent, LuminousPurple)
                        )
                    )
            )
        }
    }
}

@Composable
private fun ProtocolChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) ElectricCyan.copy(alpha = 0.18f)
                else MidnightDeep.copy(alpha = 0.45f)
            )
            .border(
                width = 1.dp,
                color = if (selected) ElectricCyan.copy(alpha = 0.55f) else GlassStrokeSubtle,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) ElectricCyan else TextMuted
        )
    }
}

@Composable
private fun RecentHistoryPreviewPanel(
    history: List<TestHistoryEntry>,
    isRunning: Boolean,
    onViewAll: () -> Unit,
    onRetest: (String) -> Unit
) {
    LiquidGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.label_recent_history),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onViewAll,
                    modifier = Modifier.testTag("view_all_history_button")
                ) {
                    Text(
                        text = "View All (${history.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_history_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.take(3).forEach { entry ->
                    HistoryEntryRow(
                        entry = entry,
                        isRunning = isRunning,
                        onRetest = { onRetest(entry.url) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    history: List<TestHistoryEntry>,
    lifetimeCompleted: Int,
    isRunning: Boolean,
    onRetest: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LiquidGlassPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Local Test History",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "$lifetimeCompleted total completed test runs saved on device",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }

                if (history.isNotEmpty()) {
                    Surface(
                        onClick = onClearHistory,
                        shape = RoundedCornerShape(12.dp),
                        color = CoralDanger.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, CoralDanger.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.btn_clear_history),
                                tint = CoralDanger,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelMedium,
                                color = CoralDanger
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (history.isEmpty()) {
            LiquidGlassPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(28.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.14f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.empty_history_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.empty_history_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 108.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { entry ->
                    HistoryEntryCard(
                        entry = entry,
                        isRunning = isRunning,
                        onRetest = { onRetest(entry.url) },
                        onOpenExternal = { onOpenExternal(entry.url) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryRow(
    entry: TestHistoryEntry,
    isRunning: Boolean,
    onRetest: () -> Unit
) {
    val badgeColor = statusBadgeColor(entry.statusType)
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MidnightDeep.copy(alpha = 0.52f))
            .border(1.dp, GlassStrokeSubtle, shape)
            .clickable(enabled = !isRunning, onClick = onRetest)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.url,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatTimestamp(entry.timestampMillis)} • ${entry.completedRuns}/${entry.totalRuns} runs",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = badgeColor.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.42f))
        ) {
            Text(
                text = entry.statusType.label,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: TestHistoryEntry,
    isRunning: Boolean,
    onRetest: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val badgeColor = statusBadgeColor(entry.statusType)

    LiquidGlassPanel(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = entry.url,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = badgeColor.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
            ) {
                Text(
                    text = entry.statusType.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTimestamp(entry.timestampMillis),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Text(
                text = "${entry.completedRuns} / ${entry.totalRuns} completed",
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onRetest,
                enabled = !isRunning,
                shape = RoundedCornerShape(12.dp),
                color = ElectricCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 9.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Load URL",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }

            Surface(
                onClick = onOpenExternal,
                shape = RoundedCornerShape(12.dp),
                color = GlassSurfaceElevated.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, GlassStrokeSubtle),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 9.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.btn_open_website),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingGlassNavBar(
    selectedDestination: AppDestination,
    historyCount: Int,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .clip(pillShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        GlassSurfaceElevated.copy(alpha = 0.90f),
                        GlassSurface.copy(alpha = 0.86f)
                    )
                )
            )
            .border(1.dp, GlassStrokeBright, pillShape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NavPillItem(
            label = stringResource(R.string.tab_dashboard),
            selected = selectedDestination == AppDestination.DASHBOARD,
            icon = if (selectedDestination == AppDestination.DASHBOARD) Icons.Rounded.Dashboard else Icons.Outlined.Dashboard,
            onClick = { onSelect(AppDestination.DASHBOARD) },
            testTag = "nav_dashboard",
            modifier = Modifier.weight(1f)
        )
        NavPillItem(
            label = if (historyCount > 0) "${stringResource(R.string.tab_history)} ($historyCount)" else stringResource(R.string.tab_history),
            selected = selectedDestination == AppDestination.HISTORY,
            icon = if (selectedDestination == AppDestination.HISTORY) Icons.Rounded.History else Icons.Outlined.History,
            onClick = { onSelect(AppDestination.HISTORY) },
            testTag = "nav_history",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NavPillItem(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(
                            ElectricCyan.copy(alpha = 0.25f),
                            LuminousPurple.copy(alpha = 0.28f)
                        )
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) ElectricCyan.copy(alpha = 0.55f) else Color.Transparent,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 12.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) ElectricCyan else TextMuted,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) TextPrimary else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SingleFileHtmlDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val htmlSource = remember {
        runCatching {
            context.assets.open("index.html").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }

    val saveHtmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(htmlSource.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "Saved index.html successfully", Toast.LENGTH_SHORT).show()
                onDismiss()
            }.onFailure {
                Toast.makeText(context, "Could not save index.html", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightNavy,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Standalone index.html",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Save or copy the complete single-file HTML/CSS/JS version of WebVisit Tester (${htmlSource.length} bytes) directly to your device Downloads or host via GitHub Pages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MidnightDeep)
                        .border(1.dp, GlassStrokeSubtle, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = htmlSource.take(700) + if (htmlSource.length > 700) "\n..." else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GradientPrimaryButton(
                        text = "Save index.html",
                        icon = Icons.Rounded.Download,
                        onClick = { saveHtmlLauncher.launch("index.html") },
                        testTag = "save_html_button",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("index.html", htmlSource))
                    Toast.makeText(context, "Copied index.html to clipboard", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier.testTag("copy_html_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy HTML", color = ElectricCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextMuted)
            }
        }
    )
}

private fun statusBadgeColor(type: HistoryStatusType): Color {
    return when (type) {
        HistoryStatusType.COMPLETED -> EmeraldSuccess
        HistoryStatusType.STOPPED -> AmberWarning
        HistoryStatusType.OPENED_EXTERNALLY -> ElectricCyan
        HistoryStatusType.BLOCKED, HistoryStatusType.ERROR -> CoralDanger
    }
}

private fun formatTimer(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return "Unknown time"
    val formatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(millis))
}

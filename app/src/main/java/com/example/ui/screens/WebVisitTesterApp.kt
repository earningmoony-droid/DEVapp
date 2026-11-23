package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.AutoScrollDirection
import com.example.model.ClickTargetItem
import com.example.model.ClickTestStatus
import com.example.model.ScrollIndicatorState
import com.example.model.ScrollSpeed
import com.example.model.TestSessionStatus
import com.example.model.VpnConnectionStatus
import com.example.model.VpnProvider
import com.example.ui.WebVisitUiState
import com.example.ui.WebVisitViewModel
import com.example.ui.components.DurationStepperRow
import com.example.ui.components.GlassActionButton
import com.example.ui.components.GradientPrimaryButton
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassPanel
import com.example.ui.components.WebPreviewCard
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassStrokeBright
import com.example.ui.theme.GlassStrokeSubtle
import com.example.ui.theme.GlassStrokeViolet
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.LuminousPurple
import com.example.ui.theme.MidnightDeep
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun WebVisitTesterApp(
    viewModel: WebVisitViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh installed VPN providers when returning from system VPN Settings or provider app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshInstalledProviders()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            }.onFailure {
                Toast.makeText(context, "No browser app found to open this URL.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LiquidGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .navigationBarsPadding()
        ) {
            MinimalHeaderBar(
                status = uiState.status,
                phaseRemainingSec = uiState.phaseRemainingSec,
                isRunning = uiState.isRunning,
                onShowHtmlClick = { viewModel.setHtmlSheetVisible(true) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val isWideScreen = maxWidth >= 820.dp
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                                MinimalControlPanel(
                                    uiState = uiState,
                                    onUrlChanged = viewModel::onUrlChanged,
                                    onApplyProtocol = viewModel::applyQuickProtocol,
                                    onStartTest = viewModel::startTest,
                                    onStopTest = viewModel::stopTest,
                                    onViewDurationChanged = viewModel::updateViewDuration,
                                    onPauseDurationChanged = viewModel::updatePauseDuration,
                                    onScrollDown = viewModel::startAutoScrollDown,
                                    onScrollUp = viewModel::startAutoScrollUp,
                                    onRandomScroll = viewModel::startRandomScroll,
                                    onStopScroll = viewModel::stopAutoScroll,
                                    onScrollSpeedChanged = viewModel::setScrollSpeed,
                                    onAutoNextChanged = viewModel::setAutoNextEnabled,
                                    onTotalRunsChanged = viewModel::updateTotalRuns
                                )

                                ClicksAutomationPanel(
                                    uiState = uiState,
                                    onAuthorizedChanged = viewModel::setAuthorizedOwnSite,
                                    onSelectorChanged = viewModel::onClickSelectorChanged,
                                    onSelectTarget = viewModel::selectDiscoveredTarget,
                                    onScanTargets = viewModel::scanClickTargets,
                                    onClickIntervalChanged = viewModel::updateClickInterval,
                                    onSingleClickTest = viewModel::triggerSingleClickTest,
                                    onStartAutoClick = viewModel::startAutoClick,
                                    onStopAutoClick = { viewModel.stopAutoClick() }
                                )

                                VpnTestingPanel(
                                    uiState = uiState,
                                    onSelectProvider = viewModel::selectVpnProvider,
                                    onConnectDisconnect = viewModel::openVpnConnectOrDisconnect,
                                    onVerifyConnection = viewModel::verifyVpnConnection,
                                    onRequireVpnChanged = viewModel::setRequireVpnForTest
                                )
                            }

                            WebPreviewCard(
                                activeUrl = uiState.activeUrl,
                                fallbackUrl = uiState.urlInput,
                                loadToken = uiState.previewLoadToken,
                                status = uiState.status,
                                isPreviewLoading = uiState.isPreviewLoading,
                                blockedReason = uiState.previewBlockedReason,
                                phaseRemainingSec = uiState.phaseRemainingSec,
                                scrollDirection = uiState.scrollDirection,
                                scrollSpeed = uiState.scrollSpeed,
                                canGoBack = uiState.canGoBack,
                                pendingCommand = uiState.webCommand,
                                onConsumeCommand = viewModel::consumeWebCommand,
                                onBackClick = viewModel::triggerBack,
                                onHomeClick = viewModel::triggerHome,
                                onReloadClick = viewModel::triggerReload,
                                onPageStarted = viewModel::onPreviewPageStarted,
                                onPageFinished = viewModel::onPreviewPageFinished,
                                onCanGoBackChanged = viewModel::updateCanGoBack,
                                onScrollStatusChanged = viewModel::updateScrollStatus,
                                onBlockedOrError = viewModel::onPreviewBlockedOrError,
                                onOpenExternal = openWebsiteInBrowser,
                                onTargetsScanned = viewModel::onClickTargetsScanned,
                                onClickExecuted = viewModel::onSingleClickExecuted,
                                modifier = Modifier.weight(1.05f)
                            )
                        }
                    } else {
                        MinimalControlPanel(
                            uiState = uiState,
                            onUrlChanged = viewModel::onUrlChanged,
                            onApplyProtocol = viewModel::applyQuickProtocol,
                            onStartTest = viewModel::startTest,
                            onStopTest = viewModel::stopTest,
                            onViewDurationChanged = viewModel::updateViewDuration,
                            onPauseDurationChanged = viewModel::updatePauseDuration,
                            onScrollDown = viewModel::startAutoScrollDown,
                            onScrollUp = viewModel::startAutoScrollUp,
                            onRandomScroll = viewModel::startRandomScroll,
                            onStopScroll = viewModel::stopAutoScroll,
                            onScrollSpeedChanged = viewModel::setScrollSpeed,
                            onAutoNextChanged = viewModel::setAutoNextEnabled,
                            onTotalRunsChanged = viewModel::updateTotalRuns
                        )

                        ClicksAutomationPanel(
                            uiState = uiState,
                            onAuthorizedChanged = viewModel::setAuthorizedOwnSite,
                            onSelectorChanged = viewModel::onClickSelectorChanged,
                            onSelectTarget = viewModel::selectDiscoveredTarget,
                            onScanTargets = viewModel::scanClickTargets,
                            onClickIntervalChanged = viewModel::updateClickInterval,
                            onSingleClickTest = viewModel::triggerSingleClickTest,
                            onStartAutoClick = viewModel::startAutoClick,
                            onStopAutoClick = { viewModel.stopAutoClick() }
                        )

                        VpnTestingPanel(
                            uiState = uiState,
                            onSelectProvider = viewModel::selectVpnProvider,
                            onConnectDisconnect = viewModel::openVpnConnectOrDisconnect,
                            onVerifyConnection = viewModel::verifyVpnConnection,
                            onRequireVpnChanged = viewModel::setRequireVpnForTest
                        )

                        WebPreviewCard(
                            activeUrl = uiState.activeUrl,
                            fallbackUrl = uiState.urlInput,
                            loadToken = uiState.previewLoadToken,
                            status = uiState.status,
                            isPreviewLoading = uiState.isPreviewLoading,
                            blockedReason = uiState.previewBlockedReason,
                            phaseRemainingSec = uiState.phaseRemainingSec,
                            scrollDirection = uiState.scrollDirection,
                            scrollSpeed = uiState.scrollSpeed,
                            canGoBack = uiState.canGoBack,
                            pendingCommand = uiState.webCommand,
                            onConsumeCommand = viewModel::consumeWebCommand,
                            onBackClick = viewModel::triggerBack,
                            onHomeClick = viewModel::triggerHome,
                            onReloadClick = viewModel::triggerReload,
                            onPageStarted = viewModel::onPreviewPageStarted,
                            onPageFinished = viewModel::onPreviewPageFinished,
                            onCanGoBackChanged = viewModel::updateCanGoBack,
                            onScrollStatusChanged = viewModel::updateScrollStatus,
                            onBlockedOrError = viewModel::onPreviewBlockedOrError,
                            onOpenExternal = openWebsiteInBrowser,
                            onTargetsScanned = viewModel::onClickTargetsScanned,
                            onClickExecuted = viewModel::onSingleClickExecuted
                        )
                    }
                }
            }
        }

        if (uiState.isHtmlSheetVisible) {
            SingleFileHtmlDialog(
                onDismiss = { viewModel.setHtmlSheetVisible(false) }
            )
        }
    }
}

@Composable
private fun MinimalHeaderBar(
    status: TestSessionStatus,
    phaseRemainingSec: Int,
    isRunning: Boolean,
    onShowHtmlClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            TestSessionStatus.VIEWING, TestSessionStatus.COMPLETED -> EmeraldSuccess
            TestSessionStatus.PAUSING_BETWEEN, TestSessionStatus.STOPPED -> AmberWarning
            TestSessionStatus.BLOCKED, TestSessionStatus.ERROR -> CoralDanger
            else -> ElectricCyan
        },
        label = "live_status_color"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_webvisit_foreground_1795404951294),
                contentDescription = "WebVisit Tester icon",
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GlassStrokeBright, RoundedCornerShape(12.dp))
            )

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("live_status_badge")
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
                    val liveLabel = if (isRunning) {
                        "${status.displayLabel} • ${formatTimer(phaseRemainingSec)}"
                    } else {
                        status.displayLabel
                    }
                    Text(
                        text = liveLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun MinimalControlPanel(
    uiState: WebVisitUiState,
    onUrlChanged: (String) -> Unit,
    onApplyProtocol: (String) -> Unit,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit,
    onViewDurationChanged: (Int) -> Unit,
    onPauseDurationChanged: (Int) -> Unit,
    onScrollDown: () -> Unit,
    onScrollUp: () -> Unit,
    onRandomScroll: () -> Unit,
    onStopScroll: () -> Unit,
    onScrollSpeedChanged: (ScrollSpeed) -> Unit,
    onAutoNextChanged: (Boolean) -> Unit,
    onTotalRunsChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val animatedProgress by animateFloatAsState(
        targetValue = uiState.overallProgress,
        animationSpec = spring(),
        label = "session_progress"
    )

    LiquidGlassPanel(modifier = modifier.fillMaxWidth()) {
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

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                modifier = Modifier.weight(1f)
            )

            GlassActionButton(
                text = stringResource(R.string.btn_stop),
                icon = Icons.Rounded.Stop,
                onClick = onStopTest,
                enabled = uiState.isRunning,
                accentColor = CoralDanger,
                testTag = "stop_test_button",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusSummary = if (uiState.isRunning) {
                "${uiState.status.displayLabel} • Run ${uiState.currentRunNumber}/${uiState.activeTotalRuns}"
            } else {
                "${stringResource(R.string.label_live_status)}: ${uiState.status.displayLabel}"
            }
            Text(
                text = statusSummary,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MidnightDeep.copy(alpha = 0.78f))
                .border(1.dp, GlassStrokeSubtle, RoundedCornerShape(999.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(ElectricCyan, IndigoAccent, LuminousPurple)
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(12.dp))

        DurationStepperRow(
            label = stringResource(R.string.label_pause_interval),
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

        // Auto Scroll Down / Up / Random / Stop + Status Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.label_auto_scroll),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = TextPrimary
            )

            val scrollColor = when (uiState.scrollStatus) {
                ScrollIndicatorState.SCROLLING_DOWN -> ElectricCyan
                ScrollIndicatorState.SCROLLING_UP -> LuminousPurple
                ScrollIndicatorState.RANDOM_DOWN, ScrollIndicatorState.RANDOM_UP -> EmeraldSuccess
                ScrollIndicatorState.RESTRICTED -> CoralDanger
                ScrollIndicatorState.AT_TOP, ScrollIndicatorState.AT_BOTTOM, ScrollIndicatorState.STOPPED -> AmberWarning
                ScrollIndicatorState.IDLE -> TextMuted
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = scrollColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, scrollColor.copy(alpha = 0.4f)),
                modifier = Modifier.testTag("scroll_status_badge")
            ) {
                Text(
                    text = uiState.scrollStatus.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = scrollColor,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GlassActionButton(
                text = stringResource(R.string.btn_scroll_down),
                icon = Icons.Rounded.ArrowDownward,
                onClick = onScrollDown,
                enabled = uiState.scrollDirection != AutoScrollDirection.DOWN,
                accentColor = ElectricCyan,
                testTag = "scroll_down_button",
                modifier = Modifier.weight(1f)
            )
            GlassActionButton(
                text = stringResource(R.string.btn_scroll_up),
                icon = Icons.Rounded.ArrowUpward,
                onClick = onScrollUp,
                enabled = uiState.scrollDirection != AutoScrollDirection.UP,
                accentColor = LuminousPurple,
                testTag = "scroll_up_button",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GlassActionButton(
                text = stringResource(R.string.btn_scroll_random),
                icon = Icons.Rounded.Shuffle,
                onClick = onRandomScroll,
                enabled = uiState.scrollDirection != AutoScrollDirection.RANDOM,
                accentColor = EmeraldSuccess,
                testTag = "scroll_random_button",
                modifier = Modifier.weight(1f)
            )
            GlassActionButton(
                text = stringResource(R.string.btn_stop_scroll),
                icon = Icons.Rounded.Stop,
                onClick = onStopScroll,
                enabled = uiState.scrollDirection != AutoScrollDirection.NONE,
                accentColor = CoralDanger,
                testTag = "stop_scroll_button",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScrollSpeed.entries.forEach { speed ->
                val selected = uiState.scrollSpeed == speed
                val shape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(
                            if (selected) ElectricCyan.copy(alpha = 0.22f)
                            else MidnightDeep.copy(alpha = 0.45f)
                        )
                        .border(
                            1.dp,
                            if (selected) ElectricCyan.copy(alpha = 0.65f) else GlassStrokeSubtle,
                            shape
                        )
                        .clickable { onScrollSpeedChanged(speed) }
                        .padding(vertical = 8.dp)
                        .testTag("scroll_speed_${speed.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = speed.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) ElectricCyan else TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.label_auto_next),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
                Text(
                    text = if (uiState.autoNextEnabled) "Automatically continues next run" else "Stops after 1 viewing run",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Switch(
                checked = uiState.autoNextEnabled,
                onCheckedChange = onAutoNextChanged,
                enabled = !uiState.isRunning,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MidnightDeep,
                    checkedTrackColor = LuminousPurple,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = MidnightDeep.copy(alpha = 0.6f)
                ),
                modifier = Modifier.testTag("auto_next_switch")
            )
        }

        AnimatedVisibility(visible = uiState.autoNextEnabled) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                DurationStepperRow(
                    label = stringResource(R.string.label_total_runs),
                    valueText = "${uiState.totalRuns}x",
                    presets = listOf("2x" to 2, "3x" to 3, "5x" to 5, "10x" to 10),
                    currentValue = uiState.totalRuns,
                    enabled = !uiState.isRunning,
                    onDecrement = { onTotalRunsChanged(uiState.totalRuns - 1) },
                    onIncrement = { onTotalRunsChanged(uiState.totalRuns + 1) },
                    onPresetSelected = onTotalRunsChanged,
                    testTagPrefix = "total_runs"
                )
            }
        }
    }
}

@Composable
private fun VpnTestingPanel(
    uiState: WebVisitUiState,
    onSelectProvider: (VpnProvider) -> Unit,
    onConnectDisconnect: () -> Unit,
    onVerifyConnection: () -> Unit,
    onRequireVpnChanged: (Boolean) -> Unit
) {
    val vpnColor = when (uiState.vpnStatus) {
        VpnConnectionStatus.CONNECTED -> EmeraldSuccess
        VpnConnectionStatus.VERIFYING -> ElectricCyan
        VpnConnectionStatus.LOST -> CoralDanger
        VpnConnectionStatus.DISCONNECTED -> AmberWarning
    }

    LiquidGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VpnKey,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.label_vpn_module),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = vpnColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, vpnColor.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("vpn_status_badge")
            ) {
                val ifaceSuffix = uiState.vpnInterfaceName?.let { " • $it" } ?: ""
                Text(
                    text = "${uiState.vpnStatus.label}$ifaceSuffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = vpnColor,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Supported VPN Providers (Country/server chosen inside provider app)",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VpnProvider.entries.forEach { provider ->
                val selected = uiState.selectedVpnProvider == provider
                val installed = provider in uiState.installedProviders
                val shape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(
                            if (selected) ElectricCyan.copy(alpha = 0.2f)
                            else MidnightDeep.copy(alpha = 0.5f)
                        )
                        .border(
                            1.dp,
                            if (selected) ElectricCyan.copy(alpha = 0.6f) else GlassStrokeSubtle,
                            shape
                        )
                        .clickable { onSelectProvider(provider) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("vpn_provider_${provider.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (installed && provider != VpnProvider.ANDROID_SYSTEM) {
                            "${provider.displayName} ✓"
                        } else {
                            provider.displayName
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) ElectricCyan else TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isConnected = uiState.vpnStatus == VpnConnectionStatus.CONNECTED
            GlassActionButton(
                text = if (isConnected) stringResource(R.string.btn_vpn_disconnect) else stringResource(R.string.btn_vpn_connect),
                icon = Icons.Rounded.Public,
                onClick = onConnectDisconnect,
                accentColor = if (isConnected) AmberWarning else ElectricCyan,
                testTag = "vpn_connect_button",
                modifier = Modifier.weight(1.1f)
            )
            GlassActionButton(
                text = if (uiState.isVerifyingVpn) "Checking..." else stringResource(R.string.btn_vpn_verify),
                icon = Icons.Rounded.VerifiedUser,
                onClick = onVerifyConnection,
                enabled = !uiState.isVerifyingVpn,
                accentColor = EmeraldSuccess,
                testTag = "vpn_verify_button",
                modifier = Modifier.weight(0.9f)
            )
        }

        uiState.vpnVerification?.let { info ->
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MidnightDeep.copy(alpha = 0.6f))
                    .border(1.dp, GlassStrokeSubtle, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                if (info.isVerified) {
                    val location = listOfNotNull(info.country, info.region).joinToString(", ").ifBlank { "Unknown" }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Exit IP: ${info.publicIp ?: "Unknown"} • Region: $location",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSuccess
                        )
                        info.networkOrg?.let { org ->
                            Text(
                                text = "Network: $org",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Text(
                        text = info.errorMessage ?: "Verification unavailable",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoralDanger
                    )
                }
            }
        }

        uiState.vpnActionMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.labelSmall,
                color = AmberWarning
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Require Active VPN",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
                Text(
                    text = "Verify VPN before loading & stop test if VPN disconnects",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = uiState.requireVpnForTest,
                onCheckedChange = onRequireVpnChanged,
                enabled = !uiState.isRunning,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MidnightDeep,
                    checkedTrackColor = EmeraldSuccess,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = MidnightDeep.copy(alpha = 0.6f)
                ),
                modifier = Modifier.testTag("require_vpn_switch")
            )
        }
    }
}

@Composable
private fun ClicksAutomationPanel(
    uiState: WebVisitUiState,
    onAuthorizedChanged: (Boolean) -> Unit,
    onSelectorChanged: (String) -> Unit,
    onSelectTarget: (ClickTargetItem) -> Unit,
    onScanTargets: () -> Unit,
    onClickIntervalChanged: (Int) -> Unit,
    onSingleClickTest: () -> Unit,
    onStartAutoClick: () -> Unit,
    onStopAutoClick: () -> Unit
) {
    val clickBadgeColor = when (uiState.clickStatus) {
        ClickTestStatus.RUNNING -> EmeraldSuccess
        ClickTestStatus.BLOCKED_AD, ClickTestStatus.RESTRICTED -> CoralDanger
        ClickTestStatus.STOPPED, ClickTestStatus.NO_TARGET -> AmberWarning
        ClickTestStatus.IDLE -> ElectricCyan
    }

    LiquidGlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.TouchApp,
                    contentDescription = null,
                    tint = LuminousPurple,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.label_click_module),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = clickBadgeColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, clickBadgeColor.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("click_counter_badge")
            ) {
                Text(
                    text = "Clicks: ${uiState.clickCount} • ${uiState.clickStatus.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = clickBadgeColor,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Authorized Own Site / Test Page",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
                Text(
                    text = "Ads & sponsored clicks are strictly blocked",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = uiState.isAuthorizedOwnSite,
                onCheckedChange = onAuthorizedChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MidnightDeep,
                    checkedTrackColor = ElectricCyan,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = MidnightDeep.copy(alpha = 0.6f)
                ),
                modifier = Modifier.testTag("authorized_site_switch")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = uiState.clickSelector,
            onValueChange = onSelectorChanged,
            enabled = !uiState.isAutoClickRunning,
            singleLine = true,
            placeholder = {
                Text(
                    text = "CSS selector (e.g. #submit-btn, .cta)",
                    color = TextMuted
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LuminousPurple,
                unfocusedBorderColor = GlassStrokeViolet,
                focusedContainerColor = MidnightDeep.copy(alpha = 0.65f),
                unfocusedContainerColor = MidnightDeep.copy(alpha = 0.50f),
                disabledContainerColor = MidnightDeep.copy(alpha = 0.35f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                disabledTextColor = TextSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("click_selector_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassActionButton(
                text = if (uiState.isScanningTargets) "Scanning..." else stringResource(R.string.btn_scan_targets),
                icon = Icons.Rounded.Search,
                onClick = onScanTargets,
                enabled = !uiState.isScanningTargets && !uiState.isAutoClickRunning,
                accentColor = ElectricCyan,
                testTag = "scan_targets_button",
                modifier = Modifier.weight(1f)
            )
            GlassActionButton(
                text = stringResource(R.string.btn_single_click),
                icon = Icons.Rounded.TouchApp,
                onClick = onSingleClickTest,
                enabled = !uiState.isAutoClickRunning,
                accentColor = LuminousPurple,
                testTag = "single_click_test_button",
                modifier = Modifier.weight(1f)
            )
        }

        if (uiState.discoveredClickTargets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uiState.discoveredClickTargets.forEachIndexed { idx, item ->
                    val selected = uiState.clickSelector == item.selector
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(
                                if (selected) LuminousPurple.copy(alpha = 0.24f)
                                else MidnightDeep.copy(alpha = 0.52f)
                            )
                            .border(
                                1.dp,
                                if (selected) LuminousPurple.copy(alpha = 0.65f) else GlassStrokeSubtle,
                                shape
                            )
                            .clickable(enabled = !uiState.isAutoClickRunning) { onSelectTarget(item) }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("click_target_chip_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${item.tagType}: ${item.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) TextPrimary else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        DurationStepperRow(
            label = stringResource(R.string.label_click_interval),
            valueText = "${uiState.clickIntervalSec}s",
            presets = listOf("1s" to 1, "2s" to 2, "5s" to 5, "10s" to 10),
            currentValue = uiState.clickIntervalSec,
            enabled = !uiState.isAutoClickRunning,
            onDecrement = { onClickIntervalChanged(uiState.clickIntervalSec - 1) },
            onIncrement = { onClickIntervalChanged(uiState.clickIntervalSec + 1) },
            onPresetSelected = onClickIntervalChanged,
            testTagPrefix = "click_interval"
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlassActionButton(
                text = stringResource(R.string.btn_start_click),
                icon = Icons.Rounded.PlayArrow,
                onClick = onStartAutoClick,
                enabled = !uiState.isAutoClickRunning,
                accentColor = EmeraldSuccess,
                testTag = "start_click_button",
                modifier = Modifier.weight(1f)
            )
            GlassActionButton(
                text = stringResource(R.string.btn_stop_click),
                icon = Icons.Rounded.Stop,
                onClick = onStopAutoClick,
                enabled = uiState.isAutoClickRunning,
                accentColor = CoralDanger,
                testTag = "stop_click_button",
                modifier = Modifier.weight(1f)
            )
        }

        uiState.clickStatusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.labelSmall,
                color = when (uiState.clickStatus) {
                    ClickTestStatus.BLOCKED_AD, ClickTestStatus.RESTRICTED -> CoralDanger
                    ClickTestStatus.RUNNING -> EmeraldSuccess
                    ClickTestStatus.STOPPED, ClickTestStatus.NO_TARGET -> AmberWarning
                    ClickTestStatus.IDLE -> ElectricCyan
                }
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
                    text = "Save or copy the minimal single-file HTML/CSS/JS version of WebVisit Tester (${htmlSource.length} bytes) with Random Scroll and VPN Testing interface.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MidnightDeep)
                        .border(1.dp, GlassStrokeSubtle, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = htmlSource.take(650) + if (htmlSource.length > 650) "\n..." else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }

                GradientPrimaryButton(
                    text = "Save index.html",
                    icon = Icons.Rounded.Download,
                    onClick = { saveHtmlLauncher.launch("index.html") },
                    testTag = "save_html_button",
                    modifier = Modifier.fillMaxWidth()
                )
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

private fun formatTimer(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

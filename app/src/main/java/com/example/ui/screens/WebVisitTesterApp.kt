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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.ScrollSpeed
import com.example.model.TestSessionStatus
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
import com.example.ui.theme.JetBrainsMonoFontFamily
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
                            MinimalControlPanel(
                                uiState = uiState,
                                onUrlChanged = viewModel::onUrlChanged,
                                onApplyProtocol = viewModel::applyQuickProtocol,
                                onStartTest = viewModel::startTest,
                                onStopTest = viewModel::stopTest,
                                onViewDurationChanged = viewModel::updateViewDuration,
                                onPauseDurationChanged = viewModel::updatePauseDuration,
                                onAutoScrollChanged = viewModel::setAutoScrollEnabled,
                                onScrollSpeedChanged = viewModel::setScrollSpeed,
                                onAutoNextChanged = viewModel::setAutoNextEnabled,
                                onTotalRunsChanged = viewModel::updateTotalRuns,
                                modifier = Modifier.weight(0.95f)
                            )

                            WebPreviewCard(
                                activeUrl = uiState.activeUrl,
                                fallbackUrl = uiState.urlInput,
                                loadToken = uiState.previewLoadToken,
                                status = uiState.status,
                                isPreviewLoading = uiState.isPreviewLoading,
                                blockedReason = uiState.previewBlockedReason,
                                phaseRemainingSec = uiState.phaseRemainingSec,
                                autoScrollEnabled = uiState.autoScrollEnabled,
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
                                onBlockedOrError = viewModel::onPreviewBlockedOrError,
                                onOpenExternal = openWebsiteInBrowser,
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
                            onAutoScrollChanged = viewModel::setAutoScrollEnabled,
                            onScrollSpeedChanged = viewModel::setScrollSpeed,
                            onAutoNextChanged = viewModel::setAutoNextEnabled,
                            onTotalRunsChanged = viewModel::updateTotalRuns
                        )

                        WebPreviewCard(
                            activeUrl = uiState.activeUrl,
                            fallbackUrl = uiState.urlInput,
                            loadToken = uiState.previewLoadToken,
                            status = uiState.status,
                            isPreviewLoading = uiState.isPreviewLoading,
                            blockedReason = uiState.previewBlockedReason,
                            phaseRemainingSec = uiState.phaseRemainingSec,
                            autoScrollEnabled = uiState.autoScrollEnabled,
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
                            onBlockedOrError = viewModel::onPreviewBlockedOrError,
                            onOpenExternal = openWebsiteInBrowser
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

            // Live testing status pill
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
    onAutoScrollChanged: (Boolean) -> Unit,
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

        // Start & Stop controls
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

        // Live progress line
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

        // Viewing Duration & Pause Interval
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

        // Auto Scroll & Scroll Speed
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.label_auto_scroll),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.label_scroll_speed),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Switch(
                checked = uiState.autoScrollEnabled,
                onCheckedChange = onAutoScrollChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MidnightDeep,
                    checkedTrackColor = ElectricCyan,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = MidnightDeep.copy(alpha = 0.6f)
                ),
                modifier = Modifier.testTag("auto_scroll_switch")
            )
        }

        AnimatedVisibility(visible = uiState.autoScrollEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Auto Next toggle & Runs stepper
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
                    text = "Save or copy the minimal single-file HTML/CSS/JS version of WebVisit Tester (${htmlSource.length} bytes) with zero history or persistent storage.",
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

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.model.ErrorDetectionSeverity
import com.example.model.TestSessionStatus
import com.example.ui.WebVisitUiState
import com.example.ui.WebVisitViewModel
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
            HeaderBar(
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
                                UrlTestControlsCard(
                                    uiState = uiState,
                                    onUrlChanged = viewModel::onUrlChanged,
                                    onApplyProtocol = viewModel::applyQuickProtocol,
                                    onStartTest = viewModel::startTest,
                                    onStopTest = viewModel::stopTest
                                )

                                ImpressionClickTrackingCard(
                                    uiState = uiState,
                                    onCheckTracking = viewModel::checkClickTrackingHealth
                                )
                            }

                            WebPreviewCard(
                                activeUrl = uiState.activeUrl,
                                fallbackUrl = uiState.urlInput,
                                loadToken = uiState.previewLoadToken,
                                status = uiState.status,
                                isPreviewLoading = uiState.isPreviewLoading,
                                blockedReason = uiState.previewBlockedReason,
                                pendingCommand = uiState.webCommand,
                                onConsumeCommand = viewModel::consumeWebCommand,
                                onReloadClick = viewModel::triggerReload,
                                onPageStarted = viewModel::onPreviewPageStarted,
                                onPageFinished = viewModel::onPreviewPageFinished,
                                onBlockedOrError = viewModel::onPreviewBlockedOrError,
                                onOpenExternal = openWebsiteInBrowser,
                                onDirectPreviewClick = viewModel::onDirectPreviewClick,
                                onAdElementObserved = viewModel::onAdElementObserved,
                                onInvalidTrafficNotice = viewModel::onInvalidTrafficOrNotice,
                                onListenerHealthResult = viewModel::onListenerHealthResult,
                                modifier = Modifier.weight(1.05f)
                            )
                        }
                    } else {
                        UrlTestControlsCard(
                            uiState = uiState,
                            onUrlChanged = viewModel::onUrlChanged,
                            onApplyProtocol = viewModel::applyQuickProtocol,
                            onStartTest = viewModel::startTest,
                            onStopTest = viewModel::stopTest
                        )

                        ImpressionClickTrackingCard(
                            uiState = uiState,
                            onCheckTracking = viewModel::checkClickTrackingHealth
                        )

                        WebPreviewCard(
                            activeUrl = uiState.activeUrl,
                            fallbackUrl = uiState.urlInput,
                            loadToken = uiState.previewLoadToken,
                            status = uiState.status,
                            isPreviewLoading = uiState.isPreviewLoading,
                            blockedReason = uiState.previewBlockedReason,
                            pendingCommand = uiState.webCommand,
                            onConsumeCommand = viewModel::consumeWebCommand,
                            onReloadClick = viewModel::triggerReload,
                            onPageStarted = viewModel::onPreviewPageStarted,
                            onPageFinished = viewModel::onPreviewPageFinished,
                            onBlockedOrError = viewModel::onPreviewBlockedOrError,
                            onOpenExternal = openWebsiteInBrowser,
                            onDirectPreviewClick = viewModel::onDirectPreviewClick,
                            onAdElementObserved = viewModel::onAdElementObserved,
                            onInvalidTrafficNotice = viewModel::onInvalidTrafficOrNotice,
                            onListenerHealthResult = viewModel::onListenerHealthResult
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
private fun HeaderBar(
    status: TestSessionStatus,
    onShowHtmlClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (status) {
            TestSessionStatus.LOADED -> EmeraldSuccess
            TestSessionStatus.BLOCKED, TestSessionStatus.STOPPED -> AmberWarning
            TestSessionStatus.FAILED, TestSessionStatus.NETWORK_ERROR -> CoralDanger
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

            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ElectricCyan,
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
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("export_html_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
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
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
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
private fun UrlTestControlsCard(
    uiState: WebVisitUiState,
    onUrlChanged: (String) -> Unit,
    onApplyProtocol: (String) -> Unit,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isLoading = uiState.status == TestSessionStatus.LOADING
    val hasActiveSession = uiState.status != TestSessionStatus.IDLE && uiState.status != TestSessionStatus.STOPPED
    val animatedProgress by animateFloatAsState(
        targetValue = uiState.overallProgress,
        animationSpec = spring(),
        label = "session_progress"
    )

    LiquidGlassPanel(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = uiState.urlInput,
            onValueChange = onUrlChanged,
            enabled = !isLoading,
            singleLine = true,
            isError = uiState.urlError != null,
            label = {
                Text(
                    text = stringResource(R.string.url_label),
                    color = TextSecondary
                )
            },
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
                if (uiState.urlInput.isNotEmpty() && !isLoading) {
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
                enabled = !isLoading,
                onClick = { onApplyProtocol("https://") },
                testTag = "protocol_https_chip",
                modifier = Modifier.weight(1f)
            )
            ProtocolChip(
                label = "http://",
                selected = !isHttps,
                enabled = !isLoading,
                onClick = { onApplyProtocol("http://") },
                testTag = "protocol_http_chip",
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
                enabled = !isLoading,
                testTag = "start_test_button",
                modifier = Modifier.weight(1f)
            )

            GlassActionButton(
                text = stringResource(R.string.btn_stop),
                icon = Icons.Rounded.Stop,
                onClick = onStopTest,
                enabled = hasActiveSession,
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
            Text(
                text = "${stringResource(R.string.label_live_status)}: ${uiState.status.displayLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.testTag("live_test_status_text")
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
    }
}

@Composable
private fun ImpressionClickTrackingCard(
    uiState: WebVisitUiState,
    onCheckTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassPanel(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = stringResource(R.string.label_tracking_section),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrackingMetricCell(
                title = "Impression Status",
                statusLabel = uiState.impressionStatusText,
                subText = "Verified Loads: ${uiState.verifiedLoadCount}",
                accent = when {
                    uiState.impressionStatusText.contains("Verified", ignoreCase = true) &&
                        !uiState.impressionStatusText.contains("Not", ignoreCase = true) -> EmeraldSuccess
                    uiState.impressionStatusText.contains("Not Verified", ignoreCase = true) -> CoralDanger
                    uiState.impressionStatusText == "Stopped" -> AmberWarning
                    else -> ElectricCyan
                },
                testTag = "impression_status_cell",
                modifier = Modifier.weight(1f)
            )
            TrackingMetricCell(
                title = "Click Tracking Status",
                statusLabel = uiState.clickTrackingStatusText,
                subText = "UI Clicks: ${uiState.verifiedUiClicks}",
                accent = when {
                    uiState.clickTrackingStatusText == "Click Event Received" -> EmeraldSuccess
                    uiState.clickTrackingStatusText == "Listener Active" ||
                        uiState.clickTrackingStatusText == "Ad Network Protected" -> ElectricCyan
                    uiState.clickTrackingStatusText.contains("Blocked", ignoreCase = true) ||
                        uiState.clickTrackingStatusText.contains("Invalid", ignoreCase = true) ||
                        uiState.clickTrackingStatusText == "Stopped" -> AmberWarning
                    uiState.clickTrackingStatusText.contains("Error", ignoreCase = true) ||
                        uiState.clickTrackingStatusText == "Unavailable" -> CoralDanger
                    else -> LuminousPurple
                },
                testTag = "click_tracking_status_cell",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusDetailCell(
                title = "Page Load Status",
                value = uiState.pageLoadStatusText,
                accent = when {
                    uiState.pageLoadStatusText.startsWith("Loaded") -> EmeraldSuccess
                    uiState.pageLoadStatusText.contains("Failed") || uiState.pageLoadStatusText.contains("Error") -> CoralDanger
                    uiState.pageLoadStatusText == "Stopped" -> AmberWarning
                    else -> TextPrimary
                },
                testTag = "page_load_status_cell",
                modifier = Modifier.weight(1f)
            )
            StatusDetailCell(
                title = "Click Event Listener",
                value = uiState.clickListenerStatusText,
                accent = when {
                    uiState.clickListenerStatusText.startsWith("Active") -> EmeraldSuccess
                    uiState.clickListenerStatusText.contains("Blocked") || uiState.clickListenerStatusText == "Stopped" -> AmberWarning
                    uiState.clickListenerStatusText.contains("Error") || uiState.clickListenerStatusText == "Unavailable" -> CoralDanger
                    else -> TextPrimary
                },
                testTag = "click_listener_status_cell",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val errorColor = when (uiState.errorSeverity) {
            ErrorDetectionSeverity.ERROR -> CoralDanger
            ErrorDetectionSeverity.WARNING -> AmberWarning
            ErrorDetectionSeverity.INFO -> ElectricCyan
            ErrorDetectionSeverity.NONE -> EmeraldSuccess
        }
        val errorBorderColor = when (uiState.errorSeverity) {
            ErrorDetectionSeverity.ERROR -> CoralDanger.copy(alpha = 0.45f)
            ErrorDetectionSeverity.WARNING -> AmberWarning.copy(alpha = 0.45f)
            ErrorDetectionSeverity.INFO -> ElectricCyan.copy(alpha = 0.4f)
            ErrorDetectionSeverity.NONE -> GlassStrokeSubtle
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MidnightDeep.copy(alpha = 0.62f))
                .border(1.dp, errorBorderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .testTag("error_detection_box"),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "CLICK TRACKING ERROR DETECTION",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Text(
                text = uiState.errorDetectionText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = errorColor
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        GlassActionButton(
            text = stringResource(R.string.btn_check_tracking),
            icon = Icons.Rounded.VerifiedUser,
            onClick = onCheckTracking,
            enabled = uiState.activeUrl != null &&
                uiState.status != TestSessionStatus.LOADING &&
                uiState.status != TestSessionStatus.FAILED &&
                uiState.status != TestSessionStatus.NETWORK_ERROR &&
                !uiState.isCheckingListener,
            accentColor = ElectricCyan,
            testTag = "check_tracking_button",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = uiState.statusMessage,
            style = MaterialTheme.typography.labelSmall,
            color = errorColor.takeIf { uiState.errorSeverity != ErrorDetectionSeverity.NONE } ?: TextSecondary,
            modifier = Modifier.testTag("tracking_status_message")
        )
    }
}

@Composable
private fun TrackingMetricCell(
    title: String,
    statusLabel: String,
    subText: String,
    accent: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MidnightDeep.copy(alpha = 0.62f))
            .border(1.dp, GlassStrokeSubtle, shape)
            .padding(horizontal = 11.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = accent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subText,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusDetailCell(
    title: String,
    value: String,
    accent: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MidnightDeep.copy(alpha = 0.58f))
            .border(1.dp, GlassStrokeSubtle, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title.uppercase(Locale.US),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProtocolChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
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
            .padding(vertical = 10.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
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
                    text = "Save or copy the standalone single-file HTML/CSS/JS version of WebVisit Tester — Impression & Click Tracking Test (${htmlSource.length} bytes).",
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

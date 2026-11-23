package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.TestSessionStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassStrokeSubtle
import com.example.ui.theme.GlassStrokeViolet
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LuminousPurple
import com.example.ui.theme.MidnightDeep
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPreviewCard(
    activeUrl: String?,
    fallbackUrl: String,
    loadToken: Int,
    status: TestSessionStatus,
    isPreviewLoading: Boolean,
    blockedReason: String?,
    phaseRemainingSec: Int,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onBlockedOrError: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var webProgress by remember { mutableIntStateOf(0) }
    var lastLoadedToken by remember { mutableIntStateOf(-1) }

    LiquidGlassPanel(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp)
    ) {
        // Preview header bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(ElectricCyan.copy(alpha = 0.25f), LuminousPurple.copy(alpha = 0.25f))
                            )
                        )
                        .border(1.dp, GlassStrokeViolet, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.label_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeUrl ?: fallbackUrl.ifBlank { "No URL loaded" },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            val openTarget = activeUrl ?: fallbackUrl
            Surface(
                onClick = { onOpenExternal(openTarget) },
                enabled = openTarget.startsWith("http://", ignoreCase = true) ||
                    openTarget.startsWith("https://", ignoreCase = true),
                shape = RoundedCornerShape(12.dp),
                color = ElectricCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("open_website_header_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.btn_open_website),
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.btn_open_website),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val viewportShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(viewportShape)
                .background(MidnightDeep.copy(alpha = 0.85f))
                .border(1.dp, GlassStrokeSubtle, viewportShape)
        ) {
            if (activeUrl != null && blockedReason == null && status != TestSessionStatus.PAUSING_BETWEEN) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                // Respect normal browser security (no mixed content spoofing/bypass)
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    webProgress = newProgress
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    onPageStarted()
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    onPageFinished()
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        val desc = error?.description?.toString()
                                            ?: "Unable to load website inside embedded preview."
                                        onBlockedOrError(desc)
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        val code = errorResponse?.statusCode ?: 0
                                        if (code >= 400) {
                                            val headers = errorResponse?.responseHeaders
                                            val xFrame = headers?.entries?.firstOrNull {
                                                it.key.equals("x-frame-options", ignoreCase = true)
                                            }?.value
                                            val msg = if (!xFrame.isNullOrBlank()) {
                                                "Site restricts embedding (X-Frame-Options: $xFrame)."
                                            } else {
                                                "HTTP $code response received. Open directly in browser if needed."
                                            }
                                            onBlockedOrError(msg)
                                        }
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    // Never bypass SSL security restrictions
                                    handler?.cancel()
                                    onBlockedOrError("SSL certificate verification failed. Open directly in your system browser.")
                                }
                            }
                        }
                    },
                    update = { webView ->
                        if (lastLoadedToken != loadToken) {
                            lastLoadedToken = loadToken
                            webProgress = 0
                            webView.loadUrl(activeUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (status == TestSessionStatus.PAUSING_BETWEEN) {
                // Inter-run cooldown overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(MidnightNavy.copy(alpha = 0.9f), MidnightDeep)
                            )
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AmberWarning.copy(alpha = 0.16f))
                            .border(1.dp, AmberWarning.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HourglassTop,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Pause Between Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${phaseRemainingSec}s before next test run",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = AmberWarning
                    )
                }
            } else if (blockedReason != null) {
                // Embedded preview blocked / error fallback with Open Website button
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(CoralDanger.copy(alpha = 0.14f), MidnightDeep)
                            )
                        )
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(CoralDanger.copy(alpha = 0.16f))
                            .border(1.dp, CoralDanger.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = CoralDanger,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.blocked_preview_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = blockedReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    GradientPrimaryButton(
                        text = stringResource(R.string.btn_open_website),
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        onClick = { onOpenExternal(activeUrl ?: fallbackUrl) },
                        testTag = "open_website_blocked_button",
                        modifier = Modifier.fillMaxWidth(0.75f)
                    )
                }
            } else {
                // Idle / Completed / Stopped empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    GlassSurfaceElevated.copy(alpha = 0.65f),
                                    MidnightDeep
                                )
                            )
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val accent = when (status) {
                        TestSessionStatus.COMPLETED -> EmeraldSuccess
                        TestSessionStatus.STOPPED -> AmberWarning
                        else -> ElectricCyan
                    }
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.14f))
                            .border(1.dp, accent.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = when (status) {
                            TestSessionStatus.COMPLETED -> "All Test Runs Completed"
                            TestSessionStatus.STOPPED -> "Testing Stopped"
                            else -> stringResource(R.string.empty_preview_title)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.empty_preview_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Top loading indicator inside viewport
            androidx.compose.animation.AnimatedVisibility(
                visible = isPreviewLoading && activeUrl != null && blockedReason == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                LinearProgressIndicator(
                    progress = { (webProgress / 100f).coerceIn(0.08f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = ElectricCyan,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.AutoScrollDirection
import com.example.model.ScrollIndicatorState
import com.example.model.ScrollSpeed
import com.example.model.TestSessionStatus
import com.example.model.WebCommandEvent
import com.example.model.WebCommandType
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassStrokeSubtle
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MidnightDeep
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    scrollDirection: AutoScrollDirection,
    scrollSpeed: ScrollSpeed,
    canGoBack: Boolean,
    pendingCommand: WebCommandEvent?,
    onConsumeCommand: (Long) -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onReloadClick: () -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: (Boolean) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit,
    onScrollStatusChanged: (ScrollIndicatorState) -> Unit,
    onBlockedOrError: (String) -> Unit,
    onOpenExternal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var webProgress by remember { mutableIntStateOf(0) }
    var lastLoadedToken by remember { mutableIntStateOf(-1) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Smooth independent Auto Scroll Down / Auto Scroll Up inside webpage container
    LaunchedEffect(
        scrollDirection,
        scrollSpeed,
        activeUrl,
        blockedReason,
        status,
        webViewRef
    ) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (scrollDirection == AutoScrollDirection.NONE ||
            activeUrl == null ||
            blockedReason != null ||
            status == TestSessionStatus.PAUSING_BETWEEN
        ) {
            return@LaunchedEffect
        }

        val delta = if (scrollDirection == AutoScrollDirection.DOWN) {
            scrollSpeed.pxPerTick
        } else {
            -scrollSpeed.pxPerTick
        }

        while (isActive) {
            val js = buildPreviewScrollScript(delta)
            wv.evaluateJavascript(js) { rawResult ->
                val result = rawResult?.trim('"') ?: ""
                when (result) {
                    "AT_TOP" -> onScrollStatusChanged(ScrollIndicatorState.AT_TOP)
                    "AT_BOTTOM" -> onScrollStatusChanged(ScrollIndicatorState.AT_BOTTOM)
                    "SCROLLING_UP" -> onScrollStatusChanged(ScrollIndicatorState.SCROLLING_UP)
                    "SCROLLING_DOWN" -> onScrollStatusChanged(ScrollIndicatorState.SCROLLING_DOWN)
                    "RESTRICTED" -> onScrollStatusChanged(ScrollIndicatorState.RESTRICTED)
                }
            }
            delay(20)
        }
    }

    LaunchedEffect(pendingCommand, webViewRef) {
        val cmd = pendingCommand ?: return@LaunchedEffect
        val wv = webViewRef
        when (cmd.type) {
            WebCommandType.BACK -> {
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                    onCanGoBackChanged(wv.canGoBack())
                }
            }
            WebCommandType.HOME -> {
                val target = cmd.targetUrl
                if (wv != null && !target.isNullOrBlank()) {
                    wv.loadUrl(target)
                }
            }
            WebCommandType.RELOAD -> {
                wv?.reload()
            }
            WebCommandType.CLEAR_SESSION -> {
                if (wv != null) {
                    wv.stopLoading()
                    wv.clearHistory()
                    wv.clearCache(false)
                    wv.loadUrl("about:blank")
                }
                lastLoadedToken = -1
            }
        }
        onConsumeCommand(cmd.id)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                clearHistory()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef = null
        }
    }

    LiquidGlassPanel(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToolbarIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back),
                    enabled = canGoBack && activeUrl != null && blockedReason == null,
                    onClick = onBackClick,
                    testTag = "preview_back_button"
                )
                ToolbarIconButton(
                    icon = Icons.Rounded.Home,
                    contentDescription = stringResource(R.string.nav_home),
                    enabled = (activeUrl ?: fallbackUrl).startsWith("http", ignoreCase = true),
                    onClick = onHomeClick,
                    testTag = "preview_home_button"
                )
                ToolbarIconButton(
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.nav_reload),
                    enabled = activeUrl != null && blockedReason == null,
                    onClick = onReloadClick,
                    testTag = "preview_reload_button"
                )
            }

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
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
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
                .height(340.dp)
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
                                domStorageEnabled = false
                                saveFormData = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
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
                                    onPageFinished(view?.canGoBack() == true)
                                }

                                override fun doUpdateVisitedHistory(
                                    view: WebView?,
                                    url: String?,
                                    isReload: Boolean
                                ) {
                                    onCanGoBackChanged(view?.canGoBack() == true)
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
                                    handler?.cancel()
                                    onBlockedOrError("SSL certificate verification failed. Open directly in your browser.")
                                }
                            }
                            webViewRef = this
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                        if (lastLoadedToken != loadToken) {
                            lastLoadedToken = loadToken
                            webProgress = 0
                            webView.loadUrl(activeUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (status == TestSessionStatus.PAUSING_BETWEEN) {
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
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(AmberWarning.copy(alpha = 0.16f))
                            .border(1.dp, AmberWarning.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HourglassTop,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Pause Interval",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${phaseRemainingSec}s before next test",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = AmberWarning
                    )
                }
            } else if (blockedReason != null) {
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
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(CoralDanger.copy(alpha = 0.16f))
                            .border(1.dp, CoralDanger.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = CoralDanger,
                            modifier = Modifier.size(24.dp)
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
                    Spacer(modifier = Modifier.height(14.dp))
                    GradientPrimaryButton(
                        text = stringResource(R.string.btn_open_website),
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        onClick = { onOpenExternal(activeUrl ?: fallbackUrl) },
                        testTag = "open_website_blocked_button",
                        modifier = Modifier.fillMaxWidth(0.75f)
                    )
                }
            } else {
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
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.14f))
                            .border(1.dp, accent.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when (status) {
                            TestSessionStatus.COMPLETED -> "Testing Completed"
                            TestSessionStatus.STOPPED -> "Session Stopped & Cleared"
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

private fun buildPreviewScrollScript(delta: Int): String {
    return """
        (function() {
          try {
            var delta = $delta;
            function findScrollable(doc) {
              if (!doc) return null;
              var root = doc.scrollingElement || doc.documentElement || doc.body;
              var candidates = [];
              if (root && root.scrollHeight > root.clientHeight + 2) {
                candidates.push(root);
              }
              var all = doc.querySelectorAll('*');
              for (var i = 0; i < all.length && i < 600; i++) {
                var el = all[i];
                if (el === root) continue;
                var style = (doc.defaultView || window).getComputedStyle(el);
                if (!style) continue;
                var oy = style.overflowY;
                if ((oy === 'auto' || oy === 'scroll') && el.scrollHeight > el.clientHeight + 4) {
                  candidates.push(el);
                }
              }
              if (delta < 0) {
                for (var j = 0; j < candidates.length; j++) {
                  if (candidates[j].scrollTop > 0) return candidates[j];
                }
              } else {
                for (var k = 0; k < candidates.length; k++) {
                  var c = candidates[k];
                  if (c.scrollTop + c.clientHeight < c.scrollHeight - 1) return c;
                }
              }
              return candidates.length > 0 ? candidates[0] : root;
            }
            var target = findScrollable(document);
            if (!target) return 'NO_TARGET';
            if (delta < 0) {
              if (target.scrollTop <= 0) return 'AT_TOP';
              target.scrollTop = Math.max(0, target.scrollTop + delta);
              return target.scrollTop <= 0 ? 'AT_TOP' : 'SCROLLING_UP';
            } else {
              var maxScroll = target.scrollHeight - target.clientHeight;
              if (maxScroll <= 0 || target.scrollTop >= maxScroll - 1) return 'AT_BOTTOM';
              target.scrollTop = Math.min(maxScroll, target.scrollTop + delta);
              return target.scrollTop >= maxScroll - 1 ? 'AT_BOTTOM' : 'SCROLLING_DOWN';
            }
          } catch (e) {
            return 'RESTRICTED';
          }
        })();
    """.trimIndent()
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) GlassSurfaceElevated.copy(alpha = 0.7f) else MidnightDeep.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, if (enabled) GlassStrokeSubtle else Color.Transparent),
        modifier = Modifier
            .size(38.dp)
            .testTag(testTag)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) TextPrimary else TextMuted.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

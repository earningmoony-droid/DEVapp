package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.model.ClickInteractionMode
import com.example.model.ClickTargetItem
import com.example.model.DEMO_ENV_URL
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
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
    isScrollPaused: Boolean,
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
    onScrollFeedback: (ScrollIndicatorState, String?) -> Unit,
    onBlockedOrError: (String, Boolean) -> Unit,
    onOpenExternal: (String) -> Unit,
    onTargetsScanned: (List<ClickTargetItem>, String?) -> Unit,
    onClickExecuted: (String, String?) -> Unit,
    onDirectPreviewClick: (String) -> Unit,
    onRandomTapExecuted: (String, String?, String?) -> Unit,
    onRandomScrollFinished: (Boolean, String?) -> Unit,
    onDiagCommandResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var webProgress by remember { mutableIntStateOf(0) }
    var lastLoadedToken by remember { mutableIntStateOf(-1) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val commandScope = rememberCoroutineScope()
    var randomScrollAnimJob by remember { mutableStateOf<Job?>(null) }

    // Smooth independent Scroll Down / Scroll Up / Random Scroll with Pause, Position & Stuck detection
    LaunchedEffect(
        scrollDirection,
        isScrollPaused,
        scrollSpeed,
        activeUrl,
        blockedReason,
        status,
        webViewRef
    ) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (scrollDirection == AutoScrollDirection.NONE ||
            isScrollPaused ||
            activeUrl == null ||
            blockedReason != null ||
            status == TestSessionStatus.PAUSING_BETWEEN
        ) {
            return@LaunchedEffect
        }

        var randomGoingDown = true
        var nextSwitchAtMs = System.currentTimeMillis() + kotlin.random.Random.nextLong(1200L, 3000L)
        var stuckFrames = 0
        var stuckExpectedAccum = 0.0

        while (isActive) {
            val now = System.currentTimeMillis()
            if (scrollDirection == AutoScrollDirection.RANDOM && now >= nextSwitchAtMs) {
                randomGoingDown = !randomGoingDown
                nextSwitchAtMs = now + kotlin.random.Random.nextLong(1100L, 3200L)
                stuckFrames = 0
                stuckExpectedAccum = 0.0
            }

            val goingDown = when (scrollDirection) {
                AutoScrollDirection.DOWN -> true
                AutoScrollDirection.UP -> false
                AutoScrollDirection.RANDOM -> randomGoingDown
                AutoScrollDirection.NONE -> true
            }
            val delta = if (goingDown) scrollSpeed.pxPerTick else -scrollSpeed.pxPerTick

            val rawResult = withTimeoutOrNull(450L) {
                suspendCancellableCoroutine<String?> { cont ->
                    wv.evaluateJavascript(buildPreviewScrollScript(delta)) { value ->
                        if (cont.isActive) cont.resume(value)
                    }
                }
            }

            val decoded = decodeJsString(rawResult)
            val parsedObj = runCatching { org.json.JSONObject(decoded) }.getOrNull()
            val resultState = parsedObj?.optString("state").orEmpty()
            val posText = parsedObj?.optString("pos")?.takeIf { it.isNotBlank() }
            val expectedMove = parsedObj?.optDouble("expectedMove", 0.0) ?: 0.0
            val actualMove = parsedObj?.optDouble("actualMove", 0.0) ?: 0.0

            if (resultState == "NOT_SCROLLABLE") {
                onScrollFeedback(ScrollIndicatorState.NOT_SCROLLABLE, posText ?: "0% • 0/0px")
                break
            }
            if (resultState == "RESTRICTED") {
                onScrollFeedback(ScrollIndicatorState.RESTRICTED, posText)
                break
            }

            if (expectedMove >= 1.0 && actualMove < 0.25 && resultState != "AT_TOP" && resultState != "AT_BOTTOM") {
                stuckFrames += 1
                stuckExpectedAccum += expectedMove
                if (stuckFrames >= 10 && stuckExpectedAccum >= 12.0) {
                    onScrollFeedback(ScrollIndicatorState.STUCK, posText)
                    break
                }
            } else {
                stuckFrames = 0
                stuckExpectedAccum = 0.0
            }

            if (scrollDirection == AutoScrollDirection.RANDOM) {
                when (resultState) {
                    "AT_TOP" -> {
                        randomGoingDown = true
                        nextSwitchAtMs = System.currentTimeMillis() + kotlin.random.Random.nextLong(1100L, 2800L)
                        stuckFrames = 0
                        stuckExpectedAccum = 0.0
                        onScrollFeedback(ScrollIndicatorState.RANDOM_DOWN, posText)
                    }
                    "AT_BOTTOM" -> {
                        randomGoingDown = false
                        nextSwitchAtMs = System.currentTimeMillis() + kotlin.random.Random.nextLong(1100L, 2800L)
                        stuckFrames = 0
                        stuckExpectedAccum = 0.0
                        onScrollFeedback(ScrollIndicatorState.RANDOM_UP, posText)
                    }
                    "SCROLLING_UP" -> onScrollFeedback(ScrollIndicatorState.RANDOM_UP, posText)
                    "SCROLLING_DOWN" -> onScrollFeedback(ScrollIndicatorState.RANDOM_DOWN, posText)
                }
            } else {
                when (resultState) {
                    "AT_TOP" -> {
                        onScrollFeedback(ScrollIndicatorState.AT_TOP, posText)
                        break
                    }
                    "AT_BOTTOM" -> {
                        onScrollFeedback(ScrollIndicatorState.AT_BOTTOM, posText)
                        break
                    }
                    "SCROLLING_UP" -> onScrollFeedback(ScrollIndicatorState.SCROLLING_UP, posText)
                    "SCROLLING_DOWN" -> onScrollFeedback(ScrollIndicatorState.SCROLLING_DOWN, posText)
                }
            }
            delay(18)
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
                    if (target == DEMO_ENV_URL) {
                        wv.loadDataWithBaseURL("https://demo.webvisittester.local/", DEMO_TEST_HTML, "text/html", "utf-8", null)
                    } else {
                        wv.loadUrl(target)
                    }
                }
            }
            WebCommandType.RELOAD -> {
                if (wv != null) {
                    if (cmd.targetUrl == DEMO_ENV_URL || activeUrl == DEMO_ENV_URL) {
                        wv.loadDataWithBaseURL("https://demo.webvisittester.local/", DEMO_TEST_HTML, "text/html", "utf-8", null)
                    } else {
                        wv.reload()
                    }
                }
            }
            WebCommandType.CLEAR_SESSION -> {
                randomScrollAnimJob?.cancel()
                randomScrollAnimJob = null
                if (wv != null) {
                    wv.stopLoading()
                    wv.clearHistory()
                    wv.clearCache(false)
                    wv.loadUrl("about:blank")
                }
                lastLoadedToken = -1
            }
            WebCommandType.SCAN_CLICK_TARGETS -> {
                if (wv == null) {
                    onTargetsScanned(emptyList(), "Preview WebView is not ready.")
                } else {
                    wv.evaluateJavascript(buildScanClickablesScript()) { raw ->
                        val parsed = parseScannedTargets(raw)
                        onTargetsScanned(parsed.first, parsed.second)
                    }
                }
            }
            WebCommandType.EXECUTE_SINGLE_CLICK -> {
                val selector = cmd.selector.orEmpty()
                if (wv == null || selector.isBlank()) {
                    onClickExecuted("NO_TARGET", "Preview or target selector unavailable.")
                } else {
                    wv.evaluateJavascript(buildSafeClickScript(selector, cmd.interactionMode)) { raw ->
                        val decoded = decodeJsString(raw)
                        val parts = decoded.split("::", limit = 2)
                        val code = parts.firstOrNull().orEmpty().ifBlank { "RESTRICTED" }
                        val detail = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                        onClickExecuted(code, detail)
                    }
                }
            }
            WebCommandType.EXECUTE_RANDOM_TAP -> {
                if (wv == null) {
                    onRandomTapExecuted("RESTRICTED", "Preview WebView is not ready.", null)
                } else {
                    wv.evaluateJavascript(buildRandomSafeTapScript(cmd.selector.orEmpty())) { raw ->
                        val decoded = decodeJsString(raw)
                        val obj = runCatching { org.json.JSONObject(decoded) }.getOrNull()
                        val st = obj?.optString("status").orEmpty().ifBlank { "RESTRICTED" }
                        val lbl = obj?.optString("label")?.takeIf { it.isNotBlank() }
                        val sel = obj?.optString("selector")?.takeIf { it.isNotBlank() }
                        onRandomTapExecuted(st, lbl, sel)
                    }
                }
            }
            WebCommandType.RANDOM_SCROLL_STEP -> {
                if (wv == null) {
                    onRandomScrollFinished(false, null)
                } else {
                    randomScrollAnimJob?.cancel()
                    val initialDir = if (cmd.selector == "up") "up" else "down"
                    randomScrollAnimJob = commandScope.launch {
                        var dir = initialDir
                        var movedAny = false
                        var latestPos: String? = null
                        for (step in 0 until 45) {
                            if (!isActive) break
                            val delta = if (dir == "down") 16 else -16
                            val raw = withTimeoutOrNull(400L) {
                                suspendCancellableCoroutine<String?> { cont ->
                                    wv.evaluateJavascript(buildPreviewScrollScript(delta)) { value ->
                                        if (cont.isActive) cont.resume(value)
                                    }
                                }
                            }
                            val decoded = decodeJsString(raw)
                            val parsed = runCatching { org.json.JSONObject(decoded) }.getOrNull()
                            val st = parsed?.optString("state").orEmpty()
                            val posText = parsed?.optString("pos")?.takeIf { it.isNotBlank() }
                            if (posText != null) latestPos = posText

                            if (st == "NOT_SCROLLABLE" || st == "RESTRICTED") {
                                break
                            }
                            if (st == "AT_BOTTOM") {
                                if (step == 0 && dir == "down") {
                                    dir = "up"
                                    continue
                                }
                                onScrollFeedback(ScrollIndicatorState.RANDOM_UP, posText)
                                break
                            }
                            if (st == "AT_TOP") {
                                if (step == 0 && dir == "up") {
                                    dir = "down"
                                    continue
                                }
                                onScrollFeedback(ScrollIndicatorState.RANDOM_DOWN, posText)
                                break
                            }
                            movedAny = true
                            onScrollFeedback(
                                if (dir == "down") ScrollIndicatorState.RANDOM_DOWN else ScrollIndicatorState.RANDOM_UP,
                                posText
                            )
                            delay(18)
                        }
                        onRandomScrollFinished(movedAny, latestPos)
                    }
                }
            }
            WebCommandType.DIAG_VERIFY_LISTENER -> {
                if (wv == null) {
                    onDiagCommandResult("""{"ok":false,"status":"FAILED","listenerText":"Not Ready","reason":"Preview WebView is not ready."}""")
                } else {
                    wv.evaluateJavascript(buildDiagVerifyListenerScript()) { raw ->
                        val decoded = decodeJsString(raw)
                        onDiagCommandResult(decoded.ifBlank { """{"ok":false,"status":"FAILED","listenerText":"Not Responding","reason":"No response from WebView DOM."}""" })
                    }
                }
            }
            WebCommandType.DIAG_TEST_BUTTON -> {
                if (wv == null) {
                    onDiagCommandResult("""{"ok":false,"status":"FAILED","clicked":false,"reason":"Preview WebView is not ready."}""")
                } else {
                    wv.evaluateJavascript(buildDiagTestButtonScript(cmd.interactionMode)) { raw ->
                        val decoded = decodeJsString(raw)
                        onDiagCommandResult(decoded.ifBlank { """{"ok":false,"status":"FAILED","clicked":false,"reason":"Button probe returned empty result."}""" })
                    }
                }
            }
            WebCommandType.DIAG_TEST_LINK -> {
                if (wv == null) {
                    onDiagCommandResult("""{"ok":false,"status":"FAILED","clicked":false,"reason":"Preview WebView is not ready."}""")
                } else {
                    wv.evaluateJavascript(buildDiagTestLinkScript()) { raw ->
                        val decoded = decodeJsString(raw)
                        onDiagCommandResult(decoded.ifBlank { """{"ok":false,"status":"FAILED","clicked":false,"reason":"Link navigation probe returned empty result."}""" })
                    }
                }
            }
        }
        onConsumeCommand(cmd.id)
    }

    DisposableEffect(Unit) {
        onDispose {
            randomScrollAnimJob?.cancel()
            randomScrollAnimJob = null
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
                    enabled = activeUrl == DEMO_ENV_URL || (activeUrl ?: fallbackUrl).startsWith("http", ignoreCase = true),
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
                            isFocusable = true
                            isFocusableInTouchMode = true
                            isClickable = true
                            setOnTouchListener { v, event ->
                                when (event.actionMasked) {
                                    MotionEvent.ACTION_DOWN,
                                    MotionEvent.ACTION_MOVE -> {
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    }
                                    MotionEvent.ACTION_UP,
                                    MotionEvent.ACTION_CANCEL -> {
                                        v.parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                                false
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = false
                                saveFormData = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onDirectClick(label: String?) {
                                        mainHandler.post {
                                            onDirectPreviewClick(label.orEmpty())
                                        }
                                    }
                                },
                                "WebVisitBridge"
                            )
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
                                    if (!url.isNullOrBlank() && url != "about:blank") {
                                        onPageStarted()
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (url.isNullOrBlank() || url == "about:blank") return
                                    view?.evaluateJavascript(buildDirectClickObserverScript(), null)
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
                                        onBlockedOrError(desc, true)
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
                                            onBlockedOrError(msg, false)
                                        }
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    handler?.cancel()
                                    onBlockedOrError("SSL certificate verification failed. Open directly in your browser.", true)
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
                            if (activeUrl == DEMO_ENV_URL) {
                                webView.loadDataWithBaseURL(
                                    "https://demo.webvisittester.local/",
                                    DEMO_TEST_HTML,
                                    "text/html",
                                    "utf-8",
                                    null
                                )
                            } else {
                                webView.loadUrl(activeUrl)
                            }
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
                            TestSessionStatus.FAILED -> "Testing Failed"
                            TestSessionStatus.ERROR -> "Network / Connection Error"
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
            function findScrollable(doc, goingUp) {
              if (!doc) return null;
              var win = doc.defaultView || window;
              var root = doc.scrollingElement || doc.documentElement || doc.body;
              var docHeight = Math.max(
                root ? root.scrollHeight : 0,
                doc.documentElement ? doc.documentElement.scrollHeight : 0,
                doc.body ? doc.body.scrollHeight : 0
              );
              var viewportH = win.innerHeight || (root ? root.clientHeight : 0);
              var winMax = Math.max(0, docHeight - viewportH);
              var candidates = [];

              if (winMax > 2) {
                candidates.push({
                  maxScroll: winMax,
                  getTop: function() {
                    return Math.max(0, win.scrollY || win.pageYOffset || (root ? root.scrollTop : 0));
                  },
                  setTop: function(nextTop) {
                    var targetEl = doc.scrollingElement || doc.documentElement || doc.body;
                    var prevBeh = targetEl && targetEl.style ? targetEl.style.scrollBehavior : '';
                    if (targetEl && targetEl.style && prevBeh !== 'auto') {
                      targetEl.style.scrollBehavior = 'auto';
                    }
                    try {
                      win.scrollTo({ top: nextTop, left: win.scrollX || 0, behavior: 'auto' });
                    } catch (e1) {
                      win.scrollTo(win.scrollX || 0, nextTop);
                    }
                    if (targetEl && Math.abs((win.scrollY || targetEl.scrollTop) - nextTop) > 2 && targetEl.scrollTop !== nextTop) {
                      targetEl.scrollTop = nextTop;
                    }
                    if (targetEl && targetEl.style) {
                      targetEl.style.scrollBehavior = prevBeh;
                    }
                  }
                });
              }

              var all = doc.querySelectorAll('*');
              for (var i = 0; i < all.length && i < 700; i++) {
                var el = all[i];
                if (el === root || el === doc.documentElement || el === doc.body) continue;
                var style = win.getComputedStyle(el);
                if (!style) continue;
                if (style.display === 'none' || style.visibility === 'hidden') continue;
                var oy = style.overflowY;
                if ((oy === 'auto' || oy === 'scroll' || oy === 'overlay') && el.scrollHeight > el.clientHeight + 4) {
                  var rect = el.getBoundingClientRect();
                  if (rect.width < 20 || rect.height < 20) continue;
                  (function(node, maxVal) {
                    candidates.push({
                      maxScroll: maxVal,
                      getTop: function() { return node.scrollTop; },
                      setTop: function(nextTop) {
                        var prevBeh = node.style.scrollBehavior;
                        if (prevBeh !== 'auto') node.style.scrollBehavior = 'auto';
                        if (node.scrollTo) {
                          try {
                            node.scrollTo({ top: nextTop, left: node.scrollLeft, behavior: 'auto' });
                          } catch (e2) {
                            node.scrollTop = nextTop;
                          }
                        } else {
                          node.scrollTop = nextTop;
                        }
                        node.style.scrollBehavior = prevBeh;
                      }
                    });
                  })(el, el.scrollHeight - el.clientHeight);
                }
              }

              if (candidates.length === 0) return null;
              if (goingUp) {
                for (var j = 0; j < candidates.length; j++) {
                  if (candidates[j].getTop() > 1) return candidates[j];
                }
              } else {
                for (var k = 0; k < candidates.length; k++) {
                  if (candidates[k].getTop() < candidates[k].maxScroll - 1) return candidates[k];
                }
              }
              return candidates[0];
            }

            var goingUp = delta < 0;
            var target = findScrollable(document, goingUp);
            if (!target || target.maxScroll <= 2) {
              return JSON.stringify({ state: 'NOT_SCROLLABLE', pos: '0% • 0/0px', expectedMove: 0, actualMove: 0 });
            }

            var maxScroll = target.maxScroll;
            var beforeTop = target.getTop();
            function formatPos(posVal) {
              var safePos = Math.max(0, Math.min(maxScroll, Math.round(posVal)));
              var safeMax = Math.max(0, Math.round(maxScroll));
              var pct = safeMax > 0 ? Math.round((safePos / safeMax) * 100) : 0;
              return pct + '% • ' + safePos + '/' + safeMax + 'px';
            }

            if (goingUp) {
              if (beforeTop <= 1) {
                return JSON.stringify({ state: 'AT_TOP', pos: formatPos(0), expectedMove: 0, actualMove: 0 });
              }
              var desiredUp = Math.max(0, beforeTop + delta);
              target.setTop(desiredUp);
              var afterUp = target.getTop();
              var nextStateUp = afterUp <= 1 ? 'AT_TOP' : 'SCROLLING_UP';
              return JSON.stringify({
                state: nextStateUp,
                pos: formatPos(afterUp),
                expectedMove: Math.abs(beforeTop - desiredUp),
                actualMove: Math.abs(afterUp - beforeTop)
              });
            } else {
              if (beforeTop >= maxScroll - 1) {
                return JSON.stringify({ state: 'AT_BOTTOM', pos: formatPos(maxScroll), expectedMove: 0, actualMove: 0 });
              }
              var desiredDown = Math.min(maxScroll, beforeTop + delta);
              target.setTop(desiredDown);
              var afterDown = target.getTop();
              var nextStateDown = afterDown >= maxScroll - 1 ? 'AT_BOTTOM' : 'SCROLLING_DOWN';
              return JSON.stringify({
                state: nextStateDown,
                pos: formatPos(afterDown),
                expectedMove: Math.abs(desiredDown - beforeTop),
                actualMove: Math.abs(afterDown - beforeTop)
              });
            }
          } catch (e) {
            return JSON.stringify({ state: 'RESTRICTED', pos: '0% • 0/0px', expectedMove: 0, actualMove: 0 });
          }
        })();
    """.trimIndent()
}

private const val AD_GUARD_JS = """
  function isProhibitedAdElement(el) {
    if (!el) return true;
    var adPattern = /(adsbygoogle|doubleclick|googlesyndication|adservice|adsystem|adserver|adnxs|taboola|outbrain|mgid|sponsored|advertisement|ad-slot|ad_slot|ad-banner|ad_banner|google_ads|data-ad)/i;
    var cur = el;
    var depth = 0;
    while (cur && depth < 6) {
      if (cur.tagName && cur.tagName.toLowerCase() === 'ins') return true;
      var attrs = [
        cur.id || '',
        typeof cur.className === 'string' ? cur.className : '',
        cur.getAttribute ? (cur.getAttribute('href') || '') : '',
        cur.getAttribute ? (cur.getAttribute('src') || '') : '',
        cur.getAttribute ? (cur.getAttribute('aria-label') || '') : '',
        cur.getAttribute ? (cur.getAttribute('data-ad-client') || '') : '',
        cur.getAttribute ? (cur.getAttribute('data-ad-slot') || '') : ''
      ].join(' ');
      if (adPattern.test(attrs)) return true;
      cur = cur.parentElement;
      depth++;
    }
    return false;
  }
  function isInteractable(el) {
    if (!el) return false;
    if (el.disabled || (el.getAttribute && el.getAttribute('aria-disabled') === 'true')) return false;
    var style = window.getComputedStyle(el);
    if (!style || style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity) === 0 || style.pointerEvents === 'none') {
      return false;
    }
    var rect = el.getBoundingClientRect();
    return rect.width >= 2 && rect.height >= 2;
  }
"""

private const val TAP_RIPPLE_JS = """
  function spawnTapRipple(clientX, clientY, accentHex) {
    try {
      var root = document.body || document.documentElement;
      if (!root) return;
      var layer = document.getElementById('__wv_ripple_layer');
      if (!layer) {
        layer = document.createElement('div');
        layer.id = '__wv_ripple_layer';
        layer.setAttribute('aria-hidden', 'true');
        layer.style.cssText = 'position:fixed;inset:0;pointer-events:none;overflow:hidden;z-index:2147483647;';
        root.appendChild(layer);
      }
      var ripple = document.createElement('span');
      ripple.setAttribute('aria-hidden', 'true');
      var x = (typeof clientX === 'number' && isFinite(clientX)) ? clientX : ((window.innerWidth || 300) / 2);
      var y = (typeof clientY === 'number' && isFinite(clientY)) ? clientY : ((window.innerHeight || 300) / 2);
      var rgb = accentHex === '#34D399'
        ? '52, 211, 153'
        : (accentHex === '#A855F7' ? '168, 85, 247' : '56, 189, 248');
      ripple.style.cssText =
        'position:fixed;left:' + x + 'px;top:' + y + 'px;width:46px;height:46px;border-radius:9999px;' +
        'pointer-events:none;box-sizing:border-box;' +
        'background:radial-gradient(circle, rgba(' + rgb + ', 0.34) 0%, rgba(' + rgb + ', 0.12) 55%, rgba(' + rgb + ', 0) 75%);' +
        'border:1.5px solid rgba(' + rgb + ', 0.72);' +
        'box-shadow:0 0 12px rgba(' + rgb + ', 0.28);' +
        'transform:translate(-50%, -50%) scale(0.22);opacity:0.9;';
      layer.appendChild(ripple);
      var cleanup = function() { try { if (ripple.parentNode) ripple.parentNode.removeChild(ripple); } catch (_) {} };
      if (typeof ripple.animate === 'function') {
        var anim = ripple.animate(
          [
            { transform: 'translate(-50%, -50%) scale(0.22)', opacity: 0.9 },
            { transform: 'translate(-50%, -50%) scale(1.3)', opacity: 0 }
          ],
          { duration: 480, easing: 'cubic-bezier(0.16, 1, 0.3, 1)', fill: 'forwards' }
        );
        anim.onfinish = cleanup;
        setTimeout(cleanup, 560);
      } else {
        ripple.style.transition = 'transform 0.46s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.46s ease-out';
        requestAnimationFrame(function() {
          ripple.style.transform = 'translate(-50%, -50%) scale(1.3)';
          ripple.style.opacity = '0';
        });
        setTimeout(cleanup, 520);
      }
    } catch (_) {}
  }
"""

private fun buildDirectClickObserverScript(): String {
    return """
        (function() {
          try {
            if (window.__wvClickAbort) {
              try { window.__wvClickAbort.abort(); } catch (e0) {}
              window.__wvClickAbort = null;
            }
            var ctrl = (typeof AbortController === 'function') ? new AbortController() : null;
            if (ctrl) {
              window.__wvClickAbort = ctrl;
            } else if (window.__wvDirectClickBound) {
              window.__wvClickListenerCount = 1;
              return;
            }
            window.__wvDirectClickBound = true;
            window.__wvClickListenerCount = 1;
            $AD_GUARD_JS
            $TAP_RIPPLE_JS
            var lastEl = null;
            var lastAt = 0;
            var onDirectClickEvent = function(ev) {
              if (!ev.isTrusted) return;
              var raw = ev.target && ev.target.closest
                ? ev.target.closest('button, a[href], [role="button"], input[type="button"], input[type="submit"], [onclick]')
                : null;
              if (!raw) return;
              var now = Date.now();
              if (raw === lastEl && now - lastAt < 280) {
                ev.preventDefault();
                ev.stopImmediatePropagation();
                return;
              }
              lastEl = raw;
              lastAt = now;
              if (isProhibitedAdElement(raw)) return;
              var hasCoord = (typeof ev.clientX === 'number' && typeof ev.clientY === 'number') && (ev.clientX !== 0 || ev.clientY !== 0);
              var tx = ev.clientX;
              var ty = ev.clientY;
              if (!hasCoord && raw.getBoundingClientRect) {
                var rr = raw.getBoundingClientRect();
                tx = rr.left + rr.width / 2;
                ty = rr.top + rr.height / 2;
              }
              spawnTapRipple(tx, ty, '#38BDF8');
              var label = (raw.innerText || raw.value || raw.getAttribute('aria-label') || raw.tagName)
                .replace(/\s+/g, ' ')
                .trim()
                .slice(0, 24);
              if (window.WebVisitBridge && typeof window.WebVisitBridge.onDirectClick === 'function') {
                window.WebVisitBridge.onDirectClick(label || raw.tagName.toLowerCase());
              }
            };
            document.addEventListener('click', onDirectClickEvent, ctrl ? { capture: true, signal: ctrl.signal } : true);
          } catch (e) {}
        })();
    """.trimIndent()
}

private fun buildDiagVerifyListenerScript(): String {
    val observer = buildDirectClickObserverScript()
    return """
        (function() {
          try {
            $observer
            var root = document.body || document.documentElement;
            if (!root) {
              return JSON.stringify({ ok: false, status: 'FAILED', listenerText: 'Not Responding', reason: 'Document body not ready.' });
            }
            var probeReceived = false;
            var probeEl = document.createElement('button');
            probeEl.type = 'button';
            probeEl.setAttribute('aria-hidden', 'true');
            probeEl.style.cssText = 'position:fixed;left:-9999px;top:-9999px;width:1px;height:1px;opacity:0;pointer-events:none;';
            probeEl.addEventListener('click', function() { probeReceived = true; }, { once: true });
            root.appendChild(probeEl);
            try {
              probeEl.click();
            } finally {
              try { if (probeEl.parentNode) probeEl.parentNode.removeChild(probeEl); } catch (_) {}
            }
            var count = window.__wvClickListenerCount || (window.__wvDirectClickBound ? 1 : 0);
            if (probeReceived && count === 1) {
              return JSON.stringify({ ok: true, status: 'OK', listenerText: 'Active (0 Duplicates)', reason: '' });
            }
            return JSON.stringify({ ok: false, status: 'FAILED', listenerText: 'Not Responding', reason: 'DOM click probe failed to fire.' });
          } catch (e) {
            return JSON.stringify({ ok: false, status: 'BLOCKED', listenerText: 'Blocked', reason: (e && e.message) ? e.message : 'Listener verification blocked.' });
          }
        })();
    """.trimIndent()
}

private fun buildDiagTestButtonScript(mode: ClickInteractionMode): String {
    val isTap = mode == ClickInteractionMode.TAP
    return """
        (function() {
          try {
            $AD_GUARD_JS
            $TAP_RIPPLE_JS
            var preferred = document.getElementById('demoOverviewBtn');
            var btnEl = (preferred && isInteractable(preferred) && !isProhibitedAdElement(preferred)) ? preferred : null;
            if (!btnEl) {
              var btns = document.querySelectorAll('button, input[type="button"], input[type="submit"], [role="button"]');
              for (var i = 0; i < btns.length; i++) {
                if (isInteractable(btns[i]) && !isProhibitedAdElement(btns[i])) {
                  btnEl = btns[i];
                  break;
                }
              }
            }
            if (!btnEl) {
              return JSON.stringify({ ok: false, status: 'FAILED', clicked: false, label: '', cellText: 'No Safe Button', reason: 'No safe non-ad button found on preview page.' });
            }
            var domClickFired = false;
            var onCheck = function(ev) {
              if (ev.target === btnEl || btnEl.contains(ev.target)) {
                domClickFired = true;
              }
            };
            document.addEventListener('click', onCheck, { capture: true, once: true });
            if (btnEl.scrollIntoView) {
              btnEl.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'auto' });
            }
            var rect = btnEl.getBoundingClientRect();
            var cx = rect.left + rect.width / 2;
            var cy = rect.top + rect.height / 2;
            spawnTapRipple(cx, cy, $isTap ? '#A855F7' : '#38BDF8');
            var prevOutline = btnEl.style.outline;
            btnEl.style.outline = '2px solid #38BDF8';
            setTimeout(function() { try { btnEl.style.outline = prevOutline; } catch (_) {} }, 320);
            if (btnEl.focus) { try { btnEl.focus({ preventScroll: true }); } catch (_) {} }
            btnEl.click();
            document.removeEventListener('click', onCheck, true);

            var label = (btnEl.innerText || btnEl.value || btnEl.getAttribute('aria-label') || 'Test Button')
              .replace(/\s+/g, ' ').trim().slice(0, 22);
            if (domClickFired) {
              return JSON.stringify({ ok: true, status: 'OK', clicked: true, label: label, cellText: 'Verified (' + label + ')', reason: '' });
            }
            return JSON.stringify({ ok: false, status: 'FAILED', clicked: false, label: label, cellText: 'Not Received', reason: 'Test button "' + label + '" did not emit a DOM click event.' });
          } catch (e) {
            return JSON.stringify({ ok: false, status: 'BLOCKED', clicked: false, label: '', cellText: 'Blocked', reason: (e && e.message) ? e.message : 'Button test blocked.' });
          }
        })();
    """.trimIndent()
}

private fun buildDiagTestLinkScript(): String {
    return """
        (function() {
          try {
            $AD_GUARD_JS
            $TAP_RIPPLE_JS
            var curOrigin = (window.location && window.location.origin && window.location.origin !== 'null') ? window.location.origin : '';
            var links = document.querySelectorAll('a[href]');
            var linkEl = null;
            for (var i = 0; i < links.length; i++) {
              var a = links[i];
              if (!isInteractable(a) || isProhibitedAdElement(a)) continue;
              var hrefAttr = a.getAttribute('href') || '';
              if (/^javascript:/i.test(hrefAttr)) continue;
              if (/^https?:\/\//i.test(hrefAttr) && curOrigin) {
                try {
                  var u = new URL(hrefAttr, window.location.href);
                  if (u.origin !== curOrigin) continue;
                } catch (eUrl) { continue; }
              }
              linkEl = a;
              break;
            }
            if (!linkEl) {
              return JSON.stringify({ ok: false, status: 'FAILED', clicked: false, label: '', cellText: 'No Safe Link', reason: 'No safe same-origin test link found on preview page.' });
            }
            var beforeHref = window.location ? window.location.href : '';
            var beforeHash = window.location ? window.location.hash : '';
            var hrefVal = linkEl.getAttribute('href') || '';
            var linkClickFired = false;
            var onCheck = function(ev) {
              if (ev.target === linkEl || linkEl.contains(ev.target)) {
                linkClickFired = true;
              }
            };
            document.addEventListener('click', onCheck, { capture: true, once: true });
            if (linkEl.scrollIntoView) {
              linkEl.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'auto' });
            }
            var rect = linkEl.getBoundingClientRect();
            var cx = rect.left + rect.width / 2;
            var cy = rect.top + rect.height / 2;
            spawnTapRipple(cx, cy, '#A855F7');
            var prevOutline = linkEl.style.outline;
            linkEl.style.outline = '2px solid #A855F7';
            setTimeout(function() { try { linkEl.style.outline = prevOutline; } catch (_) {} }, 320);
            if (linkEl.focus) { try { linkEl.focus({ preventScroll: true }); } catch (_) {} }
            linkEl.click();
            document.removeEventListener('click', onCheck, true);

            var afterHref = window.location ? window.location.href : '';
            var afterHash = window.location ? window.location.hash : '';
            var navigated = (afterHref !== beforeHref) ||
              (afterHash !== beforeHash) ||
              (hrefVal.indexOf('#') === 0 && afterHash === hrefVal);
            var label = (linkEl.innerText || hrefVal || 'Test Link').replace(/\s+/g, ' ').trim().slice(0, 22);

            if (linkClickFired && navigated) {
              return JSON.stringify({
                ok: true,
                status: 'OK',
                clicked: true,
                navigated: true,
                label: label,
                cellText: 'Verified (' + (afterHash || label) + ')',
                reason: ''
              });
            }
            if (linkClickFired) {
              return JSON.stringify({
                ok: false,
                status: 'FAILED',
                clicked: true,
                navigated: false,
                label: label,
                cellText: 'Click Only (No Nav)',
                reason: 'Link "' + label + '" emitted click but target navigation did not change URL/hash.'
              });
            }
            return JSON.stringify({
              ok: false,
              status: 'FAILED',
              clicked: false,
              navigated: false,
              label: label,
              cellText: 'Not Received',
              reason: 'Test link "' + label + '" did not emit click or navigate.'
            });
          } catch (e) {
            return JSON.stringify({ ok: false, status: 'BLOCKED', clicked: false, label: '', cellText: 'Blocked', reason: (e && e.message) ? e.message : 'Link test blocked.' });
          }
        })();
    """.trimIndent()
}

private fun buildScanClickablesScript(): String {
    return """
        (function() {
          try {
            $AD_GUARD_JS
            function escapeCssId(id) {
              return (window.CSS && CSS.escape) ? CSS.escape(id) : id.replace(/([^\w-])/g, '\\$1');
            }
            function buildSelector(el) {
              if (el.id && /^[A-Za-z_][A-Za-z0-9_-]*$/.test(el.id)) {
                var idSel = '#' + escapeCssId(el.id);
                try { if (document.querySelector(idSel) === el) return idSel; } catch (e0) {}
              }
              var testId = el.getAttribute('data-testid');
              if (testId) {
                var tidSel = el.tagName.toLowerCase() + '[data-testid="' + testId.replace(/"/g, '\\"') + '"]';
                try { if (document.querySelector(tidSel) === el) return tidSel; } catch (e1) {}
              }
              var parts = [];
              var cur = el;
              while (cur && cur.nodeType === 1 && cur.tagName.toLowerCase() !== 'html') {
                var tag = cur.tagName.toLowerCase();
                if (cur.id && /^[A-Za-z_][A-Za-z0-9_-]*$/.test(cur.id)) {
                  parts.unshift('#' + escapeCssId(cur.id));
                  break;
                }
                var sib = cur;
                var idx = 1;
                while (sib.previousElementSibling) {
                  sib = sib.previousElementSibling;
                  if (sib.tagName === cur.tagName) idx++;
                }
                parts.unshift(tag + ':nth-of-type(' + idx + ')');
                cur = cur.parentElement;
              }
              var full = parts.join(' > ');
              try { if (full && document.querySelector(full) === el) return full; } catch (e2) {}
              return full;
            }
            var nodes = document.querySelectorAll('button, a[href], [role="button"], input[type="button"], input[type="submit"]');
            var results = [];
            var seen = {};
            for (var i = 0; i < nodes.length && results.length < 18; i++) {
              var el = nodes[i];
              if (!isInteractable(el)) continue;
              if (isProhibitedAdElement(el)) continue;
              var sel = buildSelector(el);
              if (!sel || seen[sel]) continue;
              seen[sel] = true;
              var txt = (el.innerText || el.value || el.getAttribute('aria-label') || el.tagName).replace(/\s+/g, ' ').trim();
              if (!txt) txt = el.tagName.toLowerCase();
              if (txt.length > 26) txt = txt.slice(0, 26) + '...';
              results.push({
                selector: sel,
                label: txt,
                tagType: el.tagName.toLowerCase()
              });
            }
            return JSON.stringify(results);
          } catch (e) {
            return 'ERROR:' + (e && e.message ? e.message : 'scan failed');
          }
        })();
    """.trimIndent()
}

private fun buildSafeClickScript(rawSelector: String, mode: ClickInteractionMode): String {
    val quotedSelector = org.json.JSONObject.quote(rawSelector)
    val isTap = mode == ClickInteractionMode.TAP
    return """
        (function() {
          try {
            $AD_GUARD_JS
            $TAP_RIPPLE_JS
            var now = Date.now();
            if (window.__wvLastActAt && now - window.__wvLastActAt < 280) {
              return 'SKIPPED::Debounced accidental double interaction.';
            }
            var sel = $quotedSelector;
            if (/(adsbygoogle|doubleclick|googlesyndication|adservice|adsystem|adserver|adnxs|taboola|outbrain|mgid|sponsored|advertisement|ad-slot|ad_slot|ad-banner|ad_banner|google_ads|data-ad)/i.test(sel)) {
              return 'BLOCKED_AD::Blocked ad-related selector.';
            }
            var el = document.querySelector(sel);
            if (!el) return 'NO_TARGET::Target element not found on current page.';
            if (isProhibitedAdElement(el)) {
              return 'BLOCKED_AD::Blocked ad or sponsored element.';
            }
            if (!isInteractable(el)) {
              return 'NO_TARGET::Target element is hidden, disabled, or non-interactive.';
            }
            window.__wvLastActAt = now;
            if (el.scrollIntoView) {
              el.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'auto' });
            }
            var rect = el.getBoundingClientRect();
            var cx = rect.left + rect.width / 2;
            var cy = rect.top + rect.height / 2;
            var isTap = $isTap;

            spawnTapRipple(cx, cy, isTap ? '#A855F7' : '#38BDF8');

            var prevOutline = el.style.outline;
            el.style.outline = isTap ? '2px solid #A855F7' : '2px solid #38BDF8';
            setTimeout(function() { try { el.style.outline = prevOutline; } catch (_) {} }, 320);

            if (isTap) {
              var touchHandled = false;
              if (typeof Touch === 'function' && typeof TouchEvent === 'function') {
                try {
                  var touchObj = new Touch({
                    identifier: now % 100000,
                    target: el,
                    clientX: cx,
                    clientY: cy,
                    screenX: cx,
                    screenY: cy,
                    pageX: cx + (window.scrollX || 0),
                    pageY: cy + (window.scrollY || 0),
                    radiusX: 4,
                    radiusY: 4,
                    force: 1
                  });
                  el.dispatchEvent(new TouchEvent('touchstart', {
                    bubbles: true, cancelable: true, view: window,
                    touches: [touchObj], targetTouches: [touchObj], changedTouches: [touchObj]
                  }));
                  var te = new TouchEvent('touchend', {
                    bubbles: true, cancelable: true, view: window,
                    touches: [], targetTouches: [], changedTouches: [touchObj]
                  });
                  touchHandled = !el.dispatchEvent(te);
                } catch (eTouch) {}
              }
              if (typeof PointerEvent === 'function') {
                el.dispatchEvent(new PointerEvent('pointerdown', {
                  bubbles: true, cancelable: true, view: window, pointerType: 'touch', isPrimary: true, clientX: cx, clientY: cy
                }));
                var puCanceled = !el.dispatchEvent(new PointerEvent('pointerup', {
                  bubbles: true, cancelable: true, view: window, pointerType: 'touch', isPrimary: true, clientX: cx, clientY: cy
                }));
                touchHandled = touchHandled || puCanceled;
              }
              if (!touchHandled) {
                if (el.focus) { try { el.focus({ preventScroll: true }); } catch (_) {} }
                el.click();
              }
            } else {
              if (typeof PointerEvent === 'function') {
                el.dispatchEvent(new PointerEvent('pointerdown', {
                  bubbles: true, cancelable: true, view: window, pointerType: 'mouse', isPrimary: true, clientX: cx, clientY: cy
                }));
              }
              el.dispatchEvent(new MouseEvent('mousedown', {
                bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy, button: 0
              }));
              if (typeof PointerEvent === 'function') {
                el.dispatchEvent(new PointerEvent('pointerup', {
                  bubbles: true, cancelable: true, view: window, pointerType: 'mouse', isPrimary: true, clientX: cx, clientY: cy
                }));
              }
              el.dispatchEvent(new MouseEvent('mouseup', {
                bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy, button: 0
              }));
              if (el.focus) { try { el.focus({ preventScroll: true }); } catch (_) {} }
              el.click();
            }

            var label = (el.innerText || el.value || el.getAttribute('aria-label') || el.tagName).replace(/\s+/g, ' ').trim().slice(0, 24);
            return 'CLICKED::Tested ' + (isTap ? 'tap' : 'click') + ' on "' + (label || el.tagName.toLowerCase()) + '"';
          } catch (e) {
            return 'RESTRICTED::' + (e && e.message ? e.message : 'Unable to execute interaction');
          }
        })();
    """.trimIndent()
}

private fun buildRandomSafeTapScript(lastSelector: String): String {
    val quotedLast = org.json.JSONObject.quote(lastSelector)
    return """
        (function() {
          try {
            $AD_GUARD_JS
            $TAP_RIPPLE_JS
            var now = Date.now();
            function escapeCssId(id) {
              return (window.CSS && CSS.escape) ? CSS.escape(id) : id.replace(/([^\w-])/g, '\\$1');
            }
            function buildSelector(el) {
              if (el.id && /^[A-Za-z_][A-Za-z0-9_-]*$/.test(el.id)) {
                var idSel = '#' + escapeCssId(el.id);
                try { if (document.querySelector(idSel) === el) return idSel; } catch (e0) {}
              }
              var parts = [];
              var cur = el;
              while (cur && cur.nodeType === 1 && cur.tagName.toLowerCase() !== 'html') {
                var tag = cur.tagName.toLowerCase();
                if (cur.id && /^[A-Za-z_][A-Za-z0-9_-]*$/.test(cur.id)) {
                  parts.unshift('#' + escapeCssId(cur.id));
                  break;
                }
                var sib = cur;
                var idx = 1;
                while (sib.previousElementSibling) {
                  sib = sib.previousElementSibling;
                  if (sib.tagName === cur.tagName) idx++;
                }
                parts.unshift(tag + ':nth-of-type(' + idx + ')');
                cur = cur.parentElement;
              }
              return parts.join(' > ');
            }

            var nodes = document.querySelectorAll('button, a[href], [role="button"], input[type="button"], input[type="submit"]');
            var viewportH = window.innerHeight || 360;
            var curOrigin = (window.location && window.location.origin && window.location.origin !== 'null') ? window.location.origin : '';
            var inViewport = [];
            var allSafe = [];

            for (var i = 0; i < nodes.length && allSafe.length < 40; i++) {
              var el = nodes[i];
              if (!isInteractable(el)) continue;
              if (isProhibitedAdElement(el)) continue;
              if (el.tagName && el.tagName.toLowerCase() === 'a') {
                var hrefAttr = el.getAttribute('href') || '';
                if (/^https?:\/\//i.test(hrefAttr) && curOrigin) {
                  try {
                    var parsed = new URL(hrefAttr, window.location.href);
                    if (parsed.origin !== curOrigin) continue;
                  } catch (eUrl) { continue; }
                }
              }
              var sel = buildSelector(el);
              if (!sel) continue;
              var rect = el.getBoundingClientRect();
              var entry = { el: el, sel: sel };
              allSafe.push(entry);
              if (rect.bottom > 8 && rect.top < viewportH - 8) {
                inViewport.push(entry);
              }
            }

            var pool = inViewport.length > 0 ? inViewport : allSafe;
            if (pool.length === 0) {
              return JSON.stringify({ status: 'NO_TARGET', label: 'No safe non-ad elements found', selector: '' });
            }

            var lastSel = $quotedLast;
            var filtered = (pool.length > 1 && lastSel) ? pool.filter(function(item) { return item.sel !== lastSel; }) : pool;
            if (filtered.length === 0) filtered = pool;
            var pick = filtered[Math.floor(Math.random() * filtered.length)];
            var chosenEl = pick.el;

            if (chosenEl.scrollIntoView) {
              chosenEl.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'auto' });
            }
            var r = chosenEl.getBoundingClientRect();
            var cx = r.left + r.width / 2;
            var cy = r.top + r.height / 2;

            spawnTapRipple(cx, cy, '#34D399');

            var prevOutline = chosenEl.style.outline;
            chosenEl.style.outline = '2px solid #34D399';
            setTimeout(function() { try { chosenEl.style.outline = prevOutline; } catch (_) {} }, 350);

            var touchHandled = false;
            if (typeof Touch === 'function' && typeof TouchEvent === 'function') {
              try {
                var touchObj = new Touch({
                  identifier: now % 100000,
                  target: chosenEl,
                  clientX: cx, clientY: cy, screenX: cx, screenY: cy,
                  pageX: cx + (window.scrollX || 0), pageY: cy + (window.scrollY || 0),
                  radiusX: 4, radiusY: 4, force: 1
                });
                chosenEl.dispatchEvent(new TouchEvent('touchstart', {
                  bubbles: true, cancelable: true, view: window,
                  touches: [touchObj], targetTouches: [touchObj], changedTouches: [touchObj]
                }));
                touchHandled = !chosenEl.dispatchEvent(new TouchEvent('touchend', {
                  bubbles: true, cancelable: true, view: window,
                  touches: [], targetTouches: [], changedTouches: [touchObj]
                }));
              } catch (eTouch) {}
            }
            if (typeof PointerEvent === 'function') {
              chosenEl.dispatchEvent(new PointerEvent('pointerdown', {
                bubbles: true, cancelable: true, view: window, pointerType: 'touch', isPrimary: true, clientX: cx, clientY: cy
              }));
              var puCanceled = !chosenEl.dispatchEvent(new PointerEvent('pointerup', {
                bubbles: true, cancelable: true, view: window, pointerType: 'touch', isPrimary: true, clientX: cx, clientY: cy
              }));
              touchHandled = touchHandled || puCanceled;
            }
            if (!touchHandled) {
              if (chosenEl.focus) { try { chosenEl.focus({ preventScroll: true }); } catch (_) {} }
              chosenEl.click();
            }

            var lbl = (chosenEl.innerText || chosenEl.value || chosenEl.getAttribute('aria-label') || chosenEl.tagName)
              .replace(/\s+/g, ' ')
              .trim()
              .slice(0, 22) || chosenEl.tagName.toLowerCase();

            return JSON.stringify({ status: 'TAPPED', label: lbl, selector: pick.sel });
          } catch (e) {
            return JSON.stringify({ status: 'RESTRICTED', label: (e && e.message) ? e.message : 'Restricted', selector: '' });
          }
        })();
    """.trimIndent()
}

private val DEMO_TEST_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>WebVisit Demo Test Environment</title>
  <style>
    * { box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    body { margin: 0; padding: 18px; background: linear-gradient(160deg, #06091a, #101944, #1b1045); color: #f8fafc; }
    .hdr { padding: 16px; border-radius: 18px; background: rgba(255,255,255,0.08); border: 1px solid rgba(56,189,248,0.35); margin-bottom: 16px; }
    .badge { display: inline-block; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 700; background: rgba(52,211,153,0.18); color: #34d399; border: 1px solid rgba(52,211,153,0.45); }
    .card { padding: 16px; border-radius: 16px; background: rgba(15,23,56,0.78); border: 1px solid rgba(255,255,255,0.14); margin-bottom: 16px; }
    .btn-row { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
    button, .demo-link { padding: 12px 14px; border-radius: 12px; border: 1px solid rgba(56,189,248,0.5); background: rgba(56,189,248,0.16); color: #f8fafc; font-weight: 700; font-size: 13px; cursor: pointer; text-decoration: none; display: inline-block; }
    button.purple { border-color: rgba(168,85,247,0.55); background: rgba(168,85,247,0.2); }
    button.emerald { border-color: rgba(52,211,153,0.55); background: rgba(52,211,153,0.18); }
    #demoFeedback { margin-top: 10px; padding: 10px 12px; border-radius: 12px; background: rgba(6,9,26,0.7); color: #38bdf8; font-size: 13px; font-weight: 600; }
    .spacer { height: 220px; display: flex; align-items: center; justify-content: center; border-radius: 14px; border: 1px dashed rgba(255,255,255,0.15); color: #94a3b8; margin-bottom: 16px; font-size: 13px; }
  </style>
</head>
<body>
  <div class="hdr" id="top">
    <span class="badge">Safe Demo Test Environment</span>
    <h2 style="margin:8px 0 4px;">WebVisit Interactive Test Page</h2>
    <p style="margin:0;color:#cbd5e1;font-size:13px;">Built-in local environment for verifying Random Tap, Scroll Down &amp; Scroll Up interactions.</p>
    <div id="demoFeedback">Waiting for UI interaction...</div>
  </div>
  <div class="card">
    <h3>Section 1: Primary Navigation &amp; Actions</h3>
    <div class="btn-row">
      <button type="button" id="demoOverviewBtn" onclick="reportTap('Overview Tab')">Overview Tab</button>
      <button type="button" class="purple" id="demoLayoutBtn" onclick="reportTap('Layout Check')">Layout Check</button>
      <a href="#section-2" class="demo-link" onclick="reportTap('Jump Section 2')">Jump to Section 2</a>
    </div>
  </div>
  <div class="spacer">Scrollable Test Spacer A (Viewport Test)</div>
  <div class="card" id="section-2">
    <h3>Section 2: Form &amp; Feature Controls</h3>
    <div class="btn-row">
      <button type="button" class="emerald" id="demoFeatureBtn" onclick="reportTap('Toggle Feature')">Toggle Feature</button>
      <button type="button" id="demoVerifyBtn" onclick="reportTap('Verify Widget')">Verify Widget</button>
      <button type="button" class="purple" id="demoFilterBtn" onclick="reportTap('Apply Filter')">Apply Filter</button>
    </div>
  </div>
  <div class="spacer">Scrollable Test Spacer B (Mid-Page Scroll)</div>
  <div class="card" id="section-3">
    <h3>Section 3: Footer Actions</h3>
    <div class="btn-row">
      <button type="button" class="emerald" id="demoSubmitBtn" onclick="reportTap('Submit Demo Form')">Submit Demo Form</button>
      <button type="button" id="demoRefreshBtn" onclick="reportTap('Refresh Panel')">Refresh Panel</button>
      <a href="#top" class="demo-link" onclick="reportTap('Back to Top Link')">Scroll Anchor Top</a>
    </div>
  </div>
  <script>
    var demoTapSeq = 0;
    function reportTap(name) {
      demoTapSeq += 1;
      var el = document.getElementById('demoFeedback');
      if (el) el.textContent = 'Demo Event #' + demoTapSeq + ': Activated "' + name + '"';
    }
  </script>
</body>
</html>
""".trimIndent()

private fun decodeJsString(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return runCatching {
        org.json.JSONArray("[$raw]").optString(0, raw.trim('"'))
    }.getOrDefault(raw.trim('"'))
}

private fun parseScannedTargets(raw: String?): Pair<List<ClickTargetItem>, String?> {
    val decoded = decodeJsString(raw)
    if (decoded.startsWith("ERROR:")) {
        return emptyList<ClickTargetItem>() to decoded.removePrefix("ERROR:")
    }
    if (decoded.isBlank() || !decoded.trimStart().startsWith("[")) {
        return emptyList<ClickTargetItem>() to null
    }
    return runCatching {
        val arr = org.json.JSONArray(decoded)
        val list = buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val selector = obj.optString("selector")
                val label = obj.optString("label")
                val tagType = obj.optString("tagType", "button")
                if (selector.isNotBlank() && label.isNotBlank()) {
                    add(ClickTargetItem(selector = selector, label = label, tagType = tagType))
                }
            }
        }
        list to null
    }.getOrElse { emptyList<ClickTargetItem>() to "Could not parse scanned buttons/links." }
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

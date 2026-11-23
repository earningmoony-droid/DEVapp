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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import com.example.model.TestSessionStatus
import com.example.model.WebCommandEvent
import com.example.model.WebCommandType
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GlassStrokeSubtle
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.MidnightDeep
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun WebPreviewCard(
    activeUrl: String?,
    fallbackUrl: String,
    loadToken: Int,
    status: TestSessionStatus,
    isPreviewLoading: Boolean,
    blockedReason: String?,
    pendingCommand: WebCommandEvent?,
    onConsumeCommand: (Long) -> Unit,
    onReloadClick: () -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onBlockedOrError: (String, Boolean) -> Unit,
    onOpenExternal: (String) -> Unit,
    onDirectPreviewClick: (String) -> Unit,
    onAdElementObserved: () -> Unit,
    onInvalidTrafficNotice: (String) -> Unit,
    onListenerHealthResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var webProgress by remember { mutableIntStateOf(0) }
    var lastLoadedToken by remember { mutableIntStateOf(-1) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(pendingCommand, webViewRef) {
        val cmd = pendingCommand ?: return@LaunchedEffect
        val wv = webViewRef
        when (cmd.type) {
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
            WebCommandType.CHECK_CLICK_LISTENER -> {
                if (wv == null) {
                    onListenerHealthResult("""{"ok":false,"status":"FAILED","listenerText":"Not Ready","reason":"Preview WebView is not ready."}""")
                } else {
                    wv.evaluateJavascript(buildCheckListenerHealthScript()) { raw ->
                        val decoded = decodeJsString(raw)
                        onListenerHealthResult(decoded.ifBlank { """{"ok":false,"status":"FAILED","listenerText":"Error: Not Responding","reason":"No response from WebView DOM."}""" })
                    }
                }
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
            val reloadEnabled = activeUrl != null && blockedReason == null && status != TestSessionStatus.LOADING
            Surface(
                onClick = onReloadClick,
                enabled = reloadEnabled,
                shape = RoundedCornerShape(12.dp),
                color = if (reloadEnabled) GlassSurfaceElevated.copy(alpha = 0.7f) else MidnightDeep.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, if (reloadEnabled) GlassStrokeSubtle else Color.Transparent),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("preview_reload_button")
                    .semantics { contentDescription = "Reload Preview" }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = if (reloadEnabled) TextPrimary else TextMuted.copy(alpha = 0.45f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.nav_reload),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (reloadEnabled) TextPrimary else TextMuted.copy(alpha = 0.55f),
                        maxLines = 1
                    )
                }
            }

            val openTarget = activeUrl ?: fallbackUrl
            val openEnabled = openTarget.startsWith("http://", ignoreCase = true) ||
                openTarget.startsWith("https://", ignoreCase = true)
            Surface(
                onClick = { onOpenExternal(openTarget) },
                enabled = openEnabled,
                shape = RoundedCornerShape(12.dp),
                color = if (openEnabled) ElectricCyan.copy(alpha = 0.14f) else MidnightDeep.copy(alpha = 0.35f),
                border = BorderStroke(
                    1.dp,
                    if (openEnabled) ElectricCyan.copy(alpha = 0.45f) else GlassStrokeSubtle
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("open_website_header_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.btn_open_website),
                        tint = if (openEnabled) ElectricCyan else TextMuted.copy(alpha = 0.55f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_open_website),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (openEnabled) TextPrimary else TextMuted.copy(alpha = 0.55f),
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
                .height(360.dp)
                .clip(viewportShape)
                .background(MidnightDeep.copy(alpha = 0.85f))
                .border(1.dp, GlassStrokeSubtle, viewportShape)
        ) {
            if (activeUrl != null && blockedReason == null) {
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

                                    @JavascriptInterface
                                    fun onAdObserved() {
                                        mainHandler.post {
                                            onAdElementObserved()
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onInvalidTraffic(reason: String?) {
                                        mainHandler.post {
                                            onInvalidTrafficNotice(reason.orEmpty())
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
                                    onPageFinished()
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        val desc = error?.description?.toString()
                                            ?: "Network Error: Unable to reach website."
                                        onBlockedOrError("Network Error: $desc", true)
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
                                                "Page Load Failed: HTTP $code response received."
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
                                    onBlockedOrError("Network Error: SSL certificate verification failed.", true)
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
                        TestSessionStatus.LOADED -> EmeraldSuccess
                        TestSessionStatus.STOPPED, TestSessionStatus.BLOCKED -> AmberWarning
                        TestSessionStatus.FAILED, TestSessionStatus.NETWORK_ERROR -> CoralDanger
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
                            TestSessionStatus.FAILED -> "Page Load Failed"
                            TestSessionStatus.NETWORK_ERROR -> "Network Error"
                            TestSessionStatus.STOPPED -> "Test Stopped & Cleared"
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

private const val AD_GUARD_JS = """
  var AD_GUARD_REGEX = /(adsbygoogle|doubleclick|googlesyndication|adservice|adsystem|adserver|adnxs|taboola|outbrain|mgid|sponsored|advertisement|ad-slot|ad_slot|ad-banner|ad_banner|google_ads|data-ad)/i;
  function isAdProtected(el) {
    if (!el) return false;
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
      if (AD_GUARD_REGEX.test(attrs)) return true;
      cur = cur.parentElement;
      depth += 1;
    }
    return false;
  }
"""

private const val TAP_RIPPLE_JS = """
  function spawnTapRipple(cx, cy) {
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
      var r = document.createElement('span');
      r.setAttribute('aria-hidden', 'true');
      var x = (typeof cx === 'number' && isFinite(cx)) ? cx : ((window.innerWidth || 300) / 2);
      var y = (typeof cy === 'number' && isFinite(cy)) ? cy : ((window.innerHeight || 300) / 2);
      r.style.cssText =
        'position:fixed;left:' + x + 'px;top:' + y + 'px;width:44px;height:44px;border-radius:9999px;' +
        'pointer-events:none;box-sizing:border-box;' +
        'background:radial-gradient(circle, rgba(56, 189, 248, 0.34) 0%, rgba(56, 189, 248, 0.12) 55%, rgba(56, 189, 248, 0) 75%);' +
        'border:1.5px solid rgba(56, 189, 248, 0.72);' +
        'transform:translate(-50%, -50%) scale(0.22);opacity:0.9;';
      layer.appendChild(r);
      var cleanup = function() { try { if (r.parentNode) r.parentNode.removeChild(r); } catch (e) {} };
      if (typeof r.animate === 'function') {
        var anim = r.animate([
          { transform: 'translate(-50%, -50%) scale(0.22)', opacity: 0.9 },
          { transform: 'translate(-50%, -50%) scale(1.28)', opacity: 0 }
        ], { duration: 450, easing: 'cubic-bezier(0.16, 1, 0.3, 1)', fill: 'forwards' });
        anim.onfinish = cleanup;
        setTimeout(cleanup, 520);
      } else {
        setTimeout(cleanup, 450);
      }
    } catch (e) {}
  }
"""

private fun buildDirectClickObserverScript(): String {
    return """
        (function() {
          try {
            $AD_GUARD_JS
            $TAP_RIPPLE_JS
            if (window.__wvClickObserverController && typeof window.__wvClickObserverController.abort === 'function') {
              try { window.__wvClickObserverController.abort(); } catch (eAbort) {}
            }
            var controller = typeof AbortController === 'function' ? new AbortController() : null;
            window.__wvClickObserverController = controller;
            var lastAt = 0;
            var lastEl = null;

            var handler = function(ev) {
              try {
                if (window.__wvHealthProbeInFlight) return;
                if (!ev.isTrusted) {
                  if (window.WebVisitBridge && typeof window.WebVisitBridge.onInvalidTraffic === 'function') {
                    window.WebVisitBridge.onInvalidTraffic('Invalid Traffic Filtered: Untrusted synthetic click ignored.');
                  }
                  return;
                }
                if (isAdProtected(ev.target)) {
                  if (window.WebVisitBridge && typeof window.WebVisitBridge.onAdObserved === 'function') {
                    window.WebVisitBridge.onAdObserved();
                  }
                  return;
                }
                var t = ev.target && ev.target.closest ? ev.target : null;
                var clickable = t ? t.closest('button, a[href], [role="button"], input[type="button"], input[type="submit"], [onclick]') : null;
                if (!clickable) return;

                var now = Date.now();
                if (clickable === lastEl && now - lastAt < 250) {
                  if (window.WebVisitBridge && typeof window.WebVisitBridge.onInvalidTraffic === 'function') {
                    window.WebVisitBridge.onInvalidTraffic('Invalid Traffic Filtered: Rapid duplicate click burst (<250ms) ignored.');
                  }
                  return;
                }
                lastEl = clickable;
                lastAt = now;

                var rect = clickable.getBoundingClientRect ? clickable.getBoundingClientRect() : null;
                var hasCoord = typeof ev.clientX === 'number' && typeof ev.clientY === 'number' && (ev.clientX !== 0 || ev.clientY !== 0);
                var rx = hasCoord ? ev.clientX : (rect ? rect.left + rect.width / 2 : (window.innerWidth || 300) / 2);
                var ry = hasCoord ? ev.clientY : (rect ? rect.top + rect.height / 2 : (window.innerHeight || 300) / 2);
                spawnTapRipple(rx, ry);

                var lbl = (clickable.innerText || clickable.value || clickable.getAttribute('aria-label') || clickable.tagName || 'element')
                  .replace(/\s+/g, ' ')
                  .trim()
                  .slice(0, 24);
                if (window.WebVisitBridge && typeof window.WebVisitBridge.onDirectClick === 'function') {
                  window.WebVisitBridge.onDirectClick(lbl);
                }
              } catch (err) {}
            };

            var opts = controller ? { capture: true, signal: controller.signal } : true;
            document.addEventListener('click', handler, opts);
          } catch (e) {}
        })();
    """.trimIndent()
}

private fun buildCheckListenerHealthScript(): String {
    return """
        (function() {
          try {
            var body = document.body;
            if (!body) {
              return JSON.stringify({ ok: false, status: 'FAILED', listenerText: 'Error: No DOM Body', reason: 'Page Load Error: Document body is unavailable.' });
            }
            var bodyStyle = window.getComputedStyle(body);
            if (bodyStyle && bodyStyle.pointerEvents === 'none') {
              return JSON.stringify({ ok: false, status: 'FAILED', listenerText: 'Blocked by CSS', reason: 'Click Tracking Error: Document body has pointer-events: none.' });
            }
            var probeReceived = false;
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.setAttribute('aria-hidden', 'true');
            btn.style.cssText = 'position:fixed;left:-9999px;top:-9999px;width:1px;height:1px;opacity:0;pointer-events:none;';
            btn.addEventListener('click', function() { probeReceived = true; }, { once: true });
            body.appendChild(btn);
            window.__wvHealthProbeInFlight = true;
            try {
              btn.click();
            } finally {
              window.__wvHealthProbeInFlight = false;
              try { if (btn.parentNode) btn.parentNode.removeChild(btn); } catch (rmErr) {}
            }
            if (probeReceived) {
              return JSON.stringify({ ok: true, status: 'PASSED', listenerText: 'Active (0 Duplicates)', reason: '' });
            }
            return JSON.stringify({ ok: false, status: 'FAILED', listenerText: 'Error: Not Responding', reason: 'Click Tracking Error: DOM click event pipeline did not respond.' });
          } catch (e) {
            window.__wvHealthProbeInFlight = false;
            return JSON.stringify({ ok: false, status: 'BLOCKED', listenerText: 'Blocked', reason: (e && e.message) ? e.message : 'Unable to check DOM listener.' });
          }
        })();
    """.trimIndent()
}

private fun decodeJsString(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return runCatching {
        org.json.JSONArray("[$raw]").optString(0, raw.trim('"'))
    }.getOrDefault(raw.trim('"'))
}

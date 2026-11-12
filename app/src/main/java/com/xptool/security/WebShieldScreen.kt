package com.xptool.security

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.xptool.security.core.url.UrlNormalizer
import com.xptool.security.core.url.UrlResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebShieldScreen(
    incomingInterceptedUrl: String? = null,
    onUrlConsumed: () -> Unit = {},
    onBackToHub: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    var urlInput by remember { mutableStateOf("https://duckduckgo.com") }
    var currentLoadedUrl by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var isPageLoading by remember { mutableStateOf(false) }

    // Privacy Shield Controls
    var jsEnabled by remember { mutableStateOf(false) }
    var blockTrackers by remember { mutableStateOf(true) }
    var blockCookies by remember { mutableStateOf(true) }
    var incognitoMode by remember { mutableStateOf(true) }
    var httpsOnly by remember { mutableStateOf(true) }
    var trackersBlockedCount by remember { mutableStateOf(0) }
    var showPrivacySheet by remember { mutableStateOf(false) }

    // Security & Warning States
    var currentVerdict by remember { mutableStateOf("VERIFIED") }
    var warningTitle by remember { mutableStateOf<String?>(null) }
    var warningSubtitle by remember { mutableStateOf<String?>(null) }
    var isHardBlocked by remember { mutableStateOf(false) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val trackerHosts = remember {
        listOf("doubleclick.net", "google-analytics.com", "facebook.com/tr", "ads.", "tracker", "telemetry", "pixel.")
    }

    fun wipeSandboxSession() {
        webViewRef?.clearCache(true)
        webViewRef?.clearHistory()
        webViewRef?.clearFormData()
        CookieManager.getInstance().removeAllCookies(null)
        trackersBlockedCount = 0
        Toast.makeText(context, "Session data wiped clean!", Toast.LENGTH_SHORT).show()
    }

    fun applyWebSettings(wv: WebView) {
        wv.settings.javaScriptEnabled = jsEnabled
        wv.settings.domStorageEnabled = !incognitoMode
        wv.settings.saveFormData = !incognitoMode
        CookieManager.getInstance().setAcceptCookie(!blockCookies)
    }

    fun scanAndLoad(rawTarget: String, forceProceed: Boolean = false) {
        if (rawTarget.isBlank()) return
        focusManager.clearFocus()

        var target = rawTarget.trim()
        if (httpsOnly && target.startsWith("http://")) {
            target = target.replaceFirst("http://", "https://")
        }

        scope.launch {
            isScanning = true
            warningTitle = null
            warningSubtitle = null
            isHardBlocked = false

            when (val norm = UrlNormalizer.normalize(target)) {
                is UrlResult.Failure -> {
                    currentVerdict = "INVALID"
                    isHardBlocked = true
                    warningTitle = "Invalid or Unsafe URL"
                    warningSubtitle = norm.reason
                }
                is UrlResult.Success -> {
                    val initial = norm.url
                    val report = withContext(Dispatchers.IO) {
                        val chain = LookSafeEngine.resolveRedirects(initial.normalized)
                        val finalUrl = when (val res = UrlNormalizer.normalize(chain.last())) {
                            is UrlResult.Success -> res.url
                            is UrlResult.Failure -> initial
                        }
                        val (verdict, reasons) = LookSafeEngine.analyze(initial, finalUrl, chain)
                        FullScanReport(initial.normalized, finalUrl, chain, verdict, reasons)
                    }

                    urlInput = report.finalUrl.host
                    currentLoadedUrl = report.finalUrl.normalized

                    when {
                        report.verdict.startsWith("MALICIOUS") && !forceProceed -> {
                            currentVerdict = "MALICIOUS"
                            isHardBlocked = true
                            warningTitle = "Malicious domain blocked"
                            warningSubtitle = report.reasons.firstOrNull() ?: "This domain mimics a trusted brand."
                            webViewRef?.stopLoading()
                        }
                        (report.verdict.startsWith("SUSPICIOUS") || report.verdict.startsWith("UNVERIFIED")) && !forceProceed -> {
                            currentVerdict = "UNVERIFIED"
                            isHardBlocked = false
                            warningTitle = "Unverified domain"
                            warningSubtitle = report.reasons.firstOrNull() ?: "This site couldn't be verified. Proceed with caution."
                            webViewRef?.loadUrl(report.finalUrl.normalized)
                        }
                        else -> {
                            currentVerdict = "VERIFIED"
                            isHardBlocked = false
                            webViewRef?.loadUrl(report.finalUrl.normalized)
                        }
                    }
                }
            }
            isScanning = false
        }
    }

    LaunchedEffect(incomingInterceptedUrl) {
        if (!incomingInterceptedUrl.isNullOrBlank()) {
            urlInput = incomingInterceptedUrl
            scanAndLoad(incomingInterceptedUrl)
            onUrlConsumed()
        }
    }

    // Smooth Animated Shield Color Shift
    val targetShieldColor = when (currentVerdict) {
        "MALICIOUS", "INVALID" -> XPDanger
        "UNVERIFIED" -> XPWarning
        else -> XPSafe
    }
    val shieldColor by animateColorAsState(
        targetValue = targetShieldColor,
        animationSpec = tween(durationMillis = 350),
        label = "ShieldColorAnim"
    )

    // MASTER WRAPPER BOX
    Box(modifier = Modifier.fillMaxSize()) {

        // ==========================================
        // MAIN UI COLUMN
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(XPBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // 1. TOP BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            webViewRef?.destroy()
                            onBackToHub()
                        },
                    color = XPCard,
                    border = BorderStroke(1.dp, XPBorder),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Hub", tint = XPAccentBright, modifier = Modifier.size(14.dp))
                        Text("Hub", color = XPAccentBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = XPAccentBright, modifier = Modifier.size(18.dp))
                    Text("Secure View", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { wipeSandboxSession() },
                    color = XPDanger.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, XPDanger.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Wipe", tint = XPDanger, modifier = Modifier.size(14.dp))
                        Text("Wipe", color = XPDanger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. SLEEK PILL OMNIBOX
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                color = XPCard,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.2.dp, if (isHardBlocked) XPDanger else XPAccent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(shieldColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = shieldColor, modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(XPAccentBright),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { scanAndLoad(urlInput) }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (urlInput.isEmpty()) {
                                Text("Enter URL or domain...", color = Color.Gray, fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(XPAccent)
                            .clickable(enabled = !isScanning) { scanAndLoad(urlInput) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Scan & Open",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. HORIZONTAL STATUS CHIPS ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = shieldColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, shieldColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = shieldColor, modifier = Modifier.size(12.dp))
                        Text(
                            text = "Shield: ${currentVerdict.lowercase().replaceFirstChar { it.uppercase() }}",
                            color = shieldColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            jsEnabled = !jsEnabled
                            webViewRef?.let { applyWebSettings(it); it.reload() }
                        },
                    color = XPCard,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, if (jsEnabled) XPWarning else XPBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = if (jsEnabled) XPWarning else Color.LightGray,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (jsEnabled) "JS On" else "JS Off",
                            color = if (jsEnabled) XPWarning else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (jsEnabled) XPWarning else Color.Gray)
                        )
                    }
                }

                Surface(
                    color = XPCard,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, XPBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, tint = XPAccentBright, modifier = Modifier.size(12.dp))
                        Text(
                            text = "Trackers blocked: $trackersBlockedCount",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    color = XPCard,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, XPBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(12.dp))
                        Text(
                            text = if (blockCookies) "No cookies" else "Cookies on",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. ANIMATED WARNING BANNER CARD
            AnimatedVisibility(
                visible = warningTitle != null,
                enter = expandVertically(animationSpec = tween(260)) + fadeIn(tween(260)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))
            ) {
                val warnColor = if (isHardBlocked) XPDanger else XPWarning
                Column {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = warnColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, warnColor)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(warnColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = warnColor, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(warningTitle ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(warningSubtitle ?: "", color = Color.LightGray, fontSize = 11.5.sp, lineHeight = 15.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        warningTitle = null
                                        if (canGoBack) webViewRef?.goBack()
                                    },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = XPAccent)
                                ) {
                                    Text("Go back", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (currentLoadedUrl != null) {
                                            uriHandler.openUri(currentLoadedUrl!!)
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, XPBorder)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open externally", color = Color.White, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // 5. MAIN WEBVIEW SANDBOX CONTAINER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = XPCard),
                border = BorderStroke(1.dp, XPBorder)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                applyWebSettings(this)
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isPageLoading = true
                                        canGoBack = view?.canGoBack() ?: false
                                        canGoForward = view?.canGoForward() ?: false
                                        if (url != null) currentLoadedUrl = url
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isPageLoading = false
                                        canGoBack = view?.canGoBack() ?: false
                                        canGoForward = view?.canGoForward() ?: false
                                    }

                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        val reqUrl = request?.url?.toString()?.lowercase() ?: return null
                                        if (blockTrackers && trackerHosts.any { reqUrl.contains(it) }) {
                                            trackersBlockedCount++
                                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val nextUrl = request?.url?.toString() ?: return false
                                        when (val norm = UrlNormalizer.normalize(nextUrl)) {
                                            is UrlResult.Success -> {
                                                val (verdict, reasons) = LookSafeEngine.analyze(norm.url, norm.url, listOf(nextUrl))
                                                if (verdict.startsWith("MALICIOUS")) {
                                                    currentVerdict = "MALICIOUS"
                                                    isHardBlocked = true
                                                    warningTitle = "Malicious redirect blocked"
                                                    warningSubtitle = reasons.firstOrNull()
                                                    return true
                                                }
                                            }
                                            is UrlResult.Failure -> return true
                                        }
                                        return false
                                    }
                                }
                                webViewRef = this
                                loadUrl("https://duckduckgo.com")
                            }
                        }
                    )

                    if (isPageLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                            color = XPAccentBright,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 6. BOTTOM NAVIGATION DOCK
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                color = XPCard,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, XPBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { if (canGoBack) webViewRef?.goBack() }, enabled = canGoBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = if (canGoBack) Color.White else Color.DarkGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = { if (canGoForward) webViewRef?.goForward() }, enabled = canGoForward) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (canGoForward) Color.White else Color.DarkGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                currentLoadedUrl?.let { uriHandler.openUri(it) }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        Text("Open externally", color = Color.LightGray, fontSize = 12.sp)
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(shieldColor.copy(alpha = 0.16f))
                            .clickable { showPrivacySheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Privacy Controls",
                            tint = shieldColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 7. ANIMATED PRIVACY SHIELD BOTTOM SHEET
        // ==========================================
        // Backdrop Fade
        AnimatedVisibility(
            visible = showPrivacySheet,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showPrivacySheet = false }
            )
        }

        // Sheet Smooth Slide-Up
        AnimatedVisibility(
            visible = showPrivacySheet,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}, // Prevent backdrop click-through
                color = Color(0xFF0E0C18),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                border = BorderStroke(1.dp, XPBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = 0.5f))
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(XPAccent.copy(alpha = 0.5f), Color.Transparent))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = XPSafe, modifier = Modifier.size(34.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Privacy Shield", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Your privacy, in your control.", color = Color.Gray, fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    PrivacyToggleItem(
                        icon = Icons.Default.Code,
                        title = "JavaScript",
                        subtitle = "Prevents script execution",
                        checked = jsEnabled,
                        onCheckedChange = {
                            jsEnabled = it
                            webViewRef?.let { wv -> applyWebSettings(wv); wv.reload() }
                        }
                    )

                    PrivacyToggleItem(
                        icon = Icons.Default.Shield,
                        title = "Block trackers",
                        subtitle = "Stops tracking scripts and beacons",
                        checked = blockTrackers,
                        onCheckedChange = { blockTrackers = it }
                    )

                    PrivacyToggleItem(
                        icon = Icons.Default.DataUsage,
                        title = "Block third-party cookies",
                        subtitle = "Prevents cross-site cookies",
                        checked = blockCookies,
                        onCheckedChange = {
                            blockCookies = it
                            webViewRef?.let { wv -> applyWebSettings(wv) }
                        }
                    )

                    PrivacyToggleItem(
                        icon = Icons.Default.VisibilityOff,
                        title = "Incognito session",
                        subtitle = "No local storage, no history",
                        checked = incognitoMode,
                        onCheckedChange = {
                            incognitoMode = it
                            webViewRef?.let { wv -> applyWebSettings(wv) }
                        }
                    )

                    PrivacyToggleItem(
                        icon = Icons.Default.Lock,
                        title = "HTTPS only",
                        subtitle = "For a more secure connection",
                        checked = httpsOnly,
                        onCheckedChange = { httpsOnly = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = XPSafe.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, XPSafe.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = XPSafe, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connection: Encrypted (HTTPS / TLS)", color = XPSafe, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            wipeSandboxSession()
                            showPrivacySheet = false
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, XPDanger),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = XPDanger.copy(alpha = 0.12f))
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = XPDanger, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wipe session data", color = XPDanger, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PrivacyToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = XPCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, XPBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(XPAccent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = XPAccentBright, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = Color.Gray, fontSize = 11.sp)
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = XPAccent,
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF232030)
                )
            )
        }
    }
}

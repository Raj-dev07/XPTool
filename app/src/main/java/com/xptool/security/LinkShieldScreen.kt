package com.xptool.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xptool.security.core.url.UrlNormalizer
import com.xptool.security.core.url.UrlResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkShieldScreen(
    incomingSharedText: String? = null,
    onShareConsumed: () -> Unit = {},
    onBackToHub: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var errorReason by remember { mutableStateOf<String?>(null) }
    var scanReport by remember { mutableStateOf<FullScanReport?>(null) }
    var wasShared by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    fun triggerScan(targetUrl: String) {
        scope.launch {
            isScanning = true
            errorReason = null
            scanReport = null

            when (val normResult = UrlNormalizer.normalize(targetUrl)) {
                is UrlResult.Failure -> errorReason = normResult.reason
                is UrlResult.Success -> {
                    val initialUrl = normResult.url
                    scanReport = withContext(Dispatchers.IO) {
                        val chain = LookSafeEngine.resolveRedirects(initialUrl.normalized)
                        val finalNormUrl = when (val res = UrlNormalizer.normalize(chain.last())) {
                            is UrlResult.Success -> res.url
                            is UrlResult.Failure -> initialUrl
                        }
                        val (verdict, reasons) = LookSafeEngine.analyze(initialUrl, finalNormUrl, chain)
                        FullScanReport(initialUrl.normalized, finalNormUrl, chain, verdict, reasons)
                    }
                }
            }
            isScanning = false
        }
    }

    LaunchedEffect(incomingSharedText) {
        if (!incomingSharedText.isNullOrBlank()) {
            url = incomingSharedText
            wasShared = true
            triggerScan(incomingSharedText)
            onShareConsumed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onBackToHub() },
                color = XPCard,
                border = BorderStroke(1.dp, XPBorder),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "← Hub",
                    color = XPAccentBright,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
            Text("XPTool • Link Shield", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text("Link Shield", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (wasShared) "Shared link intercepted! Auto-scan complete." else "Unmasks shortlinks & checks suspicious URLs.",
            color = if (wasShared) XPAccentBright else Color.LightGray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it; wasShared = false },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://bit.ly/... or example.com") },
            label = { Text("Enter or Share URL") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { triggerScan(url) },
            enabled = !isScanning,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = XPAccent, contentColor = Color.White)
        ) {
            Text(
                text = if (isScanning) "UNMASKING & VERIFYING..." else "SCAN LINK",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = XPCard),
            border = BorderStroke(1.dp, XPBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("SCAN STATUS", color = XPAccentBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                when {
                    isScanning -> Text("Unmasking redirects & running LookSafe checks...", color = XPAccentBright)
                    errorReason != null -> {
                        Text("INVALID / REJECTED URL", color = XPDanger, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(errorReason!!, color = Color.White, fontSize = 14.sp)
                    }
                    scanReport != null -> {
                        val report = scanReport!!
                        val verdictColor = when {
                            report.verdict.startsWith("MALICIOUS") -> XPDanger
                            report.verdict.startsWith("SUSPICIOUS") || report.verdict.startsWith("UNVERIFIED") -> XPWarning
                            else -> XPSafe
                        }

                        Text("VERDICT: ${report.verdict}", color = verdictColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Final Host: ${report.finalUrl.host}\nFinal Destination: ${report.finalUrl.normalized}",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )

                        if (report.redirectChain.size > 1) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Redirect Chain (${report.redirectChain.size - 1} hop):", color = XPAccentBright, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            report.redirectChain.forEachIndexed { idx, hop ->
                                Text("${idx + 1}. $hop", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }

                        if (report.reasons.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("LookSafe Security Flags:", color = verdictColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            report.reasons.forEach { reason ->
                                Text("• $reason", color = Color.White, fontSize = 13.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("• Live DNS resolved & verified.\n• Passed LookSafe anti-spoofing checks.", color = XPSafe, fontSize = 13.sp)
                        }

                        // STEP 3 & 4: Open Website Button or Block Banner
                        Spacer(modifier = Modifier.height(16.dp))

                        if (!report.verdict.startsWith("MALICIOUS")) {
                            Button(
                                onClick = {
                                    uriHandler.openUri(report.finalUrl.normalized)
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (report.verdict.startsWith("SAFE")) XPSafe else XPWarning,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text(
                                    text = "Open ${report.finalUrl.host} ↗",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = XPDanger.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, XPDanger)
                            ) {
                                Text(
                                    text = "🚫 Blocked by XPTool: Dangerous link cannot be opened.",
                                    color = XPDanger,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                    else -> Text("Waiting for a URL...", color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}

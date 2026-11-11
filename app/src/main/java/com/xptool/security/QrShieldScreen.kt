package com.xptool.security

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.xptool.security.core.url.UrlNormalizer
import com.xptool.security.core.url.UrlResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner


data class QrAnalysisResult(
    val rawPayload: String,
    val type: String, // "WEB LINK", "UPI PAYMENT", "WI-FI CONFIG", "PLAIN TEXT", "INVALID LINK"
    val verdict: String,
    val title: String,
    val details: List<String>,
    val warnings: List<String>,
    val extractedSafeUrl: String? = null // To hold the URL for the Open Button
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrShieldScreen(
    onBackToHub: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var rawInput by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var qrResult by remember { mutableStateOf<QrAnalysisResult?>(null) }
    var scannerError by remember { mutableStateOf<String?>(null) }

    fun analyzePayload(payload: String) {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) {
            scannerError = "QR payload is empty."
            return
        }

        scope.launch {
            isAnalyzing = true
            scannerError = null
            qrResult = null

            qrResult = withContext(Dispatchers.IO) {
                inspectQrContent(trimmed)
            }
            isAnalyzing = false
        }
    }

    fun launchCameraScanner() {
        scannerError = null
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue
                if (!rawValue.isNullOrBlank()) {
                    rawInput = rawValue
                    analyzePayload(rawValue)
                } else {
                    scannerError = "Could not read text from this QR code."
                }
            }
            .addOnCanceledListener {
                // User closed camera
            }
            .addOnFailureListener { e ->
                scannerError = "Camera scanner error: ${e.localizedMessage ?: "Ensure Google Play Services is active or paste payload below."}"
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(22.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onBackToHub() },
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
            Text("XPTool • QR Shield", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text("QR Shield", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Scan QR codes safely to expose hidden phishing URLs & fake UPI collect scams.",
            color = Color.LightGray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Big Camera Scan Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { launchCameraScanner() },
            colors = CardDefaults.cardColors(containerColor = XPCard),
            border = BorderStroke(1.5.dp, XPAccent),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(XPAccent.copy(alpha = 0.14f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR",
                    tint = XPAccentBright,
                    modifier = Modifier.size(38.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "TAP TO SCAN QR WITH CAMERA",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Zero-auto-open protection • Inspects before action",
                    color = XPAccentBright,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Manual / Decoded QR Payload Input
        OutlinedTextField(
            value = rawInput,
            onValueChange = { rawInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("upi://pay?pa=... or https://...") },
            label = { Text("Decoded QR Payload (or Paste to Test)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { analyzePayload(rawInput) },
            enabled = !isAnalyzing,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = XPAccent, contentColor = Color.White)
        ) {
            Text(
                text = if (isAnalyzing) "INSPECTING QR PAYLOAD..." else "INSPECT PAYLOAD",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Result Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = XPCard),
            border = BorderStroke(1.dp, XPBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("QR INSPECTION REPORT", color = XPAccentBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                when {
                    isAnalyzing -> {
                        Text("Decoding & running LookSafe checks...", color = XPAccentBright, fontSize = 15.sp)
                    }
                    scannerError != null -> {
                        Text("SCANNER ALERT", color = XPDanger, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(scannerError!!, color = Color.White, fontSize = 13.sp)
                    }
                    qrResult != null -> {
                        val res = qrResult!!
                        val verdictColor = when {
                            res.verdict.startsWith("MALICIOUS") || res.verdict.startsWith("HIGH RISK") -> XPDanger
                            res.verdict.startsWith("SUSPICIOUS") || res.verdict.startsWith("CAUTION") || res.verdict.startsWith("UNVERIFIED") -> XPWarning
                            else -> XPSafe
                        }

                        Surface(
                            color = verdictColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, verdictColor)
                        ) {
                            Text(
                                text = "TYPE: ${res.type}  •  ${res.verdict}",
                                color = verdictColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(res.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        res.details.forEach { detail ->
                            Text(detail, color = Color.LightGray, fontSize = 13.sp, lineHeight = 19.sp)
                        }

                        if (res.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Security Warnings:", color = verdictColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            res.warnings.forEach { warn ->
                                Text("• $warn", color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }

                        // Web Link Action Button / Block Banner
                        if (res.type == "WEB LINK" && res.extractedSafeUrl != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            if (!res.verdict.startsWith("MALICIOUS") && !res.verdict.startsWith("HIGH RISK")) {
                                Button(
                                    onClick = {
                                        uriHandler.openUri(res.extractedSafeUrl)
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (res.verdict.startsWith("SAFE")) XPSafe else XPWarning,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text(
                                        text = "Open Website ↗",
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
                                        text = "🚫 Blocked by XPTool: Malicious QR Link cannot be opened.",
                                        color = XPDanger,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "Tap the camera card above to scan a QR code, or paste a QR string to test.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// Background analyzer for QR Payloads (UPI, URLs, Wi-Fi, Plain Text)
private fun inspectQrContent(raw: String): QrAnalysisResult {
    val lower = raw.lowercase()

    // 1. UPI Payment QR Detection (Very common in Indian QR scams)
    if (lower.startsWith("upi://pay")) {
        val uri = Uri.parse(raw)
        val payeeAddress = uri.getQueryParameter("pa") ?: "Unknown UPI ID"
        val payeeName = uri.getQueryParameter("pn") ?: "Unknown Name"
        val amount = uri.getQueryParameter("am")
        val note = uri.getQueryParameter("tn") ?: "None"

        val details = mutableListOf(
            "Payee Name: $payeeName",
            "UPI ID (VPA): $payeeAddress",
            "Pre-filled Amount: ${if (!amount.isNullOrBlank()) "₹$amount" else "None (User enters amount)"}",
            "Transaction Note: $note"
        )

        val warnings = mutableListOf<String>()
        warnings.add("NEVER enter your UPI PIN to 'receive' money. Scanning a UPI QR always SENDS money from your bank!")

        if (!amount.isNullOrBlank()) {
            warnings.add("This QR has a pre-locked amount of ₹$amount. Verify before paying.")
        }

        val verdict = if (!amount.isNullOrBlank()) "CAUTION (AMOUNT LOCKED)" else "PAYMENT REQUEST"

        return QrAnalysisResult(
            rawPayload = raw,
            type = "UPI PAYMENT",
            verdict = verdict,
            title = "UPI Payment to $payeeName",
            details = details,
            warnings = warnings
        )
    }

    // 2. Wi-Fi Auto-Connect QR
    if (lower.startsWith("wifi:")) {
        return QrAnalysisResult(
            rawPayload = raw,
            type = "WI-FI CONFIG",
            verdict = "CAUTION",
            title = "Wi-Fi Network Configuration QR",
            details = listOf("Raw Config: $raw"),
            warnings = listOf("Connecting to unknown public Wi-Fi via QR can expose your traffic to attackers.")
        )
    }

    // 3. Check if it's a URL or Domain
    val extractedUrl = LookSafeEngine.extractUrlFromText(raw)
    if (extractedUrl.contains(".")) {
        when (val norm = UrlNormalizer.normalize(extractedUrl)) {
            is UrlResult.Success -> {
                val initial = norm.url
                val chain = LookSafeEngine.resolveRedirects(initial.normalized)
                val finalUrl = when (val finalNorm = UrlNormalizer.normalize(chain.last())) {
                    is UrlResult.Success -> finalNorm.url
                    is UrlResult.Failure -> initial
                }
                val (verdict, reasons) = LookSafeEngine.analyze(initial, finalUrl, chain)

                val details = mutableListOf(
                    "Extracted URL: ${initial.normalized}",
                    "Final Destination: ${finalUrl.normalized}",
                    "Final Host: ${finalUrl.host}"
                )
                if (chain.size > 1) {
                    details.add("Redirects: ${chain.size - 1} hop(s) unmasked")
                }

                return QrAnalysisResult(
                    rawPayload = raw,
                    type = "WEB LINK",
                    verdict = verdict,
                    title = "QR Points to ${finalUrl.host}",
                    details = details,
                    warnings = if (reasons.isEmpty()) listOf("Passed LookSafe & DNS verification.") else reasons,
                    extractedSafeUrl = finalUrl.normalized // Store URL to open!
                )
            }
            is UrlResult.Failure -> {
                return QrAnalysisResult(
                    rawPayload = raw,
                    type = "INVALID LINK",
                    verdict = "HIGH RISK / CORRUPT",
                    title = "Malformed URL Inside QR",
                    details = listOf("Payload: $raw"),
                    warnings = listOf(norm.reason)
                )
            }
        }
    }

    // 4. Plain Text QR
    return QrAnalysisResult(
        rawPayload = raw,
        type = "PLAIN TEXT",
        verdict = "SAFE (TEXT ONLY)",
        title = "Plain Text QR Code",
        details = listOf("Content: $raw"),
        warnings = listOf("Contains plain text only (No executable link or payment trigger).")
    )
}

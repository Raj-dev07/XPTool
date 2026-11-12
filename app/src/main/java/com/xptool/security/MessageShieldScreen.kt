package com.xptool.security

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.xptool.security.core.url.UrlNormalizer
import com.xptool.security.core.url.UrlResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!TextUtils.isEmpty(flat)) {
        val names = flat.split(":")
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                return true
            }
        }
    }
    return false
}

data class MessageScanReport(
    val riskScore: Int,
    val verdict: String,
    val detectedCategory: String,
    val extractedUrl: String?,
    val extractedPhones: List<String>,
    val redFlags: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageShieldScreen(
    onBackToHub: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var messageText by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<MessageScanReport?>(null) }

    // Check permissions state
    var hasOverlayPerm by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasSmsPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isUniversalGuardActive by remember { mutableStateOf(isNotificationServiceEnabled(context)) }

    // Auto-update permission state when returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPerm = Settings.canDrawOverlays(context)
                isUniversalGuardActive = isNotificationServiceEnabled(context)
                hasSmsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasSmsPerm = perms[Manifest.permission.RECEIVE_SMS] == true
    }

    fun analyzeMessage(rawText: String) {
        if (rawText.isBlank()) return
        scope.launch {
            isScanning = true
            report = null
            report = withContext(Dispatchers.IO) {
                runMessageHeuristics(rawText)
            }
            isScanning = false
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
            Text("XPTool • Message Shield", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Message Shield", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Real-time background interceptor & manual scam message analyzer.",
            color = Color.LightGray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ==========================================
        // 1. UNIVERSAL APP GUARD (WhatsApp / Telegram)
        // ==========================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!isUniversalGuardActive) {
                        // Direct hidden settings page khol dega!
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } else {
                        // Agar off karna ho wapas settings bhej de
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                },
            color = if (isUniversalGuardActive) XPSafe.copy(alpha = 0.12f) else XPCard,
            border = BorderStroke(1.dp, if (isUniversalGuardActive) XPSafe else XPAccent),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isUniversalGuardActive) XPSafe.copy(alpha = 0.2f) else XPAccent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isUniversalGuardActive) XPSafe else XPAccentBright,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("Universal App Guard", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = if (isUniversalGuardActive) "Monitoring WhatsApp & Telegram" else "Tap to enable Notification Access",
                        color = if (isUniversalGuardActive) XPSafe else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ==========================================
        // 2. BACKGROUND SMS AUTO-GUARD CONTROL CARD
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = XPCard),
            border = BorderStroke(1.dp, if (hasOverlayPerm && hasSmsPerm) XPSafe else XPBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SMS AUTO-GUARD",
                        color = XPAccentBright,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hasOverlayPerm && hasSmsPerm) "● ARMED" else "● SETUP NEEDED",
                        color = if (hasOverlayPerm && hasSmsPerm) XPSafe else XPWarning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "High-risk SMS triggers a non-blocking floating overlay. Normal/Suspicious SMS sends a quiet notification.",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (hasOverlayPerm) XPSafe else XPAccent)
                    ) {
                        Text(
                            text = if (hasOverlayPerm) "✓ Overlay ON" else "1. Allow Overlay",
                            color = if (hasOverlayPerm) XPSafe else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            hasOverlayPerm = Settings.canDrawOverlays(context)
                            val perms = mutableListOf(
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_SMS
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (hasSmsPerm) XPSafe else XPAccent)
                    ) {
                        Text(
                            text = if (hasSmsPerm) "✓ SMS Guard ON" else "2. Allow SMS",
                            color = if (hasSmsPerm) XPSafe else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        hasOverlayPerm = Settings.canDrawOverlays(context)
                        if (hasOverlayPerm) {
                            OverlayAlertService.showOverlay(
                                context = context,
                                sender = "+91-9876543210",
                                score = 90,
                                category = "Banking / KYC Scam",
                                flag = "Urgency tactic + Spoofed phishing link detected!",
                                preview = "Dear customer, your SBI account will be blocked tonight. Update KYC at http://rnicrosoft-kyc.xyz"
                            )
                        } else {
                            val dummyReport = runMessageHeuristics("Your account has won a cashback reward.")
                            SmsNotificationHelper.sendQuietNotification(context, "VM-REWARD", dummyReport)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = XPAccent.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "⚡ Test Live Scam Overlay Popup",
                        color = XPAccentBright,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==========================================
        // 3. MANUAL SCANNER
        // ==========================================
        OutlinedTextField(
            value = messageText,
            onValueChange = { messageText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(135.dp),
            placeholder = {
                Text("Dear customer, your SBI account will be blocked today. Update PAN immediately at bit.ly/...")
            },
            label = { Text("Paste SMS / WhatsApp Message") },
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { analyzeMessage(messageText) },
                enabled = !isScanning && messageText.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = XPAccent, contentColor = Color.White)
            ) {
                Text(
                    text = if (isScanning) "ANALYZING TEXT & LINKS..." else "SCAN MESSAGE",
                    fontWeight = FontWeight.Bold
                )
            }

            if (messageText.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        messageText = ""
                        report = null
                    },
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, XPBorder)
                ) {
                    Text("Clear", color = Color.LightGray)
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ==========================================
        // 4. REPORT CARD
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = XPCard),
            border = BorderStroke(1.dp, XPBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("MESSAGE THREAT REPORT", color = XPAccentBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                when {
                    isScanning -> {
                        Text(
                            text = "Scanning message patterns & unmasking embedded URLs...",
                            color = XPAccentBright,
                            fontSize = 15.sp
                        )
                    }
                    report != null -> {
                        val res = report!!
                        val verdictColor = when {
                            res.riskScore >= 65 -> XPDanger
                            res.riskScore >= 30 -> XPWarning
                            else -> XPSafe
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = res.verdict,
                                color = verdictColor,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Surface(
                                color = verdictColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, verdictColor)
                            ) {
                                Text(
                                    text = "RISK: ${res.riskScore}%",
                                    color = verdictColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF232030))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = (res.riskScore / 100f).coerceIn(0.04f, 1f))
                                    .fillMaxHeight()
                                    .background(verdictColor)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Pattern Category: ${res.detectedCategory}",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (res.extractedUrl != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Embedded Link: ${res.extractedUrl}",
                                color = XPAccentBright,
                                fontSize = 13.sp
                            )
                        }

                        if (res.extractedPhones.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Callback Number(s): ${res.extractedPhones.joinToString(", ")}",
                                color = XPWarning,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "LookSafe Breakdown:",
                            color = verdictColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        res.redFlags.forEach { flag ->
                            Text(
                                text = "• $flag",
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "Waiting for a message to inspect...",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

fun runMessageHeuristics(rawText: String): MessageScanReport {
    val lower = rawText.lowercase()
    var score = 0
    val flags = mutableListOf<String>()
    var category = "General Message"

    val urgencyWords = listOf(
        "immediately", "urgent", "tonight", "within 24 hours", "suspended",
        "blocked", "deactivated", "disconnected", "expire", "final notice"
    )
    val matchedUrgency = urgencyWords.filter { lower.contains(it) }
    if (matchedUrgency.isNotEmpty()) {
        score += 25
        flags.add("Urgency/Panic tactic detected (${matchedUrgency.take(3).joinToString(", ")}).")
    }

    val kycWords = listOf(
        "kyc", "pan card", "aadhaar", "electricity bill", "power cut",
        "bank account", "credit card", "debit card", "netbanking", "yono"
    )
    val matchedKyc = kycWords.filter { lower.contains(it) }
    if (matchedKyc.isNotEmpty()) {
        score += 25
        category = "Banking / KYC / Utility Alert"
        flags.add("Targets sensitive financial or KYC details (${matchedKyc.take(3).joinToString(", ")}).")
    }

    val baitWords = listOf(
        "won", "lottery", "reward points", "redeem", "cashback",
        "part time job", "work from home", "daily income", "free gift", "loan approved"
    )
    val matchedBait = baitWords.filter { lower.contains(it) }
    if (matchedBait.isNotEmpty()) {
        score += 25
        if (category == "General Message") category = "Reward / Job Bait"
        flags.add("Uses high-lure reward or job bait (${matchedBait.take(3).joinToString(", ")}).")
    }

    if (lower.contains("share otp") || lower.contains("send otp") || lower.contains("upi pin")) {
        score += 45
        category = "OTP / Credential Theft"
        flags.add("CRITICAL: Asks for OTP or PIN. Legitimate institutions NEVER ask for your OTP/PIN.")
    }

    val phoneRegex = Regex("(?<!\\d)(?:\\+91[-\\s]?)?[6-9]\\d{9}(?!\\d)")
    val phones = phoneRegex.findAll(rawText).map { it.value }.distinct().toList()
    if (phones.isNotEmpty() && score > 0) {
        score += 15
        flags.add("Contains unverified callback number (${phones.joinToString()}).")
    }

    var foundUrl: String? = null
    val candidateUrl = LookSafeEngine.extractUrlFromText(rawText)
    if (candidateUrl != rawText.trim() || (!rawText.trim().contains(" ") && candidateUrl.contains("."))) {
        if (candidateUrl.contains(".")) {
            when (val norm = UrlNormalizer.normalize(candidateUrl)) {
                is UrlResult.Success -> {
                    val initial = norm.url
                    val chain = LookSafeEngine.resolveRedirects(initial.normalized)
                    val finalUrl = when (val finalNorm = UrlNormalizer.normalize(chain.last())) {
                        is UrlResult.Success -> finalNorm.url
                        is UrlResult.Failure -> initial
                    }
                    foundUrl = finalUrl.normalized
                    val (urlVerdict, urlReasons) = LookSafeEngine.analyze(initial, finalUrl, chain)

                    when {
                        urlVerdict.startsWith("MALICIOUS") -> {
                            score += 65
                            flags.add("DANGEROUS LINK INSIDE MESSAGE: ${finalUrl.host}")
                            flags.addAll(urlReasons)
                        }
                        urlVerdict.startsWith("SUSPICIOUS") || urlVerdict.startsWith("UNVERIFIED") -> {
                            score += 35
                            flags.add("Suspicious link inside message (${finalUrl.host}).")
                            flags.addAll(urlReasons)
                        }
                        else -> {
                            flags.add("Embedded link (${finalUrl.host}) passed basic domain checks.")
                        }
                    }
                }
                is UrlResult.Failure -> {}
            }
        }
    }

    val finalScore = min(100, score)

    if (flags.isEmpty()) {
        flags.add("No known phishing triggers, panic keywords, or malicious links detected.")
    }

    val verdict = when {
        finalScore >= 65 -> "HIGH SCAM RISK (PHISHING)"
        finalScore >= 30 -> "SUSPICIOUS MESSAGE"
        else -> "LOOKS SAFE (LOW RISK)"
    }

    return MessageScanReport(
        riskScore = finalScore,
        verdict = verdict,
        detectedCategory = category,
        extractedUrl = foundUrl,
        extractedPhones = phones,
        redFlags = flags
    )
}

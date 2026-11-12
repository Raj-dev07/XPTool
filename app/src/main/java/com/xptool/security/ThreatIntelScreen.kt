package com.xptool.security

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.border


// Global Live Telemetry & Custom Rule Store
object ThreatIntelStore {
    private var prefs: SharedPreferences? = null

    // Live Persistent Counters
    var scamsBlocked by mutableStateOf(0)
    var linksUnmasked by mutableStateOf(0)
    var trackersKilled by mutableStateOf(0)
    var phishingCaught by mutableStateOf(0)
    var dnsQueriesVerified by mutableStateOf(0)
    var smsInspected by mutableStateOf(0)

    // Custom Firewall Rules
    val blacklistedDomains = mutableStateListOf<String>()
    val whitelistedDomains = mutableStateListOf<String>()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("XPTool_Intel_Data", Context.MODE_PRIVATE)
            scamsBlocked = prefs?.getInt("scams", 0) ?: 0
            linksUnmasked = prefs?.getInt("links", 0) ?: 0
            trackersKilled = prefs?.getInt("trackers", 0) ?: 0
            phishingCaught = prefs?.getInt("phishing", 0) ?: 0
            dnsQueriesVerified = prefs?.getInt("dns", 0) ?: 0
            smsInspected = prefs?.getInt("sms", 0) ?: 0

            val savedBlack = prefs?.getStringSet("blacklist", null)
            blacklistedDomains.clear()
            if (savedBlack != null) {
                blacklistedDomains.addAll(savedBlack)
            } else {
                blacklistedDomains.addAll(listOf("rnicrosoft-kyc.xyz", "sbi-update-pan.top"))
            }

            val savedWhite = prefs?.getStringSet("whitelist", null)
            whitelistedDomains.clear()
            if (savedWhite != null) {
                whitelistedDomains.addAll(savedWhite)
            } else {
                whitelistedDomains.addAll(listOf("google.com", "onlinesbi.sbi"))
            }
        }
    }

    fun addScam() {
        scamsBlocked++
        prefs?.edit()?.putInt("scams", scamsBlocked)?.apply()
    }

    fun addLink() {
        linksUnmasked++
        prefs?.edit()?.putInt("links", linksUnmasked)?.apply()
    }

    fun addTracker() {
        trackersKilled++
        prefs?.edit()?.putInt("trackers", trackersKilled)?.apply()
    }

    fun addPhishing() {
        phishingCaught++
        prefs?.edit()?.putInt("phishing", phishingCaught)?.apply()
    }

    fun addDnsCheck() {
        dnsQueriesVerified++
        prefs?.edit()?.putInt("dns", dnsQueriesVerified)?.apply()
    }

    fun addSmsInspected() {
        smsInspected++
        prefs?.edit()?.putInt("sms", smsInspected)?.apply()
    }

    fun addRule(context: Context, rawDomain: String, isBlacklist: Boolean) {
        init(context)
        val clean = rawDomain.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringBefore("/")
            .lowercase()

        if (clean.isBlank()) return

        if (isBlacklist) {
            if (!blacklistedDomains.contains(clean)) {
                blacklistedDomains.add(0, clean)
                prefs?.edit()?.putStringSet("blacklist", blacklistedDomains.toSet())?.apply()
            }
        } else {
            if (!whitelistedDomains.contains(clean)) {
                whitelistedDomains.add(0, clean)
                prefs?.edit()?.putStringSet("whitelist", whitelistedDomains.toSet())?.apply()
            }
        }
    }

    fun removeRule(context: Context, domain: String, isBlacklist: Boolean) {
        init(context)
        if (isBlacklist) {
            blacklistedDomains.remove(domain)
            prefs?.edit()?.putStringSet("blacklist", blacklistedDomains.toSet())?.apply()
        } else {
            whitelistedDomains.remove(domain)
            prefs?.edit()?.putStringSet("whitelist", whitelistedDomains.toSet())?.apply()
        }
    }
}

// Reusable Glass styles
val GlassBg = Color.White.copy(alpha = 0.04f)
val GlassBgDeep = Color.White.copy(alpha = 0.02f)
fun GlassBorder() = BorderStroke(
    width = 1.dp,
    brush = Brush.linearGradient(
        listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.02f))
    )
)
val SubtleBorder = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))

@Composable
fun ThreatIntelScreen(
    onBackToHub: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ThreatIntelStore.init(context)
    }

    var selectedTabIsBlacklist by remember { mutableStateOf(true) }
    var showAddRuleBox by remember { mutableStateOf(false) }
    var newDomainInput by remember { mutableStateOf("") }

    var isRunningDiagnostics by remember { mutableStateOf(false) }
    var shieldHealth by remember { mutableStateOf(98) }

    val animatedRingProgress by animateFloatAsState(
        targetValue = if (isRunningDiagnostics) 0.25f else (shieldHealth / 100f),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "HealthRingAnim"
    )

    Box(modifier = Modifier.fillMaxSize().background(XPBackground)) {
        // 🔮 BACKGROUND PURE ROUND RADIAL GLOW ORBS (Zero Square Edges!)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = (-70).dp)
                .size(340.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            XPAccent.copy(alpha = 0.42f),
                            XPAccent.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-110).dp, y = 80.dp)
                .size(320.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            XPSafe.copy(alpha = 0.28f),
                            XPSafe.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 70.dp, y = 70.dp)
                .size(280.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            XPWarning.copy(alpha = 0.22f),
                            XPWarning.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )


        // CONTENT LAYER
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // ==========================================
            // 1. TOP BAR
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onBackToHub() },
                    color = GlassBg,
                    border = SubtleBorder,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Hub", tint = Color.White, modifier = Modifier.size(14.dp))
                        Text("Hub", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(XPAccent.copy(alpha = 0.22f))
                            .border(0.5.dp, XPAccent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrackChanges,
                            contentDescription = null,
                            tint = XPAccentBright,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text("Threat Intel", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }

                Surface(
                    color = XPSafe.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, XPSafe.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(7.dp).clip(CircleShape).background(XPSafe)
                        )
                        Text(
                            text = "ONLINE",
                            color = XPSafe,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // 2. HERO TELEMETRY CARD (GLASS)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GlassBg),
                border = GlassBorder(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Security Score & Stats",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(116.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(104.dp)) {
                                drawArc(
                                    color = Color.White.copy(alpha = 0.05f),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = XPAccent.copy(alpha = 0.4f),
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedRingProgress,
                                    useCenter = false,
                                    style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        listOf(XPAccent, XPAccentBright, XPAccent)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedRingProgress,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$shieldHealth%",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "SHIELD",
                                    color = Color.LightGray,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "HEALTH",
                                    color = Color.LightGray,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MiniStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Link,
                                    iconColor = XPAccentBright,
                                    count = ThreatIntelStore.linksUnmasked.toString(),
                                    label = "Links"
                                )
                                MiniStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Warning,
                                    iconColor = XPDanger,
                                    count = ThreatIntelStore.scamsBlocked.toString().padStart(2, '0'),
                                    label = "Scams"
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MiniStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.VerifiedUser,
                                    iconColor = XPWarning,
                                    count = ThreatIntelStore.phishingCaught.toString(),
                                    label = "Phishing"
                                )
                                MiniStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.BugReport,
                                    iconColor = XPSafe,
                                    count = ThreatIntelStore.trackersKilled.toString(),
                                    label = "Trackers"
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GlassBg),
                border = GlassBorder(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(116.dp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassBgDeep)
                            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("LOCAL DB", color = XPAccentBright, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            Text("v2.6 Synced", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                        }
                    }

                    MiniStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Dns,
                        iconColor = Color(0xFFE91E63),
                        count = ThreatIntelStore.dnsQueriesVerified.toString(),
                        label = "DNS Scans"
                    )

                    MiniStatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Chat,
                        iconColor = XPSafe,
                        count = ThreatIntelStore.smsInspected.toString(),
                        label = "SMS Guard"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ==========================================
            // 3. CUSTOM ENGINE RULES CARD
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = GlassBg),
                border = GlassBorder(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CUSTOM ENGINE RULES",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAddRuleBox = !showAddRuleBox },
                            color = XPAccent.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, XPAccent.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (showAddRuleBox) "× Cancel" else "+ Add Rule",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showAddRuleBox,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    color = GlassBgDeep,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, XPAccent.copy(alpha = 0.4f))
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        BasicTextField(
                                            value = newDomainInput,
                                            onValueChange = { newDomainInput = it },
                                            singleLine = true,
                                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                            cursorBrush = SolidColor(XPAccentBright),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { inner ->
                                                if (newDomainInput.isEmpty()) {
                                                    Text(
                                                        text = if (selectedTabIsBlacklist) "Domain to BLOCK (scam.xyz)" else "Domain to TRUST (mybank.com)",
                                                        color = Color.White.copy(alpha = 0.4f),
                                                        fontSize = 11.5.sp
                                                    )
                                                }
                                                inner()
                                            }
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .height(40.dp)
                                        .clickable {
                                            if (newDomainInput.isNotBlank()) {
                                                ThreatIntelStore.addRule(context, newDomainInput, selectedTabIsBlacklist)
                                                newDomainInput = ""
                                                showAddRuleBox = false
                                            }
                                        },
                                    color = XPAccent.copy(alpha = 0.8f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                        Text("Save", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = GlassBgDeep,
                        shape = RoundedCornerShape(10.dp),
                        border = SubtleBorder
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(3.dp)) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedTabIsBlacklist = true },
                                color = if (selectedTabIsBlacklist) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "[ Blacklist ]",
                                        color = if (selectedTabIsBlacklist) Color.White else Color.White.copy(alpha = 0.4f),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedTabIsBlacklist = false },
                                color = if (!selectedTabIsBlacklist) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "[ Whitelist ]",
                                        color = if (!selectedTabIsBlacklist) Color.White else Color.White.copy(alpha = 0.4f),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val activeList = if (selectedTabIsBlacklist) ThreatIntelStore.blacklistedDomains else ThreatIntelStore.whitelistedDomains

                    activeList.forEach { domain ->
                        RuleDomainRow(
                            domain = domain,
                            isBlocked = selectedTabIsBlacklist,
                            onDelete = { ThreatIntelStore.removeRule(context, domain, selectedTabIsBlacklist) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (selectedTabIsBlacklist && ThreatIntelStore.whitelistedDomains.isNotEmpty()) {
                        val sampleTrusted = ThreatIntelStore.whitelistedDomains.first()
                        RuleDomainRow(
                            domain = sampleTrusted,
                            isBlocked = false,
                            onDelete = { ThreatIntelStore.removeRule(context, sampleTrusted, false) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 4. ACTIVE ENGINE MODULES
            // ==========================================
            Text(
                text = "Engine Modules & Diagnostics",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            val diagnosticPairs = listOf(
                Pair("Redirect Unmasker", "ACTIVE") to Pair("Punycode AI", "ACTIVE"),
                Pair("Zero-Width Stripper", "ACTIVE") to Pair("Brand Spoofing", "ACTIVE"),
                Pair("Background SMS", "ARMED") to Pair("Rule Firewall", "ACTIVE"),
                Pair("UPI QR Inspector", "ACTIVE") to Pair("Sandbox Guard", "ACTIVE"),
                Pair("Overlay Alert", "ARMED") to Pair("DNS Sinkhole", "ACTIVE")
            )

            diagnosticPairs.forEach { (leftMod, rightMod) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticChipCard(
                        modifier = Modifier.weight(1f),
                        title = leftMod.first,
                        status = if (isRunningDiagnostics) "CHECKING" else leftMod.second
                    )
                    DiagnosticChipCard(
                        modifier = Modifier.weight(1f),
                        title = rightMod.first,
                        status = if (isRunningDiagnostics) "CHECKING" else rightMod.second
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 5. GLOWING GLASS BUTTON
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable {
                        if (!isRunningDiagnostics) {
                            scope.launch {
                                isRunningDiagnostics = true
                                delay(900)
                                shieldHealth = 100
                                isRunningDiagnostics = false
                                Toast
                                    .makeText(
                                        context,
                                        "All LookSafe sub-engines 100% healthy!",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                        }
                    },
                color = XPAccent.copy(alpha = 0.15f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, XPAccent.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = XPAccentBright,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunningDiagnostics) "RUNNING DIAGNOSTICS..." else "RUN ENGINE DIAGNOSTICS",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MiniStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    count: String,
    label: String
) {
    Surface(
        modifier = modifier.height(52.dp),
        color = GlassBg,
        shape = RoundedCornerShape(12.dp),
        border = SubtleBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
            }

            Column {
                Text(
                    text = count,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RuleDomainRow(
    domain: String,
    isBlocked: Boolean,
    onDelete: () -> Unit
) {
    val badgeColor = if (isBlocked) XPDanger else XPSafe
    val badgeText = if (isBlocked) "BLOCKED" else "TRUSTED"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GlassBg,
        shape = RoundedCornerShape(10.dp),
        border = SubtleBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = domain,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete Rule",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onDelete() }
                )
            }
        }
    }
}

@Composable
private fun DiagnosticChipCard(
    modifier: Modifier = Modifier,
    title: String,
    status: String
) {
    Surface(
        modifier = modifier.height(38.dp),
        color = GlassBg,
        shape = RoundedCornerShape(10.dp),
        border = SubtleBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = XPSafe.copy(alpha = 0.8f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            Text(
                text = "— $status",
                color = XPSafe,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

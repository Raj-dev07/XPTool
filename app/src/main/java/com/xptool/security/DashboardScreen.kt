package com.xptool.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

// ─────────────────────────────────────────────────────────────────────────────
// Local design tokens (everything else comes from XPTheme.kt)
// ─────────────────────────────────────────────────────────────────────────────
private val XPCyan = Color(0xFF00E5FF)
private val XPMuted = Color(0xFF9A93B5)
private val XPDeep = Color(0xFF0C0A14)
private val XPGlassTop = Color(0xFF1C1633)
private const val TWO_PI = 6.2831855f

private fun tintFor(id: String): Color = when (id) {
    "link" -> XPAccentBright
    "qr" -> Color(0xFF5B8CFF)
    "msg" -> Color(0xFFD14DFF)
    "num" -> Color(0xFF4DA3FF)
    "web" -> Color(0xFF8E5BFF)
    "intel" -> XPCyan
    else -> XPAccentBright
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DashboardHubScreen(
    selectedLockedModule: String?,
    onModuleClick: (ShieldModule) -> Unit
) {
    val modules = listOf(
        ShieldModule("link", Icons.Default.Security, "Link Shield", "Scan & unmask URLs", true),
        ShieldModule("qr", Icons.Default.QrCodeScanner, "QR Shield", "Inspect QR codes", true),
        ShieldModule("msg", Icons.Default.Chat, "Message Shield", "Detect SMS scams", true),
        ShieldModule("num", Icons.Default.Phone, "Number Shield", "Caller reputation", true),
        ShieldModule("web", Icons.Default.Language, "Web Shield", "Safe browsing guard", true),
        ShieldModule("intel", Icons.Default.Psychology, "Threat Intel", "Engine & local rules", true)
    )
    val byId = modules.associateBy { it.id }
    val link = byId.getValue("link")
    val qr = byId.getValue("qr")
    val msg = byId.getValue("msg")
    val num = byId.getValue("num")
    val web = byId.getValue("web")
    val intel = byId.getValue("intel")
    val armed = modules.count { it.isReady }

    val bgTransition = rememberInfiniteTransition()
    val drift = bgTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Ambient background: purple radial glows, faint grid, slow scan line
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(XPAccent.copy(alpha = 0.38f), XPAccent.copy(alpha = 0f)),
                    center = Offset(size.width * 0.95f, -size.height * 0.02f),
                    radius = size.width * 1.1f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(XPAccentBright.copy(alpha = 0.14f), XPAccentBright.copy(alpha = 0f)),
                    center = Offset(0f, size.height * 0.9f),
                    radius = size.width
                )
            )
            val step = 36.dp.toPx()
            var gx = 0f
            while (gx < size.width) {
                drawLine(Color.White.copy(alpha = 0.025f), Offset(gx, 0f), Offset(gx, size.height), 1f)
                gx += step
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(Color.White.copy(alpha = 0.025f), Offset(0f, gy), Offset(size.width, gy), 1f)
                gy += step
            }
            val band = 60.dp.toPx()
            val sy = size.height * drift.value
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        XPAccentBright.copy(alpha = 0f),
                        XPAccentBright.copy(alpha = 0.10f),
                        XPAccentBright.copy(alpha = 0f)
                    ),
                    startY = sy - band,
                    endY = sy + band
                ),
                topLeft = Offset(0f, sy - band),
                size = Size(size.width, band * 2f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp)
        ) {
            Reveal(0) { HubHeader() }

            Spacer(modifier = Modifier.height(18.dp))

            Reveal(120) { HeroStatusCard(modules) }

            Spacer(modifier = Modifier.height(26.dp))

            Reveal(240) { SectionHeader(title = "SHIELD MODULES", trailing = "$armed/${modules.size} ARMED") }

            Spacer(modifier = Modifier.height(12.dp))

            Reveal(300) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Row 1 — tall featured tile + two stacked compact tiles
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(212.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeroModuleTile(
                            module = link,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onModuleClick(link) }
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CompactModuleTile(
                                module = qr,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                onClick = { onModuleClick(qr) }
                            )
                            CompactModuleTile(
                                module = msg,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                onClick = { onModuleClick(msg) }
                            )
                        }
                    }

                    // Row 2 — two equal tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ShieldModuleCard(
                            module = num,
                            modifier = Modifier.weight(1f),
                            onClick = { onModuleClick(num) }
                        )
                        ShieldModuleCard(
                            module = web,
                            modifier = Modifier.weight(1f),
                            onClick = { onModuleClick(web) }
                        )
                    }

                    // Row 3 — full-width engine banner
                    WideModuleTile(
                        module = intel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp),
                        onClick = { onModuleClick(intel) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Reveal(420) { ShareTipCard() }

            AnimatedVisibility(
                visible = selectedLockedModule != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(XPAccent.copy(alpha = 0.15f))
                        .border(1.dp, XPAccent, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "⚡ ${selectedLockedModule ?: ""} is coming next! Tap active modules to scan.",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Entrance animation
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun Reveal(delayMs: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(500)) +
            slideInVertically(animationSpec = tween(500), initialOffsetY = { it / 10 })
    ) {
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HubHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconOrb(icon = Icons.Default.Shield, tint = XPAccentBright, orbSize = 42.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "XPTool",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "SECURITY COMMAND HUB",
                    color = XPAccentBright,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(XPAccent.copy(alpha = 0.18f))
                .border(1.dp, XPAccent.copy(alpha = 0.8f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulseDot(color = XPSafe, dotSize = 9.dp)
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "Release 1.0",
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.5.sp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(XPAccent, XPBorder, XPAccent.copy(alpha = 0f))
                    )
                )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = trailing,
            color = XPSafe,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero: live security status
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HeroStatusCard(modules: List<ShieldModule>) {
    // Live counters. If your ThreatIntelStore exposes these differently,
    // this is the only place that needs to change.
    val scams = rememberStatValue(ThreatIntelStore.scamsBlocked)
    val links = rememberStatValue(ThreatIntelStore.linksUnmasked)
    val trackers = rememberStatValue(ThreatIntelStore.trackersKilled)

    val armed = modules.count { it.isReady }
    val (statusText, statusColor) = when {
        armed == modules.size -> "PROTECTED" to XPSafe
        armed > 0 -> "PARTIAL" to XPWarning
        else -> "OFFLINE" to XPDanger
    }
    val shape = RoundedCornerShape(28.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1E1538), XPCard, XPDeep)))
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(XPAccent.copy(alpha = 0.32f), XPAccent.copy(alpha = 0f)),
                        center = Offset(size.width * 0.18f, size.height * 0.25f),
                        radius = size.width * 0.75f
                    )
                )
            }
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(XPAccentBright.copy(alpha = 0.75f), XPBorder, statusColor.copy(alpha = 0.35f))
                ),
                shape
            )
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadarScope(color = statusColor, modifier = Modifier.size(104.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulseDot(color = statusColor, dotSize = 9.dp)
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "SECURITY STATUS",
                        color = statusColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "LookSafe engine online. Share any link to scan it instantly.",
                    color = XPMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    modules.forEach { m ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (m.isReady) XPSafe else XPBorder)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "$armed/${modules.size} shields armed",
                    color = XPMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(XPAccent.copy(alpha = 0f), XPAccentBright.copy(alpha = 0.6f), XPAccent.copy(alpha = 0f))
                    )
                )
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatCell(
                label = "SCAMS\nBLOCKED",
                rawValue = scams,
                color = XPDanger,
                icon = Icons.Default.Block,
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.width(1.dp).height(46.dp).background(XPBorder))
            StatCell(
                label = "LINKS\nUNMASKED",
                rawValue = links,
                color = XPAccentBright,
                icon = Icons.Default.Link,
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.width(1.dp).height(46.dp).background(XPBorder))
            StatCell(
                label = "TRACKERS\nKILLED",
                rawValue = trackers,
                color = XPWarning,
                icon = Icons.Default.VisibilityOff,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    rawValue: Long,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val target = if (started) rawValue.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt() else 0
    val shown by animateIntAsState(
        targetValue = target,
        animationSpec = tween(1100, easing = FastOutSlowInEasing),
    )

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = formatCount(shown),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = XPMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 100_000 -> "${n / 1_000}K"
    else -> n.toString()
}

/**
 * Reads a ThreatIntelStore counter whether it is a plain number, a Compose State,
 * or a StateFlow, so the hub compiles and updates with any of those shapes.
 */
@Composable
private fun rememberStatValue(source: Any?): Long = when (source) {
    is Number -> source.toLong()
    is State<*> -> (source.value as? Number)?.toLong() ?: 0L
    is StateFlow<*> -> (source.collectAsState().value as? Number)?.toLong() ?: 0L
    else -> source?.toString()?.toLongOrNull() ?: 0L
}

// ─────────────────────────────────────────────────────────────────────────────
// Radar + pulse primitives
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RadarScope(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    val sweep = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)),
    )
    val pulse = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing)),
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f

            drawCircle(color = XPDeep.copy(alpha = 0.7f), radius = r, center = c)

            for (i in 1..3) {
                drawCircle(
                    color = XPAccentBright.copy(alpha = if (i == 3) 0.45f else 0.2f),
                    radius = r * i / 3f,
                    center = c,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            drawLine(XPAccentBright.copy(alpha = 0.18f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(XPAccentBright.copy(alpha = 0.18f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())

            // Expanding engine pulse
            val p = pulse.value
            drawCircle(
                color = color.copy(alpha = (1f - p) * 0.5f),
                radius = r * (0.3f + 0.7f * p),
                center = c,
                style = Stroke(width = 2.dp.toPx())
            )

            // Rotating sweep
            rotate(degrees = sweep.value, pivot = c) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0f to color.copy(alpha = 0f),
                        0.72f to color.copy(alpha = 0f),
                        1f to color.copy(alpha = 0.5f),
                        center = c
                    ),
                    radius = r,
                    center = c
                )
                drawLine(color.copy(alpha = 0.95f), c, Offset(c.x + r, c.y), 1.5.dp.toPx())
            }

            // Blips light up right after the sweep passes them
            val blips = listOf(
                Pair(35f, 0.62f),
                Pair(140f, 0.40f),
                Pair(215f, 0.78f),
                Pair(300f, 0.52f)
            )
            blips.forEach { (angle, dist) ->
                val diff = ((sweep.value - angle) % 360f + 360f) % 360f
                val a = (1f - diff / 300f).coerceIn(0.08f, 1f)
                val rad = angle * (TWO_PI / 360f)
                val pos = Offset(c.x + cos(rad) * r * dist, c.y + sin(rad) * r * dist)
                drawCircle(color.copy(alpha = a * 0.35f), radius = 5.dp.toPx(), center = pos)
                drawCircle(color.copy(alpha = a), radius = 2.dp.toPx(), center = pos)
            }
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(XPCard.copy(alpha = 0.9f))
                .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PulseDot(color: Color, modifier: Modifier = Modifier, dotSize: Dp = 10.dp) {
    val transition = rememberInfiniteTransition()
    val p = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing)),
    )
    Canvas(modifier = modifier.size(dotSize)) {
        val r = size.minDimension / 2f
        drawCircle(color = color.copy(alpha = (1f - p.value) * 0.55f), radius = r * (0.45f + 0.55f * p.value))
        drawCircle(color = color, radius = r * 0.42f)
    }
}

@Composable
private fun IconOrb(
    icon: ImageVector,
    tint: Color,
    orbSize: Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(orbSize * 0.32f)
    Box(
        modifier = modifier
            .size(orbSize)
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.38f), tint.copy(alpha = 0.08f))))
            .border(1.dp, tint.copy(alpha = 0.5f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(orbSize * 0.5f)
        )
    }
}

@Composable
private fun StatusPill(ready: Boolean, tint: Color) {
    val c = if (ready) XPSafe else XPMuted
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.12f))
            .border(1.dp, c.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (ready) {
            PulseDot(color = XPSafe, dotSize = 8.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tint.copy(alpha = 0.4f))
            )
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = if (ready) "ACTIVE" else "SOON",
            color = c,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Glass tile base: press scale, glow, gradient border
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GlassTile(
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
    )
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(160),
    )
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = if (enabled) 1f else 0.55f
            }
            .clip(shape)
            .background(Brush.linearGradient(listOf(XPGlassTop, XPCard, XPDeep)))
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.20f + 0.30f * glow),
                            tint.copy(alpha = 0f)
                        ),
                        center = Offset(size.width * 0.12f, size.height * 0.05f),
                        radius = size.maxDimension * 0.85f
                    )
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(tint.copy(alpha = 0f), tint.copy(alpha = 0.65f), tint.copy(alpha = 0f))
                    ),
                    start = Offset(size.width * 0.15f, 0.5f),
                    end = Offset(size.width * 0.85f, 0.5f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(tint.copy(alpha = 0.55f + 0.45f * glow), XPBorder, tint.copy(alpha = 0.15f))
                ),
                shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        content = content
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Module tiles (bento variants)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HeroModuleTile(
    module: ShieldModule,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = tintFor(module.id)
    val transition = rememberInfiniteTransition()
    val spin = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
    )

    GlassTile(tint = tint, modifier = modifier, enabled = module.isReady, onClick = onClick) {
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(140.dp)
                .offset(x = 40.dp, y = 40.dp)
        ) {
            val c = center
            val fractions = listOf(0.38f, 0.62f, 0.86f)
            fractions.forEachIndexed { i, f ->
                val dir = if (i % 2 == 0) 1f else -1f
                rotate(degrees = spin.value * dir, pivot = c) {
                    val r = size.minDimension / 2f * f
                    drawArc(
                        color = tint.copy(alpha = 0.30f - i * 0.07f),
                        startAngle = 20f,
                        sweepAngle = 250f,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                IconOrb(icon = module.icon, tint = tint, orbSize = 48.dp)
                Text(
                    text = "PRIMARY",
                    color = tint,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Column {
                StatusPill(ready = module.isReady, tint = tint)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = module.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = module.subtitle,
                    color = XPMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "LAUNCH  ›",
                    color = tint,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

@Composable
private fun CompactModuleTile(
    module: ShieldModule,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = tintFor(module.id)
    GlassTile(tint = tint, modifier = modifier, enabled = module.isReady, onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconOrb(icon = module.icon, tint = tint, orbSize = 30.dp)
                if (module.isReady) {
                    PulseDot(color = XPSafe, dotSize = 9.dp)
                } else {
                    Text(
                        text = "SOON",
                        color = XPMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column {
                Text(
                    text = module.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = module.subtitle,
                    color = XPMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Standard bento tile. Kept public with its original signature. */
@Composable
fun ShieldModuleCard(
    module: ShieldModule,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = tintFor(module.id)
    GlassTile(
        tint = tint,
        modifier = modifier.height(132.dp),
        enabled = module.isReady,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                IconOrb(icon = module.icon, tint = tint, orbSize = 40.dp)
                StatusPill(ready = module.isReady, tint = tint)
            }
            Column {
                Text(
                    text = module.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = module.subtitle,
                    color = XPMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WideModuleTile(
    module: ShieldModule,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = tintFor(module.id)
    val transition = rememberInfiniteTransition()
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
    )

    GlassTile(tint = tint, modifier = modifier, enabled = module.isReady, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconOrb(icon = module.icon, tint = tint, orbSize = 46.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = module.subtitle,
                    color = XPMuted,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatusPill(ready = module.isReady, tint = tint)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Canvas(
                modifier = Modifier
                    .width(88.dp)
                    .height(48.dp)
            ) {
                val w = size.width
                val h = size.height
                val mid = h / 2f
                val step = 2.dp.toPx()
                for (layer in 0..1) {
                    val amp = h * (if (layer == 0) 0.40f else 0.22f)
                    val freq = if (layer == 0) 2.2f else 3.6f
                    val dir = if (layer == 0) 1f else -1f
                    val path = Path()
                    path.moveTo(0f, mid)
                    var x = 0f
                    while (x <= w) {
                        val nx = x / w
                        val envelope: Float = sin(nx * TWO_PI / 2f)
                        val arg: Float = (nx * freq + phase.value * dir * 2f) * TWO_PI
                        val y: Float = mid + amp * envelope * sin(arg)
                        path.lineTo(x, y)
                        x += step
                    }
                    drawPath(
                        path = path,
                        color = tint.copy(alpha = if (layer == 0) 0.95f else 0.4f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Footer tip
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ShareTipCard() {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, XPBorder, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconOrb(icon = Icons.Default.Bolt, tint = XPWarning, orbSize = 36.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "Scan from anywhere",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Share a link from WhatsApp, SMS or your browser to XPTool and Link Shield opens right away.",
                color = XPMuted,
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}

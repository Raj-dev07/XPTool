package com.xptool.security

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.xptool.security.core.url.NormalizedUrl

// Colors
val XPBackground = Color(0xFF090B10)
val XPCard = Color(0xFF13111C)
val XPAccent = Color(0xFF7000FF)
val XPAccentBright = Color(0xFF9D4DFF)
val XPBorder = Color(0xFF2A1B4E)
val XPSafe = Color(0xFF00E676)
val XPWarning = Color(0xFFFFB300)
val XPDanger = Color(0xFFFF5252)

// Models
data class FullScanReport(
    val originalUrl: String,
    val finalUrl: NormalizedUrl,
    val redirectChain: List<String>,
    val verdict: String,
    val reasons: List<String>
)

data class ShieldModule(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val isReady: Boolean
)

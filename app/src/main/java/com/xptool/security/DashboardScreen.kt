package com.xptool.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        ShieldModule("web", Icons.Default.Language, "Web Shield", "Safe browsing guard", false),
        ShieldModule("intel", Icons.Default.Psychology, "Threat Intel", "LookSafe engine rules", false)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("XPTool", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(
                    text = "YOUR DIGITAL SECURITY SHIELD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = XPAccentBright,
                    letterSpacing = 1.2.sp
                )
            }

            Surface(
                color = XPAccent.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, XPAccent)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(XPDanger))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ALPHA 0.1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Brush.horizontalGradient(listOf(XPAccent, XPAccentBright, Color.Transparent)))
        )

        Spacer(modifier = Modifier.height(22.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = XPCard),
            border = BorderStroke(1.dp, XPBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(XPAccent.copy(alpha = 0.25f), XPCard)))
                    .padding(20.dp)
            ) {
                Text("● LOOKSAFE ENGINE ACTIVE", color = XPSafe, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Stay Protected Against Phishing & Spoofed Links.",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 27.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select a security module below or share any suspicious link directly to XPTool.",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        modules.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                rowItems.forEach { item ->
                    ShieldModuleCard(
                        module = item,
                        modifier = Modifier.weight(1f),
                        onClick = { onModuleClick(item) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (selectedLockedModule != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = XPAccent.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, XPAccent)
            ) {
                Text(
                    text = "⚡ $selectedLockedModule is coming next! Tap active modules to scan.",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun ShieldModuleCard(
    module: ShieldModule,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (module.isReady) XPAccent else XPBorder
    val badgeColor = if (module.isReady) XPAccent else Color(0xFF232030)

    Card(
        modifier = modifier
            .height(142.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = XPCard),
        border = BorderStroke(if (module.isReady) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(XPAccent.copy(alpha = if (module.isReady) 0.25f else 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = module.icon,
                        contentDescription = module.title,
                        tint = if (module.isReady) XPAccentBright else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Surface(color = badgeColor, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = if (module.isReady) "ACTIVE" else "SOON",
                        color = if (module.isReady) Color.White else Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Column {
                Text(module.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(module.subtitle, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

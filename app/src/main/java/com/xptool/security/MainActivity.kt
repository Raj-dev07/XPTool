package com.xptool.security

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    private var sharedTextState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingShare(intent)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = XPAccent,
                    background = XPBackground,
                    surface = XPCard,
                    onPrimary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                XPToolApp(
                    incomingSharedText = sharedTextState,
                    onShareConsumed = { sharedTextState = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShare(intent)
    }

    private fun handleIncomingShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                sharedTextState = LookSafeEngine.extractUrlFromText(sharedText)
            }
        }
    }
}

@Composable
fun XPToolApp(
    incomingSharedText: String? = null,
    onShareConsumed: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf("HUB") }
    var selectedLockedModule by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(incomingSharedText) {
        if (!incomingSharedText.isNullOrBlank()) {
            currentScreen = "link"
        }
    }

    BackHandler(enabled = currentScreen != "HUB") {
        currentScreen = "HUB"
    }

    Surface(modifier = Modifier.fillMaxSize(), color = XPBackground) {
        when (currentScreen) {
            "HUB" -> DashboardHubScreen(
                selectedLockedModule = selectedLockedModule,
                onModuleClick = { module ->
                    if (module.isReady) {
                        selectedLockedModule = null
                        currentScreen = module.id
                    } else {
                        selectedLockedModule = module.title
                    }
                }
            )
            "link" -> LinkShieldScreen(
                incomingSharedText = incomingSharedText,
                onShareConsumed = onShareConsumed,
                onBackToHub = { currentScreen = "HUB" }
            )
            "qr" -> QrShieldScreen(
                onBackToHub = { currentScreen = "HUB" }
            )
            "msg" -> MessageShieldScreen(
                onBackToHub = { currentScreen = "HUB" }
            )
            "num" -> NumberShieldScreen(
                onBackToHub = { currentScreen = "HUB" }
            )
        }
    }
}

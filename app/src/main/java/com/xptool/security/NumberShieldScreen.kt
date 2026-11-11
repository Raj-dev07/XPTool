package com.xptool.security

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
// Remove this:
// import androidx.compose.material.icons.automirrored.filled.ArrowBack

// Add this instead:
import androidx.compose.material.icons.filled.ArrowBack

@Composable
fun NumberShieldScreen(onBackToHub: () -> Unit) {
    val context = LocalContext.current
    var isRoleHeld by remember { mutableStateOf(false) }

    // Check if we are already the default Caller ID app
    fun checkRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            isRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
    }

    // Listen for lifecycle changes (to update UI when user returns from Settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkRole()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launcher for the System Role Request Dialog
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            checkRole()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(XPBackground)
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back Button Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToHub) {
                Icon(
                    // Change this:
                    // imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    
                    // To this:
                    imageVector = Icons.Default.ArrowBack,
                    
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Number Shield",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Protect against verified spam and spoofed callers.",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Status Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isRoleHeld) XPCard else Color(0xFF1F0A0A),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = if (isRoleHeld) "🟢 ENGINE ACTIVE" else "🔴 ENGINE INACTIVE",
                    color = if (isRoleHeld) XPSafe else XPDanger,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isRoleHeld) "XPTool is currently screening calls for spoofing and spam."
                           else "XPTool needs to be set as your default Caller ID & Spam app to intercept calls.",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action Button
        if (!isRoleHeld) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                        roleLauncher.launch(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = XPAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Enable Call Screening", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isRoleHeld) {
            Text(
                text = "When a call comes in, the system checks the carrier's STIR/SHAKEN verification. If failed, the call is dropped and a cyberpunk overlay alert will appear.",
                color = Color.DarkGray,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
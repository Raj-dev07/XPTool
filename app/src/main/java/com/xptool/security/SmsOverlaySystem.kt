package com.xptool.security

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.provider.Telephony
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 1. BACKGROUND SMS INTERCEPTOR
class SmsGuardReceiver : BroadcastReceiver() {
    // ... [Keep your existing SmsGuardReceiver code unchanged] ...
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return
        val sender = messages.firstOrNull()?.originatingAddress ?: "Unknown Sender"
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }
        if (fullBody.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val report = runMessageHeuristics(fullBody)
                withContext(Dispatchers.Main) {
                    if (report.riskScore >= 65 && Settings.canDrawOverlays(context)) {
                        OverlayAlertService.showOverlay(
                            context = context,
                            sender = sender,
                            score = report.riskScore,
                            category = report.detectedCategory,
                            flag = report.redFlags.firstOrNull() ?: "High scam probability detected.",
                            preview = fullBody,
                            isCall = false // SMS flag
                        )
                    } else {
                        SmsNotificationHelper.sendQuietNotification(context, sender, report)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

// 2. QUIET NOTIFICATION HELPER (Keep as is)
object SmsNotificationHelper {
    // ... [Keep your existing SmsNotificationHelper code unchanged] ...
    private const val CHANNEL_ID = "xptool_sms_guard"
    fun sendQuietNotification(context: Context, sender: String, report: MessageScanReport) {
        // ... existing code ...
    }
}

// 3. MODERN CYBERPUNK FLOATING OVERLAY SERVICE (Updated for Calls)
class OverlayAlertService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val sender = intent.getStringExtra("SENDER") ?: "Unknown"
        val score = intent.getIntExtra("SCORE", 85)
        val category = intent.getStringExtra("CATEGORY") ?: "Phishing Alert"
        val flag = intent.getStringExtra("FLAG") ?: "Dangerous scam pattern detected!"
        val preview = intent.getStringExtra("PREVIEW") ?: ""
        val isCall = intent.getBooleanExtra("IS_CALL", false)

        showFloatingPanel(sender, score, category, flag, preview, isCall)
        return START_NOT_STICKY
    }

    private fun showFloatingPanel(
        sender: String,
        score: Int,
        category: String,
        flag: String,
        preview: String,
        isCall: Boolean
    ) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        removeOverlay()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        fun Int.dp(): Int = (this * density).toInt()

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 8.dp(), 14.dp(), 14.dp())
        }

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
            elevation = 20.dp().toFloat()
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#1A122B"), Color.parseColor("#0D0B14"), Color.parseColor("#09080E"))
            ).apply {
                cornerRadius = 24.dp().toFloat()
                setStroke(1.dp(), Color.parseColor("#207000FF"))
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Dynamic Badge based on SMS or Call
        val threatBadge = TextView(this).apply {
            text = if (isCall) "📞 CALL SHIELD • BLOCKED" else "● SCAM SHIELD • $score% RISK"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 10.5f
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.06f
            setPadding(12.dp(), 5.dp(), 12.dp(), 5.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26FF5252"))
                cornerRadius = 50.dp().toFloat()
                setStroke(1.dp(), Color.parseColor("#66FF5252"))
            }
        }

        val spacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(32.dp(), 32.dp())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#251D3A"))
                setStroke(1.dp(), Color.parseColor("#4A3578"))
            }
            setOnClickListener {
                removeOverlay()
                stopSelf()
            }
        }

        topRow.addView(threatBadge)
        topRow.addView(spacer)
        topRow.addView(closeBtn)

        val senderView = TextView(this).apply {
            text = if (isCall) "Incoming Call: $sender" else sender
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(Typeface.SANS_SERIF, Typeface.BOLD)
            setPadding(0, 10.dp(), 0, 2.dp())
        }

        val flagView = TextView(this).apply {
            text = "$category — $flag"
            setTextColor(Color.parseColor("#B884FF"))
            textSize = 12f
            setTypeface(Typeface.SANS_SERIF, Typeface.NORMAL)
            setPadding(0, 0, 0, 10.dp())
        }

        val snippetBox = TextView(this).apply {
            val shortText = if (preview.length > 95) preview.take(95) + "..." else preview
            text = shortText
            setTextColor(Color.parseColor("#B0B3C6"))
            textSize = 11.5f
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#14111F"))
                cornerRadius = 14.dp().toFloat()
                setStroke(1.dp(), Color.parseColor("#231D36"))
            }
        }

        cardLayout.addView(topRow)
        cardLayout.addView(senderView)
        cardLayout.addView(flagView)
        if (preview.isNotEmpty()) cardLayout.addView(snippetBox)
        wrapper.addView(cardLayout)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 14.dp()
        }

        overlayView = wrapper
        windowManager?.addView(overlayView, params)
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        fun showOverlay(
            context: Context,
            sender: String,
            score: Int,
            category: String,
            flag: String,
            preview: String,
            isCall: Boolean = false // New Parameter
        ) {
            val intent = Intent(context, OverlayAlertService::class.java).apply {
                putExtra("SENDER", sender)
                putExtra("SCORE", score)
                putExtra("CATEGORY", category)
                putExtra("FLAG", flag)
                putExtra("PREVIEW", preview)
                putExtra("IS_CALL", isCall)
            }
            context.startService(intent)
        }
    }
}
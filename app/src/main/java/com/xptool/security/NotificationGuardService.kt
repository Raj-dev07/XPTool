package com.xptool.security

import android.app.Notification
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationGuardService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val targetApps = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "com.instagram.android" to "Instagram",
        // Naye SMS Interceptors (Bina SMS Permission ke!)
        "com.google.android.apps.messaging" to "Google Messages",
        "com.samsung.android.messaging" to "Samsung Messages"
    )


    private var lastProcessedSignature = ""

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val appLabel = targetApps[sbn.packageName] ?: return

        // Ignore summary group headers (e.g. "2 new messages")
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = sbn.notification.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Unknown Sender"
        val messageText = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()

        if (messageText.isNullOrBlank()) return

        // Prevent duplicate popup for the exact same notification update
        val signature = "${sbn.packageName}|$sender|$messageText"
        if (signature == lastProcessedSignature) return
        lastProcessedSignature = signature

        // Ensure persistent store is initialized even if app UI isn't open
        ThreatIntelStore.init(applicationContext)

        serviceScope.launch {
            val report = runMessageHeuristics(messageText)

            // Update persistent telemetry counters
            ThreatIntelStore.addSmsInspected()
            if (report.extractedUrl != null) {
                ThreatIntelStore.addLink()
            }

            if (report.riskScore >= 65) {
                ThreatIntelStore.addScam()
                if (report.extractedUrl != null) {
                    ThreatIntelStore.addPhishing()
                }

                if (Settings.canDrawOverlays(applicationContext)) {
                    OverlayAlertService.showOverlay(
                        context = applicationContext,
                        sender = "$sender ($appLabel)",
                        score = report.riskScore,
                        category = report.detectedCategory,
                        flag = report.redFlags.firstOrNull() ?: "High-risk phishing pattern detected!",
                        preview = messageText
                    )
                } else {
                    SmsNotificationHelper.sendQuietNotification(applicationContext, "$sender ($appLabel)", report)
                }
            } else if (report.riskScore >= 30) {
                SmsNotificationHelper.sendQuietNotification(applicationContext, "$sender ($appLabel)", report)
            }
        }
    }
}

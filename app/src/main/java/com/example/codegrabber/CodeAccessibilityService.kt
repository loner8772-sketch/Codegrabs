package com.example.codegrabber

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat

class CodeAccessibilityService : AccessibilityService() {

    companion object {
        private const val CHANNEL_ID = "code_grabber_channel"
        private const val CHANNEL_NAME = "Code Grabber"

        private val PIPE_CODE_REGEX =
            Regex("""[A-Z0-9]{3,6}(?:\s*\|\s*[A-Z0-9]{3,6}){1,5}""")

        private val LABELED_CODE_REGEX =
            Regex("""CODE\s*:\s*\n?\s*([A-Z0-9|\s\-]{6,40})""", RegexOption.IGNORE_CASE)

        private val PLAIN_CODE_REGEX =
            Regex("""\b(?=[A-Z0-9]{9,18}\b)(?=[A-Z0-9]*[A-Z])(?=[A-Z0-9]*[0-9])[A-Z0-9]{9,18}\b""")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannelIfNeeded()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") return

        val fullText = event.text?.joinToString("\n") { it.toString() } ?: return
        if (fullText.isBlank()) return

        val code = extractCode(fullText) ?: return
        copyToClipboard(code)
        showConfirmation(code)
        launchRedirectCountdown(code)
    }

    override fun onInterrupt() {
        // No-op: nothing to clean up when the service is interrupted.
    }

    private fun launchRedirectCountdown(code: String) {
        val intent = Intent(this, RedirectCountdownActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(RedirectCountdownActivity.EXTRA_CODE, code)
            putExtra(RedirectCountdownActivity.EXTRA_URL, "https://reward.ff.garena.com/en")
        }
        startActivity(intent)
    }

    private fun extractCode(source: String): String? {
        val upper = source.uppercase()

        LABELED_CODE_REGEX.find(upper)?.let {
            return normalize(it.groupValues[1])
        }
        PIPE_CODE_REGEX.find(upper)?.let {
            return normalize(it.value)
        }
        PLAIN_CODE_REGEX.find(upper)?.let {
            return normalize(it.value)
        }
        return null
    }

    private fun normalize(raw: String): String =
        raw.replace("|", "").replace(Regex("""\s+"""), "").trim()

    private fun copyToClipboard(code: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Redeem code", code))
    }

    private fun showConfirmation(code: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Code copied")
            .setContentText(code)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, notification)
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

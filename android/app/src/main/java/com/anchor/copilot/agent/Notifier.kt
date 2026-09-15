package com.anchor.copilot.agent

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anchor.copilot.MainActivity
import com.anchor.copilot.R

object Notifier {
    const val CH_STATUS = "anchor_status"
    const val CH_CHECKIN = "anchor_checkins"
    const val CH_FAMILY = "anchor_family"
    const val ID_STATUS = 1
    private const val ID_TRANSITION = 2
    private const val COLOR = 0xFF3B6E71.toInt()

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_STATUS, "Anchor is on", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Always visible while Anchor shares check-ins and a screen-time summary with your family."
                    setShowBadge(false)
                },
                NotificationChannel(CH_CHECKIN, "Transition checks", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "One quick emoji tap right after a screen session ends."
                },
                NotificationChannel(CH_FAMILY, "Family", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Kind words, family goals and patterns worth a conversation."
                },
            ),
        )
    }

    private fun canPost(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, requestCode: Int, configure: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).apply(configure)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * The transparency notification. Google Play requires monitoring apps to show a persistent
     * notification; Anchor also uses it to remind the child what is (and isn't) shared.
     */
    fun statusNotification(context: Context, familyNames: String, paused: Boolean): Notification {
        val text = if (paused) "Sharing is paused. Your family can see that it's paused."
        else "Sharing check-ins and a screen-time summary with $familyNames. Tap to see exactly what they see."
        return NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_anchor)
            .setContentTitle(if (paused) "Anchor · sharing paused" else "Anchor is on ⚓")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nNever shared: camera, microphone, messages, what you type or what's on your screen."))
            .setOngoing(true)
            .setSilent(true)
            .setColor(COLOR)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context, 10) { putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_PRIVACY) })
            .build()
    }

    fun transitionPrompt(context: Context, app: String, category: String, sessionMin: Int) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_CHECKIN)
            .setSmallIcon(R.drawable.ic_stat_anchor)
            .setContentTitle("Quick check: how do you feel right now?")
            .setContentText("Just one tap. No camera, no mic.")
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .setColor(COLOR)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context, 20) {
                putExtra(MainActivity.EXTRA_TRANSITION_APP, app)
                putExtra(MainActivity.EXTRA_TRANSITION_CATEGORY, category)
                putExtra(MainActivity.EXTRA_TRANSITION_MINUTES, sessionMin)
            })
            .build()
        NotificationManagerCompat.from(context).notify(ID_TRANSITION, n)
    }

    fun family(context: Context, key: String, title: String, text: String, tab: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CH_FAMILY)
            .setSmallIcon(R.drawable.ic_stat_anchor)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setColor(COLOR)
            .setContentIntent(openApp(context, 30 + (key.hashCode() and 0xFF)) { putExtra(MainActivity.EXTRA_TAB, tab) })
            .build()
        NotificationManagerCompat.from(context).notify(1000 + (key.hashCode() and 0xFFFF), n)
    }
}

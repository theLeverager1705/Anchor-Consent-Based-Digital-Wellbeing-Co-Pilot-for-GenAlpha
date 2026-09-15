package com.anchor.copilot.agent

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.anchor.copilot.data.AnchorRepository
import com.anchor.copilot.data.Role
import com.anchor.copilot.data.Transport
import com.anchor.copilot.data.UsageCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs on the child's device only after consent. It does three small things:
 *  1. keeps the "Anchor is on" disclosure visible,
 *  2. notices when a screen session ends and asks a one-tap Transition Check ~60–90 s later,
 *  3. shares the day's screen-time summary every few minutes (if the child allows it).
 */
class CompanionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = AnchorRepository.get(this)
        if (!shouldRun(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val fam = repo.currentFamily()
        val names = fam.guardians.joinToString(" & ") { it.name }.ifBlank { "your family" }
        ServiceCompat.startForeground(
            this, Notifier.ID_STATUS,
            Notifier.statusNotification(this, names, fam.sharing.paused),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (loop?.isActive != true) loop = scope.launch { run(repo) }
        return START_STICKY
    }

    private suspend fun run(repo: AnchorRepository) {
        val prefs = getSharedPreferences("companion", MODE_PRIVATE)
        var lastSnapshotAt = 0L
        while (scope.isActive) {
            if (!shouldRun(this)) {
                stopSelf()
                return
            }
            val fam = repo.currentFamily()
            val demo = repo.profile.value.transport == Transport.LOCAL_DEMO

            // Transition Check: a session that ended 45 s – 10 min ago and hasn't been asked about yet.
            if (fam.sharing.transitions && !fam.sharing.paused) {
                val ended = UsageCollector.lastEndedSession(this, minMinutes = if (demo) 1 else 10)
                val lastAsked = prefs.getLong("lastAskedEnd", 0L)
                if (ended != null && ended.end > lastAsked && System.currentTimeMillis() - ended.end < 10 * 60_000L &&
                    (demo || ended.category.isHighPull)
                ) {
                    val wait = 75_000L - (System.currentTimeMillis() - ended.end)
                    if (wait > 0) delay(wait)
                    Notifier.transitionPrompt(this, ended.label, ended.category.name, ended.minutes)
                    prefs.edit().putLong("lastAskedEnd", ended.end).apply()
                }
            }

            // Screen-time summary every 5 minutes.
            if (System.currentTimeMillis() - lastSnapshotAt > 5 * 60_000L) {
                AgentActions.shareSnapshot(this, repo)
                lastSnapshotAt = System.currentTimeMillis()
            }
            delay(20_000L)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Only a consented child device (or the one-phone demo in Kid Mode) runs the companion. */
        fun shouldRun(context: Context): Boolean {
            val repo = AnchorRepository.get(context)
            val p = repo.profile.value
            if (!p.onboarded) return false
            val isChild = p.role == Role.CHILD || (p.transport == Transport.LOCAL_DEMO && UsageCollector.hasUsageAccess(context))
            return isChild && repo.currentFamily().consented
        }

        fun start(context: Context) {
            if (!shouldRun(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, CompanionService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CompanionService::class.java))
        }
    }
}

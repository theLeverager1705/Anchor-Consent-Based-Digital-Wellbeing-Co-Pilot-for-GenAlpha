package com.anchor.copilot.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anchor.copilot.MainActivity
import com.anchor.copilot.data.AnchorRepository
import com.anchor.copilot.data.EventType
import com.anchor.copilot.data.Kudos
import com.anchor.copilot.data.Role
import com.anchor.copilot.data.Transport
import com.anchor.copilot.data.UsageCollector
import com.anchor.copilot.data.decode
import com.anchor.copilot.data.toPayload
import java.util.concurrent.TimeUnit

object AgentActions {

    /** Collects today's summary and shares it, respecting every sharing choice the child made. */
    fun shareSnapshot(context: Context, repo: AnchorRepository) {
        val p = repo.profile.value
        val fam = repo.currentFamily()
        val childDevice = p.role == Role.CHILD || p.transport == Transport.LOCAL_DEMO
        if (!childDevice || !fam.consented || fam.sharing.paused || !fam.sharing.screenTime) return
        val snap = UsageCollector.collectToday(context, fam.pact, fam.sharing) ?: return
        if (snap.totalMinutes == 0 && p.transport == Transport.LOCAL_DEMO) return // keep sample data until real usage exists
        repo.emitRaw(EventType.SNAPSHOT, snap.toPayload(), Role.CHILD)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("anchor-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Periodic relay sync plus gentle family notifications for things that came from the other side. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = AnchorRepository.get(applicationContext)
        val p = repo.profile.value
        if (!p.onboarded) return Result.success()
        AgentActions.shareSnapshot(applicationContext, repo)
        repo.sync()
        if (p.transport == Transport.RELAY) notifyNew(repo)
        return Result.success()
    }

    private fun notifyNew(repo: AnchorRepository) {
        val p = repo.profile.value
        val fresh = repo.events.value.filter { it.ts > p.lastNotifiedTs && it.from != p.role }
        if (fresh.isEmpty()) return
        val ctx = applicationContext
        fresh.forEach { e ->
            when {
                p.role != Role.CHILD && e.type == EventType.FLAG ->
                    Notifier.family(ctx, e.id, "${e.fromName} reached out 🛟", "Something bothered them. Open Anchor for a calm way to start the conversation.", MainActivity.TAB_DIGEST)
                p.role != Role.CHILD && e.type == EventType.SHARING_CHANGED ->
                    Notifier.family(ctx, e.id, "Sharing settings changed", "${e.fromName} updated what they share. You can see the details in Family.", MainActivity.TAB_FAMILY)
                p.role == Role.CHILD && e.type == EventType.KUDOS ->
                    Notifier.family(ctx, e.id, "💌 ${e.fromName}", e.decode<Kudos>()?.message ?: "sent you a kind word", MainActivity.TAB_HOME)
                p.role == Role.CHILD && e.type == EventType.PACT_PROPOSED ->
                    Notifier.family(ctx, e.id, "New family goals to look at", "${e.fromName} suggested goals. Nothing changes until you agree.", MainActivity.TAB_PRIVACY)
            }
        }
        repo.updateProfile { it.copy(lastNotifiedTs = fresh.maxOf { e -> e.ts }) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AgentActions.schedule(context)
            CompanionService.start(context)
        }
    }
}

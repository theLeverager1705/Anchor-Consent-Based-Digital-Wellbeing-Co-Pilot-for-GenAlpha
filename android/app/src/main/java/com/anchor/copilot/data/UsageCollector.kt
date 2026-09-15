package com.anchor.copilot.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import com.anchor.copilot.BuildConfig
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Turns Android's usage events into a privacy-preserving daily summary.
 * Only app names, categories and minutes are computed. Nothing about what happens inside an app
 * (messages, videos, keystrokes, screens) is ever read.
 */
object UsageCollector {

    private const val MERGE_GAP_MS = 60_000L
    private const val MIN_APP_MINUTES = 1

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private data class Interval(val pkg: String, val start: Long, val end: Long)

    fun collectToday(context: Context, pact: Pact, sharing: SharingPrefs): UsageSnapshot? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val zone = ZoneId.systemDefault()
        val dayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()

        val ignored = ignoredPackages(context)
        val events = usm.queryEvents(dayStart, now)
        val openSince = HashMap<String, Long>()
        val intervals = ArrayList<Interval>()
        var pickups = 0
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (e.packageName !in ignored) openSince.putIfAbsent(e.packageName, e.timeStamp)
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    openSince.remove(e.packageName)?.let { intervals += Interval(e.packageName, it, e.timeStamp) }
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> pickups++
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    // Screen off closes everything that was open.
                    openSince.forEach { (pkg, start) -> intervals += Interval(pkg, start, e.timeStamp) }
                    openSince.clear()
                }
            }
        }
        // Apps still in the foreground right now.
        openSince.forEach { (pkg, start) -> intervals += Interval(pkg, start, now) }

        if (intervals.isEmpty()) {
            return UsageSnapshot(date = today(), takenAt = now, totalMinutes = 0, apps = emptyList(), hourly = List(24) { 0 }, pickups = pickups)
        }

        val pm = context.packageManager
        val hourlyMs = LongArray(24)
        val perAppMs = HashMap<String, Long>()
        val perAppSessions = HashMap<String, Int>()
        val perAppLongest = HashMap<String, Long>()
        var lateNightMs = 0L
        var mealMs = 0L

        val bed = LocalTime.parse(pact.bedtime)
        val wake = LocalTime.parse(pact.wakeTime)
        // "Late night" = after bedtime or before wake-up.
        val lateWindows = listOf(
            dayStart to dayStart + wake.toSecondOfDay() * 1000L,
            dayStart + bed.toSecondOfDay() * 1000L to dayStart + 24L * 3600_000L,
        )
        val mealWindows = pact.meals.map {
            dayStart + LocalTime.parse(it.start).toSecondOfDay() * 1000L to dayStart + LocalTime.parse(it.end).toSecondOfDay() * 1000L
        }

        // Merge back-to-back intervals of the same app into sessions.
        val sessions = ArrayList<Interval>()
        intervals.sortedBy { it.start }.forEach { iv ->
            val last = sessions.lastOrNull()
            if (last != null && last.pkg == iv.pkg && iv.start - last.end <= MERGE_GAP_MS) {
                sessions[sessions.lastIndex] = last.copy(end = maxOf(last.end, iv.end))
            } else {
                sessions += iv
            }
        }

        for (s in sessions) {
            val start = maxOf(s.start, dayStart)
            val end = minOf(s.end, now)
            if (end <= start) continue
            val dur = end - start
            perAppMs[s.pkg] = (perAppMs[s.pkg] ?: 0L) + dur
            perAppSessions[s.pkg] = (perAppSessions[s.pkg] ?: 0) + 1
            perAppLongest[s.pkg] = maxOf(perAppLongest[s.pkg] ?: 0L, dur)
            var t = start
            while (t < end) {
                val hour = ((t - dayStart) / 3600_000L).toInt().coerceIn(0, 23)
                val hourEnd = dayStart + (hour + 1) * 3600_000L
                val chunkEnd = minOf(end, hourEnd)
                hourlyMs[hour] += chunkEnd - t
                t = chunkEnd
            }
            lateNightMs += lateWindows.sumOf { overlap(start, end, it.first, it.second) }
            mealMs += mealWindows.sumOf { overlap(start, end, it.first, it.second) }
        }

        val apps = perAppMs.mapNotNull { (pkg, ms) ->
            val minutes = (ms / 60_000L).toInt()
            if (minutes < MIN_APP_MINUTES) return@mapNotNull null
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            val label = info?.let { pm.getApplicationLabel(it).toString() } ?: AppCatalog.lensFor(pkg)?.name ?: pkg.substringAfterLast('.')
            AppUsage(
                pkg = pkg,
                label = label,
                category = AppCatalog.categorize(pkg, info),
                minutes = minutes,
                sessions = perAppSessions[pkg] ?: 1,
                longestSessionMin = ((perAppLongest[pkg] ?: 0L) / 60_000L).toInt(),
            )
        }.sortedByDescending { it.minutes }

        val longest = sessions.maxByOrNull { it.end - it.start }
        val labelOf = { pkg: String -> apps.firstOrNull { it.pkg == pkg }?.label ?: "" }

        val snapshot = UsageSnapshot(
            date = today(),
            takenAt = now,
            totalMinutes = apps.sumOf { it.minutes },
            apps = apps,
            hourly = hourlyMs.map { (it / 60_000L).toInt() },
            longestSessionMin = longest?.let { ((it.end - it.start) / 60_000L).toInt() } ?: 0,
            longestSessionApp = longest?.let { labelOf(it.pkg) } ?: "",
            lateNightMinutes = (lateNightMs / 60_000L).toInt(),
            mealTimeMinutes = (mealMs / 60_000L).toInt(),
            pickups = pickups,
        )
        return applySharing(snapshot, sharing)
    }

    /** The child's sharing choices are applied on-device, before anything leaves the phone. */
    fun applySharing(s: UsageSnapshot, sharing: SharingPrefs): UsageSnapshot {
        var out = s
        if (!sharing.appNames) {
            out = out.copy(
                apps = s.apps.groupBy { it.category }.map { (cat, list) ->
                    AppUsage(pkg = "category:${cat.name}", label = cat.label, category = cat, minutes = list.sumOf { it.minutes }, sessions = list.sumOf { it.sessions }, longestSessionMin = list.maxOf { it.longestSessionMin })
                }.sortedByDescending { it.minutes },
                longestSessionApp = s.apps.firstOrNull { it.label == s.longestSessionApp }?.category?.label ?: "",
                appNamesShared = false,
            )
        }
        return out
    }

    data class EndedSession(val pkg: String, val label: String, val category: AppCategory, val start: Long, val end: Long) {
        val minutes get() = ((end - start) / 60_000L).toInt()
    }

    /**
     * The most recent screen session that has clearly ended (no activity from that app for
     * [quietMs]) and lasted at least [minMinutes]. Used for the Transition Check.
     */
    fun lastEndedSession(context: Context, minMinutes: Int, quietMs: Long = 45_000L): EndedSession? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ignored = ignoredPackages(context)
        val events = usm.queryEvents(now - 4 * 3600_000L, now)
        val open = HashMap<String, Long>()
        val intervals = ArrayList<Interval>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> if (e.packageName !in ignored) open.putIfAbsent(e.packageName, e.timeStamp)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    open.remove(e.packageName)?.let { intervals += Interval(e.packageName, it, e.timeStamp) }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    open.forEach { (pkg, start) -> intervals += Interval(pkg, start, e.timeStamp) }
                    open.clear()
                }
            }
        }
        if (BuildConfig.DEBUG) Log.d("AnchorAgent", "open=${open.keys} last=${intervals.maxByOrNull { it.end }?.let { "${it.pkg} ${(now - it.end) / 1000}s ago, ${(it.end - it.start) / 1000}s long" }}")
        if (open.isNotEmpty()) return null // something is still on screen
        val sessions = ArrayList<Interval>()
        intervals.sortedBy { it.start }.forEach { iv ->
            val last = sessions.lastOrNull()
            if (last != null && last.pkg == iv.pkg && iv.start - last.end <= MERGE_GAP_MS) sessions[sessions.lastIndex] = last.copy(end = maxOf(last.end, iv.end))
            else sessions += iv
        }
        val last = sessions.lastOrNull() ?: return null
        if (now - last.end < quietMs) return null
        if ((last.end - last.start) / 60_000L < minMinutes) return null
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(last.pkg, 0) }.getOrNull()
        val label = info?.let { pm.getApplicationLabel(it).toString() } ?: AppCatalog.lensFor(last.pkg)?.name ?: last.pkg.substringAfterLast('.')
        return EndedSession(last.pkg, label, AppCatalog.categorize(last.pkg, info), last.start, last.end)
    }

    private fun overlap(a0: Long, a1: Long, b0: Long, b1: Long): Long = maxOf(0L, minOf(a1, b1) - maxOf(a0, b0))

    /**
     * Packages that never count as screen time: home screens, Anchor itself, system UI, and anything
     * without a launcher icon (permission dialogs, overlays, background system components).
     */
    private fun ignoredPackages(context: Context): IgnoreSet = IgnoreSet(context)

    class IgnoreSet(private val context: Context) {
        private val pm = context.packageManager
        private val fixed: Set<String> = run {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val launchers = pm.queryIntentActivities(home, 0).map { it.activityInfo.packageName }
            (launchers + listOf(context.packageName, "com.android.systemui", "com.android.settings", "android")).toSet()
        }
        private val cache = HashMap<String, Boolean>()

        operator fun contains(pkg: String): Boolean =
            pkg in fixed || cache.getOrPut(pkg) { pm.getLaunchIntentForPackage(pkg) == null }
    }
}

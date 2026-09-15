package com.anchor.copilot.data

import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/**
 * Seeded, deterministic sample history for the one-phone demo, mirroring the web prototype:
 * a 20-day mood series with a dip in the last few days, tough recent transitions and a
 * short-form-video-heavy week. Always labelled "sample data" in the UI.
 */
object SampleFamily {

    const val CHILD_NAME = "Mira"
    const val CHILD_AVATAR = "🌱"

    private data class SampleApp(val pkg: String, val label: String, val cat: AppCategory, val base: Int, val hours: List<Int>)

    private val pool = listOf(
        SampleApp("com.zhiliaoapp.musically", "TikTok", AppCategory.SHORT_VIDEO, 50, listOf(7, 16, 17, 21, 22)),
        SampleApp("com.google.android.youtube", "YouTube", AppCategory.VIDEO, 32, listOf(16, 19, 20)),
        SampleApp("com.roblox.client", "Roblox", AppCategory.GAMES, 42, listOf(17, 18)),
        SampleApp("com.mojang.minecraftpe", "Minecraft", AppCategory.GAMES, 18, listOf(18, 19)),
        SampleApp("com.snapchat.android", "Snapchat", AppCategory.SOCIAL, 16, listOf(8, 16, 21)),
        SampleApp("com.whatsapp", "WhatsApp", AppCategory.CHAT, 12, listOf(8, 15, 20)),
        SampleApp("com.duolingo", "Duolingo", AppCategory.LEARNING, 14, listOf(7, 19)),
        SampleApp("org.khanacademy.android", "Khan Academy", AppCategory.LEARNING, 9, listOf(16)),
        SampleApp("com.canva.editor", "Canva", AppCategory.CREATIVITY, 8, listOf(18)),
        SampleApp("com.spotify.music", "Spotify", AppCategory.MUSIC, 12, listOf(8, 17)),
    )

    fun events(guardianName: String): List<Event> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val out = ArrayList<Event>()
        var seq = 0L
        fun at(daysAgo: Int, time: String): Long =
            today.minusDays(daysAgo.toLong()).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli().coerceAtMost(now - 60_000)

        fun add(type: String, from: Role, name: String, ts: Long, payload: JsonObject) {
            out += Event(UUID.randomUUID().toString(), type, from, name, ts, ++seq, payload)
        }

        val start = at(21, "18:00")
        add(EventType.MEMBER_JOINED, Role.PARENT, guardianName, start, MemberJoined(guardianName, Role.PARENT).toPayload())
        add(EventType.PACT_PROPOSED, Role.PARENT, guardianName, start + 60_000, Pact(version = 1, proposedBy = guardianName, proposedAt = start, note = "Screens are okay. We just want sleep, dinners and play to win too.").toPayload())
        add(EventType.CONSENT, Role.CHILD, CHILD_NAME, start + 5 * 60_000, ConsentPayload(CHILD_NAME, CHILD_AVATAR, SharingPrefs()).toPayload())
        add(EventType.PACT_ACCEPTED, Role.CHILD, CHILD_NAME, start + 6 * 60_000, PactResponse(1).toPayload())
        add(EventType.MEMBER_JOINED, Role.GUIDE, "Riya", start + 30 * 60_000, MemberJoined("Riya", Role.GUIDE).toPayload())

        // 20-day mood history with the prototype's random walk and a dip in the last 4 days.
        val rnd = Random(20260826)
        var base = 3.4
        for (daysAgo in 19 downTo 1) {
            base += (rnd.nextDouble() - 0.52) * 0.6
            base = base.coerceIn(1.4, 4.8)
            if (daysAgo <= 3) base -= 0.35
            if (daysAgo == 6 || daysAgo == 11) continue // skipped days are allowed
            val level = base.toInt().coerceIn(1, 5) + if (rnd.nextDouble() < base % 1) 1 else 0
            val lv = level.coerceIn(1, 5)
            add(EventType.VIBE_CHECK, Role.CHILD, CHILD_NAME, at(daysAgo, "19:${10 + rnd.nextInt(40)}"), VibeCheck(lv, Moods.scale[lv - 1].label, cameraUsed = rnd.nextBoolean(), hadPrivateNote = rnd.nextDouble() < 0.4).toPayload())
            if (rnd.nextDouble() < 0.5) {
                val morning = (lv + if (rnd.nextBoolean()) 1 else -1).coerceIn(1, 5)
                add(EventType.VIBE_CHECK, Role.CHILD, CHILD_NAME, at(daysAgo, "08:${10 + rnd.nextInt(40)}"), VibeCheck(morning, Moods.scale[morning - 1].label).toPayload())
            }
        }
        add(EventType.VIBE_CHECK, Role.CHILD, CHILD_NAME, at(0, "08:12"), VibeCheck(4, "happy").toPayload())
        add(EventType.VIBE_CHECK, Role.CHILD, CHILD_NAME, at(0, "13:40"), VibeCheck(3, "neutral", sharedNote = "school was ok").toPayload())

        // Transition reactions: last sessions trending tougher, mostly after short-form video.
        val transitionApps = listOf("TikTok", "Roblox", "YouTube", "TikTok", "Minecraft", "TikTok", "YouTube", "TikTok", "Roblox", "TikTok")
        val intensities = listOf(1.8, 2.4, 3.1, 1.6, 2.2, 3.6, 2.0, 4.1, 3.8, 4.4)
        transitionApps.forEachIndexed { i, app ->
            val daysAgo = 9 - i
            val lvl = (6 - intensities[i]).roundToIntSafe().coerceIn(1, 5)
            val cat = pool.first { it.label == app }.cat
            add(EventType.TRANSITION, Role.CHILD, CHILD_NAME, at(daysAgo, "20:${15 + i * 3}"),
                TransitionCheck(lvl, intensities[i], 900L + rnd.nextLong(0, 2600), app, cat, 25 + rnd.nextInt(50)).toPayload())
        }

        for (daysAgo in 6 downTo 0) {
            val date = today.minusDays(daysAgo.toLong())
            val snap = daySnapshot(date, daysAgo, if (daysAgo == 0) now else at(daysAgo, "23:30"))
            add(EventType.SNAPSHOT, Role.CHILD, CHILD_NAME, snap.takenAt, snap.toPayload())
        }

        add(EventType.FOCUS, Role.CHILD, CHILD_NAME, at(2, "20:00"), FocusSession(30, "Dinner").toPayload())
        add(EventType.FOCUS, Role.CHILD, CHILD_NAME, at(1, "20:00"), FocusSession(45, "Dinner").toPayload())
        add(EventType.KUDOS, Role.PARENT, guardianName, at(1, "20:50"), Kudos("Proud of you for the phone-free dinner! 🍝").toPayload())
        add(EventType.SHARE, Role.CHILD, CHILD_NAME, at(1, "17:30"), SharedItem("share-1", "This robot learned to play badminton 🤖🏸", "Can we build one??").toPayload())
        add(EventType.FLAG, Role.CHILD, CHILD_NAME, now - 95 * 60_000L, Flag("flag-1", "stranger", "Someone in a Roblox game asked for my Snapchat", "Roblox").toPayload())
        return out
    }

    private fun Double.roundToIntSafe() = Math.round(this).toInt()

    private fun daySnapshot(date: LocalDate, daysAgo: Int, takenAt: Long): UsageSnapshot {
        val rnd = Random(date.toEpochDay())
        val weekend = date.dayOfWeek.value >= 6
        val isToday = daysAgo == 0
        val currentHour = LocalTime.now().hour
        val hourly = IntArray(24)
        val apps = pool.mapNotNull { a ->
            var m = (a.base * (0.4 + rnd.nextDouble() * 0.55) * if (weekend) 1.45 else 1.0).toInt()
            if (isToday) m = (m * (currentHour.coerceIn(8, 22) / 22.0)).toInt()
            if (m < 3) return@mapNotNull null
            val hours = a.hours.filter { !isToday || it <= maxOf(currentHour, 9) }.ifEmpty { listOf(currentHour.coerceIn(0, 23)) }
            var left = m
            hours.forEachIndexed { i, h ->
                val share = if (i == hours.lastIndex) left else (left / (hours.size - i)).coerceAtMost(left)
                hourly[h] += share
                left -= share
            }
            AppUsage(a.pkg, a.label, a.cat, m, sessions = 1 + rnd.nextInt(4), longestSessionMin = (m * (0.4 + rnd.nextDouble() * 0.5)).toInt())
        }.sortedByDescending { it.minutes }
        val longest = apps.maxBy { it.longestSessionMin }
        return UsageSnapshot(
            date = date.iso(),
            takenAt = takenAt,
            totalMinutes = apps.sumOf { it.minutes },
            apps = apps,
            hourly = hourly.toList(),
            longestSessionMin = longest.longestSessionMin,
            longestSessionApp = longest.label,
            lateNightMinutes = if (isToday) 0 else hourly[22] + hourly[23],
            mealTimeMinutes = if (daysAgo == 3 || daysAgo == 5) 12 else 0,
            pickups = 40 + rnd.nextInt(35),
            sample = true,
        )
    }
}

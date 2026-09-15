package com.anchor.copilot.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val AnchorJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

inline fun <reified T> T.toPayload(): JsonObject = AnchorJson.encodeToJsonElement(this).jsonObject
inline fun <reified T> Event.decode(): T? = runCatching { AnchorJson.decodeFromJsonElement<T>(payload) }.getOrNull()

fun Long.toLocalDate(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
fun LocalDate.iso(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)
fun todayIso(): String = LocalDate.now().iso()

data class Stamped<T>(val event: Event, val value: T)
data class FlagItem(val event: Event, val flag: Flag, val acknowledged: Boolean)
data class MoodPoint(val date: String, val value: Double, val live: Boolean)

/** Everything the family can see, rebuilt from the shared event log on every device. */
data class FamilyState(
    val childName: String = "",
    val childAvatar: String = "🌱",
    val guardians: List<MemberJoined> = emptyList(),
    val consentAt: Long = 0,
    val sharing: SharingPrefs = SharingPrefs(),
    val snapshots: Map<String, UsageSnapshot> = emptyMap(),
    val acceptedPact: Pact? = null,
    val proposedPact: Pact? = null,
    val vibes: List<Stamped<VibeCheck>> = emptyList(),
    val transitions: List<Stamped<TransitionCheck>> = emptyList(),
    val flags: List<FlagItem> = emptyList(),
    val shares: List<Stamped<SharedItem>> = emptyList(),
    val focus: List<Stamped<FocusSession>> = emptyList(),
    val kudos: List<Stamped<Kudos>> = emptyList(),
) {
    val consented get() = consentAt > 0
    val pact: Pact get() = acceptedPact ?: proposedPact ?: Pact()
    val hasPendingPact get() = proposedPact != null && (acceptedPact == null || proposedPact.version > acceptedPact.version)
    val today: UsageSnapshot? get() = snapshots[todayIso()]
    val openFlags get() = flags.filter { !it.acknowledged }

    /** Vibe + transition check-ins both count as "showing up". */
    val checkinCount get() = vibes.size + transitions.count { !it.value.simulated }
    fun checkinsOn(date: String) = vibes.count { it.event.ts.toLocalDate() == date } + transitions.count { it.event.ts.toLocalDate() == date }

    /**
     * Streak of days with at least one check-in. One skipped day is forgiven ("skipping is allowed,
     * not punished"), two in a row ends the streak.
     */
    val streak: Int
        get() {
            val days = (vibes.map { it.event.ts.toLocalDate() } + transitions.map { it.event.ts.toLocalDate() }).toSet()
            var day = LocalDate.now()
            if (day.iso() !in days) day = day.minusDays(1)
            var n = 0
            var skips = 0
            while (skips < 2) {
                if (day.iso() in days) { n++; skips = 0 } else skips++
                day = day.minusDays(1)
                if (n > 365) break
            }
            return n
        }

    /** Sprout growth: +22 XP per check-in; each stage takes a little longer; it never shrinks. */
    val xpTotal get() = checkinCount * 22
    val sproutStage get() = STAGE_XP.count { xpTotal >= it }
    /** Progress through the current stage, 0..100. */
    val xpInStage: Int
        get() {
            val stage = sproutStage
            if (stage >= STAGE_XP.size) return 100
            val from = STAGE_XP[stage - 1]
            return ((xpTotal - from) * 100 / (STAGE_XP[stage] - from)).coerceIn(0, 100)
        }

    /** Daily average mood for the last [days] days (only days that have check-ins). */
    fun moodSeries(days: Int = 20): List<MoodPoint> {
        val today = LocalDate.now()
        return (days - 1 downTo 0).mapNotNull { back ->
            val d = today.minusDays(back.toLong()).iso()
            val list = vibes.filter { it.event.ts.toLocalDate() == d }
            if (list.isEmpty()) null else MoodPoint(d, list.map { it.value.level }.average(), back == 0)
        }
    }

    /** Category share of screen time across the last 7 days, largest first, as percentages. */
    fun weeklyCategoryMix(): List<Pair<AppCategory, Int>> {
        val start = LocalDate.now().minusDays(6)
        val totals = HashMap<AppCategory, Int>()
        snapshots.values.filter { !LocalDate.parse(it.date).isBefore(start) }.forEach { s ->
            s.apps.forEach { a -> totals[a.category] = (totals[a.category] ?: 0) + a.minutes }
        }
        val sum = totals.values.sum()
        if (sum == 0) return emptyList()
        return totals.entries.sortedByDescending { it.value }.map { it.key to Math.round(it.value * 100.0 / sum).toInt() }
    }

    fun weeklyMinutes(): List<Pair<String, Int?>> {
        val today = LocalDate.now()
        return (6 downTo 0).map { back -> today.minusDays(back.toLong()).iso().let { it to snapshots[it]?.totalMinutes } }
    }

    companion object {
        /** XP needed to reach stages 1..5 (5, 15, 30 and 50 check-ins). */
        val STAGE_XP = listOf(0, 110, 330, 660, 1100)

        fun reduce(events: List<Event>): FamilyState {
            var s = FamilyState()
            val sorted = events.sortedWith(compareBy<Event>({ if (it.seq == 0L) Long.MAX_VALUE else it.seq }, { it.ts }))
            val snapshots = HashMap<String, UsageSnapshot>()
            val flags = LinkedHashMap<String, FlagItem>()
            val vibes = ArrayList<Stamped<VibeCheck>>()
            val transitions = ArrayList<Stamped<TransitionCheck>>()
            val shares = ArrayList<Stamped<SharedItem>>()
            val focus = ArrayList<Stamped<FocusSession>>()
            val kudos = ArrayList<Stamped<Kudos>>()
            val guardians = ArrayList<MemberJoined>()
            for (e in sorted) {
                when (e.type) {
                    EventType.MEMBER_JOINED -> e.decode<MemberJoined>()?.let { m ->
                        if (m.role != Role.CHILD && guardians.none { it.name == m.name && it.role == m.role }) guardians += m
                    }
                    EventType.CONSENT -> e.decode<ConsentPayload>()?.let {
                        s = s.copy(childName = it.childName, childAvatar = it.avatar, sharing = it.sharing, consentAt = e.ts)
                    }
                    EventType.SHARING_CHANGED -> e.decode<SharingPrefs>()?.let { s = s.copy(sharing = it) }
                    EventType.SNAPSHOT -> e.decode<UsageSnapshot>()?.let { snap ->
                        val prev = snapshots[snap.date]
                        // A real reading always wins over sample data for the same day.
                        if (prev == null || (prev.sample && !snap.sample) || (prev.sample == snap.sample && snap.takenAt >= prev.takenAt)) {
                            snapshots[snap.date] = snap
                        }
                    }
                    EventType.VIBE_CHECK -> e.decode<VibeCheck>()?.let { vibes += Stamped(e, it) }
                    EventType.TRANSITION -> e.decode<TransitionCheck>()?.let { transitions += Stamped(e, it) }
                    EventType.PACT_PROPOSED -> e.decode<Pact>()?.let { s = s.copy(proposedPact = it) }
                    EventType.PACT_ACCEPTED -> e.decode<PactResponse>()?.let { r ->
                        val p = s.proposedPact
                        if (p != null && p.version == r.version) s = s.copy(acceptedPact = p)
                    }
                    EventType.FLAG -> e.decode<Flag>()?.let { flags[it.id] = FlagItem(e, it, false) }
                    EventType.FLAG_ACK -> e.decode<FlagAck>()?.let { a -> flags[a.flagId]?.let { flags[a.flagId] = it.copy(acknowledged = true) } }
                    EventType.SHARE -> e.decode<SharedItem>()?.let { shares += Stamped(e, it) }
                    EventType.FOCUS -> e.decode<FocusSession>()?.let { focus += Stamped(e, it) }
                    EventType.KUDOS -> e.decode<Kudos>()?.let { kudos += Stamped(e, it) }
                }
            }
            return s.copy(
                guardians = guardians,
                snapshots = snapshots,
                vibes = vibes.sortedByDescending { it.event.ts },
                transitions = transitions.sortedByDescending { it.event.ts },
                flags = flags.values.sortedByDescending { it.event.ts },
                shares = shares.sortedByDescending { it.event.ts },
                focus = focus.sortedByDescending { it.event.ts },
                kudos = kudos.sortedByDescending { it.event.ts },
            )
        }
    }
}

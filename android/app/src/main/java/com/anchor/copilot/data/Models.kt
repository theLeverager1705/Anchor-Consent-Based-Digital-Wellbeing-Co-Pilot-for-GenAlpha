package com.anchor.copilot.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class Role { CHILD, PARENT, GUIDE }

/** How this device reaches the rest of the family. */
@Serializable
enum class Transport { LOCAL_DEMO, RELAY }

/** What the child has agreed to share. Every toggle is visible to both sides. */
@Serializable
data class SharingPrefs(
    val screenTime: Boolean = true,
    val appNames: Boolean = true,
    val moods: Boolean = true,
    val transitions: Boolean = true,
    val paused: Boolean = false,
)

@Serializable
data class DeviceProfile(
    val onboarded: Boolean = false,
    val role: Role? = null,
    val transport: Transport = Transport.LOCAL_DEMO,
    val relayUrl: String = "",
    val familyId: String = "",
    val token: String = "",
    val pairCode: String = "",
    val myName: String = "",
    val avatar: String = "🌱",
    val consentAt: Long = 0,
    val lastSeq: Long = 0,
    val lastNotifiedTs: Long = 0,
    /** In the one-phone demo the same device can switch between Kid Mode and Parent Mode. */
    val demoViewAs: Role = Role.CHILD,
    /** Private journal notes stay on the kid's device unless explicitly shared. */
    val privateJournal: List<JournalNote> = emptyList(),
)

@Serializable
data class JournalNote(val ts: Long, val emoji: String, val text: String)

@Serializable
data class AppUsage(
    val pkg: String,
    val label: String,
    val category: AppCategory,
    val minutes: Int,
    val sessions: Int = 1,
    val longestSessionMin: Int = 0,
)

@Serializable
data class UsageSnapshot(
    val date: String,            // yyyy-MM-dd, device local
    val takenAt: Long,
    val totalMinutes: Int,
    val apps: List<AppUsage>,
    val hourly: List<Int> = emptyList(),   // 24 buckets, minutes
    val longestSessionMin: Int = 0,
    val longestSessionApp: String = "",
    val lateNightMinutes: Int = 0,
    val mealTimeMinutes: Int = 0,
    val pickups: Int = 0,
    val appNamesShared: Boolean = true,
    val sample: Boolean = false,
) {
    fun minutesFor(cat: AppCategory) = apps.filter { it.category == cat }.sumOf { it.minutes }
}

@Serializable
data class TimeWindow(val label: String, val start: String, val end: String)

/** Family goals the child agreed to. Anchor nudges and reflects; it never blocks apps. */
@Serializable
data class Pact(
    val version: Int = 1,
    val dailyGoalMin: Int = 150,
    val bedtime: String = "21:30",
    val wakeTime: String = "06:30",
    val meals: List<TimeWindow> = listOf(
        TimeWindow("Breakfast", "07:30", "08:00"),
        TimeWindow("Dinner", "20:00", "20:45"),
    ),
    val note: String = "",
    val proposedBy: String = "",
    val proposedAt: Long = 0,
)

@Serializable
data class Event(
    val id: String,
    val type: String,
    val from: Role,
    val fromName: String = "",
    val ts: Long,
    val seq: Long = 0,           // 0 = not yet acknowledged by the relay
    val payload: JsonObject,
)

object EventType {
    const val CONSENT = "CONSENT"
    const val MEMBER_JOINED = "MEMBER_JOINED"
    const val SNAPSHOT = "SNAPSHOT"
    const val VIBE_CHECK = "VIBE_CHECK"
    const val TRANSITION = "TRANSITION"
    const val PACT_PROPOSED = "PACT_PROPOSED"
    const val PACT_ACCEPTED = "PACT_ACCEPTED"
    const val FLAG = "FLAG"
    const val FLAG_ACK = "FLAG_ACK"
    const val SHARE = "SHARE"
    const val FOCUS = "FOCUS"
    const val SHARING_CHANGED = "SHARING_CHANGED"
    const val KUDOS = "KUDOS"
}

@Serializable
data class ConsentPayload(val childName: String, val avatar: String, val sharing: SharingPrefs)

@Serializable
data class MemberJoined(val name: String, val role: Role)

/**
 * A Vibe Check as the family sees it: the emoji, a derived tone label and, only if the child
 * chose to share it, the journal note. Camera frames are never part of this.
 */
@Serializable
data class VibeCheck(
    val level: Int,              // 1..5
    val label: String,           // sad / anxious / neutral / happy / angry
    val cameraUsed: Boolean = false,
    val sharedNote: String = "",
    val hadPrivateNote: Boolean = false,
)

/** One-tap reaction captured shortly after a screen session ended. */
@Serializable
data class TransitionCheck(
    val level: Int,              // 1..5 emoji picked
    val intensity: Double,       // 6 - level, matches the prototype
    val reactionMs: Long,
    val app: String = "",
    val category: AppCategory? = null,
    val sessionMin: Int = 0,
    val simulated: Boolean = false,
)

@Serializable
data class Flag(val id: String, val kind: String, val note: String, val app: String = "")

@Serializable
data class FlagAck(val flagId: String)

@Serializable
data class SharedItem(val id: String, val text: String, val note: String = "")

@Serializable
data class FocusSession(val minutes: Int, val kind: String, val completed: Boolean = true)

@Serializable
data class PactResponse(val version: Int, val message: String = "")

@Serializable
data class Kudos(val message: String)

/** Same scale as the web prototype: 1 = rough, 5 = great. */
object Moods {
    data class Face(val emoji: String, val value: Int, val label: String)

    val scale = listOf(
        Face("😢", 1, "sad"),
        Face("😟", 2, "anxious"),
        Face("😐", 3, "neutral"),
        Face("🙂", 4, "happy"),
        Face("😄", 5, "happy"),
    )

    fun emoji(level: Int) = scale.getOrNull(level - 1)?.emoji ?: "😐"
}

object FlagKinds {
    val all = listOf(
        "upsetting" to "Something upsetting or scary",
        "stranger" to "A stranger contacted me",
        "mean" to "Someone was mean to me",
        "pressure" to "I felt pressured to do something",
        "other" to "Something else",
    )
    fun label(kind: String) = all.firstOrNull { it.first == kind }?.second ?: "Something else"
}

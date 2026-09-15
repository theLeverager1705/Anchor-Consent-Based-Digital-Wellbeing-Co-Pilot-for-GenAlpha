package com.anchor.copilot.data

import kotlin.math.roundToInt

enum class AlertType { WARN, OK }

data class Alert(
    val type: AlertType,
    val title: String,
    val body: String,
    val starter: String,
    val showResources: Boolean = false,
)

data class Resource(val region: String, val name: String, val contact: String)

fun formatMinutes(min: Int): String = when {
    min < 60 -> "${min}m"
    min % 60 == 0 -> "${min / 60}h"
    else -> "${min / 60}h ${min % 60}m"
}

/**
 * Anchor's pattern engine. Rule-based and explainable on purpose: a false "diagnosis" is a worse
 * failure than a missed alert (see Research Dossier §6). Every output is a prompt for a conversation.
 */
object CoPilot {

    private fun List<Double>.avg() = if (isEmpty()) 0.0 else average()

    fun alerts(state: FamilyState): List<Alert> {
        val name = state.childName.ifBlank { "your child" }
        val alerts = ArrayList<Alert>()

        // 0. The child asked for help. Always first.
        state.openFlags.forEach { f ->
            alerts += Alert(
                AlertType.WARN,
                "$name reached out: ${FlagKinds.label(f.flag.kind).lowercase()}",
                (if (f.flag.note.isNotBlank()) "\"${f.flag.note}\" " else "") + "Telling you took courage. Lead with calm, not consequences.",
                "“Thank you for telling me. You're not in trouble. Want to show me what happened?”",
                showResources = true,
            )
        }

        // 1. Sustained mood dip: recent 4 check-in days vs the 6 before (prototype rule).
        val series = state.moodSeries(20).map { it.value }
        val recent = series.takeLast(4)
        val earlier = series.dropLast(4).takeLast(6)
        if (recent.size >= 2 && earlier.size >= 2 && earlier.avg() - recent.avg() > 0.5) {
            alerts += Alert(
                AlertType.WARN,
                "A gentle dip over the last few days",
                "Mood check-ins have trended lower than the days before. Not a diagnosis, just a pattern worth a low-key check-in.",
                "“I noticed the last few days seemed a little harder than usual. Want to tell me about it?”",
                showResources = earlier.avg() - recent.avg() > 1.5,
            )
        }

        // 2. Tough screen-off transitions (prototype rule: 3 of the last 5 at intensity >= 3.5).
        val lastTransitions = state.transitions.take(5)
        if (lastTransitions.count { it.value.intensity >= 3.5 } >= 3) {
            val app = lastTransitions.filter { it.value.intensity >= 3.5 }.groupBy { it.value.app }.maxByOrNull { it.value.size }?.key.orEmpty()
            alerts += Alert(
                AlertType.WARN,
                "Screen-off transitions look tough lately",
                "Several recent transition checks came back higher-intensity" + (if (app.isNotBlank()) ", mostly after $app" else "") +
                    ". That's consistent with a withdrawal response, not defiance.",
                "“Endings feel extra hard sometimes right after a screen. Want to try a 2-minute warning next time?”",
            )
        }

        // 3. Dominant category (prototype rule: > 35% of the week).
        state.weeklyCategoryMix().firstOrNull()?.takeIf { it.second > 35 }?.let { (cat, pct) ->
            alerts += Alert(
                AlertType.WARN,
                "${cat.label} is the biggest slice this week ($pct%)",
                "Not inherently bad, just worth knowing before a conversation about balance.",
                "“I saw ${cat.label.lowercase()} was a big chunk of your week. What's been good about it lately?”",
            )
        }

        // 4. Usage patterns from the OS screen-time API (Research Dossier §1.2 and §2).
        val week = state.snapshots.values.filter { it.date >= java.time.LocalDate.now().minusDays(6).iso() }
        val lateNights = week.count { it.lateNightMinutes >= 15 }
        if (lateNights >= 2) {
            alerts += Alert(
                AlertType.WARN,
                "Screens after bedtime on $lateNights nights",
                "Late-night use is one of the clearest ways screens crowd out sleep for growing brains.",
                "“What if we all charge our phones outside the bedroom this week? Grown-ups too.”",
            )
        }
        val mealDays = week.count { it.mealTimeMinutes >= 10 }
        if (mealDays >= 2) {
            alerts += Alert(
                AlertType.WARN,
                "Screens at the table on $mealDays days",
                "Mealtime screen use often grows as a loop (a hard meal → a screen → harder meals without one). You can gently interrupt the loop together.",
                "“Phones-in-a-basket dinner tonight? Loser of rock-paper-scissors does the dishes.”",
            )
        }
        state.today?.takeIf { it.longestSessionMin >= 60 }?.let { t ->
            alerts += Alert(
                AlertType.WARN,
                "A ${formatMinutes(t.longestSessionMin)} unbroken session today",
                "Mostly on ${t.longestSessionApp.ifBlank { "one app" }}. Long absorbed sessions make endings feel sudden and unfair.",
                "“When's a good save point to stop? Let's set a timer for that instead of a random time.”",
            )
        }

        if (alerts.none { it.type == AlertType.WARN }) {
            alerts += Alert(
                AlertType.OK,
                "Nothing unusual this week",
                "Mood and check-in consistency look steady compared to the days before.",
                "“You've been really consistent with check-ins this week. Anything you're proud of?”",
            )
        }
        return alerts
    }

    data class Stats(val streak: Int, val checkinsToday: Int, val latestMood: Double?, val patterns: Int)

    fun stats(state: FamilyState, alerts: List<Alert>) = Stats(
        streak = state.streak,
        checkinsToday = state.checkinsOn(todayIso()),
        latestMood = state.moodSeries(20).lastOrNull()?.value,
        patterns = alerts.count { it.type == AlertType.WARN },
    )

    /** The local, rule-based digest. The relay can replace it with a Claude-written one. */
    fun digest(state: FamilyState, alerts: List<Alert>): String {
        val warn = alerts.count { it.type == AlertType.WARN }
        val weekCheckins = (0..6).sumOf { state.checkinsOn(java.time.LocalDate.now().minusDays(it.toLong()).iso()) }
        val mood = state.moodSeries(7).map { it.value }.takeIf { it.isNotEmpty() }?.average()
        val moodText = mood?.let { ", mood holding around ${"%.1f".format(it)}/5" } ?: ""
        return if (warn == 0) {
            "This week looked steady: $weekCheckins check-ins logged$moodText. No patterns flagged."
        } else {
            "$warn pattern${if (warn > 1) "s" else ""} worth a look this week, nothing alarming, just a nudge to start a conversation. " +
                "$weekCheckins check-ins logged$moodText."
        }
    }

    /**
     * The only data that may leave the parent's device for AI digest generation: aggregated,
     * de-identified numbers and pattern titles. No names, no journal text, no app-level detail.
     */
    fun digestInput(state: FamilyState, alerts: List<Alert>): Map<String, Any> {
        val mix = state.weeklyCategoryMix().take(4).map { "${it.first.label} ${it.second}%" }
        val week = state.weeklyMinutes().mapNotNull { it.second }
        return mapOf(
            "checkinsThisWeek" to (0..6).sumOf { state.checkinsOn(java.time.LocalDate.now().minusDays(it.toLong()).iso()) },
            "streakDays" to state.streak,
            "moodAvgLast7" to (state.moodSeries(7).map { it.value }.takeIf { it.isNotEmpty() }?.average()?.let { (it * 10).roundToInt() / 10.0 } ?: "n/a"),
            "moodAvgPrior7" to (state.moodSeries(14).dropLast(7).map { it.value }.takeIf { it.isNotEmpty() }?.average()?.let { (it * 10).roundToInt() / 10.0 } ?: "n/a"),
            "toughTransitionsOfLast5" to state.transitions.take(5).count { it.value.intensity >= 3.5 },
            "avgDailyScreenMinutes" to (if (week.isEmpty()) "n/a" else week.average().roundToInt()),
            "categoryMix" to mix,
            "patterns" to alerts.filter { it.type == AlertType.WARN }.map { it.title.replace(state.childName, "the child") },
        )
    }

    val resources = listOf(
        Resource("India", "CHILDLINE", "Call 1098 (24×7, free)"),
        Resource("India", "Tele-MANAS mental health", "Call 14416"),
        Resource("USA", "988 Suicide & Crisis Lifeline", "Call or text 988"),
        Resource("UK", "Childline", "Call 0800 1111"),
        Resource("Anywhere", "Your pediatrician or school counselor", "A real conversation beats any app"),
    )

    /** Short tips from Gen Z, the first generation that grew up with smartphones. */
    val genZTips = listOf(
        "Taking the phone away as punishment? We just learned to hide it better. Make it a plan, not a penalty.",
        "Ask to see the game or the creator. Curiosity gets you invited in; criticism gets you locked out.",
        "Say it out loud: these apps are designed by adults to keep kids hooked. It makes it you + them vs. the feed.",
        "Model it. If your phone comes to dinner, theirs will too.",
        "Never punish them for telling you something bad happened online. That's how you lose the next warning.",
        "Boredom is where creativity starts. A little of it is a feature, not a bug.",
    )
}

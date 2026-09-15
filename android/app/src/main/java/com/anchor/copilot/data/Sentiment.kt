package com.anchor.copilot.data

/**
 * The prototype's on-device lexicon sentiment pass, ported 1:1. Runs entirely on the phone;
 * the journal text itself never leaves the device unless the child taps "share with my family".
 */
object Sentiment {

    private val lexicon = mapOf(
        "happy" to listOf("happy", "fun", "great", "awesome", "excited", "good", "love", "yay", "cool", "proud", "best", "laugh", "glad"),
        "sad" to listOf("sad", "cry", "lonely", "miss", "hurt", "upset", "down", "bad", "tired", "bored", "alone"),
        "angry" to listOf("mad", "angry", "annoyed", "hate", "stupid", "unfair", "furious", "frustrat"),
        "anxious" to listOf("scared", "worried", "nervous", "afraid", "stress", "anxious", "panic", "scary"),
    )

    data class Result(val top: String, val scores: Map<String, Int>)

    fun analyze(text: String): Result? {
        if (text.isBlank()) return null
        val words = text.lowercase().replace(Regex("[^a-z' ]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        val scores = lexicon.keys.associateWith { 0 }.toMutableMap()
        var negateNext = false
        for (w in words) {
            if (w == "not" || w.endsWith("n't")) {
                negateNext = true
                continue
            }
            for ((cat, roots) in lexicon) {
                if (roots.any { w.startsWith(it) }) scores[cat] = scores.getValue(cat) + if (negateNext) -1 else 1
            }
            negateNext = false
        }
        var top = "neutral"
        var topScore = 0
        scores.forEach { (cat, s) -> if (s > topScore) { top = cat; topScore = s } }
        return Result(top, scores)
    }
}

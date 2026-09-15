package com.anchor.copilot.data

import android.content.pm.ApplicationInfo
import kotlinx.serialization.Serializable

@Serializable
enum class AppCategory(val label: String, val emoji: String, val color: Long, val kidLabel: String) {
    SHORT_VIDEO("Short-form video", "📱", 0xFFFF4F7B, "Swipe videos"),
    VIDEO("Video & TV", "📺", 0xFFFF8A3D, "Shows & videos"),
    GAMES("Games", "🎮", 0xFF7C5CFF, "Games"),
    SOCIAL("Social", "💬", 0xFF3D8BFF, "Social"),
    CHAT("Messaging", "✉️", 0xFF22B573, "Chatting"),
    LEARNING("Learning", "📚", 0xFF12B5A0, "Learning"),
    CREATIVITY("Creativity", "🎨", 0xFFF5A524, "Making stuff"),
    MUSIC("Music & audio", "🎧", 0xFFE05CD0, "Music"),
    BROWSING("Browsing", "🌐", 0xFF5B7089, "Web"),
    OTHER("Other", "🧩", 0xFF9AA8B8, "Other");

    /** Categories where long unbroken sessions tend to crowd out sleep, meals and play. */
    val isHighPull: Boolean get() = this == SHORT_VIDEO || this == VIDEO || this == GAMES || this == SOCIAL
    val isEnriching: Boolean get() = this == LEARNING || this == CREATIVITY
}

/**
 * A plain-language guide for grown-ups about a popular app. Written for conversation, not control.
 */
data class AppLens(
    val name: String,
    val category: AppCategory,
    val minAge: Int,
    val whatItIs: String,
    val watchFor: List<String>,
    val settingsToCheckTogether: List<String>,
    val conversationStarter: String,
)

object AppCatalog {

    private val lenses: Map<String, AppLens> = listOf(
        "com.zhiliaoapp.musically" to AppLens(
            "TikTok", AppCategory.SHORT_VIDEO, 13,
            "An endless feed of short videos picked by an algorithm that learns what keeps you watching.",
            listOf("Infinite scroll makes time disappear", "Trends and challenges that can be risky", "DMs from strangers on public accounts"),
            listOf("Family Pairing", "Private account", "Daily screen time limit", "Restricted mode"),
            "\"What's a trend on your feed right now that you think is actually funny?\"",
        ),
        "com.ss.android.ugc.trill" to AppLens(
            "TikTok", AppCategory.SHORT_VIDEO, 13,
            "An endless feed of short videos picked by an algorithm that learns what keeps you watching.",
            listOf("Infinite scroll makes time disappear", "Trends and challenges that can be risky", "DMs from strangers"),
            listOf("Family Pairing", "Private account", "Restricted mode"),
            "\"Show me a video that made you laugh today?\"",
        ),
        "com.google.android.youtube" to AppLens(
            "YouTube", AppCategory.VIDEO, 13,
            "Long videos plus Shorts. Autoplay and recommendations keep the next video coming.",
            listOf("Shorts behaves like TikTok's endless feed", "Autoplay rabbit holes", "Comments can be harsh"),
            listOf("Supervised experience or YouTube Kids", "Turn off autoplay", "Restricted mode", "Take-a-break reminder"),
            "\"Which creator would you want to meet in real life? Why?\"",
        ),
        "com.google.android.apps.youtube.kids" to AppLens(
            "YouTube Kids", AppCategory.VIDEO, 4,
            "A filtered version of YouTube for younger kids with parent controls.",
            listOf("Filters are good but not perfect", "Autoplay still keeps going"),
            listOf("Approved content only mode", "Timer", "Turn off search"),
            "\"What's the best thing you learned from a video this week?\"",
        ),
        "com.instagram.android" to AppLens(
            "Instagram", AppCategory.SOCIAL, 13,
            "Photos, Stories and Reels (short videos). Built around followers and likes.",
            listOf("Comparing yourself to filtered lives", "Reels endless feed", "Messages from people you don't know"),
            listOf("Teen Account settings", "Private account", "Hidden like counts", "Quiet mode at night"),
            "\"Does anyone you follow make you feel good about yourself? Anyone who doesn't?\"",
        ),
        "com.snapchat.android" to AppLens(
            "Snapchat", AppCategory.SOCIAL, 13,
            "Disappearing photo messages, streaks, and a map that can show location.",
            listOf("Streak pressure to open daily", "Snap Map location sharing", "Disappearing messages feel 'safe' but can be screenshotted"),
            listOf("Ghost Mode on Snap Map", "Family Center", "Who can contact me: Friends only"),
            "\"How many streaks do you have? Do any of them feel like a chore?\"",
        ),
        "com.roblox.client" to AppLens(
            "Roblox", AppCategory.GAMES, 9,
            "Millions of mini-games built by players, with chat and a virtual currency (Robux).",
            listOf("Chat with strangers", "Pressure to buy Robux", "Some user-made games aren't age-appropriate"),
            listOf("Parental controls & PIN", "Chat limited to friends", "Spending limits", "Age-based experience settings"),
            "\"Can you give me a tour of your favourite Roblox world?\"",
        ),
        "com.mojang.minecraftpe" to AppLens(
            "Minecraft", AppCategory.GAMES, 7,
            "A building and survival sandbox. Can be super creative, especially offline or with friends.",
            listOf("Public servers have open chat", "Marathon build sessions"),
            listOf("Play on friends-only realms", "Xbox family settings for chat"),
            "\"What's the coolest thing you've built? Can I see it?\"",
        ),
        "com.supercell.brawlstars" to AppLens(
            "Brawl Stars", AppCategory.GAMES, 9,
            "Fast team battle game with loot rewards and in-app purchases.",
            listOf("Reward boxes feel like gambling", "In-app purchases"),
            listOf("Purchase approval in Google Play", "Turn off chat in clubs"),
            "\"Who's your main brawler and why?\"",
        ),
        "com.dts.freefireth" to AppLens(
            "Free Fire", AppCategory.GAMES, 12,
            "Battle-royale shooter with voice chat and purchases.",
            listOf("Violence", "Voice chat with strangers", "In-game purchases"),
            listOf("Disable voice chat", "Purchase approval"),
            "\"What makes a good teammate in the game?\"",
        ),
        "com.whatsapp" to AppLens(
            "WhatsApp", AppCategory.CHAT, 13,
            "Messaging and group chats. Anchor never reads messages.",
            listOf("Big group chats can get overwhelming or mean", "Forwarded rumours"),
            listOf("Who can add me to groups: My contacts", "Last seen & online privacy"),
            "\"Are any of your group chats stressful? You can always leave one.\"",
        ),
        "com.discord" to AppLens(
            "Discord", AppCategory.CHAT, 13,
            "Community servers with text and voice chat around games and interests.",
            listOf("Large public servers", "DMs from strangers", "Mature servers"),
            listOf("Family Center", "Safe direct messaging filter", "Only friends can DM"),
            "\"Which server is your favourite community? What do people talk about?\"",
        ),
        "com.duolingo" to AppLens(
            "Duolingo", AppCategory.LEARNING, 4,
            "Bite-size language lessons with streaks.",
            listOf("Streak anxiety (mostly harmless!)"),
            listOf("Daily goal that feels achievable"),
            "\"Teach me one word you learned today?\"",
        ),
        "org.khanacademy.android" to AppLens(
            "Khan Academy", AppCategory.LEARNING, 4,
            "Free lessons in maths, science and more.",
            emptyList(), emptyList(),
            "\"What topic clicked for you this week?\"",
        ),
        "com.byjus.thelearningapp" to AppLens(
            "BYJU'S", AppCategory.LEARNING, 6,
            "Video lessons and practice for school subjects.",
            listOf("Sales calls and upsells to parents"),
            emptyList(),
            "\"Which lesson was easiest to understand?\"",
        ),
        "com.google.android.apps.classroom" to AppLens(
            "Google Classroom", AppCategory.LEARNING, 4,
            "School assignments and class updates.",
            emptyList(), emptyList(),
            "\"Anything due this week I can help with?\"",
        ),
        "com.spotify.music" to AppLens(
            "Spotify", AppCategory.MUSIC, 13,
            "Music and podcasts.",
            listOf("Explicit lyrics"),
            listOf("Allow explicit content: off", "Spotify Kids for younger children"),
            "\"Play me the song you've had on repeat?\"",
        ),
        "com.netflix.mediaclient" to AppLens(
            "Netflix", AppCategory.VIDEO, 7,
            "Shows and movies with autoplay of the next episode.",
            listOf("Autoplay next episode", "Profiles without maturity limits"),
            listOf("Kids profile or maturity rating", "Turn off autoplay next episode"),
            "\"If you could jump into any show, which one would it be?\"",
        ),
        "com.canva.editor" to AppLens(
            "Canva", AppCategory.CREATIVITY, 13,
            "Design tool for posters, videos and presentations.",
            emptyList(), emptyList(),
            "\"What are you designing? Can I see?\"",
        ),
        "com.pinterest" to AppLens(
            "Pinterest", AppCategory.SOCIAL, 13,
            "Idea boards of images on any topic.",
            listOf("Endless image feed", "Body-image content"),
            listOf("Private boards", "Messaging off"),
            "\"What's on your favourite board?\"",
        ),
        "com.android.chrome" to AppLens(
            "Chrome", AppCategory.BROWSING, 4,
            "Web browser.",
            listOf("Anything on the open web"),
            listOf("SafeSearch", "Google Family Link site filters"),
            "\"Found any good websites lately?\"",
        ),
    ).toMap()

    private val prefixCategories = listOf(
        "com.supercell" to AppCategory.GAMES,
        "com.king." to AppCategory.GAMES,
        "com.miniclip" to AppCategory.GAMES,
        "com.epicgames" to AppCategory.GAMES,
        "com.activision" to AppCategory.GAMES,
        "com.tencent.ig" to AppCategory.GAMES,
        "com.pubg" to AppCategory.GAMES,
        "com.facebook.katana" to AppCategory.SOCIAL,
        "com.facebook.orca" to AppCategory.CHAT,
        "com.twitter" to AppCategory.SOCIAL,
        "com.reddit" to AppCategory.SOCIAL,
        "org.telegram" to AppCategory.CHAT,
        "com.google.android.apps.messaging" to AppCategory.CHAT,
        "com.amazon.avod" to AppCategory.VIDEO,
        "in.startv.hotstar" to AppCategory.VIDEO,
        "com.jio" to AppCategory.VIDEO,
        "com.google.android.apps.photos" to AppCategory.CREATIVITY,
        "com.adobe" to AppCategory.CREATIVITY,
        "com.google.android.apps.docs" to AppCategory.LEARNING,
        "com.microsoft.office" to AppCategory.LEARNING,
        "org.mozilla" to AppCategory.BROWSING,
        "com.brave" to AppCategory.BROWSING,
        "com.opera" to AppCategory.BROWSING,
    )

    fun lensFor(pkg: String): AppLens? = lenses[pkg]

    fun lensByName(label: String): AppLens? = lenses.values.firstOrNull { it.name.equals(label, ignoreCase = true) }

    fun categorize(pkg: String, info: ApplicationInfo?): AppCategory {
        lenses[pkg]?.let { return it.category }
        prefixCategories.firstOrNull { pkg.startsWith(it.first) }?.let { return it.second }
        return when (info?.category) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
            ApplicationInfo.CATEGORY_AUDIO -> AppCategory.MUSIC
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_IMAGE -> AppCategory.CREATIVITY
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.LEARNING
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.BROWSING
            else -> if (info != null && (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0) AppCategory.GAMES else AppCategory.OTHER
        }
    }
}

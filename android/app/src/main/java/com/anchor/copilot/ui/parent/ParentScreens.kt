package com.anchor.copilot.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.copilot.data.*
import com.anchor.copilot.ui.Overlay
import com.anchor.copilot.ui.components.*
import com.anchor.copilot.ui.kid.clock
import com.anchor.copilot.ui.theme.Anchor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private fun ago(ts: Long): String {
    val m = (System.currentTimeMillis() - ts) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "${m}m ago"
        m < 24 * 60 -> "${m / 60}h ago"
        else -> "${m / 1440}d ago"
    }
}

@Composable
private fun ChildHeader(state: FamilyState, profile: DeviceProfile, syncStatus: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(Anchor.GoodBg), contentAlignment = Alignment.Center) {
            Text(sproutEmoji(state.sproutStage), fontSize = 24.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(state.childName.ifBlank { "Waiting for your child to join" }, style = MaterialTheme.typography.titleMedium)
            val last = state.snapshots.values.maxOfOrNull { it.takenAt }
            val status = when {
                !state.consented -> "Share the family code to connect"
                state.sharing.paused -> "⏸️ Sharing paused by ${state.childName}"
                syncStatus != null -> syncStatus
                last != null -> "Last update ${ago(last)}" + if (profile.transport == Transport.LOCAL_DEMO) " · sample data" else ""
                else -> "Connected"
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(16.dp))
}

// ------------------------------------------------------------------ Digest

@Composable
fun ParentDigest(state: FamilyState, profile: DeviceProfile, repo: AnchorRepository, syncStatus: String?, open: (Overlay) -> Unit) {
    val alerts = remember(state) { CoPilot.alerts(state) }
    val stats = remember(state) { CoPilot.stats(state, alerts) }
    val scope = rememberCoroutineScope()
    var ai by remember { mutableStateOf<RelayClient.Digest?>(null) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    // Ask the relay whether a Claude key is configured, so the button tells the truth up front.
    var aiConfigured by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(profile.relayUrl) {
        aiConfigured = runCatching { RelayClient(profile.relayUrl.ifBlank { com.anchor.copilot.BuildConfig.DEFAULT_RELAY_URL }).aiEnabled() }.getOrNull()
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp)) {
        PageTitle("Family Digest")
        ChildHeader(state, profile, syncStatus)

        AnchorCard {
            Kicker("This week")
            Text(ai?.digest ?: CoPilot.digest(state, alerts), style = MaterialTheme.typography.bodyLarge)
            ai?.let {
                Spacer(Modifier.height(8.dp))
                StarterText(it.starter)
                Spacer(Modifier.height(6.dp))
                Text("✨ Written by ${it.model.ifBlank { "Claude" }} from aggregated numbers only. No names, notes or app details were sent.", fontSize = 11.sp, color = Anchor.Muted)
            }
            aiError?.let { Text(it, fontSize = 12.sp, color = Anchor.Muted, modifier = Modifier.padding(top = 6.dp)) }
            if (aiConfigured == false && ai == null && aiError == null) {
                Text(
                    "The summary above is Anchor's own rule-based digest. Claude can write it instead once an API key is set on the relay.",
                    fontSize = 12.sp, color = Anchor.Muted, modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (aiConfigured != false || ai != null) {
                Spacer(Modifier.height(12.dp))
                GhostButton(if (aiLoading) "Writing…" else if (ai == null) "✨ Write this week's digest with AI" else "✨ Rewrite", enabled = !aiLoading && state.consented) {
                    aiLoading = true
                    aiError = null
                    scope.launch {
                        repo.aiDigest(CoPilot.digestInput(state, alerts))
                            .onSuccess { ai = it; aiConfigured = true }
                            .onFailure {
                                aiConfigured = false
                                aiError = "Claude isn't connected to this relay yet, so Anchor's own digest above is being used."
                            }
                        aiLoading = false
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${stats.streak}", "day streak", Modifier.weight(1f))
            StatTile("${stats.checkinsToday}", "check-ins today", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stats.latestMood?.let { "%.1f".format(it) } ?: "–", "latest mood /5", Modifier.weight(1f))
            StatTile("${stats.patterns}", "patterns flagged", Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            CardTitle("Worth a conversation?")
            alerts.forEach { a ->
                val flag = state.openFlags.firstOrNull { a.title.startsWith("${state.childName} reached out") }
                AnchorCard(color = if (a.type == AlertType.WARN) Anchor.WarnBg else Anchor.GoodBg, padding = 14.dp) {
                    Text((if (a.type == AlertType.WARN) "💬 " else "✅ ") + a.title, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(a.body, fontSize = 12.5.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    StarterText(a.starter)
                    if (a.showResources || flag != null) {
                        Row {
                            if (a.showResources) TextButton({ open(Overlay.Resources) }) { Text("Get support →") }
                            if (flag != null) TextButton({ repo.emit(EventType.FLAG_ACK, FlagAck(flag.flag.id)) }) { Text("We talked about it ✓") }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(18.dp))

        val tip = CoPilot.genZTips[LocalDate.now().dayOfYear % CoPilot.genZTips.size]
        AnchorCard(color = Anchor.Dark) {
            Kicker("Gen Z → Gen Alpha tip", Anchor.Secondary)
            Text(tip, color = Anchor.White, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(18.dp))

        if (state.consented && profile.role != Role.GUIDE) {
            AnchorCard {
                CardTitle("Send a kind word 💌", "Noticing the good stuff builds the trust that makes hard conversations possible.")
                val sent = remember { mutableStateListOf<String>() }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Proud of you for checking in 🌱", "Thanks for telling me 💛", "Phone-free dinner was fun 🍝", "Love you, kiddo").forEach { msg ->
                        AssistChip(onClick = { if (msg !in sent) { repo.emit(EventType.KUDOS, Kudos(msg)); sent += msg } }, label = { Text(if (msg in sent) "Sent ✓" else msg) })
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        if (state.shares.isNotEmpty()) {
            AnchorCard {
                CardTitle("${state.childName} shared with you ✨")
                state.shares.take(3).forEach { s ->
                    Text(s.value.text, style = MaterialTheme.typography.bodyMedium)
                    if (s.value.note.isNotBlank()) Text("“${s.value.note}” · ${ago(s.event.ts)}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        Footnote("This dashboard never shows raw video, audio, or camera frames, only the derived scores described above. Nothing here is a diagnosis; it's a prompt to start a conversation.")
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------ Trends

@Composable
fun ParentTrends(state: FamilyState, profile: DeviceProfile, open: (Overlay) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp)) {
        PageTitle("Trends", "20-day rolling view, not a minute-by-minute feed.")

        AnchorCard {
            CardTitle("Mood trend: last 20 days", "1 = rough day · 5 = great day. Daily average of Vibe Checks.")
            if (state.sharing.moods) MoodTrendChart(state.moodSeries(20)) else Text("${state.childName} isn't sharing moods right now.", color = Anchor.Muted)
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            val mix = state.weeklyCategoryMix()
            val sample = state.snapshots.values.any { it.sample }
            CardTitle("Screen-time category mix", if (sample) "Sample data in the demo. On a real device this comes from Android's screen-time API." else "Last 7 days, from Android's own screen-time API.")
            if (mix.isEmpty()) Text("No screen-time summary shared yet.", color = Anchor.Muted) else CategoryDonut(mix)
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            CardTitle("Transition reactions", "How intense the reaction was right after screen-off, last 10 sessions.")
            val bars = state.transitions.take(10).reversed().map { Bar(it.value.intensity, it.value.app.ifBlank { "·" }.take(8), it.value.intensity >= 3.5) }
            TransitionBars(bars)
            Legend(listOf(Anchor.Secondary to "Calm", Anchor.Accent to "Tough (≥ 3.5)"))
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            CardTitle("Screen time this week", "Goal ${formatMinutes(state.pact.dailyGoalMin)} a day, agreed together.")
            val week = state.weeklyMinutes()
            val maxV = (week.mapNotNull { it.second }.maxOrNull() ?: 0).coerceAtLeast(state.pact.dailyGoalMin).toDouble()
            TransitionBars(
                week.map { (d, m) -> Bar((m ?: 0).toDouble(), LocalDate.parse(d).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), (m ?: 0) > state.pact.dailyGoalMin) },
                max = maxV, height = 160f,
            )
            Legend(listOf(Anchor.Secondary to "Within goal", Anchor.Accent to "Over goal"))
        }
        Spacer(Modifier.height(18.dp))

        state.today?.let { t ->
            AnchorCard {
                CardTitle("Today's apps", if (t.appNamesShared) "Tap an app for the App Lens: what it is and what to talk about." else "${state.childName} chose to share categories only.")
                t.apps.take(8).forEach { a ->
                    val lens = AppCatalog.lensFor(a.pkg)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = t.appNamesShared) { open(Overlay.Lens(a.pkg, a.label, a.category)) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(Color(a.category.color))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, style = MaterialTheme.typography.titleSmall)
                            Text(a.category.label + (lens?.let { " · ${it.minAge}+" } ?: ""), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatMinutes(a.minutes), fontWeight = FontWeight.SemiBold)
                        if (t.appNamesShared) Text("  ›", color = Anchor.Muted)
                    }
                }
                if (t.lateNightMinutes > 0 || t.longestSessionMin > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("Longest session ${formatMinutes(t.longestSessionMin)}" + (if (t.longestSessionApp.isNotBlank()) " (${t.longestSessionApp})" else "") + " · after bedtime ${formatMinutes(t.lateNightMinutes)} · ${t.pickups} unlocks", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Footnote("Nothing here is a diagnosis. Anchor shows patterns so a family can talk, not so anyone can be watched.")
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------ Family

@Composable
fun ParentFamily(state: FamilyState, profile: DeviceProfile, repo: AnchorRepository, open: (Overlay) -> Unit, onReset: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp)) {
        PageTitle("Family")

        AnchorCard {
            CardTitle("Invite with the family code", "On your child's phone (or an older sibling's), install Anchor and enter this code together.")
            Text(profile.pairCode.ifBlank { "——" }, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp, fontFamily = FontFamily.Monospace, color = Anchor.Dark)
            if (profile.transport == Transport.LOCAL_DEMO) Text("Demo family: this code is illustrative. Use a relay to pair real phones.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            CardTitle("${state.childName.ifBlank { "Your child" }}'s sharing choices", "Set by your child. You'll always see when they change.")
            val s = state.sharing
            BulletLine(if (s.paused) "⏸️" else "▶️", if (s.paused) "Sharing is paused" else "Sharing is on")
            BulletLine(if (s.screenTime) "✅" else "🚫", "Screen-time summary")
            BulletLine(if (s.appNames) "✅" else "🚫", "App names (otherwise categories only)")
            BulletLine(if (s.moods) "✅" else "🚫", "Mood check-ins")
            BulletLine(if (s.transitions) "✅" else "🚫", "Transition checks")
            if (state.consented) Text("Agreed on ${java.time.Instant.ofEpochMilli(state.consentAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            val p = state.proposedPact ?: state.pact
            CardTitle("Family goals 🤝", if (state.hasPendingPact) "Waiting for ${state.childName.ifBlank { "your child" }} to agree." else "Agreed together. Anchor reflects on these; it never blocks apps.")
            BulletLine("⏳", "About ${formatMinutes(p.dailyGoalMin)} of screens a day")
            BulletLine("🌙", "Screens rest ${p.bedtime}–${p.wakeTime}")
            p.meals.forEach { BulletLine("🍽️", "${it.label} ${it.start}–${it.end}") }
            if (profile.role != Role.GUIDE) {
                Spacer(Modifier.height(10.dp))
                GhostButton("Suggest new goals") { open(Overlay.PactEditor) }
            }
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            CardTitle("Your circle")
            state.guardians.forEach {
                val roleLabel = if (it.role == Role.GUIDE) "Gen Z guide (older sibling / mentor)" else "Parent / caregiver"
                BulletLine(if (it.role == Role.GUIDE) "🧑‍🎤" else "🧑", if (it.name.equals("Parent", ignoreCase = true)) roleLabel else "${it.name} · $roleLabel")
            }
            if (state.consented) BulletLine(state.childAvatar, "${state.childName} · Gen Alpha, the reason we're here")
            Spacer(Modifier.height(6.dp))
            Text("Gen Z protecting Gen Alpha: invite an older sibling or cousin as a Guide. They see the same patterns and can be the first person a kid talks to.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard(onClick = { open(Overlay.Resources) }) {
            Text("🧭 Support resources", style = MaterialTheme.typography.titleSmall)
            Text("Helplines and when to talk to a professional", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            CardTitle("This device")
            Text("Mode: " + if (profile.transport == Transport.LOCAL_DEMO) "One-phone demo" else "Connected via Anchor Relay", style = MaterialTheme.typography.bodyMedium)
            if (profile.transport == Transport.RELAY) {
                Text(profile.relayUrl, style = MaterialTheme.typography.bodySmall)
                if (profile.relayUrl.startsWith("http://")) Text("⚠️ This relay isn't using HTTPS. Fine for a local demo; use HTTPS for real families.", fontSize = 12.sp, color = Anchor.Accent)
            }
            Spacer(Modifier.height(10.dp))
            GhostButton(if (profile.transport == Transport.LOCAL_DEMO) "Leave demo" else "Disconnect & reset", color = Anchor.Accent, onClick = onReset)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------ Sheets

@Composable
fun AppLensScreen(lens: Overlay.Lens, onClose: () -> Unit) {
    val info = AppCatalog.lensFor(lens.pkg)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Kicker("App Lens")
        Text(lens.label, style = MaterialTheme.typography.headlineMedium)
        Text(lens.category.label + (info?.let { " · Rated for ${it.minAge}+" } ?: ""), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        if (info == null) {
            Text("Anchor doesn't have a guide for this app yet. The best lens is still your child: ask them to show you around it.", style = MaterialTheme.typography.bodyLarge)
        } else {
            AnchorCard { Text(info.whatItIs, style = MaterialTheme.typography.bodyLarge) }
            if (info.watchFor.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                AnchorCard(color = Anchor.WarnBg) {
                    CardTitle("Worth knowing")
                    info.watchFor.forEach { BulletLine("•", it) }
                }
            }
            if (info.settingsToCheckTogether.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                AnchorCard(color = Anchor.GoodBg) {
                    CardTitle("Settings to check together", "Do it side by side, not behind their back.")
                    info.settingsToCheckTogether.forEach { BulletLine("✓", it) }
                }
            }
            Spacer(Modifier.height(14.dp))
            AnchorCard { StarterText(info.conversationStarter) }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Done", onClick = onClose)
    }
}

@Composable
fun ResourcesScreen(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("🧭", fontSize = 40.sp)
        Text("Support resources", style = MaterialTheme.typography.headlineSmall)
        Text("Anchor spots patterns; it can't diagnose anything. If something feels serious, reach out to a person.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        CoPilot.resources.forEach { r ->
            AnchorCard(padding = 14.dp) {
                Kicker(r.region, Anchor.Primary)
                Text(r.name, style = MaterialTheme.typography.titleSmall)
                Text(r.contact, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
        }
        AnchorCard(color = Anchor.WarnBg, padding = 14.dp) {
            Text("If a child is in immediate danger, call your local emergency number.", style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Done", onClick = onClose)
    }
}

@Composable
fun PactEditorScreen(state: FamilyState, repo: AnchorRepository, onClose: (String?) -> Unit) {
    val current = state.proposedPact ?: state.pact
    var goal by remember { mutableFloatStateOf(current.dailyGoalMin.toFloat()) }
    var bedtime by remember { mutableStateOf(current.bedtime) }
    var note by remember { mutableStateOf(current.note) }
    val bedtimes = listOf("20:30", "21:00", "21:30", "22:00", "22:30")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Suggest family goals 🤝", style = MaterialTheme.typography.headlineSmall)
        Text("${state.childName.ifBlank { "Your child" }} sees these and agrees before anything changes. Anchor never blocks apps.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
        Text("Daily screen goal: ${formatMinutes(goal.toInt())}", style = MaterialTheme.typography.titleSmall)
        Slider(goal, { goal = (it / 15).toInt() * 15f }, valueRange = 30f..300f, colors = SliderDefaults.colors(thumbColor = Anchor.Primary, activeTrackColor = Anchor.Primary))
        Spacer(Modifier.height(12.dp))
        Text("Screens rest from", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bedtimes.forEach { b -> FilterChip(selected = bedtime == b, onClick = { bedtime = b }, label = { Text(b) }) }
        }
        Spacer(Modifier.height(12.dp))
        current.meals.forEach { BulletLine("🍽️", "${it.label} ${it.start}–${it.end} phone-free") }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(note, { note = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("Why these goals? (your child will read this)") }, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel") { onClose(null) }
            PrimaryButton("Send to ${state.childName.ifBlank { "child" }}") {
                repo.emit(EventType.PACT_PROPOSED, current.copy(version = maxOf(current.version, state.acceptedPact?.version ?: 0) + 1, dailyGoalMin = goal.toInt(), bedtime = bedtime, note = note.trim(), proposedBy = repo.profile.value.myName, proposedAt = System.currentTimeMillis()))
                onClose("Goals sent. They'll apply once ${state.childName.ifBlank { "your child" }} agrees.")
            }
        }
    }
}

package com.anchor.copilot.ui.kid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.anchor.copilot.agent.CompanionService
import com.anchor.copilot.data.*
import com.anchor.copilot.ui.Overlay
import com.anchor.copilot.ui.components.*
import com.anchor.copilot.ui.theme.Anchor
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
fun Long.clock(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(timeFmt)

@Composable
fun rememberUsageAccess(): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(UsageCollector.hasUsageAccess(context)) }
    LifecycleResumeEffect(Unit) {
        granted = UsageCollector.hasUsageAccess(context)
        if (granted) CompanionService.start(context)
        onPauseOrDispose { }
    }
    return granted
}

// ------------------------------------------------------------------ Kid Home

@Composable
fun KidHome(state: FamilyState, profile: DeviceProfile, repo: AnchorRepository, open: (Overlay) -> Unit, openPrivacy: () -> Unit) {
    val context = LocalContext.current
    val usageAccess = rememberUsageAccess()
    var transitionStatus by remember { mutableStateOf("") }
    var simulating by remember { mutableStateOf(false) }
    LaunchedEffect(simulating) {
        if (simulating) {
            delay(1800)
            simulating = false
            transitionStatus = ""
            open(Overlay.Transition(app = "", category = null, minutes = 0, simulated = true))
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp)) {
        PageTitle("Hey ${state.childName.ifBlank { profile.myName }.ifBlank { "there" }} 👋")

        Banner {
            Column {
                Text(
                    buildString {
                        append("🔎 What's shared: only what you choose, your check-ins and a screen-time summary. ")
                        append("The camera turns on only when you tap it, and never records.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text("See exactly what my family can see →", color = Anchor.Primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.clickable(onClick = openPrivacy))
            }
        }
        Spacer(Modifier.height(18.dp))

        state.kudos.firstOrNull()?.takeIf { System.currentTimeMillis() - it.event.ts < 48 * 3600_000L }?.let { k ->
            AnchorCard(color = Anchor.GoodBg) {
                Text("💌 A kind word from ${k.event.fromName}", style = MaterialTheme.typography.titleSmall)
                Text(k.value.message, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(18.dp))
        }
        if (state.hasPendingPact) {
            AnchorCard(color = Anchor.WarnBg, onClick = openPrivacy) {
                Text("🤝 Your family suggested some goals", style = MaterialTheme.typography.titleSmall)
                Text("Nothing changes until you look and agree. Tap to review.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(18.dp))
        }

        // Sprout
        AnchorCard {
            CardTitle("Your Sprout ${sproutEmoji(1)}", "Grows every time you check in. Honest days count just as much as good ones.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bounce by rememberInfiniteTransition(label = "sprout").animateFloat(1f, 1.06f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "bounce")
                Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    Text(sproutEmoji(state.sproutStage), fontSize = 60.sp, modifier = Modifier.scale(bounce))
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    StreakBadge(state.streak)
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(state.xpInStage / 100f)
                    Spacer(Modifier.height(4.dp))
                    Text("Stage ${state.sproutStage} of 5 · ${state.checkinCount} check-ins", fontSize = 12.sp, color = Anchor.Muted)
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            CardTitle("Vibe Check ✨", "Takes about 15 seconds. Pick how you feel, or let the camera help you decide.")
            PrimaryButton("Start Vibe Check") { open(Overlay.VibeCheck) }
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            CardTitle("Transition Check ⏱️", "Right after a screen session ends, Anchor asks for one quick tap. No camera or mic involved.")
            if (usageAccess) {
                BulletLine("✅", "Auto-checks are on for this phone")
            } else {
                BulletLine("⚪", "Auto-checks need Usage access (screen time)")
                Spacer(Modifier.height(8.dp))
                GhostButton("Turn on auto-checks") { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }
            if (profile.transport == Transport.LOCAL_DEMO) {
                Spacer(Modifier.height(10.dp))
                GhostButton(if (simulating) "Simulating…" else "Simulate: Screen Turned Off", enabled = !simulating) {
                    transitionStatus = "Simulating screen-off… prompt will appear in a moment."
                    simulating = true
                }
            }
            if (transitionStatus.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(transitionStatus, fontSize = 13.sp, color = Anchor.Muted)
            }
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            CardTitle("Anchor Time ⚓", "Phone-free time you choose, like dinner, homework or going outside. Your Sprout loves it.")
            PrimaryButton("Drop anchor", color = Anchor.Dark) { open(Overlay.AnchorTime) }
        }
        Spacer(Modifier.height(18.dp))

        state.today?.takeIf { it.totalMinutes > 0 }?.let { t ->
            AnchorCard {
                CardTitle("Your day in screens", if (t.sample) "Sample data for the demo" else "From this phone's own screen-time counter")
                Text(formatMinutes(t.totalMinutes), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Anchor.Dark)
                Text("today · goal ${formatMinutes(state.pact.dailyGoalMin)}", fontSize = 12.sp, color = Anchor.Muted)
                Spacer(Modifier.height(8.dp))
                ProgressBar(t.totalMinutes / state.pact.dailyGoalMin.toFloat(), color = if (t.totalMinutes > state.pact.dailyGoalMin) Anchor.Accent else Anchor.Secondary)
                Spacer(Modifier.height(10.dp))
                t.apps.groupBy { it.category }.map { it.key to it.value.sumOf { a -> a.minutes } }.sortedByDescending { it.second }.take(4).forEach { (cat, m) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("${cat.emoji} ${cat.kidLabel}", Modifier.weight(1f), fontSize = 14.sp)
                        Text(formatMinutes(m), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        AnchorCard(color = Anchor.WarnBg) {
            CardTitle("Something bothered you? 🛟", "Saw something scary, or someone online made you uncomfortable? Telling your family is brave, and you won't be in trouble.")
            PrimaryButton("Tell my family", color = Anchor.Accent) { open(Overlay.Flag) }
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            CardTitle("Recent Check-Ins")
            val recent = (state.vibes.map { Triple(it.event.ts, Moods.emoji(it.value.level), it.value.label to it.value.sharedNote) } +
                state.transitions.map { Triple(it.event.ts, Moods.emoji(it.value.level), Moods.scale[it.value.level - 1].label to (if (it.value.app.isNotBlank()) "after ${it.value.app}" else "transition")) })
                .sortedByDescending { it.first }.take(6)
            if (recent.isEmpty()) Text("No check-ins yet today. Tap Vibe Check to start your streak.", color = Anchor.Muted, fontSize = 14.sp)
            recent.forEachIndexed { i, (ts, emoji, pair) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val day = if (ts.toLocalDate() == todayIso()) ts.clock() else Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE h:mm a"))
                    Text("$emoji $day" + if (pair.second.isNotBlank()) " · “${pair.second}”" else "", Modifier.weight(1f), fontSize = 13.5.sp)
                    MoodTag(pair.first)
                }
                if (i < recent.lastIndex) HorizontalDivider(color = Anchor.Line)
            }
        }
        Footnote("The camera is a mirror to help you see your own face. It never runs a face-reading model, never records, and nothing it sees leaves this phone. You always make the final call on how you feel.")
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------ Vibe Check

@Composable
fun VibeCheckScreen(repo: AnchorRepository, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Moods.Face?>(null) }
    var journal by remember { mutableStateOf("") }
    var shareNote by remember { mutableStateOf(false) }
    var cameraOn by remember { mutableStateOf(false) }
    var cameraUsed by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { cameraOn = true; cameraUsed = true } else cameraDenied = true
    }
    val sentiment = remember(journal) { Sentiment.analyze(journal) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text("How are you feeling right now?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Optional: turn on the camera to help you decide. It's only ever on right now, on this screen.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        if (cameraOn) {
            CameraMirror()
            Spacer(Modifier.height(10.dp))
            GhostButton("Turn camera off") { cameraOn = false }
        } else {
            GhostButton(if (cameraDenied) "Camera unavailable, pick an emoji below" else "Turn on camera", enabled = !cameraDenied) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraOn = true; cameraUsed = true
                } else permission.launch(Manifest.permission.CAMERA)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Moods.scale.forEach { face -> EmojiButton(face.emoji, selected == face, 56.dp) { selected = face } }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = journal,
            onValueChange = { journal = it.take(500) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
            placeholder = { Text("Want to say more? (totally optional, private to you)") },
            shape = RoundedCornerShape(12.dp),
        )
        AnimatedVisibility(journal.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sounds like: ", fontSize = 12.sp, color = Anchor.Muted)
                    MoodTag(sentiment?.top ?: "neutral")
                    Text("  · read on this phone only", fontSize = 12.sp, color = Anchor.Muted)
                }
                Row(Modifier.fillMaxWidth().clickable { shareNote = !shareNote }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = shareNote, onCheckedChange = { shareNote = it })
                    Text("Share this note with my family", fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", onClick = onClose)
            PrimaryButton("Save Check-In", enabled = selected != null) {
                val face = selected ?: return@PrimaryButton
                val note = journal.trim()
                val tone = Sentiment.analyze(note)?.top
                val label = if (tone == null || tone == "neutral") face.label else tone
                repo.emit(EventType.VIBE_CHECK, VibeCheck(face.value, label, cameraUsed, if (shareNote) note else "", note.isNotBlank() && !shareNote))
                if (note.isNotBlank()) repo.addPrivateNote(JournalNote(System.currentTimeMillis(), face.emoji, note))
                onSaved("+22 XP · your Sprout grew a little 🌱")
            }
        }
    }
}

// ------------------------------------------------------------------ Transition Check

@Composable
fun TransitionCheckScreen(repo: AnchorRepository, overlay: Overlay.Transition, onDone: (String) -> Unit) {
    val shownAt = remember { System.nanoTime() }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Quick check: how do you feel right now?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("(No camera, no mic. Just tap one.)", style = MaterialTheme.typography.bodySmall)
        if (overlay.app.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("You just finished ${formatMinutes(overlay.minutes)} on ${overlay.app}.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Moods.scale.forEach { face ->
                EmojiButton(face.emoji, false, 56.dp) {
                    val reactionMs = (System.nanoTime() - shownAt) / 1_000_000L
                    repo.emit(
                        EventType.TRANSITION,
                        TransitionCheck(face.value, (6 - face.value).toDouble(), reactionMs, overlay.app, overlay.category, overlay.minutes, overlay.simulated),
                    )
                    onDone("Logged in ${"%.1f".format(reactionMs / 1000.0)}s. Thanks for checking in.")
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Something bothered me

@Composable
fun FlagScreen(repo: AnchorRepository, onClose: () -> Unit, onSent: (String) -> Unit) {
    var kind by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("🛟", fontSize = 44.sp)
        Text("Tell your family", style = MaterialTheme.typography.headlineSmall)
        Text("You're not in trouble. Telling someone is the bravest move online.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        FlagKinds.all.forEach { (k, label) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (kind == k) Anchor.GoodBg else Anchor.Card).clickable { kind = k }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = kind == k, onClick = { kind = k })
                Text(label, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(note, { note = it.take(300) }, Modifier.fillMaxWidth(), placeholder = { Text("What happened? (optional)") }, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(10.dp))
        Text("Tip: don't delete the chat or post. A grown-up can help you block and report it.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", onClick = onClose)
            PrimaryButton("Send to my family", enabled = kind != null, color = Anchor.Accent) {
                repo.emit(EventType.FLAG, Flag(UUID.randomUUID().toString(), kind!!, note.trim()))
                onSent("Sent. Your family will check in with you soon 💛")
            }
        }
    }
}

// ------------------------------------------------------------------ Share to family (from the Android share sheet)

@Composable
fun ShareScreen(repo: AnchorRepository, text: String, onClose: () -> Unit, onSent: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("✨ Share with my family", style = MaterialTheme.typography.headlineSmall)
        Text("Only what you pick here is shared.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        AnchorCard { Text(text, style = MaterialTheme.typography.bodyLarge) }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(note, { note = it.take(200) }, Modifier.fillMaxWidth(), placeholder = { Text("Add a message? (optional)") }, shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", onClick = onClose)
            PrimaryButton("Share") {
                repo.emit(EventType.SHARE, SharedItem(UUID.randomUUID().toString(), text.take(500), note.trim()))
                onSent("Shared with your family")
            }
        }
    }
}

// ------------------------------------------------------------------ Anchor Time

@Composable
fun AnchorTimeScreen(repo: AnchorRepository, onClose: (String?) -> Unit) {
    val kinds = listOf("🍽️" to "Meal", "📚" to "Homework", "🌳" to "Outside", "🌙" to "Wind-down")
    var kind by remember { mutableStateOf(kinds[0]) }
    var minutes by remember { mutableIntStateOf(20) }
    var endAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) {
        while (endAt > 0) { now = System.currentTimeMillis(); delay(1000) }
    }
    val running = endAt > 0
    val left = (endAt - now).coerceAtLeast(0)
    val done = running && left == 0L

    Column(Modifier.fillMaxSize().background(if (running) Anchor.Dark else Anchor.White).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (!running) {
            Text("⚓", fontSize = 48.sp)
            Text("Drop anchor", style = MaterialTheme.typography.headlineSmall)
            Text("Pick what you're doing and for how long.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEach { k ->
                    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(if (kind == k) Anchor.GoodBg else Anchor.Card).clickable { kind = k }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(k.first, fontSize = 26.sp)
                        Text(k.second, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30, 45).forEach { m ->
                    FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") })
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Not now") { onClose(null) }
                PrimaryButton("Start") { endAt = System.currentTimeMillis() + minutes * 60_000L }
            }
        } else {
            val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "b")
            Box(Modifier.size(200.dp).scale(breathe).clip(CircleShape).background(Anchor.Primary.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Text(if (done) "🌸" else kind.first, fontSize = 64.sp)
            }
            Spacer(Modifier.height(28.dp))
            Text(if (done) "You did it!" else "%d:%02d".format(left / 60000, (left / 1000) % 60), color = Anchor.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(if (done) "Your Sprout is proud of you." else "${kind.second} time. Breathe in… and out.", color = Anchor.TabInactive, fontSize = 15.sp)
            Spacer(Modifier.height(28.dp))
            PrimaryButton(if (done) "Finish" else "I'm done early", color = Anchor.Secondary) {
                val spent = ((System.currentTimeMillis() - (endAt - minutes * 60_000L)) / 60_000L).toInt().coerceIn(1, minutes)
                repo.emit(EventType.FOCUS, FocusSession(spent, kind.second, completed = done || spent >= minutes))
                onClose(if (done) "Anchor Time complete ⚓" else "Nice, $spent phone-free minutes ⚓")
            }
        }
    }
}

// ------------------------------------------------------------------ What my family can see

@Composable
fun KidPrivacyScreen(state: FamilyState, profile: DeviceProfile, repo: AnchorRepository) {
    val context = LocalContext.current
    val usageAccess = rememberUsageAccess()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val family = state.guardians.joinToString(" & ") { it.name }.ifBlank { "your family" }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 22.dp)) {
        PageTitle("What your family can see 🔍", "You're a first-class user here, not a monitoring target.")

        AnchorCard {
            BulletLine("✅", "Your mood emoji, streak and Sprout")
            BulletLine("✅", "How long apps were used, by category")
            BulletLine("✅", "Whether check-ins are happening consistently")
            BulletLine("✅", "Anything you choose to tell or share")
            Spacer(Modifier.height(10.dp))
            BulletLine("🚫", "Never your camera video or audio")
            BulletLine("🚫", "Never your private journal text unless you choose to share it")
            BulletLine("🚫", "Never your messages, photos, what you type or what's on screen")
            BulletLine("🚫", "Never anything while sharing is paused")
        }
        Spacer(Modifier.height(18.dp))

        // Live mirror of exactly what the grown-up app shows.
        AnchorCard(color = Anchor.GoodBg) {
            Kicker("Right now $family can see", Anchor.Good)
            val lastVibe = state.vibes.firstOrNull()
            Text(
                buildString {
                    append("Streak ${state.streak} days · ${state.checkinCount} check-ins\n")
                    if (state.sharing.moods) append("Latest mood ${lastVibe?.let { Moods.emoji(it.value.level) } ?: "none yet"}") else append("Moods: not shared")
                    lastVibe?.value?.sharedNote?.takeIf { it.isNotBlank() && state.sharing.moods }?.let { append(" · note “$it”") }
                    append("\n")
                    val t = state.today
                    if (!state.sharing.screenTime) append("Screen time: not shared")
                    else if (t != null) append("Screen time today ${formatMinutes(t.totalMinutes)}" + if (t.appNamesShared) " (with app names)" else " (categories only)")
                    else append("Screen time: nothing yet today")
                    if (state.sharing.paused) append("\n⏸️ Sharing is paused, and they can see that it's paused")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            CardTitle("You're in control", "Changes are shown to your family so nobody is surprised.")
            fun update(s: SharingPrefs) = repo.emit(EventType.SHARING_CHANGED, s)
            val s = state.sharing
            ToggleRow("Screen-time summary", "Minutes per app category", s.screenTime, !s.paused) { update(s.copy(screenTime = it)) }
            ToggleRow("App names", "Off = categories only, like “Games”", s.appNames, !s.paused && s.screenTime) { update(s.copy(appNames = it)) }
            ToggleRow("Mood check-ins", "Your Vibe Check emoji", s.moods, !s.paused) { update(s.copy(moods = it)) }
            ToggleRow("Transition checks", "The one-tap check after screens", s.transitions, !s.paused) { update(s.copy(transitions = it)) }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Anchor.Line)
            ToggleRow("Pause all sharing", "Your family will see that sharing is paused", s.paused) { update(s.copy(paused = it)) }
        }
        Spacer(Modifier.height(18.dp))

        if (state.hasPendingPact || state.acceptedPact != null) {
            val p = state.proposedPact ?: state.pact
            AnchorCard(color = if (state.hasPendingPact) Anchor.WarnBg else Anchor.Card) {
                CardTitle(if (state.hasPendingPact) "🤝 New family goals to look at" else "🤝 Our family goals", "Anchor never blocks apps. Goals are for noticing, together.")
                BulletLine("⏳", "About ${formatMinutes(p.dailyGoalMin)} of screens a day")
                BulletLine("🌙", "Screens rest from ${p.bedtime} to ${p.wakeTime}")
                p.meals.forEach { BulletLine("🍽️", "${it.label} ${it.start}–${it.end} is phone-free") }
                if (p.note.isNotBlank()) BulletLine("💬", "“${p.note}”")
                if (state.hasPendingPact) {
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("I agree to these goals") { repo.emit(EventType.PACT_ACCEPTED, PactResponse(p.version)) }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        AnchorCard {
            CardTitle("This phone")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (usageAccess) "✅ Screen-time access on" else "⚪ Screen-time access off", Modifier.weight(1f), fontSize = 14.sp)
                if (!usageAccess) TextButton({ context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Turn on") }
            }
            val notifOk = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (notifOk) "✅ Notifications on" else "⚪ Notifications off", Modifier.weight(1f), fontSize = 14.sp)
                if (!notifOk) TextButton({ notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Turn on") }
            }
            Text("Family: " + (state.guardians.joinToString { "${it.name} (${if (it.role == Role.GUIDE) "Gen Z guide" else "grown-up"})" }.ifBlank { "not connected yet" }), fontSize = 13.sp, color = Anchor.Muted)
        }
        Spacer(Modifier.height(18.dp))

        AnchorCard {
            CardTitle("My private journal 🔒", "Only on this phone. Never uploaded.")
            if (profile.privateJournal.isEmpty()) Text("Notes you write in a Vibe Check appear here.", color = Anchor.Muted, fontSize = 14.sp)
            profile.privateJournal.take(8).forEach { n ->
                Text("${n.emoji} ${n.ts.clock()} · ${n.text}", fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

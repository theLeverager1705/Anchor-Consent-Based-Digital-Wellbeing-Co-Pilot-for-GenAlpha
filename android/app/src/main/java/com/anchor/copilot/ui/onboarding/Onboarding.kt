package com.anchor.copilot.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anchor.copilot.BuildConfig
import com.anchor.copilot.agent.CompanionService
import com.anchor.copilot.data.*
import com.anchor.copilot.ui.components.*
import com.anchor.copilot.ui.kid.rememberUsageAccess
import com.anchor.copilot.ui.theme.Anchor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Step { WELCOME, PARENT, GUIDE, KID }

@Composable
fun Onboarding(repo: AnchorRepository) {
    var step by remember { mutableStateOf(Step.WELCOME) }
    when (step) {
        Step.WELCOME -> Welcome(
            onDemo = { repo.startDemo("Parent") },
            onParent = { step = Step.PARENT },
            onKid = { step = Step.KID },
            onGuide = { step = Step.GUIDE },
        )
        Step.PARENT -> GrownUpSetup(repo, Role.PARENT) { step = Step.WELCOME }
        Step.GUIDE -> GrownUpSetup(repo, Role.GUIDE) { step = Step.WELCOME }
        Step.KID -> KidSetup(repo) { step = Step.WELCOME }
    }
}

@Composable
private fun Welcome(onDemo: () -> Unit, onParent: () -> Unit, onKid: () -> Unit, onGuide: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Anchor.Dark).verticalScroll(rememberScrollState()).systemBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        Box(Modifier.size(88.dp).clip(CircleShape).background(Anchor.Secondary), contentAlignment = Alignment.Center) { Text("⚓", fontSize = 44.sp) }
        Spacer(Modifier.height(18.dp))
        Text("ANCHOR", color = Anchor.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
        Spacer(Modifier.height(6.dp))
        Text("A consent-based digital wellbeing co-pilot for Gen Alpha families", color = Anchor.TabInactive, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Text("Not surveillance. A check-in kids choose, and a pattern parents can finally see.", color = Anchor.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 24.sp)
        Spacer(Modifier.height(36.dp))
        PrimaryButton("Try the demo on this phone", Modifier.fillMaxWidth(), color = Anchor.Accent, onClick = onDemo)
        Spacer(Modifier.height(10.dp))
        Text("Switch between Kid Mode and Parent Mode with sample data.", color = Anchor.TabInactive, fontSize = 12.sp)
        Spacer(Modifier.height(28.dp))
        Text("SET UP A REAL FAMILY", color = Anchor.Secondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        RoleButton("🧑", "I'm a parent or caregiver", "Create your family and get a code", onParent)
        RoleButton("🌱", "I'm a kid", "Set up together with a grown-up", onKid)
        RoleButton("🧑‍🎤", "I'm an older sibling or mentor", "Join as a Gen Z guide", onGuide)
        Spacer(Modifier.height(24.dp))
        Text("Nothing covert. Nothing diagnostic. Everything the child can see.", color = Anchor.TabInactive, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RoleButton(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Anchor.Dark2).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Anchor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(sub, color = Anchor.TabInactive, fontSize = 12.sp)
        }
        Text("›", color = Anchor.TabInactive, fontSize = 22.sp)
    }
}

@Composable
private fun SetupScaffold(title: String, sub: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).systemBarsPadding().imePadding().padding(24.dp)) {
        TextButton(onBack, contentPadding = PaddingValues(0.dp)) { Text("‹ Back") }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
        content()
    }
}

@Composable
private fun RelayField(url: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    TextButton({ expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) "Hide server settings" else "Server settings") }
    if (expanded) {
        OutlinedTextField(url, onChange, Modifier.fillMaxWidth(), label = { Text("Anchor Relay URL") }, singleLine = true, shape = RoundedCornerShape(12.dp))
        Text("Default is the hosted relay. For a laptop demo use http://<laptop-ip>:8787 (emulator: http://10.0.2.2:8787).", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GrownUpSetup(repo: AnchorRepository, role: Role, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var relay by remember { mutableStateOf(BuildConfig.DEFAULT_RELAY_URL) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var createdCode by remember { mutableStateOf<String?>(null) }
    val isParent = role == Role.PARENT

    if (createdCode != null) {
        SetupScaffold("Your family code", "Enter it on your child's phone, together. Nothing is shared until your child agrees.", onBack = {}) {
            AnchorCard(color = Anchor.GoodBg) {
                Text(createdCode!!, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 8.sp, color = Anchor.Dark)
            }
            Spacer(Modifier.height(16.dp))
            BulletLine("1️⃣", "Install Anchor on your child's device")
            BulletLine("2️⃣", "Choose “I'm a kid” and read the screens together")
            BulletLine("3️⃣", "Your child enters this code and chooses what to share")
            BulletLine("🧑‍🎤", "Older sibling or mentor? They can join with the same code as a Gen Z guide")
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Go to my dashboard", Modifier.fillMaxWidth()) { repo.updateProfile { it.copy(onboarded = true) } }
        }
        return
    }

    SetupScaffold(
        if (isParent) "Create your family" else "Join as a Gen Z guide",
        if (isParent) "You'll get a code to connect your child's device. Anchor only works with your child's agreement." else "Older siblings and mentors see the same patterns and can be the first person a kid talks to.",
        onBack,
    ) {
        OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("Your name (as your child knows you)") }, singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        if (!isParent) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(code, { code = it.uppercase().take(8) }, Modifier.fillMaxWidth(), label = { Text("Family code") }, singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
        }
        Spacer(Modifier.height(8.dp))
        RelayField(relay) { relay = it }
        error?.let { Text(it, color = Anchor.Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(if (busy) "Connecting…" else if (isParent) "Create family" else "Join family", Modifier.fillMaxWidth(), enabled = !busy && name.isNotBlank() && (isParent || code.length >= 4)) {
            busy = true
            error = null
            scope.launch {
                if (isParent) {
                    repo.createFamily(relay.trim(), name.trim(), role).onSuccess { createdCode = it }.onFailure { error = "Couldn't reach the relay: ${it.message}. Try again, or use the one-phone demo." }
                } else {
                    repo.joinFamily(relay.trim(), code, name.trim(), role).onSuccess { repo.updateProfile { p -> p.copy(onboarded = true) } }.onFailure { error = "Couldn't join: ${it.message}" }
                }
                busy = false
            }
        }
    }
}

@Composable
private fun KidSetup(repo: AnchorRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var page by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("🌱") }
    var code by remember { mutableStateOf("") }
    var relay by remember { mutableStateOf(BuildConfig.DEFAULT_RELAY_URL) }
    var sharing by remember { mutableStateOf(SharingPrefs()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val family by repo.family.collectAsState()
    val back: () -> Unit = { if (page == 0) onBack() else page -= 1 }

    when (page) {
        0 -> SetupScaffold("Hi! I'm Anchor ⚓", "Read this together with your grown-up.", back) {
            Text("I help you and your family keep screens fun, not stressful.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            BulletLine("🌱", "You grow a Sprout by checking in on how you feel. Honest days count just as much as good ones.")
            BulletLine("⏱️", "After screen time, I might ask one quick tap: how do you feel?")
            BulletLine("🛟", "If something online bothers you, you can tell your family in two taps.")
            BulletLine("🙅", "I'm not a spy. You'll always see what your family sees.")
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Next", Modifier.fillMaxWidth()) { page = 1 }
        }
        1 -> SetupScaffold("What your family can see 🔍", "And what they never can.", back) {
            AnchorCard {
                BulletLine("✅", "Your mood emoji, streak and Sprout")
                BulletLine("✅", "How long apps were used, by category")
                BulletLine("✅", "Whether check-ins are happening")
                Spacer(Modifier.height(10.dp))
                BulletLine("🚫", "Never your camera video or audio")
                BulletLine("🚫", "Never your private journal unless you share it")
                BulletLine("🚫", "Never your messages, photos or what you type")
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Got it", Modifier.fillMaxWidth()) { page = 2 }
        }
        2 -> SetupScaffold("You're in control", "Pick what to share. You can change this anytime, and your family will see the change.", back) {
            ToggleRow("Screen-time summary", "Minutes per app category", sharing.screenTime) { sharing = sharing.copy(screenTime = it) }
            ToggleRow("App names", "Off = categories only, like “Games”", sharing.appNames, sharing.screenTime) { sharing = sharing.copy(appNames = it) }
            ToggleRow("Mood check-ins", "Your Vibe Check emoji", sharing.moods) { sharing = sharing.copy(moods = it) }
            ToggleRow("Transition checks", "The one-tap check after screens", sharing.transitions) { sharing = sharing.copy(transitions = it) }
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Next", Modifier.fillMaxWidth()) { page = 3 }
        }
        3 -> SetupScaffold("About you", "Pick a name and a buddy.", back) {
            OutlinedTextField(name, { name = it.take(20) }, Modifier.fillMaxWidth(), label = { Text("Your first name") }, singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🌱", "🦊", "🐼", "🐙", "🦄", "🐢").forEach { a -> EmojiButton(a, avatar == a, 48.dp) { avatar = a } }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(code, { code = it.uppercase().take(8) }, Modifier.fillMaxWidth(), label = { Text("Family code from your grown-up") }, singleLine = true, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            RelayField(relay) { relay = it }
            error?.let { Text(it, color = Anchor.Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(if (busy) "Connecting…" else "Connect", Modifier.fillMaxWidth(), enabled = !busy && name.isNotBlank() && code.length >= 4) {
                busy = true
                error = null
                scope.launch {
                    repo.joinFamily(relay.trim(), code, name.trim(), Role.CHILD).onSuccess { page = 4 }.onFailure { error = "Couldn't connect: ${it.message}" }
                    busy = false
                }
            }
        }
        4 -> SetupScaffold("Our family pact 🤝", "Anchor never blocks apps. These goals are for noticing, together.", back) {
            val p = family.proposedPact ?: Pact()
            AnchorCard {
                BulletLine("⏳", "About ${formatMinutes(p.dailyGoalMin)} of screens a day")
                BulletLine("🌙", "Screens rest ${p.bedtime}–${p.wakeTime}")
                p.meals.forEach { BulletLine("🍽️", "${it.label} ${it.start}–${it.end} phone-free") }
                if (p.note.isNotBlank()) BulletLine("💬", "“${p.note}”")
            }
            Spacer(Modifier.height(16.dp))
            BulletLine("✋", "You can pause sharing anytime. Your family will see it's paused.")
            Spacer(Modifier.height(24.dp))
            HoldToAgree {
                repo.emit(EventType.CONSENT, ConsentPayload(name.trim(), avatar, sharing))
                family.proposedPact?.let { repo.emit(EventType.PACT_ACCEPTED, PactResponse(it.version)) }
                page = 5
            }
        }
        else -> {
            val usage = rememberUsageAccess()
            val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            SetupScaffold("Last step: permissions", "Android asks you directly, so you can see exactly what you're allowing.", back) {
                AnchorCard {
                    Text("📊 Usage access", style = MaterialTheme.typography.titleSmall)
                    Text("Lets Anchor count minutes per app and notice when a screen session ends. It can't see inside apps.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    if (usage) Text("✅ On", color = Anchor.Good, fontWeight = FontWeight.Bold)
                    else GhostButton("Open settings") { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                }
                Spacer(Modifier.height(12.dp))
                AnchorCard {
                    Text("🔔 Notifications", style = MaterialTheme.typography.titleSmall)
                    Text("For the one-tap Transition Check and the “Anchor is on” reminder.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    var notifOn by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
                    LaunchedEffect(Unit) { while (!notifOn) { delay(1000); notifOn = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() } }
                    if (notifOn) Text("✅ On", color = Anchor.Good, fontWeight = FontWeight.Bold)
                    else GhostButton("Allow notifications") { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Start Anchor", Modifier.fillMaxWidth()) {
                    repo.updateProfile { it.copy(onboarded = true, avatar = avatar, consentAt = System.currentTimeMillis()) }
                    CompanionService.start(context)
                }
            }
        }
    }
}

/** A deliberate, un-rushable "I agree": press and hold for 1.5 s. */
@Composable
private fun HoldToAgree(onAgreed: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (holding) 1f else 0f, tween(if (holding) 1500 else 200), label = "hold")
    LaunchedEffect(holding) {
        if (holding) {
            delay(1500)
            if (holding) onAgreed()
        }
    }
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(Anchor.Line)
            .pointerInput(Unit) { detectTapGestures(onPress = { holding = true; tryAwaitRelease(); holding = false }) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Anchor.Primary))
        Text(if (holding) "Keep holding…" else "Press and hold to agree together", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = if (progress > 0.5f) Anchor.White else Anchor.Dark)
    }
}

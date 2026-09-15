package com.anchor.copilot.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.anchor.copilot.MainActivity
import com.anchor.copilot.agent.CompanionService
import com.anchor.copilot.data.*
import com.anchor.copilot.ui.kid.*
import com.anchor.copilot.ui.onboarding.Onboarding
import com.anchor.copilot.ui.parent.*
import com.anchor.copilot.ui.theme.Anchor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Full-screen flows that sit on top of the tabs. */
sealed interface Overlay {
    data object VibeCheck : Overlay
    data class Transition(val app: String, val category: AppCategory?, val minutes: Int, val simulated: Boolean) : Overlay
    data object Flag : Overlay
    data class Share(val text: String) : Overlay
    data object AnchorTime : Overlay
    data class Lens(val pkg: String, val label: String, val category: AppCategory) : Overlay
    data object Resources : Overlay
    data object PactEditor : Overlay
}

@Composable
fun AnchorRoot(repo: AnchorRepository, launchIntent: Intent?, consumeIntent: () -> Unit) {
    val profile by repo.profile.collectAsState()
    val state by repo.family.collectAsState()
    val syncStatus by repo.syncStatus.collectAsState()

    if (!profile.onboarded) {
        Onboarding(repo)
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val kidView = if (profile.transport == Transport.LOCAL_DEMO) profile.demoViewAs == Role.CHILD else profile.role == Role.CHILD
    var kidTab by rememberSaveable { mutableStateOf(MainActivity.TAB_HOME) }
    var parentTab by rememberSaveable { mutableStateOf(MainActivity.TAB_DIGEST) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    fun toast(msg: String?) { if (msg != null) scope.launch { snackbar.showSnackbar(msg) } }

    // Keep a connected family live while the app is on screen (the worker covers the background).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(profile.transport, profile.familyId) {
        if (profile.transport != Transport.RELAY) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                repo.sync()
                delay(15_000)
            }
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Deep links from notifications and the Android share sheet.
    LaunchedEffect(launchIntent) {
        val intent = launchIntent ?: return@LaunchedEffect
        intent.getStringExtra(MainActivity.EXTRA_TRANSITION_APP)?.let { app ->
            if (profile.transport == Transport.LOCAL_DEMO) repo.setDemoView(Role.CHILD)
            overlay = Overlay.Transition(
                app,
                intent.getStringExtra(MainActivity.EXTRA_TRANSITION_CATEGORY)?.let { runCatching { AppCategory.valueOf(it) }.getOrNull() },
                intent.getIntExtra(MainActivity.EXTRA_TRANSITION_MINUTES, 0),
                simulated = false,
            )
        }
        intent.getStringExtra(MainActivity.EXTRA_TAB)?.let { tab ->
            if (tab == MainActivity.TAB_HOME || tab == MainActivity.TAB_PRIVACY) kidTab = tab else parentTab = tab
        }
        if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { text ->
                if (kidView) overlay = Overlay.Share(text) else toast("Sharing to family is part of Kid Mode")
            }
        }
        consumeIntent()
    }

    Box(Modifier.fillMaxSize().background(Anchor.White)) {
        Scaffold(
            topBar = { TopBar(profile, kidView, repo) },
            bottomBar = {
                NavigationBar(containerColor = Anchor.White, tonalElevation = 2.dp) {
                    val tabs = if (kidView) listOf(Triple(MainActivity.TAB_HOME, "🌱", "Home"), Triple(MainActivity.TAB_PRIVACY, "🔍", "What's shared"))
                    else listOf(Triple(MainActivity.TAB_DIGEST, "📋", "Digest"), Triple(MainActivity.TAB_TRENDS, "📈", "Trends"), Triple(MainActivity.TAB_FAMILY, "🤝", "Family"))
                    val current = if (kidView) kidTab else parentTab
                    tabs.forEach { (id, emoji, label) ->
                        NavigationBarItem(
                            selected = current == id,
                            onClick = { if (kidView) kidTab = id else parentTab = id },
                            icon = { Text(emoji, fontSize = 20.sp) },
                            label = { Text(label, fontWeight = if (current == id) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Anchor.GoodBg, selectedTextColor = Anchor.Dark),
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = Anchor.White,
        ) { inner ->
            Box(Modifier.padding(inner).fillMaxSize()) {
                if (kidView) {
                    when (kidTab) {
                        MainActivity.TAB_PRIVACY -> KidPrivacyScreen(state, profile, repo)
                        else -> KidHome(state, profile, repo, { overlay = it }, openPrivacy = { kidTab = MainActivity.TAB_PRIVACY })
                    }
                } else {
                    when (parentTab) {
                        MainActivity.TAB_TRENDS -> ParentTrends(state, profile) { overlay = it }
                        MainActivity.TAB_FAMILY -> ParentFamily(state, profile, repo, { overlay = it }) {
                            CompanionService.stop(context)
                            NotificationManagerCompat.from(context).cancelAll()
                            repo.reset()
                        }
                        else -> ParentDigest(state, profile, repo, syncStatus) { overlay = it }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = overlay,
            transitionSpec = { (fadeIn() + slideInVertically { it / 12 }) togetherWith fadeOut() },
            label = "overlay",
        ) { current ->
            if (current != null) {
                BackHandler { overlay = null }
                Surface(Modifier.fillMaxSize(), color = Anchor.White) {
                    Box(Modifier.systemBarsPadding().imePadding()) {
                        when (current) {
                            Overlay.VibeCheck -> VibeCheckScreen(repo, { overlay = null }) { overlay = null; toast(it) }
                            is Overlay.Transition -> TransitionCheckScreen(repo, current) { overlay = null; toast(it) }
                            Overlay.Flag -> FlagScreen(repo, { overlay = null }) { overlay = null; toast(it) }
                            is Overlay.Share -> ShareScreen(repo, current.text, { overlay = null }) { overlay = null; toast(it) }
                            Overlay.AnchorTime -> AnchorTimeScreen(repo) { overlay = null; toast(it) }
                            is Overlay.Lens -> AppLensScreen(current) { overlay = null }
                            Overlay.Resources -> ResourcesScreen { overlay = null }
                            Overlay.PactEditor -> PactEditorScreen(state, repo) { overlay = null; toast(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(profile: DeviceProfile, kidView: Boolean, repo: AnchorRepository) {
    Row(
        Modifier.fillMaxWidth().background(Anchor.Dark).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(Anchor.Secondary), contentAlignment = Alignment.Center) { Text("⚓", fontSize = 16.sp) }
        Spacer(Modifier.width(10.dp))
        Text("ANCHOR", color = Anchor.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (profile.transport == Transport.LOCAL_DEMO) {
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Anchor.Dark2).padding(4.dp)) {
                ModeTab("Kid Mode", kidView) { repo.setDemoView(Role.CHILD) }
                ModeTab("Parent Mode", !kidView) { repo.setDemoView(Role.PARENT) }
            }
        } else {
            val label = when (profile.role) { Role.CHILD -> "Kid"; Role.GUIDE -> "Gen Z guide"; else -> "Parent" }
            Text(label, color = Anchor.Dark, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Anchor.Secondary).padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}

@Composable
private fun ModeTab(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(9.dp)).background(if (active) Anchor.Secondary else Anchor.Dark2).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        color = if (active) Anchor.Dark else Anchor.TabInactive,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    )
}

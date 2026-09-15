package com.anchor.copilot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.anchor.copilot.agent.CompanionService
import com.anchor.copilot.data.AnchorRepository
import com.anchor.copilot.ui.AnchorRoot
import com.anchor.copilot.ui.theme.AnchorTheme

class MainActivity : ComponentActivity() {

    private val pendingIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingIntent.value = intent
        val repo = AnchorRepository.get(this)
        setContent {
            AnchorTheme {
                AnchorRoot(repo, pendingIntent.value) { pendingIntent.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }

    override fun onResume() {
        super.onResume()
        CompanionService.start(this)
        AnchorRepository.get(this).syncInBackground()
    }

    companion object {
        const val EXTRA_TAB = "anchor.tab"
        const val TAB_HOME = "home"
        const val TAB_PRIVACY = "privacy"
        const val TAB_DIGEST = "digest"
        const val TAB_TRENDS = "trends"
        const val TAB_FAMILY = "family"
        const val EXTRA_TRANSITION_APP = "anchor.transition.app"
        const val EXTRA_TRANSITION_CATEGORY = "anchor.transition.category"
        const val EXTRA_TRANSITION_MINUTES = "anchor.transition.minutes"
    }
}

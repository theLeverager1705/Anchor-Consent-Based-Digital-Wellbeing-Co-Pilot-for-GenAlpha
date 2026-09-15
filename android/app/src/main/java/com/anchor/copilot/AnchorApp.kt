package com.anchor.copilot

import android.app.Application
import com.anchor.copilot.agent.AgentActions
import com.anchor.copilot.agent.Notifier

class AnchorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        AgentActions.schedule(this)
    }
}

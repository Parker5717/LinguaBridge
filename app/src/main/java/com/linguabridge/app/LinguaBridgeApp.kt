package com.linguabridge.app

import android.app.Application
import com.linguabridge.app.notifications.Notifications

class LinguaBridgeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}

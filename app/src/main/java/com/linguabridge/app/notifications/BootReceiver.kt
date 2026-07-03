package com.linguabridge.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.linguabridge.app.LinguaBridgeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Alarms do not survive a reboot; re-arm whichever features are enabled. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as LinguaBridgeApp
                val settings = app.container.settingsRepository.settings.first()
                if (settings.wordNotificationsEnabled) WordNotifications.scheduleNext(context)
            } finally {
                pending.finish()
            }
        }
    }
}

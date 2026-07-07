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

/** Alarms survive neither a reboot nor an app update; re-arm whichever
 *  features are enabled on both events. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
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

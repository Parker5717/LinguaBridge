package com.linguabridge.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives word alarms; DB work runs off the main thread via
 *  [goAsync] (well under the broadcast time budget). */
class NotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    AlarmScheduler.ACTION_WORD -> WordNotifications.onAlarm(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

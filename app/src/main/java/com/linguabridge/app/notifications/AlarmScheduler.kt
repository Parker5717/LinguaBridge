package com.linguabridge.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Exact-ish alarms instead of WorkManager: Samsung defers deferrable work
 * until the app is opened, so time-based notifications never fired on their
 * own. [AlarmManager.setAndAllowWhileIdle] fires without the exact-alarm
 * permission and survives Doze (delivery may drift a few minutes, which is
 * fine for both notification kinds).
 */
object AlarmScheduler {

    const val ACTION_WORD = "com.linguabridge.app.action.WORD_ALARM"

    private const val REQUEST_WORD = 1

    fun scheduleWordAt(context: Context, triggerAtMillis: Long) =
        schedule(context, ACTION_WORD, REQUEST_WORD, triggerAtMillis)

    fun cancelWord(context: Context) = cancel(context, ACTION_WORD, REQUEST_WORD)

    private fun schedule(context: Context, action: String, requestCode: Int, at: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at,
            pendingIntent(context, action, requestCode),
        )
    }

    private fun cancel(context: Context, action: String, requestCode: Int) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(pendingIntent(context, action, requestCode))
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

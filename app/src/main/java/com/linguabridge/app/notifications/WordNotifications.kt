package com.linguabridge.app.notifications

import android.content.Context
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * "Word of the moment": one notification per fixed daily slot
 * (09:00, 11:30, 14:00, 16:30, 19:00). Rotation: 4 English words, then one
 * Chinese. The notified card is bumped to the front of the new-card queue so
 * it opens the next review session.
 */
object WordNotifications {

    private const val NOTIFICATION_ID = 100
    private val SLOTS = listOf(
        LocalTime.of(9, 0), LocalTime.of(11, 30), LocalTime.of(14, 0),
        LocalTime.of(16, 30), LocalTime.of(19, 0),
    )

    /** Fired by [NotificationAlarmReceiver]; shows the word and re-arms. */
    suspend fun onAlarm(context: Context) {
        val app = context.applicationContext as LinguaBridgeApp
        val settings = app.container.settingsRepository
        if (!settings.settings.first().wordNotificationsEnabled) return

        val counter = settings.nextWordNotificationCounter()
        val deckType = if (counter % 5 == 4) "zh_en" else "en_ru"

        val state = app.container.userDb.cardStateDao().newCardsByDeck(deckType, 1).firstOrNull()
        val card = state?.let { app.container.contentDb.cardDao().byId(it.cardId) }
        if (card != null) {
            app.container.userDb.cardStateDao().upsert(state.copy(addedAt = 0))
            val translation = card.back.lineSequence().first().trim()
            Notifications.show(
                context,
                Notifications.CHANNEL_WORDS,
                id = NOTIFICATION_ID,
                title = context.getString(R.string.notif_word_title, card.front),
                body = listOfNotNull(translation, card.example).joinToString("\n"),
            )
        }
        scheduleNext(context)
    }

    /** Arms an alarm for the next slot today, or the first slot tomorrow. */
    fun scheduleNext(context: Context) {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val next = SLOTS.map { now.toLocalDate().atTime(it) }
            .firstOrNull { it.isAfter(now) }
            ?: now.toLocalDate().plusDays(1).atTime(SLOTS.first())
        AlarmScheduler.scheduleWordAt(context, next.atZone(zone).toInstant().toEpochMilli())
    }

    fun cancel(context: Context) = AlarmScheduler.cancelWord(context)
}

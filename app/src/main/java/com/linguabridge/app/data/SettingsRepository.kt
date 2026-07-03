package com.linguabridge.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val newCardsPerDay: Int,
    val maxReviewsPerDay: Int,
    val ttsRate: Float,       // 0.7f..1.2f
    val theme: String,        // "system" | "light" | "dark"
    val dailyGoalReviews: Int,
    /** Card-type prefixes whose decks take part in study (e.g. "en_ru"). */
    val activeDecks: Set<String>,
    /** Current study language: "en" or "zh". Drives Today and review queues. */
    val studyLanguage: String,
    val wordNotificationsEnabled: Boolean,
    /** ISO date (yyyy-MM-dd) the daily word game was last completed, or null if never. */
    val wordGameDailyDate: String?,
    /** Serialized result of that day's daily game, e.g. "win:3" or "loss". Null if never. */
    val wordGameDailyResult: String?,
)

/** English words through Russian is the starting point; the user enables
 *  Chinese/terms decks himself once he is ready for them. */
val DEFAULT_ACTIVE_DECKS = setOf("en_ru")

/** Decks studied under the currently selected language. Chinese is its own
 *  track (zh_en only); English mode covers everything else that is enabled. */
fun AppSettings.decksForStudy(): Set<String> =
    if (studyLanguage == "zh") setOf("zh_en")
    else (activeDecks - "zh_en").ifEmpty { DEFAULT_ACTIVE_DECKS }

class SettingsRepository(private val context: Context) {

    private object Keys {
        val NEW_PER_DAY = intPreferencesKey("new_cards_per_day")
        val MAX_REVIEWS = intPreferencesKey("max_reviews_per_day")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val THEME = stringPreferencesKey("theme")
        val DAILY_GOAL = intPreferencesKey("daily_goal_reviews")
        val ACTIVE_DECKS = stringSetPreferencesKey("active_decks")
        val STUDY_LANGUAGE = stringPreferencesKey("study_language")
        val WORD_NOTIFICATIONS = booleanPreferencesKey("word_notifications_enabled")
        val WORD_NOTIF_COUNTER = intPreferencesKey("word_notification_counter")
        val WORDGAME_DAILY_DATE = stringPreferencesKey("wordgame_daily_date")
        val WORDGAME_DAILY_RESULT = stringPreferencesKey("wordgame_daily_result")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            newCardsPerDay = p[Keys.NEW_PER_DAY] ?: 20,
            maxReviewsPerDay = p[Keys.MAX_REVIEWS] ?: 200,
            ttsRate = p[Keys.TTS_RATE] ?: 1.0f,
            theme = p[Keys.THEME] ?: "system",
            dailyGoalReviews = p[Keys.DAILY_GOAL] ?: 30,
            activeDecks = p[Keys.ACTIVE_DECKS] ?: DEFAULT_ACTIVE_DECKS,
            studyLanguage = p[Keys.STUDY_LANGUAGE] ?: "en",
            wordNotificationsEnabled = p[Keys.WORD_NOTIFICATIONS] ?: false,
            wordGameDailyDate = p[Keys.WORDGAME_DAILY_DATE],
            wordGameDailyResult = p[Keys.WORDGAME_DAILY_RESULT],
        )
    }

    suspend fun setStudyLanguage(value: String) =
        context.dataStore.edit { it[Keys.STUDY_LANGUAGE] = value }

    suspend fun setWordNotificationsEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.WORD_NOTIFICATIONS] = value }

    /** Post-increments the rotation counter for word notifications. */
    suspend fun nextWordNotificationCounter(): Int {
        var current = 0
        context.dataStore.edit { p ->
            current = p[Keys.WORD_NOTIF_COUNTER] ?: 0
            p[Keys.WORD_NOTIF_COUNTER] = current + 1
        }
        return current
    }

    suspend fun setDeckActive(deckType: String, active: Boolean) =
        context.dataStore.edit { p ->
            val current = p[Keys.ACTIVE_DECKS] ?: DEFAULT_ACTIVE_DECKS
            p[Keys.ACTIVE_DECKS] = if (active) current + deckType else current - deckType
        }

    suspend fun setNewCardsPerDay(value: Int) =
        context.dataStore.edit { it[Keys.NEW_PER_DAY] = value.coerceIn(0, 200) }

    suspend fun setMaxReviewsPerDay(value: Int) =
        context.dataStore.edit { it[Keys.MAX_REVIEWS] = value.coerceIn(10, 1000) }

    suspend fun setTtsRate(value: Float) =
        context.dataStore.edit { it[Keys.TTS_RATE] = value.coerceIn(0.7f, 1.2f) }

    suspend fun setTheme(value: String) =
        context.dataStore.edit { it[Keys.THEME] = value }

    suspend fun setDailyGoalReviews(value: Int) =
        context.dataStore.edit { it[Keys.DAILY_GOAL] = value.coerceIn(5, 500) }

    /** Records the outcome of today's daily word game so it isn't replayed until tomorrow. */
    suspend fun setWordGameDaily(date: String, result: String) =
        context.dataStore.edit { p ->
            p[Keys.WORDGAME_DAILY_DATE] = date
            p[Keys.WORDGAME_DAILY_RESULT] = result
        }
}

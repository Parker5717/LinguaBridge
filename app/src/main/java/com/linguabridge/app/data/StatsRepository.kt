package com.linguabridge.app.data

import com.linguabridge.app.data.db.content.ContentDatabase
import com.linguabridge.app.data.db.user.DailyStatEntity
import com.linguabridge.app.data.db.user.UserDatabase
import java.time.LocalDate
import java.time.ZoneId

/** Per-deck learning progress shown on the Stats screen. */
data class SkillProgress(
    val deckType: String,
    val learnedCount: Int,
    val totalCount: Int,
)

/** Deck types tracked in the skill-progress breakdown (def_en is an alternate
 *  direction of en_ru, not a separate skill, so it is intentionally skipped). */
private val SKILL_DECKS = listOf("en_ru", "zh_en", "gram_term", "stem_en")

/** All-time totals shown in the Stats screen's totals row. */
data class StatsTotals(val reviews: Int, val dictations: Int, val quizzes: Int)

class StatsRepository(
    private val contentDb: ContentDatabase,
    private val userDb: UserDatabase,
) {

    /** Sum of reviews+dictations+quizzes per day for the trailing [weeks] weeks
     *  (including today), keyed by calendar date. Days with no activity/no
     *  daily_stat row are simply absent from the map. */
    suspend fun heatmap(weeks: Int = 26, today: LocalDate): Map<LocalDate, Int> {
        val fromDate = today.minusDays(weeks * 7L - 1)
        return userDb.dailyStatDao().since(fromDate.toString())
            .associate { LocalDate.parse(it.date) to it.activityTotal() }
    }

    /** Consecutive days with any activity, walking backwards from today.
     *  A miss today doesn't break the streak until tomorrow (so "yesterday
     *  had activity, today doesn't yet" still shows the streak as alive). */
    suspend fun streak(today: LocalDate): Int {
        val stats = userDb.dailyStatDao().since(today.minusDays(400).toString())
            .associateBy { LocalDate.parse(it.date) }

        fun hasActivity(day: LocalDate) = (stats[day]?.activityTotal() ?: 0) > 0

        var cursor = today
        if (!hasActivity(cursor)) {
            cursor = today.minusDays(1)
            if (!hasActivity(cursor)) return 0
        }
        var count = 0
        while (hasActivity(cursor)) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** Fraction of reviews rated better than AGAIN over the trailing [days]
     *  days, or null when there were no reviews in that window. */
    suspend fun retention(days: Int = 30, now: Long): Float? {
        val fromTs = now - days * 24L * 60 * 60 * 1000
        val total = userDb.reviewLogDao().countSince(fromTs)
        if (total == 0) return null
        val success = userDb.reviewLogDao().countSuccessSince(fromTs)
        return success.toFloat() / total.toFloat()
    }

    suspend fun skillProgress(): List<SkillProgress> = SKILL_DECKS.map { deckType ->
        SkillProgress(
            deckType = deckType,
            learnedCount = userDb.cardStateDao().countReviewByDeck(deckType),
            totalCount = contentDb.cardDao().countByType(deckType),
        )
    }

    suspend fun todayActivity(today: LocalDate): DailyStatEntity? =
        userDb.dailyStatDao().get(today.toString())

    /** All-time sums across every daily_stat row, for the Stats screen totals row. */
    suspend fun allTimeTotals(): StatsTotals {
        val all = userDb.dailyStatDao().all()
        return StatsTotals(
            reviews = all.sumOf { it.reviewsDone },
            dictations = all.sumOf { it.dictationsDone },
            quizzes = all.sumOf { it.quizzesDone },
        )
    }

    private fun DailyStatEntity.activityTotal(): Int =
        reviewsDone + dictationsDone + quizzesDone

    companion object {
        fun today(now: Long = System.currentTimeMillis()): LocalDate =
            java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}

package com.linguabridge.app.data

import com.linguabridge.app.data.db.content.CardEntity
import com.linguabridge.app.data.db.content.ContentDatabase
import com.linguabridge.app.data.db.user.CardStateEntity
import com.linguabridge.app.data.db.user.DailyStatEntity
import com.linguabridge.app.data.db.user.ReviewLogEntity
import com.linguabridge.app.data.db.user.UserDatabase
import com.linguabridge.app.domain.srs.CardPhase
import com.linguabridge.app.domain.srs.Rating
import com.linguabridge.app.domain.srs.SrsScheduler
import com.linguabridge.app.domain.srs.applyTo
import com.linguabridge.app.domain.srs.toSrsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A card due for review together with its content. */
data class ReviewCard(
    val card: CardEntity,
    val state: CardStateEntity,
)

data class QueueCounts(val newCount: Int, val learningCount: Int, val reviewCount: Int)

class SrsRepository(
    private val contentDb: ContentDatabase,
    private val userDb: UserDatabase,
    private val scheduler: SrsScheduler,
) {

    /** Enrolls every bundled card as 'new' exactly once. Cheap after first run. */
    suspend fun seedNewCards(now: Long) {
        val contentIds = contentDb.cardDao().allIds()
        if (userDb.cardStateDao().count() >= contentIds.size) return
        // added_at follows card id order so new cards are introduced deck by deck.
        val states = contentIds.mapIndexed { i, id ->
            CardStateEntity(
                cardId = id,
                state = "new",
                easeFactor = 2.5,
                intervalDays = 0.0,
                reps = 0,
                lapses = 0,
                learningStepIndex = 0,
                dueAt = now,
                lastReviewedAt = null,
                addedAt = now + i,
            )
        }
        userDb.cardStateDao().insertAllIgnore(states)
    }

    /** Due learning/review cards first (earliest due first), then new cards up
     *  to the remaining daily allowance. Only active decks take part. */
    suspend fun buildQueue(
        now: Long,
        newPerDay: Int,
        maxReviews: Int,
        activeDecks: Set<String>,
    ): List<ReviewCard> {
        val today = dateKey(now)
        val stat = userDb.dailyStatDao().get(today)
        val newAllowance = (newPerDay - (stat?.newLearned ?: 0)).coerceAtLeast(0)
        val reviewAllowance = (maxReviews - (stat?.reviewsDone ?: 0)).coerceAtLeast(0)

        val dao = userDb.cardStateDao()
        val due = activeDecks
            .flatMap { dao.dueByDeck(it, now, reviewAllowance) }
            .sortedBy { it.dueAt }
            .take(reviewAllowance)
        val fresh = activeDecks
            .flatMap { dao.newCardsByDeck(it, newAllowance) }
            .sortedBy { it.addedAt }
            .take(newAllowance)
        return attachContent(due + fresh)
    }

    /** Earliest upcoming due time among started cards in [decks], if any. */
    suspend fun nextDueAt(decks: Set<String>): Long? =
        decks.mapNotNull { userDb.cardStateDao().nextDueByDeck(it) }.minOrNull()

    /** New cards beyond the daily allowance, for an explicit "learn more". */
    suspend fun extraNewCards(n: Int, decks: Set<String>): List<ReviewCard> =
        attachContent(
            decks.flatMap { userDb.cardStateDao().newCardsByDeck(it, n) }
                .sortedBy { it.addedAt }
                .take(n)
        )

    /** Backs (or fronts, for reverse choices) of random same-deck cards used
     *  as wrong options in choice exercises. */
    suspend fun distractors(card: CardEntity, useFronts: Boolean, n: Int = 6): List<String> =
        contentDb.cardDao()
            .randomByType(card.cardType, card.id, n)
            .map { if (useFronts) it.front else it.back }

    /** Applies a rating: schedules the card, logs the review, bumps daily stats. */
    suspend fun answer(state: CardStateEntity, rating: Rating, now: Long): CardStateEntity {
        val before = state.toSrsState()
        val after = scheduler.next(before, rating, now)
        val updated = after.applyTo(state, now)

        userDb.cardStateDao().upsert(updated)
        userDb.reviewLogDao().insert(
            ReviewLogEntity(
                cardId = state.cardId,
                ts = now,
                rating = rating.ordinal,
                intervalAfterDays = after.intervalDays,
            )
        )
        bumpDailyStat(now, wasNew = before.phase == CardPhase.NEW)
        return updated
    }

    fun countDue(now: Long, decks: Set<String>): Flow<Int> =
        sumPerDeck(decks) { userDb.cardStateDao().countDueByDeck(it, now) }

    fun countNew(decks: Set<String>): Flow<Int> =
        sumPerDeck(decks) { userDb.cardStateDao().countByStateByDeck(it, "new") }

    fun countLearning(decks: Set<String>): Flow<Int> =
        sumPerDeck(decks) { userDb.cardStateDao().countLearningByDeck(it) }

    private fun sumPerDeck(decks: Set<String>, source: (String) -> Flow<Int>): Flow<Int> =
        if (decks.isEmpty()) flowOf(0)
        else combine(decks.map(source)) { counts -> counts.sum() }

    suspend fun todayStat(now: Long): DailyStatEntity? =
        userDb.dailyStatDao().get(dateKey(now))

    private suspend fun attachContent(states: List<CardStateEntity>): List<ReviewCard> {
        if (states.isEmpty()) return emptyList()
        val cards = contentDb.cardDao().byIds(states.map { it.cardId }).associateBy { it.id }
        return states.mapNotNull { s -> cards[s.cardId]?.let { ReviewCard(it, s) } }
    }

    private suspend fun bumpDailyStat(now: Long, wasNew: Boolean) {
        val key = dateKey(now)
        val current = userDb.dailyStatDao().get(key)
            ?: DailyStatEntity(key, 0, 0, 0, 0)
        userDb.dailyStatDao().upsert(
            current.copy(
                reviewsDone = current.reviewsDone + 1,
                newLearned = current.newLearned + if (wasNew) 1 else 0,
            )
        )
    }

    companion object {
        fun dateKey(now: Long): String =
            Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()

        fun todayKey(): String = LocalDate.now().toString()
    }
}

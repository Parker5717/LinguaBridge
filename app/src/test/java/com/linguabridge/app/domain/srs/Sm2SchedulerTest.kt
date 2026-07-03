package com.linguabridge.app.domain.srs

import com.linguabridge.app.data.db.user.CardStateEntity
import org.junit.Test
import kotlin.test.assertEquals

private const val NOW = 1_000_000_000_000L
private const val MINUTE = 60_000L
private const val DAY = 24L * 60L * 60L * 1000L
private const val EPS = 1e-9

class Sm2SchedulerTest {

    private val scheduler = Sm2Scheduler()

    // --- NEW card, each rating -------------------------------------------------------------

    @Test
    fun `new card AGAIN enters learning at step 0`() {
        val result = scheduler.next(CardSrsState.initial(NOW), Rating.AGAIN, NOW)
        assertEquals(CardPhase.LEARNING, result.phase)
        assertEquals(0, result.learningStepIndex)
        assertEquals(NOW + MINUTE, result.dueAt)
        assertEquals(2.5, result.easeFactor, EPS)
    }

    @Test
    fun `new card HARD enters learning at step 0`() {
        val result = scheduler.next(CardSrsState.initial(NOW), Rating.HARD, NOW)
        assertEquals(CardPhase.LEARNING, result.phase)
        assertEquals(0, result.learningStepIndex)
        assertEquals(NOW + MINUTE, result.dueAt)
    }

    @Test
    fun `new card GOOD enters learning at step 1`() {
        val result = scheduler.next(CardSrsState.initial(NOW), Rating.GOOD, NOW)
        assertEquals(CardPhase.LEARNING, result.phase)
        assertEquals(1, result.learningStepIndex)
        assertEquals(NOW + 10 * MINUTE, result.dueAt)
    }

    @Test
    fun `new card EASY graduates immediately at 4 days`() {
        val result = scheduler.next(CardSrsState.initial(NOW), Rating.EASY, NOW)
        assertEquals(CardPhase.REVIEW, result.phase)
        assertEquals(4.0, result.intervalDays, EPS)
        assertEquals(NOW + 4 * DAY, result.dueAt)
        assertEquals(2.5, result.easeFactor, EPS) // ease unchanged on EASY graduation from NEW
    }

    // --- Full graduation path ----------------------------------------------------------------

    @Test
    fun `full graduation path NEW GOOD GOOD reaches REVIEW with 1 day interval`() {
        var state = CardSrsState.initial(NOW)
        state = scheduler.next(state, Rating.GOOD, NOW) // -> LEARNING step 1
        assertEquals(CardPhase.LEARNING, state.phase)
        assertEquals(1, state.learningStepIndex)

        state = scheduler.next(state, Rating.GOOD, NOW) // -> graduate
        assertEquals(CardPhase.REVIEW, state.phase)
        assertEquals(1.0, state.intervalDays, EPS)
        assertEquals(NOW + DAY, state.dueAt)
        assertEquals(0, state.reps) // reps counts only successful REVIEW-phase reviews, not graduation
    }

    @Test
    fun `learning AGAIN resets to step 0`() {
        var state = CardSrsState.initial(NOW)
        state = scheduler.next(state, Rating.GOOD, NOW) // step 1
        state = scheduler.next(state, Rating.AGAIN, NOW)
        assertEquals(CardPhase.LEARNING, state.phase)
        assertEquals(0, state.learningStepIndex)
        assertEquals(NOW + MINUTE, state.dueAt)
    }

    @Test
    fun `learning HARD repeats current step`() {
        var state = CardSrsState.initial(NOW)
        state = scheduler.next(state, Rating.GOOD, NOW) // step 1
        state = scheduler.next(state, Rating.HARD, NOW)
        assertEquals(CardPhase.LEARNING, state.phase)
        assertEquals(1, state.learningStepIndex)
        assertEquals(NOW + 10 * MINUTE, state.dueAt)
    }

    @Test
    fun `learning EASY graduates immediately at 4 days`() {
        var state = CardSrsState.initial(NOW)
        state = scheduler.next(state, Rating.AGAIN, NOW) // step 0
        state = scheduler.next(state, Rating.EASY, NOW)
        assertEquals(CardPhase.REVIEW, state.phase)
        assertEquals(4.0, state.intervalDays, EPS)
        assertEquals(NOW + 4 * DAY, state.dueAt)
    }

    // --- REVIEW interval / ease math ----------------------------------------------------------

    private fun reviewState(intervalDays: Double, easeFactor: Double = 2.5, reps: Int = 0): CardSrsState =
        CardSrsState(
            phase = CardPhase.REVIEW,
            easeFactor = easeFactor,
            intervalDays = intervalDays,
            reps = reps,
            lapses = 0,
            learningStepIndex = 0,
            dueAt = NOW,
        )

    @Test
    fun `review GOOD grows interval by ease factor`() {
        val result = scheduler.next(reviewState(intervalDays = 4.0, easeFactor = 2.5), Rating.GOOD, NOW)
        assertEquals(10.0, result.intervalDays, EPS)
        assertEquals(2.5, result.easeFactor, EPS) // ease unchanged on GOOD
        assertEquals(1, result.reps)
        assertEquals(NOW + (10.0 * DAY).toLong(), result.dueAt)
    }

    @Test
    fun `review HARD grows interval by 1point2 and drops ease by 0point15`() {
        val result = scheduler.next(reviewState(intervalDays = 10.0, easeFactor = 2.5), Rating.HARD, NOW)
        assertEquals(12.0, result.intervalDays, EPS)
        assertEquals(2.35, result.easeFactor, EPS)
        assertEquals(1, result.reps)
    }

    @Test
    fun `review EASY grows interval by ease times 1point3 and raises ease by 0point15`() {
        val result = scheduler.next(reviewState(intervalDays = 10.0, easeFactor = 2.5), Rating.EASY, NOW)
        assertEquals(32.5, result.intervalDays, EPS)
        assertEquals(2.65, result.easeFactor, EPS)
        assertEquals(1, result.reps)
    }

    @Test
    fun `ease factor never drops below 1point3 after many HARD reviews`() {
        var state = reviewState(intervalDays = 10.0, easeFactor = 2.5)
        repeat(15) {
            state = scheduler.next(state, Rating.HARD, NOW)
        }
        assertEquals(1.3, state.easeFactor, EPS)
    }

    @Test
    fun `ease factor never drops below 1point3 after many lapses`() {
        var state = reviewState(intervalDays = 10.0, easeFactor = 2.5)
        repeat(15) {
            // Force back into REVIEW after each lapse so the next AGAIN also hits onReview.
            state = scheduler.next(state, Rating.AGAIN, NOW)
            state = state.copy(phase = CardPhase.REVIEW)
        }
        assertEquals(1.3, state.easeFactor, EPS)
    }

    // --- Lapses / relearning -------------------------------------------------------------------

    @Test
    fun `review AGAIN is a lapse that enters relearning`() {
        val start = reviewState(intervalDays = 10.0, easeFactor = 2.5, reps = 3)
        val result = scheduler.next(start, Rating.AGAIN, NOW)
        assertEquals(CardPhase.RELEARNING, result.phase)
        assertEquals(0, result.learningStepIndex)
        assertEquals(1, result.lapses)
        assertEquals(2.3, result.easeFactor, EPS)
        assertEquals(NOW + MINUTE, result.dueAt)
        // pending interval stashed for later graduation: max(1, 10*0.5) capped at 10 = 5.0
        assertEquals(5.0, result.intervalDays, EPS)
    }

    @Test
    fun `relearning graduation restores capped pending interval of at least 1 day`() {
        val start = reviewState(intervalDays = 10.0, easeFactor = 2.5)
        var state = scheduler.next(start, Rating.AGAIN, NOW) // -> RELEARNING step 0, pending 5.0
        state = scheduler.next(state, Rating.GOOD, NOW) // -> RELEARNING step 1
        assertEquals(CardPhase.RELEARNING, state.phase)
        assertEquals(1, state.learningStepIndex)

        state = scheduler.next(state, Rating.GOOD, NOW) // -> graduate back to REVIEW
        assertEquals(CardPhase.REVIEW, state.phase)
        assertEquals(5.0, state.intervalDays, EPS)
        assertEquals(NOW + (5.0 * DAY).toLong(), state.dueAt)
    }

    @Test
    fun `relearning graduation floors pending interval at 1 day for short intervals`() {
        val start = reviewState(intervalDays = 1.0, easeFactor = 2.5)
        var state = scheduler.next(start, Rating.AGAIN, NOW) // pending = max(1, 0.5) = 1.0
        assertEquals(1.0, state.intervalDays, EPS)
        state = scheduler.next(state, Rating.GOOD, NOW) // step 1
        state = scheduler.next(state, Rating.GOOD, NOW) // graduate
        assertEquals(CardPhase.REVIEW, state.phase)
        assertEquals(1.0, state.intervalDays, EPS)
    }

    @Test
    fun `relearning AGAIN resets to step 0`() {
        val start = reviewState(intervalDays = 10.0, easeFactor = 2.5)
        var state = scheduler.next(start, Rating.AGAIN, NOW) // step 0
        state = scheduler.next(state, Rating.GOOD, NOW) // step 1
        state = scheduler.next(state, Rating.AGAIN, NOW) // back to step 0
        assertEquals(CardPhase.RELEARNING, state.phase)
        assertEquals(0, state.learningStepIndex)
        assertEquals(NOW + MINUTE, state.dueAt)
    }

    @Test
    fun `relearning HARD repeats current step`() {
        val start = reviewState(intervalDays = 10.0, easeFactor = 2.5)
        var state = scheduler.next(start, Rating.AGAIN, NOW) // step 0
        state = scheduler.next(state, Rating.HARD, NOW)
        assertEquals(CardPhase.RELEARNING, state.phase)
        assertEquals(0, state.learningStepIndex)
        assertEquals(NOW + MINUTE, state.dueAt)
    }

    // --- Interval cap ----------------------------------------------------------------------

    @Test
    fun `interval is capped at 365 days`() {
        val start = reviewState(intervalDays = 300.0, easeFactor = 3.0)
        val result = scheduler.next(start, Rating.GOOD, NOW)
        assertEquals(365.0, result.intervalDays, EPS)
        assertEquals(NOW + 365 * DAY, result.dueAt)
    }

    @Test
    fun `interval cap also applies to EASY reviews`() {
        val start = reviewState(intervalDays = 300.0, easeFactor = 3.0)
        val result = scheduler.next(start, Rating.EASY, NOW)
        assertEquals(365.0, result.intervalDays, EPS)
    }

    // --- Mapper round-trip -------------------------------------------------------------------

    @Test
    fun `mapper round-trip preserves fields and updates lastReviewedAt`() {
        val entity = CardStateEntity(
            cardId = "card-1",
            state = "review",
            easeFactor = 2.3,
            intervalDays = 12.5,
            reps = 4,
            lapses = 1,
            learningStepIndex = 0,
            dueAt = NOW,
            lastReviewedAt = NOW - DAY,
            addedAt = NOW - 30 * DAY,
        )

        val srsState = entity.toSrsState()
        assertEquals(CardPhase.REVIEW, srsState.phase)
        assertEquals(2.3, srsState.easeFactor, EPS)
        assertEquals(12.5, srsState.intervalDays, EPS)
        assertEquals(4, srsState.reps)
        assertEquals(1, srsState.lapses)
        assertEquals(0, srsState.learningStepIndex)
        assertEquals(NOW, srsState.dueAt)

        val nextState = Sm2Scheduler().next(srsState, Rating.GOOD, NOW)
        val updatedEntity = nextState.applyTo(entity, NOW)

        // Fields not tracked by CardSrsState are preserved from the original entity.
        assertEquals("card-1", updatedEntity.cardId)
        assertEquals(NOW - 30 * DAY, updatedEntity.addedAt)
        // lastReviewedAt is stamped with `now`.
        assertEquals(NOW, updatedEntity.lastReviewedAt)
        // Updated scheduling fields reflect the new state.
        assertEquals("review", updatedEntity.state)
        assertEquals(nextState.intervalDays, updatedEntity.intervalDays, EPS)
        assertEquals(nextState.easeFactor, updatedEntity.easeFactor, EPS)
        assertEquals(nextState.reps, updatedEntity.reps)
        assertEquals(nextState.dueAt, updatedEntity.dueAt)
    }

    @Test
    fun `mapper maps all phase strings both directions`() {
        val phases = mapOf(
            "new" to CardPhase.NEW,
            "learning" to CardPhase.LEARNING,
            "review" to CardPhase.REVIEW,
            "relearning" to CardPhase.RELEARNING,
        )
        for ((dbValue, phase) in phases) {
            val entity = CardStateEntity(
                cardId = "c",
                state = dbValue,
                easeFactor = 2.5,
                intervalDays = 0.0,
                reps = 0,
                lapses = 0,
                learningStepIndex = 0,
                dueAt = NOW,
                lastReviewedAt = null,
                addedAt = NOW,
            )
            assertEquals(phase, entity.toSrsState().phase)

            val roundTripped = entity.toSrsState().applyTo(entity, NOW)
            assertEquals(dbValue, roundTripped.state)
        }
    }

    @Test
    fun `custom learning steps are respected via constructor injection`() {
        val customScheduler = Sm2Scheduler(learningStepsMillis = listOf(30_000L))
        val result = customScheduler.next(CardSrsState.initial(NOW), Rating.AGAIN, NOW)
        assertEquals(CardPhase.LEARNING, result.phase)
        assertEquals(0, result.learningStepIndex)
        assertEquals(NOW + 30_000L, result.dueAt)

        // Single-step config: GOOD from NEW should graduate immediately since there is no step 1.
        val graduated = customScheduler.next(CardSrsState.initial(NOW), Rating.GOOD, NOW)
        assertEquals(CardPhase.REVIEW, graduated.phase)
        assertEquals(1.0, graduated.intervalDays, EPS)
    }
}

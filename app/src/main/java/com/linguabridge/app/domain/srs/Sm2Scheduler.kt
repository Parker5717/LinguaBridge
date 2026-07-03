package com.linguabridge.app.domain.srs

import kotlin.math.min

/** One day in milliseconds, used to convert fractional interval days to a `dueAt` timestamp. */
private const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

/**
 * Anki-flavoured SM-2 scheduler.
 *
 * Learning/relearning progress through short, fixed [learningStepsMillis]. Once a card
 * graduates it is scheduled in whole-ish days using the classic SM-2 ease-factor formula,
 * with Anki's "Hard"/"Easy" bonuses layered on top.
 *
 * @param learningStepsMillis ordered learning steps (e.g. 1 min, 10 min). Must be non-empty.
 * @param graduatingIntervalDays interval applied when a card graduates from the *last*
 *   learning step via GOOD.
 * @param easyGraduatingIntervalDays interval applied when a NEW or LEARNING card graduates
 *   immediately via EASY.
 * @param maxIntervalDays hard cap applied to every computed review interval.
 */
class Sm2Scheduler(
    private val learningStepsMillis: List<Long> = listOf(1L * 60_000L, 10L * 60_000L),
    private val graduatingIntervalDays: Double = 1.0,
    private val easyGraduatingIntervalDays: Double = 4.0,
    private val maxIntervalDays: Double = 365.0,
) : SrsScheduler {

    init {
        require(learningStepsMillis.isNotEmpty()) { "learningStepsMillis must not be empty" }
    }

    private val lastStepIndex: Int get() = learningStepsMillis.lastIndex

    override fun next(state: CardSrsState, rating: Rating, now: Long): CardSrsState {
        return when (state.phase) {
            CardPhase.NEW -> onNew(state, rating, now)
            CardPhase.LEARNING -> onLearning(state, rating, now)
            CardPhase.REVIEW -> onReview(state, rating, now)
            CardPhase.RELEARNING -> onRelearning(state, rating, now)
        }
    }

    // --- NEW -----------------------------------------------------------------------------

    private fun onNew(state: CardSrsState, rating: Rating, now: Long): CardSrsState = when (rating) {
        // AGAIN/HARD on a brand-new card: begin learning at the first step.
        Rating.AGAIN, Rating.HARD -> enterLearningStep(state, stepIndex = 0, now = now)
        // GOOD on a brand-new card: advance into the next learning step (or graduate
        // immediately if there is only a single learning step configured).
        Rating.GOOD -> {
            val nextStep = 1
            if (nextStep > lastStepIndex) {
                graduate(state, now, graduatingIntervalDays)
            } else {
                enterLearningStep(state, stepIndex = nextStep, now = now)
            }
        }
        // EASY skips learning altogether and graduates straight to REVIEW.
        Rating.EASY -> graduate(state, now, easyGraduatingIntervalDays)
    }

    // --- LEARNING --------------------------------------------------------------------------

    private fun onLearning(state: CardSrsState, rating: Rating, now: Long): CardSrsState = when (rating) {
        // AGAIN sends the card back to the very first learning step.
        Rating.AGAIN -> enterLearningStep(state, stepIndex = 0, now = now)
        // HARD repeats the current step (same index, due after the same step interval again).
        Rating.HARD -> enterLearningStep(state, stepIndex = state.learningStepIndex, now = now)
        // GOOD advances to the next step, or graduates to REVIEW (1 day) if this was the last step.
        Rating.GOOD -> {
            val nextStep = state.learningStepIndex + 1
            if (nextStep > lastStepIndex) {
                graduate(state, now, graduatingIntervalDays)
            } else {
                enterLearningStep(state, stepIndex = nextStep, now = now)
            }
        }
        // EASY graduates immediately regardless of which step the card is on.
        Rating.EASY -> graduate(state, now, easyGraduatingIntervalDays)
    }

    // --- REVIEW ----------------------------------------------------------------------------

    private fun onReview(state: CardSrsState, rating: Rating, now: Long): CardSrsState = when (rating) {
        // AGAIN is a lapse: drop into RELEARNING, bump lapses, penalize ease, and compute the
        // interval that will be restored once relearning is complete (half the old interval,
        // floor 1 day, capped at the old interval so it can never grow from a lapse).
        Rating.AGAIN -> {
            val newEase = floorEase(state.easeFactor - 0.20)
            val pendingInterval = min(maxOf(1.0, state.intervalDays * 0.5), state.intervalDays)
            enterLearningStep(
                state = state.copy(
                    easeFactor = newEase,
                    lapses = state.lapses + 1,
                    intervalDays = pendingInterval,
                ),
                stepIndex = 0,
                now = now,
                phase = CardPhase.RELEARNING,
            )
        }
        // HARD: small interval bump (x1.2), ease penalty (-0.15), counts as a successful rep.
        Rating.HARD -> {
            val newEase = floorEase(state.easeFactor - 0.15)
            val newInterval = cappedInterval(state.intervalDays * 1.2)
            state.copy(
                easeFactor = newEase,
                intervalDays = newInterval,
                reps = state.reps + 1,
                dueAt = now + daysToMillis(newInterval),
            )
        }
        // GOOD: classic SM-2 step, interval grows by the current ease factor.
        Rating.GOOD -> {
            val newInterval = cappedInterval(state.intervalDays * state.easeFactor)
            state.copy(
                intervalDays = newInterval,
                reps = state.reps + 1,
                dueAt = now + daysToMillis(newInterval),
            )
        }
        // EASY: larger interval jump (ease * 1.3) plus a permanent ease bonus (+0.15).
        Rating.EASY -> {
            val newEase = state.easeFactor + 0.15
            val newInterval = cappedInterval(state.intervalDays * state.easeFactor * 1.3)
            state.copy(
                easeFactor = newEase,
                intervalDays = newInterval,
                reps = state.reps + 1,
                dueAt = now + daysToMillis(newInterval),
            )
        }
    }

    // --- RELEARNING --------------------------------------------------------------------------

    private fun onRelearning(state: CardSrsState, rating: Rating, now: Long): CardSrsState = when (rating) {
        // AGAIN restarts relearning from the first step.
        Rating.AGAIN -> enterLearningStep(state, stepIndex = 0, now = now, phase = CardPhase.RELEARNING)
        // HARD repeats the current relearning step.
        Rating.HARD -> enterLearningStep(state, stepIndex = state.learningStepIndex, now = now, phase = CardPhase.RELEARNING)
        // GOOD advances; on the last step it restores the pending interval computed at lapse time.
        Rating.GOOD -> {
            val nextStep = state.learningStepIndex + 1
            if (nextStep > lastStepIndex) {
                graduateFromRelearning(state, now)
            } else {
                enterLearningStep(state, stepIndex = nextStep, now = now, phase = CardPhase.RELEARNING)
            }
        }
        // EASY graduates back to REVIEW immediately with the pending interval, same as GOOD on
        // the last step — mirrors Anki's "Easy" behaviour during relearning.
        Rating.EASY -> graduateFromRelearning(state, now)
    }

    // --- helpers ---------------------------------------------------------------------------

    private fun enterLearningStep(
        state: CardSrsState,
        stepIndex: Int,
        now: Long,
        phase: CardPhase = CardPhase.LEARNING,
    ): CardSrsState {
        val stepMillis = learningStepsMillis[stepIndex]
        return state.copy(
            phase = phase,
            learningStepIndex = stepIndex,
            dueAt = now + stepMillis,
        )
    }

    private fun graduate(state: CardSrsState, now: Long, intervalDays: Double): CardSrsState {
        val interval = cappedInterval(intervalDays)
        return state.copy(
            phase = CardPhase.REVIEW,
            intervalDays = interval,
            learningStepIndex = 0,
            dueAt = now + daysToMillis(interval),
        )
    }

    /** Graduate from RELEARNING back to REVIEW using the interval stashed at lapse time. */
    private fun graduateFromRelearning(state: CardSrsState, now: Long): CardSrsState {
        val interval = cappedInterval(maxOf(1.0, state.intervalDays))
        return state.copy(
            phase = CardPhase.REVIEW,
            intervalDays = interval,
            learningStepIndex = 0,
            dueAt = now + daysToMillis(interval),
        )
    }

    private fun floorEase(value: Double): Double = maxOf(value, CardSrsState.MIN_EASE_FACTOR)

    private fun cappedInterval(value: Double): Double = min(value, maxIntervalDays)

    private fun daysToMillis(days: Double): Long = (days * DAY_MILLIS).toLong()
}

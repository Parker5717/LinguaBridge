package com.linguabridge.app.domain.srs

/** User grading of a review, from worst to best recall. */
enum class Rating { AGAIN, HARD, GOOD, EASY }

/** Lifecycle phase of a card within the scheduler. */
enum class CardPhase { NEW, LEARNING, REVIEW, RELEARNING }

/**
 * Pure-Kotlin representation of a card's scheduling state, decoupled from the
 * Room [com.linguabridge.app.data.db.user.CardStateEntity]. Schedulers operate
 * only on this type so alternative algorithms (e.g. FSRS) can be swapped in
 * behind [SrsScheduler] without touching persistence code.
 */
data class CardSrsState(
    val phase: CardPhase,
    val easeFactor: Double,
    val intervalDays: Double,
    val reps: Int,
    val lapses: Int,
    val learningStepIndex: Int,
    val dueAt: Long,
) {
    companion object {
        /** Default starting ease factor for brand-new cards. */
        const val DEFAULT_EASE_FACTOR: Double = 2.5

        /** Ease factor never drops below this value. */
        const val MIN_EASE_FACTOR: Double = 1.3

        /** A freshly-added card: NEW phase, default ease, due immediately. */
        fun initial(now: Long): CardSrsState = CardSrsState(
            phase = CardPhase.NEW,
            easeFactor = DEFAULT_EASE_FACTOR,
            intervalDays = 0.0,
            reps = 0,
            lapses = 0,
            learningStepIndex = 0,
            dueAt = now,
        )
    }
}

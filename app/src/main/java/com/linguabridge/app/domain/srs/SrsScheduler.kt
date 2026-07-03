package com.linguabridge.app.domain.srs

/**
 * Computes the next scheduling state for a card given a review rating.
 *
 * Implementations must be pure functions: no I/O, no wall-clock reads (the
 * caller supplies [now]), and no mutation of the input state. This keeps the
 * scheduler swappable — e.g. an FSRS-based implementation can be introduced
 * later behind the same interface without any caller changes.
 */
interface SrsScheduler {
    fun next(state: CardSrsState, rating: Rating, now: Long): CardSrsState
}

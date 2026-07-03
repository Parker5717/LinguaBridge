package com.linguabridge.app.domain.srs

import com.linguabridge.app.data.db.user.CardStateEntity

private const val PHASE_NEW = "new"
private const val PHASE_LEARNING = "learning"
private const val PHASE_REVIEW = "review"
private const val PHASE_RELEARNING = "relearning"

private fun CardPhase.toDbString(): String = when (this) {
    CardPhase.NEW -> PHASE_NEW
    CardPhase.LEARNING -> PHASE_LEARNING
    CardPhase.REVIEW -> PHASE_REVIEW
    CardPhase.RELEARNING -> PHASE_RELEARNING
}

private fun String.toCardPhase(): CardPhase = when (this) {
    PHASE_NEW -> CardPhase.NEW
    PHASE_LEARNING -> CardPhase.LEARNING
    PHASE_REVIEW -> CardPhase.REVIEW
    PHASE_RELEARNING -> CardPhase.RELEARNING
    else -> throw IllegalArgumentException("Unknown card state: $this")
}

/** Maps a persisted [CardStateEntity] to the pure-domain [CardSrsState] used by schedulers. */
fun CardStateEntity.toSrsState(): CardSrsState = CardSrsState(
    phase = state.toCardPhase(),
    easeFactor = easeFactor,
    intervalDays = intervalDays,
    reps = reps,
    lapses = lapses,
    learningStepIndex = learningStepIndex,
    dueAt = dueAt,
)

/**
 * Applies this scheduling result onto [entity], producing an updated copy. Fields not tracked
 * by [CardSrsState] (id, addedAt) are preserved from [entity]; [lastReviewedAt] is stamped
 * with [now].
 */
fun CardSrsState.applyTo(entity: CardStateEntity, now: Long): CardStateEntity = entity.copy(
    state = phase.toDbString(),
    easeFactor = easeFactor,
    intervalDays = intervalDays,
    reps = reps,
    lapses = lapses,
    learningStepIndex = learningStepIndex,
    dueAt = dueAt,
    lastReviewedAt = now,
)

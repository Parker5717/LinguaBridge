package com.linguabridge.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linguabridge.app.data.ReviewCard
import com.linguabridge.app.data.SettingsRepository
import com.linguabridge.app.data.SrsRepository
import com.linguabridge.app.data.decksForStudy
import com.linguabridge.app.domain.exercise.AnswerVerdict
import com.linguabridge.app.domain.exercise.Exercise
import com.linguabridge.app.domain.exercise.ExerciseFactory
import com.linguabridge.app.domain.exercise.ExerciseKind
import com.linguabridge.app.domain.dictation.DiffToken
import com.linguabridge.app.domain.dictation.diffWords
import com.linguabridge.app.domain.exercise.checkPinyinAnswer
import com.linguabridge.app.domain.exercise.checkTypedAnswer
import com.linguabridge.app.domain.srs.Rating
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Feedback after an answer; null while the learner is still answering. */
data class Feedback(
    val verdict: AnswerVerdict,
    val correctAnswer: String,
    val fullBack: String,
    val example: String?,
)

sealed interface ReviewUiState {
    data object Loading : ReviewUiState

    /** Queue is empty right now; explains why and what the user can do. */
    data class Empty(
        /** Earliest moment a started card comes back, null if none started. */
        val nextDueAt: Long?,
        /** More new cards exist beyond today's daily limit. */
        val hasMoreNew: Boolean,
    ) : ReviewUiState
    data class Studying(
        val exercise: Exercise,
        val feedback: Feedback?,
        val done: Int,
        val remaining: Int,
    ) : ReviewUiState

    data class Finished(val done: Int, val correct: Int) : ReviewUiState
}

/** Cards answered wrong (or on short learning steps) come back within this
 *  window, so they are re-queued inside the same session. */
private const val REQUEUE_LOOKAHEAD_MS = 15 * 60 * 1000L

class ReviewViewModel(
    private val srs: SrsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState

    private val factory = ExerciseFactory()
    private val queue = ArrayDeque<ReviewCard>()
    private var done = 0
    private var correct = 0

    init {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            srs.seedNewCards(now)
            val s = settings.settings.first()
            queue.addAll(srs.buildQueue(now, s.newCardsPerDay, s.maxReviewsPerDay, s.decksForStudy()))
            presentNext()
        }
    }

    /** INTRO card acknowledged: counts as GOOD so the word enters practice. */
    fun continueIntro() {
        val s = _uiState.value
        if (s !is ReviewUiState.Studying || s.exercise.kind != ExerciseKind.INTRO) return
        commitAnswer(s.exercise, Rating.GOOD, countCorrect = false)
    }

    fun chooseOption(option: String) {
        val s = _uiState.value
        if (s !is ReviewUiState.Studying || s.feedback != null) return
        val ex = s.exercise
        if (ex.kind != ExerciseKind.CHOICE_FORWARD && ex.kind != ExerciseKind.CHOICE_REVERSE) return
        val verdict =
            if (option.equals(ex.answer, ignoreCase = true)) AnswerVerdict.CORRECT
            else AnswerVerdict.WRONG
        showFeedback(s, verdict)
    }

    fun submitTyped(text: String) {
        val s = _uiState.value
        if (s !is ReviewUiState.Studying || s.feedback != null) return
        when (s.exercise.kind) {
            ExerciseKind.TYPE_ANSWER, ExerciseKind.CLOZE ->
                showFeedback(
                    s,
                    if (s.exercise.answerIsPinyin) checkPinyinAnswer(s.exercise.answer, text)
                    else checkTypedAnswer(s.exercise.answer, text),
                )
            ExerciseKind.SENTENCE_TRANSLATE ->
                showFeedback(s, checkSentence(s.exercise.answer, text))
            else -> return
        }
    }

    /** Whole-sentence check: word-level LCS diff; one imperfect token is a
     *  near-miss, more is a wrong answer. */
    private fun checkSentence(expected: String, typed: String): AnswerVerdict {
        val diff = diffWords(expected, typed)
        val imperfect = diff.count { it.kind != DiffToken.Kind.CORRECT }
        return when {
            imperfect == 0 -> AnswerVerdict.CORRECT
            imperfect == 1 -> AnswerVerdict.TYPO
            else -> AnswerVerdict.WRONG
        }
    }

    /** "I don't know" — treated as a wrong answer, shows the solution. */
    fun giveUp() {
        val s = _uiState.value
        if (s !is ReviewUiState.Studying || s.feedback != null) return
        showFeedback(s, AnswerVerdict.WRONG)
    }

    /** Advances past the feedback banner and applies the SRS rating. */
    fun next() {
        val s = _uiState.value
        if (s !is ReviewUiState.Studying) return
        val fb = s.feedback ?: return
        val rating = if (fb.verdict == AnswerVerdict.WRONG) Rating.AGAIN else Rating.GOOD
        commitAnswer(s.exercise, rating, countCorrect = fb.verdict != AnswerVerdict.WRONG)
    }

    private fun showFeedback(state: ReviewUiState.Studying, verdict: AnswerVerdict) {
        _uiState.value = state.copy(
            feedback = Feedback(
                verdict = verdict,
                correctAnswer = state.exercise.answer,
                fullBack = state.exercise.fullBack,
                example = state.exercise.example,
            )
        )
    }

    private fun commitAnswer(exercise: Exercise, rating: Rating, countCorrect: Boolean) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = srs.answer(exercise.card.state, rating, now)
            queue.removeFirstOrNull()
            done++
            if (countCorrect) correct++
            if (updated.dueAt <= now + REQUEUE_LOOKAHEAD_MS) {
                // Random slot (but not immediately next) so the return of a
                // repeated card cannot be anticipated.
                val slot =
                    if (queue.size <= 2) queue.size
                    else (2..queue.size).random()
                queue.add(slot, exercise.card.copy(state = updated))
            }
            presentNext()
        }
    }

    /** Ignores the daily new-card limit and continues with 10 more words. */
    fun studyMore() {
        viewModelScope.launch {
            val s = settings.settings.first()
            queue.addAll(srs.extraNewCards(10, s.decksForStudy()))
            presentNext()
        }
    }

    private suspend fun presentNext() {
        val head = queue.firstOrNull()
        _uiState.value = when {
            head != null -> {
                val exercise = buildExercise(head)
                ReviewUiState.Studying(
                    exercise = exercise,
                    feedback = null,
                    done = done,
                    remaining = queue.size,
                )
            }

            done > 0 -> ReviewUiState.Finished(done, correct)
            else -> {
                val decks = settings.settings.first().decksForStudy()
                ReviewUiState.Empty(
                    nextDueAt = srs.nextDueAt(decks),
                    hasMoreNew = srs.extraNewCards(1, decks).isNotEmpty(),
                )
            }
        }
    }

    private suspend fun buildExercise(item: ReviewCard): Exercise {
        // Reverse choices offer card FRONTS as options; everything else backs.
        val preview = factory.create(item, emptyList())
        return if (preview.kind == ExerciseKind.CHOICE_FORWARD ||
            preview.kind == ExerciseKind.CHOICE_REVERSE
        ) {
            val useFronts = preview.kind == ExerciseKind.CHOICE_REVERSE
            factory.build(preview.kind, item, srs.distractors(item.card, useFronts))
        } else {
            preview
        }
    }

    class Factory(
        private val srs: SrsRepository,
        private val settings: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReviewViewModel(srs, settings) as T
    }
}

package com.linguabridge.app.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linguabridge.app.data.QuizItem
import com.linguabridge.app.data.QuizRepository
import com.linguabridge.app.domain.exercise.AnswerVerdict
import com.linguabridge.app.domain.exercise.checkTypedAnswer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One answered question, kept for the end-of-quiz review list. */
data class AnsweredQuestion(
    val item: QuizItem,
    val givenAnswer: String,
    val verdict: AnswerVerdict,
)

sealed interface QuizSessionUiState {
    data object Loading : QuizSessionUiState

    data class Question(
        val item: QuizItem,
        val index: Int,
        val total: Int,
        val typed: String,
        val selected: String?,
        val verdict: AnswerVerdict?,
        val score: Int,
    ) : QuizSessionUiState

    data class Finished(
        val category: String,
        val score: Int,
        val total: Int,
        val review: List<AnsweredQuestion>,
    ) : QuizSessionUiState
}

class QuizSessionViewModel(
    private val quizRepository: QuizRepository,
    private val category: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<QuizSessionUiState>(QuizSessionUiState.Loading)
    val uiState: StateFlow<QuizSessionUiState> = _uiState

    private var questions: List<QuizItem> = emptyList()
    private var index = 0
    private var score = 0
    private var typed = ""
    private var selected: String? = null
    private var verdict: AnswerVerdict? = null
    private val review = mutableListOf<AnsweredQuestion>()

    init {
        load()
    }

    private fun load() {
        _uiState.value = QuizSessionUiState.Loading
        viewModelScope.launch {
            questions = quizRepository.questions(category)
            index = 0
            score = 0
            typed = ""
            selected = null
            verdict = null
            review.clear()
            presentCurrent()
        }
    }

    fun restart() = load()

    fun updateTyped(value: String) {
        if (verdict != null) return
        typed = value
        presentCurrent()
    }

    fun submitTyped() {
        val q = questions.getOrNull(index) ?: return
        if (verdict != null || typed.isBlank()) return
        val v = checkTypedAnswer(q.question.answer, typed)
        applyVerdict(q, typed, v)
    }

    fun selectOption(option: String) {
        val q = questions.getOrNull(index) ?: return
        if (verdict != null) return
        selected = option
        val v = if (option == q.question.answer) AnswerVerdict.CORRECT else AnswerVerdict.WRONG
        applyVerdict(q, option, v)
    }

    private fun applyVerdict(q: QuizItem, given: String, v: AnswerVerdict) {
        verdict = v
        if (v != AnswerVerdict.WRONG) score++
        review += AnsweredQuestion(q, given, v)
        presentCurrent()
    }

    fun next() {
        index++
        typed = ""
        selected = null
        verdict = null
        if (index >= questions.size) {
            viewModelScope.launch {
                quizRepository.finishQuiz(category, score, questions.size, System.currentTimeMillis())
            }
            _uiState.value = QuizSessionUiState.Finished(category, score, questions.size, review.toList())
        } else {
            presentCurrent()
        }
    }

    private fun presentCurrent() {
        val q = questions.getOrNull(index) ?: return
        _uiState.value = QuizSessionUiState.Question(
            item = q,
            index = index,
            total = questions.size,
            typed = typed,
            selected = selected,
            verdict = verdict,
            score = score,
        )
    }

    class Factory(
        private val quizRepository: QuizRepository,
        private val category: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QuizSessionViewModel(quizRepository, category) as T
    }
}

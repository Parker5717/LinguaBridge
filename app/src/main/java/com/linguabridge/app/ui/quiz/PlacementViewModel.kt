package com.linguabridge.app.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linguabridge.app.data.QuizItem
import com.linguabridge.app.data.QuizRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A level counts as passed when this share of its questions is correct.
 *  (A share, not an absolute count: the pool grew from 4 to 10 per level.) */
private const val PASS_SHARE = 0.6

sealed interface PlacementUiState {
    data object Intro : PlacementUiState
    data object Loading : PlacementUiState

    data class Question(
        val item: QuizItem,
        val index: Int,
        val total: Int,
    ) : PlacementUiState

    data class Finished(val estimate: String, val score: Int, val total: Int) : PlacementUiState
}

class PlacementViewModel(private val quizRepository: QuizRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<PlacementUiState>(PlacementUiState.Intro)
    val uiState: StateFlow<PlacementUiState> = _uiState

    private var questions: List<QuizItem> = emptyList()
    private var index = 0
    private var totalScore = 0
    private val correctByLevel = mutableMapOf("A2" to 0, "B1" to 0, "B2" to 0)

    fun start() {
        _uiState.value = PlacementUiState.Loading
        viewModelScope.launch {
            questions = quizRepository.placementQuestions()
            index = 0
            totalScore = 0
            correctByLevel.keys.forEach { correctByLevel[it] = 0 }
            presentCurrent()
        }
    }

    fun answer(option: String) {
        val q = questions.getOrNull(index) ?: return
        if (option == q.question.answer) {
            totalScore++
            correctByLevel[q.question.level] = (correctByLevel[q.question.level] ?: 0) + 1
        }
        index++
        if (index >= questions.size) finish() else presentCurrent()
    }

    private fun finish() {
        val estimate = estimateLevel()
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            quizRepository.savePlacement(estimate, totalScore, questions.size, now)
            quizRepository.finishQuiz("placement", totalScore, questions.size, now)
        }
        _uiState.value = PlacementUiState.Finished(estimate, totalScore, questions.size)
    }

    private fun estimateLevel(): String {
        fun passed(level: String): Boolean {
            val total = questions.count { it.question.level == level }
            if (total == 0) return false
            val needed = kotlin.math.ceil(total * PASS_SHARE).toInt()
            return (correctByLevel[level] ?: 0) >= needed
        }
        if (!passed("A2")) return "PRE_A2"
        if (!passed("B1")) return "A2"
        if (!passed("B2")) return "B1"
        return "B2"
    }

    private fun presentCurrent() {
        val q = questions.getOrNull(index) ?: return
        _uiState.value = PlacementUiState.Question(item = q, index = index, total = questions.size)
    }

    class Factory(private val quizRepository: QuizRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PlacementViewModel(quizRepository) as T
    }
}

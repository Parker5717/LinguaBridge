package com.linguabridge.app.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linguabridge.app.data.QuizRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Loads the last stored placement estimate shown on the quiz list's placement card. */
class QuizListViewModel(private val quizRepository: QuizRepository) : ViewModel() {

    private val _lastPlacementLevel = MutableStateFlow<String?>(null)
    val lastPlacementLevel: StateFlow<String?> = _lastPlacementLevel

    init {
        viewModelScope.launch {
            _lastPlacementLevel.value = quizRepository.lastPlacement()?.estimatedLevel
        }
    }

    class Factory(private val quizRepository: QuizRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            QuizListViewModel(quizRepository) as T
    }
}

package com.linguabridge.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linguabridge.app.data.ListeningQuestion
import com.linguabridge.app.data.PracticeRepository
import com.linguabridge.app.data.SettingsRepository
import com.linguabridge.app.data.db.content.ListeningPassageEntity
import com.linguabridge.app.tts.TtsLanguage
import com.linguabridge.app.tts.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface ListeningUiState {
    /** No level chosen yet. */
    data object LevelPicker : ListeningUiState

    data class PassageList(val level: String, val passages: List<ListeningPassageEntity>) : ListeningUiState

    data object Loading : ListeningUiState

    data class Question(
        val passage: ListeningPassageEntity,
        val questionIndex: Int,
        val total: Int,
        val question: ListeningQuestion,
        val selected: String?,
        val correct: Boolean?,
        val score: Int,
    ) : ListeningUiState

    data class PassageFinished(
        val passage: ListeningPassageEntity,
        val score: Int,
        val total: Int,
    ) : ListeningUiState
}

class ListeningViewModel(
    private val practiceRepository: PracticeRepository,
    private val settingsRepository: SettingsRepository,
    private val ttsManager: TtsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListeningUiState>(ListeningUiState.LevelPicker)
    val uiState: StateFlow<ListeningUiState> = _uiState

    val ttsState get() = ttsManager.state

    private var questions: List<ListeningQuestion> = emptyList()
    private var qIndex = 0
    private var score = 0
    private var selected: String? = null
    private var correct: Boolean? = null

    fun selectLevel(level: String) {
        _uiState.value = ListeningUiState.Loading
        viewModelScope.launch {
            val passages = practiceRepository.passages(level)
            _uiState.value = ListeningUiState.PassageList(level, passages)
        }
    }

    fun backToLevelPicker() {
        _uiState.value = ListeningUiState.LevelPicker
    }

    fun backToPassageList() {
        val level = (_uiState.value as? ListeningUiState.Question)?.passage?.level
            ?: (_uiState.value as? ListeningUiState.PassageFinished)?.passage?.level
        if (level != null) selectLevel(level)
        else backToLevelPicker()
    }

    fun openPassage(passage: ListeningPassageEntity) {
        _uiState.value = ListeningUiState.Loading
        viewModelScope.launch {
            questions = practiceRepository.questionsFor(passage.id)
            qIndex = 0
            score = 0
            selected = null
            correct = null
            presentCurrent(passage)
        }
    }

    fun play(text: String) {
        viewModelScope.launch {
            val rate = settingsRepository.settings.first().ttsRate
            ttsManager.speak(text, TtsLanguage.ENGLISH, rate)
        }
    }

    fun answer(passage: ListeningPassageEntity, option: String) {
        if (selected != null) return
        val q = questions.getOrNull(qIndex) ?: return
        selected = option
        correct = option == q.question.answer
        if (correct == true) score++
        presentCurrent(passage)
    }

    fun next(passage: ListeningPassageEntity) {
        qIndex++
        selected = null
        correct = null
        if (qIndex >= questions.size) {
            viewModelScope.launch {
                practiceRepository.recordQuiz("listening", score, questions.size, System.currentTimeMillis())
            }
            _uiState.value = ListeningUiState.PassageFinished(passage, score, questions.size)
        } else {
            presentCurrent(passage)
        }
    }

    private fun presentCurrent(passage: ListeningPassageEntity) {
        val q = questions.getOrNull(qIndex) ?: return
        _uiState.value = ListeningUiState.Question(
            passage = passage,
            questionIndex = qIndex,
            total = questions.size,
            question = q,
            selected = selected,
            correct = correct,
            score = score,
        )
    }

    override fun onCleared() {
        ttsManager.stop()
    }

    class Factory(
        private val practiceRepository: PracticeRepository,
        private val settingsRepository: SettingsRepository,
        private val ttsManager: TtsManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ListeningViewModel(practiceRepository, settingsRepository, ttsManager) as T
    }
}

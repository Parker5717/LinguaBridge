package com.linguabridge.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linguabridge.app.data.PracticeRepository
import com.linguabridge.app.data.SettingsRepository
import com.linguabridge.app.domain.dictation.DiffToken
import com.linguabridge.app.domain.dictation.diffWords
import com.linguabridge.app.domain.dictation.isPerfect
import com.linguabridge.app.tts.TtsLanguage
import com.linguabridge.app.tts.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Vocab level picker for dictation, mapped to the underlying vocab.level values. */
enum class DictationLevel(val vocabLevel: String) {
    A2_B1("A2B1"),
    B1_B2("B1B2"),
}

private const val SESSION_SIZE = 5

sealed interface DictationUiState {
    /** No level chosen yet. */
    data object LevelPicker : DictationUiState

    data object Loading : DictationUiState

    data class Sentence(
        val index: Int,
        val total: Int,
        val typed: String,
        val diff: List<DiffToken>?,
        val perfectCount: Int,
    ) : DictationUiState

    data class Finished(val perfectCount: Int, val total: Int) : DictationUiState
}

class DictationViewModel(
    private val practiceRepository: PracticeRepository,
    private val settingsRepository: SettingsRepository,
    private val ttsManager: TtsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DictationUiState>(DictationUiState.LevelPicker)
    val uiState: StateFlow<DictationUiState> = _uiState

    val ttsState get() = ttsManager.state

    private var sentences: List<String> = emptyList()
    private var index = 0
    private var perfectCount = 0
    private var typed = ""
    private var diff: List<DiffToken>? = null

    fun selectLevel(level: DictationLevel) {
        _uiState.value = DictationUiState.Loading
        viewModelScope.launch {
            sentences = practiceRepository.dictationSentences(level.vocabLevel, SESSION_SIZE)
            index = 0
            perfectCount = 0
            typed = ""
            diff = null
            presentCurrent()
        }
    }

    fun play() {
        val sentence = sentences.getOrNull(index) ?: return
        viewModelScope.launch {
            val rate = settingsRepository.settings.first().ttsRate
            ttsManager.speak(sentence, TtsLanguage.ENGLISH, rate)
        }
    }

    fun updateTyped(text: String) {
        if (diff != null) return
        typed = text
        presentCurrent()
    }

    fun check() {
        val sentence = sentences.getOrNull(index) ?: return
        if (typed.isBlank()) return
        diff = diffWords(sentence, typed)
        if (isPerfect(diff!!)) perfectCount++
        viewModelScope.launch {
            practiceRepository.recordDictation(System.currentTimeMillis())
        }
        presentCurrent()
    }

    fun retry() {
        typed = ""
        diff = null
        presentCurrent()
    }

    fun next() {
        index++
        typed = ""
        diff = null
        presentCurrent()
    }

    fun restart() {
        _uiState.value = DictationUiState.LevelPicker
    }

    private fun presentCurrent() {
        _uiState.value = if (index >= sentences.size) {
            DictationUiState.Finished(perfectCount, sentences.size)
        } else {
            DictationUiState.Sentence(
                index = index,
                total = sentences.size,
                typed = typed,
                diff = diff,
                perfectCount = perfectCount,
            )
        }
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
            DictationViewModel(practiceRepository, settingsRepository, ttsManager) as T
    }
}

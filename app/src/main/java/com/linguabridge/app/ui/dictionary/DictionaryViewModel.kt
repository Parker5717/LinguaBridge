package com.linguabridge.app.ui.dictionary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linguabridge.app.data.DictionaryRepository
import com.linguabridge.app.data.DictionaryResults
import com.linguabridge.app.data.LibraryRepository
import com.linguabridge.app.data.SettingsRepository
import com.linguabridge.app.tts.TtsLanguage
import com.linguabridge.app.tts.TtsManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 300L

data class DictionaryUiState(
    val query: String = "",
    val results: DictionaryResults = DictionaryResults.EMPTY,
    val loading: Boolean = false,
    /** ids of vocab entries that were just added to the deck (shows "Added" state). */
    val addedVocabIds: Set<String> = emptySet(),
    /** row keys ("vocab:<id>", "hsk:<id>", "stem:<id>", "gram:<id>") currently expanded. */
    val expandedKeys: Set<String> = emptySet(),
)

@OptIn(FlowPreview::class)
class DictionaryViewModel(
    private val dictionaryRepository: DictionaryRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val ttsManager: TtsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DictionaryUiState())
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    val ttsState get() = ttsManager.state

    private val queryFlow = MutableStateFlow("")

    init {
        queryFlow
            .debounce(SEARCH_DEBOUNCE_MS)
            .onEach { runSearch(it) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(text: String) {
        _uiState.value = _uiState.value.copy(query = text)
        if (text.trim().length < 2) {
            _uiState.value = _uiState.value.copy(results = DictionaryResults.EMPTY, loading = false)
        } else {
            _uiState.value = _uiState.value.copy(loading = true)
        }
        queryFlow.value = text
    }

    fun clearQuery() {
        onQueryChange("")
    }

    fun toggleExpanded(key: String) {
        val current = _uiState.value.expandedKeys
        _uiState.value = _uiState.value.copy(
            expandedKeys = if (key in current) current - key else current + key,
        )
    }

    fun speak(text: String, language: TtsLanguage) {
        viewModelScope.launch {
            val rate = settingsRepository.settings.first().ttsRate
            ttsManager.speak(text, language, rate)
        }
    }

    fun addToDeck(vocabId: String) {
        if (vocabId in _uiState.value.addedVocabIds) return
        viewModelScope.launch {
            val bumped = libraryRepository.addToDeckFront(vocabId)
            if (bumped) {
                _uiState.value = _uiState.value.copy(addedVocabIds = _uiState.value.addedVocabIds + vocabId)
            }
        }
    }

    private suspend fun runSearch(query: String) {
        val results = dictionaryRepository.search(query)
        // Ignore stale results if the query changed while we were searching.
        if (queryFlow.value == query) {
            _uiState.value = _uiState.value.copy(results = results, loading = false)
        }
    }

    override fun onCleared() {
        ttsManager.stop()
    }

    class Factory(
        private val dictionaryRepository: DictionaryRepository,
        private val libraryRepository: LibraryRepository,
        private val settingsRepository: SettingsRepository,
        private val ttsManager: TtsManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DictionaryViewModel(dictionaryRepository, libraryRepository, settingsRepository, ttsManager) as T
    }
}

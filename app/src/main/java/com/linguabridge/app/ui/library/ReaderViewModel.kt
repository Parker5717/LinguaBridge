package com.linguabridge.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linguabridge.app.data.LibraryRepository
import com.linguabridge.app.data.db.content.ReadingTextEntity
import com.linguabridge.app.data.db.content.VocabEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(val text: ReadingTextEntity, val restoredItem: Int) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}

sealed interface WordLookupState {
    data object Hidden : WordLookupState
    data class Found(val vocab: VocabEntity, val added: Boolean) : WordLookupState
    data class NotFound(val word: String) : WordLookupState
}

class ReaderViewModel(
    private val textId: String,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _lookup = MutableStateFlow<WordLookupState>(WordLookupState.Hidden)
    val lookup: StateFlow<WordLookupState> = _lookup.asStateFlow()

    init {
        viewModelScope.launch {
            val text = libraryRepository.text(textId)
            _uiState.value = if (text != null) {
                val restored = libraryRepository.readPosition(textId)
                ReaderUiState.Ready(text, restored)
            } else {
                ReaderUiState.Error("Text not found")
            }
        }
    }

    fun onWordTapped(word: String) {
        viewModelScope.launch {
            val vocab = libraryRepository.lookupWord(word)
            _lookup.value = if (vocab != null) {
                WordLookupState.Found(vocab, added = false)
            } else {
                WordLookupState.NotFound(word)
            }
        }
    }

    fun dismissLookup() {
        _lookup.value = WordLookupState.Hidden
    }

    fun addToDeck() {
        val current = _lookup.value
        if (current !is WordLookupState.Found || current.added) return
        viewModelScope.launch {
            val bumped = libraryRepository.addToDeckFront(current.vocab.id)
            if (bumped) {
                _lookup.value = current.copy(added = true)
            }
        }
    }

    fun saveReadPosition(item: Int) {
        viewModelScope.launch {
            libraryRepository.saveReadPosition(textId, item)
        }
    }

    class Factory(
        private val textId: String,
        private val libraryRepository: LibraryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReaderViewModel(textId, libraryRepository) as T
    }
}

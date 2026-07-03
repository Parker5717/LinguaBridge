package com.linguabridge.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linguabridge.app.data.LibraryRepository
import com.linguabridge.app.data.db.content.DialogueEntity
import com.linguabridge.app.data.db.content.DialogueLineEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DialogueUiState {
    data object Loading : DialogueUiState
    data class Ready(val dialogue: DialogueEntity, val lines: List<DialogueLineEntity>) : DialogueUiState
    data class Error(val message: String) : DialogueUiState
}

class DialogueViewModel(
    private val dialogueId: String,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DialogueUiState>(DialogueUiState.Loading)
    val uiState: StateFlow<DialogueUiState> = _uiState.asStateFlow()

    private val _notesVisible = MutableStateFlow(true)
    val notesVisible: StateFlow<Boolean> = _notesVisible.asStateFlow()

    init {
        viewModelScope.launch {
            val dialogue = libraryRepository.dialogues().first().firstOrNull { it.id == dialogueId }
            _uiState.value = if (dialogue != null) {
                val lines = libraryRepository.dialogueLines(dialogueId)
                DialogueUiState.Ready(dialogue, lines)
            } else {
                DialogueUiState.Error("Dialogue not found")
            }
        }
    }

    fun toggleNotes() {
        _notesVisible.update { !it }
    }

    class Factory(
        private val dialogueId: String,
        private val libraryRepository: LibraryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DialogueViewModel(dialogueId, libraryRepository) as T
    }
}

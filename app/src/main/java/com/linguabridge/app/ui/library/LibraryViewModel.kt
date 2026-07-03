package com.linguabridge.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.linguabridge.app.data.LibraryRepository
import com.linguabridge.app.data.db.content.DialogueEntity
import com.linguabridge.app.data.db.content.ReadingTextEntity
import kotlinx.coroutines.flow.Flow

class LibraryViewModel(private val libraryRepository: LibraryRepository) : ViewModel() {

    fun textsForLevel(level: String): Flow<List<ReadingTextEntity>> = libraryRepository.texts(level)

    val dialogues: Flow<List<DialogueEntity>> = libraryRepository.dialogues()

    class Factory(private val libraryRepository: LibraryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(libraryRepository) as T
    }
}

package com.linguabridge.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.linguabridge.app.data.LibraryRepository
import com.linguabridge.app.data.SettingsRepository
import com.linguabridge.app.data.db.content.DialogueEntity
import com.linguabridge.app.data.db.content.ReadingTextEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryViewModel(
    private val libraryRepository: LibraryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    fun textsForLevel(level: String): Flow<List<ReadingTextEntity>> = libraryRepository.texts(level)

    val dialogues: Flow<List<DialogueEntity>> = libraryRepository.dialogues()

    /** Drives Library's mode switch: "en" shows A2/B1/B2 texts, "zh" shows only ZH texts. */
    val studyLanguage: Flow<String> = settingsRepository.settings.map { it.studyLanguage }

    class Factory(
        private val libraryRepository: LibraryRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(libraryRepository, settingsRepository) as T
    }
}

package com.linguabridge.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import com.linguabridge.app.data.SettingsRepository
import com.linguabridge.app.data.db.content.DialogueLineEntity
import com.linguabridge.app.tts.TtsLanguage
import com.linguabridge.app.tts.TtsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogueScreen(dialogueId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val viewModel: DialogueViewModel = viewModel(
        factory = DialogueViewModel.Factory(dialogueId, app.container.libraryRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notesVisible by viewModel.notesVisible.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val ready = state as? DialogueUiState.Ready
                    Text(ready?.dialogue?.title.orEmpty())
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleNotes) {
                        Icon(
                            imageVector = if (notesVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (notesVisible) R.string.dialogue_hide_notes else R.string.dialogue_show_notes,
                            ),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when (val s = state) {
                DialogueUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is DialogueUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }

                is DialogueUiState.Ready -> DialogueBody(s.lines, notesVisible, app.container.ttsManager, app.container.settingsRepository)
            }
        }
    }
}

@Composable
private fun DialogueBody(
    lines: List<DialogueLineEntity>,
    notesVisible: Boolean,
    ttsManager: TtsManager,
    settingsRepository: SettingsRepository,
) {
    val firstSpeaker = lines.firstOrNull()?.speaker

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(lines, key = { it.id }) { line ->
            DialogueBubble(
                line = line,
                isLeft = line.speaker == firstSpeaker,
                notesVisible = notesVisible,
                ttsManager = ttsManager,
                settingsRepository = settingsRepository,
            )
        }
    }
}

@Composable
private fun DialogueBubble(
    line: DialogueLineEntity,
    isLeft: Boolean,
    notesVisible: Boolean,
    ttsManager: TtsManager,
    settingsRepository: SettingsRepository,
) {
    val bubbleColor = if (isLeft) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val onBubbleColor = if (isLeft) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val horizontalAlignment = if (isLeft) Alignment.Start else Alignment.End

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment,
    ) {
        Text(
            line.speaker,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Row(
            modifier = Modifier.widthIn(max = 320.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!isLeft) {
                PlayLineButton(line, ttsManager, settingsRepository)
            }
            Card(colors = CardDefaults.cardColors(containerColor = bubbleColor)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(line.text, color = onBubbleColor, style = MaterialTheme.typography.bodyLarge)
                    if (line.lang == "zh" && !line.pinyin.isNullOrBlank()) {
                        Text(
                            line.pinyin,
                            color = onBubbleColor.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        )
                    }
                }
            }
            if (isLeft) {
                PlayLineButton(line, ttsManager, settingsRepository)
            }
        }
        if (notesVisible && !line.ruNote.isNullOrBlank()) {
            Text(
                line.ruNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun PlayLineButton(
    line: DialogueLineEntity,
    ttsManager: TtsManager,
    settingsRepository: SettingsRepository,
) {
    val scope = rememberCoroutineScope()
    IconButton(onClick = {
        scope.launch {
            val rate = settingsRepository.settings.first().ttsRate
            val language = if (line.lang == "zh") TtsLanguage.CHINESE else TtsLanguage.ENGLISH
            ttsManager.speak(line.text, language, rate)
        }
    }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = stringResource(R.string.dialogue_play_line),
        )
    }
}

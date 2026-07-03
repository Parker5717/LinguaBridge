package com.linguabridge.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import com.linguabridge.app.data.db.content.ListeningPassageEntity
import com.linguabridge.app.tts.VoiceStatus

private val LEVELS = listOf("A2", "B1", "B2")

private fun levelLabelRes(level: String): Int = when (level) {
    "A2" -> R.string.library_level_a2
    "B1" -> R.string.library_level_b1
    else -> R.string.library_level_b2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val viewModel: ListeningViewModel = viewModel(
        factory = ListeningViewModel.Factory(
            app.container.practiceRepository,
            app.container.settingsRepository,
            app.container.ttsManager,
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.practice_listening_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (state) {
                                ListeningUiState.LevelPicker -> onBack()
                                is ListeningUiState.PassageList -> viewModel.backToLevelPicker()
                                else -> viewModel.backToPassageList()
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (val s = state) {
                ListeningUiState.LevelPicker -> LevelPicker(onSelect = viewModel::selectLevel)
                is ListeningUiState.PassageList -> PassageListBody(s, onOpen = viewModel::openPassage)
                ListeningUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ListeningUiState.Question -> QuestionBody(s, viewModel)
                is ListeningUiState.PassageFinished -> FinishedBody(s, viewModel)
            }
        }
    }
}

@Composable
private fun LevelPicker(onSelect: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.listening_pick_level),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LEVELS.forEach { level ->
                OutlinedButton(onClick = { onSelect(level) }) {
                    Text(stringResource(levelLabelRes(level)))
                }
            }
        }
    }
}

@Composable
private fun PassageListBody(state: ListeningUiState.PassageList, onOpen: (ListeningPassageEntity) -> Unit) {
    if (state.passages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.listening_no_passages),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.passages, key = { _, passage -> passage.id }) { i, passage ->
            Card(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(passage) }) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.listening_passage_number, i + 1),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionBody(state: ListeningUiState.Question, viewModel: ListeningViewModel) {
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(R.string.listening_question_progress, state.questionIndex + 1, state.total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FilledIconButton(
                onClick = { viewModel.play(state.passage.text) },
                modifier = Modifier.size(64.dp),
                enabled = ttsState.engineReady && ttsState.english == VoiceStatus.READY,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.dictation_play), modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(24.dp))

        Text(state.question.question.prompt, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.question.options.forEach { option ->
                val isSelected = option == state.selected
                val isAnswer = option == state.question.question.answer
                val colors = when {
                    state.selected == null -> CardDefaults.cardColors()
                    isAnswer -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    isSelected -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    else -> CardDefaults.cardColors()
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = colors,
                    onClick = { viewModel.answer(state.passage, option) },
                    enabled = state.selected == null,
                ) {
                    Text(option, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (state.correct != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.correct) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(if (state.correct) R.string.feedback_correct else R.string.feedback_wrong),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(state.question.question.explanation, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.next(state.passage) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.exercise_continue)) }
        }
    }
}

@Composable
private fun FinishedBody(state: ListeningUiState.PassageFinished, viewModel: ListeningViewModel) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.listening_session_summary, state.score, state.total),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.listening_transcript), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Text(state.passage.text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = viewModel::backToPassageList, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.listening_back_to_passages))
        }
    }
}

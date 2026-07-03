package com.linguabridge.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import com.linguabridge.app.domain.dictation.DiffToken
import com.linguabridge.app.tts.VoiceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictationScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val viewModel: DictationViewModel = viewModel(
        factory = DictationViewModel.Factory(
            app.container.practiceRepository,
            app.container.settingsRepository,
            app.container.ttsManager,
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.practice_dictation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (val s = state) {
                DictationUiState.LevelPicker -> LevelPicker(onSelect = viewModel::selectLevel)
                DictationUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is DictationUiState.Sentence -> SentenceBody(s, viewModel)
                is DictationUiState.Finished -> FinishedBody(s, onRestart = viewModel::restart, onBack = onBack)
            }
        }
    }
}

@Composable
private fun LevelPicker(onSelect: (DictationLevel) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.dictation_pick_level),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onSelect(DictationLevel.A2_B1) }) {
                Text(stringResource(R.string.dictation_level_a2b1))
            }
            OutlinedButton(onClick = { onSelect(DictationLevel.B1_B2) }) {
                Text(stringResource(R.string.dictation_level_b1b2))
            }
        }
    }
}

@Composable
private fun SentenceBody(state: DictationUiState.Sentence, viewModel: DictationViewModel) {
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val total = state.total.coerceAtLeast(1)
        LinearProgressIndicator(
            progress = { state.index.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.dictation_progress, state.index + 1, state.total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FilledIconButton(
                onClick = viewModel::play,
                modifier = Modifier.size(72.dp),
                enabled = ttsState.engineReady && ttsState.english == VoiceStatus.READY,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.dictation_play), modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.typed,
            onValueChange = viewModel::updateTyped,
            enabled = state.diff == null,
            label = { Text(stringResource(R.string.dictation_type_what_you_heard)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (state.typed.isNotBlank()) viewModel.check() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        if (state.diff == null) {
            Button(
                onClick = viewModel::check,
                enabled = state.typed.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.exercise_check)) }
        } else {
            DiffResult(state.diff)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = viewModel::retry, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.dictation_retry))
                }
                Button(onClick = viewModel::next, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.exercise_continue))
                }
            }
        }
    }
}

@Composable
private fun DiffResult(diff: List<DiffToken>) {
    val perfect = diff.all { it.kind == DiffToken.Kind.CORRECT }
    if (perfect) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Text(
                stringResource(R.string.dictation_perfect),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Text(
            buildDiffAnnotatedString(diff),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun buildDiffAnnotatedString(diff: List<DiffToken>) = buildAnnotatedString {
    val errorColor = MaterialTheme.colorScheme.error
    diff.forEachIndexed { i, token ->
        if (i > 0) append(" ")
        when (token.kind) {
            DiffToken.Kind.CORRECT -> append(token.text)
            DiffToken.Kind.WRONG, DiffToken.Kind.MISSING -> withStyle(
                SpanStyle(color = errorColor, fontWeight = FontWeight.Bold)
            ) { append(token.text) }
            DiffToken.Kind.EXTRA -> withStyle(
                SpanStyle(color = errorColor, textDecoration = TextDecoration.LineThrough)
            ) { append(token.text) }
        }
    }
}

@Composable
private fun FinishedBody(state: DictationUiState.Finished, onRestart: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.dictation_session_summary, state.perfectCount, state.total),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.review_back)) }
            Button(onClick = onRestart) { Text(stringResource(R.string.dictation_practice_again)) }
        }
    }
}

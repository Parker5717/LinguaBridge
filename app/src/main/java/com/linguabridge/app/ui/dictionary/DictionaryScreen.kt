package com.linguabridge.app.ui.dictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import com.linguabridge.app.data.db.content.GrammarTermEntity
import com.linguabridge.app.data.db.content.HskWordEntity
import com.linguabridge.app.data.db.content.StemTermEntity
import com.linguabridge.app.data.db.content.VocabEntity
import com.linguabridge.app.tts.TtsLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen() {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val viewModel: DictionaryViewModel = viewModel(
        factory = DictionaryViewModel.Factory(
            app.container.dictionaryRepository,
            app.container.libraryRepository,
            app.container.settingsRepository,
            app.container.ttsManager,
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.dictionary_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.dictionary_clear_search))
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(),
        )

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        val trimmedLen = state.query.trim().length
        if (trimmedLen < 2) {
            HintBox(stringResource(R.string.dictionary_min_chars_hint))
        } else if (!state.loading && state.results.isEmpty) {
            HintBox(stringResource(R.string.dictionary_nothing_found, state.query.trim()))
        } else {
            ResultsList(state, viewModel)
        }
    }
}

@Composable
private fun HintBox(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ResultsList(state: DictionaryUiState, viewModel: DictionaryViewModel) {
    val results = state.results
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (results.vocab.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.dictionary_section_vocab)) }
            items(results.vocab, key = { "vocab:${it.id}" }) { vocab ->
                VocabRow(
                    vocab = vocab,
                    expanded = "vocab:${vocab.id}" in state.expandedKeys,
                    added = vocab.id in state.addedVocabIds,
                    onToggle = { viewModel.toggleExpanded("vocab:${vocab.id}") },
                    onPlay = { viewModel.speak(vocab.headword, TtsLanguage.ENGLISH) },
                    onAddToDeck = { viewModel.addToDeck(vocab.id) },
                )
            }
        }
        if (results.hsk.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.dictionary_section_hsk)) }
            items(results.hsk, key = { "hsk:${it.id}" }) { word ->
                HskRow(
                    word = word,
                    expanded = "hsk:${word.id}" in state.expandedKeys,
                    onToggle = { viewModel.toggleExpanded("hsk:${word.id}") },
                    onPlay = { viewModel.speak(word.hanzi, TtsLanguage.CHINESE) },
                )
            }
        }
        if (results.stem.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.dictionary_section_stem)) }
            items(results.stem, key = { "stem:${it.id}" }) { term ->
                StemRow(
                    term = term,
                    expanded = "stem:${term.id}" in state.expandedKeys,
                    onToggle = { viewModel.toggleExpanded("stem:${term.id}") },
                    onPlay = { viewModel.speak(term.term, TtsLanguage.ENGLISH) },
                )
            }
        }
        if (results.gramTerms.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.dictionary_section_gramterm)) }
            items(results.gramTerms, key = { "gram:${it.id}" }) { term ->
                GrammarRow(
                    term = term,
                    expanded = "gram:${term.id}" in state.expandedKeys,
                    onToggle = { viewModel.toggleExpanded("gram:${term.id}") },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun VocabRow(
    vocab: VocabEntity,
    expanded: Boolean,
    added: Boolean,
    onToggle: () -> Unit,
    onPlay: () -> Unit,
    onAddToDeck: () -> Unit,
) {
    ResultCard(onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vocab.headword, style = MaterialTheme.typography.titleMedium)
            if (vocab.ipa.isNotBlank()) {
                Text(
                    "  ${vocab.ipa}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            vocab.ruTranslation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                HorizontalDivider()
                Text(
                    vocab.enDefinition,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (vocab.example1.isNotBlank()) {
                    Text(
                        "${stringResource(R.string.dictionary_example_label)}: ${vocab.example1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (!vocab.example1Ru.isNullOrBlank()) {
                        Text(
                            vocab.example1Ru,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.dictionary_play_audio))
                    }
                    TextButton(onClick = onAddToDeck, enabled = !added) {
                        if (added) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text(stringResource(R.string.dictionary_added_to_deck))
                        } else {
                            Text(stringResource(R.string.dictionary_add_to_deck))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HskRow(
    word: HskWordEntity,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlay: () -> Unit,
) {
    ResultCard(onToggle) {
        Text(word.hanzi, style = MaterialTheme.typography.titleMedium)
        Text(
            word.pinyin,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(word.enMeaning, style = MaterialTheme.typography.bodyMedium)
        Text(word.ruMeaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        if (expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                HorizontalDivider()
                if (word.exampleZh.isNotBlank()) {
                    Text(word.exampleZh, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    if (word.examplePinyin.isNotBlank()) {
                        Text(
                            word.examplePinyin,
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (word.exampleEn.isNotBlank()) {
                        Text(
                            word.exampleEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    IconButton(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.dictionary_play_audio))
                    }
                }
            }
        }
    }
}

@Composable
private fun StemRow(
    term: StemTermEntity,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlay: () -> Unit,
) {
    ResultCard(onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(term.term, style = MaterialTheme.typography.titleMedium)
            if (!term.symbol.isNullOrBlank()) {
                Text(
                    "  (${term.symbol})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(term.ruTranslation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        if (expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                HorizontalDivider()
                Text(
                    term.enDefinition,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (term.cscaExample.isNotBlank()) {
                    Text(
                        "${stringResource(R.string.dictionary_example_label)}: ${term.cscaExample}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    IconButton(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.dictionary_play_audio))
                    }
                }
            }
        }
    }
}

@Composable
private fun GrammarRow(
    term: GrammarTermEntity,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    ResultCard(onToggle) {
        Text(term.term, style = MaterialTheme.typography.titleMedium)
        Text(term.ruTranslation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        if (expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                HorizontalDivider()
                Text(
                    term.enExplanation,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (term.zhExample.isNotBlank()) {
                    Text(
                        term.zhExample,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (term.zhExamplePinyin.isNotBlank()) {
                        Text(
                            term.zhExamplePinyin,
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (term.zhExampleEn.isNotBlank()) {
                        Text(
                            term.zhExampleEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(onToggle: () -> Unit, content: ColumnScopeContent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            content()
        }
    }
}

private typealias ColumnScopeContent = @Composable ColumnScope.() -> Unit

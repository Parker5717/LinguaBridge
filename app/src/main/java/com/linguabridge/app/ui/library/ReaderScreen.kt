package com.linguabridge.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(textId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val viewModel: ReaderViewModel = viewModel(
        factory = ReaderViewModel.Factory(textId, app.container.libraryRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lookup by viewModel.lookup.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val ready = state as? ReaderUiState.Ready
                    Text(ready?.text?.title.orEmpty())
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.review_back))
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
                ReaderUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                is ReaderUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                }

                is ReaderUiState.Ready -> ReaderBody(
                    body = s.text.body,
                    level = s.text.level,
                    restoredItem = s.restoredItem,
                    onWordTapped = viewModel::onWordTapped,
                    onSavePosition = viewModel::saveReadPosition,
                )
            }
        }
    }

    if (lookup !is WordLookupState.Hidden) {
        WordLookupSheet(
            state = lookup,
            onDismiss = viewModel::dismissLookup,
            onAddToDeck = viewModel::addToDeck,
        )
    }
}

@Composable
private fun ReaderBody(
    body: String,
    level: String,
    restoredItem: Int,
    onWordTapped: (String) -> Unit,
    onSavePosition: (Int) -> Unit,
) {
    val paragraphs = remember(body) { body.split("\n\n").filter { it.isNotBlank() } }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = restoredItem.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0)),
    )
    val isChinese = level == "ZH"

    DisposableEffect(Unit) {
        onDispose { onSavePosition(listState.firstVisibleItemIndex) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(paragraphs.size) { index ->
            if (isChinese) {
                ChineseParagraphView(paragraphs[index])
            } else {
                ParagraphView(paragraphs[index], onWordTapped)
            }
        }
    }
}

@Composable
private fun ParagraphView(paragraph: String, onWordTapped: (String) -> Unit) {
    val words = remember(paragraph) { paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() } }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        words.forEach { word ->
            Text(
                text = word,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable { onWordTapped(word) },
            )
        }
    }
}

/**
 * Chinese reading texts are stored as blocks of three lines (hanzi, pinyin,
 * English), one block per paragraph. Word-tap lookup only makes sense for
 * English vocab, so Chinese paragraphs render as plain, non-tappable text.
 */
@Composable
private fun ChineseParagraphView(paragraph: String) {
    val lines = remember(paragraph) { paragraph.split("\n").filter { it.isNotBlank() } }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEachIndexed { index, line ->
            when (index) {
                0 -> Text(line, style = MaterialTheme.typography.titleMedium)
                1 -> Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordLookupSheet(
    state: WordLookupState,
    onDismiss: () -> Unit,
    onAddToDeck: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        when (state) {
            is WordLookupState.NotFound -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(state.word, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.reader_word_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            is WordLookupState.Found -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(state.vocab.headword, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        state.vocab.ipa,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    state.vocab.pos,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                HorizontalDivider()
                Text(
                    state.vocab.ruTranslation,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(state.vocab.enDefinition, style = MaterialTheme.typography.bodyLarge)
                if (state.vocab.example1.isNotBlank()) {
                    Text(
                        "${stringResource(R.string.reader_example_label)}: ${state.vocab.example1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onAddToDeck,
                    enabled = !state.added,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.added) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.reader_added_to_deck))
                    } else {
                        Text(stringResource(R.string.reader_add_to_deck))
                    }
                }
            }

            WordLookupState.Hidden -> Unit
        }
    }
}

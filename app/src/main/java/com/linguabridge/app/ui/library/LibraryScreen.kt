package com.linguabridge.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import com.linguabridge.app.data.db.content.DialogueEntity
import com.linguabridge.app.data.db.content.ReadingTextEntity

private val LEVELS = listOf("A2", "B1", "B2")

private fun levelLabelRes(level: String): Int = when (level) {
    "A2" -> R.string.library_level_a2
    "B1" -> R.string.library_level_b1
    else -> R.string.library_level_b2
}

@Composable
fun LibraryScreen(
    onOpenText: (String) -> Unit,
    onOpenDialogue: (String) -> Unit,
    onOpenHskGrammar: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(app.container.libraryRepository, app.container.settingsRepository)
    )
    val studyLanguage by viewModel.studyLanguage.collectAsStateWithLifecycle(initialValue = "en")
    val isChinese = studyLanguage == "zh"

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = if (isChinese) {
        listOf(R.string.library_tab_texts, R.string.library_tab_dialogues, R.string.library_tab_grammar)
    } else {
        listOf(R.string.library_tab_texts, R.string.library_tab_dialogues)
    }
    // Guard against a stale tab index when switching study language.
    val safeTabIndex = tabIndex.coerceIn(0, tabs.lastIndex)

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.library_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        TabRow(selectedTabIndex = safeTabIndex) {
            tabs.forEachIndexed { index, labelRes ->
                Tab(
                    selected = safeTabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(stringResource(labelRes)) },
                )
            }
        }
        when (safeTabIndex) {
            0 -> TextsTab(viewModel, isChinese, onOpenText)
            1 -> DialoguesTab(viewModel, onOpenDialogue)
            else -> GrammarTab(onOpenHskGrammar)
        }
    }
}

@Composable
private fun TextsTab(viewModel: LibraryViewModel, isChinese: Boolean, onOpenText: (String) -> Unit) {
    var level by rememberSaveable { mutableStateOf(LEVELS.first()) }
    val effectiveLevel = if (isChinese) "ZH" else level
    val texts by viewModel.textsForLevel(effectiveLevel).collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize()) {
        if (isChinese) {
            Text(
                stringResource(R.string.library_hsk_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LEVELS.forEach { lvl ->
                    FilterChip(
                        selected = level == lvl,
                        onClick = { level = lvl },
                        label = { Text(stringResource(levelLabelRes(lvl))) },
                    )
                }
            }
        }

        val current = texts
        when {
            current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            current.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.library_no_texts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(current, key = { it.id }) { text ->
                    ReadingTextCard(text, onClick = { onOpenText(text.id) })
                }
            }
        }
    }
}

@Composable
private fun GrammarTab(onOpenHskGrammar: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), onClick = onOpenHskGrammar) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.hskgrammar_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.library_hsk_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReadingTextCard(text: ReadingTextEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text.title, style = MaterialTheme.typography.titleMedium)
            SuggestionChip(
                onClick = {},
                enabled = false,
                label = { Text(text.topic) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun DialoguesTab(viewModel: LibraryViewModel, onOpenDialogue: (String) -> Unit) {
    val dialogues by viewModel.dialogues.collectAsStateWithLifecycle(initialValue = null)

    val current = dialogues
    when {
        current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        current.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.library_no_dialogues),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(current, key = { it.id }) { dialogue ->
                DialogueCard(dialogue, onClick = { onOpenDialogue(dialogue.id) })
            }
        }
    }
}

@Composable
private fun DialogueCard(dialogue: DialogueEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dialogue.title, style = MaterialTheme.typography.titleMedium)
            Text(
                dialogue.scenario,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(levelLabelRes(dialogue.level)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

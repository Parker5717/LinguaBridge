package com.linguabridge.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import com.linguabridge.app.data.db.content.HskGrammarEntity

/**
 * Lists all HSK1-2 grammar points (currently 40) for Chinese-mode Library.
 * Loaded once via produceState; no ViewModel needed since there is no
 * mutable/observed state beyond which cards are expanded.
 */
@Composable
fun HskGrammarScreen() {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val grammar by produceState<List<HskGrammarEntity>?>(initialValue = null) {
        value = app.container.libraryRepository.hskGrammar()
    }

    val current = grammar
    when {
        current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        current.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.hskgrammar_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        else -> {
            var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(current, key = { it.id }) { point ->
                    GrammarPointCard(
                        point = point,
                        expanded = expandedId == point.id,
                        onToggle = { expandedId = if (expandedId == point.id) null else point.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun GrammarPointCard(point: HskGrammarEntity, expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(point.title, style = MaterialTheme.typography.titleMedium)
            SuggestionChip(
                onClick = {},
                enabled = false,
                label = { Text(point.pattern) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
            if (expanded) {
                HorizontalDivider()
                Text(point.enExplanation, style = MaterialTheme.typography.bodyMedium)
                if (point.exampleZh.isNotBlank()) {
                    Text(point.exampleZh, style = MaterialTheme.typography.bodyLarge)
                    if (point.examplePinyin.isNotBlank()) {
                        Text(
                            point.examplePinyin,
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (point.exampleEn.isNotBlank()) {
                        Text(
                            point.exampleEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (point.ruNote.isNotBlank()) {
                    Text(
                        point.ruNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

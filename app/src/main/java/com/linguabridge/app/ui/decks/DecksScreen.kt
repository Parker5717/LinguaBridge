package com.linguabridge.app.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import com.linguabridge.app.data.DEFAULT_ACTIVE_DECKS
import kotlinx.coroutines.launch

private data class DeckRow(val type: String, val labelRes: Int, val count: Int)

@Composable
fun DecksScreen() {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val scope = rememberCoroutineScope()
    val settings = app.container.settingsRepository

    val decks by produceState(initialValue = emptyList<DeckRow>()) {
        val dao = app.container.contentDb.cardDao()
        value = listOf(
            DeckRow("en_ru", R.string.deck_en_ru, dao.countByType("en_ru")),
            DeckRow("def_en", R.string.deck_def_en, dao.countByType("def_en")),
            DeckRow("zh_en", R.string.deck_zh_en, dao.countByType("zh_en")),
            DeckRow("gram_term", R.string.deck_gram_term, dao.countByType("gram_term")),
            DeckRow("stem_en", R.string.deck_stem_en, dao.countByType("stem_en")),
        )
    }
    val appSettings by settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val activeDecks = appSettings?.activeDecks ?: DEFAULT_ACTIVE_DECKS

    // Decks follow the study-language switch on Today: Chinese mode studies
    // only the zh_en deck, so showing English decks there was just confusing.
    val visibleDecks = when (appSettings?.studyLanguage) {
        "zh" -> decks.filter { it.type == "zh_en" }
        else -> decks.filter { it.type != "zh_en" }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.decks_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(
                if (appSettings?.studyLanguage == "zh") R.string.decks_mode_chinese
                else R.string.decks_mode_english
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleDecks) { deck ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(deck.labelRes), style = MaterialTheme.typography.bodyLarge)
                            Text("${deck.count}", style = MaterialTheme.typography.labelMedium)
                        }
                        Switch(
                            checked = deck.type in activeDecks,
                            onCheckedChange = { checked ->
                                scope.launch { settings.setDeckActive(deck.type, checked) }
                            },
                        )
                    }
                }
            }
        }
    }
}

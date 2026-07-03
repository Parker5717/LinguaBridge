package com.linguabridge.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import com.linguabridge.app.data.ContentCounts
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(onStartReview: () -> Unit, onOpenDecks: () -> Unit) {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val viewModel: TodayViewModel = viewModel(
        factory = TodayViewModel.Factory(
            app.container.contentRepository,
            app.container.srsRepository,
            app.container.statsRepository,
            app.container.settingsRepository,
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        TodayUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is TodayUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = s.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(24.dp),
            )
        }

        is TodayUiState.Ready -> Dashboard(s, onStartReview, onOpenDecks)
    }
}

/** EN / 中文 study-language switch shown above the queue. */
@Composable
private fun LanguageSwitcher() {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val scope = rememberCoroutineScope()
    val settings by app.container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val selected = settings?.studyLanguage ?: "en"

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selected == "en",
            onClick = { scope.launch { app.container.settingsRepository.setStudyLanguage("en") } },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.lang_english)) }
        SegmentedButton(
            selected = selected == "zh",
            onClick = { scope.launch { app.container.settingsRepository.setStudyLanguage("zh") } },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.lang_chinese)) }
    }
}

@Composable
private fun Dashboard(
    state: TodayUiState.Ready,
    onStartReview: () -> Unit,
    onOpenDecks: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleLarge)

        LanguageSwitcher()

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.today_queue),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QueueStat(stringResource(R.string.queue_due), state.srs.due, MaterialTheme.colorScheme.primary)
                    QueueStat(stringResource(R.string.queue_learning), state.srs.learning, MaterialTheme.colorScheme.tertiary)
                    QueueStat(stringResource(R.string.queue_new), state.srs.newCount, MaterialTheme.colorScheme.secondary)
                }
                Button(onClick = onStartReview, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.today_start_review))
                }
                TextButton(onClick = onOpenDecks, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.today_browse_decks))
                }
                state.streak?.let { streak ->
                    Text(
                        stringResource(
                            R.string.today_streak_line,
                            streak.streakDays,
                            streak.reviewsToday,
                            streak.dailyGoal,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        CountsCard(state.counts)
    }
}

@Composable
private fun QueueStat(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CountsCard(counts: ContentCounts) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.today_content_overview),
                style = MaterialTheme.typography.titleMedium,
            )
            CountRow(stringResource(R.string.count_vocabulary), counts.vocab)
            CountRow(stringResource(R.string.count_cards), counts.cards)
            CountRow(stringResource(R.string.count_grammar_terms), counts.grammarTerms)
            CountRow(stringResource(R.string.count_stem_terms), counts.stemTerms)
            CountRow(stringResource(R.string.count_hsk_words), counts.hskWords)
            CountRow(stringResource(R.string.count_texts), counts.texts)
            CountRow(stringResource(R.string.count_dialogues), counts.dialogues)
        }
    }
}

@Composable
private fun CountRow(label: String, value: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text("$value", style = MaterialTheme.typography.bodyLarge)
    }
}

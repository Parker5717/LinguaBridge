package com.linguabridge.app.ui.wordgame

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import com.linguabridge.app.data.db.content.VocabEntity
import com.linguabridge.app.domain.wordgame.LetterState

@Composable
fun WordGameScreen() {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val viewModel: WordGameViewModel = viewModel(
        factory = WordGameViewModel.Factory(
            app.container.libraryRepository,
            app.container.settingsRepository,
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.unavailable -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.wordgame_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(24.dp),
                textAlign = TextAlign.Center,
            )
        }
        else -> WordGameContent(state, viewModel)
    }
}

@Composable
private fun WordGameContent(state: WordGameUiState, viewModel: WordGameViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.mode == WordGameMode.DAILY,
                onClick = { viewModel.selectMode(WordGameMode.DAILY) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.wordgame_mode_daily)) }
            SegmentedButton(
                selected = state.mode == WordGameMode.PRACTICE,
                onClick = { viewModel.selectMode(WordGameMode.PRACTICE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.wordgame_mode_practice)) }
        }

        if (state.mode == WordGameMode.DAILY && state.dailyAlreadyPlayed && state.status == GameStatus.PLAYING) {
            // Defensive: dailyAlreadyPlayed should always come with a WON/LOST status,
            // but guard the message anyway in case persisted state is malformed.
            Text(
                stringResource(R.string.wordgame_daily_already_played),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        GameBoard(state)

        if (state.invalidGuessMessage) {
            Text(
                stringResource(R.string.wordgame_invalid_guess),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        when (state.status) {
            GameStatus.PLAYING -> Keyboard(state, viewModel)
            GameStatus.WON, GameStatus.LOST -> ResultPanel(state, viewModel)
        }
    }
}

@Composable
private fun GameBoard(state: WordGameUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val rowScores = state.rowScores
        for (row in 0 until MAX_GUESSES) {
            when {
                row < state.guesses.size -> {
                    GameRow(letters = state.guesses[row].uppercase().toList(), scores = rowScores[row])
                }
                row == state.guesses.size && state.status == GameStatus.PLAYING -> {
                    val letters = state.currentInput.uppercase().toList() +
                        List(WORD_LENGTH - state.currentInput.length) { null }
                    GameRow(letters = letters, scores = null)
                }
                else -> {
                    GameRow(letters = List(WORD_LENGTH) { null }, scores = null)
                }
            }
        }
    }
}

@Composable
private fun GameRow(letters: List<Char?>, scores: List<LetterState>?) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        letters.forEachIndexed { index, letter ->
            GameTile(letter = letter, state = scores?.get(index))
        }
    }
}

@Composable
private fun GameTile(letter: Char?, state: LetterState?) {
    val colors = MaterialTheme.colorScheme
    val (background, contentColor, borderColor) = when (state) {
        LetterState.CORRECT -> Triple(colors.tertiary, colors.onTertiary, colors.tertiary)
        LetterState.PRESENT -> Triple(colors.secondary, colors.onSecondary, colors.secondary)
        LetterState.ABSENT -> Triple(colors.surfaceVariant, colors.onSurfaceVariant, colors.surfaceVariant)
        null -> Triple(colors.surface, colors.onSurface, colors.outlineVariant)
    }
    Surface(
        modifier = Modifier
            .size(52.dp)
            .aspectRatio(1f)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp)),
        color = background,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = letter?.toString().orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun Keyboard(state: WordGameUiState, viewModel: WordGameViewModel) {
    val keyStates = state.keyStates
    val rows = listOf(
        "qwertyuiop".toList(),
        "asdfghjkl".toList(),
        "zxcvbnm".toList(),
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (rowIndex == 2) {
                    ActionKey(onClick = viewModel::submitGuess, label = stringResource(R.string.wordgame_enter))
                }
                row.forEach { c ->
                    KeyboardKey(letter = c, state = keyStates[c], onClick = { viewModel.onKeyPress(c) })
                }
                if (rowIndex == 2) {
                    ActionKey(
                        onClick = viewModel::onBackspace,
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = stringResource(R.string.wordgame_backspace),
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardKey(letter: Char, state: LetterState?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (background, contentColor) = when (state) {
        LetterState.CORRECT -> colors.tertiary to colors.onTertiary
        LetterState.PRESENT -> colors.secondary to colors.onSecondary
        LetterState.ABSENT -> colors.surfaceVariant to colors.onSurfaceVariant
        null -> colors.surfaceContainerHigh to colors.onSurface
    }
    Surface(
        modifier = Modifier
            .width(32.dp)
            .height(44.dp)
            .clickable(onClick = onClick),
        color = background,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(letter.uppercase(), style = MaterialTheme.typography.labelLarge, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ActionKey(
    onClick: () -> Unit,
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    contentDescription: String? = null,
) {
    Surface(
        modifier = Modifier
            .width(48.dp)
            .height(44.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
            } else if (label != null) {
                Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ResultPanel(state: WordGameUiState, viewModel: WordGameViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.status == GameStatus.WON) {
                Text(
                    stringResource(R.string.wordgame_solved, state.guesses.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(
                    stringResource(R.string.wordgame_lost, state.secret.uppercase()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }

            state.secretEntry?.let { entry -> WordCardInfo(entry) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::addToDeck, enabled = !state.addedToDeck) {
                    Text(
                        if (state.addedToDeck) {
                            stringResource(R.string.dictionary_added_to_deck)
                        } else {
                            stringResource(R.string.dictionary_add_to_deck)
                        }
                    )
                }
                if (state.mode == WordGameMode.PRACTICE) {
                    Button(onClick = viewModel::newPracticeWord) {
                        Text(stringResource(R.string.wordgame_new_word))
                    }
                }
            }
        }
    }
}

@Composable
private fun WordCardInfo(entry: VocabEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            entry.headword,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            entry.ruTranslation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (entry.enDefinition.isNotBlank()) {
            Text(entry.enDefinition, style = MaterialTheme.typography.bodyMedium)
        }
        if (entry.example1.isNotBlank()) {
            Text(
                "${stringResource(R.string.dictionary_example_label)}: ${entry.example1}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!entry.example1Ru.isNullOrBlank()) {
                Text(
                    entry.example1Ru,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

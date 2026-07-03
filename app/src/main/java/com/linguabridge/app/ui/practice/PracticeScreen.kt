package com.linguabridge.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.linguabridge.app.R

@Composable
fun PracticeScreen(
    onOpenDictation: () -> Unit,
    onOpenListening: () -> Unit,
    onOpenQuizzes: () -> Unit,
    onOpenDictionary: () -> Unit,
    onOpenWordGame: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.practice_title), style = MaterialTheme.typography.titleLarge)

        PracticeCard(
            icon = Icons.Filled.Mic,
            title = stringResource(R.string.practice_dictation_title),
            description = stringResource(R.string.practice_dictation_description),
            enabled = true,
            onClick = onOpenDictation,
        )
        PracticeCard(
            icon = Icons.Filled.Headphones,
            title = stringResource(R.string.practice_listening_title),
            description = stringResource(R.string.practice_listening_description),
            enabled = true,
            onClick = onOpenListening,
        )
        PracticeCard(
            icon = Icons.Filled.Quiz,
            title = stringResource(R.string.practice_quizzes_title),
            description = stringResource(R.string.practice_quizzes_description),
            enabled = true,
            onClick = onOpenQuizzes,
        )
        PracticeCard(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = stringResource(R.string.dictionary_title),
            description = stringResource(R.string.practice_dictionary_description),
            enabled = true,
            onClick = onOpenDictionary,
        )
        PracticeCard(
            icon = Icons.Filled.GridView,
            title = stringResource(R.string.wordgame_title),
            description = stringResource(R.string.practice_wordgame_description),
            enabled = true,
            onClick = onOpenWordGame,
        )
    }
}

@Composable
private fun PracticeCard(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        colors = if (enabled) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

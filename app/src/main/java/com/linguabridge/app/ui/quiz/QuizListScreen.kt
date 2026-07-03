package com.linguabridge.app.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R

private data class QuizCategoryInfo(
    val category: String,
    val icon: ImageVector,
    val titleRes: Int,
    val descriptionRes: Int,
)

private val CATEGORY_INFO = listOf(
    QuizCategoryInfo("vocab", Icons.Filled.Translate, R.string.quiz_category_vocab, R.string.quiz_category_vocab_description),
    QuizCategoryInfo("engrammar", Icons.Filled.Gavel, R.string.quiz_category_engrammar, R.string.quiz_category_engrammar_description),
    QuizCategoryInfo("gramterm", Icons.Filled.Book, R.string.quiz_category_gramterm, R.string.quiz_category_gramterm_description),
    QuizCategoryInfo("stem", Icons.Filled.Science, R.string.quiz_category_stem, R.string.quiz_category_stem_description),
    QuizCategoryInfo("hsk", Icons.Filled.Language, R.string.quiz_category_hsk, R.string.quiz_category_hsk_description),
    QuizCategoryInfo("csca", Icons.Filled.Calculate, R.string.quiz_category_csca, R.string.quiz_category_csca_description),
)

@Composable
fun QuizListScreen(onOpenQuiz: (String) -> Unit, onOpenPlacement: () -> Unit) {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val viewModel: QuizListViewModel = viewModel(
        factory = QuizListViewModel.Factory(app.container.quizRepository)
    )
    val lastPlacement by viewModel.lastPlacementLevel.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.quiz_list_title), style = MaterialTheme.typography.titleLarge)
        }
        item {
            PlacementCard(lastPlacement, onClick = onOpenPlacement)
        }
        items(CATEGORY_INFO, key = { it.category }) { info ->
            QuizCategoryCard(info, onClick = { onOpenQuiz(info.category) })
        }
    }
}

@Composable
private fun PlacementCard(lastPlacement: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                stringResource(R.string.quiz_placement_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.quiz_placement_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (lastPlacement != null) {
                Text(
                    stringResource(R.string.quiz_placement_last_result, stringResource(placementLevelLabelRes(lastPlacement))),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun QuizCategoryCard(info: QuizCategoryInfo, onClick: () -> Unit) {
    val isCsca = info.category == "csca"
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = if (isCsca) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                info.icon,
                contentDescription = null,
                tint = if (isCsca) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(info.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = if (isCsca) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(info.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCsca) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

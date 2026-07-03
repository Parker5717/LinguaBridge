package com.linguabridge.app.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import com.linguabridge.app.domain.exercise.AnswerVerdict

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSessionScreen(category: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val viewModel: QuizSessionViewModel = viewModel(
        factory = QuizSessionViewModel.Factory(app.container.quizRepository, category),
        key = category,
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(quizCategoryTitleRes(category))) },
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
                QuizSessionUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is QuizSessionUiState.Question -> QuestionBody(s, viewModel)
                is QuizSessionUiState.Finished -> FinishedBody(s, onRestart = viewModel::restart, onBack = onBack)
            }
        }
    }
}

@Composable
private fun QuestionBody(state: QuizSessionUiState.Question, viewModel: QuizSessionViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val total = state.total.coerceAtLeast(1)
        LinearProgressIndicator(
            progress = { state.index.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.quiz_question_progress, state.index + 1, state.total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))

        Text(state.item.question.prompt, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        if (state.item.question.type == "typing") {
            TypingBody(state, viewModel)
        } else {
            McqBody(state, viewModel)
        }

        if (state.verdict != null) {
            Spacer(Modifier.height(16.dp))
            ExplanationCard(state.verdict, state.item.question.answer, state.item.question.explanation)
            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::next, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.exercise_continue))
            }
        }
    }
}

@Composable
private fun McqBody(state: QuizSessionUiState.Question, viewModel: QuizSessionViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.item.options.forEach { option ->
            val isSelected = option == state.selected
            val isAnswer = option == state.item.question.answer
            val colors = when {
                state.selected == null -> CardDefaults.cardColors()
                isAnswer -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                isSelected -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                else -> CardDefaults.cardColors()
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = colors,
                onClick = { viewModel.selectOption(option) },
                enabled = state.selected == null,
            ) {
                Text(option, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun TypingBody(state: QuizSessionUiState.Question, viewModel: QuizSessionViewModel) {
    Column {
        OutlinedTextField(
            value = state.typed,
            onValueChange = viewModel::updateTyped,
            enabled = state.verdict == null,
            label = { Text(stringResource(R.string.exercise_answer_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (state.typed.isNotBlank()) viewModel.submitTyped() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.verdict == null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::submitTyped,
                enabled = state.typed.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.exercise_check)) }
        }
    }
}

@Composable
private fun ExplanationCard(verdict: AnswerVerdict, answer: String, explanation: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (verdict == AnswerVerdict.WRONG) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(
                    when (verdict) {
                        AnswerVerdict.CORRECT -> R.string.feedback_correct
                        AnswerVerdict.TYPO -> R.string.feedback_typo
                        AnswerVerdict.WRONG -> R.string.feedback_wrong
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (verdict != AnswerVerdict.CORRECT) {
                Text(stringResource(R.string.feedback_answer_is, answer), style = MaterialTheme.typography.bodyMedium)
            }
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FinishedBody(state: QuizSessionUiState.Finished, onRestart: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(R.string.quiz_session_summary, state.score, state.total),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.review_back))
            }
            Button(onClick = onRestart, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.quiz_try_again))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.quiz_review_title), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.review, key = { it.item.question.id }) { answered ->
                ReviewRow(answered)
            }
        }
    }
}

@Composable
private fun ReviewRow(answered: AnsweredQuestion) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val correct = answered.verdict != AnswerVerdict.WRONG
            Icon(
                if (correct) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                contentDescription = null,
                tint = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.fillMaxWidth()) {
                Text(answered.item.question.prompt, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.quiz_review_your_answer, answered.givenAnswer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun quizCategoryTitleRes(category: String): Int = when (category) {
    "vocab" -> R.string.quiz_category_vocab
    "engrammar" -> R.string.quiz_category_engrammar
    "gramterm" -> R.string.quiz_category_gramterm
    "stem" -> R.string.quiz_category_stem
    "hsk" -> R.string.quiz_category_hsk
    "csca" -> R.string.quiz_category_csca
    "placement" -> R.string.quiz_placement_title
    else -> R.string.practice_quizzes_title
}

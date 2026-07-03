package com.linguabridge.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.linguabridge.app.data.SettingsRepository
import com.linguabridge.app.domain.exercise.AnswerVerdict
import com.linguabridge.app.domain.exercise.Exercise
import com.linguabridge.app.domain.exercise.ExerciseKind
import com.linguabridge.app.tts.TtsLanguage
import com.linguabridge.app.tts.TtsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(onFinished: () -> Unit) {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val viewModel: ReviewViewModel = viewModel(
        factory = ReviewViewModel.Factory(app.container.srsRepository, app.container.settingsRepository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        ReviewUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is ReviewUiState.Empty -> EmptyContent(s, onFinished, viewModel::studyMore)

        is ReviewUiState.Finished -> CenteredMessage(
            stringResource(R.string.review_session_summary, s.done, s.correct), onFinished
        )

        is ReviewUiState.Studying -> ExerciseContent(s, viewModel, app.container.ttsManager, app.container.settingsRepository)
    }
}

@Composable
private fun EmptyContent(
    state: ReviewUiState.Empty,
    onFinished: () -> Unit,
    onStudyMore: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(
                if (state.hasMoreNew) R.string.review_daily_limit_reached
                else R.string.review_nothing_due
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        state.nextDueAt?.let { dueAt ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.review_next_due, formatDueTime(dueAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (state.hasMoreNew) {
            Button(onClick = onStudyMore, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_study_more))
            }
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = onFinished) { Text(stringResource(R.string.review_back)) }
    }
}

/** Same-day due times as HH:mm, later ones with the date. */
private fun formatDueTime(dueAt: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val due = java.time.Instant.ofEpochMilli(dueAt).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val pattern = if (due.toLocalDate() == today) "HH:mm" else "dd.MM HH:mm"
    return due.format(java.time.format.DateTimeFormatter.ofPattern(pattern))
}

@Composable
private fun CenteredMessage(message: String, onFinished: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onFinished) { Text(stringResource(R.string.review_back)) }
    }
}

@Composable
private fun ExerciseContent(
    state: ReviewUiState.Studying,
    viewModel: ReviewViewModel,
    ttsManager: TtsManager,
    settingsRepository: SettingsRepository,
) {
    val ex = state.exercise
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val total = (state.done + state.remaining).coerceAtLeast(1)
        LinearProgressIndicator(
            progress = { state.done.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            TaskLabel(ex.kind)
            Spacer(Modifier.height(8.dp))

            when (ex.kind) {
                ExerciseKind.INTRO -> IntroBody(ex, ttsManager, settingsRepository)
                ExerciseKind.CHOICE_FORWARD, ExerciseKind.CHOICE_REVERSE ->
                    ChoiceBody(ex, enabled = state.feedback == null, onChoose = viewModel::chooseOption)
                ExerciseKind.TYPE_ANSWER, ExerciseKind.CLOZE, ExerciseKind.SENTENCE_TRANSLATE ->
                    TypeBody(ex, enabled = state.feedback == null, onSubmit = viewModel::submitTyped)
            }

            state.feedback?.let { FeedbackBanner(it, ex, ttsManager, settingsRepository) }
        }

        Spacer(Modifier.height(12.dp))

        when {
            ex.kind == ExerciseKind.INTRO -> Button(
                onClick = viewModel::continueIntro,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.exercise_got_it)) }

            state.feedback != null -> Button(
                onClick = viewModel::next,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.exercise_continue)) }

            else -> TextButton(
                onClick = viewModel::giveUp,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.exercise_dont_know)) }
        }
    }
}

@Composable
private fun TaskLabel(kind: ExerciseKind) {
    val res = when (kind) {
        ExerciseKind.INTRO -> R.string.task_intro
        ExerciseKind.CHOICE_FORWARD -> R.string.task_choose_translation
        ExerciseKind.CHOICE_REVERSE -> R.string.task_choose_word
        ExerciseKind.TYPE_ANSWER -> R.string.task_type_word
        ExerciseKind.CLOZE -> R.string.task_fill_gap
        ExerciseKind.SENTENCE_TRANSLATE -> R.string.task_translate_sentence
    }
    Text(
        stringResource(res),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun IntroBody(ex: Exercise, ttsManager: TtsManager, settingsRepository: SettingsRepository) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ex.prompt, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                PlayTtsButton(ex.card.card.ttsText, ex.card.card.ttsLang, ttsManager, settingsRepository)
            }
            ex.promptHint?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            }
            Text(ex.fullBack, style = MaterialTheme.typography.titleMedium)
            ex.example?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Small icon button that speaks [ttsText] in [ttsLang] ("en"/"zh"); hidden when there's nothing to say. */
@Composable
private fun PlayTtsButton(
    ttsText: String?,
    ttsLang: String?,
    ttsManager: TtsManager,
    settingsRepository: SettingsRepository,
) {
    if (ttsText.isNullOrBlank()) return
    val scope = rememberCoroutineScope()
    IconButton(onClick = {
        scope.launch {
            val rate = settingsRepository.settings.first().ttsRate
            val language = if (ttsLang == "zh") TtsLanguage.CHINESE else TtsLanguage.ENGLISH
            ttsManager.speak(ttsText, language, rate)
        }
    }) {
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.dialogue_play_line))
    }
}

@Composable
private fun ChoiceBody(ex: Exercise, enabled: Boolean, onChoose: (String) -> Unit) {
    Text(ex.prompt, style = MaterialTheme.typography.headlineSmall)
    ex.promptHint?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ex.options.forEach { option ->
            OutlinedButton(
                onClick = { onChoose(option) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(option, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun TypeBody(ex: Exercise, enabled: Boolean, onSubmit: (String) -> Unit) {
    var text by remember(ex) { mutableStateOf("") }
    Text(ex.prompt, style = MaterialTheme.typography.headlineSmall)
    ex.promptHint?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
    }
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        enabled = enabled,
        singleLine = ex.kind != ExerciseKind.SENTENCE_TRANSLATE,
        minLines = if (ex.kind == ExerciseKind.SENTENCE_TRANSLATE) 2 else 1,
        label = { Text(stringResource(R.string.exercise_answer_label)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSubmit(text) }),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onSubmit(text) },
        enabled = enabled && text.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.exercise_check)) }
}

@Composable
private fun FeedbackBanner(
    fb: Feedback,
    ex: Exercise,
    ttsManager: TtsManager,
    settingsRepository: SettingsRepository,
) {
    Spacer(Modifier.height(16.dp))
    val (container, titleRes) = when (fb.verdict) {
        AnswerVerdict.CORRECT -> MaterialTheme.colorScheme.secondaryContainer to R.string.feedback_correct
        AnswerVerdict.TYPO -> MaterialTheme.colorScheme.tertiaryContainer to R.string.feedback_typo
        AnswerVerdict.WRONG -> MaterialTheme.colorScheme.errorContainer to R.string.feedback_wrong
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                PlayTtsButton(ex.card.card.ttsText, ex.card.card.ttsLang, ttsManager, settingsRepository)
            }
            if (fb.verdict != AnswerVerdict.CORRECT) {
                Text(
                    stringResource(R.string.feedback_answer_is, fb.correctAnswer),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(fb.fullBack, style = MaterialTheme.typography.bodyMedium)
            fb.example?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

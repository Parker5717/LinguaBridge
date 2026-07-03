package com.linguabridge.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linguabridge.app.LinguaBridgeApp
import com.linguabridge.app.R
import com.linguabridge.app.data.SkillProgress
import java.time.LocalDate
import kotlin.math.roundToInt

private const val HEATMAP_WEEKS = 26
private val CELL_SIZE = 10.dp
private val CELL_GAP = 2.dp

@Composable
fun StatsScreen() {
    val app = LocalContext.current.applicationContext as LinguaBridgeApp
    val viewModel: StatsViewModel = viewModel(
        factory = StatsViewModel.Factory(
            app.container.statsRepository,
            app.container.settingsRepository,
        )
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleLarge)

        StreakCard(state)
        HeatmapCard(state.heatmap)
        RetentionCard(state.retention)
        SkillProgressCard(state.skillProgress)
        TotalsCard(state)
    }
}

@Composable
private fun StreakCard(state: StatsUiState) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(stringResource(R.string.stats_streak_label), style = MaterialTheme.typography.labelMedium)
                Text(
                    "🔥 " + stringResource(R.string.stats_streak_days, state.streakDays),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            val progress = if (state.dailyGoal > 0) {
                (state.reviewsToday.toFloat() / state.dailyGoal.toFloat()).coerceIn(0f, 1f)
            } else 0f
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                )
                Text(
                    stringResource(R.string.stats_today_progress, state.reviewsToday, state.dailyGoal),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun HeatmapCard(heatmap: Map<LocalDate, Int>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stats_heatmap_title), style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WeekdayLabels()
                HeatmapGrid(heatmap)
            }
        }
    }
}

@Composable
private fun WeekdayLabels() {
    val rowSize = CELL_SIZE + CELL_GAP
    Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        for (i in 0 until 7) {
            Box(Modifier.height(CELL_SIZE), contentAlignment = Alignment.CenterStart) {
                val label = when (i) {
                    0 -> stringResource(R.string.stats_weekday_mon)
                    2 -> stringResource(R.string.stats_weekday_wed)
                    4 -> stringResource(R.string.stats_weekday_fri)
                    else -> ""
                }
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Spacer(Modifier.width(rowSize - CELL_SIZE))
}

@Composable
private fun HeatmapGrid(heatmap: Map<LocalDate, Int>) {
    val today = LocalDate.now()
    // Align the grid to whole weeks: the first column starts on the Monday
    // that begins the (HEATMAP_WEEKS-1) weeks-ago week.
    val todayDow = today.dayOfWeek.value // 1=Mon .. 7=Sun
    val gridStart = today.minusDays((todayDow - 1).toLong()).minusWeeks((HEATMAP_WEEKS - 1).toLong())

    val maxValue = (heatmap.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    val cellPx = CELL_SIZE
    val gapPx = CELL_GAP
    val widthDp = (cellPx + gapPx) * HEATMAP_WEEKS
    val heightDp = (cellPx + gapPx) * 7

    Canvas(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
    ) {
        val cell = cellPx.toPx()
        val gap = gapPx.toPx()
        for (week in 0 until HEATMAP_WEEKS) {
            for (dow in 0 until 7) {
                val date = gridStart.plusDays((week * 7 + dow).toLong())
                if (date.isAfter(today)) continue
                val value = heatmap[date] ?: 0
                val color = bucketColor(value, maxValue, surfaceVariant, primary)
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = week * (cell + gap),
                        y = dow * (cell + gap),
                    ),
                    size = Size(cell, cell),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                )
            }
        }
    }
}

private fun bucketColor(value: Int, maxValue: Int, empty: Color, base: Color): Color {
    if (value <= 0) return empty
    val ratio = value.toFloat() / maxValue.toFloat()
    val alpha = when {
        ratio <= 0.3f -> 0.3f
        ratio <= 0.6f -> 0.55f
        ratio <= 0.8f -> 0.8f
        else -> 1.0f
    }
    return base.copy(alpha = alpha)
}

@Composable
private fun RetentionCard(retention: Float?) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.stats_retention_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = retention?.let { stringResource(R.string.stats_retention_value, (it * 100).roundToInt()) }
                    ?: stringResource(R.string.stats_retention_unknown),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun SkillProgressCard(skills: List<SkillProgress>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.stats_skill_progress_title), style = MaterialTheme.typography.titleMedium)
            skills.forEach { skill ->
                SkillRow(skill)
            }
        }
    }
}

@Composable
private fun SkillRow(skill: SkillProgress) {
    val labelRes = when (skill.deckType) {
        "en_ru" -> R.string.stats_skill_en_ru
        "zh_en" -> R.string.stats_skill_zh_en
        "gram_term" -> R.string.stats_skill_gram_term
        "stem_en" -> R.string.stats_skill_stem_en
        else -> R.string.stats_skill_en_ru
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
        val progress = if (skill.totalCount > 0) {
            (skill.learnedCount.toFloat() / skill.totalCount.toFloat()).coerceIn(0f, 1f)
        } else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.stats_skill_progress_row, skill.learnedCount, skill.totalCount),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun TotalsCard(state: StatsUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.stats_totals_title), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TotalStat(stringResource(R.string.stats_totals_reviews), state.totalReviews)
                TotalStat(stringResource(R.string.stats_totals_dictations), state.totalDictations)
                TotalStat(stringResource(R.string.stats_totals_quizzes), state.totalQuizzes)
            }
        }
    }
}

@Composable
private fun TotalStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

package com.linguabridge.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linguabridge.app.data.SettingsRepository
import com.linguabridge.app.data.SkillProgress
import com.linguabridge.app.data.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val HEATMAP_WEEKS = 26
private const val RETENTION_DAYS = 30

data class StatsUiState(
    val streakDays: Int = 0,
    val reviewsToday: Int = 0,
    val dailyGoal: Int = 30,
    val heatmap: Map<LocalDate, Int> = emptyMap(),
    val retention: Float? = null,
    val skillProgress: List<SkillProgress> = emptyList(),
    val totalReviews: Int = 0,
    val totalDictations: Int = 0,
    val totalQuizzes: Int = 0,
    val loading: Boolean = true,
)

class StatsViewModel(
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val today = StatsRepository.today(now)
            val dailyGoal = settingsRepository.settings.first().dailyGoalReviews

            val heatmap = statsRepository.heatmap(HEATMAP_WEEKS, today)
            val streak = statsRepository.streak(today)
            val retention = statsRepository.retention(RETENTION_DAYS, now)
            val skillProgress = statsRepository.skillProgress()
            val todayStat = statsRepository.todayActivity(today)
            val totals = statsRepository.allTimeTotals()

            _uiState.value = StatsUiState(
                streakDays = streak,
                reviewsToday = todayStat?.reviewsDone ?: 0,
                dailyGoal = dailyGoal,
                heatmap = heatmap,
                retention = retention,
                skillProgress = skillProgress,
                totalReviews = totals.reviews,
                totalDictations = totals.dictations,
                totalQuizzes = totals.quizzes,
                loading = false,
            )
        }
    }

    class Factory(
        private val statsRepository: StatsRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StatsViewModel(statsRepository, settingsRepository) as T
    }
}

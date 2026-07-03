package com.linguabridge.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linguabridge.app.data.ContentCounts
import com.linguabridge.app.data.ContentRepository
import com.linguabridge.app.data.SettingsRepository
import com.linguabridge.app.data.SrsRepository
import com.linguabridge.app.data.StatsRepository
import com.linguabridge.app.data.decksForStudy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SrsCounts(val due: Int, val newCount: Int, val learning: Int)

data class StreakInfo(val streakDays: Int, val reviewsToday: Int, val dailyGoal: Int)

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data class Ready(
        val counts: ContentCounts,
        val srs: SrsCounts,
        val streak: StreakInfo?,
    ) : TodayUiState
    data class Error(val message: String) : TodayUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val contentRepository: ContentRepository,
    private val srsRepository: SrsRepository,
    private val statsRepository: StatsRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val contentCounts = MutableStateFlow<ContentCounts?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val streakInfo = MutableStateFlow<StreakInfo?>(null)

    // Counts follow the active-deck selection, so toggling a deck in Decks
    // immediately changes what Today shows.
    private val srsCounts = settingsRepository.settings.flatMapLatest { s ->
        val decks = s.decksForStudy()
        combine(
            srsRepository.countDue(System.currentTimeMillis(), decks),
            srsRepository.countNew(decks),
            srsRepository.countLearning(decks),
        ) { due, newCount, learning -> SrsCounts(due, newCount, learning) }
    }

    val uiState: StateFlow<TodayUiState> = combine(
        contentCounts,
        error,
        srsCounts,
        streakInfo,
    ) { counts, err, srs, streak ->
        when {
            err != null -> TodayUiState.Error(err)
            counts == null -> TodayUiState.Loading
            else -> TodayUiState.Ready(counts, srs, streak)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState.Loading)

    init {
        viewModelScope.launch {
            try {
                srsRepository.seedNewCards(System.currentTimeMillis())
                contentCounts.value = contentRepository.counts()

                val now = System.currentTimeMillis()
                val today = StatsRepository.today(now)
                val goal = settingsRepository.settings.first().dailyGoalReviews
                val reviewsToday = statsRepository.todayActivity(today)?.reviewsDone ?: 0
                streakInfo.value = StreakInfo(
                    streakDays = statsRepository.streak(today),
                    reviewsToday = reviewsToday,
                    dailyGoal = goal,
                )
            } catch (e: Exception) {
                error.value = e.message ?: "Failed to open content database"
            }
        }
    }

    class Factory(
        private val contentRepository: ContentRepository,
        private val srsRepository: SrsRepository,
        private val statsRepository: StatsRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TodayViewModel(contentRepository, srsRepository, statsRepository, settingsRepository) as T
    }
}

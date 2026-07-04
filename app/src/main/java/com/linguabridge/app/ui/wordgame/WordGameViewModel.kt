package com.linguabridge.app.ui.wordgame

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.linguabridge.app.R
import com.linguabridge.app.data.LibraryRepository
import com.linguabridge.app.data.SettingsRepository
import com.linguabridge.app.data.db.content.VocabEntity
import com.linguabridge.app.domain.wordgame.LetterState
import com.linguabridge.app.domain.wordgame.dailyIndex
import com.linguabridge.app.domain.wordgame.keyboardState
import com.linguabridge.app.domain.wordgame.scoreGuess
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val WORD_LENGTH = 5
const val MAX_GUESSES = 6

enum class WordGameMode { DAILY, PRACTICE }

enum class GameStatus { PLAYING, WON, LOST }

data class WordGameUiState(
    val mode: WordGameMode = WordGameMode.DAILY,
    val loading: Boolean = true,
    /** True once we've confirmed the bundled dictionary has too few 5-letter words to play. */
    val unavailable: Boolean = false,
    val secret: String = "",
    val secretEntry: VocabEntity? = null,
    val guesses: List<String> = emptyList(),
    val currentInput: String = "",
    val status: GameStatus = GameStatus.PLAYING,
    val invalidGuessMessage: Boolean = false,
    /** True when a well-formed 5-letter guess was rejected for not being a recognized word. Transient, cleared on next input change. */
    val invalidWordMessage: Boolean = false,
    /** True if today's daily game was already completed before this screen opened. */
    val dailyAlreadyPlayed: Boolean = false,
    val addedToDeck: Boolean = false,
) {
    val rowScores: List<List<LetterState>>
        get() = guesses.map { scoreGuess(secret, it) }

    val keyStates: Map<Char, LetterState>
        get() = keyboardState(guesses, secret)
}

/**
 * Drives both game modes off the same shared 5-letter vocab pool:
 * - Daily: the word is `pool[dailyIndex(today)]`, one attempt per calendar day,
 *   persisted via [SettingsRepository.setWordGameDaily] so re-opening the
 *   screen after a completed daily game shows the result instead of a fresh board.
 * - Practice: a random word from the pool, freely replayable.
 */
class WordGameViewModel(
    private val application: Application,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordGameUiState())
    val uiState: StateFlow<WordGameUiState> = _uiState.asStateFlow()

    private var pool: List<VocabEntity> = emptyList()

    /** Extra accepted 5-letter guesses beyond the vocab pool, loaded lazily from `res/raw/wordle_valid.txt`. */
    private var validGuessWords: Set<String>? = null

    /** Guards against double-submits while [isAcceptedGuess] is checking (first call loads the dictionary file). */
    private var checkingGuess = false

    init {
        viewModelScope.launch {
            pool = libraryRepository.wordGamePool()
            if (pool.size < 2) {
                _uiState.value = _uiState.value.copy(loading = false, unavailable = true)
                return@launch
            }
            startDaily()
        }
    }

    private fun todayIso(): String = LocalDate.now(ZoneId.systemDefault()).toString()

    fun selectMode(mode: WordGameMode) {
        if (_uiState.value.mode == mode || _uiState.value.unavailable) return
        when (mode) {
            WordGameMode.DAILY -> viewModelScope.launch { startDaily() }
            WordGameMode.PRACTICE -> startPractice()
        }
    }

    private suspend fun startDaily() {
        val today = LocalDate.now(ZoneId.systemDefault())
        val index = dailyIndex(today, pool.size)
        val entry = pool[index]
        val settings = settingsRepository.settings.first()
        val playedToday = settings.wordGameDailyDate == today.toString()

        if (playedToday) {
            val result = settings.wordGameDailyResult.orEmpty()
            val (status, guessCount) = parseResult(result)
            _uiState.value = WordGameUiState(
                mode = WordGameMode.DAILY,
                loading = false,
                secret = entry.headword,
                secretEntry = entry,
                guesses = if (status == GameStatus.WON) List(guessCount) { entry.headword } else emptyList(),
                status = status,
                dailyAlreadyPlayed = true,
            )
        } else {
            _uiState.value = WordGameUiState(
                mode = WordGameMode.DAILY,
                loading = false,
                secret = entry.headword,
                secretEntry = entry,
            )
        }
    }

    /** "win:3" -> (WON, 3); "loss" -> (LOST, 0); anything else -> (PLAYING, 0). */
    private fun parseResult(result: String): Pair<GameStatus, Int> = when {
        result.startsWith("win:") -> GameStatus.WON to (result.removePrefix("win:").toIntOrNull() ?: MAX_GUESSES)
        result == "loss" -> GameStatus.LOST to 0
        else -> GameStatus.PLAYING to 0
    }

    private fun startPractice() {
        val entry = pool.random()
        _uiState.value = WordGameUiState(
            mode = WordGameMode.PRACTICE,
            loading = false,
            secret = entry.headword,
            secretEntry = entry,
        )
    }

    /** Called by "New word" in Practice mode to replay with a fresh random word. */
    fun newPracticeWord() {
        if (_uiState.value.mode != WordGameMode.PRACTICE) return
        startPractice()
    }

    fun onInputChange(text: String) {
        val filtered = text.lowercase().filter { it in 'a'..'z' }.take(WORD_LENGTH)
        _uiState.value = _uiState.value.copy(
            currentInput = filtered,
            invalidGuessMessage = false,
            invalidWordMessage = false,
        )
    }

    fun onKeyPress(c: Char) {
        val state = _uiState.value
        if (state.status != GameStatus.PLAYING || state.currentInput.length >= WORD_LENGTH) return
        _uiState.value = state.copy(
            currentInput = state.currentInput + c,
            invalidGuessMessage = false,
            invalidWordMessage = false,
        )
    }

    fun onBackspace() {
        val state = _uiState.value
        if (state.status != GameStatus.PLAYING || state.currentInput.isEmpty()) return
        _uiState.value = state.copy(
            currentInput = state.currentInput.dropLast(1),
            invalidGuessMessage = false,
            invalidWordMessage = false,
        )
    }

    /** Loads (and caches) the bundled dictionary of accepted 5-letter guesses from `res/raw/wordle_valid.txt`. */
    private suspend fun loadValidGuessWords(): Set<String> {
        validGuessWords?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            application.resources.openRawResource(R.raw.wordle_valid).bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }.filter { it.length == WORD_LENGTH }.toSet()
            }
        }
        validGuessWords = loaded
        return loaded
    }

    /**
     * A guess is accepted if it's one of the pool's headwords (so the secret
     * word itself is always guessable) or appears in the bundled dictionary
     * of common 5-letter English words.
     */
    private suspend fun isAcceptedGuess(guess: String): Boolean {
        if (pool.any { it.headword == guess }) return true
        return guess in loadValidGuessWords()
    }

    /**
     * Submits the current input as a guess. Malformed input (wrong length or
     * non a-z characters) and well-formed guesses that aren't real words
     * (checked against the vocab pool + bundled dictionary) are both rejected
     * without consuming an attempt.
     */
    fun submitGuess() {
        val state = _uiState.value
        if (state.status != GameStatus.PLAYING || checkingGuess) return
        val guess = state.currentInput
        if (guess.length != WORD_LENGTH || guess.any { it !in 'a'..'z' }) {
            _uiState.value = state.copy(invalidGuessMessage = true)
            return
        }

        checkingGuess = true
        viewModelScope.launch {
            val accepted = isAcceptedGuess(guess)
            checkingGuess = false
            if (!accepted) {
                _uiState.value = _uiState.value.copy(invalidWordMessage = true)
                return@launch
            }
            commitGuess(guess)
        }
    }

    private fun commitGuess(guess: String) {
        val state = _uiState.value
        if (state.status != GameStatus.PLAYING) return

        val newGuesses = state.guesses + guess
        val won = guess == state.secret
        val lost = !won && newGuesses.size >= MAX_GUESSES
        val newStatus = when {
            won -> GameStatus.WON
            lost -> GameStatus.LOST
            else -> GameStatus.PLAYING
        }

        _uiState.value = state.copy(
            guesses = newGuesses,
            currentInput = "",
            status = newStatus,
            invalidGuessMessage = false,
            invalidWordMessage = false,
        )

        if (state.mode == WordGameMode.DAILY && newStatus != GameStatus.PLAYING) {
            val result = if (won) "win:${newGuesses.size}" else "loss"
            viewModelScope.launch {
                settingsRepository.setWordGameDaily(todayIso(), result)
            }
        }
    }

    fun addToDeck() {
        val entry = _uiState.value.secretEntry ?: return
        if (_uiState.value.addedToDeck) return
        viewModelScope.launch {
            val added = libraryRepository.addToDeckFront(entry.id)
            if (added) {
                _uiState.value = _uiState.value.copy(addedToDeck = true)
            }
        }
    }

    class Factory(
        private val application: Application,
        private val libraryRepository: LibraryRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WordGameViewModel(application, libraryRepository, settingsRepository) as T
    }
}

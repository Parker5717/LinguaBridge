package com.linguabridge.app.domain.wordgame

import java.time.LocalDate
import kotlin.math.absoluteValue

/** Per-letter verdict for a single guess row, classic Wordle semantics. */
enum class LetterState { CORRECT, PRESENT, ABSENT }

/**
 * Scores [guess] against [secret], both expected to be same-length, lowercase,
 * a-z strings. Duplicate letters are handled with the standard two-pass
 * algorithm: exact-position matches are resolved first and removed from the
 * pool of "remaining" secret letters, then leftover guess letters are matched
 * against whatever counts remain — so a letter is only ever marked PRESENT as
 * many times as it actually still occurs in the secret.
 */
fun scoreGuess(secret: String, guess: String): List<LetterState> {
    require(secret.length == guess.length) { "secret and guess must be the same length" }

    val result = MutableList(guess.length) { LetterState.ABSENT }
    val remaining = HashMap<Char, Int>()

    // Pass 1: exact matches.
    for (i in secret.indices) {
        if (guess[i] == secret[i]) {
            result[i] = LetterState.CORRECT
        } else {
            remaining[secret[i]] = (remaining[secret[i]] ?: 0) + 1
        }
    }

    // Pass 2: remaining letters, consuming from the leftover pool.
    for (i in guess.indices) {
        if (result[i] == LetterState.CORRECT) continue
        val c = guess[i]
        val left = remaining[c] ?: 0
        if (left > 0) {
            result[i] = LetterState.PRESENT
            remaining[c] = left - 1
        }
    }

    return result
}

/**
 * Best known state per letter across all guesses so far, for tinting the
 * on-screen keyboard. Priority: CORRECT > PRESENT > ABSENT.
 */
fun keyboardState(guesses: List<String>, secret: String): Map<Char, LetterState> {
    val best = HashMap<Char, LetterState>()
    for (guess in guesses) {
        val scored = scoreGuess(secret, guess)
        for (i in guess.indices) {
            val c = guess[i]
            val state = scored[i]
            val current = best[c]
            if (current == null || state.rank() > current.rank()) {
                best[c] = state
            }
        }
    }
    return best
}

private fun LetterState.rank(): Int = when (this) {
    LetterState.ABSENT -> 0
    LetterState.PRESENT -> 1
    LetterState.CORRECT -> 2
}

/**
 * Deterministic index into a pool of size [poolSize] for [date], so every
 * player sees the same daily word on the same calendar day. Uses the epoch
 * day hashed rather than taken modulo directly so consecutive days don't walk
 * the pool in a predictable +1 order.
 */
fun dailyIndex(date: LocalDate, poolSize: Int): Int {
    require(poolSize > 0) { "poolSize must be positive" }
    val epochDay = date.toEpochDay()
    // A simple, stable integer hash (variant of splitmix64) — deterministic
    // across platforms/JVMs, unlike Any.hashCode().
    var h = epochDay
    h = (h xor (h ushr 30)) * -0x61c8864680b583ebL
    h = (h xor (h ushr 27)) * -0x3b314601e57a13adL
    h = h xor (h ushr 31)
    return (h % poolSize).absoluteValue.toInt()
}

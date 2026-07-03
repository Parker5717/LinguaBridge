package com.linguabridge.app.domain.wordgame

import java.time.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordGameTest {

    @Test
    fun `all letters correct`() {
        val result = scoreGuess("apple", "apple")
        assertEquals(List(5) { LetterState.CORRECT }, result)
    }

    @Test
    fun `no letters in common`() {
        val result = scoreGuess("apple", "brunt")
        assertEquals(List(5) { LetterState.ABSENT }, result)
    }

    @Test
    fun `duplicate letter in guess but single in secret marks only one present`() {
        // secret "crane" = c(0) r(1) a(2) n(3) e(4) has a single 'a', at index 2.
        // guess "axaxx": index2 'a' matches exactly -> CORRECT, consuming the only 'a'.
        // index0 'a' then has no remaining 'a' in the pool -> ABSENT.
        val result = scoreGuess("crane", "axaxx")
        assertEquals(LetterState.ABSENT, result[0])
        assertEquals(LetterState.CORRECT, result[2])
    }

    @Test
    fun `duplicate letter handling secret apple guess eppee`() {
        // secret "apple": a=1, p=2, l=1, e=1
        // guess  "eppee": e,p,p,e,e
        // index0 'e' vs secret[0]='a' -> not exact. remaining after pass1 exact matches:
        // pass1: no exact position matches at all (apple vs eppee -> a/e,p/p... let's check index2)
        // secret: a p p l e
        // guess : e p p e e
        // index0: a vs e -> no
        // index1: p vs p -> CORRECT
        // index2: p vs p -> CORRECT
        // index3: l vs e -> no
        // index4: e vs e -> CORRECT
        // remaining secret letters after removing matched positions (1,2,4): a(1) l(1) left over from index0,3
        // guess leftover letters at index0 'e', index3 'e' — remaining pool has no 'e' (already consumed at index4? no,
        // index4 was an exact match consuming secret's 'e' via the CORRECT path, not added to remaining pool).
        // remaining pool (secret letters at non-matched indices 0,3) = {a:1, l:1}. Guess leftover chars are 'e','e' -> ABSENT, ABSENT.
        val result = scoreGuess("apple", "eppee")
        assertEquals(
            listOf(
                LetterState.ABSENT,
                LetterState.CORRECT,
                LetterState.CORRECT,
                LetterState.ABSENT,
                LetterState.CORRECT,
            ),
            result,
        )
    }

    @Test
    fun `guess has more of a letter than secret contains only marks that many present`() {
        // secret "route" has a single 'e', at index 4 (not touched by this guess).
        // guess "eexxx" repeats 'e' at indices 0 and 1; neither is an exact-position
        // match, so only one of them may be marked PRESENT (capped by the single
        // remaining 'e' in the secret) and the other must be ABSENT.
        val result = scoreGuess("route", "eexxx")
        val eStates = listOf(result[0], result[1])
        assertEquals(1, eStates.count { it == LetterState.PRESENT })
        assertEquals(1, eStates.count { it == LetterState.ABSENT })
    }

    @Test
    fun `mixed correct present and absent`() {
        // secret "crane", guess "cabin": c matches, a present (secret has 'a' at idx1),
        // b absent, i absent, n present (secret has 'n' at idx4)... but position 4 is 'n' vs guess 'n' -> exact!
        // secret: c r a n e
        // guess : c a b i n
        // idx0: c/c CORRECT
        // idx1: r/a -> no
        // idx2: a/b -> no
        // idx3: n/i -> no
        // idx4: e/n -> no
        // remaining secret (non-matched idx1..4) = {r:1, a:1, n:1, e:1}
        // guess leftover idx1 'a' -> present (a in remaining), idx2 'b' -> absent, idx3 'i' -> absent, idx4 'n' -> present
        val result = scoreGuess("crane", "cabin")
        assertEquals(
            listOf(
                LetterState.CORRECT,
                LetterState.PRESENT,
                LetterState.ABSENT,
                LetterState.ABSENT,
                LetterState.PRESENT,
            ),
            result,
        )
    }

    @Test
    fun `keyboard state takes best rank across guesses`() {
        // secret "crane" (a at index 2). First guess "axabb" -> 'a' ABSENT at idx0,
        // CORRECT at idx2 -> best across the guess is CORRECT.
        // Second guess of unrelated letters shouldn't downgrade it.
        val state = keyboardState(listOf("axabb", "zzzzz"), "crane")
        assertEquals(LetterState.CORRECT, state['a'])
        assertEquals(LetterState.ABSENT, state['b'])
        assertEquals(LetterState.ABSENT, state['z'])
    }

    @Test
    fun `keyboard state upgrades present to correct across guesses`() {
        // secret "crane". Guess1 "eppee": 'e' PRESENT (idx4 is actually correct in apple test, use crane here).
        // secret crane: c r a n e. guess1 "eeeee" -> idx4 'e' CORRECT, others ABSENT-ish but 'e' best is CORRECT already.
        // Better: guess1 puts 'n' as PRESENT (wrong position), guess2 puts 'n' as CORRECT.
        val guess1 = "nzzzz" // n at idx0 vs secret idx0 'c' -> PRESENT (n exists at idx3)
        val guess2 = "crane" // exact secret -> CORRECT everywhere
        val state = keyboardState(listOf(guess1, guess2), "crane")
        assertEquals(LetterState.CORRECT, state['n'])
    }

    @Test
    fun `keyboard state never downgrades correct back to present`() {
        val guess1 = "crane" // exact -> all CORRECT
        val guess2 = "nzzzz" // 'n' present elsewhere, should not overwrite CORRECT
        val state = keyboardState(listOf(guess1, guess2), "crane")
        assertEquals(LetterState.CORRECT, state['n'])
    }

    @Test
    fun `dailyIndex is deterministic for the same date`() {
        val date = LocalDate.of(2026, 7, 4)
        val a = dailyIndex(date, 500)
        val b = dailyIndex(date, 500)
        assertEquals(a, b)
    }

    @Test
    fun `dailyIndex is in range for various pool sizes`() {
        val date = LocalDate.of(2026, 1, 1)
        for (poolSize in listOf(1, 2, 5, 17, 500, 12345)) {
            val idx = dailyIndex(date, poolSize)
            assertTrue(idx >= 0 && idx < poolSize, "index $idx out of range for pool $poolSize")
        }
    }

    @Test
    fun `dailyIndex varies across different dates`() {
        val poolSize = 1000
        val indices = (0 until 30).map { offset ->
            dailyIndex(LocalDate.of(2026, 1, 1).plusDays(offset.toLong()), poolSize)
        }
        // Not every index needs to be unique, but they shouldn't all collapse to one value.
        assertTrue(indices.toSet().size > 1)
    }

    @Test
    fun `scoreGuess requires equal length`() {
        var threw = false
        try {
            scoreGuess("apple", "ab")
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}

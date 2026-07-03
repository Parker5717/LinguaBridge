package com.linguabridge.app.domain.dictation

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordDiffTest {

    @Test
    fun `identical sentences are perfect`() {
        val diff = diffWords("The cat sat on the mat", "The cat sat on the mat")
        assertTrue(diff.all { it.kind == DiffToken.Kind.CORRECT })
        assertTrue(isPerfect(diff))
    }

    @Test
    fun `case and punctuation differences are ignored`() {
        val diff = diffWords("The cat sat on the mat.", "the CAT sat, on the mat")
        assertTrue(isPerfect(diff))
        assertEquals(6, diff.size)
    }

    @Test
    fun `one wrong word is flagged WRONG at its position`() {
        val diff = diffWords("The cat sat on the mat", "The dog sat on the mat")
        assertEquals(
            listOf(
                DiffToken.Kind.CORRECT,
                DiffToken.Kind.WRONG,
                DiffToken.Kind.CORRECT,
                DiffToken.Kind.CORRECT,
                DiffToken.Kind.CORRECT,
                DiffToken.Kind.CORRECT,
            ),
            diff.map { it.kind },
        )
        assertEquals("cat", diff[1].text)
        assertFalse(isPerfect(diff))
    }

    @Test
    fun `missing word is flagged MISSING with expected spelling`() {
        val diff = diffWords("The cat sat on the mat", "The cat on the mat")
        val missing = diff.filter { it.kind == DiffToken.Kind.MISSING }
        assertEquals(1, missing.size)
        assertEquals("sat", missing.first().text)
        assertFalse(isPerfect(diff))
    }

    @Test
    fun `extra word is flagged EXTRA`() {
        val diff = diffWords("The cat sat on the mat", "The cat sat right on the mat")
        val extra = diff.filter { it.kind == DiffToken.Kind.EXTRA }
        assertEquals(1, extra.size)
        assertEquals("right", extra.first().text)
        assertFalse(isPerfect(diff))
    }

    @Test
    fun `combined wrong missing and extra words`() {
        val diff = diffWords("The quick brown fox jumps", "The quick red fox leaps high")
        assertFalse(isPerfect(diff))
        val kinds = diff.map { it.kind }
        assertTrue(DiffToken.Kind.WRONG in kinds)
        assertTrue(DiffToken.Kind.EXTRA in kinds)
    }

    @Test
    fun `empty typed input marks every expected word as MISSING`() {
        val diff = diffWords("The cat sat", "")
        assertEquals(3, diff.size)
        assertTrue(diff.all { it.kind == DiffToken.Kind.MISSING })
        assertFalse(isPerfect(diff))
    }

    @Test
    fun `isPerfect is false when there are extra words even if all expected words matched`() {
        val diff = diffWords("The cat sat", "The cat sat right now")
        assertTrue(diff.filter { it.kind == DiffToken.Kind.CORRECT }.size == 3)
        assertTrue(diff.any { it.kind == DiffToken.Kind.EXTRA })
        assertFalse(isPerfect(diff))
    }
}

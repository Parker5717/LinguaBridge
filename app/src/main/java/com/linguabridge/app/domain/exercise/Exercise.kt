package com.linguabridge.app.domain.exercise

import com.linguabridge.app.data.ReviewCard

/** How a card is presented to the learner. Selection depends on how well the
 *  card is known (phase/reps), so each word climbs: intro → recognize →
 *  recall → produce in context. */
enum class ExerciseKind {
    /** First encounter: show everything, no answer required. */
    INTRO,

    /** Multiple choice: front (e.g. EN word) → pick the translation. */
    CHOICE_FORWARD,

    /** Multiple choice: translation → pick the front (e.g. EN word). */
    CHOICE_REVERSE,

    /** Type the answer given the translation as prompt. */
    TYPE_ANSWER,

    /** Fill the blanked-out target word inside its example sentence. */
    CLOZE,

    /** Translate the whole example sentence from Russian into English. */
    SENTENCE_TRANSLATE,
}

data class Exercise(
    val kind: ExerciseKind,
    val card: ReviewCard,
    /** What the learner sees as the task. */
    val prompt: String,
    /** Secondary line under the prompt (IPA, pos, RU hint...), optional. */
    val promptHint: String?,
    /** Options for CHOICE_*; empty otherwise. */
    val options: List<String>,
    /** The accepted answer (option text or expected typed word). */
    val answer: String,
    /** Full card back, shown in the feedback banner. */
    val fullBack: String,
    /** Example sentence for the feedback banner (already blanked for CLOZE). */
    val example: String?,
)

enum class AnswerVerdict { CORRECT, TYPO, WRONG }

/** Duolingo-style check: exact (case/space-insensitive) or one edit away. */
fun checkTypedAnswer(expected: String, given: String): AnswerVerdict {
    val e = expected.trim().lowercase()
    val g = given.trim().lowercase()
    if (e == g) return AnswerVerdict.CORRECT
    if (e.isEmpty() || g.isEmpty()) return AnswerVerdict.WRONG
    return if (levenshtein(e, g) <= 1) AnswerVerdict.TYPO else AnswerVerdict.WRONG
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    val prev = IntArray(b.length + 1) { it }
    val cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        cur.copyInto(prev)
    }
    return prev[b.length]
}

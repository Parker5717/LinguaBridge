package com.linguabridge.app.domain.exercise

import com.linguabridge.app.data.ReviewCard
import com.linguabridge.app.domain.srs.CardPhase
import com.linguabridge.app.domain.srs.toSrsState

/**
 * Picks and builds the exercise for a card.
 *
 * Ladder per card: NEW → INTRO; learning step 0 → recognize (choice forward);
 * later learning steps → recall (choice reverse); young review cards → typing;
 * mature review cards → cloze in the example sentence (falls back to typing
 * when the example does not contain the headword).
 */
class ExerciseFactory(private val random: kotlin.random.Random = kotlin.random.Random.Default) {

    /**
     * @param distractorBacks translation lines from OTHER cards of the same
     *   deck, used as wrong options. Needs at least 3 entries for choices.
     */
    fun create(item: ReviewCard, distractorBacks: List<String>): Exercise {
        val srs = item.state.toSrsState()
        val kind = when (srs.phase) {
            CardPhase.NEW -> ExerciseKind.INTRO
            CardPhase.LEARNING, CardPhase.RELEARNING ->
                if (srs.learningStepIndex == 0) ExerciseKind.CHOICE_FORWARD
                else ExerciseKind.CHOICE_REVERSE
            CardPhase.REVIEW -> when {
                srs.reps >= 5 && item.card.exampleRu != null && item.card.example != null ->
                    ExerciseKind.SENTENCE_TRANSLATE
                srs.reps < 3 -> ExerciseKind.TYPE_ANSWER
                else -> ExerciseKind.CLOZE
            }
        }
        return build(kind, item, distractorBacks)
    }

    fun build(kind: ExerciseKind, item: ReviewCard, distractorBacks: List<String>): Exercise {
        val card = item.card
        val translation = card.back.lineSequence().first().trim()

        return when (kind) {
            ExerciseKind.INTRO -> Exercise(
                kind = kind,
                card = item,
                prompt = card.front,
                promptHint = card.hint,
                options = emptyList(),
                answer = "",
                fullBack = card.back,
                example = card.example,
            )

            ExerciseKind.CHOICE_FORWARD -> Exercise(
                kind = kind,
                card = item,
                prompt = card.front,
                promptHint = card.hint,
                options = shuffledOptions(translation, distractorBacks),
                answer = translation,
                fullBack = card.back,
                example = card.example,
            )

            ExerciseKind.CHOICE_REVERSE -> Exercise(
                kind = kind,
                card = item,
                prompt = translation,
                promptHint = null,
                // Reverse choice needs fronts as options; distractorBacks must
                // then contain fronts (the repository provides the right list).
                options = shuffledOptions(card.front, distractorBacks),
                answer = card.front,
                fullBack = card.back,
                example = card.example,
            )

            ExerciseKind.TYPE_ANSWER -> Exercise(
                kind = kind,
                card = item,
                prompt = typePrompt(card, translation),
                promptHint = if (card.cardType == "def_en") card.hint else null,
                options = emptyList(),
                answer = typedTarget(card),
                fullBack = card.back,
                example = card.example,
                answerIsPinyin = card.cardType == "zh_en",
            )

            ExerciseKind.SENTENCE_TRANSLATE -> Exercise(
                kind = kind,
                card = item,
                prompt = requireNotNull(card.exampleRu),
                promptHint = null,
                options = emptyList(),
                answer = requireNotNull(card.example),
                fullBack = card.back,
                example = card.example,
            )

            ExerciseKind.CLOZE -> {
                // Chinese cards have no English sentence to blank; the cloze
                // target for definition cards is the headword on the back.
                val cloze =
                    if (card.cardType == "zh_en") null
                    else buildCloze(typedTarget(card), card.example)
                if (cloze == null) {
                    build(ExerciseKind.TYPE_ANSWER, item, distractorBacks)
                } else {
                    Exercise(
                        kind = kind,
                        card = item,
                        prompt = cloze.blankedSentence,
                        promptHint = translation,
                        options = emptyList(),
                        answer = cloze.blankedWord,
                        fullBack = card.back,
                        example = cloze.blankedSentence,
                    )
                }
            }
        }
    }

    private fun shuffledOptions(correct: String, distractors: List<String>): List<String> {
        val wrong = distractors
            .map { it.lineSequence().first().trim() }
            .filter { !it.equals(correct, ignoreCase = true) }
            .distinct()
            .shuffled(random)
            .take(3)
        return (wrong + correct).shuffled(random)
    }

    /** What the learner actually types, per card type: the English word for
     *  en_ru/gram_term/stem_en, the headword (not the definition!) for def_en,
     *  and the pinyin line (not the hanzi) for zh_en. */
    private fun typedTarget(card: com.linguabridge.app.data.db.content.CardEntity): String =
        when (card.cardType) {
            "def_en" -> card.back.lineSequence().first().trim()
            "zh_en" -> card.front.lineSequence().drop(1).firstOrNull()?.trim()
                ?: card.front.trim()
            else -> card.front.trim()
        }

    /** The typing task's prompt: the definition for def_en cards, the first
     *  back line (RU translation / EN meaning) for everything else. */
    private fun typePrompt(
        card: com.linguabridge.app.data.db.content.CardEntity,
        translation: String,
    ): String = when (card.cardType) {
        "def_en" -> card.front
        else -> translation
    }

    private data class Cloze(val blankedSentence: String, val blankedWord: String)

    /** Finds the headword (or a simple inflected form) inside the example and
     *  replaces it with a blank. Returns null when no token matches. */
    private fun buildCloze(headword: String, example: String?): Cloze? {
        if (example.isNullOrBlank()) return null
        val head = headword.trim().lowercase()
        if (head.contains(' ')) {
            // Phrases: blank the whole phrase if it appears verbatim.
            val idx = example.lowercase().indexOf(head)
            if (idx < 0) return null
            val original = example.substring(idx, idx + head.length)
            return Cloze(example.replaceRange(idx, idx + head.length, "_____"), original)
        }
        val tokens = Regex("[A-Za-z']+").findAll(example)
        for (m in tokens) {
            val t = m.value.lowercase()
            if (t == head || inflections(head).contains(t)) {
                return Cloze(
                    example.replaceRange(m.range.first, m.range.last + 1, "_____"),
                    m.value,
                )
            }
        }
        return null
    }

    private fun inflections(head: String): Set<String> = buildSet {
        add(head + "s")
        add(head + "es")
        add(head + "ed")
        add(head + "d")
        add(head + "ing")
        if (head.endsWith("e")) add(head.dropLast(1) + "ing")
        if (head.endsWith("y")) {
            add(head.dropLast(1) + "ies")
            add(head.dropLast(1) + "ied")
        }
    }
}

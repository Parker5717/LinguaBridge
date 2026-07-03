package com.linguabridge.app.data

import com.linguabridge.app.data.db.content.ContentDatabase
import com.linguabridge.app.data.db.content.DialogueEntity
import com.linguabridge.app.data.db.content.DialogueLineEntity
import com.linguabridge.app.data.db.content.ReadingTextEntity
import com.linguabridge.app.data.db.content.VocabEntity
import com.linguabridge.app.data.db.user.ReadPositionEntity
import com.linguabridge.app.data.db.user.UserDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Backs the graded reader, dialogues and tap-to-translate popup: bundled
 * reading/dialogue/vocab content plus the user's read position and the SRS
 * "add to deck" shortcut.
 */
class LibraryRepository(
    private val contentDb: ContentDatabase,
    private val userDb: UserDatabase,
) {

    fun texts(level: String): Flow<List<ReadingTextEntity>> = contentDb.readingTextDao().byLevel(level)

    suspend fun text(id: String): ReadingTextEntity? = contentDb.readingTextDao().byId(id)

    fun dialogues(): Flow<List<DialogueEntity>> = contentDb.dialogueDao().all()

    suspend fun dialogueLines(id: String): List<DialogueLineEntity> = contentDb.dialogueDao().linesFor(id)

    /**
     * Candidate pool for the word game: 5-letter vocab headwords, lowercased
     * and restricted to plain ASCII a-z (the DB query already strips spaces,
     * hyphens and apostrophes but can't filter accented loanwords, so that
     * last check happens here).
     */
    suspend fun wordGamePool(): List<VocabEntity> =
        contentDb.vocabDao().fiveLetterWords()
            .map { it.copy(headword = it.headword.lowercase()) }
            .filter { entry -> entry.headword.all { it in 'a'..'z' } }

    /**
     * Normalizes [raw] (lowercase, strip leading/trailing non-letters) and tries an
     * exact match first, then a handful of simple suffix-stripped candidates
     * (plural/past-tense/gerund forms). Returns the first hit, or null.
     */
    suspend fun lookupWord(raw: String): VocabEntity? {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return null

        contentDb.vocabDao().lookup(normalized)?.let { return it }

        for (candidate in suffixCandidates(normalized)) {
            contentDb.vocabDao().lookup(candidate)?.let { return it }
        }
        return null
    }

    /**
     * Bumps a vocab entry's SRS card to the front of the new-card queue by
     * zeroing its added_at. No-op (returns false) if there is no card_state
     * row yet or the card has already left the "new" state.
     */
    suspend fun addToDeckFront(vocabId: String): Boolean {
        val cardId = "card:en_ru:$vocabId"
        val state = userDb.cardStateDao().byId(cardId) ?: return false
        if (state.state != "new") return false
        userDb.cardStateDao().upsert(state.copy(addedAt = 0))
        return true
    }

    suspend fun readPosition(textId: String): Int = userDb.readPositionDao().get(textId)?.scrollItem ?: 0

    suspend fun saveReadPosition(textId: String, item: Int) {
        userDb.readPositionDao().upsert(
            ReadPositionEntity(
                textId = textId,
                scrollItem = item,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun normalize(raw: String): String =
        raw.lowercase().trim { !(it.isLetter() || it == '\'' || it == '-') }
            .trim('\'', '-')

    private fun suffixCandidates(normalized: String): List<String> {
        val candidates = mutableListOf<String>()
        when {
            normalized.endsWith("ies") && normalized.length > 3 ->
                candidates += normalized.dropLast(3) + "y"
            normalized.endsWith("es") && normalized.length > 2 ->
                candidates += normalized.dropLast(2)
            normalized.endsWith("s") && normalized.length > 1 ->
                candidates += normalized.dropLast(1)
        }
        if (normalized.endsWith("ed") && normalized.length > 2) {
            candidates += normalized.dropLast(2)
            candidates += normalized.dropLast(1)
        }
        if (normalized.endsWith("ing") && normalized.length > 3) {
            candidates += normalized.dropLast(3)
            candidates += normalized.dropLast(3) + "e"
        }
        return candidates
    }
}

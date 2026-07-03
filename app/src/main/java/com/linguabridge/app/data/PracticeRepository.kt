package com.linguabridge.app.data

import com.linguabridge.app.data.db.content.ContentDatabase
import com.linguabridge.app.data.db.content.ListeningPassageEntity
import com.linguabridge.app.data.db.content.QuizQuestionEntity
import com.linguabridge.app.data.db.user.DailyStatEntity
import com.linguabridge.app.data.db.user.QuizResultEntity
import com.linguabridge.app.data.db.user.UserDatabase
import kotlinx.serialization.json.Json

/** A listening MCQ question with its options already parsed. */
data class ListeningQuestion(
    val question: QuizQuestionEntity,
    val options: List<String>,
)

/**
 * Backs the two Phase 4 practice modes: TTS dictation (vocab example
 * sentences) and listening comprehension (bundled passages + quiz).
 */
class PracticeRepository(
    private val contentDb: ContentDatabase,
    private val userDb: UserDatabase,
) {

    /** Random example sentences for dictation. [level] is a vocab level: "A2B1" or "B1B2". */
    suspend fun dictationSentences(level: String, n: Int): List<String> =
        contentDb.vocabDao().randomByLevel(level, n)
            .map { it.example1 }
            .filter { it.isNotBlank() }

    /** Listening passages at [level] ("A2" | "B1" | "B2"), in id order. */
    suspend fun passages(level: String): List<ListeningPassageEntity> =
        contentDb.quizDao().passagesByLevel(level).sortedBy { it.id }

    /** MCQ questions tied to [passageId], with options parsed from JSON. */
    suspend fun questionsFor(passageId: String): List<ListeningQuestion> =
        contentDb.quizDao().byCategory("listening")
            .filter { it.passageId == passageId }
            .map { q ->
                val options = q.optionsJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
                ListeningQuestion(q, options)
            }

    /** Bumps today's dictation counter by one completed sentence. */
    suspend fun recordDictation(now: Long) {
        val key = SrsRepository.dateKey(now)
        val current = userDb.dailyStatDao().get(key) ?: DailyStatEntity(key, 0, 0, 0, 0)
        userDb.dailyStatDao().upsert(current.copy(dictationsDone = current.dictationsDone + 1))
    }

    /** Records a finished quiz/listening session and bumps today's quiz counter. */
    suspend fun recordQuiz(category: String, score: Int, total: Int, now: Long) {
        userDb.quizResultDao().insert(
            QuizResultEntity(category = category, ts = now, score = score, total = total)
        )
        val key = SrsRepository.dateKey(now)
        val current = userDb.dailyStatDao().get(key) ?: DailyStatEntity(key, 0, 0, 0, 0)
        userDb.dailyStatDao().upsert(current.copy(quizzesDone = current.quizzesDone + 1))
    }
}

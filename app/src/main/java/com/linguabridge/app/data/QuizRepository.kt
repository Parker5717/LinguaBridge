package com.linguabridge.app.data

import com.linguabridge.app.data.db.content.ContentDatabase
import com.linguabridge.app.data.db.content.QuizQuestionEntity
import com.linguabridge.app.data.db.user.PlacementResultEntity
import com.linguabridge.app.data.db.user.UserDatabase
import kotlinx.serialization.json.Json

/** A quiz question with its options already parsed (mcq) or empty (typing). */
data class QuizItem(
    val question: QuizQuestionEntity,
    val options: List<String>,
)

/** Quiz categories exposed on the quiz list screen ("listening" has its own dedicated flow). */
val QUIZ_CATEGORIES = listOf("vocab", "engrammar", "gramterm", "stem", "hsk", "csca")

private val PLACEMENT_LEVEL_ORDER = listOf("A2", "B1", "B2")

/**
 * Backs Phase 5: category quizzes, the level placement test, and CSCA
 * exam-prep mode. Delegates session bookkeeping to [PracticeRepository].
 */
class QuizRepository(
    private val contentDb: ContentDatabase,
    private val userDb: UserDatabase,
    private val practiceRepository: PracticeRepository,
) {

    /** Questions for [category], shuffled; mcq options are shuffled too. */
    suspend fun questions(category: String): List<QuizItem> =
        contentDb.quizDao().byCategory(category)
            .shuffled()
            .map { toQuizItem(it, shuffleOptions = true) }

    /** Placement questions ordered A2 → B1 → B2 (shuffled within each level). */
    suspend fun placementQuestions(): List<QuizItem> =
        contentDb.quizDao().byCategory("placement")
            .groupBy { it.level }
            .let { byLevel ->
                PLACEMENT_LEVEL_ORDER.flatMap { level -> byLevel[level].orEmpty().shuffled() }
            }
            .map { toQuizItem(it, shuffleOptions = true) }

    private fun toQuizItem(q: QuizQuestionEntity, shuffleOptions: Boolean): QuizItem {
        val options = q.optionsJson?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
        return QuizItem(q, if (shuffleOptions) options.shuffled() else options)
    }

    /** Records a finished quiz/placement session and bumps today's quiz counter. */
    suspend fun finishQuiz(category: String, score: Int, total: Int, now: Long) {
        practiceRepository.recordQuiz(category, score, total, now)
    }

    suspend fun savePlacement(level: String, score: Int, total: Int, now: Long) {
        userDb.placementResultDao().insert(
            PlacementResultEntity(ts = now, estimatedLevel = level, score = score, total = total)
        )
    }

    suspend fun lastPlacement(): PlacementResultEntity? = userDb.placementResultDao().latest()
}

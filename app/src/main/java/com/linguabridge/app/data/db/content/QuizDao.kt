package com.linguabridge.app.data.db.content

import androidx.room.Dao
import androidx.room.Query

@Dao
interface QuizDao {

    @Query("SELECT * FROM quiz_question WHERE category = :category")
    suspend fun byCategory(category: String): List<QuizQuestionEntity>

    @Query("SELECT * FROM quiz_question WHERE category = :category AND level = :level")
    suspend fun byCategoryAndLevel(category: String, level: String): List<QuizQuestionEntity>

    @Query("SELECT * FROM listening_passage WHERE id = :id")
    suspend fun passageById(id: String): ListeningPassageEntity?

    @Query("SELECT * FROM listening_passage WHERE level = :level")
    suspend fun passagesByLevel(level: String): List<ListeningPassageEntity>

    @Query("SELECT COUNT(*) FROM quiz_question WHERE category = :category")
    suspend fun countByCategory(category: String): Int
}

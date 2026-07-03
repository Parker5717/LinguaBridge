package com.linguabridge.app.data.db.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface QuizResultDao {

    @Insert
    suspend fun insert(r: QuizResultEntity)

    @Query("SELECT * FROM quiz_result")
    suspend fun all(): List<QuizResultEntity>
}

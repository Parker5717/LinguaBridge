package com.linguabridge.app.data.db.user

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quiz_result")
data class QuizResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val category: String,
    val ts: Long,
    val score: Int,
    val total: Int
)

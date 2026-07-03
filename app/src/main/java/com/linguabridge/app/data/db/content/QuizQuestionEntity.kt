package com.linguabridge.app.data.db.content

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quiz_question",
    indices = [Index(value = ["category"])]
)
data class QuizQuestionEntity(
    @PrimaryKey
    val id: String,
    val category: String,
    val level: String,
    val type: String,
    val prompt: String,
    @ColumnInfo(name = "options_json")
    val optionsJson: String?,
    val answer: String,
    val explanation: String,
    @ColumnInfo(name = "passage_id")
    val passageId: String?
)

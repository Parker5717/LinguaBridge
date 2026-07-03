package com.linguabridge.app.data.db.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_stat")
data class DailyStatEntity(
    /** ISO yyyy-MM-dd. */
    @PrimaryKey
    val date: String,
    @ColumnInfo(name = "reviews_done")
    val reviewsDone: Int,
    @ColumnInfo(name = "new_learned")
    val newLearned: Int,
    @ColumnInfo(name = "dictations_done")
    val dictationsDone: Int,
    @ColumnInfo(name = "quizzes_done")
    val quizzesDone: Int
)

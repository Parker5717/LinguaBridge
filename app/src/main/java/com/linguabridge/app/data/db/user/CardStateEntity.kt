package com.linguabridge.app.data.db.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_state")
data class CardStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "card_id")
    val cardId: String,
    /** One of "new", "learning", "review", "relearning". */
    val state: String,
    @ColumnInfo(name = "ease_factor")
    val easeFactor: Double,
    @ColumnInfo(name = "interval_days")
    val intervalDays: Double,
    val reps: Int,
    val lapses: Int,
    @ColumnInfo(name = "learning_step_index")
    val learningStepIndex: Int,
    @ColumnInfo(name = "due_at")
    val dueAt: Long,
    @ColumnInfo(name = "last_reviewed_at")
    val lastReviewedAt: Long?,
    @ColumnInfo(name = "added_at")
    val addedAt: Long
)

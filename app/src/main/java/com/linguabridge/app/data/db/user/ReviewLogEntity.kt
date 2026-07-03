package com.linguabridge.app.data.db.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_log",
    indices = [Index(value = ["card_id"])]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "card_id")
    val cardId: String,
    val ts: Long,
    val rating: Int,
    @ColumnInfo(name = "interval_after_days")
    val intervalAfterDays: Double
)

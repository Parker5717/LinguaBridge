package com.linguabridge.app.data.db.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "read_position")
data class ReadPositionEntity(
    @PrimaryKey
    @ColumnInfo(name = "text_id")
    val textId: String,
    @ColumnInfo(name = "scroll_item")
    val scrollItem: Int,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

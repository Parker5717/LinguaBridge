package com.linguabridge.app.data.db.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "placement_result")
data class PlacementResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ts: Long,
    @ColumnInfo(name = "estimated_level")
    val estimatedLevel: String,
    val score: Int,
    val total: Int
)

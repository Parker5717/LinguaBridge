package com.linguabridge.app.data.db.content

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "listening_passage")
data class ListeningPassageEntity(
    @PrimaryKey
    val id: String,
    val level: String,
    val text: String
)

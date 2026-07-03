package com.linguabridge.app.data.db.content

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_text")
data class ReadingTextEntity(
    @PrimaryKey
    val id: String,
    val level: String,
    val title: String,
    val topic: String,
    val body: String
)

package com.linguabridge.app.data.db.content

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dialogue")
data class DialogueEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val scenario: String,
    val level: String
)

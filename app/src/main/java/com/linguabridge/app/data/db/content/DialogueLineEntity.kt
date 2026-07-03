package com.linguabridge.app.data.db.content

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dialogue_line",
    indices = [Index(value = ["dialogue_id"])]
)
data class DialogueLineEntity(
    @PrimaryKey
    val id: Int,
    @ColumnInfo(name = "dialogue_id")
    val dialogueId: String,
    val ord: Int,
    val speaker: String,
    val lang: String,
    val text: String,
    val pinyin: String?,
    @ColumnInfo(name = "ru_note")
    val ruNote: String?
)

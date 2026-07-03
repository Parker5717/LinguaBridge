package com.linguabridge.app.data.db.content

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "card",
    indices = [Index(value = ["card_type"])]
)
data class CardEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "card_type")
    val cardType: String,
    val front: String,
    val back: String,
    val hint: String?,
    val example: String?,
    /** Russian translation of [example], used by sentence-translation drills. */
    @ColumnInfo(name = "example_ru")
    val exampleRu: String?,
    @ColumnInfo(name = "tts_lang")
    val ttsLang: String?,
    @ColumnInfo(name = "tts_text")
    val ttsText: String?,
    @ColumnInfo(name = "source_ref")
    val sourceRef: String
)

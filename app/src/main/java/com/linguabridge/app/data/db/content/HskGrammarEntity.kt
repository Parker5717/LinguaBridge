package com.linguabridge.app.data.db.content

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hsk_grammar")
data class HskGrammarEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val pattern: String,
    @ColumnInfo(name = "en_explanation")
    val enExplanation: String,
    @ColumnInfo(name = "example_zh")
    val exampleZh: String,
    @ColumnInfo(name = "example_pinyin")
    val examplePinyin: String,
    @ColumnInfo(name = "example_en")
    val exampleEn: String,
    @ColumnInfo(name = "ru_note")
    val ruNote: String
)

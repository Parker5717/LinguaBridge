package com.linguabridge.app.data.db.content

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "grammar_term")
data class GrammarTermEntity(
    @PrimaryKey
    val id: String,
    val term: String,
    @ColumnInfo(name = "en_explanation")
    val enExplanation: String,
    @ColumnInfo(name = "ru_translation")
    val ruTranslation: String,
    @ColumnInfo(name = "zh_example")
    val zhExample: String,
    @ColumnInfo(name = "zh_example_pinyin")
    val zhExamplePinyin: String,
    @ColumnInfo(name = "zh_example_en")
    val zhExampleEn: String
)

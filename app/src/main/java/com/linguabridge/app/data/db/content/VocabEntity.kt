package com.linguabridge.app.data.db.content

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vocab",
    indices = [Index(value = ["headword"])]
)
data class VocabEntity(
    @PrimaryKey
    val id: String,
    val level: String,
    val headword: String,
    val ipa: String,
    val pos: String,
    @ColumnInfo(name = "ru_translation")
    val ruTranslation: String,
    @ColumnInfo(name = "en_definition")
    val enDefinition: String,
    val example1: String,
    /** Natural Russian translation of example1; null until content provides it. */
    @ColumnInfo(name = "example1_ru")
    val example1Ru: String?,
    val example2: String,
    val topic: String
)

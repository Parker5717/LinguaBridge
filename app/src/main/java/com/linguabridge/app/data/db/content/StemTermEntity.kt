package com.linguabridge.app.data.db.content

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stem_term")
data class StemTermEntity(
    @PrimaryKey
    val id: String,
    val domain: String,
    val term: String,
    @ColumnInfo(name = "ru_translation")
    val ruTranslation: String,
    val symbol: String?,
    @ColumnInfo(name = "en_definition")
    val enDefinition: String,
    @ColumnInfo(name = "csca_example")
    val cscaExample: String
)

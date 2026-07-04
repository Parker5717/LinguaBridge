package com.linguabridge.app.data.db.content

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hsk_word")
data class HskWordEntity(
    @PrimaryKey
    val id: String,
    val hanzi: String,
    val pinyin: String,
    @ColumnInfo(name = "en_meaning")
    val enMeaning: String,
    @ColumnInfo(name = "ru_meaning")
    val ruMeaning: String,
    @ColumnInfo(name = "hsk_level")
    val hskLevel: Int,
    @ColumnInfo(name = "example_zh")
    val exampleZh: String,
    @ColumnInfo(name = "example_pinyin")
    val examplePinyin: String,
    @ColumnInfo(name = "example_en")
    val exampleEn: String,
    /** Russian memory association for the characters (visual/etymological). */
    @ColumnInfo(name = "mnemonic_ru")
    val mnemonicRu: String?
)

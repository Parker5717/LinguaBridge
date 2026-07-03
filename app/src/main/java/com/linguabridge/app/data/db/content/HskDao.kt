package com.linguabridge.app.data.db.content

import androidx.room.Dao
import androidx.room.Query

@Dao
interface HskDao {

    @Query("SELECT * FROM hsk_word")
    suspend fun words(): List<HskWordEntity>

    @Query("SELECT * FROM hsk_grammar")
    suspend fun grammarPoints(): List<HskGrammarEntity>

    @Query("SELECT COUNT(*) FROM hsk_word")
    suspend fun wordCount(): Int

    @Query(
        "SELECT * FROM hsk_word WHERE hanzi LIKE '%' || :q || '%' OR pinyin LIKE '%' || :q || '%' " +
            "OR en_meaning LIKE '%' || :q || '%' OR ru_meaning LIKE '%' || :q || '%' LIMIT 25"
    )
    suspend fun search(q: String): List<HskWordEntity>
}

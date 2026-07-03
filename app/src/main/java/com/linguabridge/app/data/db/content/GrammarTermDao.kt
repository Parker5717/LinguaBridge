package com.linguabridge.app.data.db.content

import androidx.room.Dao
import androidx.room.Query

@Dao
interface GrammarTermDao {

    @Query("SELECT * FROM grammar_term")
    suspend fun all(): List<GrammarTermEntity>

    @Query("SELECT COUNT(*) FROM grammar_term")
    suspend fun count(): Int

    @Query(
        "SELECT * FROM grammar_term WHERE term LIKE '%' || :q || '%' OR ru_translation LIKE '%' || :q || '%' LIMIT 25"
    )
    suspend fun search(q: String): List<GrammarTermEntity>
}

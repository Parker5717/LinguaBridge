package com.linguabridge.app.data.db.content

import androidx.room.Dao
import androidx.room.Query

@Dao
interface VocabDao {

    @Query("SELECT * FROM vocab WHERE lower(headword) = :normalized LIMIT 1")
    suspend fun lookup(normalized: String): VocabEntity?

    @Query("SELECT * FROM vocab WHERE id = :id")
    suspend fun byId(id: String): VocabEntity?

    @Query("SELECT COUNT(*) FROM vocab")
    suspend fun count(): Int

    @Query("SELECT * FROM vocab WHERE level = :level ORDER BY RANDOM() LIMIT :n")
    suspend fun randomByLevel(level: String, n: Int): List<VocabEntity>

    @Query(
        "SELECT * FROM vocab WHERE headword LIKE '%' || :q || '%' OR ru_translation LIKE '%' || :q || '%' " +
            "ORDER BY LENGTH(headword) LIMIT 25"
    )
    suspend fun search(q: String): List<VocabEntity>

    // Candidate pool for the word game. SQLite LENGTH/LIKE are enough to weed out
    // spaces, hyphens and apostrophes; non-ASCII letters (accented loanwords etc.)
    // are filtered in Kotlin afterwards since SQLite has no [a-z] character class.
    @Query(
        "SELECT * FROM vocab WHERE LENGTH(headword) = 5 AND headword NOT LIKE '% %' " +
            "AND headword NOT LIKE '%-%' AND headword NOT LIKE \"%'%\""
    )
    suspend fun fiveLetterWords(): List<VocabEntity>
}

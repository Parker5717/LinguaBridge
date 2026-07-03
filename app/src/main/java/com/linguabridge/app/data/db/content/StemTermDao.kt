package com.linguabridge.app.data.db.content

import androidx.room.Dao
import androidx.room.Query

@Dao
interface StemTermDao {

    @Query("SELECT * FROM stem_term WHERE domain = :domain")
    suspend fun byDomain(domain: String): List<StemTermEntity>

    @Query("SELECT COUNT(*) FROM stem_term")
    suspend fun count(): Int

    @Query(
        "SELECT * FROM stem_term WHERE term LIKE '%' || :q || '%' OR ru_translation LIKE '%' || :q || '%' LIMIT 25"
    )
    suspend fun search(q: String): List<StemTermEntity>
}

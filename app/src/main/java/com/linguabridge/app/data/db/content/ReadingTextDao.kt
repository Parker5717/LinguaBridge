package com.linguabridge.app.data.db.content

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingTextDao {

    @Query("SELECT * FROM reading_text WHERE level = :level")
    fun byLevel(level: String): Flow<List<ReadingTextEntity>>

    @Query("SELECT * FROM reading_text WHERE id = :id")
    suspend fun byId(id: String): ReadingTextEntity?

    @Query("SELECT COUNT(*) FROM reading_text")
    suspend fun count(): Int
}

package com.linguabridge.app.data.db.content

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DialogueDao {

    @Query("SELECT * FROM dialogue")
    fun all(): Flow<List<DialogueEntity>>

    @Query("SELECT * FROM dialogue_line WHERE dialogue_id = :dialogueId ORDER BY ord")
    suspend fun linesFor(dialogueId: String): List<DialogueLineEntity>

    @Query("SELECT COUNT(*) FROM dialogue")
    suspend fun count(): Int
}

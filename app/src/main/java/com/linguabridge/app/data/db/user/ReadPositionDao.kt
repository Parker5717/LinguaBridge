package com.linguabridge.app.data.db.user

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ReadPositionDao {

    @Upsert
    suspend fun upsert(p: ReadPositionEntity)

    @Query("SELECT * FROM read_position WHERE text_id = :textId")
    suspend fun get(textId: String): ReadPositionEntity?
}

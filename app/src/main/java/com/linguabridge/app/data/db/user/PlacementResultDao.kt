package com.linguabridge.app.data.db.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PlacementResultDao {

    @Insert
    suspend fun insert(r: PlacementResultEntity)

    @Query("SELECT * FROM placement_result ORDER BY ts DESC LIMIT 1")
    suspend fun latest(): PlacementResultEntity?
}

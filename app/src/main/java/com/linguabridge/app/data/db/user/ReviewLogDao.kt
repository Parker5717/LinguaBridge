package com.linguabridge.app.data.db.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReviewLogDao {

    @Insert
    suspend fun insert(log: ReviewLogEntity)

    @Query("SELECT * FROM review_log WHERE ts >= :ts")
    suspend fun since(ts: Long): List<ReviewLogEntity>

    @Query("SELECT COUNT(*) FROM review_log WHERE ts >= :fromTs")
    suspend fun countSince(fromTs: Long): Int

    @Query("SELECT COUNT(*) FROM review_log WHERE ts >= :fromTs AND rating > 0")
    suspend fun countSuccessSince(fromTs: Long): Int
}

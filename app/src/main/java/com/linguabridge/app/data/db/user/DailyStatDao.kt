package com.linguabridge.app.data.db.user

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatDao {

    @Query("SELECT * FROM daily_stat WHERE date = :date")
    suspend fun get(date: String): DailyStatEntity?

    @Upsert
    suspend fun upsert(stat: DailyStatEntity)

    @Query("SELECT * FROM daily_stat WHERE date = :date")
    fun observe(date: String): Flow<DailyStatEntity?>

    @Query("SELECT * FROM daily_stat")
    suspend fun all(): List<DailyStatEntity>

    @Query("SELECT * FROM daily_stat WHERE date >= :fromDate ORDER BY date")
    suspend fun since(fromDate: String): List<DailyStatEntity>

    @Query("SELECT * FROM daily_stat ORDER BY date DESC")
    suspend fun allDesc(): List<DailyStatEntity>
}

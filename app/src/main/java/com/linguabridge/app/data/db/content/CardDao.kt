package com.linguabridge.app.data.db.content

import androidx.room.Dao
import androidx.room.Query

@Dao
interface CardDao {

    @Query("SELECT * FROM card WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<CardEntity>

    @Query("SELECT id FROM card")
    suspend fun allIds(): List<String>

    @Query("SELECT id FROM card WHERE card_type = :cardType")
    suspend fun idsByType(cardType: String): List<String>

    @Query("SELECT * FROM card WHERE id = :id")
    suspend fun byId(id: String): CardEntity?

    @Query("SELECT COUNT(*) FROM card WHERE card_type = :cardType")
    suspend fun countByType(cardType: String): Int

    /** Random same-deck cards used as wrong options in choice exercises. */
    @Query(
        "SELECT * FROM card WHERE card_type = :cardType AND id != :excludeId " +
            "ORDER BY RANDOM() LIMIT :n"
    )
    suspend fun randomByType(cardType: String, excludeId: String, n: Int): List<CardEntity>

    @Query("SELECT COUNT(*) FROM card")
    suspend fun count(): Int
}

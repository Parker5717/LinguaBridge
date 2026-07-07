package com.linguabridge.app.data.db.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CardStateDao {

    @Upsert
    suspend fun upsert(state: CardStateEntity)

    /** Seeds bundled cards as 'new'; existing progress rows are left untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(states: List<CardStateEntity>)

    @Query("SELECT COUNT(*) FROM card_state WHERE state = 'learning' OR state = 'relearning'")
    fun countLearning(): Flow<Int>

    @Query("SELECT * FROM card_state WHERE card_id = :cardId")
    suspend fun byId(cardId: String): CardStateEntity?

    @Query(
        "SELECT * FROM card_state WHERE state != 'new' AND due_at <= :now " +
            "ORDER BY due_at LIMIT :limit"
    )
    suspend fun due(now: Long, limit: Int): List<CardStateEntity>

    @Query("SELECT * FROM card_state WHERE state = 'new' ORDER BY added_at LIMIT :limit")
    suspend fun newCards(limit: Int): List<CardStateEntity>

    // Card ids look like "card:{type}:{source}", so deck filtering is a prefix match.

    @Query(
        "SELECT * FROM card_state WHERE state != 'new' AND due_at <= :now " +
            "AND card_id LIKE 'card:' || :deckType || ':%' ORDER BY due_at LIMIT :limit"
    )
    suspend fun dueByDeck(deckType: String, now: Long, limit: Int): List<CardStateEntity>

    @Query(
        "SELECT * FROM card_state WHERE state = 'new' " +
            "AND card_id LIKE 'card:' || :deckType || ':%' ORDER BY added_at LIMIT :limit"
    )
    suspend fun newCardsByDeck(deckType: String, limit: Int): List<CardStateEntity>

    @Query(
        "SELECT COUNT(*) FROM card_state WHERE state != 'new' AND due_at <= :now " +
            "AND card_id LIKE 'card:' || :deckType || ':%'"
    )
    fun countDueByDeck(deckType: String, now: Long): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM card_state WHERE state = :state " +
            "AND card_id LIKE 'card:' || :deckType || ':%'"
    )
    fun countByStateByDeck(deckType: String, state: String): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM card_state WHERE (state = 'learning' OR state = 'relearning') " +
            "AND card_id LIKE 'card:' || :deckType || ':%'"
    )
    fun countLearningByDeck(deckType: String): Flow<Int>

    @Query(
        "SELECT MIN(due_at) FROM card_state WHERE state != 'new' " +
            "AND card_id LIKE 'card:' || :deckType || ':%'"
    )
    suspend fun nextDueByDeck(deckType: String): Long?

    /** Next new card that has NOT been bumped to the queue front yet
     *  (added_at = 0 marks cards already surfaced via notification or
     *  add-to-deck), so word notifications never repeat a word. */
    @Query(
        "SELECT * FROM card_state WHERE state = 'new' AND added_at > 0 " +
            "AND card_id LIKE 'card:' || :deckType || ':%' ORDER BY added_at LIMIT 1"
    )
    suspend fun nextNotifiableByDeck(deckType: String): CardStateEntity?

    @Query("SELECT COUNT(*) FROM card_state WHERE state != 'new' AND due_at <= :now")
    fun countDue(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM card_state WHERE state = :state")
    fun countByState(state: String): Flow<Int>

    @Query("SELECT card_id FROM card_state")
    suspend fun allCardIds(): List<String>

    @Query("SELECT COUNT(*) FROM card_state")
    suspend fun count(): Int

    @Query(
        "SELECT COUNT(*) FROM card_state WHERE state = 'review' " +
            "AND card_id LIKE 'card:' || :deckType || ':%'"
    )
    suspend fun countReviewByDeck(deckType: String): Int
}

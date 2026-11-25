package com.chinoisfacile.mandarin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chinoisfacile.mandarin.data.local.entity.WordProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM word_progress")
    fun observeAllProgress(): Flow<List<WordProgressEntity>>

    @Query("SELECT * FROM word_progress")
    suspend fun getAllProgress(): List<WordProgressEntity>

    @Query("SELECT * FROM word_progress WHERE wordId IN (:wordIds)")
    suspend fun getProgressByWordIds(wordIds: List<String>): List<WordProgressEntity>

    @Query(
        """
        SELECT * FROM word_progress
        WHERE nextReviewEpochDay <= :todayEpochDay
        ORDER BY nextReviewEpochDay ASC, easeFactor ASC
        """,
    )
    suspend fun getDueProgress(todayEpochDay: Long): List<WordProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(items: List<WordProgressEntity>)

    @Query("DELETE FROM word_progress")
    suspend fun clearAllProgress()
}

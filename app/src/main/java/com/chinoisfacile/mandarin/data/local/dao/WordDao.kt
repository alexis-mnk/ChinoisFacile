package com.chinoisfacile.mandarin.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.chinoisfacile.mandarin.data.local.entity.WordEntity
import kotlinx.coroutines.flow.Flow

data class WordLevelRef(
    val id: String,
    val hskLevel: Int,
)

@Dao
interface WordDao {
    @Query("SELECT * FROM words WHERE hskLevel = :hskLevel ORDER BY id ASC")
    fun observeWordsByLevel(hskLevel: Int): Flow<List<WordEntity>>

    @Query("SELECT id, hskLevel FROM words")
    fun observeAllWordLevelRefs(): Flow<List<WordLevelRef>>

    @Query("SELECT * FROM words WHERE lessonId = :lessonId ORDER BY id ASC")
    suspend fun getWordsForLesson(lessonId: String): List<WordEntity>

    @Query(
        """
        SELECT w.* FROM words w
        INNER JOIN lessons l ON w.lessonId = l.id
        WHERE l.unitId = :unitId
        ORDER BY l.orderIndex ASC, w.id ASC
        """,
    )
    suspend fun getWordsForUnit(unitId: String): List<WordEntity>

    @Query("SELECT * FROM words WHERE hskLevel = :hskLevel ORDER BY id ASC")
    suspend fun getWordsByLevel(hskLevel: Int): List<WordEntity>

    @Query("SELECT hanzi FROM words WHERE hskLevel < :hskLevel")
    suspend fun getHanziBelowLevel(hskLevel: Int): List<String>

    @Query(
        """
        SELECT w.hanzi FROM words w
        INNER JOIN lessons l ON w.lessonId = l.id
        WHERE w.hskLevel = :hskLevel AND l.orderIndex <= :maxOrderIndex
        """,
    )
    suspend fun getHanziUpToLessonOrder(hskLevel: Int, maxOrderIndex: Int): List<String>

    @Query("SELECT * FROM words ORDER BY id ASC")
    suspend fun getAllWords(): List<WordEntity>

    @Query(
        """
        SELECT w.* FROM words w
        INNER JOIN lessons l ON w.lessonId = l.id
        WHERE w.hskLevel = :hskLevel AND l.isUnlocked = 1
        ORDER BY l.orderIndex ASC, w.id ASC
        """,
    )
    suspend fun getUnlockedWordsByLevel(hskLevel: Int): List<WordEntity>

    @Upsert
    suspend fun upsertWords(words: List<WordEntity>)

    @Query("DELETE FROM words WHERE hskLevel = :hskLevel")
    suspend fun deleteWordsByLevel(hskLevel: Int)
}

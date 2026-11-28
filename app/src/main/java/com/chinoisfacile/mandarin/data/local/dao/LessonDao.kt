package com.chinoisfacile.mandarin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.chinoisfacile.mandarin.data.local.entity.LessonEntity
import com.chinoisfacile.mandarin.data.local.entity.UnitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {
    @Query("SELECT * FROM units WHERE hskLevel = :hskLevel ORDER BY orderIndex ASC")
    fun observeUnitsByLevel(hskLevel: Int): Flow<List<UnitEntity>>

    @Query("SELECT * FROM lessons WHERE hskLevel = :hskLevel ORDER BY orderIndex ASC")
    fun observeLessonsByLevel(hskLevel: Int): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE hskLevel = :hskLevel ORDER BY orderIndex ASC")
    suspend fun getLessonsByLevel(hskLevel: Int): List<LessonEntity>

    @Query("SELECT * FROM units WHERE id = :unitId LIMIT 1")
    suspend fun getUnitById(unitId: String): UnitEntity?

    @Query("SELECT * FROM lessons WHERE unitId = :unitId ORDER BY orderIndex ASC")
    suspend fun getLessonsByUnit(unitId: String): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE id = :lessonId LIMIT 1")
    suspend fun getLessonById(lessonId: String): LessonEntity?

    @Query(
        """
        SELECT * FROM lessons
        WHERE hskLevel = :hskLevel AND orderIndex > :currentOrder
        ORDER BY orderIndex ASC
        LIMIT 1
        """,
    )
    suspend fun getNextLessonAfterOrder(hskLevel: Int, currentOrder: Int): LessonEntity?

    @Query("SELECT COUNT(*) FROM lessons WHERE hskLevel = :hskLevel")
    suspend fun countLessonsByLevel(hskLevel: Int): Int

    @Upsert
    suspend fun upsertUnits(units: List<UnitEntity>)

    @Upsert
    suspend fun upsertLessons(lessons: List<LessonEntity>)

    @Query("DELETE FROM units WHERE hskLevel = :hskLevel")
    suspend fun deleteUnitsByLevel(hskLevel: Int)

    @Query("DELETE FROM lessons WHERE hskLevel = :hskLevel")
    suspend fun deleteLessonsByLevel(hskLevel: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLessonsIgnore(lessons: List<LessonEntity>)

    /**
     * Met à jour uniquement les métadonnées d'une leçon sans jamais toucher
     * à isUnlocked, isCompleted, bestScore ni completedCount.
     */
    @Query(
        """
        UPDATE lessons
        SET unitId = :unitId,
            hskLevel = :hskLevel,
            orderIndex = :orderIndex,
            title = :title
        WHERE id = :id
        """,
    )
    suspend fun updateLessonMetadata(
        id: String,
        unitId: String,
        hskLevel: Int,
        orderIndex: Int,
        title: String,
    )

    @Query(
        """
        UPDATE lessons
        SET isCompleted = 1,
            isUnlocked = 1,
            bestScore = CASE WHEN :score > bestScore THEN :score ELSE bestScore END,
            completedCount = completedCount + 1
        WHERE id = :lessonId
        """,
    )
    suspend fun markLessonCompleted(lessonId: String, score: Int)

    @Query("UPDATE lessons SET isUnlocked = 1 WHERE id = :lessonId")
    suspend fun unlockLesson(lessonId: String)

    @Query(
        """
        UPDATE lessons
        SET isCompleted = 0,
            bestScore = 0,
            completedCount = 0,
            isUnlocked = 0
        """,
    )
    suspend fun resetAllLessonsProgress()

    @Query(
        """
        SELECT * FROM lessons
        WHERE isUnlocked = 1 OR isCompleted = 1 OR bestScore > 0 OR completedCount > 0
        """,
    )
    suspend fun getProgressedLessons(): List<LessonEntity>

    @Query(
        """
        UPDATE lessons
        SET isUnlocked = :isUnlocked,
            isCompleted = :isCompleted,
            bestScore = :bestScore,
            completedCount = :completedCount
        WHERE id = :lessonId
        """,
    )
    suspend fun restoreLessonProgress(
        lessonId: String,
        isUnlocked: Boolean,
        isCompleted: Boolean,
        bestScore: Int,
        completedCount: Int,
    )
}

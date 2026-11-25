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
            isUnlocked = CASE WHEN orderIndex = 1 THEN 1 ELSE 0 END
        """,
    )
    suspend fun resetAllLessonsProgress()
}

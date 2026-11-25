package com.chinoisfacile.mandarin.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chinoisfacile.mandarin.data.local.dao.LessonDao
import com.chinoisfacile.mandarin.data.local.dao.ProgressDao
import com.chinoisfacile.mandarin.data.local.dao.UserStatsDao
import com.chinoisfacile.mandarin.data.local.dao.WordDao
import com.chinoisfacile.mandarin.data.local.entity.LessonEntity
import com.chinoisfacile.mandarin.data.local.entity.UnitEntity
import com.chinoisfacile.mandarin.data.local.entity.UserStatsEntity
import com.chinoisfacile.mandarin.data.local.entity.WordEntity
import com.chinoisfacile.mandarin.data.local.entity.WordProgressEntity

@Database(
    entities = [
        UnitEntity::class,
        LessonEntity::class,
        WordEntity::class,
        WordProgressEntity::class,
        UserStatsEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao
    abstract fun wordDao(): WordDao
    abstract fun progressDao(): ProgressDao
    abstract fun userStatsDao(): UserStatsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE units ADD COLUMN hskLevel INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE lessons ADD COLUMN hskLevel INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE words ADD COLUMN hskLevel INTEGER NOT NULL DEFAULT 1")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_units_hskLevel` ON `units` (`hskLevel`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_hskLevel` ON `lessons` (`hskLevel`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_words_hskLevel` ON `words` (`hskLevel`)")
            }
        }
    }
}

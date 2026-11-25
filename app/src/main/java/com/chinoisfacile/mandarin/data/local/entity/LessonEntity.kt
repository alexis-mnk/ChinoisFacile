package com.chinoisfacile.mandarin.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lessons",
    indices = [
        Index(value = ["unitId"]),
        Index(value = ["hskLevel"]),
    ],
)
data class LessonEntity(
    @PrimaryKey val id: String,
    val unitId: String,
    val hskLevel: Int = 1,
    val orderIndex: Int,
    val title: String,
    val isUnlocked: Boolean,
    val isCompleted: Boolean,
    val bestScore: Int,
    val completedCount: Int,
)

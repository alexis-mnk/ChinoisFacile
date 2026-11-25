package com.chinoisfacile.mandarin.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_progress")
data class WordProgressEntity(
    @PrimaryKey val wordId: String,
    val repetitions: Int = 0,
    val easeFactor: Double = 2.5,
    val intervalDays: Int = 0,
    val nextReviewEpochDay: Long = 0L,
    val lastReviewedEpochDay: Long? = null,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
)

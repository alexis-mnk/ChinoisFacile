package com.chinoisfacile.mandarin.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
    indices = [
        Index(value = ["lessonId"]),
        Index(value = ["hskLevel"]),
    ],
)
data class WordEntity(
    @PrimaryKey val id: String,
    val lessonId: String,
    val hskLevel: Int = 1,
    val hanzi: String,
    val pinyin: String,
    val english: String,
    val exampleHanzi: String,
    val examplePinyin: String,
    val exampleEnglish: String,
    val exampleTokensJson: String,
)

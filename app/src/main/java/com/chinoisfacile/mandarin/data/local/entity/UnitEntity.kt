package com.chinoisfacile.mandarin.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "units",
    indices = [Index(value = ["hskLevel"])],
)
data class UnitEntity(
    @PrimaryKey val id: String,
    val hskLevel: Int = 1,
    val orderIndex: Int,
    val title: String,
    val description: String,
)

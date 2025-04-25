package com.example.stepcoach

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "steps")
data class Steps(
    @PrimaryKey val date: String,
    val steps: Int
)
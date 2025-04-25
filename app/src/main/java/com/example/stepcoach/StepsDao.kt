package com.example.stepcoach

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StepsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(Steps: Steps)

    @Query("SELECT * FROM steps ORDER BY date DESC")
    fun getAllSteps(): Flow<List<Steps>>

    @Query("SELECT * FROM steps WHERE date = :date LIMIT 1")
    suspend fun getStepsForDate(date: String): Steps?
}
package com.example.stepcoach

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Steps::class], version = 1)
abstract class thisDatabase : RoomDatabase() {
    abstract fun stepsDao(): StepsDao

    companion object {
        @Volatile private var INSTANCE: thisDatabase? = null

        fun getDatabase(context: Context): thisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    thisDatabase::class.java,
                    "steps_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
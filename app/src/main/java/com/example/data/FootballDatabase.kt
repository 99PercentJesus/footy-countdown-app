package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SelectedTeamEntity::class, CachedMatchEntity::class],
    version = 2,
    exportSchema = false
)
abstract class FootballDatabase : RoomDatabase() {
    abstract fun footballDao(): FootballDao

    companion object {
        @Volatile
        private var INSTANCE: FootballDatabase? = null

        fun getDatabase(context: Context): FootballDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FootballDatabase::class.java,
                    "football_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

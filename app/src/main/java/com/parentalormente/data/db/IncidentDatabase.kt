package com.parentalormente.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [IncidentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class IncidentDatabase : RoomDatabase() {

    abstract fun incidentDao(): IncidentDao

    companion object {
        @Volatile
        private var INSTANCE: IncidentDatabase? = null

        fun getInstance(context: Context): IncidentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IncidentDatabase::class.java,
                    "parentalormente_incidents.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

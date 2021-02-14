package com.meteocool.location.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.meteocool.location.MeteocoolLocation

@Database(entities = [MeteocoolLocation::class], version = 1)
abstract class BasicLocationDatabase : RoomDatabase() {
    abstract fun meteoLocationDao(): LocationDao

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: BasicLocationDatabase? = null

        fun getDatabase(context: Context): BasicLocationDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BasicLocationDatabase::class.java,
                    "location_database"
                ).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }

}

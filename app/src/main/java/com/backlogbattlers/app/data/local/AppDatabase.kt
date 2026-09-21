package com.backlogbattlers.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.backlogbattlers.app.data.local.dao.CachedGameDao
import com.backlogbattlers.app.data.local.dao.LibraryEntryDao
import com.backlogbattlers.app.data.local.dao.UserDao
import com.backlogbattlers.app.data.local.entity.CachedGameEntity
import com.backlogbattlers.app.data.local.entity.LibraryEntryEntity
import com.backlogbattlers.app.data.local.entity.UserEntity

//------------------------------
// this class provides the Room database for the application
@Database(
    entities = [
        UserEntity::class,
        CachedGameEntity::class,
        LibraryEntryEntity::class,
    ],
    // !!!Please increment the version here if you merge from main and wanna test. Current: 1
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    // returns the UserDao instance
    abstract fun userDao(): UserDao

    // returns the CachedGameDao instance
    abstract fun cachedGameDao(): CachedGameDao

    // returns the LibraryEntryDao instance
    abstract fun libraryEntryDao(): LibraryEntryDao

    companion object {

        @Volatile
        private var instance: AppDatabase? = null

        // returns the singleton instance of AppDatabase
        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "backlog_battlers.db",
                )
                    // destructive migrations true for P2 ONLY, ensure run on all our PC's with no records on create
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
//------------------------------EOF------------------------------\\
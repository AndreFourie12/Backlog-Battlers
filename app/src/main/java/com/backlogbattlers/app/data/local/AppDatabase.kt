package com.backlogbattlers.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    // !!!changing an entity? increment the version AND add a Migration below (see MIGRATION_4_5).
    // from version 4 on the database is migrated, not wiped, so library changes that have not
    // synced yet survive an app update. a version bump with no migration now crashes on purpose
    version = 6,
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

        // returns the singleton instance of AppDatabase. currentUserId is the signed-in account
        // (null when signed out), which MIGRATION_4_5 hands the existing library rows to
        fun getInstance(context: Context, currentUserId: () -> String?): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "backlog_battlers.db",
                )
                    .addMigrations(Migration4To5(currentUserId), MIGRATION_5_6)
                    // the Part 2 builds (versions 1-3) only ever held test data, so those are still wiped
                    .fallbackToDestructiveMigrationFrom(true, 1, 2, 3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

//------------------------------
// version 4 -> 5: library entries now belong to an account (userId).
// rows from before this change were all made on this phone by whoever was signed in, so they
// go to the account that is signed in now. with nobody signed in they keep an empty userId,
// which no account can see, rather than being handed to whoever signs in next
class Migration4To5(private val currentUserId: () -> String?) : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `library_entries` ADD COLUMN `userId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_entries_userId` ON `library_entries` (`userId`)")
        currentUserId()?.let { userId ->
            db.execSQL("UPDATE `library_entries` SET `userId` = ?", arrayOf(userId))
        }
    }
}

//------------------------------
// version 5 -> 6: cached games keep every landscape picture the API found (artworkUrls), so a wide tile
// can try the next one when a picture is missing. games cached before this have none, and fall back to
// their single artworkUrl until the API sends them again
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cached_games` ADD COLUMN `artworkUrls` TEXT NOT NULL DEFAULT ''")
    }
}
//------------------------------EOF------------------------------\\
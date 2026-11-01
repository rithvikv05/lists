package com.lists.app.data.db

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.lists.app.data.model.ItemStatus

class Converters {
    @TypeConverter
    fun fromItemStatus(value: ItemStatus?): String {
        return value?.name ?: ItemStatus.BACKLOG.name
    }

    @TypeConverter
    fun toItemStatus(value: String?): ItemStatus {
        return try {
            if (value != null) ItemStatus.valueOf(value) else ItemStatus.BACKLOG
        } catch (e: Exception) {
            ItemStatus.BACKLOG
        }
    }
}

@Database(entities = [UserList::class, ListItem::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun listDao(): ListDao
    abstract fun itemDao(): ItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN reviewRating INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE items ADD COLUMN checkedEpisodesJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE items ADD COLUMN episodesDataJson TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add uuid column with empty default
                db.execSQL("ALTER TABLE lists ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE items ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                // Backfill existing rows with unique hex UUIDs
                db.execSQL("""
                    UPDATE lists SET uuid = 
                        lower(hex(randomblob(4))) || '-' || 
                        lower(hex(randomblob(2))) || '-4' || 
                        substr(lower(hex(randomblob(2))),2) || '-' || 
                        substr('89ab', abs(random()) % 4 + 1, 1) || 
                        substr(lower(hex(randomblob(2))),2) || '-' || 
                        lower(hex(randomblob(6)))
                    WHERE uuid = ''
                """)
                db.execSQL("""
                    UPDATE items SET uuid = 
                        lower(hex(randomblob(4))) || '-' || 
                        lower(hex(randomblob(2))) || '-4' || 
                        substr(lower(hex(randomblob(2))),2) || '-' || 
                        substr('89ab', abs(random()) % 4 + 1, 1) || 
                        substr(lower(hex(randomblob(2))),2) || '-' || 
                        lower(hex(randomblob(6)))
                    WHERE uuid = ''
                """)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val legacyDb = context.getDatabasePath("listy_database.db")
                val newDb = context.getDatabasePath("lists_database.db")
                if (legacyDb.exists() && !newDb.exists()) {
                    try {
                        legacyDb.renameTo(newDb)
                        val legacyShm = context.getDatabasePath("listy_database.db-shm")
                        if (legacyShm.exists()) legacyShm.renameTo(context.getDatabasePath("lists_database.db-shm"))
                        val legacyWal = context.getDatabasePath("listy_database.db-wal")
                        if (legacyWal.exists()) legacyWal.renameTo(context.getDatabasePath("lists_database.db-wal"))
                    } catch (_: Exception) {}
                }

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lists_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}


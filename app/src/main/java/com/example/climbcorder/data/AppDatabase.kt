package com.example.climbcorder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Calendar
import java.util.concurrent.Executors

@Database(entities = [ClimbRecording::class, HiddenVideo::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun hiddenVideoDao(): HiddenVideoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hidden_videos` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`mediaStoreId` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_hidden_videos_mediaStoreId` ON `hidden_videos` (`mediaStoreId`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "climbcorder.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            Executors.newSingleThreadExecutor().execute {
                                val dao = getInstance(context).recordingDao()
                                // Jan 2026: 5, 15, 22
                                for (day in listOf(5, 15, 22)) {
                                    dao.insert(ClimbRecording(timestamp = toEpoch(2026, Calendar.JANUARY, day)))
                                }
                                // Feb 2026: 3, 7, 9
                                for (day in listOf(3, 7, 9)) {
                                    dao.insert(ClimbRecording(timestamp = toEpoch(2026, Calendar.FEBRUARY, day)))
                                }
                            }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private fun toEpoch(year: Int, month: Int, day: Int): Long {
            return Calendar.getInstance().apply {
                set(year, month, day, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
}

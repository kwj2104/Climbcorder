package com.example.climbcorder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Calendar
import java.util.concurrent.Executors

@Database(entities = [ClimbRecording::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "climbcorder.db"
                )
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

package com.example.climbcorder

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RecordingDao {
    @Insert
    fun insert(recording: ClimbRecording)

    @Query("SELECT * FROM ClimbRecording WHERE timestamp >= :startMillis AND timestamp < :endMillis")
    fun getRecordingsInRange(startMillis: Long, endMillis: Long): List<ClimbRecording>
}

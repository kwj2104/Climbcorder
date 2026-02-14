package com.example.climbcorder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RecordingDao {
    @Insert
    fun insert(recording: ClimbRecording)

    @Query("SELECT * FROM ClimbRecording WHERE timestamp >= :startMillis AND timestamp < :endMillis")
    fun getRecordingsInRange(startMillis: Long, endMillis: Long): List<ClimbRecording>

    @Query("SELECT DISTINCT timestamp FROM ClimbRecording ORDER BY timestamp DESC")
    fun getAllTimestampsDesc(): List<Long>
}

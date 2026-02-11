package com.example.climbcorder

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ClimbRecording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long
)

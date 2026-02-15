package com.example.climbcorder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hidden_videos",
    indices = [Index(value = ["mediaStoreId"], unique = true)]
)
data class HiddenVideo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreId: Long
)

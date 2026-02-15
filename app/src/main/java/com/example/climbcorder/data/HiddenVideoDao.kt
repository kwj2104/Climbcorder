package com.example.climbcorder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HiddenVideoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(hiddenVideo: HiddenVideo)

    @Query("SELECT mediaStoreId FROM hidden_videos")
    fun getAllHiddenIds(): List<Long>

    @Query("DELETE FROM hidden_videos WHERE mediaStoreId = :mediaStoreId")
    fun deleteByMediaStoreId(mediaStoreId: Long)
}

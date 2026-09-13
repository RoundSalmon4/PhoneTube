package com.roundsalmon4.phonetube.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.roundsalmon4.phonetube.core.database.entity.IptvChannel

@Dao
interface IptvChannelDao {
    @Query("SELECT * FROM iptv_channels WHERE providerId = :providerId")
    suspend fun getAllForProvider(providerId: String): List<IptvChannel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<IptvChannel>)

    @Query("DELETE FROM iptv_channels WHERE providerId = :providerId")
    suspend fun deleteForProvider(providerId: String)
}
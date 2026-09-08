package com.roundsalmon4.phonetube.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.roundsalmon4.phonetube.core.database.entity.IptvFavorite
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvFavoriteDao {
    @Query("SELECT * FROM iptv_favorites ORDER BY addedAt DESC")
    fun getAll(): Flow<List<IptvFavorite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: IptvFavorite)

    @Query("DELETE FROM iptv_favorites WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("SELECT COUNT(*) FROM iptv_favorites WHERE videoId = :videoId")
    suspend fun exists(videoId: String): Int
}
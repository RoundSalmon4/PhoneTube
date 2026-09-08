package com.roundsalmon4.phonetube.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.roundsalmon4.phonetube.core.database.entity.IptvProvider
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {
    @Query("SELECT * FROM iptv_providers ORDER BY name")
    fun getAll(): Flow<List<IptvProvider>>

    @Query("SELECT * FROM iptv_providers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): IptvProvider?

    @Query("SELECT * FROM iptv_providers WHERE enabled = 1")
    suspend fun getEnabledSync(): List<IptvProvider>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: IptvProvider)

    @Query("DELETE FROM iptv_providers WHERE id = :id")
    suspend fun delete(id: String)
}
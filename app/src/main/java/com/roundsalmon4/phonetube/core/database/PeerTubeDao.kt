package com.roundsalmon4.phonetube.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.roundsalmon4.phonetube.core.database.entity.PeerTubeInstance
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerTubeDao {
    @Query("SELECT * FROM peertube_instances ORDER BY name")
    fun getAll(): Flow<List<PeerTubeInstance>>

    @Query("SELECT * FROM peertube_instances WHERE enabled = 1")
    suspend fun getEnabledSync(): List<PeerTubeInstance>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(instance: PeerTubeInstance)

    @Query("DELETE FROM peertube_instances WHERE host = :host")
    suspend fun delete(host: String)

    @Query("UPDATE peertube_instances SET enabled = :enabled WHERE host = :host")
    suspend fun setEnabled(host: String, enabled: Boolean)
}
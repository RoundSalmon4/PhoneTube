package com.roundsalmon4.phonetube.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.roundsalmon4.phonetube.core.database.entity.InvidiousInstance
import kotlinx.coroutines.flow.Flow

@Dao
interface InvidiousDao {
    @Query("SELECT * FROM invidious_instances ORDER BY name")
    fun getAll(): Flow<List<InvidiousInstance>>

    @Query("SELECT * FROM invidious_instances WHERE enabled = 1")
    suspend fun getEnabledSync(): List<InvidiousInstance>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(instance: InvidiousInstance)

    @Query("DELETE FROM invidious_instances WHERE host = :host")
    suspend fun delete(host: String)

    @Query("UPDATE invidious_instances SET enabled = :enabled WHERE host = :host")
    suspend fun setEnabled(host: String, enabled: Boolean)
}

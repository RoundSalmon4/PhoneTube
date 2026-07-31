package com.roundsalmon4.phonetube.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.PlaylistVideo
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<LocalPlaylist>>

    @Query("SELECT sourcePlaylistId FROM playlists WHERE sourcePlaylistId IS NOT NULL")
    fun getSavedPlaylistIds(): Flow<List<String>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): LocalPlaylist?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: LocalPlaylist): Long

    @Update
    suspend fun updatePlaylist(playlist: LocalPlaylist)

    @Delete
    suspend fun deletePlaylist(playlist: LocalPlaylist)

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY position")
    fun getPlaylistVideos(playlistId: Long): Flow<List<PlaylistVideo>>

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getPlaylistVideosSync(playlistId: Long): List<PlaylistVideo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: PlaylistVideo)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeVideo(playlistId: Long, videoId: String)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun getVideoCount(playlistId: Long): Int

    @Query("UPDATE playlist_videos SET position = :position WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun updateVideoPosition(playlistId: Long, videoId: String, position: Int)
}

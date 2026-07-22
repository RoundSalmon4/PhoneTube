package app.phonetube.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.phonetube.core.database.entity.LocalPlaylist
import app.phonetube.core.database.entity.PlaylistVideo
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<LocalPlaylist>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): LocalPlaylist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: LocalPlaylist): Long

    @Delete
    suspend fun deletePlaylist(playlist: LocalPlaylist)

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY position")
    fun getPlaylistVideos(playlistId: Long): Flow<List<PlaylistVideo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: PlaylistVideo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<PlaylistVideo>)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeVideo(playlistId: Long, videoId: String)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)
}

package com.roundsalmon4.phonetube.core.database

import com.roundsalmon4.phonetube.core.database.entity.LocalPlaylist
import com.roundsalmon4.phonetube.core.database.entity.PlaylistVideo
import com.roundsalmon4.phonetube.core.engine.model.Video

fun Video.toPlaylistVideoInfo() = PlaylistVideoInfo(
    videoId = videoId,
    title = title,
    channelName = author,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs
)

data class PlaylistVideoInfo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationMs: Long
)

object PlaylistSaver {

    suspend fun addToPlaylist(playlistDao: PlaylistDao, video: PlaylistVideoInfo, playlist: LocalPlaylist): Boolean {
        return try {
            val count = playlistDao.getVideoCount(playlist.id)
            playlistDao.insertVideo(
                PlaylistVideo(
                    playlistId = playlist.id,
                    videoId = video.videoId,
                    title = video.title,
                    channelName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl,
                    durationMs = video.durationMs,
                    position = count
                )
            )
            playlistDao.updatePlaylist(playlist.copy(videoCount = count + 1))
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun createAndAdd(playlistDao: PlaylistDao, video: PlaylistVideoInfo, name: String): Boolean {
        return try {
            val trimmed = name.trim()
            val id = playlistDao.insertPlaylist(
                LocalPlaylist(name = trimmed, createdAt = System.currentTimeMillis())
            )
            playlistDao.insertVideo(
                PlaylistVideo(
                    playlistId = id,
                    videoId = video.videoId,
                    title = video.title,
                    channelName = video.channelName,
                    thumbnailUrl = video.thumbnailUrl,
                    durationMs = video.durationMs,
                    position = 0
                )
            )
            playlistDao.updatePlaylist(
                LocalPlaylist(id = id, name = trimmed, createdAt = System.currentTimeMillis(), videoCount = 1)
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}

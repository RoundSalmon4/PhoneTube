package app.phonetube.core.engine

import com.liskovsoft.mediaserviceinterfaces.ContentService
import com.liskovsoft.mediaserviceinterfaces.MediaItemService
import com.liskovsoft.mediaserviceinterfaces.ServiceManager
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.rx2.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeEngine @Inject constructor(
    private val initializer: YouTubeInitializer
) {
    private val serviceManager: ServiceManager
        get() {
            initializer.init()
            return YouTubeServiceManager.instance()
        }

    private val contentService: ContentService
        get() = serviceManager.contentService

    private val mediaItemService: MediaItemService
        get() = serviceManager.mediaItemService

    fun getHome(): Flow<List<MediaGroup>> = flow {
        val groups = contentService.homeObserve.await()
        emit(groups)
    }.flowOn(Dispatchers.IO)

    fun getTrending(): Flow<List<MediaGroup>> = flow {
        val groups = contentService.trendingObserve.await()
        emit(groups)
    }.flowOn(Dispatchers.IO)

    fun getMusic(): Flow<List<MediaGroup>> = flow {
        val groups = contentService.musicObserve.await()
        emit(groups)
    }.flowOn(Dispatchers.IO)

    fun search(query: String): Flow<List<MediaItem>> = flow {
        val results = contentService.getSearchObserve(query).await()
        emit(results)
    }.flowOn(Dispatchers.IO)

    fun getSearchSuggestions(query: String): Flow<List<String>> = flow {
        val suggestions = contentService.getSearchTagsObserve(query).await()
        emit(suggestions)
    }.flowOn(Dispatchers.IO)

    fun getChannel(channelId: String): Flow<MediaGroup> = flow {
        val channel = contentService.getChannelObserve(channelId).await()
        emit(channel)
    }.flowOn(Dispatchers.IO)

    fun getChannelVideos(channelId: String): Flow<List<MediaItem>> = flow {
        val group = contentService.getChannelObserve(channelId).await()
        emit(group.mediaItems)
    }.flowOn(Dispatchers.IO)

    fun getStreamInfo(videoId: String): Flow<MediaItemFormatInfo> = flow {
        val info = mediaItemService.getFormatInfoObserve(videoId).await()
        emit(info)
    }.flowOn(Dispatchers.IO)

    fun getMetadata(videoId: String): Flow<MediaItemMetadata> = flow {
        val metadata = mediaItemService.getMetadataObserve(videoId).await()
        emit(metadata)
    }.flowOn(Dispatchers.IO)

    fun getSponsorSegments(videoId: String): Flow<List<SponsorSegment>> = flow {
        val segments = mediaItemService.getSponsorSegmentsObserve(videoId).await()
        emit(segments)
    }.flowOn(Dispatchers.IO)

    fun continueGroup(group: MediaGroup): Flow<List<MediaItem>> = flow {
        val items = contentService.continueGroupObserve(group).await()
        emit(items)
    }.flowOn(Dispatchers.IO)

    fun isSignedIn(): Boolean {
        initializer.init()
        return serviceManager.signInService.isSigned
    }
}

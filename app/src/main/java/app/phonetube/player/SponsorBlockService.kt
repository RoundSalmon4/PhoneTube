package app.phonetube.player

import androidx.compose.ui.graphics.Color
import app.phonetube.core.engine.YouTubeEngine
import app.phonetube.core.engine.model.SponsorSegment
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SponsorBlockService @Inject constructor(
    private val engine: YouTubeEngine
) {
    fun getSegments(videoId: String): Flow<List<SponsorSegment>> =
        engine.getSponsorSegments(videoId)

    fun checkForSkip(
        positionMs: Long,
        segments: List<SponsorSegment>
    ): SkipAction? {
        for (segment in segments) {
            if (segment.action == ACTION_MUTE) continue
            if (positionMs in segment.startMs until segment.endMs) {
                return SkipAction(
                    segment = segment,
                    seekToMs = segment.endMs
                )
            }
        }
        return null
    }

    data class SkipAction(
        val segment: SponsorSegment,
        val seekToMs: Long
    )

    companion object {
        private const val ACTION_SKIP = "skip"
        private const val ACTION_MUTE = "mute"

        val CATEGORY_COLORS = mapOf(
            "sponsor" to Color(0xFF00D400),
            "intro" to Color(0xFF0202ED),
            "outro" to Color(0xFF0202ED),
            "interaction" to Color(0xFFCC00FF),
            "selfpromo" to Color(0xFFFFFF00),
            "music_offtopic" to Color(0xFFFF9900),
            "preview" to Color(0xFF00FFFF),
            "poi_highlight" to Color(0xFFFF0000),
            "filler" to Color(0xFF00FFFF)
        )

        val DEFAULT_COLOR = Color(0xFF00D400)

        fun getCategoryColor(category: String): Color =
            CATEGORY_COLORS[category] ?: DEFAULT_COLOR
    }
}

# PhoneTube -- New UI Branch Implementation Plan

## Quick Start for New Assistant

**Repository:** `https://github.com/RoundSalmon4/SmartTube`
**Branch:** `new-ui` (branched from `phone-port`)
**Goal:** Build a brand-new Kotlin + Jetpack Compose YouTube phone app that uses SmartTube's MediaServiceCore as its YouTube data engine. No TV UI, no Leanback, no MVP presenters -- completely fresh modern Android architecture.
**Why:** The phone-port branch proved MediaServiceCore works on phones, but converting the TV-based Leanback UI is not worth the effort. Better to build a clean phone UI from scratch.

### Before doing anything
1. Read this entire document
2. Run `git log --oneline -3` to verify the branch state
3. Read the key files listed in the "Reference Files" section
4. **Do NOT try to build locally** -- there is no Android SDK. Code-only commits.
5. **Commits as roundsalmon4 / 209016228+RoundSalmon4@users.noreply.github.com**
6. **Never use the user's real name or personal info**
7. **All code/commits/comments in solo-dev style, not AI style**
8. **Always use PAT to commit and push** (configured in remote `fork`)
9. **Provide commit summary and get final go-ahead before committing**

---

## 1. Project Overview

### What We Are Building

A clean, modern YouTube phone app called **PhoneTube** (working name) with:
- YouTube content browsing (home, trending, search)
- Full video player with SponsorBlock support
- Local watch history
- Local playlists (no account needed)
- Local channel subscriptions (no account needed)
- No sign-in whatsoever

### What We Are NOT Building
- No sign-in / account management
- No cloud history sync
- No cloud playlists
- No server-side subscriptions
- No like/dislike
- No notifications from YouTube
- No Watch Later
- No TV UI / Leanback
- No live chat
- No comments (initially)

### Why MediaServiceCore

SmartTube's `MediaServiceCore` submodule is the most reliable YouTube data extraction engine available. It handles:
- Search, browse, trending, channel data
- Stream URL resolution (DASH, HLS, SABR)
- SponsorBlock segment fetching
- DeArrow (title/thumbnail replacement)
- Return YouTube Dislike data

The phone-port branch proved it works on phones. We keep ONLY this engine and throw away everything else.

---

## 2. Decisions Already Made (Do Not Re-Litigate)

These decisions were discussed and finalized. Do not suggest alternatives.

| Decision | Choice | Why |
|----------|--------|-----|
| **Language** | Kotlin | User requirement |
| **UI framework** | Jetpack Compose | User requirement |
| **Player library** | Media3 ExoPlayer (NOT the old fork) | The ExoPlayer 2.10.6 fork is outdated. SABR can be handled by the community `sabr-exoplayer` library with Media3. DASH/HLS work natively. |
| **Architecture** | MVVM + UDF (single immutable UiState per screen) | Standard 2025/2026 Android architecture |
| **DI** | Hilt | Standard for modern Android |
| **Async** | Kotlin Coroutines + Flow | MediaServiceCore's RxJava is bridged at the boundary |
| **Local DB** | Room | For history, playlists, subscriptions |
| **Prefs** | DataStore | Replaces SharedPreferences |
| **Images** | Coil 3 | Kotlin-first, Compose-native |
| **Navigation** | Navigation Compose | Standard |
| **Build system** | Kotlin DSL + Version Catalog | Modern Gradle |
| **minSdk** | 24 (Android 7.0) | Covers 96%+ of devices |
| **targetSdk** | 35 (36 later) | Started on 35 to keep AGP 8.7.3 happy; bump to 36 comes with the AGP 9 move (§10.1) |
| **compileSdk** | 35 (36 later) | Matches targetSdk |
| **ExoPlayer** | NOT the SmartTube fork | Using Media3 1.8.0. SABR via sabr-exoplayer community library. |
| **No sign-in** | Confirmed | All features local-only |
| **UI reference** | Nuvio Mobile (https://github.com/NuvioMedia/NuvioMobile) | Good reference for Compose media player app in Kotlin |
| **App name** | TBD | User hasn't decided yet |

---

## 3. What We Keep vs Delete

### KEEP (as local modules / git submodules)

| Module | Path | Why |
|--------|------|-----|
| MediaServiceCore | `MediaServiceCore/` (submodule) | YouTube API engine. We consume `:youtubeapi` and `:mediaserviceinterfaces`. |
| SharedModules | `SharedModules/` (submodule) | Required by MediaServiceCore. `GlobalPreferences.instance(context)` must be called first. Provides `:sharedutils`. |

### DELETE (not needed for new app)

| Module | Why |
|--------|-----|
| `smarttubetv/` | All TV and phone UI. Replaced by new `app` module. |
| `common/` | MVP presenters, TV-coupled logic, Glide, RxJava. All replaced by Kotlin ViewModels. |
| `leanback-1.0.0/` | Forked AndroidX Leanback. TV-only. |
| `leanbackassistant/` | TV channel recommendations. |
| `chatkit/` | TV live chat widget. |
| `doubletapplayerview/` | Can rebuild in Compose if needed later. |
| `slidableactivity/` | Compose has built-in gesture support. |
| `filepicker-lib/` | Not needed. |
| `fragment-1.1.0/` | Forked AndroidX Fragment. Not needed with Compose. |
| `exoplayer-amzn-2.10.6/` | Outdated ExoPlayer fork. Using Media3 instead. |

### KEEP TEMPORARILY (until new app module is working)

| File | Why |
|------|-----|
| `gradlew`, `gradlew.bat` | Gradle wrapper scripts |
| `gradle/` | Gradle wrapper jar + properties (will be updated) |
| `.gitmodules` | Will be updated to only include MediaServiceCore and SharedModules |
| `.github/` | CI workflows (may need updating later) |

---

## 4. Tech Stack

These are the versions actually in `gradle/libs.versions.toml` right now. They're deliberately conservative — the point was to get MediaServiceCore's old submodules building alongside a modern Compose app without a fight. See §10.1 for the eventual bump to Kotlin 2.3 / AGP 9.2.

| Layer | Technology | Version |
|-------|-----------|---------|
| **Language** | Kotlin | 2.2.21 |
| **UI** | Jetpack Compose | BOM 2024.12.01 |
| **Design** | Material 3 | 1.3.1 |
| **Architecture** | MVVM + UDF | ViewModels + StateFlow |
| **DI** | Hilt | 2.53.1 |
| **Async** | Coroutines + Flow | KotlinX Coroutines 1.9.0 |
| **Local DB** | Room | 2.6.1 (KSP1 — see note) |
| **Prefs** | DataStore | 1.1.1 |
| **Images** | Coil 3 | 3.0.4 |
| **Navigation** | Navigation Compose | 2.8.5 |
| **Player** | Media3 ExoPlayer | 1.8.0 |
| **Player UI** | media3-ui-compose | 1.8.0 |
| **Media Session** | media3-session | 1.8.0 |
| **Network** | OkHttp + Retrofit (from MediaServiceCore) | 3.12.13 (forced) |
| **Build** | Kotlin DSL + Version Catalog | Gradle 8.11.1 |
| **AGP** | Android Gradle Plugin | 8.7.3 |
| **KSP** | Kotlin Symbol Processing | 2.2.21-2.0.5 (KSP1 — `ksp.useKSP2=false`) |
| **minSdk** | 24 | Android 7.0 |
| **targetSdk / compileSdk** | 35 | Android 15 |

**KSP note:** KSP2 (the default on Kotlin 2.x) blows up on Room's generated `suspend` DAOs with `IllegalStateException: unexpected jvm signature V` (google/ksp #2177, #2957; the broader Room+KSP2 saga is #1896, only closed mid-2026). We pin back to the stable KSP1 processor via `ksp.useKSP2=false` in `gradle.properties`. Revisit when we move to a Room/KSP that handles KSP2 cleanly — see §10.1.

### MediaServiceCore Dependencies (transitive)

These come along because we depend on `:youtubeapi` and `:mediaserviceinterfaces`:
- RxJava 2 (we bridge to Coroutines at the boundary)
- Retrofit 2 + Gson
- OkHttp 3.12.13
- Cronet (Chromium network stack)
- J2V8 (JavaScript engine for YouTube signature decryption)
- nanojson
- WebKit (for WebView-based auth if ever needed)

---

## 5. Project Structure

```
PhoneTube/                                  # Root repo
├── MediaServiceCore/                       # Git submodule (YouTube API engine)
├── SharedModules/                          # Git submodule (utilities, GlobalPreferences)
├── app/                                    # NEW: Our Compose app module
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/roundsalmon4/phonetube/
│       │   ├── PhoneTubeApp.kt             # @HiltAndroidApp Application
│       │   ├── MainActivity.kt              # Single Activity, hosts NavHost
│       │   │
│       │   ├── core/
│       │   │   ├── engine/                  # MediaServiceCore Kotlin wrapper
│       │   │   │   ├── YouTubeInitializer.kt   # Calls GlobalPreferences.instance(ctx) first
│       │   │   │   ├── YouTubeEngine.kt        # RxJava→Coroutines bridge for all API calls
│       │   │   │   └── model/                  # Clean Kotlin data classes
│       │   │   │       ├── Video.kt            # Video title, id, channel, thumbnail, duration, etc.
│       │   │   │       ├── StreamInfo.kt       # Stream URLs, formats, subtitles, SponsorBlock segments
│       │   │   │       ├── ChannelInfo.kt      # Channel name, avatar, subscriber count
│       │   │   │       ├── SearchResult.kt     # Search results (videos + channels + playlists)
│       │   │   │       └── SponsorsSegment.kt  # SponsorBlock segment data
│       │   │   ├── database/                # Room
│       │   │   │   ├── AppDatabase.kt       # @Database
│       │   │   │   ├── HistoryDao.kt        # Watch history CRUD
│       │   │   │   ├── PlaylistDao.kt       # Local playlists CRUD
│       │   │   │   ├── SubscriptionDao.kt   # Local subscriptions CRUD
│       │   │   │   └── entity/              # Room @Entity classes
│       │   │   │       ├── WatchHistoryEntry.kt
│       │   │   │       ├── LocalPlaylist.kt
│       │   │   │       ├── PlaylistVideo.kt (cross-ref)
│       │   │   │       └── LocalSubscription.kt
│       │   │   ├── datastore/
│       │   │   │   └── PlayerPreferences.kt # DataStore: speed, quality, SponsorBlock config, etc.
│       │   │   └── di/
│       │   │       ├── EngineModule.kt      # @Provides YouTubeEngine
│       │   │       ├── DatabaseModule.kt    # @Provides Room DAOs
│       │   │       └── AppModule.kt         # @Provides DataStore, etc.
│       │   │
│       │   ├── player/                     # Player layer (Nuvio-inspired)
│       │   │   ├── PlayerEngineController.kt # Abstraction over Media3 ExoPlayer
│       │   │   ├── PlayerPlaybackSnapshot.kt # Immutable state snapshot (polled 250ms)
│       │   │   ├── PlayerLayoutMetrics.kt   # Responsive breakpoints (phone/tablet)
│       │   │   ├── SponsorBlockService.kt   # Segment fetch + auto-skip logic
│       │   │   ├── service/
│       │   │   │   ├── PlaybackService.kt   # Media3 Session foreground service
│       │   │   │   └── NotificationBuilder.kt
│       │   │   └── gesture/
│       │   │       └── PlayerGestures.kt    # Brightness/volume/seek by drag
│       │   │
│       │   ├── repository/
│       │   │   ├── VideoRepository.kt       # YouTubeEngine + Room history integration
│       │   │   ├── HistoryRepository.kt     # Local watch history
│       │   │   ├── PlaylistRepository.kt    # Local playlists
│       │   │   └── SubscriptionRepository.kt # Local channel subscriptions
│       │   │
│       │   └── ui/
│       │       ├── theme/
│       │       │   ├── Theme.kt             # Material 3 theme (dark/light/AMOLED)
│       │       │   ├── Color.kt
│       │       │   └── Type.kt
│       │       ├── navigation/
│       │       │   └── AppNavigation.kt     # NavHost + route definitions
│       │       ├── home/
│       │       │   ├── HomeViewModel.kt
│       │       │   └── HomeScreen.kt        # YouTube trending/recommendations
│       │       ├── player/
│       │       │   ├── PlayerViewModel.kt
│       │       │   ├── PlayerScreen.kt      # Full player with controls
│       │       │   └── PlayerControls.kt    # Custom Compose controls overlay
│       │       ├── search/
│       │       │   ├── SearchViewModel.kt
│       │       │   └── SearchScreen.kt
│       │       ├── channel/
│       │       │   ├── ChannelViewModel.kt
│       │       │   └── ChannelScreen.kt     # Channel page with videos
│       │       ├── library/
│       │       │   ├── LibraryViewModel.kt
│       │       │   └── LibraryScreen.kt     # Tabs: History, Playlists, Subscriptions
│       │       ├── settings/
│       │       │   ├── SettingsViewModel.kt
│       │       │   └── SettingsScreen.kt    # SponsorBlock config, player prefs, app settings
│       │       └── components/
│       │           ├── VideoCard.kt          # Reusable video card composable
│       │           └── MiniPlayer.kt         # Persistent mini-player bar
│       │
│       └── res/
│           ├── values/
│           │   ├── strings.xml
│           │   ├── colors.xml
│           │   └── themes.xml               # Minimal theme (Compose handles the rest)
│           ├── drawable/                     # App icon, notification icon
│           └── xml/
│               └── network_security_config.xml # If needed for HTTP
│
├── gradle/
│   ├── libs.versions.toml                   # Version catalog (NEW)
│   └── wrapper/
│       └── gradle-wrapper.properties        # Updated to Gradle 8.11+
│
├── build.gradle.kts                         # Root build file (NEW, replaces old build.gradle)
├── settings.gradle.kts                      # NEW, replaces old settings.gradle
├── gradle.properties                        # UPDATED
├── gradlew                                  # KEPT (update wrapper)
├── gradlew.bat                              # KEPT
├── .gitmodules                              # UPDATED (only MediaServiceCore + SharedModules)
├── plan.md                                  # This file
└── README.md                                # Will need updating eventually
```

---

## 6. Screen-by-Screen Breakdown

### 6.1 Home Screen
- **Route:** `home`
- **ViewModel:** `HomeViewModel` → `HomeUiState`
- **What it does:** Fetches YouTube home/trending content via `YouTubeEngine`. Displays horizontal rows of video cards (like YouTube mobile).
- **Layout:** LazyColumn of horizontal LazyRows. Each row has a title ("Trending", "Music", "Gaming", etc.) and horizontal scrolling video cards.
- **Pull-to-refresh:** Swipe down refreshes all rows.
- **Video card tap:** Navigates to player screen.
- **Video card long-press:** Bottom sheet with "Add to playlist", "Open channel", "Share".

```kotlin
data class HomeUiState(
    val sections: List<HomeSection> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class HomeSection(
    val title: String,
    val videos: List<Video>
)
```

### 6.2 Player Screen
- **Route:** `player/{videoId}`
- **ViewModel:** `PlayerViewModel` → `PlayerUiState`
- **What it does:** Plays a video with full controls, SponsorBlock integration, and related videos below.
- **Layout (Nuvio-inspired):**
  - `PlayerSurface` (Media3 Compose) for video rendering
  - Custom Compose controls overlay (play/pause, seek, speed, subtitles)
  - Gesture handling (brightness/volume by vertical drag, seek by horizontal drag)
  - Below player: video title, channel info, action buttons (Add to playlist, Share, Open channel)
  - Up Next list (LazyColumn of related videos)
- **Controls:** Tap to show/hide. Auto-hide after 4 seconds.
- **Seekbar:** Custom with SponsorBlock colored segments.
- **Speed control:** 0.25x to 3.0x in bottom sheet.
- **Subtitles:** Toggle on/off, select track.
- **PiP:** Auto-enter PiP on back press or Home.
- **Background play:** Continues audio via Media3 Session foreground service.
- **Mini-player:** When navigating away, a mini-player bar appears in the browse/library screen.

```kotlin
data class PlayerUiState(
    val video: Video? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isSubtitleEnabled: Boolean = false,
    val sponsorBlockSegments: List<SponsorSegment> = emptyList(),
    val relatedVideos: List<Video> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
```

### 6.3 Search Screen
- **Route:** `search`
- **ViewModel:** `SearchViewModel` → `SearchUiState`
- **What it does:** Search YouTube with autocomplete suggestions.
- **Layout:** Search bar at top. Below: either autocomplete suggestions or search results grid.
- **Results:** Same video card component as home screen. 2-column grid.
- **Autocomplete:** Debounced (300ms) text input → suggestions list.

```kotlin
data class SearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val results: List<Video> = emptyList(),
    val isSearching: Boolean = false,
    val showSuggestions: Boolean = true
)
```

### 6.4 Channel Screen
- **Route:** `channel/{channelId}`
- **ViewModel:** `ChannelViewModel` → `ChannelUiState`
- **What it does:** Shows channel info and videos.
- **Layout:** Channel header (avatar, name, subscriber count, subscribe button). Below: horizontal rows of videos (or vertical grid for uploads).
- **Subscribe button:** Toggles local subscription (stored in Room). Changes appearance when subscribed.
- **Pagination:** Infinite scroll loads more videos.

```kotlin
data class ChannelUiState(
    val channel: ChannelInfo? = null,
    val videos: List<Video> = emptyList(),
    val isSubscribed: Boolean = false,
    val isLoading: Boolean = true
)
```

### 6.5 Library Screen
- **Route:** `library`
- **ViewModel:** `LibraryViewModel` → `LibraryUiState`
- **What it does:** Shows local watch history, playlists, and subscriptions.
- **Layout:** Top tabs: History | Playlists | Subscriptions
  - **History:** Vertical list of recently watched videos (from Room). Shows watch progress bar on thumbnail.
  - **Playlists:** Grid of user-created playlists. Tap to open playlist detail. "+" button to create new playlist.
  - **Subscriptions:** Grid of locally subscribed channels. Tap to open channel page.

```kotlin
data class LibraryUiState(
    val activeTab: LibraryTab = LibraryTab.HISTORY,
    val history: List<WatchHistoryEntry> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val subscriptions: List<LocalSubscription> = emptyList()
)

enum class LibraryTab { HISTORY, PLAYLISTS, SUBSCRIPTIONS }
```

### 6.6 Settings Screen
- **Route:** `settings`
- **ViewModel:** `SettingsViewModel` → `SettingsUiState`
- **What it does:** App settings and SponsorBlock configuration.
- **Sections:**
  - **Player:** Default speed, default quality, resume playback
  - **SponsorBlock:** Enable/disable, per-category actions (skip/toast/dialog), excluded channels
  - **Appearance:** Theme (dark/light/AMOLED)
  - **Data:** Clear history, clear playlists, export/import

### 6.7 Mini Player
- **Persistent bar** shown above the bottom navigation when playback is active in background.
- Shows: thumbnail, title, play/pause button, close button.
- Tap opens full player screen.
- Appears/disappears with animation.

---

## 7. Data Layer

### 7.1 Room Database Schema

```kotlin
@Entity(tableName = "watch_history")
data class WatchHistoryEntry(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val positionMs: Long,         // Where user stopped watching
    val speed: Float,             // Playback speed at time of save
    val timestamp: Long           // When it was last watched
)

@Entity(tableName = "playlists")
data class LocalPlaylist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val videoCount: Int = 0       // Denormalized for display
)

@Entity(
    tableName = "playlist_videos",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [ForeignKey(
        entity = LocalPlaylist::class,
        parentColumns = ["id"],
        childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("playlistId")]
)
data class PlaylistVideo(
    val playlistId: Long,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val position: Int              // Order within playlist
)

@Entity(
    tableName = "subscriptions",
    indices = [Index("channelId", unique = true)]
)
data class LocalSubscription(
    @PrimaryKey val channelId: String,
    val channelName: String,
    val thumbnailUrl: String,
    val subscribedAt: Long
)
```

### 7.2 YouTubeEngine (MediaServiceCore Wrapper)

This is the critical bridge between MediaServiceCore's RxJava-based API and our Kotlin/Coroutines architecture.

```kotlin
// Pseudocode - the actual implementation bridges RxJava Observables to Kotlin Flows
class YouTubeEngine @Inject constructor(
    private val context: Context
) {
    init {
        // MUST be called first - initializes auth tokens, language, context
        YouTubeInitializer.init(context)
    }

    private val contentService get() = YouTubeServiceManager.instance().contentService
    private val mediaItemService get() = YouTubeServiceManager.instance().mediaItemService

    // Browse
    fun getHome(): Flow<List<Video>>          // contentService.homeObserve()
    fun getTrending(): Flow<List<Video>>      // contentService.trendingObserve()
    fun getMusic(): Flow<List<Video>>         // contentService.musicObserve()

    // Search
    fun search(query: String): Flow<List<Video>>  // contentService.getSearchObserve()
    fun getSearchSuggestions(query: String): Flow<List<String>>

    // Video info
    fun getStreamInfo(videoId: String): Flow<StreamInfo>  // mediaItemService.getFormatInfoObserve()
    fun getVideoMetadata(videoId: String): Flow<Video>    // mediaItemService.getMetadataObserve()

    // Channel
    fun getChannel(channelId: String): Flow<ChannelInfo>
    fun getChannelVideos(channelId: String): Flow<List<Video>>

    // SponsorBlock
    fun getSponsorSegments(videoId: String): Flow<List<SponsorSegment>>
        // mediaItemService.getSponsorSegmentsObserve()

    // History (cloud - skipped when not signed in)
    // Not needed - we only use local Room history
}
```

### 7.3 RxJava → Coroutines Bridge Pattern

All MediaServiceCore calls return `io.reactivex.Observable<T>`. Bridge like this:

```kotlin
import io.reactivex.rxjava3.kotlin.Flowable
import kotlinx.coroutines.rx3.await

// Option 1: Using rxjava3 coroutine adapter
suspend fun <T> Observable<T>.awaitFirst(): T =
    firstOrError().await()

// Option 2: Convert to Flow
fun <T> Observable<T>.asFlow(): Flow<T> = flow {
    val subscription = subscribe { emit(it) }
    awaitCancellableCancellation { subscription.dispose() }
}

// Usage in YouTubeEngine:
fun getHome(): Flow<List<Video>> = flow {
    val groups = contentService.homeObserve.awaitFirst()
    emit(groups.flatMap { it.map { mediaItem -> mediaItem.toVideo() } })
}
```

---

## 8. Player Architecture (Nuvio-Inspired)

### 8.1 PlayerEngineController

Clean abstraction over Media3 ExoPlayer. The player UI never touches ExoPlayer directly.

```kotlin
interface PlayerEngineController {
    // Playback
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekBy(offsetMs: Long)
    fun setPlaybackSpeed(speed: Float)

    // Source
    fun openDash(manifestUrl: String)
    fun openHls(manifestUrl: String)
    fun openSabr(streamInfo: StreamInfo)

    // Tracks
    fun getAudioTracks(): List<AudioTrack>
    fun getSubtitleTracks(): List<SubtitleTrack>
    fun selectAudioTrack(index: Int)
    fun selectSubtitleTrack(index: Int)

    // State (read via Flow)
    val playbackState: StateFlow<PlayerPlaybackSnapshot>
}
```

### 8.2 PlayerPlaybackSnapshot

Immutable snapshot polled every 250ms. UI collects this as a StateFlow.

```kotlin
data class PlayerPlaybackSnapshot(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isBuffering: Boolean = false,
    val audioTrackCount: Int = 0,
    val subtitleTrackCount: Int = 0
)
```

### 8.3 ExoPlayer Initialization (in PlayerViewModel or PlayerEngineController)

```kotlin
// Based on Nuvio's PlayerEngine.android.kt pattern
val exoPlayer = ExoPlayer.Builder(context)
    .setRenderersFactory(DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER))
    .setTrackSelector(DefaultTrackSelector(context)
        .apply { setParameters(buildUponParameters().setMaxVideoSizeSd())) })
    .setLoadControl(DefaultLoadControl.Builder()
        .setBufferDurationsMs(10_000, 30_000, 500, 10_000) // smaller for YouTube
        .build())
    .build()
```

### 8.4 SponsorBlock Integration

```kotlin
class SponsorBlockService @Inject constructor(
    private val youTubeEngine: YouTubeEngine,
    private val preferences: PlayerPreferences
) {
    // Fetch segments when video loads
    fun getSegments(videoId: String): Flow<List<SponsorSegment>>

    // Called every second by player, checks if current position is in a segment
    fun checkForSkip(positionMs: Long, segments: List<SponsorSegment>): SkipAction?

    data class SkipAction(
        val segment: SponsorSegment,
        val seekToMs: Long
    )
}
```

### 8.5 Media Session + Background Playback

Using Media3 Session (not legacy MediaSession like Nuvio):

```kotlin
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = // ExoPlayer instance (injected or from ViewModel)
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}
```

---

## 9. Navigation

### Routes

```kotlin
@Serializable
sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data class Player(val videoId: String) : Route
    @Serializable data object Search : Route
    @Serializable data class Channel(val channelId: String) : Route
    @Serializable data object Library : Route
    @Serializable data object Settings : Route
}
```

### NavHost

```kotlin
NavHost(navController = navController, startDestination = Route.Home) {
    composable<Route.Home> { HomeScreen(...) }
    composable<Route.Player> { PlayerScreen(...) }
    composable<Route.Search> { SearchScreen(...) }
    composable<Route.Channel> { ChannelScreen(...) }
    composable<Route.Library> { LibraryScreen(...) }
    composable<Route.Settings> { SettingsScreen(...) }
}
```

### Bottom Navigation

Four tabs: Home | Search | Library | Settings

---

## 10. Build System

### Version Catalog (`gradle/libs.versions.toml`)

The block below is the *original target*. The **actual** catalog in the repo uses the conservative versions from §4 (Kotlin 2.2.21, AGP 8.7.3, KSP 2.2.21-2.0.5, etc.) — check the real file, not this snippet. Kept here for the shape/structure reference only.

```toml
[versions]
kotlin = "2.3.0"
agp = "9.2.0"
compose-bom = "2025.05.01"
material3 = "1.11.0-alpha07"
media3 = "1.8.0"
hilt = "2.54"
room = "2.7.0"
navigation = "2.9.0"
lifecycle = "2.11.0-beta01"
coil = "3.5.0"
ksp = "2.3.0-1.0.31"
coroutines = "1.10.1"
datastore = "1.1.7"
serialization = "1.8.1"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-runtime = { group = "androidx.compose.runtime", name = "runtime" }
compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
compose-activity = { group = "androidx.activity", name = "activity-compose", version = "1.10.1" }

# Media3
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-exoplayer-dash = { group = "androidx.media3", name = "media3-exoplayer-dash", version.ref = "media3" }
media3-exoplayer-hls = { group = "androidx.media3", name = "media3-exoplayer-hls", version.ref = "media3" }
media3-ui-compose = { group = "androidx.media3", name = "media3-ui-compose", version.ref = "media3" }
media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
media3-datasource-okhttp = { group = "androidx.media3", name = "media3-datasource-okhttp", version.ref = "media3" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version = "1.2.0" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Navigation
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# Lifecycle
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }

# Coil
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }

# DataStore
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# KotlinX
serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-rx3 = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-rx3", version.ref = "coroutines" }

# AndroidX Core
core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.16.0" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
```

### Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
```

### `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolution {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// PhoneTube submodules (YouTube API engine)
include(":sharedutils")
include(":mediaserviceinterfaces")
include(":youtubeapi")

// Our app
include(":app")
```

**NOTE:** The exact include paths for MediaServiceCore and SharedModules depend on their `core_settings.gradle` files. We need to read those files to get the exact module names. They may need to be included differently. The key point is that `:youtubeapi` and `:mediaserviceinterfaces` from MediaServiceCore and `:sharedutils` from SharedModules must be included as local Gradle modules.

### 10.1 Getting to Kotlin 2.3 / AGP 9.2 (later)

We shipped on Kotlin 2.2.21 + AGP 8.7.3 + KSP1 on purpose — it's the combo that actually compiles today with MediaServiceCore's old submodules in the tree. Kotlin 2.3 / AGP 9.2 is still the target, just not worth the yak-shave until the app itself is further along. When we do the bump, do it as its own branch/PR (it touches the whole build), not mixed in with feature work. Rough order:

1. **Clear the KSP2 blocker first.** The whole reason we're on KSP1 is `ksp.useKSP2=false` (Room's suspend DAOs crash KSP2 with "unexpected jvm signature V"). Before bumping Kotlin, flip that flag back on with the *current* versions and confirm Room builds. If it still crashes, move Room up (2.7.x+) first and retest. Don't bump Kotlin until KSP2 is green — otherwise you can't tell which change broke it.
2. **Room → 2.7.x** (or whatever the latest is at bump time). 2.7 has real KSP2 support; that's what unlocks dropping the flag.
3. **Hilt → 2.5x** that's built against the target Kotlin. Hilt lags Kotlin releases, so this is usually the gate on how new Kotlin can go — check Hilt's release notes for the max supported Kotlin before picking the Kotlin version.
4. **Kotlin → 2.3.0 + KSP → 2.3.0-x.x.x.** KSP version must match the Kotlin version exactly (first part).
5. **AGP → 9.2.0.** This is the big one:
   - AGP 9 wants JDK 17+ (we're already on 17 in CI, good).
   - `compileSdk`/`targetSdk` → 36, `buildToolsVersion` gets dropped (AGP picks it).
   - AGP 9 is stricter about namespaces — the reflection hack in root `build.gradle.kts` that injects namespaces into the MediaServiceCore/SharedModules submodules may break or become unnecessary. Watch that closely; ideally we patch the submodule build files to declare their own namespace and delete the hack.
   - `com.android.library` alias needs to exist in the catalog (it does) since submodules use it.
6. **Gradle wrapper** to whatever AGP 9.2 requires (8.13+ range).
7. **Compose BOM + Material3** forward to match the new Kotlin compose-compiler.
8. Bump one layer at a time, run CI after each. Resist the urge to bump everything and debug the pile-up.

Reality check: the pacing item is almost always Hilt + Room vs Kotlin. If Hilt doesn't support Kotlin 2.3 yet at bump time, stay on 2.2.x and just do the AGP 9 / SDK 36 half — those are more independent.

---

## 11. Implementation Plan -- Step by Step

### Step 1: Gradle Setup
- Create `gradle/libs.versions.toml` (version catalog)
- Replace root `build.gradle` with `build.gradle.kts`
- Replace `settings.gradle` with `settings.gradle.kts`
- Update `gradle.properties`
- Update `gradle-wrapper.properties` to Gradle 8.11+
- Create `app/build.gradle.kts`
- Remove old modules from includes (keep only MediaServiceCore + SharedModules)

### Step 2: App Shell + YouTubeInitializer
- Create `app/src/main/AndroidManifest.xml`
- Create `PhoneTubeApp.kt` (@HiltAndroidApp)
- Create `MainActivity.kt` (single activity with Compose setContent)
- Create `core/engine/YouTubeInitializer.kt` (calls GlobalPreferences.instance(context))
- Create `ui/theme/` (Theme.kt, Color.kt, Type.kt)
- Create `ui/navigation/AppNavigation.kt` (NavHost with placeholder screens)
- **Goal:** App compiles and shows a blank screen with navigation set up

### Step 3: YouTubeEngine Wrapper
- Create `core/engine/YouTubeEngine.kt` with all method stubs
- Create `core/engine/model/` data classes (Video, StreamInfo, ChannelInfo, etc.)
- Implement at least `getHome()` and `search()` methods
- Create `core/di/EngineModule.kt` (Hilt module providing YouTubeEngine)
- **Goal:** Can call YouTubeEngine from a ViewModel and get data back

### Step 4: Room Database
- Create `core/database/AppDatabase.kt`
- Create all entity classes (WatchHistoryEntry, LocalPlaylist, PlaylistVideo, LocalSubscription)
- Create all DAOs (HistoryDao, PlaylistDao, SubscriptionDao)
- Create `core/di/DatabaseModule.kt`
- **Goal:** Room compiles and DAOs are injectable

### Step 5: Home Screen
- Create `VideoCard.kt` composable (reusable card component)
- Create `HomeViewModel.kt` + `HomeScreen.kt`
- Wire up to YouTubeEngine.getHome()
- Create horizontal row layout with video cards
- **Goal:** YouTube home feed displays in a scrollable list of horizontal rows

### Step 6: Player (Core)
- Create `player/PlayerEngineController.kt`
- Create `player/PlayerPlaybackSnapshot.kt`
- Create `PlayerViewModel.kt` + `PlayerScreen.kt`
- Create `PlayerControls.kt` (custom Compose overlay)
- Create `player/service/PlaybackService.kt` (Media3 Session)
- Wire up to YouTubeEngine.getStreamInfo()
- **Goal:** Can tap a video and play it with basic controls

### Step 7: Player (SponsorBlock)
- Create `player/SponsorBlockService.kt`
- Add SponsorBlock colored segments to seekbar
- Add auto-skip logic
- **Goal:** SponsorBlock works during playback

### Step 8: Search Screen
- Create `SearchViewModel.kt` + `SearchScreen.kt`
- Wire up to YouTubeEngine.search()
- Add autocomplete suggestions
- **Goal:** Can search YouTube and see results

### Step 9: Channel Screen
- Create `ChannelViewModel.kt` + `ChannelScreen.kt`
- Wire up to YouTubeEngine.getChannel()
- Add local subscription toggle
- **Goal:** Can view channel pages and subscribe locally

### Step 10: Library Screen
- Create `LibraryViewModel.kt` + `LibraryScreen.kt`
- Wire up to Room DAOs
- Implement History, Playlists, and Subscriptions tabs
- Add create playlist dialog
- **Goal:** Can view history, create playlists, see subscriptions

### Step 11: Settings Screen
- Create `SettingsViewModel.kt` + `SettingsScreen.kt`
- Wire up to PlayerPreferences (DataStore)
- Add SponsorBlock config, player defaults, theme selection
- Add option to choose default feed (Home, Trending, etc.) — used on app launch
- Add landscape lock toggle for player (force landscape during playback)
- **Goal:** Can configure app settings

### Step 12: Mini Player
- Create `components/MiniPlayer.kt`
- Show when playback is active in background
- Add play/pause, close, tap-to-open-full-player
- **Goal:** Persistent mini-player bar works

### Step 13: Polish + Release
- Clean up all TODOs
- Enable R8 minification and resource shrinking in `app/build.gradle.kts` (`isMinifyEnabled = true`, `isShrinkResources = true`)
- Test all screens flow
- Final commit

---

## 12. Reference Files

### From SmartTube (MediaServiceCore integration)

| File | What to read | Why |
|------|-------------|-----|
| `common/.../misc/MediaServiceManager.java` | Lines 100-400 | Shows how to call all MediaServiceCore APIs. This is the pattern we replicate in YouTubeEngine.kt |
| `common/.../presenters/BrowsePresenter.java` | Lines 186-233 | Shows all content section types and their API calls |
| `common/.../playback/controllers/VideoLoaderController.java` | Lines 278-364 | Shows how stream info is fetched and processed (DASH/HLS/SABR decision tree) |
| `common/.../playback/controllers/SponsorBlockController.java` | Lines 170-310 | Shows SponsorBlock segment fetching and auto-skip logic |
| `common/.../prefs/SponsorBlockData.java` | Full file | Shows SponsorBlock preferences structure |
| `common/.../app/models/data/Video.java` | Lines 1-150 | Shows how MediaItem maps to a Video data class |
| `common/src/main/java/.../playback/service/VideoStateService.java` | Full file | Shows local history storage pattern (we replace with Room) |
| `settings.gradle` | Full file | Shows how MediaServiceCore and SharedModules are included |

### From Nuvio Mobile (UI/Player patterns)

| File | What to read | Why |
|------|-------------|-----|
| `composeApp/.../features/player/PlayerEngineController.kt` | Interface definition | Clean player abstraction pattern |
| `composeApp/.../features/player/PlayerScreenRuntime.kt` | Full file | Player state management pattern |
| `composeApp/.../features/player/PlayerScreenContent.kt` | Full file | Player composable structure |
| `composeApp/.../features/player/PlayerControlsShell.kt` | Full file | Custom Compose player controls |
| `composeApp/.../features/player/PlayerPlaybackOverlays.kt` | Full file | Loading/error/gesture overlays |
| `composeApp/.../features/player/PlayerPictureInPictureManager.kt` | Full file | PiP implementation |
| `composeApp/.../features/player/engine/PlayerEngine.android.kt` | Full file | ExoPlayer initialization pattern |
| `composeApp/.../features/player/gesture/` | All files | Gesture handling (brightness/volume/seek) |
| `composeApp/src/androidMain/.../PlayerNowPlayingController.kt` | Full file | Media notification pattern |
| `gradle/libs.versions.toml` | Full file | Dependency versions reference |
| `composeApp/build.gradle.kts` | Full file | Build config reference |

### Key MediaServiceCore Interfaces (from GitHub)

| Interface | Methods we use |
|-----------|---------------|
| `ServiceManager` | Root factory - access all services |
| `ContentService` | `getHomeObserve()`, `getTrendingObserve()`, `getSearchObserve()`, `getChannelObserve()`, `continueGroupObserve()` |
| `MediaItemService` | `getFormatInfoObserve()`, `getMetadataObserve()`, `getSponsorSegmentsObserve()` |
| `SignInService` | `isSigned()` (check only - we don't sign in) |

---

## 13. Key Constraints

1. **MediaServiceCore uses RxJava 2.** We bridge to Coroutines/Flow at the YouTubeEngine boundary. Never expose RxJava types to the rest of the app.

2. **GlobalPreferences.instance(context) MUST be called first.** Before ANY MediaServiceCore API call. This initializes auth tokens, language, and context. See `YouTubeInitializer.kt`.

3. **MediaServiceCore uses the old ExoPlayer 2.10.6 package names internally.** But we use Media3 for OUR player. The stream info (URLs, format data) is passed as plain data objects - there's no ExoPlayer coupling between MediaServiceCore and our player.

4. **SABR protocol.** YouTube is progressively rolling out SABR as the primary streaming protocol. For now, most videos still serve DASH. The `sabr-exoplayer` community library handles SABR with Media3. If it's not ready when we need it, we can fall back to DASH-only for most content.

5. **No Android SDK locally.** Code-only commits. No builds, no APKs. Test on device manually or via CI.

6. **SharedModules constants.gradle** defines version variables that MediaServiceCore modules reference. We need to include SharedModules and apply its `core_settings.gradle` for the build to work.

7. **MediaServiceCore's `core_settings.gradle`** defines the `:mediaserviceinterfaces` and `:youtubeapi` modules. We need to read this file to include them correctly.

---

## 14. Git / Commit Conventions

- **Author:** `roundsalmon4 <209016228+RoundSalmon4@users.noreply.github.com>`
- **Never commit with user's real name or personal info**
- **All code comments, commit messages, changelog entries** should read like a solo developer wrote them, not AI
  - Bad: "Refactored the VideoCardViewHolder to utilize a shared adapter pattern for improved code maintainability"
  - Good: "pulled out the video card view holder into a shared adapter. was tired of copy-pasting it between 5 fragments"
- **Commit messages** use prefix: `new-ui:` for this branch
- **Always provide commit summary and get go-ahead before committing**
- **Push via PAT** (configured in remote `fork`)
- **Do not build locally** -- no Android SDK available

package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.vkay94.dtpv.DoubleTapPlayerAdapter;
import com.github.vkay94.dtpv.DoubleTapPlayerView;
import com.github.vkay94.dtpv.youtube.YouTubeOverlay;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.CustomOverridesRenderersFactory;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector.RestoreTrackSelector;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.BackboneQueueNavigator;

import java.io.InputStream;
import java.util.List;

public class PhonePlaybackFragment extends Fragment implements PlaybackView {
    private static final String TAG = PhonePlaybackFragment.class.getSimpleName();

    private SimpleExoPlayer mPlayer;
    private PlaybackPresenter mPlaybackPresenter;
    private ExoPlayerController mExoPlayerController;
    private ExoPlayerInitializer mPlayerInitializer;

    private PlayerView mPlayerView;
    private TextView mVideoTitle;
    private SubtitleView mSubtitleView;
    private View mBtnBackPersistent;

    private DoubleTapPlayerAdapter mDoubleTapPlayerAdapter;
    private YouTubeOverlay mYouTubeOverlay;
    private MediaSessionCompat mMediaSession;
    private MediaSessionConnector mMediaSessionConnector;

    private boolean mIsEngineBlocked;
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    // --- Lifecycle ---

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_phone_playback, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getContext() == null) {
            return;
        }

        mPlayerInitializer = new ExoPlayerInitializer(getContext());
        mPlaybackPresenter = PlaybackPresenter.instance(getContext());
        mPlaybackPresenter.setView(this);
        mExoPlayerController = new ExoPlayerController(getContext(), mPlaybackPresenter);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPlayerView = view.findViewById(R.id.exo_player_view);
        mVideoTitle = view.findViewById(R.id.exo_title);
        mSubtitleView = view.findViewById(R.id.subtitle_view);
        mYouTubeOverlay = view.findViewById(R.id.youtube_overlay);
        mBtnBackPersistent = view.findViewById(R.id.btn_back_persistent);

        mBtnBackPersistent.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        ImageButton btnMore = view.findViewById(R.id.exo_more);
        if (btnMore != null) {
            btnMore.setOnClickListener(v -> showPlayerMenu());
        }

        setupDoubleTap();

        mPlaybackPresenter.onViewInitialized();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (VERSION.SDK_INT > 23) {
            initializePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (VERSION.SDK_INT > 23) {
            maybeReleasePlayer();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (VERSION.SDK_INT <= 23 || mPlayer == null) {
            initializePlayer();
        }
        mPlaybackPresenter.onViewResumed();
        blockEngine(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        mPlaybackPresenter.onViewPaused();
        if (VERSION.SDK_INT <= 23) {
            maybeReleasePlayer();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mUiHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releasePlayer();
        if (mPlaybackPresenter.getView() == this) {
            mPlaybackPresenter.onViewDestroyed();
        }
    }

    // --- Player creation / release ---

    private void initializePlayer() {
        if (mPlayer != null) {
            return;
        }

        createPlayer();
        createMediaSession();
        setupDoubleTap();

        mPlaybackPresenter.setView(this);
        mPlaybackPresenter.onEngineInitialized();
    }

    private void createPlayer() {
        DefaultTrackSelector trackSelector = new RestoreTrackSelector(new AdaptiveTrackSelection.Factory());
        mExoPlayerController.setTrackSelector(trackSelector);

        DefaultRenderersFactory renderersFactory = new CustomOverridesRenderersFactory(getContext());
        mPlayer = mPlayerInitializer.createPlayer(getContext(), renderersFactory, trackSelector);
        mExoPlayerController.setPlayer(mPlayer);

        mPlayerView.setPlayer(mPlayer);

        mPlayer.addListener(new Player.EventListener() {
            @Override
            public void onPlaybackParametersChanged(com.google.android.exoplayer2.PlaybackParameters playbackParameters) {
                // no-op
            }
        });
    }

    private void createMediaSession() {
        if (VERSION.SDK_INT <= 19 || getContext() == null) {
            return;
        }

        boolean disableNotifications = PlayerTweaksData.instance(getContext()).isPlaybackNotificationsDisabled();
        mMediaSession = new MediaSessionCompat(getContext().getApplicationContext(), getContext().getPackageName());
        mMediaSession.setActive(!disableNotifications);
        mMediaSessionConnector = new MediaSessionConnector(mMediaSession);

        try {
            mMediaSessionConnector.setPlayer(mPlayer);
        } catch (NoSuchMethodError e) {
            return;
        }

        mMediaSessionConnector.setMediaMetadataProvider(player -> {
            if (getVideo() == null) {
                return null;
            }

            MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
            builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, getVideo().getTitleFull());
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, getVideo().getTitleFull());
            builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getVideo().getAuthor());
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, Helpers.toString(getVideo().getSecondTitleFull()));
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, getVideo().getCardImageUrl());
            builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDurationMs());
            return builder.build();
        });

        mMediaSessionConnector.setQueueNavigator(new BackboneQueueNavigator() {
            @Override
            public long getSupportedQueueNavigatorActions(Player player) {
                return PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
            }

            @Override
            public void onSkipToPrevious(Player player, ControlDispatcher controlDispatcher) {
                mPlaybackPresenter.onPreviousClicked();
            }

            @Override
            public void onSkipToNext(Player player, ControlDispatcher controlDispatcher) {
                mPlaybackPresenter.onNextClicked();
            }
        });

        mMediaSessionConnector.setControlDispatcher(new DefaultControlDispatcher() {
            @Override
            public boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady) {
                if (System.currentTimeMillis() - PlayerData.instance(getContext()).getAfrSwitchTimeMs() < 5_000) {
                    return false;
                }
                return super.dispatchSetPlayWhenReady(player, playWhenReady);
            }
        });
    }

    private void maybeReleasePlayer() {
        if (isEngineBlocked()) {
            return;
        }
        releasePlayer();
    }

    private void releasePlayer() {
        if (mPlayer != null) {
            mPlaybackPresenter.onEngineReleased();
            destroyPlayerObjects();
        }
    }

    private void destroyPlayerObjects() {
        if (mMediaSessionConnector != null) {
            mMediaSessionConnector.setPlayer(null);
            mMediaSessionConnector.setControlDispatcher(null);
            mMediaSessionConnector.setMediaMetadataProvider(null);
            mMediaSessionConnector.setQueueNavigator(null);
            mMediaSessionConnector = null;
        }
        if (mMediaSession != null) {
            mMediaSession.setActive(false);
            mMediaSession.release();
            mMediaSession = null;
        }
        mPlayerInitializer.release();
        mExoPlayerController.release();
        if (mDoubleTapPlayerAdapter != null) {
            mDoubleTapPlayerAdapter.controller(null);
            mDoubleTapPlayerAdapter.onSingleTap(null);
        }
        if (mYouTubeOverlay != null) {
            mYouTubeOverlay.player(null).playerView(null).performListener(null);
        }
        mDoubleTapPlayerAdapter = null;
        mPlayer = null;
    }

    // --- Double-tap ---

    private void setupDoubleTap() {
        if (getContext() == null || getView() == null || !Helpers.isTouchSupported(getContext())) {
            return;
        }

        if (mYouTubeOverlay == null) {
            mYouTubeOverlay = getView().findViewById(R.id.youtube_overlay);
        }
        if (mYouTubeOverlay == null) {
            return;
        }

        mDoubleTapPlayerAdapter = new DoubleTapPlayerAdapter(getView());
        mDoubleTapPlayerAdapter.onSingleTap(v -> {
            if (mPlayerView.isControllerFullyVisible()) {
                mPlayerView.hideController();
                mPlaybackPresenter.onControlsShown(false);
            } else {
                mPlayerView.showController();
                mPlaybackPresenter.onControlsShown(true);
            }
        });
        mDoubleTapPlayerAdapter.controller(mYouTubeOverlay);

        mYouTubeOverlay
                .player(mPlayer)
                .playerView(mDoubleTapPlayerAdapter)
                .seekSeconds(PlayerData.instance(getContext()).getSeekIncrementMs() / 1_000)
                .performListener(new YouTubeOverlay.PerformListener() {
                    @Override
                    public void onAnimationStart() {
                        if (mYouTubeOverlay != null) {
                            mYouTubeOverlay.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationEnd() {
                        if (mYouTubeOverlay != null) {
                            mYouTubeOverlay.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public Boolean shouldForward(@NonNull Player player, @NonNull DoubleTapPlayerView playerView, float posX) {
                        if (player.getPlaybackState() == PlaybackStateCompat.STATE_ERROR ||
                                player.getPlaybackState() == PlaybackStateCompat.STATE_NONE ||
                                player.getPlaybackState() == PlaybackStateCompat.STATE_STOPPED) {
                            playerView.cancelInDoubleTapMode();
                            return false;
                        }
                        if (player.getCurrentPosition() > 500 && posX < playerView.getPlayerWidth() * 0.35) {
                            return false;
                        }
                        if (player.getCurrentPosition() < player.getDuration() && posX > playerView.getPlayerWidth() * 0.65) {
                            return true;
                        }
                        return false;
                    }
                });
    }

    // --- UI helpers ---

    private void showPlayerMenu() {
        if (getContext() == null || getActivity() == null || getActivity().isFinishing()) {
            return;
        }

        String[] items = {
                getString(R.string.action_video_speed),
                getString(R.string.action_subtitles),
                getString(R.string.action_video_zoom),
                getString(R.string.action_pip),
                getString(R.string.share_link),
                getString(R.string.action_video_info),
                getString(R.string.action_playlist_add),
                getString(R.string.action_playback_queue)
        };

        int[] actions = {
                R.id.action_video_speed,
                R.id.lb_control_closed_captioning,
                R.id.action_video_zoom,
                R.id.action_pip,
                R.id.action_share,
                R.id.action_info,
                R.id.action_playlist_add,
                R.id.action_playback_queue
        };

        PhonePlayerMenuBottomSheet sheet = PhonePlayerMenuBottomSheet.newInstance(items, actions);
        sheet.setOnMenuItemClickListener(actionId -> {
            if (mPlaybackPresenter != null) {
                mPlaybackPresenter.onButtonClicked(actionId, PlayerUI.BUTTON_OFF);
            }
        });
        sheet.show(getChildFragmentManager(), "player_menu");
    }

    // Touch forwarding for double-tap
    public void onDispatchTouchEvent(MotionEvent event) {
        if (mDoubleTapPlayerAdapter != null) {
            mDoubleTapPlayerAdapter.onTouchEvent(event);
        }
    }

    // --- PlaybackView: PlayerManager ---

    @Override
    public void setVideo(Video video) {
        mExoPlayerController.setVideo(video);
        if (video != null && mVideoTitle != null) {
            mVideoTitle.setText(video.getTitleFull());
        }
    }

    @Override
    public Video getVideo() {
        return mExoPlayerController != null ? mExoPlayerController.getVideo() : null;
    }

    @Override
    public void finish() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void finishReally() {
        if (getActivity() instanceof PhonePlaybackActivity) {
            ((PhonePlaybackActivity) getActivity()).finishReally();
        } else if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void showBackground(String url) {
        // No-op for phone
    }

    @Override
    public void showBackgroundColor(int colorResId) {
        // No-op for phone
    }

    @Override
    public void resetPlayerState() {
        mExoPlayerController.resetPlayerState();
    }

    @Override
    public boolean isEmbed() {
        return false;
    }

    // --- PlaybackView: PlayerEngine ---

    @Override
    public void openSabr(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.openSabr(formatInfo);
    }

    @Override
    public void openDash(MediaItemFormatInfo formatInfo) {
        mExoPlayerController.openDash(formatInfo);
    }

    @Override
    public void openDash(InputStream dashManifest) {
        mExoPlayerController.openDash(dashManifest);
    }

    @Override
    public void openDashUrl(String dashManifestUrl) {
        mExoPlayerController.openDashUrl(dashManifestUrl);
    }

    @Override
    public void openHlsUrl(String hlsPlaylistUrl) {
        mExoPlayerController.openHlsUrl(hlsPlaylistUrl);
    }

    @Override
    public void openUrlList(List<String> urlList) {
        mExoPlayerController.openUrlList(urlList);
    }

    @Override
    public void openMerged(MediaItemFormatInfo formatInfo, String hlsPlaylistUrl) {
        mExoPlayerController.openMerged(formatInfo, hlsPlaylistUrl);
    }

    @Override
    public void openMerged(InputStream dashManifest, String hlsPlaylistUrl) {
        mExoPlayerController.openMerged(dashManifest, hlsPlaylistUrl);
    }

    @Override
    public long getPositionMs() {
        return mExoPlayerController.getPositionMs();
    }

    @Override
    public void setPositionMs(long positionMs) {
        mExoPlayerController.setPositionMs(positionMs);
    }

    @Override
    public long getDurationMs() {
        long durationMs = mExoPlayerController.getDurationMs();
        Video video = getVideo();
        long liveDurationMs = video != null ? video.getLiveDurationMs() : 0;
        if (durationMs > Video.MAX_LIVE_DURATION_MS && liveDurationMs != 0) {
            durationMs = liveDurationMs;
        }
        return durationMs;
    }

    @Override
    public void setPlayWhenReady(boolean play) {
        mExoPlayerController.setPlayWhenReady(play);
    }

    @Override
    public boolean getPlayWhenReady() {
        return mExoPlayerController.getPlayWhenReady();
    }

    @Override
    public boolean isPlaying() {
        return mExoPlayerController.isPlaying();
    }

    @Override
    public boolean isLoading() {
        return mExoPlayerController.isLoading();
    }

    @Override
    public List<FormatItem> getVideoFormats() {
        return mExoPlayerController.getVideoFormats();
    }

    @Override
    public List<FormatItem> getAudioFormats() {
        return mExoPlayerController.getAudioFormats();
    }

    @Override
    public List<FormatItem> getSubtitleFormats() {
        return mExoPlayerController.getSubtitleFormats();
    }

    @Override
    public void setFormat(FormatItem formatItem) {
        mExoPlayerController.selectFormat(formatItem);
    }

    @Override
    public FormatItem getVideoFormat() {
        return mExoPlayerController.getVideoFormat();
    }

    @Override
    public FormatItem getAudioFormat() {
        return mExoPlayerController.getAudioFormat();
    }

    @Override
    public FormatItem getSubtitleFormat() {
        return mExoPlayerController.getSubtitleFormat();
    }

    @Override
    public boolean isEngineInitialized() {
        return mPlayer != null;
    }

    @Override
    public void restartEngine() {
        if (getContext() == null) return;
        releasePlayer();
        initializePlayer();
    }

    @Override
    public void reloadPlayback() {
        if (mPlayer != null) {
            mPlaybackPresenter.onEngineReleased();
            mPlaybackPresenter.onEngineInitialized();
        }
    }

    @Override
    public void blockEngine(boolean block) {
        mIsEngineBlocked = block;
    }

    @Override
    public boolean isEngineBlocked() {
        return mIsEngineBlocked;
    }

    @Override
    public boolean isInPIPMode() {
        if (getActivity() instanceof PhonePlaybackActivity) {
            return ((PhonePlaybackActivity) getActivity()).isInPipMode();
        }
        return false;
    }

    @Override
    public boolean containsMedia() {
        return mExoPlayerController.containsMedia();
    }

    @Override
    public void setSpeed(float speed) {
        mExoPlayerController.setSpeed(speed);
    }

    @Override
    public float getSpeed() {
        return mExoPlayerController.getSpeed();
    }

    @Override
    public void setPitch(float pitch) {
        mExoPlayerController.setPitch(pitch);
    }

    @Override
    public float getPitch() {
        return mExoPlayerController.getPitch();
    }

    @Override
    public void setVolume(float volume) {
        mExoPlayerController.setVolume(volume);
    }

    @Override
    public float getVolume() {
        return mExoPlayerController.getVolume();
    }

    @Override
    public void setResizeMode(int mode) {
        if (mPlayerView != null) {
            mPlayerView.setResizeMode(mode);
        }
    }

    @Override
    public int getResizeMode() {
        return mPlayerView != null ? mPlayerView.getResizeMode() : 0;
    }

    @Override
    public void setZoomPercents(int percents) {
        // Not supported in phone PlayerView
    }

    @Override
    public void setAspectRatio(float ratio) {
        // Not supported in phone PlayerView
    }

    @Override
    public void setRotationAngle(int angle) {
        // Not supported in phone PlayerView
    }

    @Override
    public void setVideoFlipEnabled(boolean enabled) {
        // Not supported in phone PlayerView
    }

    @Override
    public void setVideoGravity(int gravity) {
        // Not supported in phone PlayerView
    }

    // --- PlaybackView: PlayerUI ---

    @Override
    public void updateSuggestions(VideoGroup group) {
        // Suggestions will be handled in a future commit with a phone-friendly
        // bottom sheet or horizontal list below the player.
    }

    @Override
    public void removeSuggestions(VideoGroup group) {
        // No-op for now
    }

    @Override
    public int getSuggestionsIndex(VideoGroup group) {
        return -1;
    }

    @Override
    public VideoGroup getSuggestionsByIndex(int rowIndex) {
        return null;
    }

    @Override
    public void focusSuggestedItem(int index) {
        // No-op for now
    }

    @Override
    public void focusSuggestedItem(Video video) {
        // No-op for now
    }

    @Override
    public void resetSuggestedPosition() {
        // No-op for now
    }

    @Override
    public boolean isSuggestionsEmpty() {
        return true;
    }

    @Override
    public void clearSuggestions() {
        // No-op for now
    }

    @Override
    public void showOverlay(boolean show) {
        if (mPlayerView == null) return;
        if (show) {
            mPlayerView.showController();
            mPlaybackPresenter.onControlsShown(true);
        } else {
            mPlayerView.hideController();
            mPlaybackPresenter.onControlsShown(false);
        }
    }

    @Override
    public boolean isOverlayShown() {
        return mPlayerView != null && mPlayerView.isControllerFullyVisible();
    }

    @Override
    public void showSuggestions(boolean show) {
        // No-op for now
    }

    @Override
    public boolean isSuggestionsShown() {
        return false;
    }

    @Override
    public void showControls(boolean show) {
        showOverlay(show);
    }

    @Override
    public boolean isControlsShown() {
        return isOverlayShown();
    }

    @Override
    public int getButtonState(int buttonId) {
        return PlayerUI.BUTTON_OFF;
    }

    @Override
    public void setButtonState(int buttonId, int buttonState) {
        // No-op for now
    }

    @Override
    public void setChannelIcon(String iconUrl) {
        // No-op for now
    }

    @Override
    public void setSeekPreviewTitle(String title) {
        // No-op for now
    }

    @Override
    public void setNextTitle(Video nextVideo) {
        // No-op for now
    }

    @Override
    public void showDebugInfo(boolean show) {
        // No-op for now
    }

    @Override
    public void showSubtitles(boolean show) {
        if (mSubtitleView != null) {
            mSubtitleView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void loadStoryboard() {
        // No-op for now
    }

    @Override
    public void setTitle(String title) {
        if (mVideoTitle != null) {
            mVideoTitle.setText(title);
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        // ExoPlayer handles buffering indicator automatically
    }

    @Override
    public void setSeekBarSegments(List<SeekBarSegment> segments) {
        // No-op for now
    }

    @Override
    public void updateEndingTime() {
        // No-op for now
    }

    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
        // No-op for now
    }

    // Called by PhonePlaybackActivity for PIP
    public void onPIPChanged(boolean isInPIP) {
        if (isInPIP && mPlayerView != null) {
            mPlayerView.hideController();
        }
    }

    public void maybeReleasePlayerPublic() {
        maybeReleasePlayer();
    }
}

package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phone-friendly browse fragment using a vertical RecyclerView of horizontal sections.
 * Implements BrowseView to work with existing BrowsePresenter.
 */
public class PhoneBrowseFragment extends Fragment implements BrowseView {
    private static final String TAG = PhoneBrowseFragment.class.getSimpleName();

    private RecyclerView mRecyclerView;
    private ProgressBar mProgressBar;
    private TextView mEmptyText;
    private PhoneSectionAdapter mAdapter;
    private BrowsePresenter mBrowsePresenter;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsFragmentCreated;

    // ordered section list and video data per section
    private final List<BrowseSection> mSections = new ArrayList<>();
    private final Map<Integer, VideoGroup> mVideoGroups = new HashMap<>();
    private final Map<Integer, SettingsGroup> mSettingsGroups = new HashMap<>();
    private boolean mChainingLoads;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_phone_browse_content, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mRecyclerView = view.findViewById(R.id.phone_browse_recycler);
        mProgressBar = view.findViewById(R.id.phone_browse_progress);
        mEmptyText = view.findViewById(R.id.phone_browse_empty);

        // Search bar — tapping opens PhoneSearchActivity via SearchPresenter
        View searchBar = view.findViewById(R.id.search_bar_text);
        if (searchBar != null) {
            searchBar.setOnClickListener(v -> {
                SearchPresenter.instance(requireContext()).startSearch(null);
            });
        }

        mAdapter = new PhoneSectionAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);

        // Trigger section data loading as the user scrolls, mirroring TV Leanback behavior.
        // On TV, the Leanback adapter calls selectSection() when a row gains focus.
        // Here we detect the top-most visible section and trigger onSectionFocused.
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private int mLastFocusedId = -1;

            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy == 0) return;
                notifyTopVisibleSection();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    notifyTopVisibleSection();
                }
            }

            private void notifyTopVisibleSection() {
                LinearLayoutManager lm = (LinearLayoutManager) mRecyclerView.getLayoutManager();
                if (lm == null || mSections.isEmpty()) return;

                int first = lm.findFirstVisibleItemPosition();
                if (first < 0 || first >= mSections.size()) return;

                BrowseSection section = mSections.get(first);
                if (section != null && section.getId() != mLastFocusedId) {
                    mLastFocusedId = section.getId();
                    mBrowsePresenter.onSectionFocused(section.getId());
                }
            }
        });

        mBrowsePresenter = BrowsePresenter.instance(requireContext());
        mBrowsePresenter.setView(this);
        mBrowsePresenter.onViewInitialized();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mIsFragmentCreated) {
            mBrowsePresenter.onViewResumed();
        }
        mIsFragmentCreated = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!mIsFragmentCreated) {
            mBrowsePresenter.onViewPaused();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBrowsePresenter.onViewDestroyed();
    }

    // --- BrowseView implementation ---

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) return;
        mHandler.post(() -> {
            // Remove if already exists
            for (int i = mSections.size() - 1; i >= 0; i--) {
                if (mSections.get(i).getId() == section.getId()) {
                    mSections.remove(i);
                    mVideoGroups.remove(section.getId());
                    mSettingsGroups.remove(section.getId());
                    mAdapter.notifyItemRemoved(i);
                    break;
                }
            }
            if (index == -1 || index >= mSections.size()) {
                mSections.add(section);
                mAdapter.notifyItemInserted(mSections.size() - 1);
            } else {
                mSections.add(index, section);
                mAdapter.notifyItemInserted(index);
            }
            updateEmptyState();
        });
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (section == null) return;
        mHandler.post(() -> {
            for (int i = mSections.size() - 1; i >= 0; i--) {
                if (mSections.get(i).getId() == section.getId()) {
                    mSections.remove(i);
                    mVideoGroups.remove(section.getId());
                    mSettingsGroups.remove(section.getId());
                    mAdapter.notifyItemRemoved(i);
                    break;
                }
            }
            updateEmptyState();
        });
    }

    @Override
    public void removeAllSections() {
        mHandler.post(() -> {
            int size = mSections.size();
            mSections.clear();
            mSettingsGroups.clear();
            // Don't clear mVideoGroups — preserve cached video data across section rebuilds
            // triggered by onAccountChanged. The presenter's updateVideoRows sends an empty
            // placeholder group that would overwrite real data if we cleared here.
            mAdapter.notifyItemRangeRemoved(0, size);
            updateEmptyState();
        });
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        mHandler.post(() -> {
            if (index >= 0 && index < mSections.size()) {
                LinearLayoutManager lm = (LinearLayoutManager) mRecyclerView.getLayoutManager();
                if (lm != null) {
                    lm.scrollToPositionWithOffset(index, 0);
                }

                BrowseSection section = mSections.get(index);

                // Settings sections use SettingsGroup
                if (section.getType() == BrowseSection.TYPE_SETTINGS_GRID) {
                    SettingsGroup sg = mSettingsGroups.get(section.getId());
                    if (focusOnContent || sg == null || sg.isEmpty()) {
                        mBrowsePresenter.onSectionFocused(section.getId());
                    }
                    return;
                }

                VideoGroup group = mVideoGroups.get(section.getId());
                boolean hasData = group != null && group.getVideos() != null && !group.getVideos().isEmpty();
                if (focusOnContent || !hasData) {
                    mBrowsePresenter.onSectionFocused(section.getId());
                }
            }
        });
    }

    @Override
    public void updateSection(VideoGroup group) {
        if (group == null) return;
        mHandler.post(() -> {
            boolean newHasData = !group.isEmpty();
            BrowseSection section = group.getSection();
            if (section != null) {
                VideoGroup existing = mVideoGroups.get(section.getId());
                boolean existingHasData = existing != null && !existing.isEmpty();
                // Don't let an empty placeholder overwrite a group that already has real
                // videos. The presenter's updateVideoRows sends an empty VideoGroup first
                // (line ~709 of BrowsePresenter) before the Observable emits real data.
                if (!existingHasData || newHasData) {
                    mVideoGroups.put(section.getId(), group);
                }
            }
            mAdapter.notifyDataSetChanged();

            // Chain-load: after a section gets real data, trigger the next unloaded section.
            // This mirrors TV Leanback's sidebar focus behavior where each section loads
            // sequentially as the user navigates down.
            if (newHasData) {
                triggerNextUnloadedSection();
            }
        });
    }

    @Override
    public void updateSection(SettingsGroup group) {
        if (group == null) return;
        mHandler.post(() -> {
            BrowseSection section = group.getCategory();
            if (section != null) {
                mSettingsGroups.put(section.getId(), group);
            }
            mAdapter.notifyDataSetChanged();
            triggerNextUnloadedSection();
        });
    }

    @Override
    public void clearSection(BrowseSection section) {
        if (section == null) return;
        mHandler.post(() -> {
            mVideoGroups.remove(section.getId());
            mSettingsGroups.remove(section.getId());
            mAdapter.notifyDataSetChanged();
        });
    }

    @Override
    public void selectSectionItem(int index) {
    }

    @Override
    public void selectSectionItem(Video item) {
    }

    @Override
    public void showError(ErrorFragmentData data) {
        mHandler.post(() -> {
            if (mEmptyText != null) {
                mEmptyText.setText(data != null ? data.toString() : "Error");
                mEmptyText.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void showProgressBar(boolean show) {
        mHandler.post(() -> {
            if (mProgressBar != null) {
                mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public boolean isProgressBarShowing() {
        return mProgressBar != null && mProgressBar.getVisibility() == View.VISIBLE;
    }

    @Override
    public void focusOnContent() {
        if (mRecyclerView != null) {
            mRecyclerView.requestFocus();
        }
    }

    @Override
    public boolean isEmpty() {
        return mSections.isEmpty();
    }

    @Override
    public void updateBadge() {
    }

    private void updateEmptyState() {
        if (mEmptyText != null) {
            mEmptyText.setVisibility(mSections.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Chain-load: find the next section without data and trigger onSectionFocused.
     * Called after each section's data arrives so sections load sequentially on first boot.
     */
    private void triggerNextUnloadedSection() {
        if (mChainingLoads || mSections.isEmpty()) return;

        for (BrowseSection section : mSections) {
            // Settings sections use SettingsGroup, not VideoGroup
            if (section.getType() == BrowseSection.TYPE_SETTINGS_GRID) {
                SettingsGroup sg = mSettingsGroups.get(section.getId());
                if (sg == null || sg.isEmpty()) {
                    mChainingLoads = true;
                    mHandler.postDelayed(() -> {
                        mChainingLoads = false;
                        if (isAdded()) {
                            mBrowsePresenter.onSectionFocused(section.getId());
                        }
                    }, 50);
                    return;
                }
                continue;
            }

            VideoGroup group = mVideoGroups.get(section.getId());
            boolean hasData = group != null && group.getVideos() != null && !group.getVideos().isEmpty();
            if (!hasData) {
                mChainingLoads = true;
                mHandler.postDelayed(() -> {
                    mChainingLoads = false;
                    if (isAdded()) {
                        mBrowsePresenter.onSectionFocused(section.getId());
                    }
                }, 50);
                return;
            }
        }
    }

    // ---------- Adapter ----------

    private class PhoneSectionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_VIDEO_SECTION = 0;
        private static final int TYPE_SETTINGS_SECTION = 1;

        @Override
        public int getItemViewType(int position) {
            BrowseSection section = mSections.get(position);
            if (section.getType() == BrowseSection.TYPE_SETTINGS_GRID) {
                return TYPE_SETTINGS_SECTION;
            }
            return TYPE_VIDEO_SECTION;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_SETTINGS_SECTION) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_phone_section_settings, parent, false);
                return new SettingsSectionViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_phone_section, parent, false);
                return new VideoSectionViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            BrowseSection section = mSections.get(position);

            if (holder instanceof SettingsSectionViewHolder) {
                SettingsSectionViewHolder sh = (SettingsSectionViewHolder) holder;
                sh.sectionTitle.setText(section.getTitle());
                SettingsGroup settingsGroup = mSettingsGroups.get(section.getId());
                if (settingsGroup != null && !settingsGroup.isEmpty()) {
                    sh.settingsAdapter.setItems(settingsGroup.getItems());
                } else {
                    sh.settingsAdapter.setItems(null);
                }
            } else {
                VideoSectionViewHolder vh = (VideoSectionViewHolder) holder;
                vh.sectionTitle.setText(section.getTitle());
                VideoGroup group = mVideoGroups.get(section.getId());
                List<Video> videos = group != null ? group.getVideos() : null;
                if (videos != null && !videos.isEmpty()) {
                    vh.videoAdapter.setVideos(videos);
                } else {
                    vh.videoAdapter.setVideos(null);
                }
            }
        }

        @Override
        public int getItemCount() {
            return mSections.size();
        }

        class VideoSectionViewHolder extends RecyclerView.ViewHolder {
            TextView sectionTitle;
            RecyclerView horizontalList;
            VideoCardListAdapter videoAdapter;

            VideoSectionViewHolder(@NonNull View itemView) {
                super(itemView);
                sectionTitle = itemView.findViewById(R.id.section_title);
                horizontalList = itemView.findViewById(R.id.section_items);
                horizontalList.setLayoutManager(new LinearLayoutManager(
                        itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
                videoAdapter = new VideoCardListAdapter();
                horizontalList.setAdapter(videoAdapter);
            }
        }

        class SettingsSectionViewHolder extends RecyclerView.ViewHolder {
            TextView sectionTitle;
            RecyclerView settingsList;
            SettingsItemAdapter settingsAdapter;

            SettingsSectionViewHolder(@NonNull View itemView) {
                super(itemView);
                sectionTitle = itemView.findViewById(R.id.section_title);
                settingsList = itemView.findViewById(R.id.section_items);
                settingsList.setLayoutManager(new LinearLayoutManager(
                        itemView.getContext(), LinearLayoutManager.VERTICAL, false));
                settingsAdapter = new SettingsItemAdapter();
                settingsList.setAdapter(settingsAdapter);
            }
        }
    }

    // ---------- Video list adapter (horizontal) ----------

    private static class VideoCardListAdapter extends RecyclerView.Adapter<VideoCardListAdapter.VideoViewHolder> {
        private List<Video> mVideos = new ArrayList<>();

        void setVideos(List<Video> videos) {
            mVideos = videos != null ? videos : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_phone_video_card, parent, false);
            return new VideoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
            Video video = mVideos.get(position);

            holder.title.setText(video.getTitle());
            holder.channelName.setText(video.getAuthor());
            holder.viewsDate.setText(video.getSecondTitle());

            String thumbnailUrl = ClickbaitRemover.updateThumbnail(video, 0);
            if (thumbnailUrl == null) {
                thumbnailUrl = video.getCardImageUrl();
            }

            Activity activity = null;
            if (holder.itemView.getContext() instanceof Activity) {
                activity = (Activity) holder.itemView.getContext();
            }
            if (activity != null && !activity.isDestroyed()) {
                Glide.with(activity)
                        .load(thumbnailUrl)
                        .centerCrop()
                        .into(holder.thumbnail);
            }

            holder.itemView.setOnClickListener(v -> {
                BrowsePresenter presenter = BrowsePresenter.instance(v.getContext());
                presenter.onVideoItemClicked(video);
            });
        }

        @Override
        public int getItemCount() {
            return mVideos != null ? mVideos.size() : 0;
        }

        static class VideoViewHolder extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            TextView title;
            TextView channelName;
            TextView viewsDate;

            VideoViewHolder(@NonNull View itemView) {
                super(itemView);
                thumbnail = itemView.findViewById(R.id.video_thumbnail);
                title = itemView.findViewById(R.id.video_title);
                channelName = itemView.findViewById(R.id.video_channel_name);
                viewsDate = itemView.findViewById(R.id.video_views_date);
            }
        }
    }

    // ---------- Settings item adapter (vertical) ----------

    private static class SettingsItemAdapter extends RecyclerView.Adapter<SettingsItemAdapter.SettingsViewHolder> {
        private List<SettingsItem> mItems = new ArrayList<>();

        SettingsItemAdapter() {
        }

        SettingsItemAdapter(List<SettingsItem> items) {
            mItems = items != null ? items : new ArrayList<>();
        }

        void setItems(List<SettingsItem> items) {
            mItems = items != null ? items : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public SettingsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_phone_settings, parent, false);
            return new SettingsViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SettingsViewHolder holder, int position) {
            SettingsItem item = mItems.get(position);
            holder.title.setText(item.title);

            if (item.imageResId != -1) {
                holder.icon.setImageResource(item.imageResId);
                holder.icon.setVisibility(View.VISIBLE);
            } else {
                holder.icon.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(v -> {
                if (item.onClick != null) {
                    item.onClick.run();
                }
            });
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        static class SettingsViewHolder extends RecyclerView.ViewHolder {
            android.widget.ImageView icon;
            android.widget.TextView title;

            SettingsViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.settings_icon);
                title = itemView.findViewById(R.id.settings_title);
            }
        }
    }
}

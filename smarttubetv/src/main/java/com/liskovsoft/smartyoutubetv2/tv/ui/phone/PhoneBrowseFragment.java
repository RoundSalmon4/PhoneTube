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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Home tab fragment — displays browse sections as horizontal video rows.
 * Display-only. Data is pushed from PhoneBrowseActivity.
 */
public class PhoneBrowseFragment extends Fragment {
    private static final String TAG = PhoneBrowseFragment.class.getSimpleName();

    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeRefresh;
    private ProgressBar mProgressBar;
    private TextView mEmptyText;
    private PhoneSectionAdapter mAdapter;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final List<BrowseSection> mSections = new ArrayList<>();
    private final Map<Integer, VideoGroup> mVideoGroups = new HashMap<>();
    private final Map<Integer, SettingsGroup> mSettingsGroups = new HashMap<>();
    private OnRefreshListener mRefreshListener;

    interface OnRefreshListener {
        void onRefresh(int sectionId);
    }

    void setOnRefreshListener(OnRefreshListener listener) {
        mRefreshListener = listener;
    }

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
        mSwipeRefresh = view.findViewById(R.id.phone_browse_swipe_refresh);
        mProgressBar = view.findViewById(R.id.phone_browse_progress);
        mEmptyText = view.findViewById(R.id.phone_browse_empty);

        mSwipeRefresh.setColorSchemeResources(android.R.color.holo_blue_bright);
        mSwipeRefresh.setOnRefreshListener(() -> {
            LinearLayoutManager lm = (LinearLayoutManager) mRecyclerView.getLayoutManager();
            if (lm != null && !mSections.isEmpty()) {
                int first = lm.findFirstVisibleItemPosition();
                if (first >= 0 && first < mSections.size()) {
                    BrowseSection section = mSections.get(first);
                    if (mRefreshListener != null) {
                        mRefreshListener.onRefresh(section.getId());
                    }
                }
            }
            mSwipeRefresh.setRefreshing(false);
        });

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
    }

    // --- Data methods called by PhoneBrowseActivity ---

    void addSection(int index, BrowseSection section) {
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
            if (index == -1 || index >= mSections.size()) {
                mSections.add(section);
                mAdapter.notifyItemInserted(mSections.size() - 1);
            } else {
                mSections.add(index, section);
                mAdapter.notifyItemInserted(index);
            }
            updateEmptyState();

            // Hide progress bar once we have sections to display
            if (mProgressBar != null && !mSections.isEmpty()) {
                mProgressBar.setVisibility(View.GONE);
            }
        });
    }

    void removeSection(BrowseSection section) {
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

    void removeAllSections() {
        mHandler.post(() -> {
            int size = mSections.size();
            mSections.clear();
            mVideoGroups.clear();
            mSettingsGroups.clear();
            mAdapter.notifyItemRangeRemoved(0, size);
            updateEmptyState();
        });
    }

    void scrollToSection(int index) {
        mHandler.post(() -> {
            if (index >= 0 && index < mSections.size()) {
                LinearLayoutManager lm = (LinearLayoutManager) mRecyclerView.getLayoutManager();
                if (lm != null) {
                    lm.scrollToPositionWithOffset(index, 0);
                }
            }
        });
    }

    void updateSection(VideoGroup group) {
        if (group == null) return;
        mHandler.post(() -> {
            boolean newHasData = !group.isEmpty();
            BrowseSection section = group.getSection();
            if (section != null) {
                VideoGroup existing = mVideoGroups.get(section.getId());
                boolean existingHasData = existing != null && !existing.isEmpty();
                // A TYPE_ROW section arrives as several VideoGroups sharing one section id
                // (the presenter emits an empty ACTION_REPLACE placeholder first, then the
                // real rows). Only swap in a group that carries data so an empty placeholder
                // never wipes rows we already drew. Never mutate the incoming group in place —
                // its video list is shared with the presenter's cache and touching it corrupts
                // the parallel loads that are still in flight.
                if (!existingHasData || newHasData) {
                    mVideoGroups.put(section.getId(), group);
                }
            }
            mAdapter.notifyDataSetChanged();
        });
    }

    void updateSection(SettingsGroup group) {
        if (group == null) return;
        mHandler.post(() -> {
            BrowseSection section = group.getCategory();
            if (section != null) {
                mSettingsGroups.put(section.getId(), group);
            }
            mAdapter.notifyDataSetChanged();
        });
    }

    void clearSection(BrowseSection section) {
        if (section == null) return;
        mHandler.post(() -> {
            mVideoGroups.remove(section.getId());
            mSettingsGroups.remove(section.getId());
            mAdapter.notifyDataSetChanged();
        });
    }

    void showProgressBar(boolean show) {
        mHandler.post(() -> {
            // Only show progress bar during initial load (no sections yet).
            // Once sections appear, hide it — individual section data loads in the
            // background shouldn't show a global spinner.
            if (mProgressBar != null) {
                if (show && mSections.isEmpty()) {
                    mProgressBar.setVisibility(View.VISIBLE);
                } else {
                    mProgressBar.setVisibility(View.GONE);
                }
            }
        });
    }

    void showError(String message) {
        mHandler.post(() -> {
            if (mEmptyText != null) {
                mEmptyText.setText(message != null ? message : "Error");
                mEmptyText.setVisibility(View.VISIBLE);
            }
        });
    }

    boolean isEmpty() {
        return mSections.isEmpty();
    }

    private void updateEmptyState() {
        if (mEmptyText != null) {
            mEmptyText.setVisibility(mSections.isEmpty() ? View.VISIBLE : View.GONE);
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

    static class VideoCardListAdapter extends RecyclerView.Adapter<VideoCardListAdapter.VideoViewHolder> {
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
                // Let the presenter route the tap. It funnels through VideoActionPresenter,
                // which already knows how to open a channel, a playlist or play a video —
                // so Channels/Playlists/My Videos cards all end up in the right place.
                BrowsePresenter.instance(v.getContext()).onVideoItemClicked(video);
            });
            holder.itemView.setOnLongClickListener(v -> {
                BrowsePresenter presenter = BrowsePresenter.instance(v.getContext());
                presenter.onVideoItemLongClicked(video);
                return true;
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

    static class SettingsItemAdapter extends RecyclerView.Adapter<SettingsItemAdapter.SettingsViewHolder> {
        private List<SettingsItem> mItems = new ArrayList<>();

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

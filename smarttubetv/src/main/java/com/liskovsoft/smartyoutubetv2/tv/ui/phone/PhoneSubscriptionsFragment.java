package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Subscriptions tab — vertical list of subscription videos.
 * Display-only fragment. Data is pushed from PhoneBrowseActivity.
 */
public class PhoneSubscriptionsFragment extends Fragment {
    private static final String TAG = PhoneSubscriptionsFragment.class.getSimpleName();

    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeRefresh;
    private ProgressBar mProgressBar;
    private TextView mEmptyText;
    private VideoListAdapter mAdapter;
    private BrowseSection mSection;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_phone_tab_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mRecyclerView = view.findViewById(R.id.tab_recycler);
        mSwipeRefresh = view.findViewById(R.id.tab_swipe_refresh);
        mProgressBar = view.findViewById(R.id.tab_progress);
        mEmptyText = view.findViewById(R.id.tab_empty);

        mSwipeRefresh.setColorSchemeResources(android.R.color.holo_blue_bright);
        mSwipeRefresh.setOnRefreshListener(() -> {
            if (mSection != null) {
                BrowsePresenter.instance(requireContext()).loadSectionData(mSection.getId());
            }
            mSwipeRefresh.setRefreshing(false);
        });

        mAdapter = new VideoListAdapter();
        mRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        mRecyclerView.setAdapter(mAdapter);
    }

    void addSection(BrowseSection section) {
        mHandler.post(() -> {
            mSection = section;
            updateEmptyState();
        });
    }

    void updateSection(VideoGroup group) {
        if (group == null) return;
        mHandler.post(() -> {
            List<Video> videos = group.getVideos();
            if (videos != null && !videos.isEmpty()) {
                mAdapter.setVideos(videos);
            }
            updateEmptyState();
        });
    }

    void clearSection() {
        mHandler.post(() -> {
            mAdapter.setVideos(null);
            updateEmptyState();
        });
    }

    void showProgressBar(boolean show) {
        mHandler.post(() -> {
            if (mProgressBar != null) {
                mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
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

    private void updateEmptyState() {
        if (mEmptyText != null) {
            boolean empty = mAdapter == null || mAdapter.getItemCount() == 0;
            mEmptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
            mEmptyText.setText("No subscriptions");
        }
    }

    // ---------- Video adapter (vertical grid) ----------

    private static class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.VideoViewHolder> {
        private List<Video> mVideos = new ArrayList<>();

        void setVideos(List<Video> videos) {
            mVideos = videos != null ? new ArrayList<>(videos) : new ArrayList<>();
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
                if (video.isChannel()) {
                    ChannelPresenter.instance(v.getContext()).openChannel(video);
                } else {
                    BrowsePresenter presenter = BrowsePresenter.instance(v.getContext());
                    presenter.onVideoItemClicked(video);
                }
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
            android.widget.ImageView thumbnail;
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
}

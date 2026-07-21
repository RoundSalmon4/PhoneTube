package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PhoneChannelFragment extends Fragment implements ChannelView {
    private static final String TAG = PhoneChannelFragment.class.getSimpleName();

    private RecyclerView mRecyclerView;
    private ProgressBar mProgressBar;
    private TextView mEmptyText;
    private TextView mTitleText;
    private View mHeader;
    private ImageView mAvatar;
    private TextView mChannelName;
    private TextView mSubscriberCount;
    private ChannelPresenter mChannelPresenter;
    private ChannelRowAdapter mAdapter;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsFragmentCreated;

    // ordered list of group IDs and the groups themselves
    private final List<Integer> mGroupIds = new ArrayList<>();
    private final Map<Integer, VideoGroup> mGroups = new LinkedHashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_phone_channel, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mRecyclerView = view.findViewById(R.id.channel_video_rows);
        mProgressBar = view.findViewById(R.id.channel_progress);
        mEmptyText = view.findViewById(R.id.channel_empty);
        mTitleText = view.findViewById(R.id.channel_title);
        mHeader = view.findViewById(R.id.channel_header);
        mAvatar = view.findViewById(R.id.channel_avatar);
        mChannelName = view.findViewById(R.id.channel_name);
        mSubscriberCount = view.findViewById(R.id.channel_subscriber_count);

        ImageButton backButton = view.findViewById(R.id.channel_back_button);
        backButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        mAdapter = new ChannelRowAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);

        // Pagination: detect scroll to end and call onScrollEnd with the last video of the last row
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;

                int lastVisible = lm.findLastVisibleItemPosition();
                int total = mAdapter.getItemCount();
                if (lastVisible >= total - 2) {
                    // Near the bottom — find the last video across all groups
                    Video lastVideo = getLastVideo();
                    if (lastVideo != null) {
                        mChannelPresenter.onScrollEnd(lastVideo);
                    }
                }
            }
        });

        mChannelPresenter = ChannelPresenter.instance(requireContext());
        mChannelPresenter.setView(this);
        mChannelPresenter.onViewInitialized();

        updateHeader();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mIsFragmentCreated) {
            mChannelPresenter.onViewResumed();
        }
        mIsFragmentCreated = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!mIsFragmentCreated) {
            mChannelPresenter.onViewPaused();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mChannelPresenter.onViewDestroyed();
    }

    public void onFinish() {
        mChannelPresenter.onFinish();
    }

    // --- ChannelView implementation ---

    @Override
    public void update(VideoGroup group) {
        if (group == null) return;
        mHandler.post(() -> {
            int groupId = group.getId();

            if (group.getAction() == VideoGroup.ACTION_REPLACE) {
                if (group.getPosition() == -1) {
                    clearInternal();
                } else {
                    // replace single row
                    removeGroupById(groupId);
                }
            } else if (group.getAction() == VideoGroup.ACTION_REMOVE) {
                removeGroupById(groupId);
                return;
            }

            if (group.isEmpty()) {
                return;
            }

            VideoGroup existing = mGroups.get(groupId);
            if (existing != null) {
                // Append videos to existing row
                for (Video video : group.getVideos()) {
                    existing.add(video);
                }
                int idx = mGroupIds.indexOf(groupId);
                if (idx != -1) {
                    mAdapter.notifyItemChanged(idx);
                }
            } else {
                // New row
                mGroups.put(groupId, group);
                mGroupIds.add(groupId);
                mAdapter.notifyItemInserted(mGroupIds.size() - 1);
            }
            updateEmptyState();
        });
    }

    @Override
    public void setPosition(int index) {
        // Not used in phone layout
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
    public void clear() {
        mHandler.post(this::clearInternal);
    }

    private void clearInternal() {
        mGroups.clear();
        mGroupIds.clear();
        mAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void removeGroupById(int id) {
        int idx = mGroupIds.indexOf(id);
        if (idx != -1) {
            mGroupIds.remove(idx);
            mGroups.remove(id);
            mAdapter.notifyItemRemoved(idx);
        }
    }

    private void updateHeader() {
        Video channel = mChannelPresenter.getChannel();
        if (channel == null) {
            return;
        }

        mHeader.setVisibility(View.VISIBLE);

        String author = channel.getAuthor();
        String title = channel.getTitle();
        String displayName = Helpers.firstNonNull(author, title);

        if (mTitleText != null) {
            mTitleText.setText(displayName);
        }
        if (mChannelName != null) {
            mChannelName.setText(displayName);
        }

        String subs = channel.subscriberCount;
        if (mSubscriberCount != null && subs != null && !subs.isEmpty()) {
            mSubscriberCount.setText(subs);
            mSubscriberCount.setVisibility(View.VISIBLE);
        } else if (mSubscriberCount != null) {
            mSubscriberCount.setVisibility(View.GONE);
        }

        String avatarUrl = channel.getCardImageUrl();
        if (mAvatar != null && avatarUrl != null && !avatarUrl.isEmpty()) {
            Activity activity = getActivity();
            if (activity != null && !activity.isDestroyed()) {
                Glide.with(activity)
                        .load(avatarUrl)
                        .circleCrop()
                        .into(mAvatar);
            }
        }
    }

    private void updateEmptyState() {
        if (mEmptyText != null) {
            mEmptyText.setVisibility(mGroups.isEmpty() && mProgressBar.getVisibility() != View.VISIBLE
                    ? View.VISIBLE : View.GONE);
        }
    }

    private Video getLastVideo() {
        for (int i = mGroupIds.size() - 1; i >= 0; i--) {
            VideoGroup group = mGroups.get(mGroupIds.get(i));
            if (group != null && !group.isEmpty()) {
                List<Video> videos = group.getVideos();
                if (!videos.isEmpty()) {
                    return videos.get(videos.size() - 1);
                }
            }
        }
        return null;
    }

    // ---------- Adapter ----------

    private class ChannelRowAdapter extends RecyclerView.Adapter<ChannelRowAdapter.RowViewHolder> {

        @NonNull
        @Override
        public RowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_phone_channel_row, parent, false);
            return new RowViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RowViewHolder holder, int position) {
            Integer groupId = mGroupIds.get(position);
            VideoGroup group = mGroups.get(groupId);
            if (group == null) return;

            holder.rowTitle.setText(group.getTitle());
            holder.videoAdapter.setVideos(group.getVideos());
        }

        @Override
        public int getItemCount() {
            return mGroupIds.size();
        }

        class RowViewHolder extends RecyclerView.ViewHolder {
            TextView rowTitle;
            RecyclerView rowItems;
            HorizontalVideoAdapter videoAdapter;

            RowViewHolder(@NonNull View itemView) {
                super(itemView);
                rowTitle = itemView.findViewById(R.id.row_title);
                rowItems = itemView.findViewById(R.id.row_items);
                LinearLayoutManager lm = new LinearLayoutManager(
                        itemView.getContext(), LinearLayoutManager.HORIZONTAL, false);
                rowItems.setLayoutManager(lm);
                videoAdapter = new HorizontalVideoAdapter();
                rowItems.setAdapter(videoAdapter);
            }
        }
    }

    // ---------- Horizontal video adapter ----------

    private class HorizontalVideoAdapter extends RecyclerView.Adapter<HorizontalVideoAdapter.VideoViewHolder> {
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

            holder.itemView.setOnClickListener(v ->
                    mChannelPresenter.onVideoItemClicked(video));
            holder.itemView.setOnLongClickListener(v -> {
                mChannelPresenter.onVideoItemLongClicked(video);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mVideos != null ? mVideos.size() : 0;
        }

        class VideoViewHolder extends RecyclerView.ViewHolder {
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
}

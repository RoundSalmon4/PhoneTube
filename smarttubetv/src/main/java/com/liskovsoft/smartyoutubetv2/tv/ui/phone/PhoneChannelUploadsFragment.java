package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

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
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

public class PhoneChannelUploadsFragment extends Fragment implements ChannelUploadsView {
    private static final String TAG = PhoneChannelUploadsFragment.class.getSimpleName();

    private RecyclerView mRecyclerView;
    private ProgressBar mProgressBar;
    private TextView mEmptyText;
    private TextView mTitleText;
    private ChannelUploadsPresenter mPresenter;
    private VideoListAdapter mAdapter;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsFragmentCreated;
    private List<Video> mVideos = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_phone_channel_uploads, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mRecyclerView = view.findViewById(R.id.uploads_video_list);
        mProgressBar = view.findViewById(R.id.uploads_progress);
        mEmptyText = view.findViewById(R.id.uploads_empty);
        mTitleText = view.findViewById(R.id.uploads_title);

        ImageButton backButton = view.findViewById(R.id.uploads_back_button);
        backButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        mAdapter = new VideoListAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);

        // Pagination: detect scroll to end
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;

                int lastVisible = lm.findLastVisibleItemPosition();
                int total = mAdapter.getItemCount();
                if (lastVisible >= total - 2 && !mVideos.isEmpty()) {
                    Video lastVideo = mVideos.get(mVideos.size() - 1);
                    mPresenter.onScrollEnd(lastVideo);
                }
            }
        });

        mPresenter = ChannelUploadsPresenter.instance(requireContext());
        mPresenter.setView(this);
        mPresenter.onViewInitialized();

        updateTitle();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mIsFragmentCreated) {
            mPresenter.onViewResumed();
        }
        mIsFragmentCreated = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!mIsFragmentCreated) {
            mPresenter.onViewPaused();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPresenter.onViewDestroyed();
    }

    public void onFinish() {
        mPresenter.onFinish();
    }

    // --- ChannelUploadsView implementation ---

    @Override
    public void update(VideoGroup group) {
        if (group == null) return;
        mHandler.post(() -> {
            int action = group.getAction();

            if (action == VideoGroup.ACTION_REPLACE) {
                mVideos.clear();
            }

            if (!group.isEmpty()) {
                for (Video video : group.getVideos()) {
                    mVideos.add(video);
                }
            }

            mAdapter.notifyDataSetChanged();
            updateEmptyState();
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
    public void clear() {
        mHandler.post(() -> {
            mVideos.clear();
            mAdapter.notifyDataSetChanged();
            updateEmptyState();
        });
    }

    private void updateTitle() {
        if (mTitleText == null) return;
        Video channel = mPresenter.getChannel();
        if (channel != null) {
            String title = channel.getTitle();
            if (title != null && !title.isEmpty()) {
                mTitleText.setText(title);
            } else if (channel.getAuthor() != null) {
                mTitleText.setText(channel.getAuthor());
            }
        }
    }

    private void updateEmptyState() {
        if (mEmptyText != null) {
            mEmptyText.setVisibility(mVideos.isEmpty() && mProgressBar.getVisibility() != View.VISIBLE
                    ? View.VISIBLE : View.GONE);
        }
    }

    // ---------- Adapter ----------

    private class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.VideoViewHolder> {

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

            Glide.with(holder.itemView)
                    .load(thumbnailUrl)
                    .centerCrop()
                    .into(holder.thumbnail);

            holder.itemView.setOnClickListener(v ->
                    mPresenter.onVideoItemClicked(video));
            holder.itemView.setOnLongClickListener(v -> {
                mPresenter.onVideoItemLongClicked(video);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mVideos.size();
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

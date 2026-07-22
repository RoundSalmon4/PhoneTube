package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

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

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable 2-column video grid tab. Replaces both PhoneSubscriptionsFragment
 * and PhoneLibraryFragment which were 95% identical.
 * The only difference is the empty message, passed via setEmptyMessage().
 */
public class PhoneVideoGridFragment extends Fragment {
    private static final String TAG = PhoneVideoGridFragment.class.getSimpleName();

    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeRefresh;
    private ProgressBar mProgressBar;
    private TextView mEmptyText;
    private PhoneVideoCardAdapter mAdapter;
    private BrowseSection mSection;
    private String mEmptyMessage = "Nothing here yet";
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

        mAdapter = new PhoneVideoCardAdapter(new PhoneVideoCardAdapter.OnVideoClickListener() {
            @Override
            public void onClick(com.liskovsoft.smartyoutubetv2.common.app.models.data.Video video) {
                if (video.isChannel()) {
                    ChannelPresenter.instance(requireContext()).openChannel(video);
                } else {
                    BrowsePresenter.instance(requireContext()).onVideoItemClicked(video);
                }
            }

            @Override
            public void onLongClick(com.liskovsoft.smartyoutubetv2.common.app.models.data.Video video) {
                BrowsePresenter.instance(requireContext()).onVideoItemLongClicked(video);
            }
        });
        mRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        mRecyclerView.setAdapter(mAdapter);
    }

    void setEmptyMessage(String message) {
        mEmptyMessage = message;
        if (mEmptyText != null) {
            mEmptyText.setText(mEmptyMessage);
        }
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
            List<com.liskovsoft.smartyoutubetv2.common.app.models.data.Video> videos = group.getVideos();
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
            mEmptyText.setText(mEmptyMessage);
        }
    }
}

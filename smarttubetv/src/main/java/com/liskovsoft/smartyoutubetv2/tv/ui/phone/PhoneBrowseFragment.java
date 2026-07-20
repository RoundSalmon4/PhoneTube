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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Phone-friendly browse fragment using a vertical RecyclerView of horizontal sections.
 * Implements BrowseView to work with existing presenters.
 */
public class PhoneBrowseFragment extends Fragment implements BrowseView {
    private static final String TAG = PhoneBrowseFragment.class.getSimpleName();

    private RecyclerView mRecyclerView;
    private ProgressBar mProgressBar;
    private TextView mEmptyText;
    private PhoneSectionAdapter mAdapter;
    private BrowsePresenter mBrowsePresenter;
    private final List<BrowseSection> mSections = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsFragmentCreated;

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

        mAdapter = new PhoneSectionAdapter();
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setAdapter(mAdapter);

        mBrowsePresenter = BrowsePresenter.instance(requireContext());
        mBrowsePresenter.setView(this);
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
            mAdapter.notifyItemRangeRemoved(0, size);
            updateEmptyState();
        });
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (index >= 0 && index < mSections.size()) {
            mHandler.post(() -> {
                LinearLayoutManager lm = (LinearLayoutManager) mRecyclerView.getLayoutManager();
                if (lm != null) {
                    lm.scrollToPositionWithOffset(index, 0);
                }
            });
        }
    }

    @Override
    public void updateSection(VideoGroup group) {
        mHandler.post(() -> mAdapter.notifyDataSetChanged());
    }

    @Override
    public void updateSection(SettingsGroup group) {
        mHandler.post(() -> mAdapter.notifyDataSetChanged());
    }

    @Override
    public void clearSection(BrowseSection section) {
        mHandler.post(() -> mAdapter.notifyDataSetChanged());
    }

    @Override
    public void selectSectionItem(int index) {
        // Not needed for phone vertical scroll
    }

    @Override
    public void selectSectionItem(Video item) {
        // Not needed for phone vertical scroll
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
        // No badge on phone
    }

    private void updateEmptyState() {
        if (mEmptyText != null) {
            mEmptyText.setVisibility(mSections.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Simple adapter that shows section headers. The actual video content within each section
     * will be rendered in a future commit with horizontal RecyclerViews.
     */
    private class PhoneSectionAdapter extends RecyclerView.Adapter<PhoneSectionAdapter.SectionViewHolder> {

        @NonNull
        @Override
        public SectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_phone_section, parent, false);
            return new SectionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SectionViewHolder holder, int position) {
            BrowseSection section = mSections.get(position);
            holder.sectionTitle.setText(section.getTitle());
            // Video items within each section will be handled in a future commit
            // with a horizontal RecyclerView per section
        }

        @Override
        public int getItemCount() {
            return mSections.size();
        }

        class SectionViewHolder extends RecyclerView.ViewHolder {
            TextView sectionTitle;
            RecyclerView horizontalList;

            SectionViewHolder(@NonNull View itemView) {
                super(itemView);
                sectionTitle = itemView.findViewById(R.id.section_title);
                horizontalList = itemView.findViewById(R.id.section_items);
                horizontalList.setLayoutManager(new LinearLayoutManager(
                        itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
            }
        }
    }
}

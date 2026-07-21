package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

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

import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings tab — vertical list of settings items.
 * Display-only. Data is pushed from PhoneBrowseActivity.
 */
public class PhoneSettingsFragment extends Fragment {
    private static final String TAG = PhoneSettingsFragment.class.getSimpleName();

    private RecyclerView mRecyclerView;
    private ProgressBar mProgressBar;
    private SettingsItemAdapter mAdapter;
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
        mProgressBar = view.findViewById(R.id.tab_progress);

        SwipeRefreshLayout swipeRefresh = view.findViewById(R.id.tab_swipe_refresh);
        if (swipeRefresh != null) swipeRefresh.setEnabled(false);

        mAdapter = new SettingsItemAdapter();
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setAdapter(mAdapter);
    }

    void addSection(BrowseSection section) {
        mHandler.post(() -> mSection = section);
    }

    void updateSection(SettingsGroup group) {
        if (group == null) return;
        mHandler.post(() -> {
            List<SettingsItem> items = group.getItems();
            if (items != null && !items.isEmpty()) {
                mAdapter.setItems(items);
                if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
            }
        });
    }

    void clearSection() {
        mHandler.post(() -> mAdapter.setItems(null));
    }

    void showProgressBar(boolean show) {
        mHandler.post(() -> {
            if (mProgressBar != null) {
                mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    // ---------- Settings item adapter ----------

    private static class SettingsItemAdapter extends RecyclerView.Adapter<SettingsItemAdapter.SettingsViewHolder> {
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
            ImageView icon;
            TextView title;

            SettingsViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.settings_icon);
                title = itemView.findViewById(R.id.settings_title);
            }
        }
    }
}

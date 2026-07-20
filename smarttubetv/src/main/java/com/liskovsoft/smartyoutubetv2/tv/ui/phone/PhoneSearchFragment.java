package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

public class PhoneSearchFragment extends Fragment implements SearchView {
    private static final String TAG = PhoneSearchFragment.class.getSimpleName();
    private static final long SUGGESTION_DEBOUNCE_MS = 300;
    private static final int VOICE_REQUEST_CODE = 1001;

    private EditText mSearchEditText;
    private ImageButton mBackButton;
    private ImageButton mVoiceButton;
    private ImageButton mSettingsButton;
    private RecyclerView mSuggestionsList;
    private RecyclerView mResultsList;
    private ProgressBar mProgressBar;

    private SearchPresenter mSearchPresenter;
    private SuggestionAdapter mSuggestionAdapter;
    private ResultsAdapter mResultsAdapter;
    private MediaServiceSearchTagProvider mTagsProvider;
    private final List<Tag> mSuggestions = new ArrayList<>();
    private final List<VideoGroup> mVideoGroups = new ArrayList<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private String mSearchQuery;
    private boolean mIsFragmentCreated;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_phone_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSearchEditText = view.findViewById(R.id.search_edit_text);
        mBackButton = view.findViewById(R.id.search_back_button);
        mVoiceButton = view.findViewById(R.id.search_voice_button);
        mSettingsButton = view.findViewById(R.id.search_settings_button);
        mSuggestionsList = view.findViewById(R.id.search_suggestions_list);
        mResultsList = view.findViewById(R.id.search_results_list);
        mProgressBar = view.findViewById(R.id.search_progress);

        mSuggestionAdapter = new SuggestionAdapter();
        mSuggestionsList.setLayoutManager(new LinearLayoutManager(getContext()));
        mSuggestionsList.setAdapter(mSuggestionAdapter);

        mResultsAdapter = new ResultsAdapter();
        mResultsList.setLayoutManager(new LinearLayoutManager(getContext()));
        mResultsList.setAdapter(mResultsAdapter);

        mBackButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        mVoiceButton.setOnClickListener(v -> startVoiceRecognition());

        mSettingsButton.setOnClickListener(v -> {
            if (mSearchPresenter != null) {
                mSearchPresenter.onSearchSettingsClicked();
            }
        });

        mSearchEditText.addTextChangedListener(new TextWatcher() {
            private Runnable mPendingRunnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mPendingRunnable != null) {
                    mHandler.removeCallbacks(mPendingRunnable);
                }
                mPendingRunnable = () -> loadSuggestions(s.toString());
                mHandler.postDelayed(mPendingRunnable, SUGGESTION_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submitSearch();
                return true;
            }
            return false;
        });

        mSearchPresenter = SearchPresenter.instance(requireContext());
        mSearchPresenter.setView(this);
        mSearchPresenter.onViewInitialized();
        mIsFragmentCreated = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!mIsFragmentCreated) {
            mSearchPresenter.onViewResumed();
        }
        mIsFragmentCreated = false;

        if (!TextUtils.isEmpty(mSearchQuery)) {
            mSearchEditText.setText(mSearchQuery);
            mSearchEditText.selectAll();
            mSuggestionsList.setVisibility(View.GONE);
            mResultsList.setVisibility(View.VISIBLE);
        } else {
            mSearchEditText.requestFocus();
            showKeyboard();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mSearchPresenter.onViewPaused();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mHandler.removeCallbacksAndMessages(null);
        mSearchPresenter.onViewDestroyed();
    }

    public void onFinish() {
        mSearchPresenter.onFinish();
    }

    private void submitSearch() {
        String query = mSearchEditText.getText().toString().trim();
        if (!TextUtils.isEmpty(query)) {
            mSearchQuery = query;
            mSearchPresenter.onSearch(query);
            mSuggestionsList.setVisibility(View.GONE);
            mResultsList.setVisibility(View.VISIBLE);
            hideKeyboard();
        }
    }

    private void loadSuggestions(String query) {
        if (mTagsProvider == null || TextUtils.isEmpty(query)) {
            mSuggestions.clear();
            mSuggestionAdapter.notifyDataSetChanged();
            mSuggestionsList.setVisibility(View.GONE);
            return;
        }

        mTagsProvider.search(query, results -> {
            if (!isAdded()) return;
            mHandler.post(() -> {
                mSuggestions.clear();
                if (results != null) {
                    mSuggestions.addAll(results);
                }
                mSuggestionAdapter.notifyDataSetChanged();
                mSuggestionsList.setVisibility(mSuggestions.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void showKeyboard() {
        if (mSearchEditText != null && getActivity() != null) {
            mSearchEditText.postDelayed(() -> {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) requireActivity()
                                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null && mSearchEditText != null) {
                    imm.showSoftInput(mSearchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            }, 200);
        }
    }

    private void hideKeyboard() {
        if (getActivity() != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) requireActivity()
                            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null && getActivity().getWindow() != null) {
                imm.hideSoftInputFromWindow(getActivity().getWindow().getDecorView().getWindowToken(), 0);
            }
        }
    }

    // --- SearchView implementation ---

    @Override
    public void updateSearch(VideoGroup group) {
        if (group == null) return;
        mHandler.post(() -> {
            if (group.isEmpty()) return;

            if (group.getAction() == VideoGroup.ACTION_REPLACE) {
                clearSearch();
            }

            VideoGroup existing = findGroupById(group.getId());
            if (existing == null) {
                mVideoGroups.add(group);
            } else {
                // Merge new videos into existing group
                for (Video video : group.getVideos()) {
                    existing.add(video);
                }
            }
            mResultsAdapter.setGroups(mVideoGroups);

            mSuggestionsList.setVisibility(View.GONE);
            mResultsList.setVisibility(View.VISIBLE);
            showProgressBar(false);
        });
    }

    @Nullable
    private VideoGroup findGroupById(int id) {
        for (VideoGroup group : mVideoGroups) {
            if (group.getId() == id) {
                return group;
            }
        }
        return null;
    }

    @Override
    public void clearSearch() {
        mHandler.post(() -> {
            mVideoGroups.clear();
            mResultsAdapter.setGroups(mVideoGroups);
        });
    }

    @Override
    public void clearSearchTags() {
        mHandler.post(() -> {
            mSuggestions.clear();
            mSuggestionAdapter.notifyDataSetChanged();
            mSuggestionsList.setVisibility(View.GONE);
        });
    }

    @Override
    public void removeSearchTag(Tag tag) {
        mHandler.post(() -> {
            mSuggestions.remove(tag);
            mSuggestionAdapter.notifyDataSetChanged();
            if (mSuggestions.isEmpty()) {
                mSuggestionsList.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void setTagsProvider(MediaServiceSearchTagProvider provider) {
        mTagsProvider = provider;
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
    public void startSearch(String searchText) {
        mHandler.post(() -> {
            if (searchText != null) {
                mSearchEditText.setText(searchText);
                mSearchEditText.setSelection(searchText.length());
                mSearchQuery = searchText;
                mSuggestionsList.setVisibility(View.GONE);
                mResultsList.setVisibility(View.VISIBLE);
            } else {
                mSearchEditText.setText("");
                mSearchEditText.requestFocus();
                showKeyboard();
            }
        });
    }

    @Override
    public String getSearchText() {
        if (mSearchEditText != null) {
            return mSearchEditText.getText().toString();
        }
        return null;
    }

    @Override
    public void startVoiceRecognition() {
        mHandler.post(() -> {
            try {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                        getString(R.string.search_hint));
                startActivityForResult(intent, VOICE_REQUEST_CODE);
            } catch (Exception e) {
                // No speech recognizer available
            }
        });
    }

    @Override
    public void finishReally() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            List<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String query = matches.get(0);
                mSearchEditText.setText(query);
                mSearchPresenter.onSearch(query);
                mSuggestionsList.setVisibility(View.GONE);
                mResultsList.setVisibility(View.VISIBLE);
            }
        }
    }

    // --- Suggestion adapter ---

    private class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder> {

        @NonNull
        @Override
        public SuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_phone_search_suggestion, parent, false);
            return new SuggestionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SuggestionViewHolder holder, int position) {
            Tag tag = mSuggestions.get(position);
            holder.text.setText(tag.tag);
            holder.itemView.setOnClickListener(v -> {
                mSearchEditText.setText(tag.tag);
                mSearchPresenter.onSearch(tag.tag);
                mSuggestionsList.setVisibility(View.GONE);
                mResultsList.setVisibility(View.VISIBLE);
            });
            holder.itemView.setOnLongClickListener(v -> {
                mSearchPresenter.onTagLongClicked(tag);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mSuggestions.size();
        }

        class SuggestionViewHolder extends RecyclerView.ViewHolder {
            android.widget.TextView text;

            SuggestionViewHolder(@NonNull View itemView) {
                super(itemView);
                text = itemView.findViewById(R.id.suggestion_text);
            }
        }
    }

    // --- Results adapter (groups with headers, channels separated from videos) ---

    private class ResultsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_GROUP_HEADER = 0;
        private static final int TYPE_VIDEO = 1;
        private static final int TYPE_CHANNEL = 2;

        private final List<Object> mItems = new ArrayList<>();

        void setGroups(List<VideoGroup> groups) {
            mItems.clear();
            for (VideoGroup group : groups) {
                if (group.getVideos() == null || group.getVideos().isEmpty()) continue;

                // Add group header if the group has a title
                if (group.getTitle() != null && !group.getTitle().isEmpty()) {
                    mItems.add(group.getTitle());
                }

                for (Video video : group.getVideos()) {
                    mItems.add(video);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Object item = mItems.get(position);
            if (item instanceof String) {
                return TYPE_GROUP_HEADER;
            }
            Video video = (Video) item;
            return video.isChannel() ? TYPE_CHANNEL : TYPE_VIDEO;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_GROUP_HEADER) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_phone_search_group_header, parent, false);
                return new HeaderViewHolder(view);
            } else if (viewType == TYPE_CHANNEL) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_phone_channel_card, parent, false);
                return new ChannelViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_phone_video_card, parent, false);
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if (lp != null) {
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                }
                return new VideoViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = mItems.get(position);

            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).title.setText((String) item);
                return;
            }

            Video video = (Video) item;

            if (holder instanceof ChannelViewHolder) {
                bindChannel((ChannelViewHolder) holder, video);
            } else {
                bindVideo((VideoViewHolder) holder, video);
            }

            holder.itemView.setOnClickListener(v -> mSearchPresenter.onVideoItemClicked(video));
            holder.itemView.setOnLongClickListener(v -> {
                mSearchPresenter.onVideoItemLongClicked(video);
                return true;
            });

            // Trigger next page load near the end
            if (position >= mItems.size() - 5 && video != null) {
                mSearchPresenter.onScrollEnd(video);
            }
        }

        private void bindVideo(VideoViewHolder holder, Video video) {
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
        }

        private void bindChannel(ChannelViewHolder holder, Video video) {
            holder.channelName.setText(video.getTitle());
            holder.subscriberCount.setText(video.subscriberCount);

            String avatarUrl = video.getCardImageUrl();

            Activity activity = null;
            if (holder.itemView.getContext() instanceof Activity) {
                activity = (Activity) holder.itemView.getContext();
            }
            if (activity != null && !activity.isDestroyed()) {
                Glide.with(activity)
                        .load(avatarUrl)
                        .circleCrop()
                        .into(holder.avatar);
            }
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        class HeaderViewHolder extends RecyclerView.ViewHolder {
            android.widget.TextView title;

            HeaderViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.group_header_title);
            }
        }

        class VideoViewHolder extends RecyclerView.ViewHolder {
            android.widget.ImageView thumbnail;
            android.widget.TextView title;
            android.widget.TextView channelName;
            android.widget.TextView viewsDate;

            VideoViewHolder(@NonNull View itemView) {
                super(itemView);
                thumbnail = itemView.findViewById(R.id.video_thumbnail);
                title = itemView.findViewById(R.id.video_title);
                channelName = itemView.findViewById(R.id.video_channel_name);
                viewsDate = itemView.findViewById(R.id.video_views_date);
            }
        }

        class ChannelViewHolder extends RecyclerView.ViewHolder {
            android.widget.ImageView avatar;
            android.widget.TextView channelName;
            android.widget.TextView subscriberCount;

            ChannelViewHolder(@NonNull View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.channel_avatar);
                channelName = itemView.findViewById(R.id.channel_name);
                subscriberCount = itemView.findViewById(R.id.channel_subscriber_count);
            }
        }
    }
}

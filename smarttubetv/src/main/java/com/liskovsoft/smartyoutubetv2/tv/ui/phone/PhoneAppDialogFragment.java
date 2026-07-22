package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.AppPreferenceManager;

import java.util.List;

public class PhoneAppDialogFragment extends PreferenceFragmentCompat implements AppDialogView {
    private static final String TAG = PhoneAppDialogFragment.class.getSimpleName();
    private AppDialogPresenter mPresenter;
    private AppPreferenceManager mManager;
    private List<OptionCategory> mCategories;
    private CharSequence mTitle;
    private boolean mIsPaused;
    private int mId;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPresenter = AppDialogPresenter.instance(getActivity());
        mPresenter.setView(this);
        mManager = new AppPreferenceManager(getActivity());
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Preferences are built in show() when categories arrive from presenter
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsPaused = false;

        try {
            mPresenter.setView(this);
            mPresenter.onViewInitialized();
        } catch (IllegalStateException e) {
            // NOP
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsPaused = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPresenter.getView() == this) {
            mPresenter.onViewDestroyed();
        }
    }

    private void buildPreferenceScreen(List<OptionCategory> categories, CharSequence title) {
        if (getPreferenceManager() == null || categories == null) {
            return;
        }

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        screen.setTitle(title);
        setPreferenceScreen(screen);

        for (OptionCategory category : categories) {
            if (category.options != null) {
                Preference preference = mManager.createPreference(category);
                if (preference != null) {
                    screen.addPreference(preference);
                }
            }
        }
    }

    // AppDialogView implementation

    @Override
    public void show(List<OptionCategory> categories, CharSequence title, boolean isExpandable, boolean isTransparent, boolean isOverlay, int id) {
        mCategories = categories;
        mTitle = title;
        mId = id;

        if (isExpandable && categories != null && categories.size() == 1) {
            OptionCategory category = categories.get(0);
            if (category.options != null && category.options.size() == 1) {
                // Single expandable option - just click it
                category.options.get(0).onSelect(true);
                return;
            }
        }

        buildPreferenceScreen(categories, title);
    }

    @Override
    public void finish() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @Override
    public void goBack() {
        finish();
    }

    @Override
    public void clearBackstack() {
        // No back stack in phone settings
    }

    @Override
    public boolean canGoBack() {
        return false;
    }

    @Override
    public boolean isShown() {
        return isVisible();
    }

    @Override
    public boolean isTransparent() {
        return false;
    }

    @Override
    public boolean isOverlay() {
        return false;
    }

    @Override
    public boolean isPaused() {
        return mIsPaused;
    }

    @Override
    public int getViewId() {
        return mId;
    }

    public void onFinish() {
        mPresenter.onFinish();
    }
}

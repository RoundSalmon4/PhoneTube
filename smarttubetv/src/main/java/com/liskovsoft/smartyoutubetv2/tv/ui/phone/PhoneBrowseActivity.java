package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.PhoneActivity;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Phone-friendly browse activity. Implements BrowseView and routes section data
 * to the appropriate tab fragment. Manages BottomNavigationView for tab switching.
 */
public class PhoneBrowseActivity extends PhoneActivity implements BrowseView {
    private static final String TAG = PhoneBrowseActivity.class.getSimpleName();
    private static final String TAG_HOME = "phone_home";
    private static final String TAG_SUBSCRIPTIONS = "phone_subscriptions";
    private static final String TAG_LIBRARY = "phone_library";

    private BottomNavigationView mBottomNav;
    private PhoneBrowseFragment mHomeFragment;
    private PhoneSubscriptionsFragment mSubscriptionsFragment;
    private PhoneLibraryFragment mLibraryFragment;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Tracks all sections from BrowsePresenter for selectSection mapping
    private final List<BrowseSection> mAllSections = new ArrayList<>();
    private boolean mPresenterInitialized;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_phone_browse);

        mBottomNav = findViewById(R.id.phone_browse_bottom_nav);

        if (savedInstanceState == null) {
            initFragments();
        } else {
            restoreFragments();
        }

        initBottomNav();
    }

    private void initFragments() {
        mHomeFragment = new PhoneBrowseFragment();
        mSubscriptionsFragment = new PhoneSubscriptionsFragment();
        mLibraryFragment = new PhoneLibraryFragment();

        mHomeFragment.setOnRefreshListener(sectionId ->
                BrowsePresenter.instance(this).loadSectionData(sectionId));

        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction()
                .add(R.id.phone_browse_fragment_container, mHomeFragment, TAG_HOME)
                .add(R.id.phone_browse_fragment_container, mSubscriptionsFragment, TAG_SUBSCRIPTIONS)
                .add(R.id.phone_browse_fragment_container, mLibraryFragment, TAG_LIBRARY)
                .hide(mSubscriptionsFragment)
                .hide(mLibraryFragment)
                .commit();
        fm.executePendingTransactions();
    }

    private void restoreFragments() {
        FragmentManager fm = getSupportFragmentManager();
        mHomeFragment = (PhoneBrowseFragment) fm.findFragmentByTag(TAG_HOME);
        mSubscriptionsFragment = (PhoneSubscriptionsFragment) fm.findFragmentByTag(TAG_SUBSCRIPTIONS);
        mLibraryFragment = (PhoneLibraryFragment) fm.findFragmentByTag(TAG_LIBRARY);

        if (mHomeFragment != null) {
            mHomeFragment.setOnRefreshListener(sectionId ->
                    BrowsePresenter.instance(this).loadSectionData(sectionId));
        }
    }

    private void initBottomNav() {
        mBottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                switchToTab(TAG_HOME);
                return true;
            } else if (itemId == R.id.nav_subscriptions) {
                switchToTab(TAG_SUBSCRIPTIONS);
                // Load subscriptions data without disposing other sections
                BrowsePresenter.instance(this).loadSectionData(MediaGroup.TYPE_SUBSCRIPTIONS);
                return true;
            } else if (itemId == R.id.nav_library) {
                switchToTab(TAG_LIBRARY);
                // Load history data without disposing other sections
                BrowsePresenter.instance(this).loadSectionData(MediaGroup.TYPE_HISTORY);
                return true;
            } else if (itemId == R.id.nav_settings) {
                // Launch settings as a separate screen
                AppDialogPresenter.instance(this).showDialog();
                return false; // Don't select settings tab
            }
            return false;
        });
    }

    private void switchToTab(String tag) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        // Hide all, show target
        if (mHomeFragment != null && !tag.equals(TAG_HOME)) ft.hide(mHomeFragment);
        if (mSubscriptionsFragment != null && !tag.equals(TAG_SUBSCRIPTIONS)) ft.hide(mSubscriptionsFragment);
        if (mLibraryFragment != null && !tag.equals(TAG_LIBRARY)) ft.hide(mLibraryFragment);

        Fragment target = fm.findFragmentByTag(tag);
        if (target != null) ft.show(target);

        ft.commit();
    }

    /**
     * Determine which tab a section belongs to.
     */
    private String getTabForSection(int sectionId) {
        if (sectionId == MediaGroup.TYPE_SUBSCRIPTIONS) return TAG_SUBSCRIPTIONS;
        if (sectionId == MediaGroup.TYPE_HISTORY) return TAG_LIBRARY;
        return TAG_HOME;
    }

    /**
     * Check if a section belongs to the home tab.
     */
    private boolean isHomeSection(int sectionId) {
        return sectionId != MediaGroup.TYPE_SUBSCRIPTIONS
                && sectionId != MediaGroup.TYPE_HISTORY
                && sectionId != MediaGroup.TYPE_SETTINGS;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mPresenterInitialized) {
            mPresenterInitialized = true;
            BrowsePresenter presenter = BrowsePresenter.instance(this);
            presenter.setView(this);
            presenter.onViewInitialized();

            // After all addSection() handler posts complete, fire loadSectionData()
            // for every section in parallel. Stagger by 50ms to avoid network congestion.
            mHandler.post(() -> {
                for (int i = 0; i < mAllSections.size(); i++) {
                    final int idx = i;
                    BrowseSection section = mAllSections.get(i);
                    mHandler.postDelayed(() -> {
                        BrowsePresenter.instance(this).loadSectionData(section.getId());
                    }, (long) idx * 50);
                }
            });
        } else {
            BrowsePresenter.instance(this).setView(this);
        }
    }

    @Override
    protected void onPause() {
        BrowsePresenter.instance(this).onViewPaused();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        BrowsePresenter.instance(this).onViewDestroyed();
        super.onDestroy();
    }

    @Override
    protected void initTheme() {
        int browseThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (browseThemeResId > 0) {
            setTheme(browseThemeResId);
        }
    }

    // --- BrowseView implementation (routes to fragments) ---

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) return;

        // Track all sections for selectSection mapping
        if (index >= 0 && index <= mAllSections.size()) {
            mAllSections.add(index, section);
        } else {
            mAllSections.add(section);
        }

        int id = section.getId();

        if (isHomeSection(id) && mHomeFragment != null) {
            // Convert global index to home fragment's local index
            int localIndex = 0;
            for (int i = 0; i < mAllSections.size(); i++) {
                if (mAllSections.get(i) == section) break;
                if (isHomeSection(mAllSections.get(i).getId())) localIndex++;
            }
            mHomeFragment.addSection(localIndex, section);
        } else if (id == MediaGroup.TYPE_SUBSCRIPTIONS && mSubscriptionsFragment != null) {
            mSubscriptionsFragment.addSection(section);
        } else if (id == MediaGroup.TYPE_HISTORY && mLibraryFragment != null) {
            mLibraryFragment.addSection(section);
        }
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (section == null) return;

        mAllSections.remove(section);

        int id = section.getId();
        if (isHomeSection(id) && mHomeFragment != null) {
            mHomeFragment.removeSection(section);
        } else if (id == MediaGroup.TYPE_SUBSCRIPTIONS && mSubscriptionsFragment != null) {
            mSubscriptionsFragment.clearSection();
        } else if (id == MediaGroup.TYPE_HISTORY && mLibraryFragment != null) {
            mLibraryFragment.clearSection();
        }
    }

    @Override
    public void removeAllSections() {
        mAllSections.clear();

        if (mHomeFragment != null) mHomeFragment.removeAllSections();
        if (mSubscriptionsFragment != null) mSubscriptionsFragment.clearSection();
        if (mLibraryFragment != null) mLibraryFragment.clearSection();
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (index < 0 || index >= mAllSections.size()) return;

        BrowseSection section = mAllSections.get(index);
        String tabTag = getTabForSection(section.getId());

        // Switch to the correct tab
        int navId;
        if (tabTag.equals(TAG_SUBSCRIPTIONS)) {
            navId = R.id.nav_subscriptions;
        } else if (tabTag.equals(TAG_LIBRARY)) {
            navId = R.id.nav_library;
        } else {
            navId = R.id.nav_home;
        }

        if (mBottomNav.getSelectedItemId() != navId) {
            mBottomNav.setOnItemSelectedListener(null);
            mBottomNav.setSelectedItemId(navId);
            initBottomNav();
        }

        switchToTab(tabTag);

        // For home tab, scroll to the right section
        if (tabTag.equals(TAG_HOME) && mHomeFragment != null) {
            int localIndex = 0;
            for (int i = 0; i < mAllSections.size(); i++) {
                if (i == index) break;
                if (isHomeSection(mAllSections.get(i).getId())) localIndex++;
            }
            mHomeFragment.scrollToSection(localIndex);
        }
    }

    @Override
    public void updateSection(VideoGroup group) {
        if (group == null) return;

        BrowseSection section = group.getSection();
        if (section == null) return;

        int id = section.getId();
        if (isHomeSection(id) && mHomeFragment != null) {
            mHomeFragment.updateSection(group);
        } else if (id == MediaGroup.TYPE_SUBSCRIPTIONS && mSubscriptionsFragment != null) {
            mSubscriptionsFragment.updateSection(group);
        } else if (id == MediaGroup.TYPE_HISTORY && mLibraryFragment != null) {
            mLibraryFragment.updateSection(group);
        }
    }

    @Override
    public void updateSection(SettingsGroup group) {
        if (group == null) return;

        BrowseSection section = group.getCategory();
        if (section != null && mHomeFragment != null) {
            mHomeFragment.updateSection(group);
        }
    }

    @Override
    public void clearSection(BrowseSection section) {
        if (section == null) return;

        int id = section.getId();
        if (isHomeSection(id) && mHomeFragment != null) {
            mHomeFragment.clearSection(section);
        } else if (id == MediaGroup.TYPE_SUBSCRIPTIONS && mSubscriptionsFragment != null) {
            mSubscriptionsFragment.clearSection();
        } else if (id == MediaGroup.TYPE_HISTORY && mLibraryFragment != null) {
            mLibraryFragment.clearSection();
        }
    }

    @Override
    public void selectSectionItem(int index) {
    }

    @Override
    public void selectSectionItem(Video item) {
    }

    @Override
    public void showError(ErrorFragmentData data) {
        String message = data != null ? data.getMessage() : "Error";
        // Show error on the active tab
        if (mHomeFragment != null && mHomeFragment.isVisible()) {
            mHomeFragment.showError(message);
        } else if (mSubscriptionsFragment != null && mSubscriptionsFragment.isVisible()) {
            mSubscriptionsFragment.showError(message);
        } else if (mLibraryFragment != null && mLibraryFragment.isVisible()) {
            mLibraryFragment.showError(message);
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        // Route to active tab
        if (mHomeFragment != null && mHomeFragment.isVisible()) {
            mHomeFragment.showProgressBar(show);
        } else if (mSubscriptionsFragment != null && mSubscriptionsFragment.isVisible()) {
            mSubscriptionsFragment.showProgressBar(show);
        } else if (mLibraryFragment != null && mLibraryFragment.isVisible()) {
            mLibraryFragment.showProgressBar(show);
        }
    }

    @Override
    public boolean isProgressBarShowing() {
        return (mHomeFragment != null && mHomeFragment.isVisible());
    }

    @Override
    public void focusOnContent() {
        if (mHomeFragment != null && mHomeFragment.isVisible()) {
            // Home fragment doesn't have focusOnContent, but could scroll to top
        }
    }

    @Override
    public boolean isEmpty() {
        return mAllSections.isEmpty();
    }

    @Override
    public void updateBadge() {
    }
}

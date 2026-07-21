package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.PhoneActivity;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;

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
    private static final String TAG_SETTINGS = "phone_settings";

    private BottomNavigationView mBottomNav;
    private PhoneBrowseFragment mHomeFragment;
    private PhoneSubscriptionsFragment mSubscriptionsFragment;
    private PhoneLibraryFragment mLibraryFragment;
    private PhoneSettingsFragment mSettingsFragment;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

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
        mSettingsFragment = new PhoneSettingsFragment();

        mHomeFragment.setOnRefreshListener(sectionId ->
                BrowsePresenter.instance(this).loadSectionData(sectionId));

        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction()
                .add(R.id.phone_browse_fragment_container, mHomeFragment, TAG_HOME)
                .add(R.id.phone_browse_fragment_container, mSubscriptionsFragment, TAG_SUBSCRIPTIONS)
                .add(R.id.phone_browse_fragment_container, mLibraryFragment, TAG_LIBRARY)
                .add(R.id.phone_browse_fragment_container, mSettingsFragment, TAG_SETTINGS)
                .hide(mSubscriptionsFragment)
                .hide(mLibraryFragment)
                .hide(mSettingsFragment)
                .commit();
        fm.executePendingTransactions();
    }

    private void restoreFragments() {
        FragmentManager fm = getSupportFragmentManager();
        mHomeFragment = (PhoneBrowseFragment) fm.findFragmentByTag(TAG_HOME);
        mSubscriptionsFragment = (PhoneSubscriptionsFragment) fm.findFragmentByTag(TAG_SUBSCRIPTIONS);
        mLibraryFragment = (PhoneLibraryFragment) fm.findFragmentByTag(TAG_LIBRARY);
        mSettingsFragment = (PhoneSettingsFragment) fm.findFragmentByTag(TAG_SETTINGS);

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
                BrowsePresenter.instance(this).loadSectionData(MediaGroup.TYPE_SUBSCRIPTIONS);
                return true;
            } else if (itemId == R.id.nav_library) {
                switchToTab(TAG_LIBRARY);
                BrowsePresenter.instance(this).loadSectionData(MediaGroup.TYPE_HISTORY);
                return true;
            } else if (itemId == R.id.nav_settings) {
                switchToTab(TAG_SETTINGS);
                BrowsePresenter.instance(this).loadSectionData(MediaGroup.TYPE_SETTINGS);
                return true;
            }
            return false;
        });
    }

    private void switchToTab(String tag) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        if (mHomeFragment != null && !tag.equals(TAG_HOME)) ft.hide(mHomeFragment);
        if (mSubscriptionsFragment != null && !tag.equals(TAG_SUBSCRIPTIONS)) ft.hide(mSubscriptionsFragment);
        if (mLibraryFragment != null && !tag.equals(TAG_LIBRARY)) ft.hide(mLibraryFragment);
        if (mSettingsFragment != null && !tag.equals(TAG_SETTINGS)) ft.hide(mSettingsFragment);

        Fragment target = fm.findFragmentByTag(tag);
        if (target != null) ft.show(target);

        ft.commit();
    }

    private void updateTabVisibility() {
        boolean signedIn = YouTubeSignInService.instance().isSigned();

        Menu menu = mBottomNav.getMenu();
        menu.findItem(R.id.nav_subscriptions).setVisible(signedIn);
        menu.findItem(R.id.nav_library).setVisible(signedIn);

        // If currently on a hidden tab, switch to home
        if (!signedIn) {
            String currentTag = getSelectedTabTag();
            if (TAG_SUBSCRIPTIONS.equals(currentTag) || TAG_LIBRARY.equals(currentTag)) {
                mBottomNav.setSelectedItemId(R.id.nav_home);
            }
        }
    }

    private String getSelectedTabTag() {
        int id = mBottomNav.getSelectedItemId();
        if (id == R.id.nav_subscriptions) return TAG_SUBSCRIPTIONS;
        if (id == R.id.nav_library) return TAG_LIBRARY;
        if (id == R.id.nav_settings) return TAG_SETTINGS;
        return TAG_HOME;
    }

    private String getTabForSection(int sectionId) {
        if (sectionId == MediaGroup.TYPE_SUBSCRIPTIONS) return TAG_SUBSCRIPTIONS;
        if (sectionId == MediaGroup.TYPE_HISTORY) return TAG_LIBRARY;
        if (sectionId == MediaGroup.TYPE_SETTINGS) return TAG_SETTINGS;
        return TAG_HOME;
    }

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

            // Update tab visibility based on sign-in state
            updateTabVisibility();

            // Load all sections in parallel, staggered by 50ms
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
            updateTabVisibility();
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

    // --- BrowseView implementation ---

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) return;

        if (index >= 0 && index <= mAllSections.size()) {
            mAllSections.add(index, section);
        } else {
            mAllSections.add(section);
        }

        int id = section.getId();

        if (isHomeSection(id) && mHomeFragment != null) {
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
        } else if (id == MediaGroup.TYPE_SETTINGS && mSettingsFragment != null) {
            mSettingsFragment.addSection(section);
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
        } else if (id == MediaGroup.TYPE_SETTINGS && mSettingsFragment != null) {
            mSettingsFragment.clearSection();
        }
    }

    @Override
    public void removeAllSections() {
        mAllSections.clear();

        if (mHomeFragment != null) mHomeFragment.removeAllSections();
        if (mSubscriptionsFragment != null) mSubscriptionsFragment.clearSection();
        if (mLibraryFragment != null) mLibraryFragment.clearSection();
        if (mSettingsFragment != null) mSettingsFragment.clearSection();
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (index < 0 || index >= mAllSections.size()) return;

        BrowseSection section = mAllSections.get(index);
        String tabTag = getTabForSection(section.getId());

        int navId;
        if (tabTag.equals(TAG_SUBSCRIPTIONS)) {
            navId = R.id.nav_subscriptions;
        } else if (tabTag.equals(TAG_LIBRARY)) {
            navId = R.id.nav_library;
        } else if (tabTag.equals(TAG_SETTINGS)) {
            navId = R.id.nav_settings;
        } else {
            navId = R.id.nav_home;
        }

        if (mBottomNav.getSelectedItemId() != navId) {
            mBottomNav.setOnItemSelectedListener(null);
            mBottomNav.setSelectedItemId(navId);
            initBottomNav();
        }

        switchToTab(tabTag);

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
        if (section == null) return;

        int id = section.getId();
        if (id == MediaGroup.TYPE_SETTINGS && mSettingsFragment != null) {
            mSettingsFragment.updateSection(group);
        } else if (isHomeSection(id) && mHomeFragment != null) {
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
        } else if (id == MediaGroup.TYPE_SETTINGS && mSettingsFragment != null) {
            mSettingsFragment.clearSection();
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
        if (mHomeFragment != null && mHomeFragment.isVisible()) {
            mHomeFragment.showError(message);
        } else if (mSubscriptionsFragment != null && mSubscriptionsFragment.isVisible()) {
            mSubscriptionsFragment.showError(message);
        } else if (mLibraryFragment != null && mLibraryFragment.isVisible()) {
            mLibraryFragment.showError(message);
        } else if (mSettingsFragment != null && mSettingsFragment.isVisible()) {
            // Settings doesn't show errors the same way
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        // Only route to the currently visible fragment to keep counters balanced
        if (mHomeFragment != null && mHomeFragment.isVisible()) {
            mHomeFragment.showProgressBar(show);
        } else if (mSubscriptionsFragment != null && mSubscriptionsFragment.isVisible()) {
            mSubscriptionsFragment.showProgressBar(show);
        } else if (mLibraryFragment != null && mLibraryFragment.isVisible()) {
            mLibraryFragment.showProgressBar(show);
        } else if (mSettingsFragment != null && mSettingsFragment.isVisible()) {
            mSettingsFragment.showProgressBar(show);
        }
    }

    @Override
    public boolean isProgressBarShowing() {
        return false;
    }

    @Override
    public void focusOnContent() {
    }

    @Override
    public boolean isEmpty() {
        return mAllSections.isEmpty();
    }

    @Override
    public void updateBadge() {
    }
}

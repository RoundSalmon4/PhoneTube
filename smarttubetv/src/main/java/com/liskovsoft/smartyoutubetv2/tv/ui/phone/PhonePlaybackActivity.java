package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.annotation.TargetApi;
import android.app.PictureInPictureParams;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.PhoneActivity;

public class PhonePlaybackActivity extends PhoneActivity {
    private static final String TAG = PhonePlaybackActivity.class.getSimpleName();
    private PhonePlaybackFragment mPlaybackFragment;
    private boolean mIsBackPressed;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_playback);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.phone_playback_container, new PhonePlaybackFragment())
                    .commit();
        }

        // Find fragment (works for both fresh start and recreation)
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.phone_playback_container);
        if (fragment instanceof PhonePlaybackFragment) {
            mPlaybackFragment = (PhonePlaybackFragment) fragment;
        }
    }

    @Override
    protected void initTheme() {
        int playerThemeResId = MainUIData.instance(this).getColorScheme().playerThemeResId;
        if (playerThemeResId > 0) {
            setTheme(playerThemeResId);
        } else {
            setTheme(R.style.App_Theme_Phone_Player);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchTouchEvent(event);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        mIsBackPressed = true;
        finish();
    }

    @Override
    protected void onResume() {
        mIsBackPressed = false;
        super.onResume();
    }

    /**
     * Override PhoneActivity.finish() to handle PIP and background playback
     * instead of the standard double-back exit behavior.
     */
    @Override
    public void finish() {
        if (!skipPip()) {
            enterPipMode();
        }

        if (doNotDestroy() && !skipPip()) {
            if (mPlaybackFragment != null) {
                mPlaybackFragment.blockEngine(true);
            }
            getViewManager().blockTop(this);
            getViewManager().startParentView(this);
        } else {
            if (PlayerTweaksData.instance(this).isKeepFinishedActivityEnabled()) {
                getViewManager().startParentView(this);
                if (mPlaybackFragment != null) {
                    mPlaybackFragment.maybeReleasePlayerPublic();
                }
            } else {
                super.finish();
            }
        }
    }

    @Override
    public void finishReally() {
        getViewManager().startParentView(this);
        super.finishReally();
    }

    @Override
    public void onUserLeaveHint() {
        if (mIsBackPressed || isFinishing() || getViewManager().isNewViewPending()
                || GeneralData.instance(this).getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK) {
            return;
        }

        switch (PlayerData.instance(this).getBackgroundMode()) {
            case PlayerData.BACKGROUND_MODE_PIP:
                enterPipMode();
                if (doNotDestroy()) {
                    if (mPlaybackFragment != null) {
                        mPlaybackFragment.blockEngine(true);
                    }
                    getViewManager().blockTop(this);
                }
                break;
            case PlayerData.BACKGROUND_MODE_SOUND:
                if (doNotDestroy()) {
                    if (mPlaybackFragment != null) {
                        mPlaybackFragment.blockEngine(true);
                    }
                    getViewManager().blockTop(this);
                }
                break;
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onPIPChanged(isInPictureInPictureMode);
        }
    }

    @TargetApi(24)
    @SuppressWarnings("deprecation")
    private void enterPipMode() {
        if (Helpers.isPictureInPictureSupported(this) && wannaEnterToPip()) {
            Log.d(TAG, "Entering PIP mode...");
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    PictureInPictureParams.Builder params = new PictureInPictureParams.Builder();
                    enterPictureInPictureMode(params.build());
                } else {
                    enterPictureInPictureMode();
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    public boolean isInPipMode() {
        if (Build.VERSION.SDK_INT < 24) {
            return false;
        }
        return isInPictureInPictureMode();
    }

    @TargetApi(24)
    private boolean wannaEnterToPip() {
        boolean isPip = PlayerData.instance(this).getBackgroundMode() == PlayerData.BACKGROUND_MODE_PIP || isEngineBlocked();
        return isPip && !isInPictureInPictureMode();
    }

    private boolean doNotDestroy() {
        boolean isBackground = PlayerData.instance(this).getBackgroundMode() == PlayerData.BACKGROUND_MODE_SOUND || isEngineBlocked();
        return isInPipMode() || isBackground;
    }

    private boolean skipPip() {
        return mIsBackPressed && GeneralData.instance(this).getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME;
    }

    private boolean isEngineBlocked() {
        return mPlaybackFragment != null && mPlaybackFragment.isEngineBlocked();
    }
}

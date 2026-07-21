package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.os.Build;
import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.PhoneActivity;

public class PhoneAppDialogActivity extends PhoneActivity {
    private static final String TAG = PhoneAppDialogActivity.class.getSimpleName();
    private PhoneAppDialogFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_app_dialog);

        if (savedInstanceState == null) {
            mFragment = new PhoneAppDialogFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.phone_dialog_container, mFragment)
                    .commit();
        } else {
            mFragment = (PhoneAppDialogFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.phone_dialog_container);
        }
    }

    @Override
    protected void initTheme() {
        int settingsThemeResId = MainUIData.instance(this).getColorScheme().settingsThemeResId;
        if (settingsThemeResId > 0) {
            setTheme(settingsThemeResId);
        }
    }

    @Override
    public void finish() {
        // A dialog is transient. Skip PhoneActivity's parent-view navigation on close —
        // that force-launches the browse screen and stomps on whatever a menu action just
        // opened (e.g. "Go to channel" starts the channel activity right before we close).
        // Drop ourselves from the view stack and tear the dialog down with a plain finish;
        // the OS back stack reveals whatever launched us, and a newly started activity stays
        // on top where it belongs.
        if (mFragment != null) {
            mFragment.onFinish();
        }

        getViewManager().removeTop(this);

        try {
            if (Build.VERSION.SDK_INT >= 21) {
                finishAndRemoveTask();
            } else {
                super.finish();
            }
        } catch (Exception e) {
            // TextView not attached to window manager (IllegalArgumentException)
        }
    }

    @Override
    public void finishReally() {
        // Reached only on the app-exit path (properlyFinishTheApp). Keep the normal
        // teardown so move-to-back still works, and let the dialog presenter clean up.
        super.finishReally();

        if (mFragment != null) {
            mFragment.onFinish();
        }
    }
}

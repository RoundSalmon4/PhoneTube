package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

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
        finishReally();
    }

    @Override
    public void finishReally() {
        super.finishReally();

        if (mFragment != null) {
            mFragment.onFinish();
        }
    }
}

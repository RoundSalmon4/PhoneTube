package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.PhoneActivity;

public class PhoneChannelActivity extends PhoneActivity {
    private static final String TAG = PhoneChannelActivity.class.getSimpleName();
    private PhoneChannelFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_channel);

        if (savedInstanceState == null) {
            mFragment = new PhoneChannelFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.phone_channel_container, mFragment)
                    .commit();
        } else {
            mFragment = (PhoneChannelFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.phone_channel_container);
        }
    }

    @Override
    protected void initTheme() {
        int browseThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (browseThemeResId > 0) {
            setTheme(browseThemeResId);
        }
    }

    @Override
    public void finishReally() {
        super.finishReally();

        if (mFragment != null) {
            mFragment.onFinish();
        }
    }
}

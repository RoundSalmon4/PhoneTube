package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.PhoneActivity;

public class PhoneChannelUploadsActivity extends PhoneActivity {
    private static final String TAG = PhoneChannelUploadsActivity.class.getSimpleName();
    private PhoneChannelUploadsFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_channel_uploads);

        if (savedInstanceState == null) {
            mFragment = new PhoneChannelUploadsFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.phone_channel_uploads_container, mFragment)
                    .commit();
        } else {
            mFragment = (PhoneChannelUploadsFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.phone_channel_uploads_container);
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

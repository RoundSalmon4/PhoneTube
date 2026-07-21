package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.os.Bundle;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.PhoneActivity;

public class PhoneWebBrowserActivity extends PhoneActivity {
    private static final String TAG = PhoneWebBrowserActivity.class.getSimpleName();
    private PhoneWebBrowserFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_phone_webbrowser);
        } catch (Exception e) {
            e.printStackTrace();
            MessageHelpers.showMessage(this, e.getMessage());
            finish();
            return;
        }

        if (savedInstanceState == null) {
            mFragment = new PhoneWebBrowserFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.phone_webbrowser_container, mFragment)
                    .commit();
        } else {
            mFragment = (PhoneWebBrowserFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.phone_webbrowser_container);
        }
    }
}

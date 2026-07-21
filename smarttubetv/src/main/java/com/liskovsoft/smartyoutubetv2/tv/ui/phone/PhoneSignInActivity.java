package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.PhoneActivity;

public class PhoneSignInActivity extends PhoneActivity {
    private static final String TAG = PhoneSignInActivity.class.getSimpleName();
    private PhoneSignInFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_signin);

        if (savedInstanceState == null) {
            mFragment = new PhoneSignInFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.phone_signin_container, mFragment)
                    .commit();
        } else {
            mFragment = (PhoneSignInFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.phone_signin_container);
        }
    }

    @Override
    public void finishReally() {
        super.finishReally();
    }
}

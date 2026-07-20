package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.PhoneActivity;

public class PhoneSearchActivity extends PhoneActivity {
    private static final String TAG = PhoneSearchActivity.class.getSimpleName();
    private PhoneSearchFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_search);

        if (savedInstanceState == null) {
            mFragment = new PhoneSearchFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.phone_search_container, mFragment)
                    .commit();
        } else {
            mFragment = (PhoneSearchFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.phone_search_container);
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

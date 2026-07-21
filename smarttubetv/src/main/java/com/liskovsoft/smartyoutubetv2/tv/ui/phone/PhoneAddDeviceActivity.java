package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.PhoneActivity;

public class PhoneAddDeviceActivity extends PhoneActivity {
    private static final String TAG = PhoneAddDeviceActivity.class.getSimpleName();
    private PhoneAddDeviceFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phone_add_device);

        if (savedInstanceState == null) {
            mFragment = new PhoneAddDeviceFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.phone_add_device_container, mFragment)
                    .commit();
        } else {
            mFragment = (PhoneAddDeviceFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.phone_add_device_container);
        }
    }

    @Override
    public void finishReally() {
        super.finishReally();
    }
}

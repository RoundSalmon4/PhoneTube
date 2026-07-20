package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.PhoneActivity;

/**
 * Phone-friendly browse activity. Hosts PhoneBrowseFragment with RecyclerView layout.
 */
public class PhoneBrowseActivity extends PhoneActivity {
    private static final String TAG = PhoneBrowseActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_phone_browse);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.phone_browse_fragment_container, new PhoneBrowseFragment())
                    .commit();
        }
    }

    @Override
    protected void initTheme() {
        int browseThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (browseThemeResId > 0) {
            setTheme(browseThemeResId);
        }
    }
}

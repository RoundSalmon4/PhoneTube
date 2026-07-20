package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.keyhandler.DoubleBackManager2;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * Base activity for phone UI. Extends MotherActivity without Leanback dependencies.
 */
public abstract class PhoneActivity extends MotherActivity {
    private static final String TAG = PhoneActivity.class.getSimpleName();
    private DoubleBackManager2 mDoubleBackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDoubleBackManager = new DoubleBackManager2(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getViewManager().addTop(this);
    }

    @Override
    public void finish() {
        if (!getViewManager().hasParentView(this)) {
            switch (getGeneralData().getAppExitShortcut()) {
                case GeneralData.EXIT_DOUBLE_BACK:
                    mDoubleBackManager.enableDoubleBackExit(this::finishTheApp);
                    break;
                case GeneralData.EXIT_SINGLE_BACK:
                    finishTheApp();
                    break;
            }
        } else {
            finishReally();
        }
    }

    @Override
    public void finishReally() {
        getViewManager().startParentView(this);
        super.finishReally();
    }

    private void finishTheApp() {
        Utils.properlyFinishTheApp(this);
    }
}

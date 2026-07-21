package com.liskovsoft.smartyoutubetv2.tv.ui.phone;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SignInPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class PhoneSignInFragment extends Fragment implements SignInView {
    private static final String TAG = PhoneSignInFragment.class.getSimpleName();
    private SignInPresenter mPresenter;
    private String mFullSignInUrl;
    private TextView mUserCodeView;
    private TextView mDescriptionView;
    private ImageView mQrCodeView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPresenter = SignInPresenter.instance(getContext());
        mPresenter.setView(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_phone_signin, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mQrCodeView = view.findViewById(R.id.signin_qr_code);
        mUserCodeView = view.findViewById(R.id.signin_user_code);
        mDescriptionView = view.findViewById(R.id.signin_description);

        Button continueBtn = view.findViewById(R.id.signin_continue);
        continueBtn.setOnClickListener(v -> mPresenter.onActionClicked());

        Button openBrowserBtn = view.findViewById(R.id.signin_open_browser);
        openBrowserBtn.setOnClickListener(v -> {
            if (mFullSignInUrl != null) {
                Utils.openLinkExt(getContext(), mFullSignInUrl);
            }
        });

        mPresenter.onViewInitialized();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPresenter.onViewDestroyed();
    }

    @Override
    public void showCode(String userCode, String signInUrl) {
        showCode(userCode, signInUrl, null);
    }

    @Override
    public void showCode(String userCode, String signInUrl, String fullSignInUrl) {
        if (getContext() == null || TextUtils.isEmpty(userCode)) {
            return;
        }

        mFullSignInUrl = fullSignInUrl != null ? fullSignInUrl : signInUrl;
        mUserCodeView.setVisibility(View.VISIBLE);
        mUserCodeView.setText(userCode);

        Glide.with(getContext())
                .load(Utils.toQrCodeLink(mFullSignInUrl))
                .placeholder(R.drawable.activate_account_qrcode)
                .apply(ViewUtil.glideOptions())
                .error(R.drawable.activate_account_qrcode)
                .listener(mErrorListener)
                .into(mQrCodeView);

        String description = getString(R.string.signin_view_description, signInUrl);
        int start = description.indexOf(signInUrl);
        int end = start + signInUrl.length();
        CharSequence coloredDescription = Utils.color(description, ContextCompat.getColor(getContext(), R.color.red), start, end);
        mDescriptionView.setText(coloredDescription);
    }

    @Override
    public void close() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private final RequestListener<Drawable> mErrorListener = new RequestListener<Drawable>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            Log.e(TAG, "Glide load failed: " + e);
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            return false;
        }
    };
}

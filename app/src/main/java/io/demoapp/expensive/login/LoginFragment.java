package io.demoapp.expensive.login;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import io.demoapp.expensive.ExpensiveApplication;
import io.demoapp.expensive.R;
import io.demoapp.expensive.databinding.LoginFragBinding;

public class LoginFragment extends Fragment {

    private static final String TAG = "LoginFragment";

    private LoginViewModel mViewModel;
    private LoginFragBinding mViewDataBinding;

    public LoginFragment() {
        Log.i(TAG, "created new login fragment");
    }

    public void setViewModel(LoginViewModel viewModel) {
        this.mViewModel = viewModel;
    }

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.login_frag, container, false);
        if (mViewDataBinding == null) {
            mViewDataBinding = LoginFragBinding.bind(root);
        }

        mViewDataBinding.setViewmodel(mViewModel);

        setHasOptionsMenu(false);
        setRetainInstance(false);

        return mViewDataBinding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        ((ExpensiveApplication)getActivity().getApplication()).getExecutorService()
                .execute(mViewModel::start);
    }
}

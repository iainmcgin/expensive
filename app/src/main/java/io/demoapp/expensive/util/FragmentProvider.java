package io.demoapp.expensive.util;

import android.support.annotation.IdRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;

import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

public class FragmentProvider {

    private static final String TAG = "FragmentProvider";

    private FragmentManager mFragmentManager;

    FragmentProvider(FragmentManager fragmentManager) {
        mFragmentManager = fragmentManager;
    }

    public <T extends Fragment> T get(@IdRes int fragmentId, FragmentCreator<T> fragmentCreator) {
        @SuppressWarnings("unchecked")
        T fragment = (T) mFragmentManager.findFragmentById(fragmentId);

        if (fragment != null) {
            Log.i(TAG, "Returning retained fragment");
            return fragment;
        }

        Log.i(TAG, "No retained fragment, creating a new instance");
        fragment = fragmentCreator.createFragment();
        FragmentTransaction transaction = mFragmentManager.beginTransaction();
        transaction.add(fragmentId, fragment);
        transaction.commit();

        return fragment;
    }

    public interface FragmentCreator<T extends Fragment> {
        T createFragment();
    }
}

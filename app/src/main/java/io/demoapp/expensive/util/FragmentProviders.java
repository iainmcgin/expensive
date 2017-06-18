package io.demoapp.expensive.util;

import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;

public final class FragmentProviders {
    public static FragmentProvider of(@NonNull FragmentActivity activity) {
        return new FragmentProvider(activity.getSupportFragmentManager());
    }
}

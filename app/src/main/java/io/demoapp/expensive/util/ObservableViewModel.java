package io.demoapp.expensive.util;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.ViewModel;
import android.databinding.Observable;
import android.databinding.PropertyChangeRegistry;

import io.demoapp.expensive.ExpensiveApplication;

public class ObservableViewModel extends AndroidViewModel implements Observable {

    private final LazyAtomicReference<PropertyChangeRegistry> mRegistryRef =
            new LazyAtomicReference<PropertyChangeRegistry>() {
                @Override
                protected PropertyChangeRegistry initialize() {
                    return new PropertyChangeRegistry();
                }
            };

    public ObservableViewModel(Application application) {
        super(application);
    }

    @Override
    public void addOnPropertyChangedCallback(OnPropertyChangedCallback callback) {
        mRegistryRef.get().add(callback);
    }

    @Override
    public void removeOnPropertyChangedCallback(OnPropertyChangedCallback callback) {
        mRegistryRef.get().remove(callback);
    }

    public void notifyChange() {
        mRegistryRef.get().notifyCallbacks(this, 0, (Void)null);
    }

    public void notifyPropertyChanged(int fieldId) {
        mRegistryRef.get().notifyCallbacks(this, fieldId, (Void)null);
    }
}

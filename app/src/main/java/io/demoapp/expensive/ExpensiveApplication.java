package io.demoapp.expensive;

import android.app.Application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExpensiveApplication extends Application {

    private ExecutorService mExecutorService;

    public ExpensiveApplication() {
        mExecutorService = Executors.newCachedThreadPool();
    }

    public ExecutorService getExecutorService() {
        return mExecutorService;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        mExecutorService.shutdownNow();
    }
}

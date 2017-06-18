package io.demoapp.expensive.util;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class LazyAtomicReference<T> {

    private final AtomicReference<T> mRef = new AtomicReference<>();
    private final CountDownLatch initLatch = new CountDownLatch(1);
    private final AtomicBoolean initializeStarted = new AtomicBoolean();

    public T get() {
        T value = mRef.get();
        if (value != null) {
            return value;
        }

        if (initializeStarted.compareAndSet(false, true)) {
            // this is the worker thread, initialize the value
            T createdValue = initialize();
            mRef.set(createdValue);
            initLatch.countDown();
            return createdValue;
        }

        // this thread lost the race to initialize the value, wait for initialization
        try {
            initLatch.await();
        } catch (InterruptedException ex) {
            throw new IllegalStateException(
                    "Interrupted while waiting for LazyAtomicReference initialization");
        }
        return mRef.get();
    }

    protected abstract T initialize();
}

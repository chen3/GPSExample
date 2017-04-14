package cn.qiditu.utility;

import android.support.annotation.NonNull;

public final class Lazy<T> {

    public Lazy(@NonNull LazyFunc<T> lazyFunc) {
        this.lazyFunc = lazyFunc;
    }

    private T value;
    private LazyFunc<T> lazyFunc;

    @NonNull
    public T get() {
        if(lazyFunc != null) {
            return compute();
        }
        else {
            return value;
        }
    }

    @NonNull
    private synchronized T compute() {
        if (lazyFunc != null) {
            value = lazyFunc.init();
            lazyFunc = null;
        }
        return value;
    }

    @SuppressWarnings("WeakerAccess")
    public interface LazyFunc<T> {
        @SuppressWarnings("unused")
        @NonNull
        T init();
    }

}

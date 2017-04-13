package cn.qiditu.utility;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

@SuppressWarnings("unused")
public final class Lazy<T> {

    @SuppressWarnings("unused")
    public Lazy(@NonNull LazyFunc<T> lazyFunc) {
        this.lazyFunc = lazyFunc;
    }

    private T value;
    private LazyFunc<T> lazyFunc;

    @SuppressWarnings("unused")
    @Nullable
    public T get() {
        if(lazyFunc != null) {
            return compute();
        }
        else {
            return value;
        }
    }

    @Nullable
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
        @Nullable
        T init();
    }

}

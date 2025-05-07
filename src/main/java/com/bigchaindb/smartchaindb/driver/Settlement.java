package com.bigchaindb.smartchaindb.driver;

import java.util.Objects;

public class Settlement<T> {
    private final boolean fulfilled;
    private final T value;
    private final Throwable reason;

    private Settlement(boolean fulfilled, T value, Throwable reason) {
        this.fulfilled = fulfilled;
        this.value = value;
        this.reason = reason;
    }

    public static <T> Settlement<T> fulfilled(T value) {
        return new Settlement<>(true, value, null);
    }

    public static <T> Settlement<T> rejected(Throwable reason) {
        return new Settlement<>(false, null, reason);
    }

    public boolean isFulfilled() {
        return fulfilled;
    }

    public T getValue() {
        return value;
    }

    public Throwable getReason() {
        return reason;
    }

    @Override
    public String toString() {
        if (fulfilled) {
            return "Fulfilled: " + value;
        } else {
            return "Rejected: " + reason;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Settlement<?> that = (Settlement<?>) o;

        if (fulfilled != that.fulfilled) return false;
        if (!Objects.equals(value, that.value)) return false;
        return Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        int result = (fulfilled ? 1 : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (reason != null ? reason.hashCode() : 0);
        return result;
    }
}


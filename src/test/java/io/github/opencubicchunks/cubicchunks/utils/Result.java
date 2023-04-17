package io.github.opencubicchunks.cubicchunks.utils;

public abstract class Result<T, E> {
    private Result() {
    }

    public static <T, E> Ok<T, E> ok(final T value) {
        return new Ok<>(value);
    }

    public static <T, E> Err<T, E> err(final E value) {
        return new Err<>(value);
    }

    public abstract boolean isOk();

    public abstract boolean isErr();

    public abstract T asOk();

    public abstract E asErr();

    private static final class Ok<T, E> extends Result<T, E> {
        private final T value;

        Ok(final T value) {
            this.value = value;
        }

        @Override
        public boolean isOk() {
            return true;
        }

        @Override
        public boolean isErr() {
            return false;
        }

        @Override
        public T asOk() {
            return value;
        }

        @Override
        public E asErr() {
            return null;
        }
    }

    private static final class Err<T, E> extends Result<T, E> {
        private final E value;

        Err(final E value) {
            this.value = value;
        }

        @Override
        public boolean isOk() {
            return false;
        }

        @Override
        public boolean isErr() {
            return true;
        }

        @Override
        public T asOk() {
            return null;
        }

        @Override
        public E asErr() {
            return value;
        }
    }
}
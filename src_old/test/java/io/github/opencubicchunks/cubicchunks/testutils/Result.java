package io.github.opencubicchunks.cubicchunks.testutils;

import java.util.function.Consumer;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

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

    public abstract void ifOk(Consumer<T> consumer);
    public abstract void ifErr(Consumer<E> consumer);

    @Nullable public abstract <R> R mapOk(Function<T, R> f);
    @Nullable public abstract <R> R mapErr(Function<E, R> f);

    public abstract <R> R map(Function<T, R> okF, Function<E, R> errF);

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

        public void ifOk(Consumer<T> consumer) {
            consumer.accept(this.value);
        }
        public void ifErr(Consumer<E> consumer) {
        }

        @Nullable public <R> R mapOk(Function<T, R> f) {
            return f.apply(this.value);
        }
        @Nullable public <R> R mapErr(Function<E, R> f) {
            return null;
        }

        public <R> R map(Function<T, R> okF, Function<E, R> errF) {
            return okF.apply(this.value);
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

        public void ifOk(Consumer<T> consumer) {
        }
        public void ifErr(Consumer<E> consumer) {
            consumer.accept(this.value);
        }

        @Nullable public <R> R mapOk(Function<T, R> f) {
            return null;
        }
        @Nullable public <R> R mapErr(Function<E, R> f) {
            return f.apply(this.value);
        }

        public <R> R map(Function<T, R> okF, Function<E, R> errF) {
            return errF.apply(this.value);
        }
    }
}
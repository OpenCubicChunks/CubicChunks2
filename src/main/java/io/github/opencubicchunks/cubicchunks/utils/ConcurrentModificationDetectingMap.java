package io.github.opencubicchunks.cubicchunks.utils;

import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConcurrentModificationDetectingMap<KEY, ELEMENT> implements Map<KEY, ELEMENT> {
    private final Map<KEY, ELEMENT> delegate;
    private final AtomicBoolean modifying = new AtomicBoolean();
    private Thread holder = null;

    public ConcurrentModificationDetectingMap(Map<KEY, ELEMENT> delegate) {
        this.delegate = delegate;
    }

    private void getModifying() {
        if (modifying.getAndSet(true)) {
            throw new ConcurrentModificationException();
        }
        holder = Thread.currentThread();
    }

    private void releaseModifying() {
        modifying.set(false);
    }

    private <T> T releaseModifying(T result) {
        modifying.set(false);
        return result;
    }

    @Override
    public ELEMENT computeIfAbsent(KEY key, @NotNull Function<? super KEY, ? extends ELEMENT> mappingFunction) {
        getModifying();
        ELEMENT value = delegate.get(key);
        if (value == null) {
            value = mappingFunction.apply(key);
            delegate.put(key, value);
        }
        return releaseModifying(value);
    }

    @Override
    public ELEMENT computeIfPresent(KEY key, @NotNull BiFunction<? super KEY, ? super ELEMENT, ? extends ELEMENT> remappingFunction) {
        getModifying();
        return releaseModifying(Map.super.computeIfPresent(key, remappingFunction));
    }

    @Override
    public ELEMENT compute(KEY key, @NotNull BiFunction<? super KEY, ? super ELEMENT, ? extends ELEMENT> remappingFunction) {
        return Map.super.compute(key, remappingFunction);
    }

    @Override
    public ELEMENT merge(KEY key, @NotNull ELEMENT value, @NotNull BiFunction<? super ELEMENT, ? super ELEMENT, ? extends ELEMENT> remappingFunction) {
        return Map.super.merge(key, value, remappingFunction);
    }

    @Override public int size() {
        return delegate.size();
    }

    @Override public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public ELEMENT get(Object key) {
        return delegate.get(key);
    }

    @Nullable @Override
    public ELEMENT put(KEY key, ELEMENT value) {
        getModifying();
        return releaseModifying(delegate.put(key, value));
    }

    @Override
    public ELEMENT remove(Object key) {
        getModifying();
        return releaseModifying(delegate.remove(key));
    }

    @Override
    public void putAll(@NotNull Map<? extends KEY, ? extends ELEMENT> m) {
        delegate.putAll(m);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @NotNull @Override
    public Set<KEY> keySet() {
        return delegate.keySet();
    }

    @NotNull @Override
    public Collection<ELEMENT> values() {
        return delegate.values();
    }

    @NotNull @Override
    public Set<Entry<KEY, ELEMENT>> entrySet() {
        return delegate.entrySet();
    }
}

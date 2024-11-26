package io.github.opencubicchunks.cubicchunks.mixin.transform.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.TypeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

public class AncestorHashMap<U extends Ancestralizable<U>, T> implements Map<U, T> {
    private final Map<U, T> map = new HashMap<>();
    private final TypeInfo hierarchy;

    public AncestorHashMap(TypeInfo hierarchy) {
        this.hierarchy = hierarchy;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof Ancestralizable method) {
            for (Type superType : hierarchy.ancestry(method.getAssociatedType())) {
                Ancestralizable id = method.withType(superType);
                if (map.containsKey(id)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public @Nullable T get(Object key) {
        if (key instanceof Ancestralizable method) {
            if (hierarchy.getNode(method.getAssociatedType()) == null) {
                return map.get(method);
            }

            for (Type superType : hierarchy.ancestry(method.getAssociatedType())) {
                Ancestralizable id = method.withType(superType);
                T value = map.get(id);
                if (value != null) {
                    return value;
                }
            }
        }

        return null;
    }

    @Nullable
    @Override
    public T put(U key, T value) {
        return map.put(key, value);
    }

    @Override
    @Nullable
    public T remove(Object key) {
        if (key instanceof Ancestralizable method) {
            for (Type superType : hierarchy.ancestry(method.getAssociatedType())) {
                Ancestralizable<?> id = method.withType(superType);
                T value = map.remove(key);
                if (value != null) {
                    return value;
                }
            }
        }

        return null;
    }

    @Override
    public void putAll(@NotNull Map<? extends U, ? extends T> m) {
        map.putAll(m);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @NotNull
    @Override
    public Set<U> keySet() {
        return map.keySet();
    }

    @NotNull
    @Override
    public Collection<T> values() {
        return map.values();
    }

    @NotNull
    @Override
    public Set<Entry<U, T>> entrySet() {
        return map.entrySet();
    }
}

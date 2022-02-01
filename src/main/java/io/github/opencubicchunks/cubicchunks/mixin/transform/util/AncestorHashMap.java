package io.github.opencubicchunks.cubicchunks.mixin.transform.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.opencubicchunks.cubicchunks.mixin.transform.typetransformer.transformer.config.HierarchyTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

public class AncestorHashMap<U extends Ancestralizable<U>, T> implements Map<U, T> {
    private final Map<U, T> map = new HashMap<>();
    private final HierarchyTree hierarchy;

    public AncestorHashMap(HierarchyTree hierarchy) {
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
            for (Type subType : hierarchy.ancestry(method.getAssociatedType())) {
                Ancestralizable id = method.withType(subType);
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
    public T get(Object key) {
        if (key instanceof Ancestralizable method) {
            if (hierarchy.getNode(method.getAssociatedType()) == null) {
                return map.get(method);
            }

            for (Type subType : hierarchy.ancestry(method.getAssociatedType())) {
                Ancestralizable id = method.withType(subType);
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
        HierarchyTree.Node current = hierarchy.getNode(key.getAssociatedType());

        HierarchyTree.Node[] nodes = keySet()
            .stream()
            .filter(val -> val.equalsWithoutType(key) && get(val).equals(value))
            .map(val -> hierarchy.getNode(val.getAssociatedType()))
            .toArray(HierarchyTree.Node[]::new);

        return map.put(key, value);
    }

    /**
     * Checks if all the objects' <em>identities</em> are the same
     */
    private static boolean areAllEqual(Object a, Object... others) {
        for (Object o : others) {
            if (a != o) {
                return false;
            }
        }
        return true;
    }

    @Override
    public T remove(Object key) {
        if (key instanceof Ancestralizable method) {
            for (Type subType : hierarchy.ancestry(method.getAssociatedType())) {
                Ancestralizable<?> id = method.withType(subType);
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

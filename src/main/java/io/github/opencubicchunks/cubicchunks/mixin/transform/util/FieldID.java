package io.github.opencubicchunks.cubicchunks.mixin.transform.util;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

public record FieldID(Type owner, String name, Type desc) implements Ancestralizable<FieldID> {
    @Override
    public Type getAssociatedType() {
        return owner;
    }

    @Override
    public FieldID withType(Type type) {
        return new FieldID(type, name, desc);
    }

    @Override
    public String toString() {
        return ASMUtil.onlyClassName(owner.getClassName()) + "." + name;
    }

    public FieldNode toNode(int access) {
        return toNode(null, access);
    }

    public FieldNode toNode(@Nullable Object defaultValue, int access) {
        return new FieldNode(access, name, desc.getDescriptor(), null, defaultValue);
    }
}

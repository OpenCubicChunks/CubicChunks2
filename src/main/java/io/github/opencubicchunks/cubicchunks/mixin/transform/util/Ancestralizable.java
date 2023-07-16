package io.github.opencubicchunks.cubicchunks.mixin.transform.util;

import org.objectweb.asm.Type;

public interface Ancestralizable<T extends Ancestralizable<T>> {
    Type getAssociatedType();
    T withType(Type type);
}

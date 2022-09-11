package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SortedArraySet.class)
public interface SortedArraySetAccess<T> {
    @Accessor
    T[] getContents();

    @Invoker
    void invokeRemoveInternal(int idx);
}

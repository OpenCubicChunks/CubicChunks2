package io.github.opencubicchunks.cubicchunks.mixin.core.common.util;

import java.util.Comparator;

import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SortedArraySet.class)
public class MixinSortedArraySet<T> {
    @Shadow @Final @Mutable private Comparator<T> comparator;

    /**
     * Inverting the internal sorting to optimise the {@link SortedArraySet#iterator()} remove implementation<p>
     * See {@link MixinSortedArraySetArrayIterator}
     */
    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/util/SortedArraySet;comparator:Ljava/util/Comparator;"))
    private void invertComparator(SortedArraySet<T> instance, Comparator<T> value) {
        this.comparator = (v1, v2) -> -value.compare(v1, v2);
    }
}

package io.github.opencubicchunks.cubicchunks.mixin.core.common.util;

import java.util.NoSuchElementException;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.SortedArraySetAccess;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@SuppressWarnings("unchecked")
@Mixin(targets = "net.minecraft.util.SortedArraySet$ArrayIterator")
public class MixinSortedArraySetArrayIterator<T> {

    @Shadow @Final SortedArraySet<T> this$0;

    @Shadow private int index = this$0.size() - 1;

    @Shadow private int last;

    /**
     * @author NotStirred
     * @reason Vanilla's Iterator starts at idx=0 and goes up, we invert that order so that {@link MixinSortedArraySetArrayIterator#remove} is faster
     */
    @Overwrite
    public boolean hasNext() {
        return this.index >= 0;
    }

    /**
     * @author NotStirred
     * @reason Vanilla's Iterator starts at idx=0 and goes up, we invert that order so that {@link MixinSortedArraySetArrayIterator#remove} is faster
     */
    @Overwrite
    public T next() {
        if (this.index < 0) {
            throw new NoSuchElementException();
        } else {
            this.last = this.index--;
            return (T) ((SortedArraySetAccess<T>) this$0).getContents()[this.last];
        }
    }

    /**
     * @author NotStirred
     * @reason Vanilla's Iterator starts at idx=0 and goes up, we invert that order so that {@link MixinSortedArraySetArrayIterator#remove} is faster
     */
    @Overwrite
    public void remove() {
        if (this.last == -1) {
            throw new IllegalStateException();
        } else {
            ((SortedArraySetAccess<T>) this$0).invokeRemoveInternal(this.last);
            --this.index;
            this.last = -1;
        }
    }
}

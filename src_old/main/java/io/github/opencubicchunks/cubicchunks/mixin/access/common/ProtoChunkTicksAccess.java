package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ProtoChunkTicks.class)
public interface ProtoChunkTicksAccess<T> {
    @Invoker
    void callSchedule(SavedTick<T> tick);
}

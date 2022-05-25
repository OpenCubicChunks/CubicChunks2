package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import java.util.function.BiConsumer;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelTicks.class)
public interface LevelTicksAccess<T> {
    @Accessor
    Long2ObjectMap<LevelChunkTicks<T>> getAllContainers();

    @Accessor
    Long2LongMap getNextTickForContainer();

    @Accessor
    BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> getChunkScheduleUpdater();
}

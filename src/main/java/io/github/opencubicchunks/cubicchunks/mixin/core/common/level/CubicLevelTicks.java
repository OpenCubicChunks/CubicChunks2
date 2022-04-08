package io.github.opencubicchunks.cubicchunks.mixin.core.common.level;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.chunk.entity.ChunkEntityStateEventHandler;
import io.github.opencubicchunks.cubicchunks.world.CubicServerTickList;
import io.github.opencubicchunks.cubicchunks.world.level.CubePos;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;

//TODO: Make this optimized again. This is the replacement for "CubicFastServerTickList"
public class CubicLevelTicks<T> extends LevelTicks<T> implements ChunkEntityStateEventHandler, CubicServerTickList<T> {
    private final BiConsumer<LevelChunkTicks<T>, ScheduledTick<T>> cubeScheduleUpdater;

    public CubicLevelTicks(LongPredicate longPredicate, Supplier<ProfilerFiller> supplier) {
        super(longPredicate, supplier);

        this.cubeScheduleUpdater = null; //TODO: Implement this
    }

    public void addContainer(CubePos cubePos, LevelChunkTicks<T> container) {

    }

    @Override
    public void onCubeEntitiesLoad(CubePos pos) {

    }

    @Override
    public void onCubeEntitiesUnload(CubePos pos) {

    }

    @Override
    public List<TickNextTickData<T>> fetchTicksInCube(CubePos pos, boolean updateState, boolean getStaleTicks) {

    }

    private
}

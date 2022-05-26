package io.github.opencubicchunks.cubicchunks.world.level;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.chunk.entity.ChunkEntityStateEventHandler;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.LevelTicksAccess;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.utils.Utils;
import io.github.opencubicchunks.cubicchunks.world.CubicServerTickList;
import io.github.opencubicchunks.cubicchunks.world.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.world.level.CubePos;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;

//TODO: Make this optimized again. This is the replacement for "CubicFastServerTickList"
public class CubicLevelTicks<T> extends LevelTicks<T> implements ChunkEntityStateEventHandler, CubicServerTickList<T> {
    public CubicLevelTicks(LongPredicate longPredicate, Supplier<ProfilerFiller> supplier) {
        super(longPredicate, supplier);
    }

    //LevelTicks
    public void addContainer(CubePos cubePos, LevelChunkTicks<T> ticks) {
        long l = cubePos.asLong();
        access().getAllContainers().put(l, ticks);

        ScheduledTick<T> tick = ticks.peek();

        if (tick != null) {
            access().getNextTickForContainer().put(l, tick.triggerTick());
        }

        ticks.setOnTickAdded(access().getChunkScheduleUpdater());
    }

    @Override
    public void addContainer(ChunkPos chunkPos, LevelChunkTicks<T> levelChunkTicks) {
        if (chunkPos instanceof ImposterChunkPos imposter) {
            this.addContainer(imposter.toCubePos(), levelChunkTicks);
        } else {
            throw new RuntimeException("Tried to add chunk to cubic level ticks");
        }
    }

    public void removeContainer(CubePos cubePos) {
        long l = cubePos.asLong();
        LevelChunkTicks<T> ticks = access().getAllContainers().remove(l);
        access().getNextTickForContainer().remove(l);
        if (ticks != null) {
            ticks.setOnTickAdded(null);
        }
    }

    @Override
    public void removeContainer(ChunkPos chunkPos) {
        if (chunkPos instanceof ImposterChunkPos imposter) {
            this.removeContainer(imposter.toCubePos());
        } else {
            throw new RuntimeException("Tried to remove chunk from cubic level ticks");
        }
    }

    @Override
    public void schedule(ScheduledTick<T> scheduledTick) {
        LevelChunkTicks<T> ticks = access().getAllContainers().get(CubePos.asLong(scheduledTick.pos()));

        if (ticks == null) {
            throw new RuntimeException("Cube not loaded!");
        } else {
            ticks.schedule(scheduledTick);
        }
    }

    @Override
    protected void updateContainerScheduling(ScheduledTick<T> scheduledTick) {
        access().getNextTickForContainer().put(CubePos.asLong(scheduledTick.pos()), scheduledTick.triggerTick());
    }

    @Override
    public boolean hasScheduledTick(BlockPos blockPos, T object) {
        LevelChunkTicks<T> ticks = access().getAllContainers().get(CubePos.asLong(blockPos));
        return ticks != null && ticks.hasScheduledTick(blockPos, object);
    }

    @Override
    protected void forContainersInArea(BoundingBox boundingBox, LevelTicks.PosAndContainerConsumer<T> posAndContainerConsumer) {
        int minCubeX = Coords.blockToCube(boundingBox.minX());
        int minCubeY = Coords.blockToCube(boundingBox.minY());
        int minCubeZ = Coords.blockToCube(boundingBox.minZ());
        int maxCubeX = Coords.blockToCube(boundingBox.maxX());
        int maxCubeY = Coords.blockToCube(boundingBox.maxY());
        int maxCubeZ = Coords.blockToCube(boundingBox.maxZ());

        for (int cubeX = minCubeX; cubeX <= maxCubeX; cubeX++) {
            for (int cubeY = minCubeY; cubeY <= maxCubeY; cubeY++) {
                for (int cubeZ = minCubeZ; cubeZ <= maxCubeZ; cubeZ++) {
                    long cubePos = CubePos.asLong(cubeX, cubeY, cubeZ);
                    LevelChunkTicks<T> ticks = access().getAllContainers().get(cubePos);
                    if (ticks != null) {
                        posAndContainerConsumer.accept(cubePos, ticks);
                    }
                }
            }
        }
    }


    //ChunkEntityStateEventHandler
    @Override
    public void onCubeEntitiesLoad(CubePos pos) {
        //TODO: Do I still need to do stuff?
    }

    @Override
    public void onCubeEntitiesUnload(CubePos pos) {
        //Same goes for this
    }

    private LevelTicksAccess<T> access() {
        return Utils.unsafeCast(this);
    }
}

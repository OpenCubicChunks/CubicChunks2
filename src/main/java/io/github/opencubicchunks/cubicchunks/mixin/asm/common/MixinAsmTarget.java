package io.github.opencubicchunks.cubicchunks.mixin.asm.common;

import io.github.opencubicchunks.cubicchunks.server.level.CubeTaskPriorityQueue;
import io.github.opencubicchunks.cubicchunks.server.level.CubeTaskPriorityQueueSorter;
import io.github.opencubicchunks.cubicchunks.server.level.CubeTicketTracker;
import io.github.opencubicchunks.cubicchunks.server.level.CubeTickingTracker;
import io.github.opencubicchunks.cubicchunks.server.level.FixedPlayerDistanceCubeTracker;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {
    ChunkStatus.class,
    ChunkMap.DistanceManager.class,
    ChunkMap.class,
    ChunkHolder.class,
    NaturalSpawner.class,
    DistanceManager.class,
    ChunkTaskPriorityQueue.class,
    CubeTaskPriorityQueue.class,
    ChunkTaskPriorityQueueSorter.class,
    CubeTaskPriorityQueueSorter.class,
    DistanceManager.class,
    DistanceManager.ChunkTicketTracker.class,
    CubeTicketTracker.class,
    TickingTracker.class,
    CubeTickingTracker.class,
    DistanceManager.FixedPlayerDistanceChunkTracker.class,
    FixedPlayerDistanceCubeTracker.class
})
public class MixinAsmTarget {
    // intentionally empty
}
package io.github.opencubicchunks.cubicchunks.mixin.asm.common;

import io.github.opencubicchunks.cubicchunks.server.level.CubeTaskPriorityQueue;
import io.github.opencubicchunks.cubicchunks.server.level.CubeTaskPriorityQueueSorter;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({
    ChunkMap.DistanceManager.class,
    ChunkMap.class,
    ChunkHolder.class,
    NaturalSpawner.class,
    DistanceManager.class,
    ChunkTaskPriorityQueue.class,
    CubeTaskPriorityQueue.class,
    ChunkTaskPriorityQueueSorter.class,
    CubeTaskPriorityQueueSorter.class
})
public class MixinAsmTarget {
    // intentionally empty
}
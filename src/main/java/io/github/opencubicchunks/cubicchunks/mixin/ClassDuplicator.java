package io.github.opencubicchunks.cubicchunks.mixin;

import io.github.opencubicchunks.cubicchunks.server.level.CubeTaskPriorityQueue;
import io.github.opencubicchunks.cubicchunks.server.level.CubeTaskPriorityQueueSorter;
import io.github.opencubicchunks.cubicchunks.server.level.CubeTicketTracker;
import io.github.opencubicchunks.cubicchunks.server.level.CubeTickingTracker;
import io.github.opencubicchunks.cubicchunks.server.level.FixedPlayerDistanceCubeTracker;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;

public class ClassDuplicator {
    public static void init() {
        // ensures class loading order for the classes to transform correctly
        load(
            ChunkTaskPriorityQueue.class,
            CubeTaskPriorityQueue.class,

            ChunkTaskPriorityQueueSorter.class,
            CubeTaskPriorityQueueSorter.class,

            DistanceManager.ChunkTicketTracker.class,
            CubeTicketTracker.class,

            TickingTracker.class,
            CubeTickingTracker.class,

            DistanceManager.FixedPlayerDistanceChunkTracker.class,
            FixedPlayerDistanceCubeTracker.class
        );
    }

    private static void load(Class<?>... cl) {
        // noop
    }
}

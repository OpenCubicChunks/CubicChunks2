package io.github.opencubicchunks.cubicchunks.mixin;

import io.github.opencubicchunks.cubicchunks.server.level.CubeTaskPriorityQueue;
import io.github.opencubicchunks.cubicchunks.server.level.CubeTaskPriorityQueueSorter;
import net.minecraft.server.level.ChunkTaskPriorityQueue;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;

public class ClassDuplicator {
    public static void init() {
        // ensures class loading order for the classes to transform correctly
        ChunkTaskPriorityQueue.class.getName();
        CubeTaskPriorityQueue.class.getName();

        ChunkTaskPriorityQueueSorter.class.getName();
        CubeTaskPriorityQueueSorter.class.getName();
    }
}

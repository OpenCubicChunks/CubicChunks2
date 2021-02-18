package io.github.opencubicchunks.cubicchunks.server.level.progress;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.level.CubePos;
import net.minecraft.world.level.chunk.ChunkStatus;

public interface CubeProgressListener {
    default void startCubes(CubePos center) {
    }

    default void onCubeStatusChange(CubePos cubePos, @Nullable ChunkStatus newStatus) {
    }
    //Interface does not have a stopCubes(); because the equivalent stop for chunks does the same thing, and is called at the same time.
}
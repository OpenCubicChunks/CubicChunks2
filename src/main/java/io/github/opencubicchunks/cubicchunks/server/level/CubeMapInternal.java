package io.github.opencubicchunks.cubicchunks.server.level;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.world.level.chunk.ChunkStatus;

public interface CubeMapInternal {

    // methods added by ASM that we need to use directly
    boolean isExistingCubeFull(CubePos pos);

    ChunkStatus getCubeDependencyStatus(ChunkStatus status, int distance);
}

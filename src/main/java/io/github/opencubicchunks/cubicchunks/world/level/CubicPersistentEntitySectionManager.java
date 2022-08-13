package io.github.opencubicchunks.cubicchunks.world.level;

import io.github.opencubicchunks.cc_core.api.CubePos;
import net.minecraft.world.level.ChunkPos;

public interface CubicPersistentEntitySectionManager {
    boolean isCubeTicking(CubePos pos);

    boolean isChunkTicking(ChunkPos pos);
}

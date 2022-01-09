package io.github.opencubicchunks.cubicchunks.world.level;

import net.minecraft.world.level.ChunkPos;

public interface CubicPersistentEntitySectionManager {
    boolean isCubeTicking(CubePos pos);

    boolean isChunkTicking(ChunkPos pos);
}

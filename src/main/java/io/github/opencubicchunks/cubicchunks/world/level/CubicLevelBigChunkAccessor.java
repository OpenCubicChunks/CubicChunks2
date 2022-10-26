package io.github.opencubicchunks.cubicchunks.world.level;

import java.util.concurrent.ConcurrentHashMap;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.BigChunk;

public interface CubicLevelBigChunkAccessor {
    /*@Nullable*/ BigChunk getBigChunk(int bigChunkX, int bigChunkZ);
    ConcurrentHashMap<CubePos, BigChunk> getBigChunkMap();
    void removeBigChunk(int bigChunkX, int bigChunkZ);
}

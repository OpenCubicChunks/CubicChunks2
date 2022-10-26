package io.github.opencubicchunks.cubicchunks.world;

import java.util.HashMap;
import java.util.Map;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.BigLightSurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.BigSurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.world.server.CubicServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

public class BigChunk {
    private final ChunkAccess[] chunks;
    // can we just count non-nulls in chunks?
    private int chunkCount;

    private BigLightSurfaceTrackerWrapper lightHeightmap;
    private final Map<Heightmap.Types, BigSurfaceTrackerWrapper> heightmaps;

    // TODO create BigChunkPos instead of using cubeX cubeZ
    private final int cubeX;
    private final int cubeZ;

    final Level level;

    public BigChunk(Level level, int cubeX, int cubeZ) {
        chunks = new ChunkAccess[CubicConstants.CHUNK_COUNT];
        this.cubeX = cubeX;
        this.cubeZ = cubeZ;
        this.level = level;
        this.heightmaps = new HashMap<>();
    }

    public CubePos getPos() {
        return CubePos.of(this.cubeX, 0, this.cubeZ);
    }

    private static int chunkIndex(ChunkPos pos) {
        return Coords.columnToColumnIndex(Coords.cubeLocalSection(pos.x), Coords.cubeLocalSection(pos.z));
    }

    public LevelHeightAccessor getHeightAccessor() {
        return this.level;
    }

    public void loadChunk(ChunkAccess chunk) {
        var index = chunkIndex(chunk.getPos());
        // FIXME handle loading properly and reenable assertions
//        assert chunks[index] == null : "attempted to load already loaded chunk in BigChunk";
        if (chunks[index] == null) {
            chunkCount++;
        }
        chunks[index] = chunk;
    }

    public void promoteProtoToFullChunk(ChunkAccess chunk) {
        var index = chunkIndex(chunk.getPos());
        assert chunks[index] != null;
        chunks[index] = chunk;
    }

    public void unloadChunk(ChunkAccess chunk) {
        var index = chunkIndex(chunk.getPos());
        assert chunks[index] != null : "attempted to unload already unloaded chunk in BigChunk";
        assert chunks[index] == chunk : "??? how";
        chunks[index] = null;
        chunkCount--;
        if (chunkCount <= 0) {
            this.unload();
        }
    }

    // TODO add separate getOrCreate, and make these ones nullable instead
    public BigLightSurfaceTrackerWrapper getServerLightHeightmap() {
        if (lightHeightmap == null) {
            lightHeightmap = new BigLightSurfaceTrackerWrapper(this, ((CubicServerLevel) this.level).getHeightmapStorage());
        }
        return lightHeightmap;
    }

    public BigSurfaceTrackerWrapper getServerHeightmap(Heightmap.Types type) {
        return this.heightmaps.computeIfAbsent(type,
            t -> new BigSurfaceTrackerWrapper(this, t, ((CubicServerLevel) this.level).getHeightmapStorage()));
    }

    public Map<Heightmap.Types, BigSurfaceTrackerWrapper> getHeightmaps() {
        return heightmaps;
    }

    private void unload() {
        // TODO actually unload heightmaps
        lightHeightmap = null;
        this.heightmaps.clear();
    }
}

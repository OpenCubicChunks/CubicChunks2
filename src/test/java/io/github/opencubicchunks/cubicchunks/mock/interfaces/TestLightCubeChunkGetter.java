package io.github.opencubicchunks.cubicchunks.mock.interfaces;

import java.util.HashMap;
import java.util.Map;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightCubeGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.jetbrains.annotations.Nullable;

/**
 * Exists to mock both {@link LightChunkGetter} and {@link LightCubeGetter} at once
 */
public class TestLightCubeChunkGetter implements LightChunkGetter, LightCubeGetter {
    private final BlockGetter level;
    private final Map<CubePos, BlockGetter> cubeMap = new HashMap<>();
    private final Map<ChunkPos, BlockGetter> chunkMap = new HashMap<>();

    public TestLightCubeChunkGetter(BlockGetter level) {
        this.level = level;
    }

    public void setCube(CubePos cubePos, BlockGetter cube) {
        this.cubeMap.put(cubePos, cube);
    }
    public void setChunk(ChunkPos chunkPos, BlockGetter chunk) {
        this.chunkMap.put(chunkPos, chunk);
    }

    @Nullable @Override public BlockGetter getCubeForLighting(int cubeX, int cubeY, int cubeZ) {
        return this.cubeMap.get(CubePos.of(cubeX, cubeY, cubeZ));
    }

    @Nullable @Override public BlockGetter getChunkForLighting(int chunkX, int chunkZ) {
        return this.chunkMap.get(new ChunkPos(chunkX, chunkZ));
    }

    @Override public BlockGetter getLevel() {
        return this.level;
    }
}

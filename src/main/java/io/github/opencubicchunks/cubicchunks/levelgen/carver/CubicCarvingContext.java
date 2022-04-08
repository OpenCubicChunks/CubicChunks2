package io.github.opencubicchunks.cubicchunks.levelgen.carver;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.carver.CarvingContext;

public class CubicCarvingContext extends CarvingContext {

    private final ChunkAccess chunk;

    public CubicCarvingContext(NoiseBasedChunkGenerator generator, RegistryAccess registryAccess, ChunkAccess chunk, NoiseChunk noiseChunk) {
        super(generator, registryAccess, chunk, noiseChunk);
        this.chunk = chunk;
    }


    @Override public int getMinGenY() {
        return chunk.getMinBuildHeight();
    }

    @Override public int getGenDepth() {
        return chunk.getHeight();
    }

    public int getOriginalMinGenY() {
        return super.getMinGenY();
    }

    public int getOriginalGenDepth() {
        return super.getGenDepth();
    }
}

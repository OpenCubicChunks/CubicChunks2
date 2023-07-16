package io.github.opencubicchunks.cubicchunks.levelgen.carver;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.carver.CarvingContext;

public class CubicCarvingContext extends CarvingContext {
    private final LevelHeightAccessor chunk;

    public CubicCarvingContext(NoiseBasedChunkGenerator generator, RegistryAccess registryAccess, LevelHeightAccessor chunk, NoiseChunk noiseChunk,
                          RandomState randomState, SurfaceRules.RuleSource ruleSource) {
        super(generator, registryAccess, chunk, noiseChunk, randomState, ruleSource);
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

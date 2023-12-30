package io.github.opencubicchunks.cubicchunks.world.level.chunklike;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.FeatureAccess;

public interface CloAccess extends BlockGetter, BiomeManager.NoiseBiomeSource, FeatureAccess, CubicLevelHeightAccessor {

    default CloPos getCloPos() {
        if (this instanceof CubeAccess) {
            return CloPos.cube(((CubeAccess) this).getCubePos());
        } else {
            return CloPos.column(((ChunkAccess) this).getPos());
        }
    }
}

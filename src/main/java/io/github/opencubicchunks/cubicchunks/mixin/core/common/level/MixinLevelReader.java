package io.github.opencubicchunks.cubicchunks.mixin.core.common.level;

import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelReader.class)
public interface MixinLevelReader {
    @Shadow Holder<Biome> getUncachedNoiseBiome(int x, int y, int z);

    /**
     * @author Salamander
     */
    @Overwrite
    default Holder<Biome> getNoiseBiome(int x, int y, int z) {
        CubicLevelAccessor cubicLevelAccessor = (CubicLevelAccessor) this;
        ChunkAccess cube = cubicLevelAccessor.getCube(
            QuartPos.fromBlock(x),
            QuartPos.fromBlock(y),
            QuartPos.fromBlock(z),
            ChunkStatus.BIOMES,
            false
        );

        return cube != null ? cube.getNoiseBiome(x, y, z) : this.getUncachedNoiseBiome(x, y, z);
    }
}

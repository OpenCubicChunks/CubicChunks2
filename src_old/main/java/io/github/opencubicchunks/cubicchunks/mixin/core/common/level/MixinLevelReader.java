package io.github.opencubicchunks.cubicchunks.mixin.core.common.level;

import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelReader;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelReader.class)
public interface MixinLevelReader extends CubicLevelReader {
    @Shadow Holder<Biome> getUncachedNoiseBiome(int x, int y, int z);

    @Shadow @Nullable ChunkAccess getChunk(int x, int z, ChunkStatus requiredStatus, boolean nonnull);

    /**
     * @author Salamander, NotStirred
     * @reason chunk to cube
     */
    @Overwrite
    default Holder<Biome> getNoiseBiome(int x, int y, int z) {
        if (this.isCubic()) {
            ChunkAccess cube = ((CubicLevelAccessor) this).getCube(
                QuartPos.fromBlock(x),
                QuartPos.fromBlock(y),
                QuartPos.fromBlock(z),
                ChunkStatus.BIOMES,
                false
            );
            return cube != null ? cube.getNoiseBiome(x, y, z) : this.getUncachedNoiseBiome(x, y, z);
        } else {
            //vanilla logic
            ChunkAccess chunkAccess = this.getChunk(QuartPos.toSection(x), QuartPos.toSection(z), ChunkStatus.BIOMES, false);
            return chunkAccess != null ? chunkAccess.getNoiseBiome(x, y, z) : this.getUncachedNoiseBiome(x, y, z);
        }
    }
}

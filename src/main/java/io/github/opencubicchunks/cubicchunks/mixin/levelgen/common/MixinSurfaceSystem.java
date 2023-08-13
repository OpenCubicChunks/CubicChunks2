package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(SurfaceSystem.class)
public abstract class MixinSurfaceSystem {
    @Redirect(method = "buildSurface",
        slice = @Slice(
            from = @At(value = "FIELD", target = "Lnet/minecraft/world/level/dimension/DimensionType;WAY_BELOW_MIN_Y:I")
        ),
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/BlockColumn;getBlock(I)Lnet/minecraft/world/level/block/state/BlockState;"
        ))
    private BlockState modifyLoop(BlockColumn instance, int yPos,
                                  RandomState randomState,
                                  BiomeManager biomeManager,
                                  Registry<Biome> registry,
                                  boolean bl,
                                  WorldGenerationContext worldGenerationContext,
                                  ChunkAccess chunkAccess) {
        if (((CubicLevelHeightAccessor) chunkAccess).isCubic() && yPos < chunkAccess.getMinBuildHeight()) {
            return Blocks.STONE.defaultBlockState();
        }
        return instance.getBlock(yPos);
    }

    //TODO: fix frozenOceanExtension instead of cancelling it
    @Redirect(method = "buildSurface", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceSystem;frozenOceanExtension(ILnet/minecraft/world/level/biome/Biome;"
        + "Lnet/minecraft/world/level/chunk/BlockColumn;Lnet/minecraft/core/BlockPos$MutableBlockPos;III)V"))
    private void cancelExtension(SurfaceSystem instance, int i, Biome biome, BlockColumn blockColumn, BlockPos.MutableBlockPos mutableBlockPos, int j, int k, int l) {

    }
}

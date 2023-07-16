package io.github.opencubicchunks.cubicchunks.mixin.core.common.level.lighting;

import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightCubeGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("rawtypes")
@Mixin(BlockLightEngine.class)
public abstract class MixinBlockLightEngine extends MixinLightEngine {

    /**
     * @author NotStirred
     * @reason Vanilla lighting is bye bye
     */
    @Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true)
    private void getLightEmission(long blockPos, CallbackInfoReturnable<Integer> cir) {
        if (!((CubicLevelHeightAccessor) this.chunkSource.getLevel()).isCubic()) {
            return;
        }
        cir.cancel();
        int blockX = BlockPos.getX(blockPos);
        int blockY = BlockPos.getY(blockPos);
        int blockZ = BlockPos.getZ(blockPos);
        BlockGetter cube = ((LightCubeGetter) this.chunkSource).getCubeForLighting(
            Coords.blockToCube(blockX),
            Coords.blockToCube(blockY),
            Coords.blockToCube(blockZ)
        );
        cir.setReturnValue(cube != null ? cube.getLightEmission(this.pos.set(blockX, blockY, blockZ)) : 0);
    }
}
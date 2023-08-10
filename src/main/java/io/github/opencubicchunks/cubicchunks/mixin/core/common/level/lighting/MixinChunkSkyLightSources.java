package io.github.opencubicchunks.cubicchunks.mixin.core.common.level.lighting;

import static net.minecraft.world.level.lighting.ChunkSkyLightSources.NEGATIVE_INFINITY;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSkyLightSources.class)
public class MixinChunkSkyLightSources {
    protected boolean isCubic;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(LevelHeightAccessor levelHeightAccessor, CallbackInfo ci) {
        if (!((CubicLevelHeightAccessor) levelHeightAccessor).isCubic()) return;
        isCubic = true;
    }

    @Inject(method = "fillFrom", cancellable = true, at = @At("HEAD"))
    private void onFillFrom(ChunkAccess chunk, CallbackInfo ci) {
        if (!this.isCubic) return;
        ci.cancel(); // TODO
    }

    @Inject(method = "update", cancellable = true, at = @At("HEAD"))
    private void onUpdate(BlockGetter level, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isCubic) return;
        cir.setReturnValue(false); // TODO
    }

    @Inject(method = "getLowestSourceY", cancellable = true, at = @At("HEAD"))
    private void onGetLowestSourceY(int x, int z, CallbackInfoReturnable<Integer> cir) {
        if (!this.isCubic) return;
        cir.setReturnValue(NEGATIVE_INFINITY); // TODO
    }

    @Inject(method = "getHighestLowestSourceY", cancellable = true, at = @At("HEAD"))
    private void onGetHighestLowestSourceY(CallbackInfoReturnable<Integer> cir) {
        if (!this.isCubic) return;
        cir.setReturnValue(NEGATIVE_INFINITY); // TODO
    }
}

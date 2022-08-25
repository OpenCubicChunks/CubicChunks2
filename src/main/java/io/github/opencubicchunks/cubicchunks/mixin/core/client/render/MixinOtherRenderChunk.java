package io.github.opencubicchunks.cubicchunks.mixin.core.client.render;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderChunk.class)
public class MixinOtherRenderChunk {
    @Shadow @Final private LevelChunk wrapped;

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void goThroughLevel(BlockPos blockPos, CallbackInfoReturnable<BlockState> cir) {
        if (((CubicLevelHeightAccessor) this.wrapped).isCubic()) {
            cir.setReturnValue(this.wrapped.getBlockState(blockPos));
        }
    }
}

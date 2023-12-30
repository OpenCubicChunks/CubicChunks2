package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common.placement.verticalanchor;

import io.github.opencubicchunks.cubicchunks.levelgen.carver.CubicCarvingContext;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VerticalAnchor.AboveBottom.class)
public class MixinVerticalAnchorAboveBottom {

    @Shadow @Final private int offset;

    @Inject(method = "resolveY", at = @At("HEAD"), cancellable = true)
    private void resolveCubicChunksY(WorldGenerationContext context, CallbackInfoReturnable<Integer> cir) {
        if (context instanceof CubicCarvingContext) {
            int defaultValue = ((CubicCarvingContext) context).getOriginalMinGenY() + offset;
            cir.setReturnValue(defaultValue);
        }
    }
}

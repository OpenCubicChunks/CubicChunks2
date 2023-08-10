package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common.feature;


import net.minecraft.world.level.levelgen.feature.treedecorators.CocoaDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CocoaDecorator.class)
public class MixinCocoaDecorator {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void exitIfListIsEmpty(TreeDecorator.Context context, CallbackInfo ci) {
        if (context.leaves().isEmpty() || context.logs().isEmpty()) {
            ci.cancel();
        }
    }
}

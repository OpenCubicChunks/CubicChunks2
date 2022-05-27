package io.github.opencubicchunks.cubicchunks.mixin.core;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({
    NoiseRouterData.class
})
public class LoaderMixin {
    @Inject(method = "<clinit>", at = @At("HEAD"))
    private static void initializing(CallbackInfo ci) {
        System.out.println("Loading Class");
    }
}

package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public class MixinServerChunkCache_NoSave {
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void noSave(boolean flush, CallbackInfo ci) {
        ci.cancel();
    }
}

package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common;

import java.util.Map;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunk.class)
public abstract class MixinNoiseChunk {
    @Shadow @Final private Map<DensityFunction, DensityFunction> wrapped;

    @Shadow protected abstract DensityFunction wrapNew(DensityFunction densityFunction);

    //TODO: This is a hack to fix a ConcurrentModificationException. It would be MUCH better to fix the underlying issue.
    @Inject(
        method = "wrap",
        at = @At("HEAD"),
        cancellable = true
    )
    private void makeSynchronized(DensityFunction densityFunction, CallbackInfoReturnable<DensityFunction> cir) {
        synchronized (this) {
            cir.setReturnValue(this.wrapped.computeIfAbsent(densityFunction, this::wrapNew));
        }
    }
}

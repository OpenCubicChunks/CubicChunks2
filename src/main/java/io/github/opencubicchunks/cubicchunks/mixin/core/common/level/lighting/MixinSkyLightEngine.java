package io.github.opencubicchunks.cubicchunks.mixin.core.common.level.lighting;


import io.github.opencubicchunks.cubicchunks.world.lighting.CubicSkyLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.SkyLightColumnChecker;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyLightEngine.class)
public abstract class MixinSkyLightEngine extends MixinLightEngine<SkyLightSectionStorage.SkyDataLayerStorageMap, SkyLightSectionStorage> implements SkyLightColumnChecker,
    CubicSkyLightEngine {
    /**
     * @author CursedFlames
     * @reason disable vanilla sky light logic
     */
    @Inject(method = "checkNode", at = @At("HEAD"), cancellable = true)
    protected void checkNode(long id, CallbackInfo ci) {
        if (!this.isCubic) {
            return;
        }
        ci.cancel();
        super.checkNode(id);
    }
}

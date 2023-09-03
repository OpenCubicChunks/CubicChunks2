package io.github.opencubicchunks.cubicchunks.mixin.core.common.level.lighting;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkyLightSectionStorage.class)
public abstract class MixinSkyLightSectionStorage extends LayerLightSectionStorage<SkyLightSectionStorage.SkyDataLayerStorageMap> {
    private boolean isCubic;

    private MixinSkyLightSectionStorage(LightLayer lightLayer, LightChunkGetter lightChunkGetter,
                                        SkyLightSectionStorage.SkyDataLayerStorageMap dataLayerStorageMap) {
        super(lightLayer, lightChunkGetter, dataLayerStorageMap);
    }

    // TODO (1.20) which of these do we actually need anymore?

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(LightChunkGetter lightChunkGetter, CallbackInfo ci) {
        if (lightChunkGetter.getLevel() == null) {
            //Special case for dummy light engine
            isCubic = true;
            return;
        }
        isCubic = ((CubicLevelHeightAccessor) lightChunkGetter.getLevel()).isCubic();
    }

    @Inject(method = "getLightValue(JZ)I", cancellable = true, at = @At("HEAD"))
    private void onGetLightValue(long blockPosLong, boolean cached, CallbackInfoReturnable<Integer> cir) {
        if (!isCubic) {
            return;
        }

        // TODO (1.20)
        cir.setReturnValue(15);
    }

    @Inject(method = "onNodeAdded", cancellable = true, at = @At("HEAD"))
    private void onOnNodeAdded(long sectionPos, CallbackInfo ci) {
        if (!isCubic) return;
        ci.cancel();
    }

    @Inject(method = "onNodeRemoved", cancellable = true, at = @At("HEAD"))
    private void onOnNodeRemoved(long sectionPos, CallbackInfo ci) {
        if (!isCubic) return;
        ci.cancel();
    }

    // FIXME (1.20) lighting
//    @Inject(method = "enableLightSources", cancellable = true,
//        at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/world/level/lighting/SkyLightSectionStorage;runAllUpdates()V"))
//    private void onEnableLightSources(long columnPos, boolean enabled, CallbackInfo ci) {
//        if (!isCubic) return;
//        if (enabled) {
//            // We handle skylight emission differently anyway, so we don't need vanilla's sky light source system
//            ci.cancel();
//        }
//    }

    @Inject(method = "createDataLayer", cancellable = true, at = @At("HEAD"))
    private void onCreateDataLayer(long sectionPos, CallbackInfoReturnable<DataLayer> cir) {
        if (!isCubic) return;
        cir.setReturnValue(super.createDataLayer(sectionPos));
    }

//    @Inject(method = "markNewInconsistencies", cancellable = true, at = @At("HEAD"))
//    private void onMarkNewInconsistencies(LightEngine<SkyLightSectionStorage.SkyDataLayerStorageMap, ?> lightProvider, boolean doSkylight, boolean skipEdgeLightPropagation,
//                                          CallbackInfo ci) {
//        if (!isCubic) return;
//        ci.cancel();
//        super.markNewInconsistencies(lightProvider, doSkylight, skipEdgeLightPropagation);
//    }
//
//    @Inject(method = "hasSectionsBelow", cancellable = true, at = @At("HEAD"))
//    private void onHasSectionsBelow(int sectionY, CallbackInfoReturnable<Boolean> cir) {
//        if (!isCubic) return;
//        cir.setReturnValue(true);
//    }

    @Inject(method = "isAboveData", cancellable = true, at = @At("HEAD"))
    private void onIsAboveData(long sectionPos, CallbackInfoReturnable<Boolean> cir) {
        if (!isCubic) return;
        cir.setReturnValue(false);
    }
}

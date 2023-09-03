package io.github.opencubicchunks.cubicchunks.mixin.core.common.level.lighting;


import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.lighting.SkyLightColumnChecker;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyLightEngine.class)
public abstract class MixinSkyLightEngine extends MixinLightEngine<SkyLightSectionStorage.SkyDataLayerStorageMap, SkyLightSectionStorage> implements SkyLightColumnChecker {
    @Override
    public void setLightEnabled(CubePos cubePos, boolean enable) {
        super.setLightEnabled(cubePos, true);
        if (enable) {
            // TODO fill sections with max light if they're fully above heightmap? see SkyLightEngine.setLightEnabled
            //      not sure if this is necessary
        }
    }

    // TODO don't manually duplicate propagateLightSources(ChunkPos)? dasm + mixin?
    @Override
    public void propagateLightSources(CubePos cubePos) {
        this.setLightEnabled(cubePos, true);
        // TODO sky lighting
//        LightChunk lightChunk = ((LightCubeGetter) this.chunkSource).getCubeForLighting(cubePos.getX(), cubePos.getY(), cubePos.getZ());
//        if (lightChunk != null) {
//            lightChunk.findBlockLightSources((blockPos, blockState) -> {
//                int i = blockState.getLightEmission();
//                this.enqueueIncrease(blockPos.asLong(), LightEngine.QueueEntry.increaseLightFromEmission(i, isEmptyShape(blockState)));
//            });
//        }
    }

    @Inject(method = "propagateIncrease", at = @At("HEAD"), cancellable = true)
    void onPropagateIncrease(long packedPos, long queueEntry, int lightLevel, CallbackInfo ci) {
        if (isCubic) ci.cancel();
        // TODO
    }

    @Inject(method = "propagateDecrease", at = @At("HEAD"), cancellable = true)
    void onPropagateDecrease(long packedPos, long lightLevel, CallbackInfo ci) {
        if (isCubic) ci.cancel();
        // TODO
    }

    @Inject(method = "updateSourcesInColumn", at = @At("HEAD"), cancellable = true)
    void onUpdateSourcesInColumn(int x, int z, int lowestY, CallbackInfo ci) {
        if (isCubic) ci.cancel();
        // TODO need to update column in a way that handles sparse sections (e.g. if we have cubes loaded at y=1000000 and y=0)
    }
}

package io.github.opencubicchunks.cubicchunks.mixin.core.common.level.lighting;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightCubeGetter;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;

@SuppressWarnings("rawtypes")
@Mixin(BlockLightEngine.class)
public abstract class MixinBlockLightEngine extends MixinLightEngine {
    // TODO don't manually duplicate propagateLightSources(ChunkPos)? dasm + mixin?
    @Override
    public void propagateLightSources(CubePos cubePos) {
        this.setLightEnabled(cubePos, true);
        LightChunk lightChunk = ((LightCubeGetter) this.chunkSource).getCubeForLighting(cubePos.getX(), cubePos.getY(), cubePos.getZ());
        if (lightChunk != null) {
            lightChunk.findBlockLightSources((blockPos, blockState) -> {
                int i = blockState.getLightEmission();
                this.enqueueIncrease(blockPos.asLong(), LightEngine.QueueEntry.increaseLightFromEmission(i, isEmptyShape(blockState)));
            });
        }
    }
}

package io.github.opencubicchunks.cubicchunks.mixin.core.common.level.lighting;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightEventListener;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LayerLightEventListener.DummyLightLayerEventListener.class)
public class MixinDummyLightLayerEventListener implements CubicLightEventListener {
    @Override public void setLightEnabled(CubePos cubePos, boolean lightEnabled) {
    }

    @Override public void propagateLightSources(CubePos cubePos) {
    }
}

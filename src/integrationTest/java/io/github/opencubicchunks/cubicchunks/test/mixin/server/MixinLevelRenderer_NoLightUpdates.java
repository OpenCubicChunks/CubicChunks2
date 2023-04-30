package io.github.opencubicchunks.cubicchunks.test.mixin.server;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer_NoLightUpdates {
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;runUpdates(IZZ)I"))
    private int noLightUpdate(LevelLightEngine instance, int i, boolean bl, boolean bl2) {
        return i;
    }
}

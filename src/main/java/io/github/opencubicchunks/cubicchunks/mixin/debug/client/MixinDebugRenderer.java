package io.github.opencubicchunks.cubicchunks.mixin.debug.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {
    private static final boolean DEBUG_HEIGHTMAP_RENDERER = System.getProperty("cubicchunks.debug.heightmaprenderer", "false").equalsIgnoreCase("true");

    @Shadow @Final public DebugRenderer.SimpleDebugRenderer heightMapRenderer;

    @Inject(method = "render", at = @At("HEAD"))
    private void enableOtherRenderers(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
        if (DEBUG_HEIGHTMAP_RENDERER) {
            this.heightMapRenderer.render(poseStack, bufferSource, cameraX, cameraY, cameraZ);
        }
    }
}

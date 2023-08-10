package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import io.github.opencubicchunks.cubicchunks.config.ServerConfig;
import io.github.opencubicchunks.cubicchunks.debug.DebugVisualization;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    private static final boolean DEBUG_WINDOW_ENABLED = System.getProperty("cubicchunks.debug.window", "false").equals("true");

    @Inject(method = "runTick", at = @At("HEAD"))
    private void onRun(CallbackInfo ci) {
        if (!DEBUG_WINDOW_ENABLED) {
            return;
        }
        DebugVisualization.onRender();
    }
}

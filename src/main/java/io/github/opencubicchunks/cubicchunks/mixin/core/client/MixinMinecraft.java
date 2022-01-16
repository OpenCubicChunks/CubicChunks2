package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import io.github.opencubicchunks.cubicchunks.config.ServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    // Target inside the lambda so we can use the levelStorageAccess
    @Inject(method = "lambda$createLevel$31", at = @At("HEAD"))
    private static void onCreateLevel(LevelSettings levelSettings, LevelStorageSource.LevelStorageAccess levelStorageAccess, CallbackInfoReturnable<DataPackConfig> cir) {
        ServerConfig.generateConfigIfNecessary(levelStorageAccess);
    }
}

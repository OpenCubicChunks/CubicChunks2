package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import java.util.function.Function;

import com.mojang.datafixers.util.Function4;
import io.github.opencubicchunks.cubicchunks.config.ServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "doLoadLevel", locals = LocalCapture.CAPTURE_FAILHARD, at = @At(value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/level/storage/LevelStorageSource;createAccess(Ljava/lang/String;)Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;"))
    private void onDoLoadLevel(String string, RegistryAccess.RegistryHolder registryHolder, Function<LevelStorageSource.LevelStorageAccess, DataPackConfig> function,
                               Function4<LevelStorageSource.LevelStorageAccess, RegistryAccess.RegistryHolder, ResourceManager, DataPackConfig, WorldData> function4, boolean bl,
                               @Coerce Object experimentalDialogType, CallbackInfo ci, LevelStorageSource.LevelStorageAccess levelStorageAccess) {
        ServerConfig.generateConfigIfNecessary(levelStorageAccess);
    }
}

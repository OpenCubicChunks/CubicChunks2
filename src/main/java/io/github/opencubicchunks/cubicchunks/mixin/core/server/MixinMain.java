package io.github.opencubicchunks.cubicchunks.mixin.core.server;

import com.mojang.datafixers.util.Pair;
import io.github.opencubicchunks.cubicchunks.config.ServerConfig;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.server.Main;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Main.class)
public class MixinMain {
    @Inject(
        method = "lambda$main$1(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Ljoptsimple/OptionSet;Ljoptsimple/OptionSpec;"
            + "Lnet/minecraft/server/dedicated/DedicatedServerSettings;Ljoptsimple/OptionSpec;Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/world/level/DataPackConfig;)Lcom/mojang/datafixers/util/Pair;",
        at = @At(value = "NEW", target = "net/minecraft/world/level/storage/PrimaryLevelData"))
    private static void onCreatePrimaryLevelData(LevelStorageSource.LevelStorageAccess levelStorageAccess, OptionSet optionSet, OptionSpec optionSpec,
                                                 DedicatedServerSettings dedicatedServerSettings, OptionSpec optionSpec2, ResourceManager resourceManager, DataPackConfig dataPackConfig,
                                                 CallbackInfoReturnable<Pair> cir) {
        ServerConfig.generateConfigIfNecessary(levelStorageAccess);
    }

}

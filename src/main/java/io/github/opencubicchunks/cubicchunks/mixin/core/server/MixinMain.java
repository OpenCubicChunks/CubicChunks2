package io.github.opencubicchunks.cubicchunks.mixin.core.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.config.ServerConfig;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Eula;
import net.minecraft.server.Main;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(Main.class)
public class MixinMain {
    @Inject(method = "main", locals = LocalCapture.CAPTURE_FAILHARD, at = @At(value = "NEW", target = "net/minecraft/world/level/storage/PrimaryLevelData"))
    private static void onCreatePrimaryLevelData(String[] strings, CallbackInfo ci, OptionParser optionParser, OptionSpec optionSpec, OptionSpec optionSpec2, OptionSpec optionSpec3,
                                                 OptionSpec optionSpec4, OptionSpec optionSpec5, OptionSpec optionSpec6, OptionSpec optionSpec7, OptionSpec optionSpec8,
                                                 OptionSpec optionSpec9, OptionSpec optionSpec10, OptionSpec optionSpec11, OptionSpec optionSpec12, OptionSpec optionSpec13,
                                                 OptionSpec optionSpec14, OptionSet optionSet, RegistryAccess.RegistryHolder registryHolder, Path path,
                                                 DedicatedServerSettings dedicatedServerSettings, Path path2, Eula eula, File file,
                                                 YggdrasilAuthenticationService yggdrasilAuthenticationService, MinecraftSessionService minecraftSessionService,
                                                 GameProfileRepository gameProfileRepository, GameProfileCache gameProfileCache, String string, LevelStorageSource levelStorageSource,
                                                 LevelStorageSource.LevelStorageAccess levelStorageAccess) {
        ServerConfig.generateConfigIfNecessary(levelStorageAccess);
    }
}

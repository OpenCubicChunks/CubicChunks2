package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import java.util.Optional;

import com.mojang.serialization.Lifecycle;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.config.ServerConfig;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(CreateWorldScreen.class)
public abstract class MixinCreateWorldScreen extends Screen {
    private CycleButton isCubicChunksButton;

    private MixinCreateWorldScreen(Component component) {
        super(component);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        isCubicChunksButton = this.addRenderableWidget(CycleButton.onOffBuilder(CubicChunks.config().shouldGenerateNewWorldsAsCC())
//                .withCustomNarration((button) -> CommonComponents.joinForNarration(button.createDefaultNarrationMessage(), Component.translatable("selectWorld.cubicChunks.info")))
                .create(this.width / 2 + 5, 151 - 20, 150, 20, Component.translatable("selectWorld.cubicChunks"), (button, cubicChunksEnabled) -> {
            var config = CubicChunks.config();
            config.setGenerateNewWorldsAsCC(cubicChunksEnabled);
            config.markDirty();
            CubicChunks.LOGGER.info("New worlds generate as CC: " + config.shouldGenerateNewWorldsAsCC());
        }));
    }

    // Target the removeTempDataPackDir call since it's after the optional.isEmpty check
    @Inject(method = "createNewWorld", locals = LocalCapture.CAPTURE_FAILHARD, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;"
        + "removeTempDataPackDir()V"))
    private void onCreateNewWorld(PrimaryLevelData.SpecialWorldProperty specialWorldProperty, LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess, Lifecycle worldGenSettingsLifecycle,
                                  CallbackInfo ci, Optional<LevelStorageSource.LevelStorageAccess> optional) {
        // This should always be the case
        optional.ifPresent(ServerConfig::generateConfigIfNecessary);
    }
}

package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class MixinCreateWorldScreen extends Screen {
    private CycleButton isCubicChunksButton;

    private MixinCreateWorldScreen(Component component) {
        super(component);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        isCubicChunksButton = this.addRenderableWidget(CycleButton.onOffBuilder(CubicChunks.config().common.generateNewWorldsAsCC)
//                .withCustomNarration((button) -> CommonComponents.joinForNarration(button.createDefaultNarrationMessage(), new TranslatableComponent("selectWorld.cubicChunks.info")))
                .create(this.width / 2 + 5, 151 - 20, 150, 20, new TranslatableComponent("selectWorld.cubicChunks"), (button, cubicChunksEnabled) -> {
            var config = CubicChunks.config();
            config.common.generateNewWorldsAsCC = cubicChunksEnabled;
            config.markDirty();
            CubicChunks.LOGGER.info("New worlds generate as CC: " + config.common.generateNewWorldsAsCC);
        }));
    }
}

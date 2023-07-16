package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VideoSettingsScreen.class)
public class MixinVideoSettingsScreen {
    private static final OptionInstance<Integer> VERTICAL_RENDER_DISTANCE;

    @Shadow
    private static OptionInstance<?>[] options(Options options) {
        throw new Error("Mixin failed to apply correctly");
    }

    // FIXME find a way that doesn't use @Redirect
    @Redirect(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/VideoSettingsScreen;options(Lnet/minecraft/client/Options;)"
        + "[Lnet/minecraft/client/OptionInstance;"))
    private OptionInstance[] onInit(Options options) {
        var optionInstances = options(options);
        optionInstances = ArrayUtils.add(optionInstances, 3, VERTICAL_RENDER_DISTANCE);
        return optionInstances;
    }

    static {
        var higherMaximum = (Minecraft.getInstance().is64Bit() && Runtime.getRuntime().maxMemory() >= 1000000000L);

        VERTICAL_RENDER_DISTANCE = new OptionInstance<>(
            "options.verticalRenderDistance",
            OptionInstance.noTooltip(),
            (component, integer) -> Options.genericValueLabel(component, Component.translatable("options.chunks", integer)),
            new OptionInstance.IntRange(2, higherMaximum ? 32 : 16), CubicChunks.config().getVerticalViewDistance(),
            newValue -> {
                CubicChunks.config().setVerticalViewDistance(newValue);
                CubicChunks.config().markDirty();
                Minecraft.getInstance().levelRenderer.needsUpdate();
            }
        );
    }
}
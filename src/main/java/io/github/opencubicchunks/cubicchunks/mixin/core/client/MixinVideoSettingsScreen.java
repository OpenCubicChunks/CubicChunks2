package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.mixin.access.client.OptionAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Option;
import net.minecraft.client.ProgressOption;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.TranslatableComponent;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(VideoSettingsScreen.class)
public class MixinVideoSettingsScreen {
    @Shadow @Final @Mutable private static Option[] OPTIONS;

    private static final ProgressOption VERTICAL_RENDER_DISTANCE = new ProgressOption("options.verticalRenderDistance", 2.0D,
        (Minecraft.getInstance().is64Bit() && Runtime.getRuntime().maxMemory() >= 1000000000L) ? 32.0 : 16.0D, 1.0F, (gameOptions) -> {
        return (double) CubicChunks.config().getVerticalViewDistance();
    }, (gameOptions, viewDistance) -> {
        CubicChunks.config().setVerticalViewDistance(viewDistance.intValue());
        CubicChunks.config().markDirty();
        Minecraft.getInstance().levelRenderer.needsUpdate();
    }, (gameOptions, option) -> {
        double value = option.get(gameOptions);
        return ((OptionAccess) option).invokeGenericValueLabel(new TranslatableComponent("options.chunks", (int) value));
    });

    static {
        OPTIONS = ArrayUtils.add(OPTIONS, 3, VERTICAL_RENDER_DISTANCE);
    }
}
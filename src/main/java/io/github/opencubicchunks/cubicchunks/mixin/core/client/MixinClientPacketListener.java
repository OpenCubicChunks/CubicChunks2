package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private ClientLevel level;

    @Shadow public abstract ClientLevel getLevel();

    //Removed handleLevelChunk because it didn't seem like it would change anything

    @Redirect(method = { "method_38546" }, //method_38546 is a lambda in queueLightUpdate
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxSection()I"))
    private int getFakeMaxSectionY(ClientLevel clientLevel) {
        if (!((CubicLevelHeightAccessor) clientLevel).isCubic()) {
            return clientLevel.getMaxSection();
        }
        return clientLevel.getMinSection() - 1; // disable the loop, cube packets do the necessary work
    }

    //Don't need to iterate over all sections to check for updates in cubic
    @Redirect(
        method = "readSectionList",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;getLightSectionCount()I")
    )
    private int getFakeSectionCount(LevelLightEngine engine) {
        if (!((CubicLevelHeightAccessor) getLevel()).isCubic()) {
            return engine.getLightSectionCount();
        }

        return 1; // Disable loop - same as above
    }
}

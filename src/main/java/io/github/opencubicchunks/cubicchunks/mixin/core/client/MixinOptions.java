package io.github.opencubicchunks.cubicchunks.mixin.core.client;

import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class MixinOptions {

    @Shadow @Final private OptionInstance<CloudStatus> cloudStatus;

    @Shadow public abstract int getEffectiveRenderDistance();

    @Inject(method = "getCloudsType", at = @At("HEAD"), cancellable = true)
    private void getCloudsTypeForVerticalViewDistance(CallbackInfoReturnable<CloudStatus> cir) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            if (!((CubicLevelHeightAccessor) level).isCubic()) {
                return;
            }
        }
        cir.setReturnValue(CubicChunks.config().getVerticalViewDistance() >= 4 && this.getEffectiveRenderDistance() >= 4 ? this.cloudStatus.get() : CloudStatus.OFF);

    }
}
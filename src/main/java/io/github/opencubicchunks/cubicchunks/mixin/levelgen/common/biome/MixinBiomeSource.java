package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common.biome;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BiomeSource.class)
public class MixinBiomeSource {
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void registerCustom(CallbackInfo ci) {
        CubicChunks.registerBiomeSources();
    }
}

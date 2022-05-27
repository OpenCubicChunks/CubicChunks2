package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common;

import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.world.level.chunk.ProtoCube;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NoiseChunk.class)
public class MixinNoiseChunk {


    @Shadow @Final private NoiseSettings noiseSettings;

    @Redirect(method = "forChunk", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"))
    private static int forceCubeMaxBounds(int a, int b, ChunkAccess chunkAccess, NoiseRouter noiseRouter, Supplier<DensityFunctions.BeardifierOrMarker> supplier,
                                        NoiseGeneratorSettings noiseGeneratorSettings, Aquifer.FluidPicker fluidPicker, Blender blender) {
        if (chunkAccess instanceof ProtoCube) {
            return b;
        }
       return Math.max(a, b);
    }

    @Redirect(method = "forChunk", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"))
    private static int forceCubeMinBounds(int a, int b, ChunkAccess chunkAccess, NoiseRouter noiseRouter, Supplier<DensityFunctions.BeardifierOrMarker> supplier,
                                        NoiseGeneratorSettings noiseGeneratorSettings, Aquifer.FluidPicker fluidPicker, Blender blender) {
        if (chunkAccess instanceof ProtoCube) {
            return b;
        } else {
            return Math.min(a, b);
        }
    }

    @Redirect(method = "forChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;intFloorDiv(II)I", ordinal = 1))
    private static int useCubeCellSize(int a, int b, ChunkAccess chunkAccess, NoiseRouter noiseRouter, Supplier<DensityFunctions.BeardifierOrMarker> supplier,
                                          NoiseGeneratorSettings noiseGeneratorSettings, Aquifer.FluidPicker fluidPicker, Blender blender) {
        if (chunkAccess instanceof ProtoCube) {
            return b;
        } else {
            return chunkAccess.getHeight() / noiseGeneratorSettings.noiseSettings().getCellHeight();
        }
    }
}

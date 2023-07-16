package io.github.opencubicchunks.cubicchunks.mixin.levelgen.common;

import java.util.Map;
import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.levelgen.chunk.NoiseAndSurfaceBuilderHelper;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.ProtoCube;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunk.class)
public abstract class MixinNoiseChunk {
    @Shadow @Final private NoiseSettings noiseSettings;

    @Shadow @Final private Map<DensityFunction, DensityFunction> wrapped;

    @Shadow protected abstract DensityFunction wrapNew(DensityFunction densityFunction);

    @Redirect(method = "forChunk", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"))
    private static int forceCubeMaxBounds(int a, int b, ChunkAccess chunkAccess, NoiseRouter noiseRouter, Supplier<DensityFunctions.BeardifierOrMarker> supplier,
                                        NoiseGeneratorSettings noiseGeneratorSettings, Aquifer.FluidPicker fluidPicker, Blender blender) {
        if (chunkAccess instanceof ProtoCube || chunkAccess instanceof NoiseAndSurfaceBuilderHelper) {
            return b;
        }
       return Math.max(a, b);
    }

    @Redirect(method = "forChunk", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I"))
    private static int forceCubeMinBounds(int a, int b, ChunkAccess chunkAccess, NoiseRouter noiseRouter, Supplier<DensityFunctions.BeardifierOrMarker> supplier,
                                        NoiseGeneratorSettings noiseGeneratorSettings, Aquifer.FluidPicker fluidPicker, Blender blender) {
        if (chunkAccess instanceof ProtoCube || chunkAccess instanceof NoiseAndSurfaceBuilderHelper) {
            return b;
        } else {
            return Math.min(a, b);
        }
    }

    @Redirect(method = "forChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floorDiv(II)I", ordinal = 1))
    private static int useCubeCellSize(int a, int b, ChunkAccess chunkAccess, NoiseRouter noiseRouter, Supplier<DensityFunctions.BeardifierOrMarker> supplier,
                                          NoiseGeneratorSettings noiseGeneratorSettings, Aquifer.FluidPicker fluidPicker, Blender blender) {
        if (chunkAccess instanceof ProtoCube || chunkAccess instanceof NoiseAndSurfaceBuilderHelper) {
            return b;
        } else {
            return chunkAccess.getHeight() / noiseGeneratorSettings.noiseSettings().getCellHeight();
        }
    }

    @Inject(
        method = "<init>",
        at = @At(
            value = "FIELD",
            opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;wrapped:Ljava/util/Map;",
            shift = At.Shift.AFTER
        )
    )
    private void wrapWrapped(int i, int j, int k, NoiseRouter noiseRouter, int l, int m, DensityFunctions.BeardifierOrMarker beardifierOrMarker,
                             NoiseGeneratorSettings noiseGeneratorSettings, Aquifer.FluidPicker fluidPicker, Blender blender, CallbackInfo ci) {

    }

    //TODO: This is a hack to fix a ConcurrentModificationException. It would be MUCH better to fix the underlying issue.
    @Inject(
        method = "wrap",
        at = @At("HEAD"),
        cancellable = true
    )
    private void makeSynchronized(DensityFunction densityFunction, CallbackInfoReturnable<DensityFunction> cir) {
        synchronized (this) {
            cir.setReturnValue(this.wrapped.computeIfAbsent(densityFunction, this::wrapNew));
        }
    }
}

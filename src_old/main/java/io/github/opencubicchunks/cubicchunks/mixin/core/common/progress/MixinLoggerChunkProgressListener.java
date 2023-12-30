package io.github.opencubicchunks.cubicchunks.mixin.core.common.progress;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cubicchunks.server.level.progress.CubeProgressListener;
import net.minecraft.server.level.progress.LoggerChunkProgressListener;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LoggerChunkProgressListener.class)
public abstract class MixinLoggerChunkProgressListener implements CubeProgressListener {
    private int loadedCubes;
    private int totalCubes;

    private boolean isCubic;

    @Shadow private int count;

    @Shadow public abstract void onStatusChange(ChunkPos chunkPos, @Nullable ChunkStatus chunkStatus);

    @Inject(method = "<init>", at = @At("RETURN"))
    public void onInit(int vanillaSpawnRadius, CallbackInfo ci) {
        // Server chunk radius, divided by CUBE_DIAMETER to get the radius in cubes
        // Except we subtract one before the ceil and readd it after, for... some reason
        // Multiply by two to convert cube radius -> diameter,
        // And then add one for the center cube
        int ccCubeRadius = 1 + (int) Math.ceil((vanillaSpawnRadius - 1) / ((float) CubicConstants.DIAMETER_IN_SECTIONS));
        int ccCubeDiameter = ccCubeRadius * 2 + 1;
        totalCubes = ccCubeDiameter * ccCubeDiameter * ccCubeDiameter;
    }

    @Override public void startCubes(CubePos center) {
        isCubic = true;
    }

    @Override public void onCubeStatusChange(CubePos cubePos, @Nullable ChunkStatus newStatus) {
        if (newStatus == ChunkStatus.FULL) {
            this.loadedCubes++;
        }
        // Call the regular method with null arguments to trigger logging, as chunks finish loading before cubes
        this.onStatusChange(null, null);
    }

    @ModifyConstant(constant = @Constant(longValue = 500L), method = "onStatusChange")
    private long getLogInterval(long arg) {
        return 5000;
    }

    /**
     * @author CursedFlames & NotStirred
     * @reason account for cubes as well as chunks in the loading progress
     */
    @Inject(method = "getProgress", at = @At("HEAD"), cancellable = true)
    private void getProgress(CallbackInfoReturnable<Integer> cir) {
        if (!isCubic) {
            return;
        }
        cir.setReturnValue(Mth.floor(loadedCubes * 100.0F / totalCubes));
    }
}
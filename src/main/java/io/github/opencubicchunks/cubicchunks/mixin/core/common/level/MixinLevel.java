package io.github.opencubicchunks.cubicchunks.mixin.core.common.level;

import java.util.Random;

import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class MixinLevel implements CubicLevelAccessor, LevelReader {

    @Final @Shadow public Random random;
    protected boolean isCubic;
    protected boolean generates2DChunks;
    protected WorldStyle worldStyle;

    @Shadow public abstract ResourceKey<Level> dimension();

    @Shadow public abstract boolean isRainingAt(BlockPos blockPos);

    @Override public int getHeight() {
        if (!isCubic()) {
            return LevelReader.super.getHeight();
        }
        return 40000000;
    }

    /**
     * @author Setadokalo
     * @reason Allows teleporting outside +/-20000000 blocks on the Y axis
     */
    @Overwrite private static boolean isOutsideSpawnableHeight(int y) {
        return CubicChunks.MIN_SUPPORTED_HEIGHT > y || y > CubicChunks.MAX_SUPPORTED_HEIGHT;
    }

    @Override public int getMinBuildHeight() {
        if (!isCubic()) {
            return LevelReader.super.getMinBuildHeight();
        }
        return -20000000;
    }

    @Inject(method = "blockEntityChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;markUnsaved()V"))
    private void onBlockEntityChanged(BlockPos blockPos, CallbackInfo ci) {
        if (!isCubic()) {
            return;
        }
        this.getCubeAt(blockPos).setDirty(true);
    }

    public CubeAccess getCubeAt(BlockPos pos) {
        return this.getCube(Coords.blockToCube(pos.getX()), Coords.blockToCube(pos.getY()), Coords.blockToCube(pos.getZ()));
    }

    @Override public WorldStyle worldStyle() {
        return worldStyle;
    }

    @Override public boolean isCubic() {
        return isCubic;
    }

    @Override public boolean generates2DChunks() {
        return generates2DChunks;
    }

    @Override public void setWorldStyle(WorldStyle worldStyle) {
        this.worldStyle = worldStyle;
        this.isCubic = worldStyle.isCubic();
        this.generates2DChunks = worldStyle.generates2DChunks();
    }

    @Override
    public CubeAccess getCube(int cubeX, int cubeY, int cubeZ) {
        return this.getCube(cubeX, cubeY, cubeZ, ChunkStatus.FULL, true);
    }

    @Override
    public CubeAccess getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus status) {
        return this.getCube(cubeX, cubeY, cubeZ, status, true);
    }

    //The method .getWorld() No longer exists
    @Override
    public CubeAccess getCube(int cubeX, int cubeY, int cubeZ, ChunkStatus requiredStatus, boolean nonnull) {
        CubeAccess cube = ((CubeSource) ((Level) (Object) this).getChunkSource()).getCube(cubeX, cubeY, cubeZ, requiredStatus, nonnull);
        if (cube == null && nonnull) {
            throw new IllegalStateException("Should always be able to create a cube!");
        } else {
            return cube;
        }
    }

    @Shadow public abstract boolean isInWorldBounds(BlockPos pos);

    @Shadow public abstract ProfilerFiller getProfiler();

    @Shadow public abstract LevelChunk getChunk(int x, int z);

    @Shadow public abstract BlockPos getBlockRandomPos(int x, int i, int z, int i1);
}
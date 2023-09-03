package io.github.opencubicchunks.cubicchunks.mixin.core.common.level.lighting;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.LayerLightSectionStorageAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightCubeGetter;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLayerLightSectionStorage;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLightEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightEngine.class)
public abstract class MixinLightEngine<M extends DataLayerStorageMap<M>, S extends LayerLightSectionStorage<M>> implements CubicLightEngine, CubicLightEventListener {

    @Shadow @Final protected S storage;

    @Shadow @Final protected LightChunkGetter chunkSource;

    protected boolean isCubic;

    @Shadow @Final private long[] lastChunkPos;

    @Shadow @Final private LightChunk[] lastChunk;

    @Shadow protected void checkNode(long id) {
    }

    @Shadow @Nullable protected abstract LightChunk getChunk(int chunkX, int chunkZ);

    @Override
    public void retainCubeData(CubePos posIn, boolean retain) {
        long i = posIn.asSectionPos().asLong();
        ((CubicLayerLightSectionStorage) this.storage).retainCubeData(i, retain);
    }

    @Override
    public void setLightEnabled(CubePos cubePos, boolean enable) {
        ChunkPos chunkPos = cubePos.asChunkPos();
        //TODO: implement invokeEnableLightSources for CubePos in SkyLightStorage
        for (int x = 0; x < CubicConstants.DIAMETER_IN_SECTIONS; x++) {
            for (int z = 0; z < CubicConstants.DIAMETER_IN_SECTIONS; z++) {
                ((LayerLightSectionStorageAccess) this.storage).invokeSetLightEnabled(ChunkPos.asLong(chunkPos.x + x, chunkPos.z + z), enable);
            }
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void setCubic(LightChunkGetter lightChunkGetter, LayerLightSectionStorage layerLightSectionStorage, CallbackInfo ci) {
        if (this.chunkSource.getLevel() == null) {
            // Special case for dummy light engine used in MixinChunkMap for serialization
            this.isCubic = true;
            return;
        }

        this.isCubic = ((CubicLevelHeightAccessor) this.chunkSource.getLevel()).isCubic();
//        this.generates2DChunks = ((CubicLevelHeightAccessor) this.chunkSource.getLevel()).generates2DChunks();
//        this.worldStyle = ((CubicLevelHeightAccessor) this.chunkSource.getLevel()).worldStyle();
    }

    @Redirect(method = "getState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/lighting/LightEngine;getChunk(II)Lnet/minecraft/world/level/chunk/LightChunk;"))
    private LightChunk getCubeReader(LightEngine layerLightEngine, int chunkX, int chunkZ, BlockPos blockPos) {
        if (!this.isCubic) {
            return this.getChunk(chunkX, chunkZ);
        }
        int sectionX = SectionPos.blockToSectionCoord(blockPos.getX());
        int sectionY = SectionPos.blockToSectionCoord(blockPos.getY());
        int sectionZ = SectionPos.blockToSectionCoord(blockPos.getZ());
        return this.getCubeReader(sectionX, sectionY, sectionZ);
    }

    // TODO might need to modify getOpacity

    @Nullable
    private LightChunk getCubeReader(int sectionX, int sectionY, int sectionZ) {
        long i = SectionPos.asLong(sectionX, sectionY, sectionZ);

        for (int j = 0; j < 2; ++j) {
            if (i == this.lastChunkPos[j]) {
                return this.lastChunk[j];
            }
        }

        LightChunk iblockreader = ((LightCubeGetter) this.chunkSource).getCubeForLighting(
            Coords.sectionToCube(sectionX),
            Coords.sectionToCube(sectionY),
            Coords.sectionToCube(sectionZ)
        );

        for (int k = 1; k > 0; --k) {
            this.lastChunkPos[k] = this.lastChunkPos[k - 1];
            this.lastChunk[k] = this.lastChunk[k - 1];
        }

        this.lastChunkPos[0] = i;
        this.lastChunk[0] = iblockreader;
        return iblockreader;
    }


    //This is here to throw an actual exception as this method will cause incomplete cube loading when called in a cubic context
    @Inject(method = "getChunk", at = @At("HEAD"))
    private void crashIfInCubicContext(int chunkX, int chunkZ, CallbackInfoReturnable<LightChunk> cir) {
        if (this.isCubic) {
            throw new UnsupportedOperationException("Trying to get chunks in a cubic context! Use \"getCubeReader\" instead!");
        }
    }

    /**
     * Only for use in testing.
     * This should be set automatically in the constructor in a non-test environment
     */
    @VisibleForTesting
    public void setCubic() {
        this.isCubic = true;
    }
}
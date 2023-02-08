package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import io.github.opencubicchunks.cc_core.world.ColumnCubeMap;
import io.github.opencubicchunks.cc_core.world.ColumnCubeMapGetter;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightHeightmapGetter;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.LightSurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.world.server.CubicServerLevel;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProtoChunk.class)
public abstract class MixinProtoChunk extends ChunkAccess implements LightHeightmapGetter, LevelHeightAccessor, ColumnCubeMapGetter, CubicLevelHeightAccessor {

    private Heightmap lightHeightmap;
    private ColumnCubeMap columnCubeMap;

    public MixinProtoChunk(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, Registry<Biome> registry, long l,
                           @Nullable LevelChunkSection[] levelChunkSections,
                           @Nullable BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, registry, l, levelChunkSections, blendingData);
        throw new UnsupportedOperationException("MixinProtoChunk constructor");
    }

    @Shadow public abstract ChunkStatus getStatus();

    @Override
    public Heightmap getLightHeightmap() {
        if (!isCubic()) {
            throw new UnsupportedOperationException("Attempted to get light heightmap on a non-cubic chunk");
        }
        return lightHeightmap;
    }

    @Override
    public ColumnCubeMap getCubeMap() {
        // TODO actually init this properly instead of doing lazy init here
        if (columnCubeMap == null) {
            columnCubeMap = new ColumnCubeMap();
        }
        return columnCubeMap;
    }

    @Inject(
        method = "setStatus(Lnet/minecraft/world/level/chunk/ChunkStatus;)V",
        at = @At("RETURN")
    )
    private void onSetStatus(ChunkStatus status, CallbackInfo ci) {
        if (!this.isCubic()) {
            return;
        }
        if (lightHeightmap == null && this.getStatus().isOrAfter(ChunkStatus.FEATURES)) {
            // Lighting only starts happening after FEATURES, so we init here to avoid creating unnecessary heightmaps
            lightHeightmap = new LightSurfaceTrackerWrapper(this, ((CubicServerLevel) this.levelHeightAccessor).getHeightmapStorage());
        }
    }
}

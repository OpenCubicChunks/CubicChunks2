package io.github.opencubicchunks.cubicchunks.mixin.core.common.chunk;

import io.github.opencubicchunks.cc_core.world.ColumnCubeMap;
import io.github.opencubicchunks.cc_core.world.ColumnCubeMapGetter;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightHeightmapGetter;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.LightSurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.world.server.CubicServerLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProtoChunk.class)
public abstract class MixinProtoChunk extends ChunkAccess implements LightHeightmapGetter, LevelHeightAccessor, ColumnCubeMapGetter, CubicLevelHeightAccessor {

    private boolean isCubic;
    private boolean generates2DChunks;
    private WorldStyle worldStyle;

    private LightSurfaceTrackerWrapper lightHeightmap;
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
        if (!isCubic) {
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

    @Inject(method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;[Lnet/minecraft/world/level/chunk/LevelChunkSection;"
        + "Lnet/minecraft/world/ticks/ProtoChunkTicks;Lnet/minecraft/world/ticks/ProtoChunkTicks;Lnet/minecraft/world/level/LevelHeightAccessor;"
        + "Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V", at = @At("RETURN"))
    private void setCubic(ChunkPos chunkPos, UpgradeData upgradeData, LevelChunkSection[] levelChunkSections, ProtoChunkTicks<Block> blockTicks, ProtoChunkTicks<Fluid> fluidTicks,
                          LevelHeightAccessor heightAccessor, Registry<Biome> registry, BlendingData blendingData, CallbackInfo ci) {
        isCubic = ((CubicLevelHeightAccessor) heightAccessor).isCubic();
        generates2DChunks = ((CubicLevelHeightAccessor) heightAccessor).generates2DChunks();
        worldStyle = ((CubicLevelHeightAccessor) heightAccessor).worldStyle();
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
            lightHeightmap = new LightSurfaceTrackerWrapper((ChunkAccess) this, ((CubicServerLevel) this.levelHeightAccessor).getHeightmapStorage());
        }
    }
}

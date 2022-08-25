package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import java.util.Map;
import java.util.stream.Stream;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.world.storage.CubeProtoTickList;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;
import org.jetbrains.annotations.Nullable;

public class ImposterProtoCube extends ProtoCube {
    private final LevelCube wrapped;
    private final boolean allowWrites;

    public ImposterProtoCube(LevelCube cubeIn, boolean allowWrites) {
        super(
            cubeIn.getCubePos(),
            UpgradeData.EMPTY,
            cubeIn.getSections(),
            new ProtoChunkTicks<>(),
            new ProtoChunkTicks<>(),
            cubeIn.getHeightAccessorForGeneration(),
            cubeIn.getLevel().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY),
            null
        );

        this.wrapped = cubeIn;
        this.allowWrites = allowWrites;
    }

    public LevelCube getWrapped() {
        return this.wrapped;
    }

    @Deprecated @Override public ChunkPos getPos() {
        return this.wrapped.getPos();
    }

    @Override public CubePos getCubePos() {
        return this.wrapped.getCubePos();
    }

    @Override @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return this.wrapped.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.wrapped.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.wrapped.getFluidState(pos);
    }

    @Override
    public int getMaxLightLevel() {
        return this.wrapped.getMaxLightLevel();
    }

    @Override
    public LevelChunkSection getSection(int i) {
        if (this.allowWrites) {
            return this.wrapped.getSection(i);
        }
        return super.getSection(i);
    }

    @Override
    @Nullable
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving) {
        if (this.allowWrites) {
            return this.wrapped.setBlockState(pos, state, isMoving);
        }
        return null;
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        if (this.allowWrites) {
            this.wrapped.setBlockEntity(blockEntity);
        }
    }

    @Override
    public void addEntity(Entity entity) {
        if (this.allowWrites) {
            this.wrapped.addEntity(entity);
        }
    }

    @Override
    public void setStatus(ChunkStatus status) {
        if (this.allowWrites) {
            super.setStatus(status);
        }
    }

    @Override
    public LevelChunkSection[] getSections() {
        return this.wrapped.getSections();
    }

    @Override
    public void setHeightmap(Heightmap.Types type, long[] data) {
    }

    private Heightmap.Types fixType(Heightmap.Types type) {
        if (type == Heightmap.Types.WORLD_SURFACE_WG) {
            return Heightmap.Types.WORLD_SURFACE;
        }
        if (type == Heightmap.Types.OCEAN_FLOOR_WG) {
            return Heightmap.Types.OCEAN_FLOOR;
        }
        return type;
    }

    @Override
    public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type) {
        return this.wrapped.getOrCreateHeightmapUnprimed(type);
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return this.wrapped.getHeight(this.fixType(type), x, z);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int i, int j, int k) {
        return this.wrapped.getNoiseBiome(i, j, k);
    }

    @Override
    @Nullable
    public StructureStart getStartForFeature(ConfiguredStructureFeature<?, ?> structure) {
        return this.wrapped.getStartForFeature(structure);
    }

    @Override
    public void setStartForFeature(ConfiguredStructureFeature<?, ?> structure, StructureStart start) {
    }

    @Override
    public Map<ConfiguredStructureFeature<?, ?>, StructureStart> getAllStarts() {
        return this.wrapped.getAllStarts();
    }

    @Override
    public void setAllStarts(Map<ConfiguredStructureFeature<?, ?>, StructureStart> structureStarts) {
    }

    @Override
    public LongSet getReferencesForFeature(ConfiguredStructureFeature<?, ?> structure) {
        return this.wrapped.getReferencesForFeature(structure);
    }

    @Override
    public void addReferenceForFeature(ConfiguredStructureFeature<?, ?> structure, long reference) {
    }

    @Override
    public Map<ConfiguredStructureFeature<?, ?>, LongSet> getAllReferences() {
        return this.wrapped.getAllReferences();
    }

    @Override
    public void setAllReferences(Map<ConfiguredStructureFeature<?, ?>, LongSet> structureReferences) {
    }

    @Override
    public void setUnsaved(boolean unsaved) {
    }

    @Override
    public boolean isUnsaved() {
        return false;
    }

    @Override
    public ChunkStatus getStatus() {
        return this.wrapped.getStatus();
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
    }

    @Override
    public void markPosForPostprocessing(BlockPos pos) {
    }

    @Override
    public void setBlockEntityNbt(CompoundTag tag) {
    }

    @Override
    @Nullable
    public CompoundTag getBlockEntityNbt(BlockPos pos) {
        return this.wrapped.getBlockEntityNbt(pos);
    }

    @Override
    @Nullable
    public CompoundTag getBlockEntityNbtForSaving(BlockPos pos) {
        return this.wrapped.getBlockEntityNbtForSaving(pos);
    }

    @Override
    public Stream<BlockPos> getLights() {
        return this.wrapped.getLights();
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        if (this.allowWrites) {
            return this.wrapped.getBlockTicks();
        }
        return BlackholeTickAccess.emptyContainer();
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        if (this.allowWrites) {
            return this.wrapped.getFluidTicks();
        }
        return BlackholeTickAccess.emptyContainer();
    }

    @Override
    public ChunkAccess.TicksToSave getTicksForSerialization() {
        return this.wrapped.getTicksForSerialization();
    }

    @Override
    @Nullable
    public BlendingData getBlendingData() {
        return this.wrapped.getBlendingData();
    }

    @Override
    public void setBlendingData(BlendingData blendingData) {
        this.wrapped.setBlendingData(blendingData);
    }

    @Override
    public CarvingMask getCarvingMask(GenerationStep.Carving step) {
        if (this.allowWrites) {
            return super.getCarvingMask(step);
        }
        throw Util.pauseInIde(new UnsupportedOperationException("Meaningless in this context"));
    }

    @Override
    public CarvingMask getOrCreateCarvingMask(GenerationStep.Carving step) {
        if (this.allowWrites) {
            return super.getOrCreateCarvingMask(step);
        }
        throw Util.pauseInIde(new UnsupportedOperationException("Meaningless in this context"));
    }

    @Override
    public boolean isLightCorrect() {
        return this.wrapped.isLightCorrect();
    }

    @Override
    public void setLightCorrect(boolean lightCorrect) {
        this.wrapped.setLightCorrect(lightCorrect);
    }

    @Override
    public void fillBiomesFromNoise(BiomeResolver biomeResolver, Climate.Sampler sampler) {
        if (this.allowWrites) {
            this.wrapped.fillBiomesFromNoise(biomeResolver, sampler);
        }
    }
}


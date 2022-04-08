package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.world.storage.CubeProtoTickList;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.ProtoChunkTicks;

@SuppressWarnings("deprecation")
public class ImposterProtoCube extends ProtoCube {

    private final LevelCube cube;

    public ImposterProtoCube(LevelCube cubeIn, LevelHeightAccessor levelHeightAccessor, Registry<Biome> biomeRegistry) {
        super(
            cubeIn.getCubePos(),
            UpgradeData.EMPTY,
            cubeIn.getCubeSections(),
            new ProtoChunkTicks<>(),
            new ProtoChunkTicks<>(),
            levelHeightAccessor,
            biomeRegistry,
            null
        );

        this.cube = cubeIn;
    }

    public LevelCube getCube() {
        return this.cube;
    }

    @Deprecated @Override public ChunkPos getPos() {
        throw new UnsupportedOperationException("This function should never be called!");
    }

    @Override public CubePos getCubePos() {
        return this.cube.getCubePos();
    }

    @Deprecated @Override public LevelChunkSection[] getSections() {
        return getCubeSections();
    }

    @Override public LevelChunkSection[] getCubeSections() {
        return cube.getCubeSections();
    }

    //STATUS
    @Deprecated @Override public ChunkStatus getStatus() {
        return this.cube.getCubeStatus();
    }

    @Override public ChunkStatus getCubeStatus() {
        return cube.getCubeStatus();
    }

    //BLOCK
    @Override @Nullable public BlockState setBlock(BlockPos pos, BlockState state, boolean isMoving) {
        return null;
    }

    @Override public BlockState getBlockState(int x, int y, int z) {
        return this.cube.getBlockState(x, y, z);
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        return this.cube.getFluidState(pos);
    }

    //ENTITY
    @Deprecated @Override public void addEntity(Entity entityIn) {
    }

    @Override public void addCubeEntity(Entity entity) {
    }

    //TILE ENTITY
    @Deprecated @Override public void setBlockEntityNbt(CompoundTag nbt) {
    }

    @Override public void setCubeBlockEntity(CompoundTag nbt) {
    }

    @Deprecated @Override public void removeBlockEntity(BlockPos pos) {
    }

    @Override public void removeCubeBlockEntity(BlockPos pos) {
    }

    @Deprecated @Override public void setBlockEntity(BlockEntity tileEntity) {
    }

    @Override public void setCubeBlockEntity(BlockEntity blockEntity) {
    }

    @Override @Nullable public BlockEntity getBlockEntity(BlockPos pos) {
        return this.cube.getBlockEntity(pos);
    }

    @Deprecated @Override @Nullable public CompoundTag getBlockEntityNbtForSaving(BlockPos pos) {
        return this.getCubeBlockEntityNbtForSaving(pos);
    }

    @Override @Nullable public CompoundTag getCubeBlockEntityNbtForSaving(BlockPos pos) {
        return this.cube.getCubeBlockEntityNbtForSaving(pos);
    }

    @Deprecated @Override @Nullable public CompoundTag getBlockEntityNbt(BlockPos pos) {
        return this.getCubeBlockEntityNbt(pos);
    }

    @Override @Nullable public CompoundTag getCubeBlockEntityNbt(BlockPos pos) {
        return this.cube.getCubeBlockEntityNbt(pos);
    }

    //LIGHTING
    @Deprecated @Override public void setLightCorrect(boolean lightCorrectIn) {
        throw new UnsupportedOperationException("Chunk method called on a cube!");
    }

    @Override public void setCubeLight(boolean lightCorrectIn) {
        this.cube.setCubeLight(lightCorrectIn);
    }

    @Deprecated @Override public boolean isLightCorrect() {
        throw new UnsupportedOperationException("Chunk method called on a cube!");
    }

    @Override public boolean hasCubeLight() {
        return this.cube.hasCubeLight();
    }

    @Deprecated @Override public Stream<BlockPos> getLights() {
        return this.getCubeLights();
    }

    @Override public Stream<BlockPos> getCubeLights() {
        return this.cube.getCubeLights();
    }

    @Override public int getMaxLightLevel() {
        return this.cube.getMaxLightLevel();
    }

    //MISC
    @Deprecated @Override public void setUnsaved(boolean modified) {
    }

    @Override public void setDirty(boolean modified) {
    }

    @Deprecated @Override public boolean isUnsaved() {
        return false;
    }

    @Override public boolean isDirty() {
        return false;
    }

    @Override public boolean isEmptyCube() {
        return this.cube.isEmptyCube();
    }

    @Override public void setHeightmap(Heightmap.Types type, long[] data) {
    }

    private Heightmap.Types fixType(Heightmap.Types type) {
        if (type == Heightmap.Types.WORLD_SURFACE_WG) {
            return Heightmap.Types.WORLD_SURFACE;
        } else {
            return type == Heightmap.Types.OCEAN_FLOOR_WG ? Heightmap.Types.OCEAN_FLOOR : type;
        }
    }

    @Override public int getHeight(Heightmap.Types types, int x, int z) {
        return this.cube.getHeight(this.fixType(types), x, z);
    }

    // getStructureStart
    @Override @Nullable public StructureStart getStartForFeature(ConfiguredStructureFeature<?, ?> var1) {
        return this.cube.getStartForFeature(var1);
    }

    @Override public void setStartForFeature(ConfiguredStructureFeature<?, ?> structureIn, StructureStart structureStartIn) {
    }

    @Override public Map<ConfiguredStructureFeature<?, ?>, StructureStart> getAllCubeStructureStarts() {
        return this.cube.getAllCubeStructureStarts();
    }

    @Override
    public Map<ConfiguredStructureFeature<?, ?>, StructureStart> getAllStarts() {
        return this.getAllCubeStructureStarts();
    }

    @Override public void setAllStarts(Map<ConfiguredStructureFeature<?, ?>, StructureStart> structureStartsIn) {
    }

    @Override public LongSet getReferencesForFeature(ConfiguredStructureFeature<?, ?> structureIn) {
        return this.cube.getReferencesForFeature(structureIn);
    }

    @Override public void addReferenceForFeature(ConfiguredStructureFeature<?, ?> structure, long reference) {
    }

    @Override public Map<ConfiguredStructureFeature<?, ?>, LongSet> getAllReferences() {
        return this.cube.getAllReferences();
    }

    @Override public void setAllReferences(Map<ConfiguredStructureFeature<?, ?>, LongSet> structures) {
    }

    @Override public void markPosForPostprocessing(BlockPos pos) {
    }

    /*
    TODO: readd fake tick list accessors
    public CubePrimerTickList<Block> getBlocksToBeTicked() {
        return new CubePrimerTickList<>((p_209219_0_) -> {
            return p_209219_0_.getDefaultState().isAir();
        }, this.getPos());
    }

    public CubePrimerTickList<Fluid> getFluidsToBeTicked() {
        return new CubePrimerTickList<>((p_209218_0_) -> {
            return p_209218_0_ == Fluids.EMPTY;
        }, this.getPos());
    }
    */

    @Override
    public CarvingMask getCarvingMask(GenerationStep.Carving type) {
        throw Util.pauseInIde(new UnsupportedOperationException("Meaningless in this context"));
    }

    @Override
    public CarvingMask getOrCreateCarvingMask(GenerationStep.Carving type) {
        throw Util.pauseInIde(new UnsupportedOperationException("Meaningless in this context"));
    }

    @Override
    public void setCarvingMask(GenerationStep.Carving type, CarvingMask mask) {
        throw Util.pauseInIde(new UnsupportedOperationException("Meaningless in this context"));
    }
}


package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import static io.github.opencubicchunks.cc_core.api.CubicConstants.DIAMETER_IN_BLOCKS;
import static io.github.opencubicchunks.cc_core.api.CubicConstants.DIAMETER_IN_SECTIONS;
import static io.github.opencubicchunks.cc_core.api.CubicConstants.SECTION_COUNT;
import static io.github.opencubicchunks.cc_core.api.CubicConstants.SECTION_DIAMETER;
import static io.github.opencubicchunks.cc_core.utils.Coords.blockToCubeLocalSection;
import static io.github.opencubicchunks.cc_core.utils.Coords.blockToIndex;
import static io.github.opencubicchunks.cc_core.utils.Coords.columnToColumnIndex;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.ColumnCubeMapGetter;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cubicchunks.levelgen.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.LightSurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.world.lighting.SkyLightColumnChecker;
import io.github.opencubicchunks.cubicchunks.world.server.CubicServerLevel;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;

/* FIXME: Before 1.18, ProtoCube extended ProtoChunk and implemented CubeAccess. After 1.18, cube access is an abstract class so we can only extend one.
 * Now it no longer extends ProtoChunk so the usages must be fixed. If this turns out to be easier the other way round, this should be changed
 */
public class ProtoCube extends CubeAccess {
    private static final BlockState EMPTY_BLOCK = Blocks.AIR.defaultBlockState();
    private static final FluidState EMPTY_FLUID = Fluids.EMPTY.defaultFluidState();

    private ChunkStatus status = ChunkStatus.EMPTY;

    //Structures
    private final Map<GenerationStep.Carving, CarvingMask> carvingMasks;
    private final Map<BlockPos, BlockState> featuresStateMap = new HashMap<>();

    private final List<BlockPos> lightPositions = Lists.newArrayList();
    private LevelLightEngine lightEngine;

    private int columnX;
    private int columnZ;

    private int minBuildHeight;
    private int height;

    //From ProtoChunk
    private final List<BlockPos> lights = Lists.newArrayList();
    private final List<CompoundTag> entities = Lists.newArrayList();

    private final ProtoChunkTicks<Block> blockTicks;
    private final ProtoChunkTicks<Fluid> fluidTicks;

    private final LevelHeightAccessor accessor2D;

    public ProtoCube(CubePos cubePos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, Registry<Biome> biomes, @Nullable BlendingData blendingData) {
        this(cubePos, upgradeData, null, new ProtoChunkTicks<>(), new ProtoChunkTicks<>(), levelHeightAccessor, biomes, blendingData);
    }

    public ProtoCube(CubePos cubePos, UpgradeData upgradeData, @Nullable LevelChunkSection[] sections, ProtoChunkTicks<Block> blockProtoTickList, ProtoChunkTicks<Fluid> fluidProtoTickList,
                     LevelHeightAccessor levelHeightAccessor, Registry<Biome> biomes, @Nullable BlendingData blendingData) {
        super(
            cubePos,
            ((CubicLevelHeightAccessor) levelHeightAccessor),
            upgradeData,
            new FakeSectionCount(cubePos.getY(), levelHeightAccessor, SECTION_COUNT),
            biomes,
            0L,
            sections,
            blendingData
        );

        this.accessor2D = levelHeightAccessor;

        this.blockTicks = blockProtoTickList;
        this.fluidTicks = fluidProtoTickList;

        this.carvingMasks = new Object2ObjectArrayMap<>();

        this.minBuildHeight = levelHeightAccessor.getMinBuildHeight();
        this.height = levelHeightAccessor.getHeight();
    }

    public void moveColumns(int newColumnX, int newColumnZ) {
        this.columnX = newColumnX;
        this.columnZ = newColumnZ;
    }

    public void setHeightToCubeBounds(boolean cubeBounds) {
        if (cubeBounds) {
            this.minBuildHeight = this.cubePos.minCubeY();
            this.height = DIAMETER_IN_BLOCKS;
        } else {
            this.minBuildHeight = this.levelHeightAccessor.getMinBuildHeight();
            this.height = this.levelHeightAccessor.getHeight();
        }
    }

    private ChunkSource getChunkSource() {
        if (this.accessor2D instanceof CubeWorldGenRegion region) {
            return region.getChunkSource();
        } else {
            return ((ServerLevel) this.accessor2D).getChunkSource();
        }
    }

    //STATUS
    public void setCubeStatus(ChunkStatus newStatus) {
        this.status = newStatus;
    }

    public void updateCubeStatus(ChunkStatus newStatus) {
        this.status = newStatus;

        if (this.status == ChunkStatus.FEATURES) {
            onEnteringFeaturesStatus();
        }
    }

    public void onEnteringFeaturesStatus() {
        assert !(this instanceof ImposterProtoCube) : "Trying to set features status on ImposterProtoCube";
        ChunkSource chunkSource = getChunkSource();

        for (int dx = 0; dx < DIAMETER_IN_SECTIONS; dx++) {
            for (int dz = 0; dz < DIAMETER_IN_SECTIONS; dz++) {

                // get the chunk for this section
                ChunkPos chunkPos = this.cubePos.asChunkPos(dx, dz);
                ChunkAccess chunk = this.columns[dx + dz * DIAMETER_IN_SECTIONS];

                // the load order guarantees the chunk being present
                assert (chunk != null && chunk.getStatus().isOrAfter(ChunkStatus.FEATURES));

                ((ColumnCubeMapGetter) chunk).getCubeMap().markLoaded(this.cubePos.getY());

                LightSurfaceTrackerWrapper lightHeightmap = ((LightHeightmapGetter) chunk).getServerLightHeightmap();

                int[] beforeValues = new int[SECTION_DIAMETER * SECTION_DIAMETER];
                for (int z = 0; z < SECTION_DIAMETER; z++) {
                    for (int x = 0; x < SECTION_DIAMETER; x++) {
                        beforeValues[z * SECTION_DIAMETER + x] = lightHeightmap.getFirstAvailable(x, z);
                    }
                }

                lightHeightmap.loadCube(((CubicServerLevel) this.accessor2D).getHeightmapStorage(), this);

                for (int z = 0; z < SECTION_DIAMETER; z++) {
                    for (int x = 0; x < SECTION_DIAMETER; x++) {
                        int beforeValue = beforeValues[z * SECTION_DIAMETER + x];
                        int afterValue = lightHeightmap.getFirstAvailable(x, z);
                        if (beforeValue != afterValue) {
                            ((SkyLightColumnChecker) chunkSource.getLightEngine()).checkSkyLightColumn((ColumnCubeMapGetter) chunk,
                                chunkPos.getBlockX(x), chunkPos.getBlockZ(z), beforeValue, afterValue);
                        }
                    }
                }
            }
        }
    }

    @Override @Nullable
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving) {
        int xSection = pos.getX() & 0xF;
        int ySection = pos.getY() & 0xF;
        int zSection = pos.getZ() & 0xF;
        int sectionIdx = blockToIndex(pos.getX(), pos.getY(), pos.getZ());

        LevelChunkSection section = this.sections[sectionIdx];
        if (section.hasOnlyAir() && state == EMPTY_BLOCK) {
            return state;
        }

        /*if (section == EMPTY_SECTION) {
            section = new LevelChunkSection(Coords.cubeToMinBlock(this.cubePos.getY() + Coords.sectionToMinBlock(Coords.indexToY(sectionIdx))));
            this.sections[sectionIdx] = section;
        }*/

        if (state.getLightEmission() > 0) {
            SectionPos sectionPosAtIndex = Coords.sectionPosByIndex(this.cubePos, sectionIdx);
            this.lightPositions.add(new BlockPos(
                    xSection + Coords.sectionToMinBlock(sectionPosAtIndex.getX()),
                    ySection + Coords.sectionToMinBlock(sectionPosAtIndex.getY()),
                    zSection + Coords.sectionToMinBlock(sectionPosAtIndex.getZ()))
            );
        }

        BlockState lastState = section.setBlockState(xSection, ySection, zSection, state, false);
        if (this.status.isOrAfter(ChunkStatus.LIGHT) && state != lastState && (state.getLightBlock(this, pos) != lastState.getLightBlock(this, pos)
                || state.getLightEmission() != lastState.getLightEmission() || state.useShapeForLightOcclusion() || lastState.useShapeForLightOcclusion())) {

            // get the chunk containing the updated block
            ChunkSource chunkSource = getChunkSource();
            ChunkPos chunkPos = Coords.chunkPosByIndex(this.cubePos, sectionIdx);
            BlockGetter chunk = chunkSource.getChunkForLighting(chunkPos.x, chunkPos.z);

            // the load order guarantees the chunk being present
            assert (chunk != null);

            LightSurfaceTrackerWrapper lightHeightmap = ((LightHeightmapGetter) chunk).getServerLightHeightmap();

            int relX = pos.getX() & 15;
            int relZ = pos.getZ() & 15;
            int oldHeight = lightHeightmap.getFirstAvailable(relX, relZ);
            // Light heightmap update needs to occur before the light engine update.
            // Not sure if this is the right blockstate to pass in, but it doesn't actually matter since we don't use it
            lightHeightmap.update(relX, pos.getY(), relZ, state);
            int newHeight = lightHeightmap.getFirstAvailable(relX, relZ);
            if (newHeight != oldHeight) {
                ((SkyLightColumnChecker) chunkSource.getLightEngine()).checkSkyLightColumn((ColumnCubeMapGetter) chunk, pos.getX(), pos.getZ(), oldHeight, newHeight);
            }

            lightEngine.checkBlock(pos);
        }

        EnumSet<Heightmap.Types> heightMapsAfter = this.getStatus().heightmapsAfter();

        int xChunk = blockToCubeLocalSection(pos.getX());
        int zChunk = blockToCubeLocalSection(pos.getZ());
        int chunkIdx = columnToColumnIndex(xChunk, zChunk);

        IntPredicate isOpaquePredicate = SurfaceTrackerWrapper.opaquePredicateForState(state);
        for (Heightmap.Types types : heightMapsAfter) {
            SurfaceTrackerLeaf leaf = getHeightmapSections(types)[chunkIdx];
            leaf.onSetBlock(xSection, pos.getY(), zSection, isOpaquePredicate);
        }

        return lastState;
    }

    /**
     * Gets the SurfaceTrackerSections for the given Heightmap.Types for all chunks of this cube.
     * Lazily initializes new SurfaceTrackerSections.
     */
    private SurfaceTrackerLeaf[] getHeightmapSections(Heightmap.Types type) {
        return cubeHeightmaps.computeIfAbsent(type, t -> {
            SectionPos cubeMinSection = this.cubePos.asSectionPos();
            SurfaceTrackerLeaf[] surfaceTrackerLeaves = new SurfaceTrackerLeaf[DIAMETER_IN_SECTIONS * DIAMETER_IN_SECTIONS];
            for (int dx = 0; dx < DIAMETER_IN_SECTIONS; dx++) {
                for (int dz = 0; dz < DIAMETER_IN_SECTIONS; dz++) {
                    int idx = columnToColumnIndex(dx, dz);
                    SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(cubePos.getY(), null, (byte) type.ordinal());
                    leaf.loadSource(cubeMinSection.x() + dx, cubeMinSection.z() + dz,
                        ((CubicServerLevel) ((ServerLevelAccessor) this.accessor2D).getLevel()).getHeightmapStorage(), this);
                    // On creation of a new node for a cube, both the node and its parents must be marked dirty
                    leaf.setAllDirty();
                    leaf.markAncestorsDirty();
                    surfaceTrackerLeaves[idx] = leaf;
                }
            }
            return surfaceTrackerLeaves;
        });
    }

    @Override public void setFeatureBlocks(BlockPos pos, BlockState state) {
       featuresStateMap.put(pos.immutable(), state);
    }

    public void applyFeatureStates() {
        featuresStateMap.forEach((pos, state) -> {
            setBlockState(pos, state, false);
        });
    }

    public Map<BlockPos, BlockState> getFeaturesStateMap() {
        return featuresStateMap;
    }

    public void addCubeEntity(CompoundTag entityCompound) {
        this.entities.add(entityCompound);
    }

    public List<CompoundTag> getEntities() {
        return this.entities;
    }

    @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) {
        return this.blockEntities.get(pos);
    }

    public Map<BlockPos, CompoundTag> getBlockEntityNbts() {
        return this.pendingBlockEntities;
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int index = blockToIndex(x, y, z);
        LevelChunkSection section = this.sections[index];
        if (!section.hasOnlyAir()) {
            return section.getFluidState(x & 15, y & 15, z & 15);
        } else {
            return EMPTY_FLUID;
        }
    }

    @Override public int getCubeLocalHeight(Heightmap.Types type, int x, int z) {
        SurfaceTrackerLeaf[] leaves = getHeightmapSections(type);
        int xSection = blockToCubeLocalSection(x);
        int zSection = blockToCubeLocalSection(z);

        int idx = columnToColumnIndex(xSection, zSection);

        SurfaceTrackerLeaf leaf = leaves[idx];
        return leaf.getHeight(Coords.blockToLocal(x), Coords.blockToLocal(z));
    }

    @Override public int getHeight(Heightmap.Types types, int x, int z) {
        return getCubeLocalHeight(types, x, z);
    }

    public static BlockPos unpackToWorld(short sectionRel, int sectionIdx, CubePos cubePosIn) {
        BlockPos pos = Coords.sectionPosToMinBlockPos(Coords.sectionPosByIndex(cubePosIn, sectionIdx));
        int xPos = (sectionRel & 15) + pos.getX();
        int yPos = (sectionRel >>> 4 & 15) + pos.getY();
        int zPos = (sectionRel >>> 8 & 15) + pos.getZ();
        return new BlockPos(xPos, yPos, zPos);
    }

    /*****ChunkPrimer Overrides*****/

    //@Override
    public ShortList[] getPackedLights() {
        ShortList[] shortLists = new ShortList[SECTION_COUNT];

        for (BlockPos light : this.lights) {
            ChunkAccess.getOrCreateOffsetList(shortLists, blockToIndex(light)).add(ProtoChunk.packOffsetCoordinates(light));
        }

        return shortLists;
    }

    public void setLightEngine(LevelLightEngine lightEngine) {
        this.lightEngine = lightEngine;
    }

    public void addLight(short packedPosition, int yOffset) {
        addLight(unpackToWorld(packedPosition, yOffset, this.cubePos));
    }

    public void addLight(BlockPos lightPos) {
        this.lightPositions.add(lightPos.immutable());
    }

    @Nullable
    //@Override
    public CarvingMask getCarvingMask(GenerationStep.Carving type) {
        return this.carvingMasks.get(type);
    }

    //@Override
    public CarvingMask getOrCreateCarvingMask(GenerationStep.Carving type) {
        return this.carvingMasks.computeIfAbsent(type, (carvingx) -> new CarvingMask(this.getHeight(), this.getMinBuildHeight()));
    }

    //@Override
    public void setCarvingMask(GenerationStep.Carving type, CarvingMask mask) {
        this.carvingMasks.put(type, mask);
    }

    @Override
    public void markPosForPostprocessing(BlockPos pos) {
        if (!this.isOutsideBuildHeight(pos)) {
            ChunkAccess.getOrCreateOffsetList(this.getPostProcessing(), blockToIndex(pos.getX(), pos.getY(), pos.getZ())).add(ProtoChunk.packOffsetCoordinates(pos));
        }
    }

    @Nullable @Override public CompoundTag getBlockEntityNbtForSaving(BlockPos pos) {
        BlockEntity tileEntity = this.getBlockEntity(pos);
        return tileEntity != null ? tileEntity.saveWithFullMetadata() : this.pendingBlockEntities.get(pos);
    }

    @Override public Stream<BlockPos> getLights() {
        return this.lightPositions.stream();
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public TicksToSave getTicksForSerialization() {
        return new TicksToSave(this.blockTicks, this.fluidTicks);
    }

    public Map<BlockPos, BlockEntity> getBlockEntities() {
        return this.blockEntities;
    }

    @Override public void removeBlockEntity(BlockPos pos) {
        this.blockEntities.remove(pos);
        this.pendingBlockEntities.remove(pos);
    }

    @Override public void setBlockEntity(BlockEntity blockEntity) {
        this.blockEntities.put(blockEntity.getBlockPos(), blockEntity);
    }

    public void setBlockEntityNbt(CompoundTag tag) {
        this.pendingBlockEntities.put(BlockEntity.getPosFromTag(tag), tag);
    }

    //ENTITY
    @Override public void addEntity(Entity entity) {
        CompoundTag nbt = new CompoundTag();
        entity.save(nbt);
        this.addCubeEntity(nbt);
    }

    public void setStatus(ChunkStatus status) {
        this.status = status;
        this.setUnsaved(true);
    }

    @Override public ChunkStatus getStatus() {
        return this.status;
    }

    //@Override
    //TODO: This might not be needed
    public BlockPos getHeighestPosition(Heightmap.Types type) {
        BlockPos.MutableBlockPos mutableBlockPos = null;

        for (int x = this.cubePos.minCubeX(); x <= this.cubePos.maxCubeX(); ++x) {
            for (int z = this.cubePos.minCubeZ(); z <= this.cubePos.maxCubeZ(); ++z) {
                int heightAtPos = this.getHeight(type, x & 15, z & 15);
                if (mutableBlockPos == null) {
                    mutableBlockPos = new BlockPos.MutableBlockPos().set(x, heightAtPos, z);
                }

                if (mutableBlockPos.getY() < heightAtPos) {
                    mutableBlockPos.set(x, heightAtPos, z);
                }
            }
        }
        return mutableBlockPos != null ? mutableBlockPos.immutable() : new BlockPos.MutableBlockPos().set(this.cubePos.minCubeX(), this.getHeight(type, this.cubePos.minCubeX() & 15,
            this.cubePos.minCubeZ() & 15), this.cubePos.minCubeZ() & 15);

    }

    @Deprecated @Override public ChunkPos getPos() {
        return this.cubePos.asChunkPos(columnX, columnZ);
    }

    @Override public int getHeight() {
        return this.height;
    }

    @Override public int getMinBuildHeight() {
        return this.minBuildHeight;
    }

    //From Proto Cube
    private static <T> LevelChunkTicks<T> unpackTicks(ProtoChunkTicks<T> protoChunkTicks) {
        return new LevelChunkTicks<>(protoChunkTicks.scheduledTicks());
    }

    public LevelChunkTicks<Block> unpackBlockTicks() {
        return unpackTicks(this.blockTicks);
    }

    public LevelChunkTicks<Fluid> unpackFluidTicks() {
        return unpackTicks(this.fluidTicks);
    }

    public static class FakeSectionCount implements LevelHeightAccessor, CubicLevelHeightAccessor {
        private final int height;
        private final int minHeight;
        private final int fakeSectionCount;
        private final boolean isCubic;
        private final boolean generates2DChunks;
        private final WorldStyle worldStyle;
        private final int cubeY;

        public FakeSectionCount(int cubeY, LevelHeightAccessor levelHeightAccessor, int sectionCount) {
            this(cubeY, levelHeightAccessor.getHeight(), levelHeightAccessor.getMinBuildHeight(), sectionCount, ((CubicLevelHeightAccessor) levelHeightAccessor).isCubic(),
                ((CubicLevelHeightAccessor) levelHeightAccessor).generates2DChunks(), ((CubicLevelHeightAccessor) levelHeightAccessor).worldStyle());
        }

        private FakeSectionCount(int cubeY, int height, int minHeight, int sectionCount, boolean isCubic, boolean generates2DChunks, WorldStyle worldStyle) {
            this.height = height;
            this.cubeY = cubeY;
            this.minHeight = minHeight;
            this.fakeSectionCount = sectionCount;
            this.isCubic = isCubic;
            this.generates2DChunks = generates2DChunks;
            this.worldStyle = worldStyle;
        }

        @Override public int getSectionYFromSectionIndex(int sectionIndex) {
            return Coords.cubeToSection(this.cubeY, Coords.indexToY(sectionIndex));
        }

        @Override public int getHeight() {
            return this.height;
        }

        @Override public int getMinBuildHeight() {
            return this.minHeight;
        }

        @Override public int getSectionsCount() {
            return this.fakeSectionCount;
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
    }
}

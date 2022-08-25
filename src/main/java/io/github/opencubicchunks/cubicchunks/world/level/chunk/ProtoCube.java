package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import static io.github.opencubicchunks.cc_core.utils.Coords.blockToCubeLocalSection;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.ColumnCubeMapGetter;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cc_core.world.heightmap.HeightmapStorage;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cubicchunks.levelgen.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkBiomeContainerAccess;
import io.github.opencubicchunks.cubicchunks.world.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.LightSurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.world.lighting.SkyLightColumnChecker;
import io.github.opencubicchunks.cubicchunks.world.server.CubicServerLevel;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;

/* FIXME: Before 1.18, ProtoCube extended ProtoChunk and implemented CubeAccess. After 1.18, cube access is an abstract class so we can only extend one.
 * Now it no longer extends ProtoChunk so the usages must be fixed. If this turns out to be easier the other way round, this should be changed
 */
public class ProtoCube extends CubeAccess implements CubicLevelHeightAccessor {
    private static final BlockState EMPTY_BLOCK = Blocks.AIR.defaultBlockState();
    private static final FluidState EMPTY_FLUID = Fluids.EMPTY.defaultFluidState();

    private final CubePos cubePos;
    private ChunkStatus status = ChunkStatus.EMPTY;

    private final Map<Heightmap.Types, SurfaceTrackerLeaf[]> cubeHeightmaps;

    private final SurfaceTrackerLeaf[] lightHeightmaps = new SurfaceTrackerLeaf[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];

    //Structures
    private final Map<GenerationStep.Carving, CarvingMask> carvingMasks;
    private final Map<BlockPos, BlockState> featuresStateMap = new HashMap<>();

    private volatile boolean modified = true;

    private final List<BlockPos> lightPositions = Lists.newArrayList();
    private volatile boolean hasLight;
    private LevelLightEngine lightEngine;

    private final boolean isCubic;
    private final boolean generates2DChunks;
    private final CubicLevelHeightAccessor.WorldStyle worldStyle;

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
            upgradeData,
            new FakeSectionCount(levelHeightAccessor, CubeAccess.SECTION_COUNT),
            biomes,
            0L,
            sections,
            blendingData
        );

        this.accessor2D = levelHeightAccessor;

        this.blockTicks = blockProtoTickList;
        this.fluidTicks = fluidProtoTickList;

        this.cubeHeightmaps = Maps.newEnumMap(Heightmap.Types.class);
        this.carvingMasks = new Object2ObjectArrayMap<>();

        this.cubePos = cubePos;

        isCubic = ((CubicLevelHeightAccessor) levelHeightAccessor).isCubic();
        generates2DChunks = ((CubicLevelHeightAccessor) levelHeightAccessor).generates2DChunks();
        worldStyle = ((CubicLevelHeightAccessor) levelHeightAccessor).worldStyle();

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
            this.height = CubeAccess.DIAMETER_IN_BLOCKS;
        } else {
            this.minBuildHeight = this.levelHeightAccessor.getMinBuildHeight();
            this.height = this.levelHeightAccessor.getHeight();
        }
    }

    @Override public CubePos getCubePos() {
        return this.cubePos;
    }

    private ChunkSource getChunkSource() {
        if (this.accessor2D instanceof CubeWorldGenRegion region) {
            return region.getChunkSource();
        } else {
            return ((ServerLevel) this.accessor2D).getChunkSource();
        }
    }

    @Override public int getSectionIndex(int i) {
        return super.getSectionIndex(i);
    }

    @Override public LevelChunkSection getSection(int i) {
        return super.getSection(i);
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
        ChunkSource chunkSource = getChunkSource();

        for (int dx = 0; dx < CubeAccess.DIAMETER_IN_SECTIONS; dx++) {
            for (int dz = 0; dz < CubeAccess.DIAMETER_IN_SECTIONS; dz++) {

                // get the chunk for this section
                ChunkPos chunkPos = this.cubePos.asChunkPos(dx, dz);
                ChunkAccess chunk = chunkSource.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY, false);

                if (!(chunk != null && chunk.getStatus().isOrAfter(ChunkStatus.FEATURES))) {
                    int j = 0;
                }
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

    @Override
    public void sectionLoaded(@Nonnull SurfaceTrackerLeaf surfaceTrackerLeaf, int localSectionX, int localSectionZ) {
        int idx = localSectionX + localSectionZ * DIAMETER_IN_SECTIONS;

        if (surfaceTrackerLeaf.getRawType() == -1) { //light
            this.lightHeightmaps[idx] = surfaceTrackerLeaf;
        } else { // normal heightmap
            this.cubeHeightmaps.computeIfAbsent(Heightmap.Types.values()[surfaceTrackerLeaf.getRawType()],
                type -> new SurfaceTrackerLeaf[DIAMETER_IN_SECTIONS * DIAMETER_IN_SECTIONS]
            )[idx] = surfaceTrackerLeaf;
        }
    }

    @Override
    public void unloadSource(@Nonnull HeightmapStorage storage) {
        SectionPos cubeMinSection = this.cubePos.asSectionPos();
        for (SurfaceTrackerLeaf[] heightmapLeaves : this.cubeHeightmaps.values()) {
            for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                    int i = localSectionX + localSectionZ * CubeAccess.DIAMETER_IN_SECTIONS;
                    if (heightmapLeaves[i] != null) {
                        heightmapLeaves[i].sourceUnloaded(cubeMinSection.x() + localSectionX, cubeMinSection.z() + localSectionZ, storage);
                        heightmapLeaves[i] = null;
                    }
                }
            }
        }
        SurfaceTrackerLeaf[] lightHeightmapLeaves = this.lightHeightmaps;
        for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
            for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                int i = localSectionX + localSectionZ * CubeAccess.DIAMETER_IN_SECTIONS;
                if (lightHeightmapLeaves[i] != null) {
                    lightHeightmapLeaves[i].sourceUnloaded(cubeMinSection.x() + localSectionX, cubeMinSection.z() + localSectionZ, storage);
                    lightHeightmapLeaves[i] = null;
                }
            }
        }
    }

    public SurfaceTrackerLeaf[] getLightHeightmaps() {
        return lightHeightmaps;
    }

    @Override @Nullable
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving) {
        int xSection = pos.getX() & 0xF;
        int ySection = pos.getY() & 0xF;
        int zSection = pos.getZ() & 0xF;
        int sectionIdx = Coords.blockToIndex(pos.getX(), pos.getY(), pos.getZ());

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
        int chunkIdx = xChunk + zChunk * DIAMETER_IN_SECTIONS;

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
            SurfaceTrackerLeaf[] surfaceTrackerLeaves = new SurfaceTrackerLeaf[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
            for (int dx = 0; dx < CubeAccess.DIAMETER_IN_SECTIONS; dx++) {
                for (int dz = 0; dz < CubeAccess.DIAMETER_IN_SECTIONS; dz++) {
                    int idx = dx + dz * CubeAccess.DIAMETER_IN_SECTIONS;
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

    public List<CompoundTag> getCubeEntities() {
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
        int index = Coords.blockToIndex(x, y, z);
        LevelChunkSection section = this.sections[index];
        if (!section.hasOnlyAir()) {
            return section.getFluidState(x & 15, y & 15, z & 15);
        } else {
            return EMPTY_FLUID;
        }
    }

    @Override public int getHighest(int x, int z, byte heightmapType) {
        if (heightmapType == -1) { //light
            return getHighestLight(x, z);
        } else { //normal heightmaps
            int maxY = Integer.MIN_VALUE;
            for (int dy = CubeAccess.DIAMETER_IN_BLOCKS - 1; dy >= 0; dy--) {
                if (SurfaceTrackerWrapper.HEIGHTMAP_TYPES[heightmapType].isOpaque().test(this.getBlockState(new BlockPos(x, dy, z)))) {
                    int minY = this.cubePos.getY() * DIAMETER_IN_BLOCKS;
                    maxY = minY + dy;
                    break;
                }
            }
            return maxY;
        }
    }

    public int getHighestLight(int x, int z) {
        int maxY = Integer.MIN_VALUE;

        int xSection = blockToCubeLocalSection(x);
        int zSection = blockToCubeLocalSection(z);

        int idx = xSection + zSection * DIAMETER_IN_SECTIONS;
        SurfaceTrackerLeaf sectionAbove = this.lightHeightmaps[idx].getSectionAbove();

        int dy = CubeAccess.DIAMETER_IN_BLOCKS - 1;

        // TODO unknown behavior for occlusion on a loading boundary (i.e. sectionAbove == null)
        BlockState above;
        if (sectionAbove == null || sectionAbove.getSource() == null) {
            above = Blocks.AIR.defaultBlockState();
        } else {
            above = ((CubeAccess) sectionAbove.getSource()).getBlockState(new BlockPos(x, 0, z));
        }
        BlockState state = this.getBlockState(new BlockPos(x, dy, z));

        // note that this BlockPos relies on `cubePos.blockY` returning correct results when the local coord is not inside the cube
        VoxelShape voxelShapeAbove = sectionAbove == null
            ? Shapes.empty()
            : this.getShape(above, new BlockPos(cubePos.blockX(x), cubePos.blockY(dy + 1), cubePos.blockZ(z)), Direction.DOWN);
        VoxelShape voxelShape = this.getShape(state, new BlockPos(cubePos.blockX(x), cubePos.blockY(dy), cubePos.blockZ(z)), Direction.UP);

        while (dy >= 0) {
            int lightBlock = state.getLightBlock(this, new BlockPos(cubePos.blockX(x), cubePos.blockY(dy), cubePos.blockZ(z)));
            if (lightBlock > 0 || Shapes.faceShapeOccludes(voxelShapeAbove, voxelShape)) {
                int minY = this.cubePos.getY() * CubeAccess.DIAMETER_IN_BLOCKS;
                maxY = minY + dy;
                break;
            }
            dy--;
            if (dy >= 0) {
                above = state;
                state = this.getBlockState(new BlockPos(x, dy, z));
                voxelShapeAbove = this.getShape(above, new BlockPos(cubePos.blockX(x), cubePos.blockY(dy + 1), cubePos.blockZ(z)), Direction.DOWN);
                voxelShape = this.getShape(state, new BlockPos(cubePos.blockX(x), cubePos.blockY(dy), cubePos.blockZ(z)), Direction.UP);
            }
        }
        return maxY;
    }

    protected VoxelShape getShape(BlockState blockState, BlockPos pos, Direction facing) {
        return blockState.canOcclude() && blockState.useShapeForLightOcclusion() ? blockState.getFaceOcclusionShape(this, pos, facing) : Shapes.empty();
    }

    @Override public int getSourceY() {
        return this.cubePos.getY();
    }

    @Override public int getCubeLocalHeight(Heightmap.Types type, int x, int z) {
        SurfaceTrackerLeaf[] leaves = getHeightmapSections(type);
        int xSection = blockToCubeLocalSection(x);
        int zSection = blockToCubeLocalSection(z);

        int idx = xSection + zSection * DIAMETER_IN_SECTIONS;

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

    @Override public BlockState getBlockState(BlockPos pos) {
        int index = Coords.blockToIndex(pos.getX(), pos.getY(), pos.getZ());
        LevelChunkSection section = this.sections[index];
        return section.hasOnlyAir() ? EMPTY_BLOCK : section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    /*****ChunkPrimer Overrides*****/

    //@Override
    public ShortList[] getPackedLights() {
        ShortList[] shortLists = new ShortList[SECTION_COUNT];

        for (BlockPos light : this.lights) {
            ChunkAccess.getOrCreateOffsetList(shortLists, Coords.blockToIndex(light)).add(ProtoChunk.packOffsetCoordinates(light));
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
            ChunkAccess.getOrCreateOffsetList(this.getPostProcessing(), Coords.blockToIndex(pos.getX(), pos.getY(), pos.getZ())).add(ProtoChunk.packOffsetCoordinates(pos));
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

    @Override public WorldStyle worldStyle() {
        return worldStyle;
    }

    @Override public boolean isCubic() {
        return isCubic;
    }

    @Override public boolean generates2DChunks() {
        return generates2DChunks;
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

        public FakeSectionCount(LevelHeightAccessor levelHeightAccessor, int sectionCount) {
            this(levelHeightAccessor.getHeight(), levelHeightAccessor.getMinBuildHeight(), sectionCount, ((CubicLevelHeightAccessor) levelHeightAccessor).isCubic(),
                ((CubicLevelHeightAccessor) levelHeightAccessor).generates2DChunks(), ((CubicLevelHeightAccessor) levelHeightAccessor).worldStyle());
        }

        private FakeSectionCount(int height, int minHeight, int sectionCount, boolean isCubic, boolean generates2DChunks, WorldStyle worldStyle) {
            this.height = height;
            this.minHeight = minHeight;
            this.fakeSectionCount = sectionCount;
            this.isCubic = isCubic;
            this.generates2DChunks = generates2DChunks;
            this.worldStyle = worldStyle;
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

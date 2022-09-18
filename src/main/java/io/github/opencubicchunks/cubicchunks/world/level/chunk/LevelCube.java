package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import static io.github.opencubicchunks.cc_core.api.CubicConstants.DIAMETER_IN_BLOCKS;
import static io.github.opencubicchunks.cc_core.api.CubicConstants.DIAMETER_IN_SECTIONS;
import static io.github.opencubicchunks.cc_core.utils.Coords.blockToCubeLocalSection;
import static io.github.opencubicchunks.cc_core.utils.Coords.blockToIndex;
import static io.github.opencubicchunks.cc_core.utils.Coords.blockToLocal;
import static io.github.opencubicchunks.cc_core.utils.Coords.indexToX;
import static io.github.opencubicchunks.cc_core.utils.Coords.indexToY;
import static io.github.opencubicchunks.cc_core.utils.Coords.indexToZ;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.MathUtil;
import io.github.opencubicchunks.cc_core.world.ColumnCubeMapGetter;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cc_core.world.heightmap.HeightmapStorage;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerNode;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelTicks;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.world.server.CubicServerLevel;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LevelCube extends CubeAccess implements CubicLevelHeightAccessor {
    private static final TickingBlockEntity NULL_TICKER = new TickingBlockEntity() {
        public void tick() {
        }

        public boolean isRemoved() {
            return true;
        }

        public BlockPos getPos() {
            return BlockPos.ZERO;
        }

        public String getType() {
            return "<null>";
        }
    };
    private static final Logger LOGGER = LogManager.getLogger(LevelCube.class);

    private LevelChunkTicks<Block> blockTicks;
    private LevelChunkTicks<Fluid> fluidTicks;

    private final Map<BlockPos, RebindableTickingBlockEntityWrapper> tickersInLevel = new HashMap<>();
    private final ClassInstanceMultiMap<Entity>[] entityLists;
    private final Level level;

    private final SurfaceTrackerLeaf[] lightHeightmaps = new SurfaceTrackerLeaf[DIAMETER_IN_SECTIONS * DIAMETER_IN_SECTIONS];

    private boolean loaded = false;

    @Nullable private Consumer<LevelCube> postLoad;
    @Nullable private Supplier<ChunkHolder.FullChunkStatus> fullStatus;

    private final boolean isCubic;
    private final boolean generates2DChunks;
    private final WorldStyle worldStyle;

    public LevelCube(Level level, CubePos cubePos) {
        this(level, cubePos, UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, null, null, null);
    }

    public LevelCube(Level level, CubePos cubePos, UpgradeData upgradeData, LevelChunkTicks<Block> blockTicks, LevelChunkTicks<Fluid> fluidTicks, long inhabitedTime,
                     @Nullable LevelChunkSection[] sections, @Nullable BlendingData blendingData, @Nullable Consumer<LevelCube> postLoad) {
        super(
            cubePos,
            upgradeData,
            new ProtoCube.FakeSectionCount(cubePos.getY(), level, CubicConstants.SECTION_COUNT),
            level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY),
            inhabitedTime,
            sections,
            blendingData
        );

        this.level = level;
        this.blockTicks = blockTicks;
        this.fluidTicks = fluidTicks;

        this.setUnsaved(true); //In 1.17.1 the field declaration for dirty in LevelCube was (boolean dirty = true) with a //TODO: change to false


        //noinspection unchecked
        this.entityLists = new ClassInstanceMultiMap[CubicConstants.SECTION_COUNT];
        for (int i = 0; i < this.entityLists.length; ++i) {
            this.entityLists[i] = new ClassInstanceMultiMap<>(Entity.class);
        }

        this.postLoad = postLoad;

        isCubic = ((CubicLevelHeightAccessor) level).isCubic();
        generates2DChunks = ((CubicLevelHeightAccessor) level).generates2DChunks();
        worldStyle = ((CubicLevelHeightAccessor) level).worldStyle();
    }

    public LevelCube(Level level, ProtoCube protoCube, @Nullable Consumer<LevelCube> postLoad) {
        //TODO: reimplement upgrade data
//        this(level, cubePrimer.getCubePos(), cubePrimer.getCubeBiomes(), cubePrimer.getUpgradeData(), cubePrimer.getBlocksToBeTicked(),
//            cubePrimer.getFluidsToBeTicked(), cubePrimer.getInhabitedTime(), cubePrimer.getSections(), (Consumer<BigCube>)null);
        this(level, protoCube.getCubePos(), null, protoCube.unpackBlockTicks(),
            protoCube.unpackFluidTicks(), protoCube.getInhabitedTime(), protoCube.getSections(), protoCube.getBlendingData(), postLoad);

        assert !Arrays.stream(protoCube.columns).allMatch(column -> column.getStatus().isOrAfter(ChunkStatus.FULL)) : "Load order broken!";

        for (BlockEntity blockEntity : protoCube.getBlockEntities().values()) {
            this.setBlockEntity(blockEntity);
        }

        this.pendingBlockEntities.putAll(protoCube.getBlockEntityNbts());

        for (int i = 0; i < protoCube.getPostProcessing().length; ++i) {
            this.postProcessing[i] = protoCube.getPostProcessing()[i];
        }

        this.setAllStarts(protoCube.getAllStarts());
        this.setAllReferences(protoCube.getAllReferences());

        // Add only the required heightmaps after ChunkStatus.FULL
        protoCube.getCubeHeightmaps().forEach(((type, leaf) -> {
            if (ChunkStatus.FULL.heightmapsAfter().contains(type)) {
                this.cubeHeightmaps.put(type, leaf);
            }
        }));

        SurfaceTrackerLeaf[] protoCubeLightHeightmaps = protoCube.getLightHeightmaps();
        SectionPos cubeMinSection = this.cubePos.asSectionPos();
        for (int localZ = 0; localZ < DIAMETER_IN_SECTIONS; localZ++) {
            for (int localX = 0; localX < DIAMETER_IN_SECTIONS; localX++) {
                int i = localX + localZ * DIAMETER_IN_SECTIONS;

                this.lightHeightmaps[i] = protoCubeLightHeightmaps[i];
                if (this.lightHeightmaps[i] == null) {
                    System.out.println("Got a null light heightmap while upgrading from CubePrimer at " + this.cubePos);
                } else {
                    this.lightHeightmaps[i].loadSource(cubeMinSection.x() + localX, cubeMinSection.z() + localZ, ((CubicServerLevel) this.level).getHeightmapStorage(), this);
                }
            }
        }
        this.setLightCorrect(protoCube.isLightCorrect());

        this.setUnsaved(true);
    }

    public void registerTicks(ServerLevel serverLevel) {
        ((CubicLevelTicks<Block>) serverLevel.getBlockTicks()).addContainer(this.cubePos, this.blockTicks);
        ((CubicLevelTicks<Fluid>) serverLevel.getFluidTicks()).addContainer(this.cubePos, this.fluidTicks);
    }

    public void unregisterTicks(ServerLevel serverLevel) {
        ((CubicLevelTicks<Block>) serverLevel.getBlockTicks()).removeContainer(this.cubePos);
        ((CubicLevelTicks<Fluid>) serverLevel.getFluidTicks()).removeContainer(this.cubePos);
    }

    @Override public void sectionLoaded(@Nonnull SurfaceTrackerLeaf surfaceTrackerLeaf, int localSectionX, int localSectionZ) {
        int idx = localSectionX + localSectionZ * DIAMETER_IN_SECTIONS;

        if (surfaceTrackerLeaf.getRawType() == -1) { //light
            this.lightHeightmaps[idx] = surfaceTrackerLeaf;
        } else { // normal heightmap
            this.getCubeHeightmaps().computeIfAbsent(Heightmap.Types.values()[surfaceTrackerLeaf.getRawType()],
                type -> new SurfaceTrackerLeaf[DIAMETER_IN_SECTIONS * DIAMETER_IN_SECTIONS]
            )[idx] = surfaceTrackerLeaf;
        }
    }

    @Override
    public void unloadSource(@Nonnull HeightmapStorage storage) {
        SectionPos cubeMinSection = this.cubePos.asSectionPos();
        for (SurfaceTrackerLeaf[] heightmapLeaves : this.getCubeHeightmaps().values()) {
            for (int localSectionZ = 0; localSectionZ < DIAMETER_IN_SECTIONS; localSectionZ++) {
                for (int localSectionX = 0; localSectionX < DIAMETER_IN_SECTIONS; localSectionX++) {
                    int i = localSectionX + localSectionZ * DIAMETER_IN_SECTIONS;
                    if (heightmapLeaves[i] != null) {
                        heightmapLeaves[i].sourceUnloaded(cubeMinSection.x() + localSectionX, cubeMinSection.z() + localSectionZ, storage);
                        heightmapLeaves[i] = null;
                    }
                }
            }
        }
        SurfaceTrackerLeaf[] lightHeightmapLeaves = this.lightHeightmaps;
        for (int localSectionZ = 0; localSectionZ < DIAMETER_IN_SECTIONS; localSectionZ++) {
            for (int localSectionX = 0; localSectionX < DIAMETER_IN_SECTIONS; localSectionX++) {
                int i = localSectionX + localSectionZ * DIAMETER_IN_SECTIONS;
                if (lightHeightmapLeaves[i] != null) {
                    lightHeightmapLeaves[i].sourceUnloaded(cubeMinSection.x() + localSectionX, cubeMinSection.z() + localSectionZ, storage);
                    lightHeightmapLeaves[i] = null;
                }
            }
        }
    }

    @Override public int getHighest(int x, int z, byte heightmapType) {
        if (heightmapType == -1) { //light
            return getHighestLight(x, z);
        } else { //normal heightmaps
            int maxY = Integer.MIN_VALUE;
            for (int dy = DIAMETER_IN_BLOCKS - 1; dy >= 0; dy--) {
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

        int dy = DIAMETER_IN_BLOCKS - 1;

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
                int minY = this.cubePos.getY() * DIAMETER_IN_BLOCKS;
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

    @Deprecated @Override public ChunkPos getPos() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override public CubePos getCubePos() {
        return this.cubePos;
    }

    @Override public LevelChunkSection[] getSections() {
        return this.sections;
    }

    @Override public ChunkStatus getStatus() {
        return ChunkStatus.FULL;
    }

    public ChunkHolder.FullChunkStatus getFullStatus() {
        return this.fullStatus == null ? ChunkHolder.FullChunkStatus.BORDER : this.fullStatus.get();
    }

    public void setFullStatus(Supplier<ChunkHolder.FullChunkStatus> supplier) {
        this.fullStatus = supplier;
    }

    //BLOCK
    @Nullable @Override public BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving) {
        return setBlockState(blockToIndex(pos.getX(), pos.getY(), pos.getZ()), pos, state, isMoving);
    }

    @Nullable public BlockState setBlockState(int sectionIndex, BlockPos pos, BlockState newState, boolean isMoving) {
        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;
        LevelChunkSection section = sections[sectionIndex];

        BlockState oldState = section.setBlockState(x, y, z, newState);
        if (oldState == newState) {
            return null;
        }
        Block newBlock = newState.getBlock();
        int localX = blockToLocal(pos.getX());
        int localZ = blockToLocal(pos.getZ());

        if (!this.getCubeHeightmaps().isEmpty()) {
            int xSection = blockToCubeLocalSection(pos.getX());
            int zSection = blockToCubeLocalSection(pos.getZ());

            int idx = xSection + zSection * DIAMETER_IN_SECTIONS;

            IntPredicate isOpaquePredicate = SurfaceTrackerWrapper.opaquePredicateForState(newState);

            this.getCubeHeightmaps().get(Heightmap.Types.MOTION_BLOCKING)[idx].onSetBlock(localX, pos.getY(), localZ, isOpaquePredicate);
            this.getCubeHeightmaps().get(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES)[idx].onSetBlock(localX, pos.getY(), localZ, isOpaquePredicate);
            this.getCubeHeightmaps().get(Heightmap.Types.OCEAN_FLOOR)[idx].onSetBlock(localX, pos.getY(), localZ, isOpaquePredicate);
            this.getCubeHeightmaps().get(Heightmap.Types.WORLD_SURFACE)[idx].onSetBlock(localX, pos.getY(), localZ, isOpaquePredicate);

            this.lightHeightmaps[idx].onSetBlock(localX, pos.getY(), localZ, isOpaquePredicate);
        }

        boolean hadBlockEntity = oldState.hasBlockEntity();
        if (!this.level.isClientSide) {
            oldState.onRemove(this.level, pos, newState, isMoving);
        } else if (!oldState.is(newBlock) && hadBlockEntity) {
            this.removeBlockEntity(pos);
        }

        if (section.getBlockState(x, y, z).getBlock() != newBlock) {
            return null;
        }

        if (!this.level.isClientSide) {
            newState.onPlace(this.level, pos, oldState, isMoving);
        }

        if (newState.hasBlockEntity()) {
            BlockEntity blockEntity = this.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
            if (blockEntity == null) {
                blockEntity = ((EntityBlock) newBlock).newBlockEntity(pos, newState);
                if (blockEntity != null) {
                    this.addAndRegisterBlockEntity(blockEntity);
                }
            } else {
                //This is deprecated but, as of 1.18.2, it is still used in the vanilla implementation so ¯\_(ツ)_/¯
                blockEntity.setBlockState(newState);
                this.updateBlockEntityTicker(blockEntity);
            }
        }

        this.setUnsaved(true);
        return oldState;
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        // TODO: crash report generation
        int index = blockToIndex(pos.getX(), pos.getY(), pos.getZ());
        return this.sections[index].hasOnlyAir() ?
            Blocks.AIR.defaultBlockState() :
            this.sections[index].getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    //ENTITY
    @Deprecated @Override public void addEntity(Entity entity) {
        // empty in vanilla too
    }

    public ClassInstanceMultiMap<Entity>[] getEntityLists() {
        return this.entityLists;
    }

    private int getIndexFromEntity(Entity entity) {
        return blockToIndex((int) entity.getX(), (int) entity.getY(), (int) entity.getZ());
    }

    public void removeEntity(Entity entity) {
        this.removeEntityAtIndex(entity, this.getIndexFromEntity(entity));
    }

    public void removeEntityAtIndex(Entity entity, int index) {
        if (index < 0) {
            index = 0;
        }
        if (index >= this.entityLists.length) {
            index = this.entityLists.length - 1;
        }
        this.entityLists[index].remove(entity);
        this.setUnsaved(true);
    }

    @Override public void setBlockEntity(BlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        if (this.getBlockState(pos).hasBlockEntity()) {
            blockEntity.setLevel(this.level);
            blockEntity.clearRemoved();
            BlockEntity old = this.blockEntities.put(pos.immutable(), blockEntity);
            if (old != null && old != blockEntity) {
                old.setRemoved();
            }
        }
    }

    public void addAndRegisterBlockEntity(BlockEntity blockEntity) {
        this.setBlockEntity(blockEntity);
        if (isInLevel()) {
            this.updateBlockEntityTicker(blockEntity);
        }
    }

    public void registerAllBlockEntitiesAfterLevelLoad() {
        this.blockEntities.values().forEach(this::updateBlockEntityTicker);
    }

    public <T extends BlockEntity> void updateBlockEntityTicker(T blockEntity) {
        BlockState blockState = blockEntity.getBlockState();
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> blockEntityTicker = (BlockEntityTicker<T>) blockState.getTicker(this.level, blockEntity.getType());
        if (blockEntityTicker == null) {
            this.removeBlockEntityTicker(blockEntity.getBlockPos());
        } else {
            this.tickersInLevel.compute(blockEntity.getBlockPos(), (blockPos, wrapper) -> {
                TickingBlockEntity tickingBlockEntity = this.createTicker(blockEntity, blockEntityTicker);
                if (wrapper != null) {
                    wrapper.rebind(tickingBlockEntity);
                    return wrapper;
                } else if (this.isInLevel()) {
                    RebindableTickingBlockEntityWrapper newWrapper = new RebindableTickingBlockEntityWrapper(tickingBlockEntity);
                    this.level.addBlockEntityTicker(newWrapper);
                    return newWrapper;
                } else {
                    return null;
                }
            });
        }
    }

    private <T extends BlockEntity> TickingBlockEntity createTicker(T blockEntity, BlockEntityTicker<T> blockEntityTicker) {
        return new BoundTickingBlockEntity<T>(blockEntity, blockEntityTicker);
    }

    // NOTE: this should not be in API
    public void removeBlockEntityTicker(BlockPos blockPos) {
        RebindableTickingBlockEntityWrapper wrapper = this.tickersInLevel.remove(blockPos);
        if (wrapper != null) {
            wrapper.rebind(NULL_TICKER);
        }

    }

    @Override public void removeBlockEntity(BlockPos pos) {
        if (isInLevel()) {
            BlockEntity blockEntity = this.blockEntities.remove(pos);
            if (blockEntity != null) {
                blockEntity.setRemoved();
            }
        }
        this.removeBlockEntityTicker(pos);
    }

    @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) {
        return getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
    }

    @Nullable public BlockEntity getBlockEntity(BlockPos pos, LevelChunk.EntityCreationType creationMode) {
        BlockEntity blockEntity = this.blockEntities.get(pos);
        if (blockEntity == null) {
            CompoundTag nbt = this.pendingBlockEntities.remove(pos);
            if (nbt != null) {
                BlockEntity pendingPromoted = this.promotePendingBlockEntity(pos, nbt);
                if (pendingPromoted != null) {
                    return pendingPromoted;
                }
            }
        }

        if (blockEntity == null) {
            if (creationMode == LevelChunk.EntityCreationType.IMMEDIATE) {
                blockEntity = this.createNewBlockEntity(pos);
                if (blockEntity != null) {
                    this.addAndRegisterBlockEntity(blockEntity);
                }
            }
        } else if (blockEntity.isRemoved()) {
            blockEntities.remove(pos);
            return null;
        }
        return blockEntity;
    }

    @Nullable private BlockEntity createNewBlockEntity(BlockPos pos) {
        BlockState state = this.getBlockState(pos);
        return !state.hasBlockEntity() ? null : ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
    }

    @Nullable private BlockEntity promotePendingBlockEntity(BlockPos pos, CompoundTag compound) {
        BlockEntity blockEntity;
        BlockState state = this.getBlockState(pos);
        if ("DUMMY".equals(compound.getString("id"))) {
            if (state.hasBlockEntity()) {
                blockEntity = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
            } else {
                blockEntity = null;
                CubicChunks.LOGGER.warn("Tried to load a DUMMY block entity @ {} but found not block entity block {} at location", pos, this.getBlockState(pos));
            }
        } else {
            blockEntity = BlockEntity.loadStatic(pos, state, compound);
        }

        if (blockEntity != null) {
            blockEntity.setLevel(this.level);
            this.addAndRegisterBlockEntity(blockEntity);
        } else {
            CubicChunks.LOGGER.warn("Tried to load a block entity for block {} but failed at location {}", state, pos);
        }

        return blockEntity;
    }

    public Map<BlockPos, BlockEntity> getTileEntityMap() {
        return blockEntities;
    }

    public Map<BlockPos, CompoundTag> getPendingBlockEntities() {
        return this.pendingBlockEntities;
    }

    @Override public Set<BlockPos> getBlockEntitiesPos() {
        Set<BlockPos> set = Sets.newHashSet(this.pendingBlockEntities.keySet());
        set.addAll(this.blockEntities.keySet());
        return set;
    }

    @Nullable @Override public CompoundTag getBlockEntityNbtForSaving(BlockPos pos) {
        BlockEntity blockEntity = this.getBlockEntity(pos);
        if (blockEntity != null && !blockEntity.isRemoved()) {
            CompoundTag tag = blockEntity.saveWithFullMetadata();
            tag.putBoolean("keepPacked", false);
            return tag;
        } else {
            CompoundTag tag = this.pendingBlockEntities.get(pos);
            if (tag != null) {
                tag = tag.copy();
                tag.putBoolean("keepPacked", true);
            }
            return tag;
        }
    }

    public void postProcessGeneration() {
        for (int i = 0; i < this.postProcessing.length; ++i) {
            if (this.postProcessing[i] != null) {
                for (Short sectionRel : this.postProcessing[i]) {
                    BlockPos blockPos = ProtoCube.unpackToWorld(sectionRel, this.getSectionYFromSectionIndex(i), this.cubePos);
                    BlockState blockState = this.getBlockState(blockPos);
                    FluidState fluidState = blockState.getFluidState();

                    if (!fluidState.isEmpty()) {
                        fluidState.tick(this.level, blockPos);
                    }

                    if (!(blockState.getBlock() instanceof LiquidBlock)) {
                        BlockState blockState2 = Block.updateFromNeighbourShapes(blockState, this.level, blockPos);
                        this.level.setBlock(blockPos, blockState2, 20);
                    }
                }
                this.postProcessing[i].clear();
            }
        }


        for (BlockPos blockPos : ImmutableList.copyOf(this.pendingBlockEntities.keySet())) {
            this.getBlockEntity(blockPos);
        }

        this.pendingBlockEntities.clear();
//        this.upgradeData.upgrade(this); //TODO: DFU
    }

    @Override public Stream<BlockPos> getLights() {
        return StreamSupport.stream(
                BlockPos.betweenClosed(
                    this.cubePos.minCubeX(), this.cubePos.minCubeY(), this.cubePos.minCubeZ(),
                    this.cubePos.maxCubeX(), this.cubePos.maxCubeY(), this.cubePos.maxCubeZ()
                ).spliterator(), false)
            .filter((blockPos) -> this.getBlockState(blockPos).getLightEmission() != 0);
    }

    //MISC
    public boolean isInLevel() {
        return this.loaded || this.level.isClientSide();
    }

    private boolean isTicking(BlockPos blockPos) {
        return (this.level.isClientSide() || this.getFullStatus().isOrAfter(ChunkHolder.FullChunkStatus.TICKING)) && this.level.getWorldBorder().isWithinBounds(blockPos);
    }

    public boolean isEmptyCube() {
        for (LevelChunkSection section : this.sections) {
            if (!section.hasOnlyAir()) {
                return false;
            }
        }
        return true;
    }

    public int getSize() {
        int size = MathUtil.ceilDiv(sections.length, Byte.SIZE); // exists flags
        for (LevelChunkSection section : this.sections) {
            if (section != null) {
                size += section.getSerializedSize();
            }
        }
        return size;
    }

    public void write(FriendlyByteBuf buf) {
        for (LevelChunkSection section : sections) {
            section.write(buf);
        }
    }

    public void read(FriendlyByteBuf readBuffer, CompoundTag tag, BiConsumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput, CubePos> consumer) {
        //Clear all block entities
        this.blockEntities.values().forEach(this::onBlockEntityRemove);
        this.blockEntities.clear();
        this.tickersInLevel.values().forEach(tickingWrapper -> {
            tickingWrapper.rebind(LevelChunk.NULL_TICKER);
        });
        this.tickersInLevel.clear();

        for (LevelChunkSection section : sections) {
            section.read(readBuffer);
        }

        //Load block entities
        consumer.accept((blockPos, blockEntityType, compundTagX) -> {
            BlockEntity blockEntity = this.getBlockEntity(blockPos, LevelChunk.EntityCreationType.IMMEDIATE);
            if (blockEntity != null && compundTagX != null && blockEntity.getType() == blockEntityType) {
                blockEntity.load(compundTagX);
            }
        }, this.cubePos);
    }

    private void onBlockEntityRemove(BlockEntity blockEntity) {
        blockEntity.setRemoved();
        this.tickersInLevel.remove(blockEntity.getBlockPos());
    }

    private void readSection(int sectionIdx, int sectionY, FriendlyByteBuf byteBuf, CompoundTag nbt/*, boolean sectionExists*/) {
        LevelChunkSection section = this.sections[sectionIdx];

        //if (sectionExists) {
        section.read(byteBuf);
        //}

        for (Heightmap.Types type : Heightmap.Types.values()) {
            String typeId = type.getSerializationKey();
            if (nbt.contains(typeId, 12)) { // NBT TAG_LONG_ARRAY
                this.setHeightmap(type, nbt.getLongArray(typeId));
            }
        }
    }

    @Deprecated
    public SectionPos getSectionPosition(int index) {
        int xPos = indexToX(index);
        int yPos = indexToY(index);
        int zPos = indexToZ(index);

        SectionPos sectionPos = this.cubePos.asSectionPos();
        return SectionPos.of(xPos + sectionPos.getX(), yPos + sectionPos.getY(), zPos + sectionPos.getZ());
    }


    public Level getLevel() {
        return level;
    }

    @Override public Collection<Map.Entry<Heightmap.Types, Heightmap>> getHeightmaps() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override public void setHeightmap(Heightmap.Types type, long[] data) {
    }

    @Override public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override public int getCubeLocalHeight(Heightmap.Types type, int x, int z) {
        SurfaceTrackerLeaf[] surfaceTrackerSections = this.getCubeHeightmaps().get(type);
        if (surfaceTrackerSections == null) {
            throw new IllegalStateException("Trying to access heightmap of type " + type + " for cube " + cubePos + " before it's loaded!");
        }
        int xSection = blockToCubeLocalSection(x);
        int zSection = blockToCubeLocalSection(z);

        int idx = xSection + zSection * DIAMETER_IN_SECTIONS;

        SurfaceTrackerLeaf surfaceTrackerSection = surfaceTrackerSections[idx];
        return surfaceTrackerSection.getHeight(blockToLocal(x), blockToLocal(z));
    }

    @Override public int getHeight(Heightmap.Types type, int x, int z) { //TODO: Use heightmap sections from column instead.
        SurfaceTrackerLeaf[] surfaceTrackerSections = this.getCubeHeightmaps().get(type);
        if (surfaceTrackerSections == null) {
            throw new IllegalStateException("Trying to access heightmap of type " + type + " for cube " + cubePos + " before it's loaded!");
        }
        int xSection = blockToCubeLocalSection(x);
        int zSection = blockToCubeLocalSection(z);

        int idx = xSection + zSection * DIAMETER_IN_SECTIONS;

        SurfaceTrackerNode surfaceTrackerSection = surfaceTrackerSections[idx];

        while (surfaceTrackerSection.getParent() != null) {
            surfaceTrackerSection = surfaceTrackerSection.getParent();
        }
        return surfaceTrackerSection.getHeight(blockToLocal(x), blockToLocal(z));
    }

    @Override public ShortList[] getPostProcessing() {
        return this.postProcessing;
    }

    @Override public TickContainerAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override public TickContainerAccess<Fluid> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public TicksToSave getTicksForSerialization() {
        return new TicksToSave(this.blockTicks, this.fluidTicks);
    }

    @Override public UpgradeData getUpgradeData() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        return this.getFluidState(pos.getX(), pos.getY(), pos.getZ());
    }

    public FluidState getFluidState(int x, int y, int z) {
        try {
            int index = blockToIndex(x, y, z);
            if (!this.sections[index].hasOnlyAir()) {
                return this.sections[index].getFluidState(x & 15, y & 15, z & 15);
            }
            return Fluids.EMPTY.defaultFluidState();
        } catch (Throwable var7) {
            CrashReport crashReport = CrashReport.forThrowable(var7, "Getting fluid state");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Block being got");
            crashReportCategory.setDetail("Location", () -> {
                return CrashReportCategory.formatLocation(this, x, y, z);
            });
            throw new ReportedException(crashReport);
        }
    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    public boolean getLoaded() {
        return this.loaded;
    }

    public void unpackTicks(long l) {
        this.blockTicks.unpack(l);
        this.fluidTicks.unpack(l);
    }

    public void postLoad() {
        if (this.postLoad != null) {
            this.postLoad.accept(this);
            this.postLoad = null;
        }
        // TODO heightmap stuff should probably be elsewhere rather than here.
        ChunkPos pos = this.cubePos.asChunkPos();
        HeightmapStorage storage = ((CubicServerLevel) this.level).getHeightmapStorage();
        for (int x = 0; x < DIAMETER_IN_SECTIONS; x++) {
            for (int z = 0; z < DIAMETER_IN_SECTIONS; z++) {

                // This force-loads the column, but it shouldn't matter if column-cube load order is working properly.
                LevelChunk chunk = this.level.getChunk(pos.x + x, pos.z + z);
                ((ColumnCubeMapGetter) chunk).getCubeMap().markLoaded(this.cubePos.getY());
                for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
                    Heightmap heightmap = entry.getValue();
                    SurfaceTrackerWrapper tracker = (SurfaceTrackerWrapper) heightmap;
                    tracker.loadCube(storage, this);
                }

                // TODO probably don't want to do this if the cube was already loaded as a CubePrimer
                ((LightHeightmapGetter) chunk).getServerLightHeightmap().loadCube(storage, this);
            }
        }
    }

    public void invalidateAllBlockEntities() {
        this.blockEntities.values().forEach(this::onBlockEntityRemove);
    }

    @Override
    public void markPosForPostprocessing(BlockPos blockPos) {
        LogManager.getLogger().warn("Trying to mark a block for PostProcessing @ {}, but this operation is not supported.", blockPos);
    }

    @Override public int getHeight() {
        return level.getHeight();
    }

    @Override public int getMinBuildHeight() {
        return level.getMinBuildHeight();
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

    public static class RebindableTickingBlockEntityWrapper implements TickingBlockEntity {
        private TickingBlockEntity ticker;

        private RebindableTickingBlockEntityWrapper(TickingBlockEntity tickingBlockEntity) {
            this.ticker = tickingBlockEntity;
        }

        private void rebind(TickingBlockEntity tickingBlockEntity) {
            this.ticker = tickingBlockEntity;
        }

        public void tick() {
            this.ticker.tick();
        }

        public boolean isRemoved() {
            return this.ticker.isRemoved();
        }

        public BlockPos getPos() {
            return this.ticker.getPos();
        }

        public String getType() {
            return this.ticker.getType();
        }

        public String toString() {
            return this.ticker.toString() + " <wrapped>";
        }
    }

    public class BoundTickingBlockEntity<T extends BlockEntity> implements TickingBlockEntity {
        private final T blockEntity;
        private final BlockEntityTicker<T> ticker;
        private boolean loggedInvalidBlockState;

        private BoundTickingBlockEntity(T blockEntity, BlockEntityTicker<T> blockEntityTicker) {
            this.blockEntity = blockEntity;
            this.ticker = blockEntityTicker;
        }

        public void tick() {
            if (!this.blockEntity.isRemoved() && this.blockEntity.hasLevel()) {
                BlockPos blockPos = this.blockEntity.getBlockPos();
                if (LevelCube.this.isTicking(blockPos)) {
                    try {
                        ProfilerFiller profilerFiller = LevelCube.this.level.getProfiler();
                        profilerFiller.push(this::getType);
                        BlockState blockState = LevelCube.this.getBlockState(blockPos);
                        if (this.blockEntity.getType().isValid(blockState)) {
                            this.ticker.tick(LevelCube.this.level, this.blockEntity.getBlockPos(), blockState, this.blockEntity);
                            this.loggedInvalidBlockState = false;
                        } else if (!this.loggedInvalidBlockState) {
                            this.loggedInvalidBlockState = true;
                            LevelCube.LOGGER.warn("Block entity {} @ {} state {} invalid for ticking:", this::getType, this::getPos, () -> blockState);
                        }

                        profilerFiller.pop();
                    } catch (Throwable var5) {
                        CrashReport crashReport = CrashReport.forThrowable(var5, "Ticking block entity");
                        CrashReportCategory crashReportCategory = crashReport.addCategory("Block entity being ticked");
                        this.blockEntity.fillCrashReportCategory(crashReportCategory);
                        throw new ReportedException(crashReport);
                    }
                }
            }

        }

        public boolean isRemoved() {
            return this.blockEntity.isRemoved();
        }

        public BlockPos getPos() {
            return this.blockEntity.getBlockPos();
        }

        public String getType() {
            return BlockEntityType.getKey(this.blockEntity.getType()).toString();
        }

        public String toString() {
            return "Level ticker for " + this.getType() + "@" + this.getPos();
        }
    }
}
package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.config.EarlyConfig;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.heightmap.HeightmapSource;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.FeatureAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.apache.logging.log4j.LogManager;

//TODO: Change most of the places where sections are accesses since they are done with the y coordinate and
//now they must be done by all three
public abstract class CubeAccess extends ChunkAccess implements BlockGetter, FeatureAccess, HeightmapSource {

    public static final int SECTION_DIAMETER = 16;
    public static final int DIAMETER_IN_SECTIONS = EarlyConfig.getDiameterInSections();
    public static final int SECTION_COUNT = DIAMETER_IN_SECTIONS * DIAMETER_IN_SECTIONS * DIAMETER_IN_SECTIONS;
    public static final int CHUNK_COUNT = DIAMETER_IN_SECTIONS * DIAMETER_IN_SECTIONS;
    public static final int DIAMETER_IN_BLOCKS = SECTION_DIAMETER * DIAMETER_IN_SECTIONS;
    public static final int BLOCK_COUNT = DIAMETER_IN_BLOCKS * DIAMETER_IN_BLOCKS * DIAMETER_IN_BLOCKS;
    public static final int BLOCK_COLUMNS_PER_SECTION = SECTION_DIAMETER * SECTION_DIAMETER;
    public static final int SIZE_BITS = (int) Math.round(Math.log(DIAMETER_IN_BLOCKS) / Math.log(2.0D));

    //NOTE: Commented out fields are because these fields are now in ChunkAccess because it is no longer an interface but an abstract class
    //protected final ShortList[] postProcessing;
    //private boolean dirty; /*Same as unsaved*/
    //private volatile boolean lightCorrect;
    protected final CubePos cubePos;
    //private long inhabitedTime;

    //TODO: Figure out what to do with carverBiome and noiseChunk

    //private final UpgradeData upgradeData;
    //protected BlendingData blendingData; //TODO: Do cubes really need this?

    private final Map<Heightmap.Types, SurfaceTrackerLeaf[]> cubeHeightmaps; //TODO: ChunkAccess now has it's own heightmaps but they are of class Heightmap

    //private final Map<ConfiguredStructureFeature<?, ?>, StructureStart> structureStarts = Maps.newHashMap();
    //private final Map<ConfiguredStructureFeature<?, ?>, LongSet> structuresReferences = Maps.newHashMap();

    //protected final Map<BlockPos, CompoundTag> pendingBlockEntities = Maps.newHashMap();
    //protected final Map<BlockPos, BlockEntity> blockEntities = Maps.newHashMap();

    //protected final LevelHeightAccessor heightAccessor; //TODO: Maybe just use CubicLevelHeightAccessor?

    //protected final LevelChunkSection[] sections = new LevelChunkSection[SECTION_COUNT];

    public CubeAccess(CubePos pos, UpgradeData upgradeData, LevelHeightAccessor heightAccessor, Registry<Biome> biomeRegistry, long inhabitedTime,
                       @Nullable LevelChunkSection[] sections, @Nullable BlendingData blendingData) {
        super(
            new ImposterChunkPos(pos), //TODO: Maybe there is a better way to handle the fact that we must now pass a ChunkPos
            upgradeData,
            heightAccessor,
            biomeRegistry,
            inhabitedTime,
            sections,
            blendingData
        );

        this.cubePos = pos;
        this.cubeHeightmaps = Maps.newEnumMap(Heightmap.Types.class);
    }

    public CubePos getCubePos() {
        return cubePos;
    }
    public LevelChunkSection[] getCubeSections() {
        return sections;
    }

    public abstract ChunkStatus getCubeStatus();

    public void setFeatureBlocks(BlockPos pos, BlockState state) {
        setBlock(pos, state, false);
    }

    //BLOCK
    // this can't be setBlockState because the implementations also implement IChunk which already has setBlockState and this breaks obfuscation
    @Nullable
    public abstract BlockState setBlock(BlockPos pos, BlockState state, boolean isMoving);

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return getBlockState(Coords.localX(pos), Coords.localY(pos), Coords.localZ(pos));
    }
    //TODO: remove this getBlockState from IBigCube to match IChunk
    public abstract BlockState getBlockState(int x, int y, int z);

    //TILE ENTITY
    // can't be add/removeTileEntity due to obfuscation issues with IChunk
    public void setCubeBlockEntity(CompoundTag nbt) {
        LogManager.getLogger().warn("Trying to set a BlockEntity, but this operation is not supported.");
    }
    public abstract void setCubeBlockEntity(BlockEntity tileEntity);
    public abstract void removeCubeBlockEntity(BlockPos pos);

    public abstract Set<BlockPos> getCubeBlockEntitiesPos();

    @Nullable
    public abstract CompoundTag getCubeBlockEntityNbtForSaving(BlockPos pos);

    @Nullable
    public CompoundTag getCubeBlockEntityNbt(BlockPos pos) {
        return this.pendingBlockEntities.get(pos);
    }

    public Map<ConfiguredStructureFeature<?, ?>, StructureStart> getAllCubeStructureStarts() {
        return this.getAllStarts();
    }

    //LIGHTING
    //can't be set/hasLight due to obfuscation issues with IChunk
    public abstract boolean hasCubeLight();
    public abstract void setCubeLight(boolean lightCorrectIn);

    public abstract Stream<BlockPos> getCubeLights();

    //MISC
    // can't be isModified due to obfuscation issues with IChunk
    //TODO: Maybe remove these and other methods that just delegate
    public void setDirty(boolean modified) {
        this.setUnsaved(modified);
    }

    public boolean isDirty() {
        return this.isUnsaved();
    }

    //TODO: remove isEmptyCube from IBigCube to match IChunk
    public boolean isEmptyCube() {
        for (LevelChunkSection section : sections) {
            if (!section.hasOnlyAir()) {
                return false;
            }
        }

        return true;
    }

    public void setCubeInhabitedTime(long newCubeInhabitedTime) {
        this.setInhabitedTime(newCubeInhabitedTime);
    }
    public long getCubeInhabitedTime() {
        return this.getInhabitedTime();
    }

    public int getCubeLocalHeight(Heightmap.Types type, int x, int z) {
        SurfaceTrackerLeaf[]  leaves = this.cubeHeightmaps.get(type);
        if (leaves == null) {
            throw new IllegalStateException("Trying to access heightmap of type " + type + " for cube " + this.cubePos + " before it's loaded!");
        }

        int xSection = Coords.blockToCubeLocalSection(x);
        int zSection = Coords.blockToCubeLocalSection(z);

        int index = xSection + zSection * DIAMETER_IN_SECTIONS;

        SurfaceTrackerLeaf leaf = leaves[index];

        return leaf.getHeight(Coords.blockToLocal(x), Coords.blockToLocal(z));
    }

    public Map<Heightmap.Types, SurfaceTrackerLeaf[]> getCubeHeightmaps() {
        return cubeHeightmaps;
    }

    //TODO: Implement other methods
    public static class CubeLevelHeightAccessor extends CubeSerializer.CubeBoundsLevelHeightAccessor {
        public CubeLevelHeightAccessor(int height, int minBuildHeight, CubicLevelHeightAccessor accessor) {
            super(height, minBuildHeight, accessor);
        }

        @Override
        public int getSectionsCount() {
            return CubeAccess.SECTION_COUNT;
        }

        @Override
        public int getMinSection() {
            return super.getMinSection();
        }

        @Override
        public int getMaxSection() {
            return super.getMaxSection();
        }

        @Override
        public int getSectionIndex(int i) {
            return super.getSectionIndex(i);
        }

        @Override
        public int getSectionIndexFromSectionY(int i) {
            return super.getSectionIndexFromSectionY(i);
        }

        @Override
        public int getSectionYFromSectionIndex(int i) {
            return Coords.indexToSectionY(i);
        }
    }
}
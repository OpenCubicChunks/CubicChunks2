package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.common.collect.Maps;
import io.github.opencubicchunks.cubicchunks.config.EarlyConfig;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.world.level.CubePos;
import io.github.opencubicchunks.cubicchunks.world.level.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapNode;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cubicchunks.world.storage.CubeSerializer;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
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
            null, // parameter is unused in the constructor. We want it to crash if used anyway, so pass null here
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
        return new HashMap<>(); //TODO: Not silently fail
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

    @Deprecated @Override
    public void setUnsaved(boolean modified) {
        super.setUnsaved(modified);
    }

    @Deprecated @Override
    public boolean isUnsaved() {
        return super.isUnsaved();
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
        SurfaceTrackerLeaf[] leaves = this.cubeHeightmaps.get(type);
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

    //Fix usages of getSectionIndex()
    @Override
    public int getSectionIndex(int i) {
        throw new UnsupportedOperationException("Sections are got from (x, y, z) in cube not just y");
    }

    @Override
    public boolean isYSpaceEmpty(int minY, int maxY) {
        if (minY < this.getMinBuildHeight()) {
            minY = this.getMinBuildHeight();
        }

        if (maxY > this.getMaxBuildHeight()) {
            maxY = this.getMaxBuildHeight();
        }

        for (int y = minY; y <= maxY; y += 16) {
            for (int x = 0; x < DIAMETER_IN_BLOCKS; x += LevelChunkSection.SECTION_WIDTH) {
                for (int z = 0; z < DIAMETER_IN_BLOCKS; z += LevelChunkSection.SECTION_WIDTH) {
                    LevelChunkSection section = this.getSection(Coords.blockToIndex(x, y, z));

                    if (!section.hasOnlyAir()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    //TODO: Maybe use mixin to prevent method duplication
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        try {
            int clampedX = Mth.clamp(x, cubePos.minCubeX(), cubePos.maxCubeX());
            int clampedY = Mth.clamp(y, cubePos.minCubeY(), cubePos.maxCubeY());
            int clampedZ = Mth.clamp(z, cubePos.minCubeZ(), cubePos.maxCubeZ());

            return this.sections[Coords.blockToIndex(clampedX, clampedY, clampedZ)].getNoiseBiome(clampedX & 0x03, clampedY & 0x03, clampedZ & 0x03);
        } catch (Throwable e) {
            CrashReport crashReport = CrashReport.forThrowable(e, "Getting biome");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Biome being got");
            crashReportCategory.setDetail("Location", () -> CrashReportCategory.formatLocation(this, x, y, z));
            throw new ReportedException(crashReport);
        }
    }

    @Override
    public synchronized void fillBiomesFromNoise(BiomeResolver biomeResolver, Climate.Sampler sampler) {
        LevelHeightAccessor heightAccessor = this.getHeightAccessorForGeneration();

        for (int sectionX = 0; sectionX < DIAMETER_IN_SECTIONS; sectionX++) {
            for (int sectionY = 0; sectionY < DIAMETER_IN_SECTIONS; sectionY++) {
                for (int sectionZ = 0; sectionZ < DIAMETER_IN_SECTIONS; sectionZ++) {
                    int minXQuart = QuartPos.fromBlock(Coords.sectionToMinBlock(sectionX));
                    int minZQuart = QuartPos.fromBlock(Coords.sectionToMinBlock(sectionZ));

                    LevelChunkSection section = this.sections[Coords.sectionToIndex(sectionX, sectionY, sectionZ)];
                    section.fillBiomesFromNoise(biomeResolver, sampler, minXQuart, minZQuart);
                }
            }
        }
    }


}
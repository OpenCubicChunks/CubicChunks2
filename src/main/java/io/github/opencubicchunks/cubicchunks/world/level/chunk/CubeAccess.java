package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import static io.github.opencubicchunks.cc_core.api.CubicConstants.DIAMETER_IN_BLOCKS;
import static io.github.opencubicchunks.cc_core.api.CubicConstants.DIAMETER_IN_SECTIONS;
import static io.github.opencubicchunks.cc_core.utils.Coords.blockToCubeLocalSection;
import static io.github.opencubicchunks.cc_core.utils.Coords.blockToIndex;
import static io.github.opencubicchunks.cc_core.utils.Coords.blockToLocal;
import static io.github.opencubicchunks.cc_core.utils.Coords.columnToColumnIndex;
import static io.github.opencubicchunks.cc_core.utils.Coords.sectionToIndex;
import static io.github.opencubicchunks.cc_core.utils.Coords.sectionToMinBlock;

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Either;
import io.github.opencubicchunks.cc_core.annotation.UsedFromASM;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.world.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cc_core.world.heightmap.HeightmapSource;
import io.github.opencubicchunks.cc_core.world.heightmap.HeightmapStorage;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerWrapper;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.FeatureAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

//TODO: Change most of the places where sections are accesses since they are done with the y coordinate and
//now they must be done by all three
public abstract class CubeAccess extends ChunkAccess implements BlockGetter, FeatureAccess, HeightmapSource, CubicLevelHeightAccessor {
    protected final CubePos cubePos;

    protected final boolean isCubic;
    protected final WorldStyle worldStyle;
    protected final boolean generates2DChunks;

    protected ChunkAccess[] columns = new ChunkAccess[DIAMETER_IN_SECTIONS * DIAMETER_IN_SECTIONS];

    //TODO: Figure out what to do with carverBiome and noiseChunk
    protected final Map<Heightmap.Types, SurfaceTrackerLeaf[]> cubeHeightmaps; //TODO: ChunkAccess now has it's own heightmaps but they are of class Heightmap
    protected final SurfaceTrackerLeaf[] lightHeightmaps = new SurfaceTrackerLeaf[DIAMETER_IN_SECTIONS * DIAMETER_IN_SECTIONS];

    public CubeAccess(CubePos pos, CubicLevelHeightAccessor levelHeightAccessor, UpgradeData upgradeData, LevelHeightAccessor heightAccessor, Registry<Biome> biomeRegistry,
                      long inhabitedTime,
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
        this.isCubic = levelHeightAccessor.isCubic();
        this.worldStyle = levelHeightAccessor.worldStyle();
        this.generates2DChunks = levelHeightAccessor.generates2DChunks();

        this.cubeHeightmaps = Maps.newEnumMap(Heightmap.Types.class);
    }

    @UsedFromASM public static CubePos getCubePosFromChunkAccess(ChunkAccess chunkAccess) {
        return ((CubeAccess) chunkAccess).cubePos;
    }

    public CubePos getCubePos() {
        return cubePos;
    }

    public void setFeatureBlocks(BlockPos pos, BlockState state) {
        setBlockState(pos, state, false);
    }

    public int getCubeLocalHeight(Heightmap.Types type, int x, int z) {
        SurfaceTrackerLeaf[] leaves = this.cubeHeightmaps.get(type);
        if (leaves == null) {
            throw new IllegalStateException("Trying to access heightmap of type " + type + " for cube " + this.cubePos + " before it's loaded!");
        }

        int xSection = blockToCubeLocalSection(x);
        int zSection = blockToCubeLocalSection(z);

        int index = columnToColumnIndex(xSection, zSection);

        SurfaceTrackerLeaf leaf = leaves[index];

        return leaf.getHeight(blockToLocal(x), blockToLocal(z));
    }

    public Map<Heightmap.Types, SurfaceTrackerLeaf[]> getCubeHeightmaps() {
        return cubeHeightmaps;
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

        int idx = columnToColumnIndex(xSection, zSection);
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

    @Override public void sectionLoaded(@Nonnull SurfaceTrackerLeaf surfaceTrackerLeaf, int localSectionX, int localSectionZ) {
        int idx = columnToColumnIndex(localSectionX, localSectionZ);

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
            for (int localSectionZ = 0; localSectionZ < DIAMETER_IN_SECTIONS; localSectionZ++) {
                for (int localSectionX = 0; localSectionX < DIAMETER_IN_SECTIONS; localSectionX++) {
                    int i = columnToColumnIndex(localSectionX, localSectionZ);
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
                int i = columnToColumnIndex(localSectionX, localSectionZ);
                if (lightHeightmapLeaves[i] != null) {
                    lightHeightmapLeaves[i].sourceUnloaded(cubeMinSection.x() + localSectionX, cubeMinSection.z() + localSectionZ, storage);
                    lightHeightmapLeaves[i] = null;
                }
            }
        }
    }

    @Override public int getSourceY() {
        return this.cubePos.getY();
    }

    public SurfaceTrackerLeaf[] getLightHeightmaps() {
        return lightHeightmaps;
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
                    LevelChunkSection section = this.getSection(blockToIndex(x, y, z));

                    if (!section.hasOnlyAir()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        // TODO: crash report generation
        int index = blockToIndex(pos.getX(), pos.getY(), pos.getZ());
        LevelChunkSection section = this.sections[index];
        return section.hasOnlyAir() ?
            Blocks.AIR.defaultBlockState() :
            section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    //TODO: Maybe use mixin to prevent method duplication
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        try {
            int clampedX = Mth.clamp(x, cubePos.minCubeX(), cubePos.maxCubeX());
            int clampedY = Mth.clamp(y, cubePos.minCubeY(), cubePos.maxCubeY());
            int clampedZ = Mth.clamp(z, cubePos.minCubeZ(), cubePos.maxCubeZ());

            return this.sections[blockToIndex(clampedX, clampedY, clampedZ)].getNoiseBiome(clampedX & 0x03, clampedY & 0x03, clampedZ & 0x03);
        } catch (Throwable e) {
            CrashReport crashReport = CrashReport.forThrowable(e, "Getting biome");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Biome being got");
            crashReportCategory.setDetail("Location", () -> CrashReportCategory.formatLocation(this, x, y, z));
            throw new ReportedException(crashReport);
        }
    }

    @Override
    public void fillBiomesFromNoise(BiomeResolver biomeResolver, Climate.Sampler sampler) {
        for (int sectionX = 0; sectionX < DIAMETER_IN_SECTIONS; sectionX++) {
            for (int sectionY = 0; sectionY < DIAMETER_IN_SECTIONS; sectionY++) {
                for (int sectionZ = 0; sectionZ < DIAMETER_IN_SECTIONS; sectionZ++) {
                    int minXQuart = QuartPos.fromBlock(sectionToMinBlock(sectionX));
                    int minZQuart = QuartPos.fromBlock(sectionToMinBlock(sectionZ));

                    LevelChunkSection section = this.sections[sectionToIndex(sectionX, sectionY, sectionZ)];
                    section.fillBiomesFromNoise(biomeResolver, sampler, minXQuart, minZQuart);
                }
            }
        }
    }

    public void setColumns(List<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> columns) {
        assert columns.size() == this.columns.length;

        for (int i = 0; i < columns.size(); i++) {
            Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> chunkEither = columns.get(i);
            if (chunkEither.left().isPresent()) {
                ChunkAccess column = chunkEither.left().get();
                assert column.getStatus().isOrAfter(this.getStatus()) : "Load order broken!";
                this.columns[i] = column;
            }
        }
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
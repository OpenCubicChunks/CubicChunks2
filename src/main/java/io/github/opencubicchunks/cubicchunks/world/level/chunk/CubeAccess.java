package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import static io.github.opencubicchunks.cc_core.api.CubicConstants.DIAMETER_IN_BLOCKS;
import static io.github.opencubicchunks.cc_core.api.CubicConstants.DIAMETER_IN_SECTIONS;

import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.Maps;
import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.heightmap.HeightmapSource;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.FeatureAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;

//TODO: Change most of the places where sections are accesses since they are done with the y coordinate and
//now they must be done by all three
public abstract class CubeAccess extends ChunkAccess implements BlockGetter, FeatureAccess, HeightmapSource {
    protected final CubePos cubePos;

    //TODO: Figure out what to do with carverBiome and noiseChunk
    protected final Map<Heightmap.Types, SurfaceTrackerLeaf[]> cubeHeightmaps; //TODO: ChunkAccess now has it's own heightmaps but they are of class Heightmap

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

    public void setFeatureBlocks(BlockPos pos, BlockState state) {
        setBlockState(pos, state, false);
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
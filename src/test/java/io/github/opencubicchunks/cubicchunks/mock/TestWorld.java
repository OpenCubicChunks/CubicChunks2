package io.github.opencubicchunks.cubicchunks.mock;

import static io.github.opencubicchunks.cc_core.utils.Coords.blockToLocal;
import static io.github.opencubicchunks.cubicchunks.levelgen.lighting.LightingTests.chunksWithinCube;
import static io.github.opencubicchunks.cubicchunks.levelgen.lighting.LightingTests.sectionsWithinCube;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.world.ColumnCubeMap;
import io.github.opencubicchunks.cubicchunks.mock.interfaces.BlockGetterLightHeightmapGetterColumnCubeMapGetter;
import io.github.opencubicchunks.cubicchunks.testutils.ColumnPos;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.LightCubeGetter;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLayerLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicSkyLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.SkyLightColumnChecker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.jetbrains.annotations.Nullable;

/**
 * Exists to mock both {@link LightChunkGetter} and {@link LightCubeGetter} at once
 */
public class TestWorld implements LightChunkGetter, LightCubeGetter {
    private final TestBlockGetter level;
    private final SkyLightEngine skyLightEngine;

    private final Map<CubePos, TestBlockGetter.TestCube> cubeMap = new HashMap<>();
    private final Map<ChunkPos, BlockGetterLightHeightmapGetterColumnCubeMapGetter> chunkMap = new HashMap<>();

    private final Map<ColumnPos, ColumnCubeMap> columnCubeMaps = new HashMap<>();

    public TestWorld(TestBlockGetter level) {
        this.level = level;
        SkyLightEngine lightEngine = new SkyLightEngine(this);
        ((CubicLayerLightEngine) (Object) lightEngine).setCubic();
        this.skyLightEngine = lightEngine;
    }

    public TestBlockGetter.TestCube loadCube(TestBlockGetter.TestCube cube) {
        if (this.cubeMap.containsKey(cube.getCubePos())) {
            fail("Cube added to world more than once!");
        }

        CubePos pos = cube.getCubePos();
        addChunksForCube(pos);

        return this.cubeMap.computeIfAbsent(pos, p -> {
            int[] beforeValues = new int[CubicConstants.DIAMETER_IN_BLOCKS * CubicConstants.DIAMETER_IN_BLOCKS];
            for (int blockX = cube.getCubePos().minCubeX(), maxX = blockX + CubicConstants.DIAMETER_IN_BLOCKS; blockX < maxX; blockX++) {
                for (int blockZ = cube.getCubePos().minCubeZ(), maxZ = blockZ + CubicConstants.DIAMETER_IN_BLOCKS; blockZ < maxZ; blockZ++) {
                    int localX = blockToLocal(blockX);
                    int localZ = blockToLocal(blockZ);
                    beforeValues[localX + localZ * CubicConstants.DIAMETER_IN_BLOCKS] = this.level.getHeightmap().getFirstAvailable(blockX, blockZ);
                }
            }


            this.columnCubeMaps.get(ColumnPos.from(pos)).markLoaded(p.getY());
            level.addCube(cube);

            Collection<SectionPos> sections = sectionsWithinCube(p);
            sections.forEach(sectionPos ->
                skyLightEngine.updateSectionStatus(sectionPos, false)
            );
            ((CubicLayerLightEngine) (Object) this.skyLightEngine).enableLightSources(cube.getCubePos(), true);
            ((CubicSkyLightEngine) (Object) this.skyLightEngine).doSkyLightForCube(cube);

            for (int localChunkX = 0; localChunkX < CubicConstants.DIAMETER_IN_SECTIONS; localChunkX++) {
                for (int localChunkZ = 0; localChunkZ < CubicConstants.DIAMETER_IN_SECTIONS; localChunkZ++) {
                    ChunkPos chunkPos = pos.asChunkPos(localChunkX, localChunkZ);
                    BlockGetterLightHeightmapGetterColumnCubeMapGetter chunk = this.chunkMap.get(chunkPos);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            int cubeLocalX = (chunkPos.x & CubicConstants.DIAMETER_IN_SECTIONS - 1) * 16 + x;
                            int cubeLocalZ = (chunkPos.z & CubicConstants.DIAMETER_IN_SECTIONS - 1) * 16 + z;
                            int oldHeight = beforeValues[cubeLocalX + cubeLocalZ * CubicConstants.DIAMETER_IN_BLOCKS];
                            int newHeight = this.level.getHeightmap().getFirstAvailable(cubeLocalX, cubeLocalZ);
                            ((SkyLightColumnChecker) (Object) this.skyLightEngine).checkSkyLightColumn(chunk,
                                chunkPos.getBlockX(x), chunkPos.getBlockZ(z), oldHeight, newHeight
                            );
                        }
                    }
                }
            }
            return cube;
        });
    }
    public void setChunk(ChunkPos chunkPos, BlockGetterLightHeightmapGetterColumnCubeMapGetter chunk) {
        this.chunkMap.put(chunkPos, chunk);
    }

    public ColumnCubeMap getColumnCubeMap(CubePos pos) {
        return this.columnCubeMaps.get(ColumnPos.from(pos));
    }

    public ColumnCubeMap getColumnCubeMap(ChunkPos pos) {
        return this.columnCubeMaps.get(ColumnPos.from(pos));
    }

    @Nullable @Override public BlockGetter getCubeForLighting(int cubeX, int cubeY, int cubeZ) {
        return this.cubeMap.get(CubePos.of(cubeX, cubeY, cubeZ));
    }

    @Nullable @Override public BlockGetter getChunkForLighting(int chunkX, int chunkZ) {
        return this.chunkMap.get(new ChunkPos(chunkX, chunkZ));
    }

    @Override public BlockGetter getLevel() {
        return this.level;
    }

    public SkyLightEngine getSkyLightEngine() {
        return this.skyLightEngine;
    }

    public Collection<CubePos> loadedCubes() {
        return new ArrayList<>(this.cubeMap.keySet());
    }

    private void addChunksForCube(CubePos pos) {
        chunksWithinCube(pos).forEach(chunkPos -> {
            this.chunkMap.computeIfAbsent(chunkPos, p -> {
                BlockGetterLightHeightmapGetterColumnCubeMapGetter lightHeightmapGetter = mock(BlockGetterLightHeightmapGetterColumnCubeMapGetter.class);
                TestHeightmap.OffsetTestHeightmap offsetHeightmap = level.getHeightmap().withOffset(chunkPos.x, chunkPos.z);
                when(lightHeightmapGetter.getLightHeightmap()).thenReturn(offsetHeightmap);
                when(lightHeightmapGetter.getCubeMap()).thenReturn(columnCubeMaps.computeIfAbsent(ColumnPos.from(pos), p_ -> new ColumnCubeMap()));

                this.skyLightEngine.enableLightSources(chunkPos, true);
                return lightHeightmapGetter;
            });
        });
    }

    public void setBlockState(BlockPos pos, BlockState state) {
        TestHeightmap heightmap = this.level.getHeightmap();

        int oldHeight = heightmap.getFirstAvailable(pos.getX(), pos.getZ());

        this.cubeMap.get(CubePos.from(pos)).setBlockStateLocal(pos, state);
        heightmap.update(pos.getX(), pos.getY(), pos.getZ(), state);

        int newHeight = heightmap.getFirstAvailable(pos.getX(), pos.getZ());
        ((SkyLightColumnChecker) (Object) skyLightEngine).checkSkyLightColumn(this.chunkMap.get(new ChunkPos(pos)), pos.getX(), pos.getZ(), oldHeight, newHeight);
    }
}

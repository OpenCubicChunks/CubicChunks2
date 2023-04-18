package io.github.opencubicchunks.cubicchunks.levelgen.lighting;

import static io.github.opencubicchunks.cubicchunks.levelgen.lighting.LightTestUtil.validateBlockLighting;
import static io.github.opencubicchunks.cubicchunks.levelgen.lighting.LightTestUtil.validateSkyLighting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.ColumnCubeMap;
import io.github.opencubicchunks.cubicchunks.mock.TestBlockGetter;
import io.github.opencubicchunks.cubicchunks.mock.TestHeightmap;
import io.github.opencubicchunks.cubicchunks.mock.interfaces.BlockGetterLightHeightmapGetterColumnCubeMapGetter;
import io.github.opencubicchunks.cubicchunks.mock.interfaces.LightCubeChunkGetter;
import io.github.opencubicchunks.cubicchunks.mock.interfaces.TestLightCubeChunkGetter;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLayerLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicSkyLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.SkyLightColumnChecker;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LightingTests {
    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Trivial sanity test for the BlockLightEngine
     */
    @Test
    public void testBlockLightEngineSingleSection() {
        TestBlockGetter blockGetter = new TestBlockGetter();

        ColumnCubeMap columnCubeMap = new ColumnCubeMap();
        columnCubeMap.markLoaded(0);

        LightCubeChunkGetter lightCubeGetter = mock(LightCubeChunkGetter.class);
        when(lightCubeGetter.getLevel()).thenReturn(blockGetter);
        when(lightCubeGetter.getChunkForLighting(anyInt(), anyInt())).thenThrow(new AssertionError("Called getChunkForLighting in BlockLightEngine"));
        when(lightCubeGetter.getCubeForLighting(0, 0, 0)).thenReturn(blockGetter);

        BlockLightEngine levelLightEngine = new BlockLightEngine(lightCubeGetter);
        ((CubicLayerLightEngine) (Object) levelLightEngine).setCubic();
        levelLightEngine.updateSectionStatus(SectionPos.of(0, 0, 0), false);
        levelLightEngine.enableLightSources(new ChunkPos(0, 0), true);

        levelLightEngine.onBlockEmissionIncrease(new BlockPos(0, 0, 0), 15);

        levelLightEngine.runUpdates(Integer.MAX_VALUE, true, true);

        validateBlockLighting(levelLightEngine, blockGetter, Set.of(SectionPos.of(0, 0, 0)), Collections.singletonMap(new BlockPos(0, 0, 0), 15))
            .ifErr(err -> fail(err.toString()));
    }

    /**
     * Trivial sanity test for the SkyLightEngine
     */
    @Test
    public void testSkyLightEngineSingleSection() {
        TestBlockGetter blockGetter = new TestBlockGetter();

        ColumnCubeMap columnCubeMap = new ColumnCubeMap();
        columnCubeMap.markLoaded(0);

        TestLightCubeChunkGetter lightCubeGetter = new TestLightCubeChunkGetter(blockGetter);

        BlockGetterLightHeightmapGetterColumnCubeMapGetter lightHeightmapGetter = mock(BlockGetterLightHeightmapGetterColumnCubeMapGetter.class);
        TestHeightmap.OffsetTestHeightmap heightmap = blockGetter.getHeightmap().withOffset(0, 0);
        when(lightHeightmapGetter.getLightHeightmap()).thenReturn(heightmap);
        when(lightHeightmapGetter.getCubeMap()).thenReturn(columnCubeMap);

        chunksWithinCube(CubePos.of(0, 0, 0)).forEach(chunkPos ->
            lightCubeGetter.setChunk(chunkPos, lightHeightmapGetter)
        );

        TestBlockGetter.OffsetCube cube = blockGetter.offsetCube(CubePos.of(0, 0, 0));
        lightCubeGetter.setCube(CubePos.of(0, 0, 0), cube);

        SkyLightEngine levelLightEngine = new SkyLightEngine(lightCubeGetter);
        ((CubicLayerLightEngine) (Object) levelLightEngine).setCubic();

        levelLightEngine.updateSectionStatus(SectionPos.of(0, 0, 0), false);
        levelLightEngine.enableLightSources(new ChunkPos(0, 0), true);

        ((CubicSkyLightEngine) (Object) levelLightEngine).doSkyLightForCube(cube);

        validateSkyLighting(levelLightEngine, blockGetter, Set.of(SectionPos.of(0, 0, 0)), blockGetter.getHeightmap().inner)
            .ifErr(err -> fail(err.toString()));
    }

    @Test
    public void testSkyLightEngineExistingTerrain() {
        TestBlockGetter blockGetter = new TestBlockGetter();

        for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
            for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                for (int y = 0; y < SectionPos.SECTION_SIZE; y++) {
                    blockGetter.setBlockState(new BlockPos(x, y, z), Math.max(x, z) > y ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }

        ColumnCubeMap columnCubeMap = new ColumnCubeMap();
        columnCubeMap.markLoaded(0);

        TestLightCubeChunkGetter lightCubeGetter = new TestLightCubeChunkGetter(blockGetter);

        BlockGetterLightHeightmapGetterColumnCubeMapGetter lightHeightmapGetter = mock(BlockGetterLightHeightmapGetterColumnCubeMapGetter.class);
        TestHeightmap.OffsetTestHeightmap heightmap = blockGetter.getHeightmap().withOffset(0, 0);
        when(lightHeightmapGetter.getLightHeightmap()).thenReturn(heightmap);
        when(lightHeightmapGetter.getCubeMap()).thenReturn(columnCubeMap);

        chunksWithinCube(CubePos.of(0, 0, 0)).forEach(chunkPos ->
            lightCubeGetter.setChunk(chunkPos, lightHeightmapGetter)
        );

        TestBlockGetter.OffsetCube cube = blockGetter.offsetCube(CubePos.of(0, 0, 0));
        lightCubeGetter.setCube(CubePos.of(0, 0, 0), cube);

        SkyLightEngine levelLightEngine = new SkyLightEngine(lightCubeGetter);
        ((CubicLayerLightEngine) (Object) levelLightEngine).setCubic();

        levelLightEngine.updateSectionStatus(SectionPos.of(0, 0, 0), false);
        levelLightEngine.enableLightSources(new ChunkPos(0, 0), true);

        ((CubicSkyLightEngine) (Object) levelLightEngine).doSkyLightForCube(cube);

        validateSkyLighting(levelLightEngine, blockGetter, Set.of(SectionPos.of(0, 0, 0)), blockGetter.getHeightmap().inner)
            .ifErr(err -> fail(err.toString()));
    }

    @Test
    public void testSkyLightEngineFullCover() {
        TestBlockGetter blockGetter = new TestBlockGetter();

        for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
            for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                blockGetter.setBlockState(new BlockPos(x, 3, z), Blocks.STONE.defaultBlockState());
            }
        }

        ColumnCubeMap columnCubeMap = new ColumnCubeMap();
        columnCubeMap.markLoaded(0);

        TestLightCubeChunkGetter lightCubeGetter = new TestLightCubeChunkGetter(blockGetter);

        BlockGetterLightHeightmapGetterColumnCubeMapGetter lightHeightmapGetter = mock(BlockGetterLightHeightmapGetterColumnCubeMapGetter.class);
        TestHeightmap.OffsetTestHeightmap heightmap = blockGetter.getHeightmap().withOffset(0, 0);
        when(lightHeightmapGetter.getLightHeightmap()).thenReturn(heightmap);
        when(lightHeightmapGetter.getCubeMap()).thenReturn(columnCubeMap);

        chunksWithinCube(CubePos.of(0, 0, 0)).forEach(chunkPos ->
            lightCubeGetter.setChunk(chunkPos, lightHeightmapGetter)
        );

        TestBlockGetter.OffsetCube cube = blockGetter.offsetCube(CubePos.of(0, 0, 0));
        lightCubeGetter.setCube(CubePos.of(0, 0, 0), cube);

        SkyLightEngine levelLightEngine = new SkyLightEngine(lightCubeGetter);
        ((CubicLayerLightEngine) (Object) levelLightEngine).setCubic();

        levelLightEngine.updateSectionStatus(SectionPos.of(0, 0, 0), false);
        levelLightEngine.enableLightSources(new ChunkPos(0, 0), true);

        ((CubicSkyLightEngine) (Object) levelLightEngine).doSkyLightForCube(cube);

        validateSkyLighting(levelLightEngine, blockGetter, Set.of(SectionPos.of(0, 0, 0)), blockGetter.getHeightmap().inner)
            .ifErr(err -> fail(err.toString()));
    }

    @Test
    public void testSkyLightEngineFullCoverWithHole() {
        TestBlockGetter blockGetter = new TestBlockGetter();

        for (int x = 0; x < CubicConstants.DIAMETER_IN_BLOCKS; x++) {
            for (int z = 0; z < CubicConstants.DIAMETER_IN_BLOCKS; z++) {
                blockGetter.setBlockState(new BlockPos(x, 3, z), Blocks.STONE.defaultBlockState());
            }
        }

        blockGetter.setBlockState(new BlockPos(7, 3, 7), Blocks.AIR.defaultBlockState());

        ColumnCubeMap columnCubeMap = new ColumnCubeMap();
        columnCubeMap.markLoaded(0);

        TestLightCubeChunkGetter lightCubeGetter = new TestLightCubeChunkGetter(blockGetter);
        chunksWithinCube(CubePos.of(0, 0, 0)).forEach(chunkPos -> {
            BlockGetterLightHeightmapGetterColumnCubeMapGetter lightHeightmapGetter = mock(BlockGetterLightHeightmapGetterColumnCubeMapGetter.class);
            TestHeightmap.OffsetTestHeightmap offsetHeightmap = blockGetter.getHeightmap().withOffset(chunkPos.x, chunkPos.z);
            when(lightHeightmapGetter.getLightHeightmap()).thenReturn(offsetHeightmap);
            when(lightHeightmapGetter.getCubeMap()).thenReturn(columnCubeMap);

            lightCubeGetter.setChunk(new ChunkPos(chunkPos.x, chunkPos.z), lightHeightmapGetter);
        });

        TestBlockGetter.OffsetCube cube = blockGetter.offsetCube(CubePos.of(0, 0, 0));
        lightCubeGetter.setCube(CubePos.of(0, 0, 0), cube);

        SkyLightEngine levelLightEngine = new SkyLightEngine(lightCubeGetter);
        ((CubicLayerLightEngine) (Object) levelLightEngine).setCubic();

        Collection<SectionPos> sections = sectionsWithinCube(CubePos.of(0, 0, 0));
        sections.forEach(sectionPos ->
            levelLightEngine.updateSectionStatus(sectionPos, false)
        );
        chunksWithinCube(CubePos.of(0, 0, 0)).forEach(chunkPos ->
            levelLightEngine.enableLightSources(chunkPos, true)
        );

        ((CubicSkyLightEngine) (Object) levelLightEngine).doSkyLightForCube(cube);
        levelLightEngine.runUpdates(Integer.MAX_VALUE, true, true);

        validateSkyLighting(levelLightEngine, blockGetter, new HashSet<>(sections), blockGetter.getHeightmap().inner)
            .ifErr(err -> fail(err.toString()));
    }

    static Stream<Arguments> testSkyLightEngineMultiCubeOrderedArguments() {
        final List<int[]> cubeLoadOrder = List.of(
            new int[] { 0, 1, 2, 3 },
            new int[] { 3, 2, 1, 0 },
            new int[] { 3, 1, 0, 2 },
            new int[] { 0, 2, 1, 3 },
            new int[] { 2, 0, 3 },
            new int[] { 1, 3, 2 },
            new int[] { 2, 0 },
            new int[] { 0, 2 }
        );

        return cubeLoadOrder.stream().map(Arguments::arguments);
    }

    @ParameterizedTest
    @MethodSource("testSkyLightEngineMultiCubeOrderedArguments")
    public void testSkyLightEngineMultiCubeOrdered(int[] loadOrder) {
        TestBlockGetter blockGetter = new TestBlockGetter();

        for (int x = 0; x < CubicConstants.DIAMETER_IN_BLOCKS; x++) {
            for (int z = 0; z < CubicConstants.DIAMETER_IN_BLOCKS; z++) {
                blockGetter.setBlockState(new BlockPos(x, 74, z), Blocks.STONE.defaultBlockState());
            }
        }

        blockGetter.setBlockState(new BlockPos(15, 74, 15), Blocks.AIR.defaultBlockState());

        ColumnCubeMap columnCubeMap = new ColumnCubeMap();

        TestLightCubeChunkGetter lightCubeGetter = new TestLightCubeChunkGetter(blockGetter);
        HashMap<ChunkPos, BlockGetterLightHeightmapGetterColumnCubeMapGetter> chunks = new HashMap<>();
        chunksWithinCube(CubePos.of(0, 0, 0)).forEach(chunkPos -> {
            BlockGetterLightHeightmapGetterColumnCubeMapGetter lightHeightmapGetter = mock(BlockGetterLightHeightmapGetterColumnCubeMapGetter.class);
            TestHeightmap.OffsetTestHeightmap offsetHeightmap = blockGetter.getHeightmap().withOffset(chunkPos.x, chunkPos.z);
            when(lightHeightmapGetter.getLightHeightmap()).thenReturn(offsetHeightmap);
            when(lightHeightmapGetter.getCubeMap()).thenReturn(columnCubeMap);

            chunks.put(chunkPos, lightHeightmapGetter);
            lightCubeGetter.setChunk(new ChunkPos(chunkPos.x, chunkPos.z), lightHeightmapGetter);
        });

        List<TestBlockGetter.OffsetCube> cubes = new ArrayList<>();

        SkyLightEngine levelLightEngine = new SkyLightEngine(lightCubeGetter);
        ((CubicLayerLightEngine) (Object) levelLightEngine).setCubic();

        for (int cubeY : loadOrder) {
            TestBlockGetter.OffsetCube cube = blockGetter.offsetCube(CubePos.of(0, cubeY, 0));
            columnCubeMap.markLoaded(cube.getCubePos().getY());
            lightCubeGetter.setCube(cube.getCubePos(), cube);
            Collection<SectionPos> sections = sectionsWithinCube(cube.getCubePos());
            sections.forEach(sectionPos ->
                levelLightEngine.updateSectionStatus(sectionPos, false)
            );
            ((CubicSkyLightEngine) (Object) levelLightEngine).doSkyLightForCube(cube);
            levelLightEngine.runUpdates(Integer.MAX_VALUE, true, true);
        }

        assertEquals(15, levelLightEngine.getLightValue(new BlockPos(15, 73, 15)));
        validateSkyLighting(levelLightEngine, blockGetter, sectionsForCubes(cubes), blockGetter.getHeightmap().inner);

        blockGetter.setBlockState(new BlockPos(15, 74, 15), Blocks.STONE.defaultBlockState());
        int height = blockGetter.getHeightmap().getFirstAvailable(15, 15);
        assertEquals(75, height);
        ((SkyLightColumnChecker) (Object) levelLightEngine).checkSkyLightColumn(chunks.get(new ChunkPos(0, 0)), 15, 15, 0, height);
        levelLightEngine.runUpdates(Integer.MAX_VALUE, true, true);

        assertEquals(0, levelLightEngine.getLightValue(new BlockPos(15, 73, 15)));
        validateSkyLighting(levelLightEngine, blockGetter, sectionsForCubes(cubes), blockGetter.getHeightmap().inner)
            .ifErr(err -> fail(err.toString()));
    }

    public static Collection<SectionPos> sectionsWithinCube(CubePos cubePos) {
        List<SectionPos> positions = new ArrayList<>(CubicConstants.SECTION_COUNT);
        for (int x = Coords.cubeToSection(cubePos.getX(), 0), maxX = x + CubicConstants.DIAMETER_IN_SECTIONS; x < maxX; x++) {
            for (int y = Coords.cubeToSection(cubePos.getY(), 0), maxY = y + CubicConstants.DIAMETER_IN_SECTIONS; y < maxY; y++) {
                for (int z = Coords.cubeToSection(cubePos.getX(), 0), maxZ = z + CubicConstants.DIAMETER_IN_SECTIONS; z < maxZ; z++) {
                    positions.add(SectionPos.of(x, y, z));
                }
            }
        }
        return positions;
    }

    public static Collection<ChunkPos> chunksWithinCube(CubePos cubePos) {
        List<ChunkPos> positions = new ArrayList<>(CubicConstants.SECTION_COUNT);
        for (int x = Coords.cubeToSection(cubePos.getX(), 0), maxX = x + CubicConstants.DIAMETER_IN_SECTIONS; x < maxX; x++) {
            for (int z = Coords.cubeToSection(cubePos.getX(), 0), maxZ = z + CubicConstants.DIAMETER_IN_SECTIONS; z < maxZ; z++) {
                positions.add(new ChunkPos(x, z));
            }
        }
        return positions;
    }

    public static Set<SectionPos> sectionsForCubes(Collection<TestBlockGetter.OffsetCube> cubes) {
        Set<SectionPos> sections = new HashSet<>();
        for (TestBlockGetter.OffsetCube cube : cubes) {
            sections.addAll(sectionsWithinCube(cube.getCubePos()));
        }
        return sections;
    }
}

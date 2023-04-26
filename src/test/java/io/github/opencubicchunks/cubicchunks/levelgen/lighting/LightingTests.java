package io.github.opencubicchunks.cubicchunks.levelgen.lighting;

import static io.github.opencubicchunks.cubicchunks.testutils.LightTestUtil.validateBlockLighting;
import static io.github.opencubicchunks.cubicchunks.testutils.LightTestUtil.validateSkyLighting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.utils.Coords;
import io.github.opencubicchunks.cc_core.world.ColumnCubeMap;
import io.github.opencubicchunks.cubicchunks.mock.TestBlockGetter;
import io.github.opencubicchunks.cubicchunks.mock.TestWorld;
import io.github.opencubicchunks.cubicchunks.mock.interfaces.LightCubeChunkGetter;
import io.github.opencubicchunks.cubicchunks.testutils.LightError;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLayerLightEngine;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
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
import org.junit.jupiter.params.provider.ValueSource;

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
            .ifErr(LightError::report);
    }

    /**
     * Trivial sanity test for the SkyLightEngine
     */
    @Test
    public void testSkyLightEngineSingleSection() {
        TestBlockGetter blockGetter = new TestBlockGetter();
        TestWorld world = new TestWorld(blockGetter);
        SkyLightEngine skyLightEngine = world.getSkyLightEngine();

        TestBlockGetter.TestCube cube = new TestBlockGetter.TestCube(CubePos.of(0, 0, 0));
        world.loadCube(cube);

        validateSkyLighting(skyLightEngine, blockGetter, sectionsForCubes(world.loadedCubes()), blockGetter.getHeightmap().inner)
            .ifErr(LightError::report);
    }

    @Test
    public void testSkyLightBottomCubeFirst() {
        TestBlockGetter blockGetter = new TestBlockGetter();
        TestWorld world = new TestWorld(blockGetter);
        SkyLightEngine skyLightEngine = world.getSkyLightEngine();

        List<TestBlockGetter.TestCube> cubesLoadOrder = new ArrayList<>();

        TestBlockGetter.TestCube cube3 = new TestBlockGetter.TestCube(CubePos.of(0, -3, 0));
        world.loadCube(cube3);
        cubesLoadOrder.add(cube3);

        skyLightEngine.runUpdates(Integer.MAX_VALUE, true, true);
        validateSkyLighting(skyLightEngine, blockGetter, sectionsForCubes(world.loadedCubes()), blockGetter.getHeightmap().inner)
            .ifErr(err -> {
                List<Integer> cubeLoadOrder = cubesLoadOrder.stream().map(c -> c.getCubePos().getY()).toList();
                err.reportWithAdditional("Cube load order: " + Arrays.toString(cubeLoadOrder.toArray()));
            });

        TestBlockGetter.TestCube cube2 = new TestBlockGetter.TestCube(CubePos.of(0, -2, 0));
        cube2.setBlockStateLocal(new BlockPos(1, 1, 24), Blocks.STONE.defaultBlockState());
        cubesLoadOrder.add(cube2);
        world.loadCube(cube2);

        skyLightEngine.runUpdates(Integer.MAX_VALUE, true, true);
        validateSkyLighting(skyLightEngine, blockGetter, sectionsForCubes(world.loadedCubes()), blockGetter.getHeightmap().inner)
            .ifErr(err -> {
                List<Integer> cubeLoadOrder = cubesLoadOrder.stream().map(c -> c.getCubePos().getY()).toList();
                err.reportWithAdditional("Cube load order: " + Arrays.toString(cubeLoadOrder.toArray()));
            });
    }

    @Test
    public void testSkyLightEngineExistingTerrain() {
        TestBlockGetter blockGetter = new TestBlockGetter();
        TestWorld world = new TestWorld(blockGetter);
        SkyLightEngine skyLightEngine = world.getSkyLightEngine();

        TestBlockGetter.TestCube cube = new TestBlockGetter.TestCube(CubePos.of(0, 0, 0));
        for (int x = 0; x < CubicConstants.DIAMETER_IN_BLOCKS; x++) {
            for (int z = 0; z < CubicConstants.DIAMETER_IN_BLOCKS; z++) {
                for (int y = 0; y < CubicConstants.DIAMETER_IN_BLOCKS; y++) {
                    cube.setBlockStateLocal(new BlockPos(x, y, z), (x == y || z == y) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        world.loadCube(cube);
        skyLightEngine.runUpdates(Integer.MAX_VALUE, true, true);

        validateSkyLighting(skyLightEngine, blockGetter, sectionsForCubes(world.loadedCubes()), blockGetter.getHeightmap().inner)
            .ifErr(LightError::report);
    }

    @Test
    public void testSkyLightEngineFullCover() {
        TestBlockGetter blockGetter = new TestBlockGetter();
        TestWorld world = new TestWorld(blockGetter);
        SkyLightEngine skyLightEngine = world.getSkyLightEngine();

        TestBlockGetter.TestCube cube = new TestBlockGetter.TestCube(CubePos.of(0, 0, 0));
        for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
            for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                cube.setBlockStateLocal(new BlockPos(x, 3, z), Blocks.STONE.defaultBlockState());
            }
        }
        world.loadCube(cube);

        skyLightEngine.runUpdates(Integer.MAX_VALUE, true, true);
        validateSkyLighting(skyLightEngine, blockGetter, sectionsForCubes(world.loadedCubes()), blockGetter.getHeightmap().inner)
            .ifErr(LightError::report);
    }

    @Test
    public void testSkyLightEngineFullCoverWithHole() {
        TestBlockGetter blockGetter = new TestBlockGetter();
        TestWorld world = new TestWorld(blockGetter);
        SkyLightEngine skyLightEngine = world.getSkyLightEngine();

        TestBlockGetter.TestCube cube = new TestBlockGetter.TestCube(CubePos.of(0, 0, 0));
        for (int x = 0; x < CubicConstants.DIAMETER_IN_BLOCKS; x++) {
            for (int z = 0; z < CubicConstants.DIAMETER_IN_BLOCKS; z++) {
                cube.setBlockStateLocal(new BlockPos(x, 3, z), Blocks.STONE.defaultBlockState());
            }
        }
        cube.setBlockStateLocal(new BlockPos(7, 3, 7), Blocks.AIR.defaultBlockState());

        world.loadCube(cube);

        skyLightEngine.runUpdates(Integer.MAX_VALUE, true, true);
        validateSkyLighting(skyLightEngine, blockGetter, sectionsForCubes(world.loadedCubes()), blockGetter.getHeightmap().inner)
            .ifErr(LightError::report);
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
        TestWorld world = new TestWorld(blockGetter);

        SkyLightEngine skyLightEngine = world.getSkyLightEngine();

        for (int cubeY : loadOrder) {
            TestBlockGetter.TestCube cube = new TestBlockGetter.TestCube(CubePos.of(0, cubeY, 0));
            if (cubeY == 2) {
                for (int x = 0; x < CubicConstants.DIAMETER_IN_BLOCKS; x++) {
                    for (int z = 0; z < CubicConstants.DIAMETER_IN_BLOCKS; z++) {
                        cube.setBlockStateLocal(new BlockPos(x, 10, z), Blocks.STONE.defaultBlockState());
                    }
                }

                cube.setBlockStateLocal(new BlockPos(15, 10, 15), Blocks.AIR.defaultBlockState());
            }
            world.loadCube(cube);
        }
        skyLightEngine.runUpdates(Integer.MAX_VALUE, true, true);

        assertEquals(15, skyLightEngine.getLightValue(new BlockPos(15, 73, 15)));
        validateSkyLighting(skyLightEngine, blockGetter, sectionsForCubes(world.loadedCubes()), blockGetter.getHeightmap().inner);

        world.setBlockState(new BlockPos(15, 74, 15), Blocks.STONE.defaultBlockState());
        skyLightEngine.runUpdates(Integer.MAX_VALUE, true, true);

        assertEquals(0, skyLightEngine.getLightValue(new BlockPos(15, 73, 15)));
        validateSkyLighting(skyLightEngine, blockGetter, sectionsForCubes(world.loadedCubes()), blockGetter.getHeightmap().inner)
            .ifErr(LightError::report);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 2 })
    public void testSkyLightEngineSeededRandom(long seed) {
        Random r = new Random(seed);
        int cubesToLoad = r.nextInt(10, 20);

        TestBlockGetter blockGetter = new TestBlockGetter();
        TestWorld world = new TestWorld(blockGetter);

        Set<Integer> loadedCubes = new HashSet<>();
        List<CubePos> cubes = new ArrayList<>();

        for (int i = 0; i < cubesToLoad; i++) {
            int cubeY = r.nextInt(-cubesToLoad, cubesToLoad);
            while (loadedCubes.contains(cubeY)) {
                cubeY = r.nextInt(-cubesToLoad, cubesToLoad);
            }
            TestBlockGetter.TestCube cube = new TestBlockGetter.TestCube(CubePos.of(0, cubeY, 0));
            loadedCubes.add(cubeY);
            cubes.add(cube.getCubePos());

            for (int j = 0; j < r.nextInt(30, 200); j++) {
                BlockPos pos = new BlockPos(r.nextInt(32), r.nextInt(32), r.nextInt(32));
                cube.setBlockStateLocal(pos, Blocks.STONE.defaultBlockState());
            }

            world.loadCube(cube);

            world.getSkyLightEngine().runUpdates(Integer.MAX_VALUE, true, true);
            validateSkyLighting(world.getSkyLightEngine(), blockGetter, sectionsForCubes(cubes), blockGetter.getHeightmap().inner)
                .ifErr(err -> {
                    List<Integer> cubeLoadOrder = cubes.stream().map(Vec3i::getY).toList();
                    err.reportWithAdditional("Cube load order: " + Arrays.toString(cubeLoadOrder.toArray()));
                });
        }
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

    public static Set<SectionPos> sectionsForCubes(Collection<CubePos> cubes) {
        Set<SectionPos> sections = new HashSet<>();
        for (CubePos pos : cubes) {
            sections.addAll(sectionsWithinCube(pos));
        }
        return sections;
    }
}

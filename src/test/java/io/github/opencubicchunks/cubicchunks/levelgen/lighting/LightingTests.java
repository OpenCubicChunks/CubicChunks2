package io.github.opencubicchunks.cubicchunks.levelgen.lighting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import io.github.opencubicchunks.cubicchunks.Vector2i;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLayerLightEngine;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LayerLightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class LightingTests {
    @BeforeAll
    public static void setup() {
        System.setProperty("cubicchunks.test.dfu_disabled", "true");
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Trivial sanity test for the BlockLightEngine
     */
    @Test
    public void testBlockLightEngine_SingleSection() {
        BlockGetter blockGetter = mock(BlockGetter.class);
        when(blockGetter.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());

        LightCubeChunkGetter lightCubeGetter = mock(LightCubeChunkGetter.class);
        when(lightCubeGetter.getLevel()).thenReturn(blockGetter);
        when(lightCubeGetter.getChunkForLighting(anyInt(), anyInt())).thenThrow(new AssertionError("Called getChunkForLighting on a lightCubeGetter"));
        when(lightCubeGetter.getCubeForLighting(0, 0, 0)).thenReturn(blockGetter);

        BlockLightEngine levelLightEngine = new BlockLightEngine(lightCubeGetter);
        ((CubicLayerLightEngine) (Object) levelLightEngine).setCubic();
        levelLightEngine.updateSectionStatus(SectionPos.of(0, 0, 0), false);
        levelLightEngine.enableLightSources(new ChunkPos(0, 0), true);

        levelLightEngine.onBlockEmissionIncrease(new BlockPos(0, 0, 0), 5);

        levelLightEngine.runUpdates(Integer.MAX_VALUE, true, true);

        validateBlockLighting(levelLightEngine, blockGetter, Set.of(SectionPos.of(0, 0, 0)), Collections.singletonMap(new BlockPos(0, 0, 0), 5));
    }

    /**
     * Trivial sanity test for the SkyLightEngine
     */
    @Test
    public void testSkyLightEngine_SingleSection() {
        BlockGetter blockGetter = mock(BlockGetter.class);
        when(blockGetter.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());

        LightCubeChunkGetter lightCubeGetter = mock(LightCubeChunkGetter.class);
        when(lightCubeGetter.getLevel()).thenReturn(blockGetter);
        when(lightCubeGetter.getChunkForLighting(anyInt(), anyInt())).thenThrow(new AssertionError("Called getChunkForLighting on a lightCubeGetter"));
        when(lightCubeGetter.getCubeForLighting(0, 0, 0)).thenReturn(blockGetter);

        SkyLightEngine levelLightEngine = new SkyLightEngine(lightCubeGetter);
        ((CubicLayerLightEngine) (Object) levelLightEngine).setCubic();

        levelLightEngine.updateSectionStatus(SectionPos.of(0, 0, 0), false);
        levelLightEngine.enableLightSources(new ChunkPos(0, 0), true);

        levelLightEngine.runUpdates(Integer.MAX_VALUE, true, true);

        levelLightEngine.updateSectionStatus(SectionPos.of(0, 0, 0), false);
        levelLightEngine.enableLightSources(new ChunkPos(0, 0), true);

        Map<Vector2i, Integer> heightmap = new HashMap<>();
        for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
            for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                heightmap.put(new Vector2i(x, z), 0);
            }
        }

        validateSkyLighting(levelLightEngine, blockGetter, Set.of(SectionPos.of(0, 0, 0)), heightmap);
    }

    public static void validateBlockLighting(LayerLightEngine<?, ?> lightEngine, BlockGetter blockGetter, Set<SectionPos> sectionsPresent, Map<BlockPos, Integer> lights) throws AssertionError {
        sectionsPresent.forEach(sectionPos -> {
            BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(0, 0, 0);
            for (int x = sectionPos.minBlockX(), maxX = x + SectionPos.SECTION_SIZE; x < maxX; x++) {
                for (int y = sectionPos.minBlockY(), maxY = y + SectionPos.SECTION_SIZE; y < maxY; y++) {
                    for (int z = sectionPos.minBlockZ(), maxZ = z + SectionPos.SECTION_SIZE; z < maxZ; z++) {
                        int light = lightEngine.getLightValue(blockPos.set(x, y, z));
                        Integer sourceLight = lights.get(blockPos);
                        if (sourceLight != null) {
                            assertEquals(sourceLight, light, "Light sources wrong!");
                            continue;
                        }

                        // TODO: handle voxel shape occlusion
                        if (validateOccluded(blockGetter, blockPos, light)) continue;

                        validateNeighbors(lightEngine, sectionsPresent, blockPos, x, y, z, light);
                    }
                }
            }
        });
    }

    public static void validateSkyLighting(LayerLightEngine<?, ?> lightEngine, BlockGetter blockGetter, Set<SectionPos> sectionsPresent, Map<Vector2i, Integer> heightMap) throws AssertionError {
        sectionsPresent.forEach(sectionPos -> {
            BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(0, 0, 0);
            for (int x = sectionPos.minBlockX(), maxX = x + SectionPos.SECTION_SIZE; x < maxX; x++) {
                for (int y = sectionPos.minBlockY(), maxY = y + SectionPos.SECTION_SIZE; y < maxY; y++) {
                    for (int z = sectionPos.minBlockZ(), maxZ = z + SectionPos.SECTION_SIZE; z < maxZ; z++) {
                        int light = lightEngine.getLightValue(blockPos.set(x, y, z));
                        Integer height = heightMap.get(new Vector2i(x, z));
                        if (y >= height) {
                            assertEquals(15, light);
                            continue;
                        }

                        // TODO: handle voxel shape occlusion
                        if (validateOccluded(blockGetter, blockPos, light)) continue;

                        validateNeighbors(lightEngine, sectionsPresent, blockPos, x, y, z, light);
                    }
                }
            }
        });
    }

    private static boolean validateOccluded(BlockGetter blockGetter, BlockPos.MutableBlockPos blockPos, int light) {
        if (blockGetter.getBlockState(blockPos).canOcclude()) {
            assertEquals(0, light, "Occluding block has light!");
            return true;
        }
        return false;
    }

    private static void validateNeighbors(LayerLightEngine<?, ?> lightEngine, Set<SectionPos> sectionsPresent, BlockPos.MutableBlockPos blockPos, int x, int y, int z, int light) {
        blockPos.set(x, y + 1, z);
        int lightAbove = 0;
        if (sectionsPresent.contains(SectionPos.of(blockPos))) {
            lightAbove = lightEngine.getLightValue(blockPos);
        }
        blockPos.set(x, y - 1, z);
        int lightBelow = 0;
        if (sectionsPresent.contains(SectionPos.of(blockPos))) {
            lightBelow = lightEngine.getLightValue(blockPos);
        }
        blockPos.set(x, y, z - 1);
        int lightNorth = 0;
        if (sectionsPresent.contains(SectionPos.of(blockPos))) {
            lightNorth = lightEngine.getLightValue(blockPos);
        }
        blockPos.set(x + 1, y, z);
        int lightEast = 0;
        if (sectionsPresent.contains(SectionPos.of(blockPos))) {
            lightEast = lightEngine.getLightValue(blockPos);
        }
        blockPos.set(x, y, z + 1);
        int lightSouth = 0;
        if (sectionsPresent.contains(SectionPos.of(blockPos))) {
            lightSouth = lightEngine.getLightValue(blockPos);
        }
        blockPos.set(x - 1, y, z);
        int lightWest = 0;
        if (sectionsPresent.contains(SectionPos.of(blockPos))) {
            lightWest = lightEngine.getLightValue(blockPos);
        }

        int brightestNeighbor = Math.max(Math.max(Math.max(lightAbove, lightBelow), Math.max(lightNorth, lightEast)), Math.max(lightSouth, lightWest));
        assertEquals(Math.max(brightestNeighbor - 1, 0), light, formatPropagationError(x, y, z, lightAbove, lightBelow, lightNorth, lightSouth, lightEast, lightWest));
    }

    /**
     * This exists because having 9 final variables every time it's called would be dumb.
     * <p><b>Java is dumb.</b></p>
     */
    public static Supplier<String> formatPropagationError(int x, int y, int z, int lightAbove, int lightBelow, int lightNorth, int lightSouth, int lightEast, int lightWest) {
        return () -> String.format("Propagation wrong for pos: (%d, %d, %d)!\n\tAbove: %d | Below: %d\n\tNorth: %d | South: %d\n\tEast: %d | West: %d",
            x, y, z, lightAbove, lightBelow, lightNorth, lightSouth, lightEast, lightWest);
    }
}

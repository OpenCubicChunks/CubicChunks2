package io.github.opencubicchunks.cubicchunks.levelgen.lighting;

import static io.github.opencubicchunks.cubicchunks.levelgen.lighting.LightTestUtil.validateBlockLighting;
import static io.github.opencubicchunks.cubicchunks.levelgen.lighting.LightTestUtil.validateSkyLighting;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Set;

import io.github.opencubicchunks.cc_core.api.CubePos;
import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.world.ColumnCubeMap;
import io.github.opencubicchunks.cubicchunks.mock.TestBlockGetter;
import io.github.opencubicchunks.cubicchunks.mock.interfaces.BlockGetterLightHeightmapGetterColumnCubeMapGetter;
import io.github.opencubicchunks.cubicchunks.mock.interfaces.LightCubeChunkGetter;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicLayerLightEngine;
import io.github.opencubicchunks.cubicchunks.world.lighting.CubicSkyLightEngine;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

        levelLightEngine.onBlockEmissionIncrease(new BlockPos(0, 0, 0), 15);

        levelLightEngine.runUpdates(Integer.MAX_VALUE, true, true);

        validateBlockLighting(levelLightEngine, blockGetter, Set.of(SectionPos.of(0, 0, 0)), Collections.singletonMap(new BlockPos(0, 0, 0), 15));
    }

    /**
     * Trivial sanity test for the SkyLightEngine
     */
    @Test
    public void testSkyLightEngineSingleSection() {
        TestBlockGetter blockGetter = new TestBlockGetter();

        for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
            for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                for (int y = 0; y < SectionPos.SECTION_SIZE; y++) {
                    blockGetter.setBlockState(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }

        LightCubeChunkGetter lightCubeGetter = mock(LightCubeChunkGetter.class);
        when(lightCubeGetter.getLevel()).thenReturn(blockGetter);

        ColumnCubeMap columnCubeMap = new ColumnCubeMap();
        columnCubeMap.markLoaded(0);

        BlockGetterLightHeightmapGetterColumnCubeMapGetter lightHeightmapGetter = mock(BlockGetterLightHeightmapGetterColumnCubeMapGetter.class);
        when(lightHeightmapGetter.getLightHeightmap()).thenReturn(blockGetter.getHeightmap());
        when(lightHeightmapGetter.getCubeMap()).thenReturn(columnCubeMap);

        when(lightCubeGetter.getChunkForLighting(anyInt(), anyInt())).thenReturn(lightHeightmapGetter);

        CubeAccess cubeAccess = mock(CubeAccess.class);
        when(cubeAccess.getCubePos()).thenReturn(CubePos.of(0, 0, 0));
        when(cubeAccess.getStatus()).thenReturn(ChunkStatus.LIGHT);

        when(lightCubeGetter.getCubeForLighting(0, 0, 0)).thenReturn(blockGetter);

        SkyLightEngine levelLightEngine = new SkyLightEngine(lightCubeGetter);
        ((CubicLayerLightEngine) (Object) levelLightEngine).setCubic();

        levelLightEngine.updateSectionStatus(SectionPos.of(0, 0, 0), false);
        levelLightEngine.enableLightSources(new ChunkPos(0, 0), true);

        ((CubicSkyLightEngine) (Object) levelLightEngine).doSkyLightForCube(cubeAccess);

        validateSkyLighting(levelLightEngine, blockGetter, Set.of(SectionPos.of(0, 0, 0)), blockGetter.getHeightmap().inner);
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

        LightCubeChunkGetter lightCubeGetter = mock(LightCubeChunkGetter.class);
        when(lightCubeGetter.getLevel()).thenReturn(blockGetter);

        ColumnCubeMap columnCubeMap = new ColumnCubeMap();
        columnCubeMap.markLoaded(0);

        BlockGetterLightHeightmapGetterColumnCubeMapGetter lightHeightmapGetter = mock(BlockGetterLightHeightmapGetterColumnCubeMapGetter.class);
        when(lightHeightmapGetter.getLightHeightmap()).thenReturn(blockGetter.getHeightmap());
        when(lightHeightmapGetter.getCubeMap()).thenReturn(columnCubeMap);

        when(lightCubeGetter.getChunkForLighting(anyInt(), anyInt())).thenReturn(lightHeightmapGetter);

        CubeAccess cubeAccess = mock(CubeAccess.class);
        when(cubeAccess.getCubePos()).thenReturn(CubePos.of(0, 0, 0));
        when(cubeAccess.getStatus()).thenReturn(ChunkStatus.LIGHT);

        when(lightCubeGetter.getCubeForLighting(0, 0, 0)).thenReturn(blockGetter);

        SkyLightEngine levelLightEngine = new SkyLightEngine(lightCubeGetter);
        ((CubicLayerLightEngine) (Object) levelLightEngine).setCubic();

        levelLightEngine.updateSectionStatus(SectionPos.of(0, 0, 0), false);
        levelLightEngine.enableLightSources(new ChunkPos(0, 0), true);

        ((CubicSkyLightEngine) (Object) levelLightEngine).doSkyLightForCube(cubeAccess);

        validateSkyLighting(levelLightEngine, blockGetter, Set.of(SectionPos.of(0, 0, 0)), blockGetter.getHeightmap().inner);
    }

    @Test
    public void testSkyLightEngineFullCover() {
        TestBlockGetter blockGetter = new TestBlockGetter();

        for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
            for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                for (int y = 0; y < SectionPos.SECTION_SIZE; y++) {
                    blockGetter.setBlockState(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }

        for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
            for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                blockGetter.setBlockState(new BlockPos(x, 3, z), Blocks.STONE.defaultBlockState());
            }
        }

        LightCubeChunkGetter lightCubeGetter = mock(LightCubeChunkGetter.class);
        when(lightCubeGetter.getLevel()).thenReturn(blockGetter);

        ColumnCubeMap columnCubeMap = new ColumnCubeMap();
        columnCubeMap.markLoaded(0);

        BlockGetterLightHeightmapGetterColumnCubeMapGetter lightHeightmapGetter = mock(BlockGetterLightHeightmapGetterColumnCubeMapGetter.class);
        when(lightHeightmapGetter.getLightHeightmap()).thenReturn(blockGetter.getHeightmap());
        when(lightHeightmapGetter.getCubeMap()).thenReturn(columnCubeMap);

        when(lightCubeGetter.getChunkForLighting(anyInt(), anyInt())).thenReturn(lightHeightmapGetter);

        CubeAccess cubeAccess = mock(CubeAccess.class);
        when(cubeAccess.getCubePos()).thenReturn(CubePos.of(0, 0, 0));
        when(cubeAccess.getStatus()).thenReturn(ChunkStatus.LIGHT);

        when(lightCubeGetter.getCubeForLighting(0, 0, 0)).thenReturn(blockGetter);

        SkyLightEngine levelLightEngine = new SkyLightEngine(lightCubeGetter);
        ((CubicLayerLightEngine) (Object) levelLightEngine).setCubic();

        levelLightEngine.updateSectionStatus(SectionPos.of(0, 0, 0), false);
        levelLightEngine.enableLightSources(new ChunkPos(0, 0), true);

        ((CubicSkyLightEngine) (Object) levelLightEngine).doSkyLightForCube(cubeAccess);

        validateSkyLighting(levelLightEngine, blockGetter, Set.of(SectionPos.of(0, 0, 0)), blockGetter.getHeightmap().inner);
    }

    @Test
    public void testSkyLightEngineFullCoverWithHole() {
        TestBlockGetter blockGetter = new TestBlockGetter();

        for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
            for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                for (int y = 0; y < SectionPos.SECTION_SIZE; y++) {
                    blockGetter.setBlockState(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }

        for (int x = 0; x < CubicConstants.DIAMETER_IN_BLOCKS; x++) {
            for (int z = 0; z < CubicConstants.DIAMETER_IN_BLOCKS; z++) {
                blockGetter.setBlockState(new BlockPos(x, 3, z), Blocks.STONE.defaultBlockState());
            }
        }

        blockGetter.setBlockState(new BlockPos(7, 3, 7), Blocks.AIR.defaultBlockState());

        LightCubeChunkGetter lightCubeGetter = mock(LightCubeChunkGetter.class);
        when(lightCubeGetter.getLevel()).thenReturn(blockGetter);

        ColumnCubeMap columnCubeMap = new ColumnCubeMap();
        columnCubeMap.markLoaded(0);

        BlockGetterLightHeightmapGetterColumnCubeMapGetter lightHeightmapGetter = mock(BlockGetterLightHeightmapGetterColumnCubeMapGetter.class);
        when(lightHeightmapGetter.getLightHeightmap()).thenReturn(blockGetter.getHeightmap());
        when(lightHeightmapGetter.getCubeMap()).thenReturn(columnCubeMap);

        when(lightCubeGetter.getChunkForLighting(anyInt(), anyInt())).thenReturn(lightHeightmapGetter);

        CubeAccess cubeAccess = mock(CubeAccess.class);
        when(cubeAccess.getCubePos()).thenReturn(CubePos.of(0, 0, 0));
        when(cubeAccess.getStatus()).thenReturn(ChunkStatus.LIGHT);

        when(lightCubeGetter.getCubeForLighting(0, 0, 0)).thenReturn(blockGetter);

        SkyLightEngine levelLightEngine = new SkyLightEngine(lightCubeGetter);
        ((CubicLayerLightEngine) (Object) levelLightEngine).setCubic();

        levelLightEngine.updateSectionStatus(SectionPos.of(0, 0, 0), false);
        levelLightEngine.enableLightSources(new ChunkPos(0, 0), true);

        ((CubicSkyLightEngine) (Object) levelLightEngine).doSkyLightForCube(cubeAccess);
        levelLightEngine.runUpdates(Integer.MAX_VALUE, true, true);

        validateSkyLighting(levelLightEngine, blockGetter, Set.of(SectionPos.of(0, 0, 0)), blockGetter.getHeightmap().inner);
    }
}

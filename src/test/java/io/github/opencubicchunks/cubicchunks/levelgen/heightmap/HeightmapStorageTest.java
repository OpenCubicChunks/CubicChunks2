package io.github.opencubicchunks.cubicchunks.levelgen.heightmap;

import static io.github.opencubicchunks.cubicchunks.testutils.Utils.forEachBlockColumnSurfaceTrackerNode;
import static io.github.opencubicchunks.cubicchunks.utils.Coords.*;
import static io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode.SCALE_0_NODE_HEIGHT;
import static io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode.WIDTH_BLOCKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.InterleavedHeightmapStorage;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerBranch;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class HeightmapStorageTest {
    @TempDir Path tempDirectory;

    /**
     * Tests unloading and loading a node with the same height in every position
     */
    @ParameterizedTest
    @ValueSource(bytes = { -1, 0, 1, 2, 3, 4, 5 })
    public void testReloadLeaf(byte heightmapType) throws IOException {
        // setup
        HeightmapStorage storage = new InterleavedHeightmapStorage(tempDirectory.toFile());
        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(1, null, heightmapType);
        SurfaceTrackerNodesTest.TestHeightmapNode32 testNode = new SurfaceTrackerNodesTest.TestHeightmapNode32(0, 1, 0);
        leaf.loadCube(0, 0, storage, testNode);

        // set heights
        Consumer<SurfaceTrackerNodesTest.HeightmapBlock> setHeight = block ->
            testNode.setBlock(block.x(), block.y() & (SCALE_0_NODE_HEIGHT - 1), block.z(), block.isOpaque());

        int blockY = Coords.cubeToMinBlock(testNode.y) + 6;
        forEachBlockColumnSurfaceTrackerNode((x, z) -> {
            setHeight.accept(new SurfaceTrackerNodesTest.HeightmapBlock(x, blockY, z, true));
        });

        // reload the node
        testNode.unloadNode(storage);
        storage.saveNode(0, 0, leaf);

        SurfaceTrackerLeaf loadedLeaf = (SurfaceTrackerLeaf) storage.loadNode(0, 0, null, heightmapType, 0, 1);

        // check positions are the same
        assertNotNull(loadedLeaf);
        forEachBlockColumnSurfaceTrackerNode((x, z) -> {
            int height = loadedLeaf.getHeight(x, z);
            assertEquals(blockY, height);
        });

        storage.close();
    }

    /**
     * Tests unloading and loading two nodes (section xz (0, 0) and (1, 0)) with different heights
     */
    @ParameterizedTest
    @ValueSource(bytes = { -1, 0, 1, 2, 3, 4, 5 })
    public void testReloadManyLeaves(byte heightmapType) throws IOException {
        // setup
        HeightmapStorage storage = new InterleavedHeightmapStorage(tempDirectory.toFile());
        int testSize = 128;
        SurfaceTrackerLeaf[] leaves = new SurfaceTrackerLeaf[testSize * testSize];
        SurfaceTrackerNodesTest.TestHeightmapNode16[] nodes = new SurfaceTrackerNodesTest.TestHeightmapNode16[testSize * testSize];
        for (int i = 0; i < leaves.length; i++) {
            leaves[i] = new SurfaceTrackerLeaf(1, null, heightmapType);
        }

        for (int nodeX = 0; nodeX < testSize; nodeX++) {
            for (int nodeZ = 0; nodeZ < testSize; nodeZ++) {
                SurfaceTrackerNodesTest.TestHeightmapNode16 node = new SurfaceTrackerNodesTest.TestHeightmapNode16(nodeX, 1, nodeZ);
                leaves[nodeX + nodeZ * testSize].loadCube(nodeX, nodeZ, storage, node);
                nodes[nodeX + nodeZ * testSize] = node;
            }
        }

        BiFunction<Integer, Integer, SurfaceTrackerNodesTest.TestHeightmapNode16> getNode = (sectionX, sectionZ) -> nodes[sectionX + sectionZ * testSize];
        // set heights
        Consumer<SurfaceTrackerNodesTest.HeightmapBlock> setHeight = block ->
            getNode.apply(blockToSection(block.x()), blockToSection(block.z())).setBlock(
                    block.x() & (WIDTH_BLOCKS - 1),
                    block.y() & (SCALE_0_NODE_HEIGHT - 1),
                    block.z() & (WIDTH_BLOCKS - 1),
                    block.isOpaque()
                );


        Random r = new Random(0);
        for (int blockX = 0; blockX < testSize * WIDTH_BLOCKS; blockX++) {
            for (int blockZ = 0; blockZ < testSize * WIDTH_BLOCKS; blockZ++) {
                setHeight.accept(new SurfaceTrackerNodesTest.HeightmapBlock(blockX, r.nextInt(SCALE_0_NODE_HEIGHT), blockZ, true));
            }
        }

        // reload the node
        for (int nodeX = 0; nodeX < testSize; nodeX++) {
            for (int nodeZ = 0; nodeZ < testSize; nodeZ++) {
                getNode.apply(nodeX, nodeZ).unloadNode(storage);
                storage.saveNode(nodeX, nodeZ, leaves[nodeX + nodeZ * testSize]);
            }
        }

        SurfaceTrackerLeaf[] loadedLeaves = new SurfaceTrackerLeaf[testSize * testSize];
        for (int nodeX = 0; nodeX < testSize; nodeX++) {
            for (int nodeZ = 0; nodeZ < testSize; nodeZ++) {
                SurfaceTrackerLeaf loadedLeaf = (SurfaceTrackerLeaf) storage.loadNode(nodeX, nodeZ, null, heightmapType, 0, 1);
                assertNotNull(loadedLeaf);
                loadedLeaves[nodeX + nodeZ * testSize] = loadedLeaf;
            }
        }

        // check positions are the same
        r = new Random(0);
        for (int blockX = 0; blockX < testSize * WIDTH_BLOCKS; blockX++) {
            for (int blockZ = 0; blockZ < testSize * WIDTH_BLOCKS; blockZ++) {
                SurfaceTrackerLeaf loadedLeaf = loadedLeaves[(blockX >> 4) + ((blockZ >> 4) * testSize)];
                int height = r.nextInt(SCALE_0_NODE_HEIGHT);
                assertEquals(32 + height, loadedLeaf.getHeight(blockX & WIDTH_BLOCKS - 1, blockZ & WIDTH_BLOCKS - 1));
            }
        }

        storage.close();
    }

    /**
     * Tests unloading and loading a node with incrementing heights in every position
     */
    @ParameterizedTest
    @ValueSource(bytes = { -1, 0, 1, 2, 3, 4, 5 })
    public void testReloadLeaf2(byte heightmapType) throws IOException {
        // setup
        HeightmapStorage storage = new InterleavedHeightmapStorage(tempDirectory.toFile());
        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(1, null, heightmapType);
        SurfaceTrackerNodesTest.TestHeightmapNode32 testNode = new SurfaceTrackerNodesTest.TestHeightmapNode32(0, 1, 0);
        leaf.loadCube(0, 0, storage, testNode);

        // set heights
        Consumer<SurfaceTrackerNodesTest.HeightmapBlock> setHeight = block ->
            testNode.setBlock(block.x(), block.y() & (SCALE_0_NODE_HEIGHT - 1), block.z(), block.isOpaque());

        int minBlockY = Coords.cubeToMinBlock(testNode.y);
        forEachBlockColumnSurfaceTrackerNode((x, z) -> {
            int blockY = minBlockY + ((x + z * WIDTH_BLOCKS) & SCALE_0_NODE_HEIGHT - 1);
            setHeight.accept(new SurfaceTrackerNodesTest.HeightmapBlock(x, blockY, z, true));
        });

        // reload the node
        testNode.unloadNode(storage);
        storage.saveNode(0, 0, leaf);

        SurfaceTrackerLeaf loadedLeaf = (SurfaceTrackerLeaf) storage.loadNode(0, 0, null, heightmapType, 0, 1);

        // check positions are the same
        assertNotNull(loadedLeaf);
        forEachBlockColumnSurfaceTrackerNode((x, z) -> {
            int blockY = minBlockY + ((x + z * WIDTH_BLOCKS) & SCALE_0_NODE_HEIGHT - 1);
            int height = loadedLeaf.getHeight(x, z);
            assertEquals(blockY, height);
        });

        storage.close();
    }

    @ParameterizedTest
    @ValueSource(bytes = { -1, 0, 1, 2, 3, 4, 5 })
    public void testReloadTree(byte heightmapType) throws IOException {
        // setup
        HeightmapStorage storage = new InterleavedHeightmapStorage(tempDirectory.toFile());
        SurfaceTrackerBranch root = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, heightmapType);
        SurfaceTrackerNodesTest.TestHeightmapNode32 testNode = new SurfaceTrackerNodesTest.TestHeightmapNode32(0, 0, 0);
        root.loadCube(0, 0, storage, testNode);

        // set heights
        Consumer<SurfaceTrackerNodesTest.HeightmapBlock> setHeight = block -> testNode.setBlock(block.x(), block.y() & (SCALE_0_NODE_HEIGHT - 1), block.z(),
            block.isOpaque());

        int blockY = 6;
        forEachBlockColumnSurfaceTrackerNode((x, z) -> {
            setHeight.accept(new SurfaceTrackerNodesTest.HeightmapBlock(x, blockY, z, true));
        });

        // reload the non MAX_SCALE nodes
        testNode.unloadNode(storage);

        SurfaceTrackerLeaf loadedLeaf = (SurfaceTrackerLeaf) storage.loadNode(0, 0, null, heightmapType, 0, 0);
        SurfaceTrackerBranch loadedBranch1 = (SurfaceTrackerBranch) storage.loadNode(0, 0, null, heightmapType, 1, 0);
        SurfaceTrackerBranch loadedBranch2 = (SurfaceTrackerBranch) storage.loadNode(0, 0, null, heightmapType, 2, 0);
        SurfaceTrackerBranch loadedBranch3 = (SurfaceTrackerBranch) storage.loadNode(0, 0, null, heightmapType, 3, 0);
        SurfaceTrackerBranch loadedBranch4 = (SurfaceTrackerBranch) storage.loadNode(0, 0, null, heightmapType, 4, 0);
        SurfaceTrackerBranch loadedBranch5 = (SurfaceTrackerBranch) storage.loadNode(0, 0, null, heightmapType, 5, 0);

        // check positions are the same
        assertNotNull(loadedLeaf);
        forEachBlockColumnSurfaceTrackerNode((x, z) -> assertEquals(blockY, loadedLeaf.getHeight(x, z)));

        assertNotNull(loadedBranch1);
        forEachBlockColumnSurfaceTrackerNode((x, z) -> assertEquals(blockY, loadedBranch1.getHeight(x, z)));

        assertNotNull(loadedBranch2);
        forEachBlockColumnSurfaceTrackerNode((x, z) -> assertEquals(blockY, loadedBranch2.getHeight(x, z)));

        assertNotNull(loadedBranch3);
        forEachBlockColumnSurfaceTrackerNode((x, z) -> assertEquals(blockY, loadedBranch3.getHeight(x, z)));

        assertNotNull(loadedBranch4);
        forEachBlockColumnSurfaceTrackerNode((x, z) -> assertEquals(blockY, loadedBranch4.getHeight(x, z)));

        assertNotNull(loadedBranch5);
        forEachBlockColumnSurfaceTrackerNode((x, z) -> assertEquals(blockY, loadedBranch5.getHeight(x, z)));

        storage.close();
    }
}

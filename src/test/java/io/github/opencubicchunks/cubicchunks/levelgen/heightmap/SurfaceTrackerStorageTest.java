package io.github.opencubicchunks.cubicchunks.levelgen.heightmap;

import static io.github.opencubicchunks.cubicchunks.testutils.Utils.forEachBlockColumnSurfaceTrackerNode;
import static io.github.opencubicchunks.cubicchunks.utils.Coords.*;
import static io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode.SCALE_0_NODE_HEIGHT;
import static io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode.WIDTH_BLOCKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;
import java.util.function.Consumer;

import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.PerNodeHeightmapStorage;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerBranch;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class SurfaceTrackerStorageTest {
    /**
     * Returns all HeightmapStorage implementations to test for all tests within this class
     */
    public static HeightmapStorage[] storageImplementationsToTest() throws IOException {
        return new HeightmapStorage[] {
            new PerNodeHeightmapStorage(Files.createTempDirectory("PerNodeHeightmapStorage").toFile())
        };
    }

    /**
     * Tests unloading and loading a node with the same height in every position
     */
    @ParameterizedTest
    @MethodSource("storageImplementationsToTest")
    public void testReloadLeaf(HeightmapStorage storage) {
        // setup
        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(1, null, (byte) 1);
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
        storage.unloadNode(0, 0, leaf);

        SurfaceTrackerLeaf loadedLeaf = (SurfaceTrackerLeaf) storage.loadNode(0, 0, null, (byte) 1, 0, 1);

        // check positions are the same
        assertNotNull(loadedLeaf);
        forEachBlockColumnSurfaceTrackerNode((x, z) -> {
            int height = loadedLeaf.getHeight(x, z);
            assertEquals(blockY, height);
        });
    }

    /**
     * Tests unloading and loading two nodes (section xz (0, 0) and (1, 0)) with different heights
     */
    @ParameterizedTest
    @MethodSource("storageImplementationsToTest")
    public void testReloadManyLeaves(HeightmapStorage storage) {
        // setup
        int TEST_SIZE = 128;
        SurfaceTrackerLeaf[] leaves = new SurfaceTrackerLeaf[TEST_SIZE * TEST_SIZE];
        SurfaceTrackerNodesTest.TestHeightmapNode16[] nodes = new SurfaceTrackerNodesTest.TestHeightmapNode16[TEST_SIZE * TEST_SIZE];
        for (int i = 0; i < leaves.length; i++) {
            leaves[i] = new SurfaceTrackerLeaf(1, null, (byte) 1);
        }

        for (int nodeX = 0; nodeX < TEST_SIZE; nodeX++) {
            for (int nodeZ = 0; nodeZ < TEST_SIZE; nodeZ++) {
                SurfaceTrackerNodesTest.TestHeightmapNode16 node = new SurfaceTrackerNodesTest.TestHeightmapNode16(nodeX, 1, nodeZ);
                leaves[nodeX + nodeZ * TEST_SIZE].loadCube(nodeX, nodeZ, storage, node);
                nodes[nodeX + nodeZ * TEST_SIZE] = node;
            }
        }

        // set heights
        Consumer<SurfaceTrackerNodesTest.HeightmapBlock> setHeight = block ->
            nodes[blockToSection(block.x()) + blockToSection(block.z()) * TEST_SIZE]
                .setBlock(
                    block.x() & (WIDTH_BLOCKS - 1),
                    block.y() & (SCALE_0_NODE_HEIGHT - 1),
                    block.z() & (WIDTH_BLOCKS - 1),
                    block.isOpaque()
                );


        Random r = new Random(0);
        for (int blockX = 0; blockX < TEST_SIZE * WIDTH_BLOCKS; blockX++) {
            for (int blockZ = 0; blockZ < TEST_SIZE * WIDTH_BLOCKS; blockZ++) {
                setHeight.accept(new SurfaceTrackerNodesTest.HeightmapBlock(blockX, r.nextInt(SCALE_0_NODE_HEIGHT), blockZ, true));
            }
        }

        // reload the node
        for (int nodeX = 0; nodeX < TEST_SIZE; nodeX++) {
            for (int nodeZ = 0; nodeZ < TEST_SIZE; nodeZ++) {
                storage.unloadNode(nodeX, nodeZ, leaves[nodeX + nodeZ * TEST_SIZE]);
            }
        }

        SurfaceTrackerLeaf[] loadedLeaves = new SurfaceTrackerLeaf[TEST_SIZE * TEST_SIZE];
        for (int nodeX = 0; nodeX < TEST_SIZE; nodeX++) {
            for (int nodeZ = 0; nodeZ < TEST_SIZE; nodeZ++) {
                SurfaceTrackerLeaf loadedLeaf = (SurfaceTrackerLeaf) storage.loadNode(nodeX, nodeZ, null, (byte) 1, 0, 1);
                assertNotNull(loadedLeaf);
                loadedLeaves[nodeX + nodeZ * TEST_SIZE] = loadedLeaf;
            }
        }

        // check positions are the same
        r = new Random(0);
        for (int blockX = 0; blockX < TEST_SIZE * WIDTH_BLOCKS; blockX++) {
            for (int blockZ = 0; blockZ < TEST_SIZE * WIDTH_BLOCKS; blockZ++) {
                SurfaceTrackerLeaf loadedLeaf = loadedLeaves[(blockX >> 4) + ((blockZ >> 4) * TEST_SIZE)];
                int height = r.nextInt(SCALE_0_NODE_HEIGHT);
                assertEquals(32 + height, loadedLeaf.getHeight(blockX & WIDTH_BLOCKS-1, blockZ & WIDTH_BLOCKS-1));
            }
        }
    }

    /**
     * Tests unloading and loading a node with incrementing heights in every position
     */
    @ParameterizedTest
    @MethodSource("storageImplementationsToTest")
    public void testReloadLeaf2(HeightmapStorage storage) {
        // setup
        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(1, null, (byte) 1);
        SurfaceTrackerNodesTest.TestHeightmapNode32 testNode = new SurfaceTrackerNodesTest.TestHeightmapNode32(0, 1, 0);
        leaf.loadCube(0, 0, storage, testNode);

        // set heights
        Consumer<SurfaceTrackerNodesTest.HeightmapBlock> setHeight = block ->
            testNode.setBlock(block.x(), block.y() & (SCALE_0_NODE_HEIGHT - 1), block.z(), block.isOpaque());

        int minBlockY = Coords.cubeToMinBlock(testNode.y);
        forEachBlockColumnSurfaceTrackerNode((x, z) -> {
            int blockY = minBlockY + ((x + z * WIDTH_BLOCKS) & SCALE_0_NODE_HEIGHT-1);
            setHeight.accept(new SurfaceTrackerNodesTest.HeightmapBlock(x, blockY, z, true));
        });

        // reload the node
        storage.unloadNode(0, 0, leaf);

        SurfaceTrackerLeaf loadedLeaf = (SurfaceTrackerLeaf) storage.loadNode(0, 0, null, (byte) 1, 0, 1);

        // check positions are the same
        assertNotNull(loadedLeaf);
        forEachBlockColumnSurfaceTrackerNode((x, z) -> {
            int blockY = minBlockY + ((x + z * WIDTH_BLOCKS) & SCALE_0_NODE_HEIGHT-1);
            int height = loadedLeaf.getHeight(x, z);
            assertEquals(blockY, height);
        });
    }

    @ParameterizedTest
    @MethodSource("storageImplementationsToTest")
    public void testReloadTree(HeightmapStorage storage) {
        // setup
        SurfaceTrackerBranch root = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
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

        SurfaceTrackerLeaf loadedLeaf = (SurfaceTrackerLeaf) storage.loadNode(0, 0, null, (byte) 0, 0, 0);
        SurfaceTrackerBranch loadedBranch1 = (SurfaceTrackerBranch) storage.loadNode(0, 0, null, (byte) 0, 1, 0);
        SurfaceTrackerBranch loadedBranch2 = (SurfaceTrackerBranch) storage.loadNode(0, 0, null, (byte) 0, 2, 0);
        SurfaceTrackerBranch loadedBranch3 = (SurfaceTrackerBranch) storage.loadNode(0, 0, null, (byte) 0, 3, 0);
        SurfaceTrackerBranch loadedBranch4 = (SurfaceTrackerBranch) storage.loadNode(0, 0, null, (byte) 0, 4, 0);
        SurfaceTrackerBranch loadedBranch5 = (SurfaceTrackerBranch) storage.loadNode(0, 0, null, (byte) 0, 5, 0);

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
    }
}

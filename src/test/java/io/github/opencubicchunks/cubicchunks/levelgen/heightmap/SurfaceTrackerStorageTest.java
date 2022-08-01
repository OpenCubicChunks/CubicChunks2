package io.github.opencubicchunks.cubicchunks.levelgen.heightmap;

import static io.github.opencubicchunks.cubicchunks.testutils.Utils.forEachBlockColumn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
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

    @ParameterizedTest
    @MethodSource("storageImplementationsToTest")
    public void testReloadLeaf(HeightmapStorage storage) {
        // setup
        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(1, null, (byte) 1);
        SurfaceTrackerNodesTest.TestHeightmapNode testNode = new SurfaceTrackerNodesTest.TestHeightmapNode(0, 1, 0);
        leaf.loadCube(0, 0, storage, testNode);

        // set heights
        Consumer<SurfaceTrackerNodesTest.HeightmapBlock> setHeight = block ->
            testNode.setBlock(block.x(), block.y() & (SurfaceTrackerLeaf.SCALE_0_NODE_HEIGHT - 1), block.z(), block.isOpaque());

        int blockY = Coords.cubeToMinBlock(testNode.y) + 6;
        forEachBlockColumn((x, z) -> {
            setHeight.accept(new SurfaceTrackerNodesTest.HeightmapBlock(x, blockY, z, true));
        });


        // reload the node
        storage.unloadNode(0, 0, leaf);

        SurfaceTrackerLeaf loadedLeaf = (SurfaceTrackerLeaf) storage.loadNode(0, 0, null, (byte) 1, 0, 1);

        // check positions are the same
        assertNotNull(loadedLeaf);
        forEachBlockColumn((x, z) -> assertEquals(loadedLeaf.getHeight(x, z), blockY));
    }

    @ParameterizedTest
    @MethodSource("storageImplementationsToTest")
    public void testReloadTree(HeightmapStorage storage) {
        // setup
        SurfaceTrackerBranch root = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        SurfaceTrackerNodesTest.TestHeightmapNode testNode = new SurfaceTrackerNodesTest.TestHeightmapNode(0, 0, 0);
        root.loadCube(0, 0, storage, testNode);

        // set heights
        Consumer<SurfaceTrackerNodesTest.HeightmapBlock> setHeight = block -> testNode.setBlock(block.x(), block.y() & (SurfaceTrackerLeaf.SCALE_0_NODE_HEIGHT - 1), block.z(),
            block.isOpaque());

        int blockY = 6;
        forEachBlockColumn((x, z) -> {
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
        forEachBlockColumn((x, z) -> assertEquals(blockY, loadedLeaf.getHeight(x, z)));

        assertNotNull(loadedBranch1);
        forEachBlockColumn((x, z) -> assertEquals(blockY, loadedBranch1.getHeight(x, z)));

        assertNotNull(loadedBranch2);
        forEachBlockColumn((x, z) -> assertEquals(blockY, loadedBranch2.getHeight(x, z)));

        assertNotNull(loadedBranch3);
        forEachBlockColumn((x, z) -> assertEquals(blockY, loadedBranch3.getHeight(x, z)));

        assertNotNull(loadedBranch4);
        forEachBlockColumn((x, z) -> assertEquals(blockY, loadedBranch4.getHeight(x, z)));

        assertNotNull(loadedBranch5);
        forEachBlockColumn((x, z) -> assertEquals(blockY, loadedBranch5.getHeight(x, z)));
    }
}

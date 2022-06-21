package io.github.opencubicchunks.cubicchunks.levelgen.heightmap;

import static io.github.opencubicchunks.cubicchunks.testutils.Utils.shouldFail;
import static io.github.opencubicchunks.cubicchunks.testutils.Utils.shouldSucceed;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.opencubicchunks.cubicchunks.levelgen.heightmap.HeightmapTreeNodesTest.NullHeightmapStorage;
import io.github.opencubicchunks.cubicchunks.levelgen.heightmap.HeightmapTreeNodesTest.TestCubeHeightAccess;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree.HeightmapTreeBranch;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree.HeightmapTreeLeaf;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree.HeightmapTreeNode;
import org.junit.jupiter.api.Test;

public class SurfaceTrackerBranchTest {
    /**
     * Tests that SurfaceTrackerBranch throws when given scales beyond its bounds
     * and that nothing is thrown within bounds
     */
    @Test
    public void testValidScaleBounds() {
        shouldFail(() -> new HeightmapTreeBranch(0, 0, null, (byte) 0),
            "SurfaceTrackerBranch didn't throw when given scale 0");
        shouldFail(() -> new HeightmapTreeBranch(HeightmapTreeNode.MAX_SCALE + 1, 0, null, (byte) 0),
            "SurfaceTrackerBranch didn't throw when given scale MAX_SCALE + 1");

        for (int i = 1; i <= HeightmapTreeNode.MAX_SCALE; i++) {
            int finalI = i;
            shouldSucceed(() -> new HeightmapTreeBranch(finalI, 0, null, (byte) 0),
                String.format("SurfaceTrackerBranch threw when given scale %d", i));
        }
    }

    /**
     * Tests that when a cube is inserted into root, it properly creates the appropriate leaf
     */
    @Test
    public void testLeafInsertionIntoRoot() {
        NullHeightmapStorage storage = new NullHeightmapStorage();
        HeightmapTreeBranch root = new HeightmapTreeBranch(HeightmapTreeNode.MAX_SCALE, 0, null, (byte) 0);
        root.loadCube(0, 0, storage, new TestCubeHeightAccess(0));

        HeightmapTreeLeaf leaf = root.getLeaf(0);
        assertNotNull(leaf, "Appropriate leaf was null after loading node into root");
        assertNotNull(leaf.getCube(), "Leaf had null node after node was loaded into root");
    }
}

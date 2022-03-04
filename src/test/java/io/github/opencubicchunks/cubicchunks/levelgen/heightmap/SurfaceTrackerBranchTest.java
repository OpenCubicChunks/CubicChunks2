package io.github.opencubicchunks.cubicchunks.levelgen.heightmap;

import static io.github.opencubicchunks.cubicchunks.testutils.Utils.shouldFail;
import static io.github.opencubicchunks.cubicchunks.testutils.Utils.shouldSucceed;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.opencubicchunks.cubicchunks.levelgen.heightmap.SurfaceTrackerNodesTest.NullHeightmapStorage;
import io.github.opencubicchunks.cubicchunks.levelgen.heightmap.SurfaceTrackerNodesTest.TestHeightmapNode;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerBranch;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode;
import org.junit.jupiter.api.Test;

public class SurfaceTrackerBranchTest {
    /**
     * Tests that SurfaceTrackerBranch throws when given scales beyond its bounds
     * and that nothing is thrown within bounds
     */
    @Test
    public void testValidScaleBounds() {
        shouldFail(() -> new SurfaceTrackerBranch(0, 0, null, (byte) 0),
            "SurfaceTrackerBranch didn't throw when given scale 0");
        shouldFail(() -> new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE + 1, 0, null, (byte) 0),
            "SurfaceTrackerBranch didn't throw when given scale 0");

        for (int i = 1; i <= SurfaceTrackerNode.MAX_SCALE; i++) {
            int finalI = i;
            shouldSucceed(() -> new SurfaceTrackerBranch(finalI, 0, null, (byte) 0),
                String.format("SurfaceTrackerBranch threw when given scale %d", i));
        }
    }

    /**
     * Tests that when a cube is inserted into a branch, it properly creates the appropriate leaf
     */
    @Test
    public void testLeafInsertion() {
        NullHeightmapStorage storage = new NullHeightmapStorage();
        SurfaceTrackerBranch branch = new SurfaceTrackerBranch(1, 0, null, (byte) 0);
        branch.loadCube(0, 0, storage, new TestHeightmapNode(0));
        SurfaceTrackerNode[] children = branch.getChildren();

        //Check that idx 0 child isn't null
        assertNotNull(children[0], "Branch had no appropriate leaf after loading cube into direct parent to leaf");
        assertNotNull(((SurfaceTrackerLeaf) children[0]).getNode(), "Leaf had null cube after cube was loaded into direct parent to leaf");
        //Check that all other children are null
        for (int i = 1; i < SurfaceTrackerNode.NODE_COUNT; i++) {
            assertNull(children[i], "Branch loaded inappropriate leaf after loading cube");
        }
    }

    /**
     * Tests that when a cube is inserted into root, it properly creates the appropriate leaf
     */
    @Test
    public void testLeafInsertionIntoRoot() {
        NullHeightmapStorage storage = new NullHeightmapStorage();
        SurfaceTrackerBranch root = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        root.loadCube(0, 0, storage, new TestHeightmapNode(0));

        SurfaceTrackerLeaf leaf = root.getMinScaleNode(0);
        assertNotNull(leaf, "Appropriate leaf was null after loading node into root");
        assertNotNull(leaf.getNode(), "Leaf had null node after node was loaded into root");
    }
}

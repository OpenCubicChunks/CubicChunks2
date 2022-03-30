package io.github.opencubicchunks.cubicchunks.levelgen.heightmap;

import static io.github.opencubicchunks.cubicchunks.testutils.Utils.forEachBlockColumn;
import static io.github.opencubicchunks.cubicchunks.testutils.Utils.shouldFail;
import static io.github.opencubicchunks.cubicchunks.testutils.Utils.shouldSucceed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.BitSet;
import java.util.Random;
import java.util.function.Consumer;

import io.github.opencubicchunks.cubicchunks.levelgen.heightmap.SurfaceTrackerNodesTest.HeightmapBlock;
import io.github.opencubicchunks.cubicchunks.levelgen.heightmap.SurfaceTrackerNodesTest.NullHeightmapStorage;
import io.github.opencubicchunks.cubicchunks.levelgen.heightmap.SurfaceTrackerNodesTest.TestHeightmapNode;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapNode;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerBranch;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class SurfaceTrackerLeafTest {

    /**
     * Tests that {@link HeightmapNode}s are correctly loaded and unloaded into and from a leaf
     */
    @Test
    public void testCubeLoadUnload() {

        TestHeightmapNode testNode = new TestHeightmapNode(0);
        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(testNode, null, (byte) 0);

        assertNotNull(leaf.getNode(), "Leaf had null HeightmapNode after being loaded");

        leaf.cubeUnloaded(0, 0, null);
        assertNull(leaf.getNode(), "Leaf had non-null HeightmapNode after being unloaded");
    }

    /**
     * Tests that a leaf properly nulls its fields when told to unload into a storage
     */
    @Test
    public void testLeafUnload() {
        NullHeightmapStorage storage = new NullHeightmapStorage();

        //Set up leaf and node with parent
        SurfaceTrackerBranch parent = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        parent.loadCube(0, 0, storage, new TestHeightmapNode(0), null);
        SurfaceTrackerLeaf leaf = parent.getMinScaleNode(0);

        //Unload the node
        leaf.cubeUnloaded(0, 0, storage);

        assertNull(leaf.getNode(), "Leaf had non-null HeightmapNode after being unloaded");
        assertNull(leaf.getParent(), "Leaf had non-null Parent after being unloaded");
    }

    /**
     * Tests that an invalid height (Integer.MIN_VALUE) is returned from a leaf with no heights set
     */
    @Test
    public void testNoValidHeights() {

        TestHeightmapNode testNode = new TestHeightmapNode(0);
        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(testNode, null, (byte) 0);

        Consumer<HeightmapBlock> setHeight = block -> testNode.setBlock(block.x(), block.y() & (SurfaceTrackerLeaf.SCALE_0_NODE_HEIGHT - 1), block.z(), block.isOpaque());

        forEachBlockColumn((x, z) -> {
            assertEquals(Integer.MIN_VALUE, leaf.getHeight(x, z), "SurfaceTrackerLeaf does not return invalid height when no block is present");

            setHeight.accept(new HeightmapBlock(x, 0, z, false));

            assertEquals(Integer.MIN_VALUE, leaf.getHeight(x, z), "SurfaceTrackerLeaf does not return invalid height when no block is present");

            setHeight.accept(new HeightmapBlock(x, 0, z, true));
            setHeight.accept(new HeightmapBlock(x, 0, z, false));

            assertEquals(Integer.MIN_VALUE, leaf.getHeight(x, z), "SurfaceTrackerLeaf does not return invalid height when no block is present");
        });
    }

    /**
     * Tests that setting of blocks inside the Leaf is correct
     */
    @Test
    public void testBasicFunctionality() {
        ReferenceHeightmap reference = new ReferenceHeightmap(0);

        TestHeightmapNode testNode = new TestHeightmapNode(0);
        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(testNode, null, (byte) 0);

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y(), block.isOpaque());
            testNode.setBlock(block.x(), block.y() & (SurfaceTrackerLeaf.SCALE_0_NODE_HEIGHT - 1), block.z(), block.isOpaque());
        };

        forEachBlockColumn((x, z) -> {
            reference.clear();

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, 4, z, true));

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, 7, z, true));
            setHeight.accept(new HeightmapBlock(x, 19, z, true));
            setHeight.accept(new HeightmapBlock(x, 21, z, false));

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, 4, z, false));
            setHeight.accept(new HeightmapBlock(x, 7, z, false));
            setHeight.accept(new HeightmapBlock(x, 19, z, false));

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));
            assertEquals(Integer.MIN_VALUE, leaf.getHeight(x, z));
        });
    }

    /**
     * Tests that setting of blocks inside the Leaf is correct for many leaf y values
     */
    @ParameterizedTest
    @ValueSource(ints = { -1024, -512, -256, -128, -64, -32, -16, -1, 0, 1, 16, 32, 64, 128, 256, 512, 1024 })
    public void testManyPositions(int nodeY) {
        ReferenceHeightmap reference = new ReferenceHeightmap(nodeY);

        TestHeightmapNode testNode = new TestHeightmapNode(nodeY);
        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(testNode, null, (byte) 0);

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y(), block.isOpaque());
            testNode.setBlock(block.x(), Coords.blockToLocal(block.y()), block.z(), block.isOpaque());
        };

        forEachBlockColumn((x, z) -> {
            reference.clear();

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, 4, z, true));

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, 7, z, true));
            setHeight.accept(new HeightmapBlock(x, 19, z, true));
            setHeight.accept(new HeightmapBlock(x, 21, z, false));

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, 4, z, false));
            setHeight.accept(new HeightmapBlock(x, 7, z, false));
            setHeight.accept(new HeightmapBlock(x, 19, z, false));

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));
            assertEquals(Integer.MIN_VALUE, leaf.getHeight(x, z));
        });
    }

    /**
     * Tests many leaf positions
     */
    @Test
    public void testSeededRandom() {
        ReferenceHeightmap reference = new ReferenceHeightmap(0);

        TestHeightmapNode testNode = new TestHeightmapNode(0);
        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(testNode, null, (byte) 0);

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y(), block.isOpaque());
            testNode.setBlock(block.x(), block.y() & (SurfaceTrackerLeaf.SCALE_0_NODE_HEIGHT - 1), block.z(), block.isOpaque());
        };

        Random r = new Random(123);

        forEachBlockColumn((x, z) -> {
            reference.clear();

            for (int i = 0; i < 1000; i++) {
                int randomY = r.nextInt(SurfaceTrackerLeaf.SCALE_0_NODE_HEIGHT);
                boolean randomOpaque = r.nextBoolean();
                setHeight.accept(new HeightmapBlock(x, randomY, z, randomOpaque));
                assertEquals(reference.getHighest(), leaf.getHeight(x, z));
            }
        });
    }

    /**
     * Tests that the leaf throws an exception when getting a position out of bounds,
     * and doesn't throw an exception when getting a position within bounds
     */
    @Test
    public void testBounds() {

        TestHeightmapNode node = new TestHeightmapNode(0);
        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(node, null, (byte) 0);

        Consumer<HeightmapBlock> setHeight = block -> {
            leaf.onSetBlock(block.x(), block.y(), block.z(), type -> block.isOpaque());
        };

        forEachBlockColumn((x, z) -> {
            //Test no exception within bounds
            shouldSucceed(() -> leaf.onSetBlock(0, 0, 0, type -> false),
                "SurfaceTrackerLeaf refused height inside itself");
            shouldSucceed(() -> leaf.onSetBlock(0, SurfaceTrackerLeaf.SCALE_0_NODE_HEIGHT - 1, 0, type -> false),
                "SurfaceTrackerLeaf refused height inside itself");

            //Test exception outside of bounds
            shouldFail(() -> leaf.onSetBlock(0, -1, 0, type -> false),
                "SurfaceTrackerLeaf accepted height below itself without throwing an exception");
            shouldFail(() -> leaf.onSetBlock(0, SurfaceTrackerLeaf.SCALE_0_NODE_HEIGHT, 0, type -> false),
                "SurfaceTrackerLeaf accepted height above itself without throwing an exception");
        });
    }

    public static class ReferenceHeightmap {
        int minNodeY;
        BitSet bitSet;

        public ReferenceHeightmap(int nodeY) {
            this.minNodeY = nodeY * SurfaceTrackerLeaf.SCALE_0_NODE_HEIGHT;
            this.bitSet = new BitSet(SurfaceTrackerLeaf.SCALE_0_NODE_HEIGHT);
        }

        void set(int blockY, boolean isOpaque) {
            this.bitSet.set(Coords.blockToLocal(blockY), isOpaque);
        }

        int getHighest() {
            int height = this.bitSet.previousSetBit(this.bitSet.length());
            return height == -1 ? Integer.MIN_VALUE : height + minNodeY;
        }

        void clear() {
            this.bitSet.clear();
        }
    }
}

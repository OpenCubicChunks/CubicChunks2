package io.github.opencubicchunks.cubicchunks.levelgen.heightmap;

import static io.github.opencubicchunks.cubicchunks.testutils.Utils.forEachBlockColumn;
import static io.github.opencubicchunks.cubicchunks.testutils.Utils.shouldFail;
import static io.github.opencubicchunks.cubicchunks.testutils.Utils.shouldSucceed;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.levelgen.heightmap.SurfaceTrackerNodesTest.HeightmapBlock;
import io.github.opencubicchunks.cubicchunks.levelgen.heightmap.SurfaceTrackerNodesTest.TestHeightmapNode;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerBranch;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode;
import org.junit.Test;

public class SurfaceTrackerLeafTest {
    /**
     * Tests that an invalid height (Integer.MIN_VALUE) is returned from a leaf with no heights set
     */
    @Test
    public void testNoValidHeights() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(0, null, (byte) 0);
        leaf.loadCube(0, 0, storage, new TestHeightmapNode(0));

        Consumer<HeightmapBlock> setHeight = block -> leaf.onSetBlock(block.x(), block.z(), block.z(), type -> block.isOpaque());

        forEachBlockColumn((x, z) -> {
            assertEquals("SurfaceTrackerLeaf does not return invalid height when no block is present", Integer.MIN_VALUE, leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, 0, z, false));

            assertEquals("SurfaceTrackerLeaf does not return invalid height when no block is present", Integer.MIN_VALUE, leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, 0, z, true));
            setHeight.accept(new HeightmapBlock(x, 0, z, false));

            assertEquals("SurfaceTrackerLeaf does not return invalid height when no block is present", Integer.MIN_VALUE, leaf.getHeight(x, z));
        });
    }

    /**
     * Tests that setting of blocks inside the Leaf is correct
     */
    @Test
    public void testBasicFunctionality() {
        ReferenceHeightmap reference = new ReferenceHeightmap(0);

        TestHeightmapStorage storage = new TestHeightmapStorage();

        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(0, null, (byte) 0);
        leaf.loadCube(0, 0, storage, new TestHeightmapNode(0));

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y(), block.isOpaque());
            leaf.onSetBlock(block.x(), block.y(), block.z(), type -> block.isOpaque());
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
     * Tests that setting of blocks inside the Leaf is correct for negative y values
     */
    @Test
    public void testNegativePositions() {
        ReferenceHeightmap reference = new ReferenceHeightmap(-1);

        TestHeightmapStorage storage = new TestHeightmapStorage();

        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(-1, null, (byte) 0);
        leaf.loadCube(0, 0, storage, new TestHeightmapNode(-1));

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y(), block.isOpaque());
            leaf.onSetBlock(block.x(), block.y(), block.z(), type -> block.isOpaque());
        };

        forEachBlockColumn((x, z) -> {
            reference.clear();

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, -4, z, true));

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, -7, z, true));
            setHeight.accept(new HeightmapBlock(x, -19, z, true));
            setHeight.accept(new HeightmapBlock(x, -21, z, false));

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, -4, z, false));
            setHeight.accept(new HeightmapBlock(x, -7, z, false));
            setHeight.accept(new HeightmapBlock(x, -19, z, false));

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));
            assertEquals(Integer.MIN_VALUE, leaf.getHeight(x, z));
        });
    }

    /**
     * Tests many leaf positions
     */
    @Test
    public void testSeededRandom() {
        ReferenceHeightmap reference = new ReferenceHeightmap(-1);

        TestHeightmapStorage storage = new TestHeightmapStorage();

        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(-1, null, (byte) 0);
        leaf.loadCube(0, 0, storage, new TestHeightmapNode(-1));

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y(), block.isOpaque());
            leaf.onSetBlock(block.x(), block.y(), block.z(), type -> block.isOpaque());
        };

        forEachBlockColumn((x, z) -> {
            reference.clear();

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, -4, z, true));

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, -7, z, true));
            setHeight.accept(new HeightmapBlock(x, -19, z, true));
            setHeight.accept(new HeightmapBlock(x, -21, z, false));

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, -4, z, false));
            setHeight.accept(new HeightmapBlock(x, -7, z, false));
            setHeight.accept(new HeightmapBlock(x, -19, z, false));

            assertEquals(reference.getHighest(), leaf.getHeight(x, z));
            assertEquals(Integer.MIN_VALUE, leaf.getHeight(x, z));
        });
    }

    /**
     * Tests that the leaf throws an exception when getting a position out of bounds,
     * and doesn't throw an exception when getting a position within bounds
     */
    @Test
    public void testBounds() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        SurfaceTrackerLeaf leaf = new SurfaceTrackerLeaf(0, null, (byte) 0);
        leaf.loadCube(0, 0, storage, new TestHeightmapNode(0));

        Consumer<HeightmapBlock> setHeight = block -> {
            leaf.onSetBlock(block.x(), block.y(), block.z(), type -> block.isOpaque());
        };

        forEachBlockColumn((x, z) -> {
            //Test no exception within bounds
            shouldSucceed(() -> setHeight.accept(new HeightmapBlock(0, 0, 0, false)),
                "SurfaceTrackerLeaf refused height inside itself");
            shouldSucceed(() -> setHeight.accept(new HeightmapBlock(0, SurfaceTrackerLeaf.SCALE_0_NODE_HEIGHT - 1, 0, false)),
                "SurfaceTrackerLeaf refused height inside itself");

            //Test exception outside of bounds
            shouldFail(() -> setHeight.accept(new HeightmapBlock(0, -1, 0, false)),
                "SurfaceTrackerLeaf accepted height below itself without throwing an exception");
            shouldFail(() -> setHeight.accept(new HeightmapBlock(0, SurfaceTrackerLeaf.SCALE_0_NODE_HEIGHT, 0, false)),
                "SurfaceTrackerLeaf accepted height above itself without throwing an exception");
        });
    }

    static class TestHeightmapStorage implements HeightmapStorage {
        @Override public void unloadNode(SurfaceTrackerNode node) {
            if (node.getScale() == 0) {
                ((SurfaceTrackerLeaf) node).setNode(null);
            } else {
                Arrays.fill(((SurfaceTrackerBranch) node).getChildren(), null);
            }
            node.setParent(null);
        }

        @Nullable @Override public SurfaceTrackerNode loadNode(SurfaceTrackerBranch parent, byte heightmapType, int scale, int scaledY) {
            return null;
        }
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

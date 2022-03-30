package io.github.opencubicchunks.cubicchunks.levelgen.heightmap;

import static io.github.opencubicchunks.cubicchunks.testutils.Utils.forEachBlockColumn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapNode;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerBranch;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

public class SurfaceTrackerNodesTest {

    /**
     * Trivially tests that SurfaceTrackerNode works
     * Places blocks:
     * 5 -> 6 (transparent) -> 3
     * assert height is 5
     * 13
     * assert height is 13
     */
    @Test
    public void sanityTest() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        ReferenceHeightmap reference = new ReferenceHeightmap(2048);
        
        TestHeightmapNode node0 = new TestHeightmapNode(0);
        SurfaceTrackerBranch[] roots = new SurfaceTrackerBranch[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        }

        for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
            for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].loadCube(localSectionX, localSectionZ, storage, node0, null);
            }
        }

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y, block.isOpaque);
            node0.setBlock(block.x, block.y, block.z, block.isOpaque);
        };

        forEachBlockColumn((x, z) -> {
            int localSectionX = Coords.blockToCubeLocalSection(x);
            int localSectionZ = Coords.blockToCubeLocalSection(z);

            reference.clear();

            setHeight.accept(new HeightmapBlock(x, 5, z, true));
            setHeight.accept(new HeightmapBlock(x, 6, z, false));
            setHeight.accept(new HeightmapBlock(x, 3, z, true));

            assertEquals(reference.getHighest(), roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, 13, z, true));

            assertEquals(reference.getHighest(), roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].getHeight(x, z));
        });
    }

    /**
     * Tests that an invalid height (Integer.MIN_VALUE) is returned from a heightmap with no heights set
     */
    @Test
    public void testNoValidHeights() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        ReferenceHeightmap reference = new ReferenceHeightmap(2048);

        TestHeightmapNode node0 = new TestHeightmapNode(0);
        SurfaceTrackerBranch[] roots = new SurfaceTrackerBranch[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new SurfaceTrackerBranch(SurfaceTrackerBranch.MAX_SCALE, 0, null, (byte) 0);
        }

        for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
            for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].loadCube(localSectionX, localSectionZ, storage, node0, null);
            }
        }

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y, block.isOpaque);
            node0.setBlock(block.x, block.y, block.z, block.isOpaque);
        };

        forEachBlockColumn((x, z) -> {
            int localSectionX = Coords.blockToCubeLocalSection(x);
            int localSectionZ = Coords.blockToCubeLocalSection(z);

            reference.clear();

            assertEquals(Integer.MIN_VALUE, roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, 0, z, false));

            assertEquals(Integer.MIN_VALUE, roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, 0, z, true));
            setHeight.accept(new HeightmapBlock(x, 0, z, false));

            assertEquals(Integer.MIN_VALUE, roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].getHeight(x, z));
        });
    }

    @Test
    public void seededRandom() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        int maxCoordinate = 2048;

        ReferenceHeightmap reference = new ReferenceHeightmap(maxCoordinate);

        Map<Integer, TestHeightmapNode> nodes = new HashMap<>();
        SurfaceTrackerBranch[] roots = new SurfaceTrackerBranch[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        }

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y, block.isOpaque);
            nodes.computeIfAbsent(block.y >> SurfaceTrackerNode.SCALE_0_NODE_BITS, y -> {
                TestHeightmapNode node = new TestHeightmapNode(y);
                for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                    for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                        roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].loadCube(localSectionX, localSectionZ, storage, node,null);
                    }
                }
                return node;
            }).setBlock(block.x, block.y & (SurfaceTrackerNode.SCALE_0_NODE_HEIGHT - 1), block.z, block.isOpaque);
        };

        forEachBlockColumn((x, z) -> {
            reference.clear();

            Random r = new Random(123);

            for (int i = 0; i < 1000; i++) {
                int y = r.nextInt(maxCoordinate * 2) - maxCoordinate;
                setHeight.accept(new HeightmapBlock(x, y, z, r.nextBoolean()));
            }
            assertEquals(reference.getHighest(), roots[Coords.blockToCubeLocalSection(x) + CubeAccess.DIAMETER_IN_SECTIONS * Coords.blockToCubeLocalSection(z)].getHeight(x, z));
        });
    }

    /**
     * Tests whether after unloading the {@link SurfaceTrackerNode} node tree is correct
     * Does not test heights.
     */
    @Test
    public void simpleUnloadTree1() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        Map<Integer, TestHeightmapNode> nodes = new HashMap<>();
        SurfaceTrackerBranch[] roots = new SurfaceTrackerBranch[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        }

        Function<Integer, HeightmapNode> loadNode = y -> nodes.computeIfAbsent(y, yPos -> {
            TestHeightmapNode node = new TestHeightmapNode(yPos);
            for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                    roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].loadCube(localSectionX, localSectionZ, storage, node, null);
                }
            }
            return node;
        });


        loadNode.apply(-1);
        loadNode.apply(0);

        nodes.get(-1).unloadNode(storage);

        for (SurfaceTrackerNode root : roots) {
            assertNull(root.getMinScaleNode(-1), "Did not unload non-required node?!");
            assertNotNull(root.getMinScaleNode(0), "Unloaded required node?!");

            verifyHeightmapTree((SurfaceTrackerBranch) root, nodes.entrySet());
        }
    }

    /**
     * Tests whether after unloading the {@link SurfaceTrackerNode} node tree is correct
     * Does not test heights.
     */
    @Test
    public void simpleUnloadTree2() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        Map<Integer, TestHeightmapNode> nodes = new HashMap<>();
        SurfaceTrackerBranch[] roots = new SurfaceTrackerBranch[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        }

        Function<Integer, HeightmapNode> loadNode = y -> nodes.computeIfAbsent(y, yPos -> {
            TestHeightmapNode node = new TestHeightmapNode(yPos);
            for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                    roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].loadCube(localSectionX, localSectionZ, storage, node, null);
                }
            }
            return node;
        });
        Function<Integer, HeightmapNode> unloadNode = y -> {
            TestHeightmapNode removed = nodes.remove(y);
            if (removed != null) {
                removed.unloadNode(storage);
            }
            return removed;
        };


        for (int i = -1; i < SurfaceTrackerNode.NODE_COUNT; i++) {
            loadNode.apply(i);
        }
        loadNode.apply(SurfaceTrackerNode.NODE_COUNT * 2);

        unloadNode.apply(-1);

        for (SurfaceTrackerNode root : roots) {
            for (TestHeightmapStorage.PackedTypeScaleScaledY packed : storage.saved.keySet()) {
                if (packed.scale == 0) {
                    assertNull(root.getMinScaleNode(packed.scaledY), "Did not unload non-required node?!");
                }
            }

            for (Integer integer : nodes.keySet()) {
                assertNotNull(root.getMinScaleNode(integer), "Unloaded required node?!");
            }

            verifyHeightmapTree((SurfaceTrackerBranch) root, nodes.entrySet());
        }
    }

    /**
     * Tests whether after unloading the {@link SurfaceTrackerNode} node tree is correct
     * Does not test heights.
     */
    @Test
    public void seededRandomUnloadTree() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        Map<Integer, TestHeightmapNode> nodes = new HashMap<>();
        SurfaceTrackerBranch[] roots = new SurfaceTrackerBranch[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        }

        Function<Integer, HeightmapNode> loadNode = y -> nodes.computeIfAbsent(y, yPos -> {
            TestHeightmapNode node = new TestHeightmapNode(yPos);
            for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                    roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].loadCube(localSectionX, localSectionZ, storage, node, null);
                }
            }
            return node;
        });
        Function<Integer, HeightmapNode> unloadNode = y -> {
            TestHeightmapNode removed = nodes.remove(y);
            if (removed != null) {
                removed.unloadNode(storage);
            }
            return removed;
        };


        Random r = new Random(123);
        for (int i = 0; i < 10000; i++) {
            loadNode.apply(r.nextInt(2048) - 1024);
        }
        for (int i = 0; i < 10000; i++) {
            unloadNode.apply(r.nextInt(2048) - 1024);
        }

        for (SurfaceTrackerNode root : roots) {
            for (TestHeightmapStorage.PackedTypeScaleScaledY packed : storage.saved.keySet()) {
                if (packed.scale == 0) {
                    assertNull(root.getMinScaleNode(packed.scaledY), "Did not unload non-required node?!");
                }
            }

            for (Integer integer : nodes.keySet()) {
                assertNotNull(root.getMinScaleNode(integer), "Unloaded required node?!");
            }

            verifyHeightmapTree((SurfaceTrackerBranch) root, nodes.entrySet());
        }
    }

    /**
     * Tests that after unloading and reloading, both the unloaded node and its parent are unchanged
     */
    @Test
    public void testUnloadReload() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        Map<Integer, TestHeightmapNode> nodes = new HashMap<>();
        SurfaceTrackerBranch root = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);

        Function<Integer, TestHeightmapNode> loadNode = y -> nodes.computeIfAbsent(y, yPos -> {
            TestHeightmapNode node = new TestHeightmapNode(yPos);
            root.loadCube(0, 0, storage, node,null);
            return node;
        });
        Function<Integer, TestHeightmapNode> unloadNode = y -> {
            TestHeightmapNode removed = nodes.remove(y);
            if (removed != null) {
                removed.unloadNode(storage);
            }
            return removed;
        };

        loadNode.apply(0);
        loadNode.apply(1);
        SurfaceTrackerLeaf node0 = root.getMinScaleNode(0);

        SurfaceTrackerBranch parent = node0.getParent();
        HeightmapNode cubeNode = node0.getNode();

        unloadNode.apply(0);

        root.loadCube(0, 0, storage, cubeNode, null);

        SurfaceTrackerLeaf reloadedNode = root.getMinScaleNode(0);

        assertEquals(parent, reloadedNode.getParent());
        assertEquals(cubeNode, reloadedNode.getNode());
    }

    /**
     * Very crude implementation of heightmap checking.
     * Iterates up from cubes adding all required nodes to a list
     * Adds all required ancestors to a set, iterates down from root verifying that all nodes are contained in the required set
     */
    private void verifyHeightmapTree(SurfaceTrackerBranch root, Set<Map.Entry<Integer, TestHeightmapNode>> entries) {
        //Collect all leaves in the cubemap
        List<SurfaceTrackerLeaf> requiredLeaves = new ArrayList<>();
        for (Map.Entry<Integer, TestHeightmapNode> entry : entries) {
            SurfaceTrackerLeaf leaf = root.getMinScaleNode(entry.getKey());
            if (leaf != null) {
                //Leaves can be null when a protocube is marked as loaded in the cubemap, but hasn't yet been added to the global heightmap
                requiredLeaves.add(leaf);
            }
        }

        LongSet requiredPositions = new LongOpenHashSet();
        //Root will not be added if there are no leaves in the tree, so we add it here
        requiredPositions.add(ChunkPos.asLong(root.getScale(), root.getScaledY()));
        //Collect all positions that are required to be loaded
        for (SurfaceTrackerLeaf leaf : requiredLeaves) {
            SurfaceTrackerNode node = leaf;
            while (node != null) {
                requiredPositions.add(ChunkPos.asLong(node.getScale(), node.getScaledY()));

                if (node instanceof SurfaceTrackerBranch branch) {
                    for (SurfaceTrackerNode child : branch.getChildren()) {
                        if (child != null) {
                            requiredPositions.add(ChunkPos.asLong(child.getScale(), child.getScaledY()));
                        }
                    }
                }

                SurfaceTrackerBranch parent = node.getParent();
                if (node.getScale() < SurfaceTrackerNode.MAX_SCALE && parent == null) {
                    throw new IllegalStateException("Detached heightmap branch exists?!");
                }
                node = parent;
            }
        }

        //Verify that heightmap meets requirements (all parents are loaded, and their direct children)
        verifyAllNodesInRequiredSet(root, requiredPositions);
    }

    private static void verifyAllNodesInRequiredSet(SurfaceTrackerBranch branch, LongSet requiredNodes) {
        for (SurfaceTrackerNode child : branch.getChildren()) {
            if (child != null) {
                if (!requiredNodes.contains(ChunkPos.asLong(child.getScale(), child.getScaledY()))) {
                    throw new IllegalStateException("Heightmap borken");
                }

                if (branch.getScale() != 1) {
                    verifyAllNodesInRequiredSet((SurfaceTrackerBranch) child, requiredNodes);
                }
            }
        }
    }

    /**
     * Test heightmap storage that always returns null on load, and correctly nulls fields on unload
     */
    static class NullHeightmapStorage implements HeightmapStorage {
        @Override public void unloadNode(SurfaceTrackerNode node) {
            throw new RuntimeException("Not implemented");
//            if (node.getScale() == 0) {
//                ((SurfaceTrackerLeaf) node).setNode(null);
//            } else {
//                Arrays.fill(((SurfaceTrackerBranch) node).getChildren(), null);
//            }
//            node.setParent(null);
        }

        @Nullable @Override public SurfaceTrackerNode loadNode(SurfaceTrackerBranch parent, byte heightmapType, int scale, int scaledY) {
            return null;
        }
    }

    /**
     * Test heightmap storage that unloads into a hashmap
     */
    static class TestHeightmapStorage implements HeightmapStorage {
        Object2ReferenceMap<PackedTypeScaleScaledY, SurfaceTrackerNode> saved = new Object2ReferenceOpenHashMap<>();

        @Override public void unloadNode(SurfaceTrackerNode node) {
            throw new RuntimeException("Not implemented");
//            if (node.getScale() == 0) {
//                ((SurfaceTrackerLeaf) node).setNode(null);
//            } else {
//                Arrays.fill(((SurfaceTrackerBranch) node).getChildren(), null);
//            }
//            node.setParent(null);
//
//            saved.put(new PackedTypeScaleScaledY(node.getRawType(), node.getScale(), node.getScaledY()), node);
        }
        @Override public SurfaceTrackerNode loadNode(SurfaceTrackerBranch parent, byte heightmapType, int scale, int scaledY) {
            SurfaceTrackerNode removed = saved.remove(new PackedTypeScaleScaledY(heightmapType, scale, scaledY));
            if (removed != null) {
                removed.setParent(parent);
            }
            return removed;
        }
        record PackedTypeScaleScaledY(byte heightmapType, int scale, int scaledY) { }
    }

    public static class TestHeightmapNode implements HeightmapNode {
        final int y;

        final BitSet[][] blockBitsets = new BitSet[CubeAccess.DIAMETER_IN_BLOCKS][CubeAccess.DIAMETER_IN_BLOCKS];
        final SurfaceTrackerLeaf[] sections = new SurfaceTrackerLeaf[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];

        TestHeightmapNode(int nodeY) {
            for (BitSet[] bitSets : blockBitsets) {
                for (int j = 0, bitSetsLength = bitSets.length; j < bitSetsLength; j++) {
                    bitSets[j] = new BitSet(SurfaceTrackerNode.SCALE_0_NODE_HEIGHT);
                }
            }
            this.y = nodeY;
        }

        void setBlock(int x, int localY, int z, boolean isOpaque) {
            assert localY >= 0 && localY < SurfaceTrackerNode.SCALE_0_NODE_HEIGHT;
            this.blockBitsets[z][x].set(localY, isOpaque);
            SurfaceTrackerLeaf section = this.sections[(x >> 4) + ((z >> 4) * CubeAccess.DIAMETER_IN_SECTIONS)];

            if (section == null) {
                fail(String.format("No section loaded for position (%d, %d, %d)", x, Coords.blockToCube(this.y) + localY, z));
            }
            section.onSetBlock(x, (this.y << SurfaceTrackerNode.SCALE_0_NODE_BITS) + localY, z, heightmapType -> isOpaque);
        }

        @Override public void sectionLoaded(@Nonnull SurfaceTrackerLeaf leaf, int localSectionX, int localSectionZ) {
            this.sections[localSectionX + localSectionZ * CubeAccess.DIAMETER_IN_SECTIONS] = leaf;
        }

        @Override public void unloadNode(@Nonnull HeightmapStorage storage) {
            SurfaceTrackerLeaf[] nodes = this.sections;
            for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                    int i = localSectionX + localSectionZ * CubeAccess.DIAMETER_IN_SECTIONS;
                    if (nodes[i] != null) {
                        nodes[i].cubeUnloaded(localSectionX, localSectionZ, storage);
                        nodes[i] = null;
                    }
                }
            }
        }

        @Override public int getHighest(int x, int z, byte heightmapType) {
            x &= CubeAccess.DIAMETER_IN_BLOCKS - 1;
            z &= CubeAccess.DIAMETER_IN_BLOCKS - 1;

            int highestY = this.blockBitsets[z][x].previousSetBit(this.blockBitsets[z][x].length());
            return highestY == -1 ? Integer.MIN_VALUE : highestY + (this.y << SurfaceTrackerNode.SCALE_0_NODE_BITS);
        }

        @Override public int getNodeY() {
            return this.y;
        }
    }

    public record HeightmapBlock (int x, int y, int z, boolean isOpaque) { }
    public static class ReferenceHeightmap {
        BitSet bitSet;
        final int offset;

        public ReferenceHeightmap(int maxCoordinate) {
            this.bitSet = new BitSet(maxCoordinate * 2);
            this.offset = maxCoordinate;
        }

        void set(int y, boolean isOpaque) {
            this.bitSet.set(y + offset, isOpaque);
        }

        int getHighest() {
            return this.bitSet.previousSetBit(this.bitSet.length()) - offset;
        }

        void clear() {
            this.bitSet.clear();
        }
    }
}

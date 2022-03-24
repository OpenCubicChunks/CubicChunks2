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
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.CubeHeightAccess;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree.HeightmapTreeBranch;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree.HeightmapTreeLeaf;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree.HeightmapTreeNode;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

public class HeightmapTreeNodesTest {

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
        
        TestCubeHeightAccess node0 = new TestCubeHeightAccess(0);
        HeightmapTreeNode[] roots = new HeightmapTreeNode[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new HeightmapTreeBranch(HeightmapTreeNode.MAX_SCALE, 0, null, (byte) 0);
        }

        for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
            for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].loadCube(localSectionX, localSectionZ, storage, node0);
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

        TestCubeHeightAccess node0 = new TestCubeHeightAccess(0);
        HeightmapTreeNode[] roots = new HeightmapTreeNode[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new HeightmapTreeBranch(HeightmapTreeNode.MAX_SCALE, 0, null, (byte) 0);
        }

        for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
            for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].loadCube(localSectionX, localSectionZ, storage, node0);
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

        Map<Integer, TestCubeHeightAccess> nodes = new HashMap<>();
        HeightmapTreeNode[] roots = new HeightmapTreeNode[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new HeightmapTreeBranch(HeightmapTreeNode.MAX_SCALE, 0, null, (byte) 0);
        }

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y, block.isOpaque);
            nodes.computeIfAbsent(block.y >> HeightmapTreeNode.SCALE_0_NODE_BITS, y -> {
                TestCubeHeightAccess node = new TestCubeHeightAccess(y);
                for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                    for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                        roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].loadCube(localSectionX, localSectionZ, storage, node);
                    }
                }
                return node;
            }).setBlock(block.x, block.y & (HeightmapTreeNode.SCALE_0_NODE_HEIGHT - 1), block.z, block.isOpaque);
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
     * Tests whether after unloading the {@link HeightmapTreeNode} node tree is correct
     * Does not test heights.
     */
    @Test
    public void simpleUnloadTree1() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        Map<Integer, TestCubeHeightAccess> nodes = new HashMap<>();
        HeightmapTreeNode[] roots = new HeightmapTreeNode[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new HeightmapTreeBranch(HeightmapTreeNode.MAX_SCALE, 0, null, (byte) 0);
        }

        Function<Integer, CubeHeightAccess> loadNode = y -> nodes.computeIfAbsent(y, yPos -> {
            TestCubeHeightAccess node = new TestCubeHeightAccess(yPos);
            for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                    roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].loadCube(localSectionX, localSectionZ, storage, node);
                }
            }
            return node;
        });


        loadNode.apply(-1);
        loadNode.apply(0);

        nodes.get(-1).unloadNode(storage);

        for (HeightmapTreeNode root : roots) {
            assertNull(root.getLeaf(-1), "Did not unload non-required node?!");
            assertNotNull(root.getLeaf(0), "Unloaded required node?!");

            verifyHeightmapTree((HeightmapTreeBranch) root, nodes.entrySet());
        }
    }

    /**
     * Tests whether after unloading the {@link HeightmapTreeNode} node tree is correct
     * Does not test heights.
     */
    @Test
    public void simpleUnloadTree2() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        Map<Integer, TestCubeHeightAccess> nodes = new HashMap<>();
        HeightmapTreeNode[] roots = new HeightmapTreeNode[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new HeightmapTreeBranch(HeightmapTreeNode.MAX_SCALE, 0, null, (byte) 0);
        }

        Function<Integer, CubeHeightAccess> loadNode = y -> nodes.computeIfAbsent(y, yPos -> {
            TestCubeHeightAccess node = new TestCubeHeightAccess(yPos);
            for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                    roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].loadCube(localSectionX, localSectionZ, storage, node);
                }
            }
            return node;
        });
        Function<Integer, CubeHeightAccess> unloadNode = y -> {
            TestCubeHeightAccess removed = nodes.remove(y);
            if (removed != null) {
                removed.unloadNode(storage);
            }
            return removed;
        };


        for (int i = -1; i < HeightmapTreeNode.NODE_COUNT; i++) {
            loadNode.apply(i);
        }
        loadNode.apply(HeightmapTreeNode.NODE_COUNT * 2);

        unloadNode.apply(-1);

        for (HeightmapTreeNode root : roots) {
            for (TestHeightmapStorage.PackedTypeScaleScaledY packed : storage.saved.keySet()) {
                if (packed.scale == 0) {
                    assertNull(root.getLeaf(packed.scaledY), "Did not unload non-required node?!");
                }
            }

            for (Integer integer : nodes.keySet()) {
                assertNotNull(root.getLeaf(integer), "Unloaded required node?!");
            }

            verifyHeightmapTree((HeightmapTreeBranch) root, nodes.entrySet());
        }
    }

    /**
     * Tests whether after unloading the {@link HeightmapTreeNode} node tree is correct
     * Does not test heights.
     */
    @Test
    public void seededRandomUnloadTree() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        Map<Integer, TestCubeHeightAccess> nodes = new HashMap<>();
        HeightmapTreeNode[] roots = new HeightmapTreeNode[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new HeightmapTreeBranch(HeightmapTreeNode.MAX_SCALE, 0, null, (byte) 0);
        }

        Function<Integer, CubeHeightAccess> loadNode = y -> nodes.computeIfAbsent(y, yPos -> {
            TestCubeHeightAccess node = new TestCubeHeightAccess(yPos);
            for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                    roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ].loadCube(localSectionX, localSectionZ, storage, node);
                }
            }
            return node;
        });
        Function<Integer, CubeHeightAccess> unloadNode = y -> {
            TestCubeHeightAccess removed = nodes.remove(y);
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

        for (HeightmapTreeNode root : roots) {
            for (TestHeightmapStorage.PackedTypeScaleScaledY packed : storage.saved.keySet()) {
                if (packed.scale == 0) {
                    assertNull(root.getLeaf(packed.scaledY), "Did not unload non-required node?!");
                }
            }

            for (Integer integer : nodes.keySet()) {
                assertNotNull(root.getLeaf(integer), "Unloaded required node?!");
            }

            verifyHeightmapTree((HeightmapTreeBranch) root, nodes.entrySet());
        }
    }

    /**
     * Tests that after unloading and reloading, both the unloaded node and its parent are unchanged
     */
    @Test
    public void testUnloadReload() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        Map<Integer, TestCubeHeightAccess> nodes = new HashMap<>();
        HeightmapTreeNode root = new HeightmapTreeBranch(HeightmapTreeNode.MAX_SCALE, 0, null, (byte) 0);

        Function<Integer, TestCubeHeightAccess> loadNode = y -> nodes.computeIfAbsent(y, yPos -> {
            TestCubeHeightAccess node = new TestCubeHeightAccess(yPos);
            root.loadCube(0, 0, storage, node);
            return node;
        });
        Function<Integer, TestCubeHeightAccess> unloadNode = y -> {
            TestCubeHeightAccess removed = nodes.remove(y);
            if (removed != null) {
                removed.unloadNode(storage);
            }
            return removed;
        };

        loadNode.apply(0);
        loadNode.apply(1);
        HeightmapTreeLeaf node0 = root.getLeaf(0);

        HeightmapTreeBranch parent = node0.getParent();
        CubeHeightAccess cubeNode = node0.getCube();

        unloadNode.apply(0);

        root.loadCube(0, 0, storage, cubeNode);

        HeightmapTreeLeaf reloadedNode = root.getLeaf(0);

        assertEquals(parent, reloadedNode.getParent());
        assertEquals(cubeNode, reloadedNode.getCube());
    }

    /**
     * Very crude implementation of heightmap checking.
     * Iterates up from cubes adding all required nodes to a list
     * Adds all required ancestors to a set, iterates down from root verifying that all nodes are contained in the required set
     */
    private void verifyHeightmapTree(HeightmapTreeBranch root, Set<Map.Entry<Integer, TestCubeHeightAccess>> entries) {
        //Collect all leaves in the cubemap
        List<HeightmapTreeLeaf> requiredLeaves = new ArrayList<>();
        for (Map.Entry<Integer, TestCubeHeightAccess> entry : entries) {
            HeightmapTreeLeaf leaf = root.getLeaf(entry.getKey());
            if (leaf != null) {
                //Leaves can be null when a protocube is marked as loaded in the cubemap, but hasn't yet been added to the global heightmap
                requiredLeaves.add(leaf);
            }
        }

        LongSet requiredPositions = new LongOpenHashSet();
        //Root will not be added if there are no leaves in the tree, so we add it here
        requiredPositions.add(ChunkPos.asLong(root.getScale(), root.getScaledY()));
        //Collect all positions that are required to be loaded
        for (HeightmapTreeLeaf leaf : requiredLeaves) {
            HeightmapTreeNode node = leaf;
            while (node != null) {
                requiredPositions.add(ChunkPos.asLong(node.getScale(), node.getScaledY()));

                if (node instanceof HeightmapTreeBranch branch) {
                    for (HeightmapTreeNode child : branch.getChildren()) {
                        if (child != null) {
                            requiredPositions.add(ChunkPos.asLong(child.getScale(), child.getScaledY()));
                        }
                    }
                }

                HeightmapTreeBranch parent = node.getParent();
                if (node.getScale() < HeightmapTreeNode.MAX_SCALE && parent == null) {
                    throw new IllegalStateException("Detached heightmap branch exists?!");
                }
                node = parent;
            }
        }

        //Verify that heightmap meets requirements (all parents are loaded, and their direct children)
        verifyAllNodesInRequiredSet(root, requiredPositions);
    }

    private static void verifyAllNodesInRequiredSet(HeightmapTreeBranch branch, LongSet requiredNodes) {
        for (HeightmapTreeNode child : branch.getChildren()) {
            if (child != null) {
                if (!requiredNodes.contains(ChunkPos.asLong(child.getScale(), child.getScaledY()))) {
                    throw new IllegalStateException("Heightmap borken");
                }

                if (branch.getScale() != 1) {
                    verifyAllNodesInRequiredSet((HeightmapTreeBranch) child, requiredNodes);
                }
            }
        }
    }

    /**
     * Test heightmap storage that always returns null on load, and correctly nulls fields on unload
     */
    static class NullHeightmapStorage implements HeightmapStorage {
        @Override public void unloadNode(HeightmapTreeNode node) {
            if (node.getScale() == 0) {
                ((HeightmapTreeLeaf) node).setCube(null);
            } else {
                Arrays.fill(((HeightmapTreeBranch) node).getChildren(), null);
            }
            node.setParent(null);
        }

        @Nullable @Override public HeightmapTreeNode loadNode(HeightmapTreeBranch parent, byte heightmapType, int scale, int scaledY) {
            return null;
        }
    }

    /**
     * Test heightmap storage that unloads into a hashmap
     */
    static class TestHeightmapStorage implements HeightmapStorage {
        Object2ReferenceMap<PackedTypeScaleScaledY, HeightmapTreeNode> saved = new Object2ReferenceOpenHashMap<>();

        @Override public void unloadNode(HeightmapTreeNode node) {
            if (node.getScale() == 0) {
                ((HeightmapTreeLeaf) node).setCube(null);
            } else {
                Arrays.fill(((HeightmapTreeBranch) node).getChildren(), null);
            }
            node.setParent(null);

            saved.put(new PackedTypeScaleScaledY(node.getRawType(), node.getScale(), node.getScaledY()), node);
        }
        @Override public HeightmapTreeNode loadNode(HeightmapTreeBranch parent, byte heightmapType, int scale, int scaledY) {
            HeightmapTreeNode removed = saved.remove(new PackedTypeScaleScaledY(heightmapType, scale, scaledY));
            if (removed != null) {
                removed.setParent(parent);
            }
            return removed;
        }
        record PackedTypeScaleScaledY(byte heightmapType, int scale, int scaledY) { }
    }

    public static class TestCubeHeightAccess implements CubeHeightAccess {
        final int y;

        final BitSet[][] blockBitsets = new BitSet[CubeAccess.DIAMETER_IN_BLOCKS][CubeAccess.DIAMETER_IN_BLOCKS];
        final HeightmapTreeLeaf[] sections = new HeightmapTreeLeaf[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];

        TestCubeHeightAccess(int nodeY) {
            for (BitSet[] bitSets : blockBitsets) {
                for (int j = 0, bitSetsLength = bitSets.length; j < bitSetsLength; j++) {
                    bitSets[j] = new BitSet(HeightmapTreeNode.SCALE_0_NODE_HEIGHT);
                }
            }
            this.y = nodeY;
        }

        void setBlock(int x, int localY, int z, boolean isOpaque) {
            assert localY >= 0 && localY < HeightmapTreeNode.SCALE_0_NODE_HEIGHT;
            this.blockBitsets[z][x].set(localY, isOpaque);
            HeightmapTreeLeaf section = this.sections[(x >> 4) + ((z >> 4) * CubeAccess.DIAMETER_IN_SECTIONS)];

            if (section == null) {
                fail(String.format("No section loaded for position (%d, %d, %d)", x, Coords.blockToCube(this.y) + localY, z));
            }
            section.onSetBlock(x, (this.y << HeightmapTreeNode.SCALE_0_NODE_BITS) + localY, z, heightmapType -> isOpaque);
        }

        @Override public void sectionLoaded(@Nonnull HeightmapTreeLeaf leaf, int localSectionX, int localSectionZ) {
            this.sections[localSectionX + localSectionZ * CubeAccess.DIAMETER_IN_SECTIONS] = leaf;
        }

        @Override public void unloadNode(@Nonnull HeightmapStorage storage) {
            HeightmapTreeLeaf[] nodes = this.sections;
            for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                    int i = localSectionX + localSectionZ * CubeAccess.DIAMETER_IN_SECTIONS;
                    if (nodes[i] != null) {
                        nodes[i].onCubeUnloaded(localSectionX, localSectionZ, storage);
                        nodes[i] = null;
                    }
                }
            }
        }

        @Override public int getHighest(int x, int z, byte heightmapType) {
            x &= CubeAccess.DIAMETER_IN_BLOCKS - 1;
            z &= CubeAccess.DIAMETER_IN_BLOCKS - 1;

            int highestY = this.blockBitsets[z][x].previousSetBit(this.blockBitsets[z][x].length());
            return highestY == -1 ? Integer.MIN_VALUE : highestY + (this.y << HeightmapTreeNode.SCALE_0_NODE_BITS);
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

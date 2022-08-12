package io.github.opencubicchunks.cubicchunks.levelgen.heightmap;

import static io.github.opencubicchunks.cubicchunks.testutils.Utils.forEachBlockColumnCube;
import static io.github.opencubicchunks.cubicchunks.testutils.Utils.forEachBlockColumnSurfaceTrackerNode;
import static io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode.MAX_SCALE;
import static io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode.NODE_COUNT;
import static io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode.ROOT_NODE_COUNT;
import static io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode.WIDTH_BLOCKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapSource;
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
        HeightmapStorage storage = new NullHeightmapStorage();

        ReferenceHeightmap reference = new ReferenceHeightmap(2048);
        
        TestHeightmapSource32 source0 = new TestHeightmapSource32(0, 0, 0);
        SurfaceTrackerNode[] roots = new SurfaceTrackerNode[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        }

        for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
            for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ]
                    .loadSource(Coords.cubeToSection(source0.cubeX, localSectionX),
                        Coords.cubeToSection(source0.cubeZ, localSectionZ),
                        storage, source0
                    );
            }
        }

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y, block.isOpaque);
            source0.setBlock(block.x, block.y, block.z, block.isOpaque);
        };

        forEachBlockColumnCube((x, z) -> {
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
        HeightmapStorage storage = new NullHeightmapStorage();

        ReferenceHeightmap reference = new ReferenceHeightmap(2048);

        TestHeightmapSource32 source0 = new TestHeightmapSource32(0, 0, 0);
        SurfaceTrackerNode[] roots = new SurfaceTrackerNode[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        }

        for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
            for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ]
                    .loadSource(Coords.cubeToSection(source0.cubeX, localSectionX),
                        Coords.cubeToSection(source0.cubeZ, localSectionZ),
                        storage, source0
                    );
            }
        }

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y, block.isOpaque);
            source0.setBlock(block.x, block.y, block.z, block.isOpaque);
        };

        forEachBlockColumnCube((x, z) -> {
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
        HeightmapStorage storage = new NullHeightmapStorage();

        int maxCoordinate = 2048;

        ReferenceHeightmap reference = new ReferenceHeightmap(maxCoordinate);

        Map<Integer, TestHeightmapSource32> sources = new HashMap<>();
        SurfaceTrackerNode[] roots = new SurfaceTrackerNode[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        }

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y, block.isOpaque);
            sources.computeIfAbsent(block.y >> SurfaceTrackerNode.SCALE_0_NODE_BITS, y -> {
                TestHeightmapSource32 node = new TestHeightmapSource32(0, y, 0);
                for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                    for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                        roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ]
                            .loadSource(Coords.cubeToSection(node.cubeX, localSectionX),
                                Coords.cubeToSection(node.cubeZ, localSectionZ),
                                storage, node
                            );
                    }
                }
                return node;
            }).setBlock(block.x, block.y & (SurfaceTrackerNode.SCALE_0_NODE_HEIGHT - 1), block.z, block.isOpaque);
        };

        forEachBlockColumnCube((x, z) -> {
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

        Map<Integer, TestHeightmapSource32> sources = new HashMap<>();
        SurfaceTrackerNode[] roots = new SurfaceTrackerNode[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        }

        Function<Integer, HeightmapSource> loadSource = y -> sources.computeIfAbsent(y, yPos -> {
            TestHeightmapSource32 node = new TestHeightmapSource32(0, yPos, 0);
            for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                    roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ]
                        .loadSource(Coords.cubeToSection(node.cubeX, localSectionX),
                            Coords.cubeToSection(node.cubeZ, localSectionZ),
                            storage, node
                        );
                }
            }
            return node;
        });


        loadSource.apply(-1);
        loadSource.apply(0);

        sources.get(-1).unloadSource(storage);

        for (SurfaceTrackerNode root : roots) {
            assertNull(root.getLeaf(-1), "Did not unload non-required node?!");
            assertNotNull(root.getLeaf(0), "Unloaded required node?!");

            verifyHeightmapTree((SurfaceTrackerBranch) root, sources.entrySet());
        }
    }

    /**
     * Tests whether after unloading the {@link SurfaceTrackerNode} node tree is correct
     * Does not test heights.
     */
    @Test
    public void simpleUnloadTree2() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        Map<Integer, TestHeightmapSource32> nodes = new HashMap<>();
        SurfaceTrackerNode[] roots = new SurfaceTrackerNode[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        }

        Function<Integer, HeightmapSource> loadSource = y -> nodes.computeIfAbsent(y, yPos -> {
            TestHeightmapSource32 node = new TestHeightmapSource32(0, yPos, 0);
            for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                    roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ]
                        .loadSource(Coords.cubeToSection(node.cubeX, localSectionX),
                            Coords.cubeToSection(node.cubeZ, localSectionZ),
                            storage, node
                        );
                }
            }
            return node;
        });
        Function<Integer, HeightmapSource> unloadNode = y -> {
            TestHeightmapSource32 removed = nodes.remove(y);
            if (removed != null) {
                removed.unloadSource(storage);
            }
            return removed;
        };


        for (int i = -1; i < NODE_COUNT; i++) {
            loadSource.apply(i);
        }
        loadSource.apply(NODE_COUNT * 2);

        unloadNode.apply(-1);

        for (SurfaceTrackerNode root : roots) {
            for (TestHeightmapStorage.PackedTypeScaleScaledY packed : storage.saved.keySet()) {
                if (packed.scale == 0) {
                    assertNull(root.getLeaf(packed.scaledY), "Did not unload non-required node?!");
                }
            }

            for (Integer integer : nodes.keySet()) {
                assertNotNull(root.getLeaf(integer), "Unloaded required node?!");
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

        Map<Integer, TestHeightmapSource32> nodes = new HashMap<>();
        SurfaceTrackerNode[] roots = new SurfaceTrackerNode[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];
        for (int i = 0; i < roots.length; i++) {
            roots[i] = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);
        }

        Function<Integer, HeightmapSource> loadSource = y -> nodes.computeIfAbsent(y, yPos -> {
            TestHeightmapSource32 node = new TestHeightmapSource32(0, yPos, 0);
            for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                    roots[localSectionX + CubeAccess.DIAMETER_IN_SECTIONS * localSectionZ]
                        .loadSource(Coords.cubeToSection(node.cubeX, localSectionX),
                            Coords.cubeToSection(node.cubeZ, localSectionZ),
                            storage, node
                        );
                }
            }
            return node;
        });
        Function<Integer, HeightmapSource> unloadNode = y -> {
            TestHeightmapSource32 removed = nodes.remove(y);
            if (removed != null) {
                removed.unloadSource(storage);
            }
            return removed;
        };


        Random r = new Random(123);
        for (int i = 0; i < 10000; i++) {
            loadSource.apply(r.nextInt(2048) - 1024);
        }
        for (int i = 0; i < 10000; i++) {
            unloadNode.apply(r.nextInt(2048) - 1024);
        }

        for (SurfaceTrackerNode root : roots) {
            for (TestHeightmapStorage.PackedTypeScaleScaledY packed : storage.saved.keySet()) {
                if (packed.scale == 0) {
                    assertNull(root.getLeaf(packed.scaledY), "Did not unload non-required node?!");
                }
            }

            for (Integer integer : nodes.keySet()) {
                assertNotNull(root.getLeaf(integer), "Unloaded required node?!");
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

        Map<Integer, TestHeightmapSource32> nodes = new HashMap<>();
        SurfaceTrackerNode root = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);

        Function<Integer, TestHeightmapSource32> loadSource = y -> nodes.computeIfAbsent(y, yPos -> {
            TestHeightmapSource32 node = new TestHeightmapSource32(0, yPos, 0);
            root.loadSource(0, 0, storage, node);
            return node;
        });
        Function<Integer, TestHeightmapSource32> unloadNode = y -> {
            TestHeightmapSource32 removed = nodes.remove(y);
            if (removed != null) {
                removed.unloadSource(storage);
            }
            return removed;
        };

        loadSource.apply(0);
        loadSource.apply(1);
        SurfaceTrackerLeaf node0 = root.getLeaf(0);

        SurfaceTrackerBranch parent = node0.getParent();
        HeightmapSource cubeNode = node0.getSource();

        unloadNode.apply(0);

        root.loadSource(0, 0, storage, cubeNode);

        SurfaceTrackerLeaf reloadedNode = root.getLeaf(0);

        assertEquals(parent, reloadedNode.getParent());
        assertEquals(cubeNode, reloadedNode.getSource());
    }

    /**
     * Tests that a leaf and its parent have no dirty positions on unload
     */
    @Test
    public void testNoDirtyOnUnload() {
        TestHeightmapStorage storage = new TestHeightmapStorage();

        Map<Integer, TestHeightmapSource32> nodes = new HashMap<>();
        SurfaceTrackerNode root = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);

        Function<Integer, TestHeightmapSource32> loadSource = y -> nodes.computeIfAbsent(y, yPos -> {
            TestHeightmapSource32 node = new TestHeightmapSource32(0, yPos, 0);
            root.loadSource(0, 0, storage, node);
            return node;
        });
        Function<Integer, TestHeightmapSource32> unloadNode = y -> {
            TestHeightmapSource32 removed = nodes.remove(y);
            if (removed != null) {
                removed.unloadSource(storage);
            }
            return removed;
        };

        loadSource.apply(0);
        loadSource.apply(1);
        SurfaceTrackerLeaf node0 = root.getLeaf(0);

        forEachBlockColumnSurfaceTrackerNode((x, z) -> {
            ((TestHeightmapSource32) node0.getSource()).setBlock(x, 29, z, true);
        });

        SurfaceTrackerBranch parent = node0.getParent();
        // parent hasn't had any gets yet, so must be dirty
        assertTrue(parent.isAnyDirty());

        unloadNode.apply(0);

        // parent had a child unloaded, so it must have no dirty positions
        assertFalse(parent.isAnyDirty());
    }

    /**
     * Tests that all branch nodes have the correct number of children
     * (root has {@link SurfaceTrackerNode#ROOT_NODE_COUNT}, all others have {@link SurfaceTrackerNode#NODE_COUNT})
     */
    @Test
    public void testNodeChildrenArraySizes() throws IOException {
        HeightmapStorage storage = new NullHeightmapStorage();

        TestHeightmapSource16 node = new TestHeightmapSource16(0, 0, 0);
        SurfaceTrackerBranch root = new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) 0);

        root.loadSource(0, 0, storage, node);

        assertNotNull(node.leaf); // if the leaf is null loading has failed, this test should fail immediately.

        forAllNodes(root, child -> {
            if (child.getScale() == MAX_SCALE) {
                assertEquals(ROOT_NODE_COUNT, ((SurfaceTrackerBranch) child).getChildren().length);
            } else if (child.getScale() > 0) {
                assertEquals(NODE_COUNT, ((SurfaceTrackerBranch) child).getChildren().length);
            }
        });

        storage.close();
    }

    private void forAllNodes(SurfaceTrackerBranch branch, Consumer<SurfaceTrackerNode> nodeConsumer) {
        nodeConsumer.accept(branch);
        forAllChildren(branch, nodeConsumer);
    }
    private void forAllChildren(SurfaceTrackerBranch branch, Consumer<SurfaceTrackerNode> childConsumer) {
        for (SurfaceTrackerNode child : branch.getChildren()) {
            if (child == null) {
                continue;
            }
            childConsumer.accept(child);
            if (child.getScale() > 0) {
                forAllChildren((SurfaceTrackerBranch) child, childConsumer);
            }
        }
    }

    /**
     * Very crude implementation of heightmap checking.
     * Iterates up from cubes adding all required nodes to a list
     * Adds all required ancestors to a set, iterates down from root verifying that all nodes are contained in the required set
     */
    private void verifyHeightmapTree(SurfaceTrackerBranch root, Set<Map.Entry<Integer, TestHeightmapSource32>> entries) {
        //Collect all leaves in the cubemap
        List<SurfaceTrackerLeaf> requiredLeaves = new ArrayList<>();
        for (Map.Entry<Integer, TestHeightmapSource32> entry : entries) {
            SurfaceTrackerLeaf leaf = root.getLeaf(entry.getKey());
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
        @Override public void saveNode(int globalSectionX, int globalSectionZ, SurfaceTrackerNode node) {

        }

        @Nullable @Override public SurfaceTrackerNode loadNode(int globalSectionX, int globalSectionZ, SurfaceTrackerBranch parent, byte heightmapType, int scale, int scaledY) {
            return null;
        }

        @Override public File storageDirectory() {
            return null;
        }

        @Override public void close() throws IOException {

        }

        @Override public void flush() throws IOException {

        }
    }

    /**
     * Test heightmap storage that unloads into a hashmap
     */
    static class TestHeightmapStorage implements HeightmapStorage {
        Object2ReferenceMap<PackedTypeScaleScaledY, SurfaceTrackerNode> saved = new Object2ReferenceOpenHashMap<>();

        @Override public void saveNode(int globalSectionX, int globalSectionZ, SurfaceTrackerNode node) {
            saved.put(new PackedTypeScaleScaledY(node.getRawType(), node.getScale(), node.getScaledY()), node);
        }
        @Override public SurfaceTrackerNode loadNode(int globalSectionX, int globalSectionZ, SurfaceTrackerBranch parent, byte heightmapType, int scale, int scaledY) {
            SurfaceTrackerNode removed = saved.remove(new PackedTypeScaleScaledY(heightmapType, scale, scaledY));
            if (removed != null) {
                removed.setParent(parent);
            }
            return removed;
        }

        @Override public File storageDirectory() {
            return null;
        }

        @Override public void close() throws IOException {

        }

        @Override public void flush() throws IOException {

        }

        record PackedTypeScaleScaledY(byte heightmapType, int scale, int scaledY) { }
    }

    public static class TestHeightmapSource32 implements HeightmapSource {
        final int cubeX;
        final int cubeZ;

        final int y;

        final BitSet[][] blockBitsets = new BitSet[CubeAccess.DIAMETER_IN_BLOCKS][CubeAccess.DIAMETER_IN_BLOCKS];
        final SurfaceTrackerLeaf[] leaves = new SurfaceTrackerLeaf[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];

        TestHeightmapSource32(int cubeX, int nodeY, int cubeZ) {
            for (BitSet[] bitSets : blockBitsets) {
                for (int j = 0, bitSetsLength = bitSets.length; j < bitSetsLength; j++) {
                    bitSets[j] = new BitSet(SurfaceTrackerNode.SCALE_0_NODE_HEIGHT);
                }
            }
            this.cubeX = cubeX;
            this.cubeZ = cubeZ;

            this.y = nodeY;
        }

        void setBlock(int x, int localY, int z, boolean isOpaque) {
            assert localY >= 0 && localY < SurfaceTrackerNode.SCALE_0_NODE_HEIGHT;
            x &= CubeAccess.DIAMETER_IN_BLOCKS - 1;
            z &= CubeAccess.DIAMETER_IN_BLOCKS - 1;

            this.blockBitsets[z][x].set(localY, isOpaque);
            SurfaceTrackerLeaf leaf = this.leaves[(x >> 4) + ((z >> 4) * CubeAccess.DIAMETER_IN_SECTIONS)];

            if (leaf == null) {
                fail(String.format("No leaf loaded for position (%d, %d, %d)", x, Coords.cubeToMinBlock(this.y) + localY, z));
            }
            leaf.onSetBlock(x, Coords.cubeToMinBlock(this.y) + localY, z, heightmapType -> isOpaque);
        }

        @Override public void sectionLoaded(@Nonnull SurfaceTrackerLeaf leaf, int localSectionX, int localSectionZ) {
            this.leaves[localSectionX + localSectionZ * CubeAccess.DIAMETER_IN_SECTIONS] = leaf;
        }

        @Override public void unloadSource(@Nonnull HeightmapStorage storage) {
            SurfaceTrackerLeaf[] nodes = this.leaves;
            for (int localSectionZ = 0; localSectionZ < CubeAccess.DIAMETER_IN_SECTIONS; localSectionZ++) {
                for (int localSectionX = 0; localSectionX < CubeAccess.DIAMETER_IN_SECTIONS; localSectionX++) {
                    int i = localSectionX + localSectionZ * CubeAccess.DIAMETER_IN_SECTIONS;
                    if (nodes[i] != null) {
                        nodes[i].sourceUnloaded(
                            Coords.cubeToSection(this.cubeX, localSectionX),
                            Coords.cubeToSection(this.cubeZ, localSectionZ),
                            storage
                        );
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

        @Override public int getSourceY() {
            return this.y;
        }
    }

    public static class TestHeightmapSource16 implements HeightmapSource {
        final int nodeX;
        final int nodeY;
        final int nodeZ;

        final BitSet[][] blockBitsets = new BitSet[WIDTH_BLOCKS][WIDTH_BLOCKS];
        SurfaceTrackerLeaf leaf = null;

        TestHeightmapSource16(int nodeX, int nodeY, int nodeZ) {
            this.nodeX = nodeX;
            this.nodeY = nodeY;
            this.nodeZ = nodeZ;

            for (BitSet[] bitSets : blockBitsets) {
                for (int i = 0, bitSetsLength = bitSets.length; i < bitSetsLength; i++) {
                    bitSets[i] = new BitSet(SurfaceTrackerNode.SCALE_0_NODE_HEIGHT);
                }
            }
        }

        void setBlock(int x, int localY, int z, boolean isOpaque) {
            assert localY >= 0 && localY < SurfaceTrackerNode.SCALE_0_NODE_HEIGHT;
            x &= WIDTH_BLOCKS - 1;
            z &= WIDTH_BLOCKS - 1;

            this.blockBitsets[z][x].set(localY, isOpaque);

            if (this.leaf == null) {
                fail(String.format("No leaf loaded for position (%d, %d, %d)", x, Coords.cubeToMinBlock(this.nodeY) + localY, z));
            }
            this.leaf.onSetBlock(x, Coords.cubeToMinBlock(this.nodeY) + localY, z, heightmapType -> isOpaque);
        }

        @Override public void sectionLoaded(@Nonnull SurfaceTrackerLeaf surfaceTrackerLeaf, int localSectionX, int localSectionZ) {
            this.leaf = surfaceTrackerLeaf;
        }

        @Override public void unloadSource(@Nonnull HeightmapStorage storage) {
            if (this.leaf != null) {
                this.leaf.sourceUnloaded(
                    this.nodeX,
                    this.nodeZ,
                    storage
                );
                this.leaf = null;
            }
        }

        @Override public int getHighest(int x, int z, byte heightmapType) {
            x &= WIDTH_BLOCKS - 1;
            z &= WIDTH_BLOCKS - 1;

            int highestY = this.blockBitsets[z][x].previousSetBit(this.blockBitsets[z][x].length());
            return highestY == -1 ? Integer.MIN_VALUE : highestY + (this.nodeY << SurfaceTrackerNode.SCALE_0_NODE_BITS);
        }

        @Override public int getSourceY() {
            return this.nodeY;
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

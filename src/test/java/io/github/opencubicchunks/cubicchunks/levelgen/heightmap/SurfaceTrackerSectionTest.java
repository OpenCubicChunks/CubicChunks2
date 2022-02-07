package io.github.opencubicchunks.cubicchunks.levelgen.heightmap;

import static org.junit.Assert.assertEquals;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapNode;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.SurfaceTrackerSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.Test;

public class SurfaceTrackerSectionTest {
    @Test
    public void sanityTest() {
        ReferenceHeightmap reference = new ReferenceHeightmap(2048);

        SurfaceTrackerSection root = new SurfaceTrackerSection(Heightmap.Types.WORLD_SURFACE);
        TestHeightmapNode node0 = new TestHeightmapNode(0);

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y, block.isOpaque);
            node0.setBlock(block.x, block.y, block.z, block.isOpaque);
        };

        forEachBlockColumn((x, z) -> {
            reference.clear();

            setHeight.accept(new HeightmapBlock(x, 5, z, true));
            setHeight.accept(new HeightmapBlock(x, 6, z, false));
            setHeight.accept(new HeightmapBlock(x, 3, z, true));

            root.loadCube(Coords.blockToCubeLocalSection(x), Coords.blockToCubeLocalSection(z), node0);

            assertEquals(reference.getHighest(), root.getHeight(x, z));

            setHeight.accept(new HeightmapBlock(x, 13, z, true));

            assertEquals(reference.getHighest(), root.getHeight(x, z));
        });
    }

    @Test
    public void seededRandom() {
        int maxCoordinate = 2048;

        ReferenceHeightmap reference = new ReferenceHeightmap(maxCoordinate);

        SurfaceTrackerSection root = new SurfaceTrackerSection(Heightmap.Types.WORLD_SURFACE);
        Map<Integer, TestHeightmapNode> nodes = new HashMap<>();

        Consumer<HeightmapBlock> setHeight = block -> {
            reference.set(block.y, block.isOpaque);
            nodes.computeIfAbsent(block.y >> SurfaceTrackerSection.SCALE_0_NODE_BITS, y -> {
                TestHeightmapNode node = new TestHeightmapNode(y);
                root.loadCube(Coords.blockToCubeLocalSection(block.x), Coords.blockToCubeLocalSection(block.z), node);
                return node;
            }).setBlock(block.x, block.y & (SurfaceTrackerSection.SCALE_0_NODE_HEIGHT - 1), block.z, block.isOpaque);
        };

        forEachBlockColumn((x, z) -> {
            reference.clear();

            Random r = new Random(123);

            for (int i = 0; i < 1000; i++) {
                int y = r.nextInt(maxCoordinate * 2) - maxCoordinate;
                setHeight.accept(new HeightmapBlock(x, y, z, r.nextBoolean()));
            }

            assertEquals(reference.getHighest(), root.getHeight(x, z));
        });
    }

    static void forEachBlockColumn(BiConsumer<Integer, Integer> xzConsumer) {
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                xzConsumer.accept(x, z);
            }
        }
    }

    static class TestHeightmapNode implements HeightmapNode {
        final int y;

        final BitSet[][] blockBitsets = new BitSet[CubeAccess.DIAMETER_IN_BLOCKS][CubeAccess.DIAMETER_IN_BLOCKS];
        final SurfaceTrackerSection[] sections = new SurfaceTrackerSection[CubeAccess.DIAMETER_IN_SECTIONS * CubeAccess.DIAMETER_IN_SECTIONS];

        TestHeightmapNode(int nodeY) {
            for (BitSet[] bitSets : blockBitsets) {
                for (int j = 0, bitSetsLength = bitSets.length; j < bitSetsLength; j++) {
                    bitSets[j] = new BitSet(SurfaceTrackerSection.SCALE_0_NODE_HEIGHT);
                }
            }

            this.y = nodeY;
        }

        void setBlock(int x, int localY, int z, boolean isOpaque) {
            assert localY >= 0 && localY < SurfaceTrackerSection.SCALE_0_NODE_HEIGHT;
            this.blockBitsets[z][x].set(localY, isOpaque);
            SurfaceTrackerSection section = this.sections[(x >> 4) + ((z >> 4) * CubeAccess.DIAMETER_IN_SECTIONS)];

            if (section != null) {
                section.onSetBlock(x, (this.y << SurfaceTrackerSection.SCALE_0_NODE_BITS) + localY, z, heightmapType -> isOpaque);
            }
        }

        @Override public void sectionLoaded(SurfaceTrackerSection surfaceTrackerSection, int localSectionX, int localSectionZ) {
            this.sections[localSectionX + localSectionZ * CubeAccess.DIAMETER_IN_SECTIONS] = surfaceTrackerSection;
        }

        @Override public int getHighest(int x, int z, byte heightmapType) {
            x &= CubeAccess.DIAMETER_IN_BLOCKS - 1;
            z &= CubeAccess.DIAMETER_IN_BLOCKS - 1;

            int highestY = this.blockBitsets[z][x].previousSetBit(this.blockBitsets[z][x].length());
            return highestY == -1 ? Integer.MIN_VALUE : highestY + (this.y << SurfaceTrackerSection.SCALE_0_NODE_BITS);
        }

        @Override public int getNodeY() {
            return this.y;
        }
    }

    record HeightmapBlock (int x, int y, int z, boolean isOpaque) { }
    static class ReferenceHeightmap {
        BitSet bitSet;
        final int offset;

        ReferenceHeightmap(int maxCoordinate) {
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

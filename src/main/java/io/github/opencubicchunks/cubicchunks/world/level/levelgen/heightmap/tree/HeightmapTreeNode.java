package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import io.github.opencubicchunks.cubicchunks.utils.MathUtil;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.CubeHeightAccess;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.levelgen.Heightmap;

public abstract class HeightmapTreeNode {
    // TODO: This currently covers y = -2^28 to 2^28 or so. One more would allow us to cover the entire integer block range
    public static final int MAX_SCALE = 6;
    /**
     * Number of bits needed to represent the children nodes (i.e. log2(NODE_COUNT)) This is also the number of bits that are added on each scale increase.
     */
    public static final int NODE_COUNT_BITS = 4;
    /** Number of children nodes */
    public static final int NODE_COUNT = 1 << NODE_COUNT_BITS;

    // Use width of 16 to match columns.
    public static final int WIDTH_BLOCKS = 16;
    public static final int WIDTH_MASK = WIDTH_BLOCKS - 1;
    public static final int SCALE_0_NODE_HEIGHT = CubeAccess.DIAMETER_IN_BLOCKS;
    public static final int SCALE_0_NODE_BITS = MathUtil.log2(CubeAccess.DIAMETER_IN_BLOCKS);

    /** Number of bits needed to represent height (excluding null) at scale zero (i.e. log2(scale0 height)) */
    private static final int BASE_SIZE_BITS = CubeAccess.SIZE_BITS;

    protected final BitStorage heights;
    protected final long[] dirtyPositions; // bitset has 100% memory usage overhead due to pointers and object headers
    protected @Nullable HeightmapTreeBranch parent;

    /**
     * Position of this section, within all sections of this size e.g. with 64-block sections, y=0-63 would be section 0, y=64-127 would be section 1, etc.
     */
    protected final int scaledY;
    protected final byte scale;
    protected final byte heightmapType;

    public HeightmapTreeNode(int scale, int scaledY, @Nullable HeightmapTreeBranch parent, byte heightmapType) {
//      super((ChunkAccess) node, types);
        // +1 in bit size to make room for null values
        this.heights = new BitStorage(BASE_SIZE_BITS + 1 + scale * NODE_COUNT_BITS, WIDTH_BLOCKS * WIDTH_BLOCKS);
        this.dirtyPositions = new long[WIDTH_BLOCKS * WIDTH_BLOCKS / Long.SIZE];
        this.parent = parent;
        this.scaledY = scaledY;
        this.scale = (byte) scale;
        this.heightmapType = heightmapType;
    }

    /**
     * Get the height for a given position. Recomputes the height if the column is marked dirty in this section.
     * x and z are <b>GLOBAL</b> coordinates (cube local is also fine, but section/chunk local is WRONG).
     */
    public int getHeight(int x, int z) {
        int idx = index(x, z);
        if (isDirty(idx)) {
            return updateHeight(x, z, idx);
        }
        int relativeY = this.heights.get(idx);
        return relToAbsY(relativeY, this.scaledY, this.scale);
    }

    /**
     * Updates height for given position, and returns the new (global) height
     */
    protected abstract int updateHeight(int x, int z, int idx);

    public abstract void loadCube(int localSectionX, int localSectionZ, HeightmapStorage storage, CubeHeightAccess newNode);

    /**
     * Tells a node to unload itself, nulling its parent, and passing itself to the provided storage
     */
    protected abstract void unload(HeightmapStorage storage);

    @Nullable public abstract HeightmapTreeLeaf getLeaf(int y);

    /**
     * Updates any positions that are dirty (used for unloading section)
     */
    public void updateDirtyHeights(int localSectionX, int localSectionZ) {
        if (!isAnyDirty()) {
            return;
        }

        for (int z = localSectionZ * WIDTH_BLOCKS, zMax = z + WIDTH_BLOCKS; z < zMax; z++) {
            for (int x = localSectionX * WIDTH_BLOCKS, xMax = x + WIDTH_BLOCKS; x < xMax; x++) {
                int idx = index(x, z);
                if (isDirty(idx)) {
                    updateHeight(x, z, idx);
                }
            }
        }
    }

    public void markAncestorsDirty() {
        for (int z = 0; z < WIDTH_BLOCKS; z++) {
            for (int x = 0; x < WIDTH_BLOCKS; x++) {
                // here we mark the tree dirty if their positions are below the top block of this cube
                this.markTreeDirtyIfRequired(x, z, relToAbsY(SCALE_0_NODE_HEIGHT << this.scale, this.scaledY, this.scale) + 1);
            }
        }
    }

    /** Returns if any position in the SurfaceTrackerSection is dirty*/
    public boolean isAnyDirty() {
        assert dirtyPositions.length == 4;

        long l = 0;
        l |= dirtyPositions[0];
        l |= dirtyPositions[1];
        l |= dirtyPositions[2];
        l |= dirtyPositions[3];
        return l != 0;
    }

    /** Returns if this SurfaceTrackerSection is dirty at the specified index */
    protected boolean isDirty(int idx) {
        return (dirtyPositions[idx >> 6] & (1L << idx)) != 0;
    }

    /** Sets the index in this SurfaceTrackerSection to non-dirty */
    protected void clearDirty(int idx) {
        dirtyPositions[idx >> 6] &= ~(1L << idx);
    }

    /** Sets the index in this SurfaceTrackerSection to dirty */
    protected void setDirty(int idx) {
        dirtyPositions[idx >> 6] |= 1L << idx;
    }

    public void setAllDirty() {
        assert dirtyPositions.length == 4;

        dirtyPositions[0] = -1;
        dirtyPositions[1] = -1;
        dirtyPositions[2] = -1;
        dirtyPositions[3] = -1;
    }

    /** Sets the index in this and all parent SurfaceTrackerSections to dirty */
    protected void markDirty(int x, int z) {
        setDirty(index(x, z));
        if (parent != null) {
            parent.markDirty(x, z);
        }
    }

    /** Sets this and parents dirty if new height > existing height */
    protected void markTreeDirtyIfRequired(int x, int z, int newHeight) {
        if (newHeight > relToAbsY(heights.get(index(x, z)), scaledY, scale) || isDirty(index(x, z))) {
            setDirty(index(x, z));
            if (this.parent != null) {
                this.parent.markTreeDirtyIfRequired(x, z, newHeight);
            }
        }
    }


    @Nullable
    public HeightmapTreeBranch getParent() {
        return parent;
    }

    public Heightmap.Types getType() {
        return Heightmap.Types.values()[heightmapType];
    }

    public byte getRawType() {
        return heightmapType;
    }

    /** Get position x/z index within a column, from global/local pos */
    protected static int index(int x, int z) {
        return (z & WIDTH_MASK) * WIDTH_BLOCKS + (x & WIDTH_MASK);
    }

    @VisibleForTesting
    public void setParent(@Nullable HeightmapTreeBranch parent) {
        this.parent = parent;
    }

    @VisibleForTesting
    static int indexOfRawHeightNode(int y, int nodeScale, int nodeScaledY) {
        if (nodeScale == 0) {
            throw new UnsupportedOperationException("Why?");
        }
        if (nodeScale == MAX_SCALE) {
            return y < 0 ? 0 : 1;
        }
        int scaled = y >> ((nodeScale - 1) * NODE_COUNT_BITS);
        return scaled - (nodeScaledY << NODE_COUNT_BITS);
    }

    @VisibleForTesting
    static int indexToScaledY(int index, int nodeScale, int nodeScaledY) {
        if (nodeScale == 0) {
            throw new UnsupportedOperationException("Why?");
        }
        if (nodeScale == MAX_SCALE) {
            return index == 0 ? -1 : 0;
        }
        return (nodeScaledY << NODE_COUNT_BITS) + index;
    }

    /** Get the lowest cube y coordinate for a given scaledY and scale */
    @VisibleForTesting
    static int scaledYBottomY(int scaledY, int scale) {
        if (scale == MAX_SCALE) {
            return -(1 << ((scale - 1) * NODE_COUNT_BITS));
        }
        return scaledY << (scale * NODE_COUNT_BITS);
    }

    /** Get the world y coordinate for a given relativeY, scaledY and scale */
    @VisibleForTesting
    static int relToAbsY(int relativeY, int scaledY, int scale) {
        if (relativeY == 0) {
            return Integer.MIN_VALUE;
        }
        return relativeY - 1 + scaledYBottomY(scaledY, scale) * SCALE_0_NODE_HEIGHT;
    }

    /** Get the relative y coordinate for a given absoluteY, scaledY and scale */
    @VisibleForTesting
    static int absToRelY(int absoluteY, int scaledY, int scale) {
        if (absoluteY == Integer.MIN_VALUE) {
            return 0;
        }
        return absoluteY + 1 - scaledYBottomY(scaledY, scale) * SCALE_0_NODE_HEIGHT;
    }

    public void writeData(int mainX, int mainZ, BitStorage data, int minValue) {
        for (int dx = 0; dx < WIDTH_BLOCKS; dx++) {
            for (int dz = 0; dz < WIDTH_BLOCKS; dz++) {
                int y = getHeight(mainX + dx, mainZ + dz) + 1;
                if (y < minValue) {
                    y = minValue;
                }
                y -= minValue;
                data.set(dx + dz * WIDTH_BLOCKS, y);
            }
        }
    }

    public int getScale() {
        return this.scale;
    }

    public int getScaledY() {
        return scaledY;
    }
}

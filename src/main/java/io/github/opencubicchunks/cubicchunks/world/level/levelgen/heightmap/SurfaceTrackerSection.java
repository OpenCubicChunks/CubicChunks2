package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import java.util.function.IntPredicate;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.utils.MathUtil;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.levelgen.Heightmap;

public class SurfaceTrackerSection {
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
    public static final int SCALE_0_NODE_HEIGHT = CubeAccess.DIAMETER_IN_BLOCKS;
    public static final int SCALE_0_NODE_BITS = MathUtil.log2(CubeAccess.DIAMETER_IN_BLOCKS);

    /** Number of bits needed to represent height (excluding null) at scale zero (i.e. log2(scale0 height)) */
    private static final int BASE_SIZE_BITS = CubeAccess.SIZE_BITS;

    protected final BitStorage heights;
    protected final long[] dirtyPositions; // bitset has 100% memory usage overhead due to pointers and object headers
    protected SurfaceTrackerSection parent;
    protected Object cubeOrNodes;
    /**
     * Position of this section, within all sections of this size e.g. with 64-block sections, y=0-63 would be section 0, y=64-127 would be section 1, etc.
     */
    protected final int scaledY;
    protected final byte scale;
    protected final byte heightmapType;

    protected boolean required;

    public SurfaceTrackerSection(Heightmap.Types types) {
        this(MAX_SCALE, 0, null, (byte) types.ordinal());
    }

    public SurfaceTrackerSection(int scale, int scaledY, SurfaceTrackerSection parent, byte heightmapType) {
        this(scale, scaledY, parent, null, heightmapType);
    }

    public SurfaceTrackerSection(int scale, int scaledY, SurfaceTrackerSection parent, HeightmapNode node, byte heightmapType) {
//      super((ChunkAccess) node, types);
        // +1 in bit size to make room for null values
        this.heights = new BitStorage(BASE_SIZE_BITS + 1 + scale * NODE_COUNT_BITS, WIDTH_BLOCKS * WIDTH_BLOCKS);
        this.dirtyPositions = new long[WIDTH_BLOCKS * WIDTH_BLOCKS / Long.SIZE];
        this.parent = parent;
        this.cubeOrNodes = scale == 0 ? node : new SurfaceTrackerSection[scale == MAX_SCALE ? 2 : NODE_COUNT];
        this.scaledY = scaledY;
        this.scale = (byte) scale;
        this.heightmapType = heightmapType;
    }

    /**
     * Implemented here for derived classes to override, eg: {@link LightSurfaceTrackerSection#createNewChild(int, int, HeightmapNode)}
     */
    protected SurfaceTrackerSection createNewChild(int newScale, int newScaledY, HeightmapNode cube) {
        if (newScale == 0) {
            return new SurfaceTrackerSection(newScale, newScaledY, this, cube, this.heightmapType);
        } else {
            return new SurfaceTrackerSection(newScale, newScaledY, this, this.heightmapType);
        }
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
        int relativeY = heights.get(idx);
        return relToAbsY(relativeY, scaledY, scale);
    }

    /**
     * Updates height for given position, and returns the new (global) height
     */
    protected int updateHeight(int x, int z, int idx) {
        synchronized(this) {
            int maxY = Integer.MIN_VALUE;
            if (scale == 0) {
                HeightmapNode node = (HeightmapNode) cubeOrNodes;
                maxY = node.getHighest(x, z, this.heightmapType);
            } else {
                SurfaceTrackerSection[] nodes = (SurfaceTrackerSection[]) cubeOrNodes;
                for (int i = nodes.length - 1; i >= 0; i--) {
                    SurfaceTrackerSection node = nodes[i];
                    if (node == null) {
                        continue;
                    }
                    int y = node.getHeight(x, z);
                    if (y != Integer.MIN_VALUE) {
                        maxY = y;
                        break;
                    }
                }
            }
            heights.set(idx, absToRelY(maxY, scaledY, scale));
            clearDirty(idx);
            return maxY;
        }
    }

    /**
     * Updates any positions that are dirty (used for unloading section)
     */
    public void updateDirtyHeights() {
        if (!isAnyDirty()) {
            return;
        }

        for (int z = 0; z < WIDTH_BLOCKS; z++) {
            for (int x = 0; x < WIDTH_BLOCKS; x++) {
                int idx = index(x, z);
                if (isDirty(idx)) {
                    updateHeight(x, z, idx);
                }
            }
        }
    }

    public void markAllDirtyAndTreeIfRequired() {
        assert dirtyPositions.length == 4;

        dirtyPositions[0] = -1;
        dirtyPositions[1] = -1;
        dirtyPositions[2] = -1;
        dirtyPositions[3] = -1;

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

    /**
     * Updates the internal heightmap for this SurfaceTracker section, and any parents who are also affected by it
     *
     * Should only be called on scale 0 heightmaps
     * @param isOpaquePredicate takes heightmap type
     */
    public void onSetBlock(int cubeLocalX, int y, int cubeLocalZ, IntPredicate isOpaquePredicate) {
        int index = index(cubeLocalX, cubeLocalZ);
        if (isDirty(index)) {
            return;
        }

        //input coordinates could be cube local, so convert Y to global
        int globalY = Coords.localToBlock(scaledY, Coords.blockToLocal(y));
        int height = getHeight(cubeLocalX, cubeLocalZ);
        if (globalY < height) {
            return;
        }

        if (heightmapType == -1) { //TODO: need to add lighting predicate (optimisation)
            markDirty(cubeLocalX, cubeLocalZ);
            return;
        }
        boolean opaque = isOpaquePredicate.test(this.heightmapType);
        if (globalY > height) {
            if (!opaque) {
                return;
            }

            if (parent != null) { //parent can only be null in a ProtoCube, or a MAX_SCALE section
                //only mark parents dirty if the Y is above their current height
                this.parent.markTreeDirtyIfRequired(cubeLocalX, cubeLocalZ, y);
            }
            this.heights.set(index, absToRelY(globalY, scaledY, scale));
            return;
        }
        //at this point globalY == height
        if (!opaque) { //if we're replacing the current (opaque) block with a non-opaque block
            markDirty(cubeLocalX, cubeLocalZ);
        }
    }

    /**
     * @param localSectionX
     * @param localSectionZ
     * @param newNode
     */
    public synchronized void loadCube(int localSectionX, int localSectionZ, HeightmapNode newNode) {
        if (this.scale != 0 && this.cubeOrNodes == null) {
            throw new IllegalStateException("Attempting to load node " + newNode + " into an unloaded surface tracker section");
        }
        if (this.scale == 0) {
            //Don't need to mark this cube-scale section as dirty, as it should have been updated on unload
            newNode.sectionLoaded(this, localSectionX, localSectionZ);
            if (this.parent != null) {
                this.markAllDirtyAndTreeIfRequired();
            }
            return;
        }
        int idx = indexOfRawHeightNode(newNode.getNodeY(), scale, scaledY);
        SurfaceTrackerSection[] nodes = (SurfaceTrackerSection[]) cubeOrNodes;
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                continue;
            }
            int newScaledY = indexToScaledY(i, scale, scaledY);
            int newScale = scale - 1;
            //TODO: load from save here, instead of always creating
            SurfaceTrackerSection newOrLoadedSection;
            if (i == idx) {
                newOrLoadedSection = createNewChild(newScale, newScaledY, newNode);
            } else {
                newOrLoadedSection = null;
            }
            nodes[i] = newOrLoadedSection;
        }
        assert nodes[idx] != null;
        nodes[idx].loadCube(localSectionX, localSectionZ, newNode);
    }

    @Nullable
    public SurfaceTrackerSection getParent() {
        return parent;
    }

    public SurfaceTrackerSection getChild(int i) {
        SurfaceTrackerSection[] nodes = (SurfaceTrackerSection[]) cubeOrNodes;
        return nodes[i];
    }

    @Nullable
    public SurfaceTrackerSection getMinScaleNode(int y) {
        if (scale == 0) {
            if (y != scaledY) {
                throw new IllegalArgumentException("Invalid Y: " + y + ", expected " + scaledY);
            }
            return this;
        }
        int idx = indexOfRawHeightNode(y, scale, scaledY);
        SurfaceTrackerSection[] nodes = (SurfaceTrackerSection[]) cubeOrNodes;
        SurfaceTrackerSection node = nodes[idx];
        if (node == null) {
            return null;
        }
        return node.getMinScaleNode(y);
    }

    public HeightmapNode getNode() {
        return (HeightmapNode) cubeOrNodes;
    }

    public Heightmap.Types getType() {
        return Heightmap.Types.values()[heightmapType];
    }

    public byte getRawType() {
        return heightmapType;
    }

    /** Get position x/z index within a column, from global/local pos */
    protected int index(int x, int z) {
        return (z & 0xF) * WIDTH_BLOCKS + (x & 0xF);
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
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int y = getHeight(mainX + dx, mainZ + dz) + 1;
                if (y < minValue) {
                    y = minValue;
                }
                y -= minValue;
                data.set(dx + dz * 16, y);
            }
        }
    }
}

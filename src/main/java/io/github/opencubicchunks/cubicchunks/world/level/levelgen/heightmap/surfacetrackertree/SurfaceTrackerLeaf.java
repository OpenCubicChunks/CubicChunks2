package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import java.util.function.IntPredicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapNode;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;

public class SurfaceTrackerLeaf extends SurfaceTrackerNode {
    protected HeightmapNode node;

    public SurfaceTrackerLeaf(int y, @Nullable SurfaceTrackerBranch parent, byte heightmapType) {
        super(0, y, parent, heightmapType);
    }

    @Override
    protected int updateHeight(int x, int z, int idx) {
        synchronized(this) {
            // Node cannot be null here. If it is, the leaf was not updated on node unloading.
            int maxY = this.node.getHighest(x, z, this.heightmapType);

            this.heights.set(idx, absToRelY(maxY, this.scaledY, this.scale));
            clearDirty(idx);
            return maxY;
        }
    }

    @Override
    public synchronized void loadCube(int localSectionX, int localSectionZ, HeightmapStorage storage, @Nonnull HeightmapNode newNode) {
        boolean isBeingInitialized = this.node == null;

        this.node = newNode;
        newNode.sectionLoaded(this, localSectionX, localSectionZ);

        // Parent might be null for proto-cube leaf nodes
        // If we are inserting a new node (it's parent is null), the parents must be updated.
        // The parent can already be set for LevelCubes, their heights are inherited from their ProtoCubes
        // and do not need to be updated
        if (this.parent != null) {
            this.markAncestorsDirty();
            if (isBeingInitialized) {
                // If this is the first node inserted into this leaf, we inform the parent node.
                // Both ProtoCube and LevelCube will call loadCube, this avoids invalid reference counting
                this.parent.onChildLoaded();
            }
        }
    }

    @Override protected void unload(@Nonnull HeightmapStorage storage) {
        assert this.node == null : "Heightmap leaf being unloaded while holding a cube?!";

        this.parent = null;

        storage.unloadNode(this);
    }

    /**
     * Called by the node (cube) when it's unloaded. This informs the parent that one of its
     * children are no longer required
     */
    public void cubeUnloaded(int localSectionX, int localSectionZ, HeightmapStorage storage) {
        assert this.node != null;

        // On unloading the node, the leaf must have no dirty positions
        updateDirtyHeights(localSectionX, localSectionZ);

        this.node = null;

        // Parent can be null for a protocube that hasn't been added to the global heightmap
        if (parent != null) {
            this.parent.onChildUnloaded(storage);
        }
    }

    @Nullable
    public SurfaceTrackerLeaf getMinScaleNode(int y) {
        if (y != this.scaledY) {
            throw new IllegalArgumentException("Invalid Y: " + y + ", expected " + this.scaledY);
        }
        return this;
    }

    /**
     * Updates the internal heightmap for this SurfaceTracker section, and any parents who are also affected by it
     *
     * Should only be called on scale 0 heightmaps
     * @param isOpaquePredicate takes heightmap type
     */
    public void onSetBlock(int cubeLocalX, int y, int cubeLocalZ, IntPredicate isOpaquePredicate) {
        assert y >= Coords.cubeToMinBlock(this.scaledY) && y <= Coords.cubeToMaxBlock(this.scaledY) :
            String.format("Leaf node (scaledY: %d) got Y position %d which is out of inclusive bounds %d to %d",
                this.scaledY, y, Coords.cubeToMinBlock(this.scaledY), Coords.cubeToMaxBlock(this.scaledY));

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

    public HeightmapNode getNode() {
        return this.node;
    }

    private SurfaceTrackerBranch getRoot() {
        SurfaceTrackerNode section = this;
        while (section.parent != null) {
            section = section.parent;
        }
        //noinspection ConstantConditions
        return (SurfaceTrackerBranch) section;
    }

    @Nullable
    public SurfaceTrackerLeaf getSectionAbove() {
        // TODO this can be optimized - don't need to go to the root every time, just the lowest node that is a parent of both this node and the node above.
        return this.getRoot().getMinScaleNode(scaledY + 1);
    }

    @VisibleForTesting
    public void setNode(@Nullable HeightmapNode node) {
        this.node = node;
    }
}

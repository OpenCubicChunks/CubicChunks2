package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import static io.github.opencubicchunks.cubicchunks.utils.Coords.*;

import java.util.function.IntPredicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapSource;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;

public class SurfaceTrackerLeaf extends SurfaceTrackerNode {
    protected HeightmapSource source;

    public SurfaceTrackerLeaf(int y, @Nullable SurfaceTrackerBranch parent, byte heightmapType) {
        super(0, y, parent, heightmapType);
    }

    /**
     * Should be used when loading from save
     */
    public SurfaceTrackerLeaf(int y, @Nullable SurfaceTrackerBranch parent, byte heightmapType, long[] heightsRaw) {
        super(0, y, parent, heightmapType, heightsRaw);
    }

    @Override
    protected int updateHeight(int x, int z, int idx) {
        synchronized(this) {
            // Node cannot be null here. If it is, the leaf was not updated on node unloading.
            int maxY = this.source.getHighest(x, z, this.getRawType());

            this.heights.set(idx, absToRelY(maxY, this.scaledY, this.scale));
            clearDirty(idx);
            return maxY;
        }
    }

    @Override
    public synchronized void loadSource(int globalSectionX, int globalSectionZ, HeightmapStorage storage, @Nonnull HeightmapSource newSource) {
        boolean isBeingInitialized = this.source == null;

        this.source = newSource;
        newSource.sectionLoaded(this, cubeLocalSection(globalSectionX), cubeLocalSection(globalSectionZ));

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

    @Override protected void unload(int globalSectionX, int globalSectionZ, @Nonnull HeightmapStorage storage) {
        assert this.source == null : "Heightmap leaf being unloaded while holding a source node?!";

        this.parent = null;

        this.save(globalSectionX, globalSectionZ, storage);
    }

    @Override protected void save(int globalSectionX, int globalSectionZ, @Nonnull HeightmapStorage storage) {
        storage.saveNode(globalSectionX, globalSectionZ, this);
    }


    /**
     * Called by the node (cube) when it's unloaded. This informs the parent that one of its
     * children are no longer required
     */
    public void sourceUnloaded(int globalSectionX, int globalSectionZ, HeightmapStorage storage) {
        assert this.source != null;

        // On unloading the node, the leaf must have no dirty positions
        updateDirtyHeights(globalSectionX, globalSectionZ);

        this.source = null;

        // Parent can be null for a protocube that hasn't been added to the global heightmap
        if (parent != null) {
            this.parent.onChildUnloaded(globalSectionX, globalSectionZ, storage);
        }
    }

    @Nullable
    public SurfaceTrackerLeaf getLeaf(int y) {
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
        if (y < Coords.cubeToMinBlock(this.scaledY) || y > Coords.cubeToMaxBlock(this.scaledY)) {
            throw new IndexOutOfBoundsException(String.format("Leaf node (scaledY: %d) got Y position %d which is out of inclusive bounds %d to %d",
                this.scaledY, y, Coords.cubeToMinBlock(this.scaledY), Coords.cubeToMaxBlock(this.scaledY)));
        }

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

        if (this.getRawType() == -1) { //TODO: need to add lighting predicate (optimisation)
            markDirty(cubeLocalX, cubeLocalZ);
            return;
        }
        boolean opaque = isOpaquePredicate.test(this.getRawType());
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

    @Nullable
    public HeightmapSource getSource() {
        return this.source;
    }

    SurfaceTrackerBranch getRoot() {
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
        return this.getRoot().getLeaf(scaledY + 1);
    }
}

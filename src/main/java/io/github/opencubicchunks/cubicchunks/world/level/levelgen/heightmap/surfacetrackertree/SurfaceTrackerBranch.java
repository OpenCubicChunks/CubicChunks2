package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapNode;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;

public class SurfaceTrackerBranch extends SurfaceTrackerNode {
    protected SurfaceTrackerNode[] children = new SurfaceTrackerNode[NODE_COUNT];

    private int requiredChildren;

    public SurfaceTrackerBranch(int scale, int scaledY,
                                @Nullable SurfaceTrackerBranch parent, byte heightmapType) {
        super(scale, scaledY, parent, heightmapType);
        assert scale > 0; //Branches cannot be scale 0
        assert scale <= SurfaceTrackerNode.MAX_SCALE; //Branches cannot be > MAX_SCALE
    }

    @Override
    protected int updateHeight(int x, int z, int idx) {
        synchronized(this) {
            int maxY = Integer.MIN_VALUE;

            //Iterate though children from top to bottom finding the first with a valid position
            for (int i = this.children.length - 1; i >= 0; i--) {
                SurfaceTrackerNode node = this.children[i];
                if (node == null) {
                    continue;
                }
                int y = node.getHeight(x, z);
                if (y != Integer.MIN_VALUE) {
                    maxY = y;
                    break;
                }
            }

            this.heights.set(idx, absToRelY(maxY, this.scaledY, this.scale));
            clearDirty(idx);
            return maxY;
        }
    }

    public SurfaceTrackerLeaf loadCube(HeightmapStorage storage, HeightmapNode newNode, @Nullable SurfaceTrackerLeaf protoLeaf) {
        int newScale = scale - 1;

        // Attempt to load all children from storage
        for (int i = 0; i < this.children.length; i++) {
            if (children[i] == null) {
                int newScaledY = indexToScaledY(i, scale, scaledY);
                children[i] = storage.loadNode(this, this.heightmapType, newScale, newScaledY);
            }
        }

        int idx = indexOfRawHeightNode(newNode.getNodeY(), scale, scaledY);
        int newScaledY = indexToScaledY(idx, scale, scaledY);

        // child is a leaf
        if (newScale == 0) {

            assert protoLeaf == null || children[idx] == null : "!";

            SurfaceTrackerLeaf newLeaf;

            // converting an existing node
            if (children[idx] != null) {
                newLeaf = new SurfaceTrackerLeaf(newNode, this, (SurfaceTrackerLeaf) children[idx]);
            }
            // attaching an external leaf
            else if (protoLeaf != null) {
                newLeaf = new SurfaceTrackerLeaf(newNode, this, protoLeaf);
            }
            // creating a new leaf
            else {
                newLeaf = new SurfaceTrackerLeaf(newNode, this, this.heightmapType);
            }

            children[idx] = newLeaf;
            newLeaf.markAncestorsDirty();

            onChildLoaded();

            return newLeaf;
        }

        // child is a branch
        else {

            // lazily create new branches
            if (children[idx] == null) {
                children[idx] = new SurfaceTrackerBranch(newScale, newScaledY, this, this.heightmapType);
            }

            return ((SurfaceTrackerBranch) children[idx]).loadCube(storage, newNode, protoLeaf);
        }
    }


    @Override protected void unload(HeightmapStorage storage) {
        for (SurfaceTrackerNode child : this.children) {
            assert child == null : "Heightmap branch being unloaded while holding a child?!";
        }

        this.parent = null;

        storage.unloadNode(this);
    }

    @Nullable public SurfaceTrackerLeaf getMinScaleNode(int y) {
        int idx = indexOfRawHeightNode(y, scale, scaledY);
        SurfaceTrackerNode node = this.children[idx];
        if (node == null) {
            return null;
        }
        return node.getMinScaleNode(y);
    }


    public void onChildLoaded() {
        if (requiredChildren == 0 && this.scale != MAX_SCALE) {
            assert this.parent != null : "Cube loaded in detached tree?!";
            this.parent.onChildLoaded();
        }

        ++requiredChildren;
        assert requiredChildren <= this.children.length : "More children than max?!";
    }

    public void onChildUnloaded(HeightmapStorage storage) {
        --requiredChildren;

        assert requiredChildren >= 0 : "Less than 0 required children?!";

        if (requiredChildren == 0) {
            SurfaceTrackerNode[] surfaceTrackerNodes = this.children;
            for (int i = 0, surfaceTrackerNodesLength = surfaceTrackerNodes.length; i < surfaceTrackerNodesLength; i++) {
                SurfaceTrackerNode child = surfaceTrackerNodes[i];
                if (child != null) {
                    child.unload(storage);
                    surfaceTrackerNodes[i] = null;
                }
            }

            if (this.parent != null) {
                this.parent.onChildUnloaded(storage);
            }
        }
    }

    @VisibleForTesting
    @Nonnull public SurfaceTrackerNode[] getChildren() {
        return this.children;
    }
}

package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapNode;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;
import org.jetbrains.annotations.Nullable;

public class SurfaceTrackerBranch extends SurfaceTrackerNode {
    protected SurfaceTrackerNode[] children = new SurfaceTrackerNode[NODE_COUNT];

    private int requiredChildren;

    public SurfaceTrackerBranch(int scale, int scaledY,
                                @Nullable SurfaceTrackerBranch parent, byte heightmapType) {
        super(scale, scaledY, parent, heightmapType);
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

    @Override public void loadCube(int localSectionX, int localSectionZ, HeightmapStorage storage, HeightmapNode newNode) {
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
        if (children[idx] == null) {
            // If the child containing new node has not been loaded from storage, create it
            // Scale 1 nodes create leaf node children
            if (newScale == 0) {
                children[idx] = new SurfaceTrackerLeaf(newScaledY, this, this.heightmapType);
            } else {
                children[idx] = new SurfaceTrackerBranch(newScale, newScaledY, this, this.heightmapType);
            }
        }
        children[idx].loadCube(localSectionX, localSectionZ, storage, newNode);
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
        }
    }
}

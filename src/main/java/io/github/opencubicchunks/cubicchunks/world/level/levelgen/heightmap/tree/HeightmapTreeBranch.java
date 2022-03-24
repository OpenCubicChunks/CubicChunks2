package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.CubeHeightAccess;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;

public class HeightmapTreeBranch extends HeightmapTreeNode {
    protected HeightmapTreeNode[] children = new HeightmapTreeNode[NODE_COUNT];

    private int requiredChildren;

    public HeightmapTreeBranch(int scale, int scaledY,
                               @Nullable HeightmapTreeBranch parent, byte heightmapType) {
        super(scale, scaledY, parent, heightmapType);
        assert scale > 0; //Branches cannot be scale 0
        assert scale <= HeightmapTreeNode.MAX_SCALE; //Branches cannot be > MAX_SCALE
    }

    @Override
    protected int updateHeight(int x, int z, int idx) {
        synchronized(this) {
            int maxY = Integer.MIN_VALUE;

            //Iterate though children from top to bottom finding the first with a valid position
            for (int i = this.children.length - 1; i >= 0; i--) {
                HeightmapTreeNode node = this.children[i];
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

    @Override public void loadCube(int localSectionX, int localSectionZ, HeightmapStorage storage, CubeHeightAccess newNode) {
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
                children[idx] = new HeightmapTreeLeaf(newScaledY, this, this.heightmapType);
            } else {
                children[idx] = new HeightmapTreeBranch(newScale, newScaledY, this, this.heightmapType);
            }
        }
        children[idx].loadCube(localSectionX, localSectionZ, storage, newNode);
    }

    @Override protected void unload(HeightmapStorage storage) {
        for (HeightmapTreeNode child : this.children) {
            assert child == null : "Heightmap branch being unloaded while holding a child?!";
        }

        this.parent = null;

        storage.unloadNode(this);
    }

    @Nullable public HeightmapTreeLeaf getLeaf(int y) {
        int idx = indexOfRawHeightNode(y, scale, scaledY);
        HeightmapTreeNode node = this.children[idx];
        if (node == null) {
            return null;
        }
        return node.getLeaf(y);
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
            HeightmapTreeNode[] heightmapTreeNodes = this.children;
            for (int i = 0, surfaceTrackerNodesLength = heightmapTreeNodes.length; i < surfaceTrackerNodesLength; i++) {
                HeightmapTreeNode child = heightmapTreeNodes[i];
                if (child != null) {
                    child.unload(storage);
                    heightmapTreeNodes[i] = null;
                }
            }

            if (this.parent != null) {
                this.parent.onChildUnloaded(storage);
            }
        }
    }

    @VisibleForTesting
    @Nonnull public HeightmapTreeNode[] getChildren() {
        return this.children;
    }
}

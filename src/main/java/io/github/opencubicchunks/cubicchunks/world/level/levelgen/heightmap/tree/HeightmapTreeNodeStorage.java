package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree;

import java.util.Arrays;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;

public class HeightmapTreeNodeStorage implements HeightmapStorage {
    //TODO: replace with a real saving impl
    private Object2ReferenceMap<PackedTypeScaleScaledY, HeightmapTreeNode> saved = new Object2ReferenceOpenHashMap<>();

    @Override
    public void unloadNode(HeightmapTreeNode heightmapTreeNode) {
        saved.put(new PackedTypeScaleScaledY(heightmapTreeNode.heightmapType, heightmapTreeNode.scale, heightmapTreeNode.scaledY), heightmapTreeNode);

        if (heightmapTreeNode.scale == 0) {
            ((HeightmapTreeLeaf) heightmapTreeNode).cubeAccess = null;
        } else {
            Arrays.fill(((HeightmapTreeBranch) heightmapTreeNode).children, null);
        }
        heightmapTreeNode.parent = null;
    }

    @Override
    public HeightmapTreeNode loadNode(HeightmapTreeBranch parent, byte heightmapType, int scale, int scaledY) {
        HeightmapTreeNode removed = saved.remove(new PackedTypeScaleScaledY(heightmapType, scale, scaledY));
        if (removed != null) {
            removed.parent = parent;
        }
        return removed;
    }

    record PackedTypeScaleScaledY(byte heightmapType, int scale, int scaledY) { }
}

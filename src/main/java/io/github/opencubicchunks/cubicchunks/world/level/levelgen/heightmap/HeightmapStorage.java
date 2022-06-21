package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree.HeightmapTreeBranch;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree.HeightmapTreeNode;

public interface HeightmapStorage {
    void unloadNode(HeightmapTreeNode surfaceTrackerSection);
    @Nullable HeightmapTreeNode loadNode(HeightmapTreeBranch parent, byte heightmapType, int scale, int scaledY);
}

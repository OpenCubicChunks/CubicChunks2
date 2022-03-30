package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import javax.annotation.Nonnull;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerLeaf;

public interface HeightmapNode {

    void unloadNode(@Nonnull HeightmapStorage storage);

    int getHighest(int x, int z, byte heightmapType);

    int getNodeY();
}

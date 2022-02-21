package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerBranch;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode;

public interface HeightmapStorage {
    void unloadNode(SurfaceTrackerNode surfaceTrackerSection);
    @Nullable SurfaceTrackerNode loadNode(SurfaceTrackerBranch parent, byte heightmapType, int scale, int scaledY);
}

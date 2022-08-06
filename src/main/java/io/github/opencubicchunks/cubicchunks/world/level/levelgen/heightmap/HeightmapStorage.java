package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import java.io.Closeable;
import java.io.Flushable;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerBranch;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode;

public interface HeightmapStorage extends Closeable, Flushable {
    void unloadNode(int globalSectionX, int globalSectionZ, SurfaceTrackerNode surfaceTrackerSection);
    @Nullable SurfaceTrackerNode loadNode(int globalSectionX, int globalSectionZ, SurfaceTrackerBranch parent, byte heightmapType, int scale, int scaledY);
}

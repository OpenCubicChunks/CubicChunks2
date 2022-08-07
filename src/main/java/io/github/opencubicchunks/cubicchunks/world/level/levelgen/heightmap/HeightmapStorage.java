package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import java.io.Closeable;
import java.io.File;
import java.io.Flushable;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerBranch;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode;
import org.jetbrains.annotations.NotNull;

public interface HeightmapStorage extends Closeable, Flushable {
    void saveNode(int globalSectionX, int globalSectionZ, @NotNull SurfaceTrackerNode surfaceTrackerSection);
    @Nullable SurfaceTrackerNode loadNode(int globalSectionX, int globalSectionZ, @Nullable SurfaceTrackerBranch parent, byte heightmapType, int scale, int scaledY);

    File storageDirectory();
}

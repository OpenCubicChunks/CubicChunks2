package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import javax.annotation.Nullable;

public interface HeightmapStorage {
    void unloadNode(byte heightmapType, int scale, int scaledY, SurfaceTrackerSection surfaceTrackerSection);
    @Nullable SurfaceTrackerSection loadNode(SurfaceTrackerSection parent, byte heightmapType, int scale, int scaledY);
}

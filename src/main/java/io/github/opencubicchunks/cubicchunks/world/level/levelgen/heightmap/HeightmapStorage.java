package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

public interface HeightmapStorage {
    void unloadNode(int scale, int scaledY, SurfaceTrackerSection surfaceTrackerSection);
    SurfaceTrackerSection loadNode(int scale, int scaledY);
}

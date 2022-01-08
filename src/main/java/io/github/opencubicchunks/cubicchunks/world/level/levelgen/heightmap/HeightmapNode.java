package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

public interface HeightmapNode {

    default void sectionLoaded(SurfaceTrackerSection surfaceTrackerSection, int localSectionX, int localSectionZ) {
        throw new IllegalStateException("Should not be reached");
    }

    int getHighest(int x, int z, byte heightmapType);

    int getY();
}

package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import javax.annotation.Nonnull;

public interface HeightmapNode {

    default void sectionLoaded(@Nonnull SurfaceTrackerSection surfaceTrackerSection, int localSectionX, int localSectionZ) {
        throw new IllegalStateException("Should not be reached");
    }

    void unloadNode(@Nonnull HeightmapStorage storage);

    int getHighest(int x, int z, byte heightmapType);

    int getNodeY();
}

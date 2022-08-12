package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import javax.annotation.Nonnull;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerLeaf;

public interface HeightmapSource {

    default void sectionLoaded(@Nonnull SurfaceTrackerLeaf surfaceTrackerLeaf, int localSectionX, int localSectionZ) {
        throw new IllegalStateException("Should not be reached");
    }

    void unloadSource(@Nonnull HeightmapStorage storage);

    int getHighest(int x, int z, byte heightmapType);

    int getSourceY();
}

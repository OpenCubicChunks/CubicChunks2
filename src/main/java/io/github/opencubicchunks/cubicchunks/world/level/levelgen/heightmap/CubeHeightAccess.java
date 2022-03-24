package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import javax.annotation.Nonnull;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree.HeightmapTreeLeaf;

public interface CubeHeightAccess {

    default void sectionLoaded(@Nonnull HeightmapTreeLeaf surfaceTrackerLeaf, int localSectionX, int localSectionZ) {
        throw new IllegalStateException("Should not be reached");
    }

    void unloadNode(@Nonnull HeightmapStorage storage);

    int getHighest(int x, int z, byte heightmapType);

    int getNodeY();
}

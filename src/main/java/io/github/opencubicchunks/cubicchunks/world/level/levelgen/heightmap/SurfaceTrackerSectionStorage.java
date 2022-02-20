package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;

public class SurfaceTrackerSectionStorage implements HeightmapStorage {
    private Long2ReferenceMap<SurfaceTrackerSection> saved = new Long2ReferenceOpenHashMap<>();

    @Override
    public void unloadNode(int scale, int scaledY, SurfaceTrackerSection surfaceTrackerSection) {
        saved.put((((long)scale) << 32) | scaledY, surfaceTrackerSection);
    }

    @Override
    public SurfaceTrackerSection loadNode(int scale, int scaledY) {
        return saved.remove((((long)scale) << 32) | scaledY);
    }
}

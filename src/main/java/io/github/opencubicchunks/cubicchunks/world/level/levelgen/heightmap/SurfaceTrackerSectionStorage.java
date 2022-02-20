package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;

public class SurfaceTrackerSectionStorage implements HeightmapStorage {
    private Object2ReferenceMap<PackedTypeScaleScaledY, SurfaceTrackerSection> saved = new Object2ReferenceOpenHashMap<>();

    @Override
    public void unloadNode(byte heightmapType, int scale, int scaledY, SurfaceTrackerSection surfaceTrackerSection) {
        saved.put(new PackedTypeScaleScaledY(heightmapType, scale, scaledY), surfaceTrackerSection);

        surfaceTrackerSection.cubeOrNodes = null;
        surfaceTrackerSection.parent = null;
    }

    @Override
    public SurfaceTrackerSection loadNode(byte heightmapType, int scale, int scaledY) {
        return saved.remove(new PackedTypeScaleScaledY(heightmapType, scale, scaledY));
    }

    record PackedTypeScaleScaledY(byte heightmapType, int scale, int scaledY) { }
}

package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import java.util.Arrays;

import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;

public class SurfaceTrackerSectionStorage implements HeightmapStorage {
    private Object2ReferenceMap<PackedTypeScaleScaledY, SurfaceTrackerSection> saved = new Object2ReferenceOpenHashMap<>();

    @Override
    public void unloadNode(byte heightmapType, int scale, int scaledY, SurfaceTrackerSection surfaceTrackerSection) {
        saved.put(new PackedTypeScaleScaledY(heightmapType, scale, scaledY), surfaceTrackerSection);

        if (surfaceTrackerSection.scale == 0) {
            surfaceTrackerSection.cubeOrNodes = null;
        } else {
            Arrays.fill(((SurfaceTrackerSection[]) surfaceTrackerSection.cubeOrNodes), null);
        }
        surfaceTrackerSection.parent = null;
    }

    @Override
    public SurfaceTrackerSection loadNode(SurfaceTrackerSection parent, byte heightmapType, int scale, int scaledY) {
        SurfaceTrackerSection removed = saved.remove(new PackedTypeScaleScaledY(heightmapType, scale, scaledY));
        if (removed != null) {
            removed.parent = parent;
        }
        return removed;
    }

    record PackedTypeScaleScaledY(byte heightmapType, int scale, int scaledY) { }
}

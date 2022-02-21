package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import java.util.Arrays;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;

public class SurfaceTrackerSectionStorage implements HeightmapStorage {
    private Object2ReferenceMap<PackedTypeScaleScaledY, SurfaceTrackerNode> saved = new Object2ReferenceOpenHashMap<>();

    @Override
    public void unloadNode(SurfaceTrackerNode surfaceTrackerNode) {
        saved.put(new PackedTypeScaleScaledY(surfaceTrackerNode.heightmapType, surfaceTrackerNode.scale, surfaceTrackerNode.scaledY), surfaceTrackerNode);

        if (surfaceTrackerNode.scale == 0) {
            ((SurfaceTrackerLeaf) surfaceTrackerNode).node = null;
        } else {
            Arrays.fill(((SurfaceTrackerBranch) surfaceTrackerNode).children, null);
        }
        surfaceTrackerNode.parent = null;
    }

    @Override
    public SurfaceTrackerNode loadNode(SurfaceTrackerBranch parent, byte heightmapType, int scale, int scaledY) {
        SurfaceTrackerNode removed = saved.remove(new PackedTypeScaleScaledY(heightmapType, scale, scaledY));
        if (removed != null) {
            removed.parent = parent;
        }
        return removed;
    }

    record PackedTypeScaleScaledY(byte heightmapType, int scale, int scaledY) { }
}

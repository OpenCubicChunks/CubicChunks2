package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.ClientLightSurfaceTracker;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.LightSurfaceTrackerWrapper;
import net.minecraft.world.level.levelgen.Heightmap;

public interface LightHeightmapGetter {
    /**
     * Can return null for a protochunk before features stage
     */
    @Nullable Heightmap getLightHeightmap();

    // Do not override this
    default ClientLightSurfaceTracker getClientLightHeightmap() {
        Heightmap lightHeightmap = this.getLightHeightmap();
        if (lightHeightmap != null && !(lightHeightmap instanceof ClientLightSurfaceTracker)) {
            throw new IllegalStateException("Attempted to get client light heightmap on server");
        }
        assert lightHeightmap != null; //Light heightmap should never be null on the client
        return (ClientLightSurfaceTracker) lightHeightmap;
    }

    // Do not override this
    /**
     * Can return null for a protochunk before features stage
     */
    @Nullable
    default LightSurfaceTrackerWrapper getServerLightHeightmap() {
        Heightmap lightHeightmap = this.getLightHeightmap();
        if (lightHeightmap != null && !(lightHeightmap instanceof LightSurfaceTrackerWrapper)) {
            throw new IllegalStateException("Attempted to get server light heightmap on client");
        }
        return (LightSurfaceTrackerWrapper) lightHeightmap;
    }
}

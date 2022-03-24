package io.github.opencubicchunks.cubicchunks.world.level.chunk;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.ClientLightHeightmap;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree.LightHeightmapTree;
import net.minecraft.world.level.levelgen.Heightmap;

public interface LightHeightmapGetter {
    /**
     * Can return null for a protochunk before features stage
     */
    @Nullable Heightmap getLightHeightmap();

    // Do not override this
    default ClientLightHeightmap getClientLightHeightmap() {
        Heightmap lightHeightmap = this.getLightHeightmap();
        if (lightHeightmap != null && !(lightHeightmap instanceof ClientLightHeightmap)) {
            throw new IllegalStateException("Attempted to get client light heightmap on server");
        }
        assert lightHeightmap != null; //Light heightmap should never be null on the client
        return (ClientLightHeightmap) lightHeightmap;
    }

    // Do not override this
    /**
     * Can return null for a protochunk before features stage
     */
    @Nullable
    default LightHeightmapTree getServerLightHeightmap() {
        Heightmap lightHeightmap = this.getLightHeightmap();
        if (lightHeightmap != null && !(lightHeightmap instanceof LightHeightmapTree)) {
            throw new IllegalStateException("Attempted to get server light heightmap on client");
        }
        return (LightHeightmapTree) lightHeightmap;
    }
}

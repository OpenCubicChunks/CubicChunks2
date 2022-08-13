package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import static io.github.opencubicchunks.cc_core.utils.Coords.blockToCube;
import static io.github.opencubicchunks.cc_core.utils.Coords.blockToLocal;

import io.github.opencubicchunks.cc_core.world.heightmap.HeightmapStorage;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public class LightSurfaceTrackerWrapper extends SurfaceTrackerWrapper {
    public LightSurfaceTrackerWrapper(ChunkAccess chunkAccess, HeightmapStorage storage) {
        // type shouldn't matter
        super(chunkAccess, Types.WORLD_SURFACE, loadOrCreateRoot(chunkAccess.getPos().x, chunkAccess.getPos().z, (byte) -1, storage));
    }

    @Override
    public boolean update(int columnLocalX, int globalY, int columnLocalZ, BlockState blockState) {
        super.update(columnLocalX, globalY, columnLocalZ, blockState);
        int relY = blockToLocal(globalY);
        // TODO how are we going to handle making sure that unloaded sections stay updated?
        if (relY == 0) {
            SurfaceTrackerLeaf leaf = surfaceTracker.getLeaf(blockToCube(globalY - 1));
            if (leaf != null) {
                leaf.markDirty(columnLocalX, columnLocalZ);
            }
        } else if (relY == CubeAccess.DIAMETER_IN_BLOCKS - 1) {
            SurfaceTrackerLeaf leaf = surfaceTracker.getLeaf(blockToCube(globalY + 1));
            if (leaf != null) {
                leaf.markDirty(columnLocalX, columnLocalZ);
            }
        }

        // Return value is unused
        return false;
    }
}

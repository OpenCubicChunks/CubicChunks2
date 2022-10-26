package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import static io.github.opencubicchunks.cc_core.utils.Coords.blockToCube;
import static io.github.opencubicchunks.cc_core.utils.Coords.blockToLocal;

import io.github.opencubicchunks.cc_core.api.CubicConstants;
import io.github.opencubicchunks.cc_core.world.heightmap.HeightmapStorage;
import io.github.opencubicchunks.cc_core.world.heightmap.surfacetrackertree.SurfaceTrackerLeaf;
import io.github.opencubicchunks.cubicchunks.world.BigChunk;
import net.minecraft.world.level.block.state.BlockState;

public class BigLightSurfaceTrackerWrapper extends BigSurfaceTrackerWrapper {
    public BigLightSurfaceTrackerWrapper(BigChunk bigChunk, HeightmapStorage storage) {
        super(bigChunk, loadOrCreateRoot(bigChunk.getPos().getX(), bigChunk.getPos().getZ(), (byte) -1, storage));
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
        } else if (relY == CubicConstants.DIAMETER_IN_BLOCKS - 1) {
            SurfaceTrackerLeaf leaf = surfaceTracker.getLeaf(blockToCube(globalY + 1));
            if (leaf != null) {
                leaf.markDirty(columnLocalX, columnLocalZ);
            }
        }

        // Return value is unused
        return false;
    }
}

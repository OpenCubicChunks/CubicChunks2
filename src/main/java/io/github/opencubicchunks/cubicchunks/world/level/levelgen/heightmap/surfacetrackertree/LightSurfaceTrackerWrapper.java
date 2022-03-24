package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import static io.github.opencubicchunks.cubicchunks.utils.Coords.*;

import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public class LightSurfaceTrackerWrapper extends SurfaceTrackerWrapper {
    public LightSurfaceTrackerWrapper(ChunkAccess chunkAccess) {
        // type shouldn't matter
        super(chunkAccess, Types.WORLD_SURFACE, new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, (byte) -1));
    }

    @Override
    public boolean update(int columnLocalX, int globalY, int columnLocalZ, BlockState blockState) {
        super.update(columnLocalX, globalY, columnLocalZ, blockState);
        int relY = blockToLocal(globalY);
        // TODO how are we going to handle making sure that unloaded sections stay updated?
        if (relY == 0) {
            SurfaceTrackerLeaf leaf = surfaceTracker.getMinScaleNode(blockToCube(globalY - 1));
            if (leaf != null) {
                leaf.markDirty(columnLocalX, columnLocalZ);
            }
        } else if (relY == CubeAccess.DIAMETER_IN_BLOCKS - 1) {
            SurfaceTrackerLeaf leaf = surfaceTracker.getMinScaleNode(blockToCube(globalY + 1));
            if (leaf != null) {
                leaf.markDirty(columnLocalX, columnLocalZ);
            }
        }

        // Return value is unused
        return false;
    }
}

package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree;

import static io.github.opencubicchunks.cubicchunks.utils.Coords.*;

import io.github.opencubicchunks.cubicchunks.world.level.chunk.CubeAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public class LightHeightmapTree extends HeightmapTree {
    public LightHeightmapTree(ChunkAccess chunkAccess) {
        // type shouldn't matter
        super(chunkAccess, Types.WORLD_SURFACE, new HeightmapTreeBranch(HeightmapTreeNode.MAX_SCALE, 0, null, (byte) -1));
    }

    @Override
    public boolean update(int cubeLocalX, int globalY, int cubeLocalZ, BlockState blockState) {
        super.update(cubeLocalX, globalY, cubeLocalZ, blockState);
        int relY = blockToLocal(globalY);
        // TODO how are we going to handle making sure that unloaded sections stay updated?
        if (relY == 0) {
            HeightmapTreeLeaf leaf = rootBranch.getLeaf(blockToCube(globalY - 1));
            if (leaf != null) {
                leaf.markDirty(cubeLocalX, cubeLocalZ);
            }
        } else if (relY == CubeAccess.DIAMETER_IN_BLOCKS - 1) {
            HeightmapTreeLeaf leaf = rootBranch.getLeaf(blockToCube(globalY + 1));
            if (leaf != null) {
                leaf.markDirty(cubeLocalX, cubeLocalZ);
            }
        }

        // Return value is unused
        return false;
    }
}

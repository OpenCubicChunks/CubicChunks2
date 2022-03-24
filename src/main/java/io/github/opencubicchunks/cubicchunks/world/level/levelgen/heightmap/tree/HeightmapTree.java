package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.tree;

import static io.github.opencubicchunks.cubicchunks.utils.Coords.*;

import java.util.function.IntPredicate;


import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.HeightmapAccess;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.CubeHeightAccess;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.commons.lang3.NotImplementedException;

public class HeightmapTree extends Heightmap {
    public static final Heightmap.Types[] HEIGHTMAP_TYPES = Heightmap.Types.values();

    protected final HeightmapTreeBranch rootBranch;
    /** global x of min block in column */
    protected final int xOffset;
    /** global z of min block in column */
    protected final int zOffset;

    public HeightmapTree(ChunkAccess chunkAccess, Types type) {
        super(chunkAccess, type);
        //noinspection ConstantConditions
        ((HeightmapAccess) this).setIsOpaque(null);
        this.rootBranch = new HeightmapTreeBranch(HeightmapTreeNode.MAX_SCALE, 0, null, (byte) type.ordinal());
        this.xOffset = sectionToMinBlock(chunkAccess.getPos().x);
        this.zOffset = sectionToMinBlock(chunkAccess.getPos().z);
    }

    protected HeightmapTree(ChunkAccess chunkAccess, Types types, HeightmapTreeBranch root) {
        super(chunkAccess, types);
        //noinspection ConstantConditions
        ((HeightmapAccess) this).setIsOpaque(null);
        this.rootBranch = root;
        this.xOffset = sectionToMinBlock(chunkAccess.getPos().x);
        this.zOffset = sectionToMinBlock(chunkAccess.getPos().z);
    }

    /**
     *
     * @param cubeLocalX cube-local x
     * @param globalY global y
     * @param cubeLocalZ cube-local z
     * @param blockState unused.
     * @return currently unused; always false
     */
    @Override
    public boolean update(int cubeLocalX, int globalY, int cubeLocalZ, BlockState blockState) {
        rootBranch.getLeaf(blockToCube(globalY)).onSetBlock(xOffset + cubeLocalX, globalY, zOffset + cubeLocalZ, opaquePredicateForState(blockState));
        // We always return false, because the result is never used anywhere anyway (by either vanilla or us)
        return false;
    }

    @Override
    public int getFirstAvailable(int cubeLocalX, int cubeLocalZ) {
        return rootBranch.getHeight(xOffset + cubeLocalX, zOffset + cubeLocalZ) + 1;
    }

    // TODO not sure what to do about these methods
    @Override
    public void setRawData(ChunkAccess chunkAccess, Types type, long[] data) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long[] getRawData() {
        BitStorage data = ((HeightmapAccess) this).getData();
        rootBranch.writeData(xOffset, zOffset, data, ((HeightmapAccess) this).getChunk().getMinBuildHeight());
        return data.getRaw();
    }

    public synchronized void loadCube(HeightmapStorage storage, CubeHeightAccess node) {
        this.rootBranch.loadCube(blockToCubeLocalSection(xOffset), blockToCubeLocalSection(zOffset), storage, node);
    }

    @Nullable
    public HeightmapTreeLeaf getLeaf(int nodeY) {
        return rootBranch.getLeaf(nodeY);
    }

    public HeightmapTreeBranch getRoot() {
        return this.rootBranch;
    }

    public static IntPredicate opaquePredicateForState(BlockState blockState) {
        return heightmapType -> {
            if (heightmapType == -1) {
                throw new NotImplementedException("Currently we are always marking light as dirty, so this should never be reached");
            } else {
                return HEIGHTMAP_TYPES[heightmapType].isOpaque().test(blockState);
            }
        };
    }
}

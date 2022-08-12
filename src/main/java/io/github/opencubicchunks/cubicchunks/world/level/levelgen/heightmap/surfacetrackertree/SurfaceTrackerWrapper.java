package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree;

import static io.github.opencubicchunks.cubicchunks.utils.Coords.*;
import static io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.surfacetrackertree.SurfaceTrackerNode.MAX_SCALE;

import java.util.function.IntPredicate;


import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.HeightmapAccess;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapSource;
import io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap.HeightmapStorage;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.commons.lang3.NotImplementedException;

public class SurfaceTrackerWrapper extends Heightmap {
    public static final Heightmap.Types[] HEIGHTMAP_TYPES = Heightmap.Types.values();

    protected final SurfaceTrackerBranch surfaceTracker;
    /** global x of min block in column */
    protected final int dx;
    /** global z of min block in column */
    protected final int dz;

    public SurfaceTrackerWrapper(ChunkAccess chunkAccess, Types types, HeightmapStorage storage) {
        super(chunkAccess, types);
        //noinspection ConstantConditions
        ((HeightmapAccess) this).setIsOpaque(null);
        this.surfaceTracker = loadOrCreateRoot(chunkAccess.getPos().x, chunkAccess.getPos().z, (byte) types.ordinal(), storage);
        this.dx = sectionToMinBlock(chunkAccess.getPos().x);
        this.dz = sectionToMinBlock(chunkAccess.getPos().z);
    }

    protected SurfaceTrackerWrapper(ChunkAccess chunkAccess, Types types, SurfaceTrackerBranch root) {
        super(chunkAccess, types);
        //noinspection ConstantConditions
        ((HeightmapAccess) this).setIsOpaque(null);
        this.surfaceTracker = root;
        this.dx = sectionToMinBlock(chunkAccess.getPos().x);
        this.dz = sectionToMinBlock(chunkAccess.getPos().z);
    }

    /**
     *
     * @param columnLocalX column-local x
     * @param globalY global y
     * @param columnLocalZ column-local z
     * @param blockState unused.
     * @return currently unused; always false
     */
    @Override
    public boolean update(int columnLocalX, int globalY, int columnLocalZ, BlockState blockState) {
        surfaceTracker.getLeaf(blockToCube(globalY)).onSetBlock(dx + columnLocalX, globalY, dz + columnLocalZ, opaquePredicateForState(blockState));
        // We always return false, because the result is never used anywhere anyway (by either vanilla or us)
        return false;
    }

    @Override
    public int getFirstAvailable(int columnLocalX, int columnLocalZ) {
        return surfaceTracker.getHeight(columnLocalX + dx, columnLocalZ + dz) + 1;
    }

    @Override
    public void setRawData(ChunkAccess clv, Types a, long[] ls) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long[] getRawData() {
        BitStorage data = ((HeightmapAccess) this).getData();
        surfaceTracker.writeDataForClient(dx, dz, data, ((HeightmapAccess) this).getChunk().getMinBuildHeight());
        return data.getRaw();
    }

    public synchronized void loadCube(HeightmapStorage storage, HeightmapSource source) {
        this.surfaceTracker.loadSource(blockToSection(dx), blockToSection(dz), storage, source);
    }

    public void saveAll(HeightmapStorage storage) {
        int globalSectionX = blockToSection(this.dx);
        int globalSectionZ = blockToSection(this.dz);
        saveAllChildren(globalSectionX, globalSectionZ, storage, this.surfaceTracker);
    }

    private void saveAllChildren(int globalSectionX, int globalSectionZ, HeightmapStorage storage, SurfaceTrackerNode node) {
        node.save(globalSectionX, globalSectionZ, storage);
        if (node.scale > 0) {
            for (SurfaceTrackerNode child : ((SurfaceTrackerBranch) node).children) {
                if (child == null) {
                    continue;
                }
                saveAllChildren(globalSectionX, globalSectionZ, storage, child);
            }
        }
    }

    @Nullable
    public SurfaceTrackerLeaf getLeaf(int nodeY) {
        return surfaceTracker.getLeaf(nodeY);
    }

    public SurfaceTrackerBranch getSurfaceTrackerSection() {
        return this.surfaceTracker;
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

    protected static SurfaceTrackerBranch loadOrCreateRoot(int globalSectionX, int globalSectionZ, byte type, HeightmapStorage storage) {
        SurfaceTrackerNode loadedNode = storage.loadNode(globalSectionX, globalSectionZ, null, type, MAX_SCALE, 0);
        if (loadedNode != null) {
            return (SurfaceTrackerBranch) loadedNode;
        }
        return new SurfaceTrackerBranch(SurfaceTrackerNode.MAX_SCALE, 0, null, type);
    }
}

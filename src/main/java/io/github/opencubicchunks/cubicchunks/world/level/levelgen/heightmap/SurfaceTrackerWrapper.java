package io.github.opencubicchunks.cubicchunks.world.level.levelgen.heightmap;

import static io.github.opencubicchunks.cubicchunks.utils.Coords.*;

import java.util.function.IntPredicate;


import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.mixin.access.common.HeightmapAccess;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import org.apache.commons.lang3.NotImplementedException;

public class SurfaceTrackerWrapper extends Heightmap {
    public static final Heightmap.Types[] HEIGHTMAP_TYPES = Heightmap.Types.values();

    protected final SurfaceTrackerSection surfaceTracker;
    /** global x of min block in column */
    protected final int dx;
    /** global z of min block in column */
    protected final int dz;

    public SurfaceTrackerWrapper(ChunkAccess chunkAccess, Types types) {
        super(chunkAccess, types);
        //noinspection ConstantConditions
        ((HeightmapAccess) this).setIsOpaque(null);
        this.surfaceTracker = new SurfaceTrackerSection(types);
        this.dx = sectionToMinBlock(chunkAccess.getPos().x);
        this.dz = sectionToMinBlock(chunkAccess.getPos().z);
    }

    protected SurfaceTrackerWrapper(ChunkAccess chunkAccess, Types types, SurfaceTrackerSection root) {
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
        surfaceTracker.getMinScaleNode(blockToCube(globalY)).onSetBlock(dx + columnLocalX, globalY, dz + columnLocalZ, opaquePredicateForState(blockState));
        // We always return false, because the result is never used anywhere anyway (by either vanilla or us)
        return false;
    }

    @Override
    public int getFirstAvailable(int columnLocalX, int columnLocalZ) {
        return surfaceTracker.getHeight(columnLocalX + dx, columnLocalZ + dz) + 1;
    }

    // TODO not sure what to do about these methods
    @Override
    public void setRawData(ChunkAccess clv, Types a, long[] ls) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long[] getRawData() {
        BitStorage data = ((HeightmapAccess) this).getData();
        surfaceTracker.writeData(dx, dz, data, ((HeightmapAccess) this).getChunk().getMinBuildHeight());
        return data.getRaw();
    }

    public synchronized void loadCube(HeightmapNode node) {
        this.surfaceTracker.loadCube(blockToCubeLocalSection(dx), blockToCubeLocalSection(dz), node);
    }

    @Nullable
    public SurfaceTrackerSection getMinScaleNode(int nodeY) {
        return surfaceTracker.getMinScaleNode(nodeY);
    }

    public SurfaceTrackerSection getSurfaceTrackerSection() {
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
}
